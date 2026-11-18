package com.example.engine

import android.graphics.Bitmap
import android.graphics.Rect
import com.example.data.LogLevel
import com.example.data.MacroRepository
import com.example.model.BranchAction
import com.example.model.DelayStep
import com.example.model.IfColorStep
import com.example.model.IfImageStep
import com.example.model.IfVariableStep
import com.example.model.JumpStep
import com.example.model.KeyPressStep
import com.example.model.LoopEndStep
import com.example.model.LoopStartStep
import com.example.model.MacroDefinition
import com.example.model.MacroStep
import com.example.model.SetVariableStep
import com.example.model.StopStep
import com.example.model.SwipeStep
import com.example.model.TapStep
import com.example.model.TouchGuardPolicy
import com.example.model.VarComparator
import com.example.model.VarOperation
import com.example.service.OverlayCoordinator
import com.example.shizuku.ShizukuConnectionState
import com.example.shizuku.ShizukuInputBridge
import com.example.vision.ScreenDetector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class ExecutionStatus {
    IDLE,
    RUNNING,
    PAUSED,
    ERROR
}

data class RuntimeExecutionState(
    val status: ExecutionStatus = ExecutionStatus.IDLE,
    val activeMacroId: String? = null,
    val activeMacroName: String = "",
    val currentStepId: String? = null,
    val currentStepLabel: String = "",
    val currentLoopIteration: Int = 0,
    val totalLoops: Int = 1,
    val variables: Map<String, String> = emptyMap(),
    val lastTelemetry: String = "Ready",
    val errorMessage: String? = null
)

private data class LoopFrame(
    val startIndex: Int,
    val startStepId: String,
    var remainingIterations: Int,
    val infiniteUntilConditionFalse: Boolean
)

class MacroInterpreter(
    private val repository: MacroRepository,
    private val shizukuBridge: ShizukuInputBridge,
    private val screenDetector: ScreenDetector,
    private val overlayCoordinator: OverlayCoordinator = OverlayCoordinator()
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var executionJob: Job? = null

    private val _state = MutableStateFlow(RuntimeExecutionState())
    val state: StateFlow<RuntimeExecutionState> = _state.asStateFlow()

    fun start(macro: MacroDefinition) {
        stop()
        if (macro.steps.isEmpty()) {
            _state.value = RuntimeExecutionState(
                status = ExecutionStatus.ERROR,
                activeMacroId = macro.id,
                activeMacroName = macro.name,
                errorMessage = "Macro has no steps to execute."
            )
            return
        }

        val requiresTouch = macro.steps.any {
            it.enabled && (it is TapStep || it is SwipeStep || it is KeyPressStep ||
                (it is IfColorStep && it.tapOnMatch) || (it is IfImageStep && it.tapOnMatch))
        }
        if (requiresTouch && shizukuBridge.status.value.state != ShizukuConnectionState.CONNECTED) {
            val msg = "Shizuku service is not connected. Start Shizuku and grant permission first."
            _state.value = RuntimeExecutionState(
                status = ExecutionStatus.ERROR,
                activeMacroId = macro.id,
                activeMacroName = macro.name,
                errorMessage = msg,
                lastTelemetry = msg
            )
            scope.launch {
                repository.appendLog(macro.id, macro.name, "Preflight", msg, LogLevel.ERROR)
            }
            return
        }

        val requiresVision = macro.steps.any { it.enabled && (it is IfColorStep || it is IfImageStep) }
        if (requiresVision && !screenDetector.isActive.value) {
            val msg = "Screen capture is not active. Grant screen capture before running color or image triggers."
            _state.value = RuntimeExecutionState(
                status = ExecutionStatus.ERROR,
                activeMacroId = macro.id,
                activeMacroName = macro.name,
                errorMessage = msg,
                lastTelemetry = msg
            )
            scope.launch {
                repository.appendLog(macro.id, macro.name, "Preflight", msg, LogLevel.ERROR)
            }
            return
        }

        _state.value = RuntimeExecutionState(
            status = ExecutionStatus.RUNNING,
            activeMacroId = macro.id,
            activeMacroName = macro.name,
            totalLoops = macro.loopCount,
            lastTelemetry = "Starting ${macro.name}..."
        )
        executionJob = scope.launch {
            runMacroLoop(macro)
        }
    }

    fun pauseOrResume() {
        val current = _state.value.status
        if (current == ExecutionStatus.RUNNING) {
            _state.update { it.copy(status = ExecutionStatus.PAUSED, lastTelemetry = "Paused by user") }
        } else if (current == ExecutionStatus.PAUSED) {
            _state.update { it.copy(status = ExecutionStatus.RUNNING, lastTelemetry = "Resumed") }
        }
    }

    fun stop() {
        executionJob?.cancel()
        executionJob = null
        shizukuBridge.releaseActiveTouch()
        _state.update {
            it.copy(
                status = ExecutionStatus.IDLE,
                currentStepId = null,
                currentStepLabel = "",
                lastTelemetry = "Stopped"
            )
        }
    }

    private suspend fun runMacroLoop(macro: MacroDefinition) {
        val variables = mutableMapOf<String, String>()
        val infiniteMacro = macro.loopCount <= 0
        var macroPass = 0

        _state.value = RuntimeExecutionState(
            status = ExecutionStatus.RUNNING,
            activeMacroId = macro.id,
            activeMacroName = macro.name,
            totalLoops = macro.loopCount,
            lastTelemetry = "Started ${macro.name}"
        )
        repository.appendLog(macro.id, macro.name, "Engine", "Started macro (${macro.steps.size} steps)", LogLevel.INFO)

        try {
            while (scope.isActive && (infiniteMacro || macroPass < macro.loopCount)) {
                macroPass++
                _state.update {
                    it.copy(
                        currentLoopIteration = macroPass,
                        variables = variables.toMap()
                    )
                }

                val steps = macro.steps
                val stepIndexById = steps.withIndex().associate { it.value.id to it.index }
                val loopStack = ArrayDeque<LoopFrame>()
                var pc = 0
                var stopRequested = false

                while (pc in steps.indices && scope.isActive && !stopRequested) {
                    waitIfPaused()

                    val step = steps[pc]
                    if (!step.enabled) {
                        pc++
                        continue
                    }

                    _state.update {
                        it.copy(
                            currentStepId = step.id,
                            currentStepLabel = step.label,
                            variables = variables.toMap()
                        )
                    }

                    when (step) {
                        is TapStep -> {
                            waitForTouchGuard(macro.touchGuardPolicy)
                            val ok = shizukuBridge.tap(step.x, step.y, step.holdMs, step.jitterPx, macro.injectionMode)
                            val msg = if (ok) {
                                "Tap (${step.x.toInt()}, ${step.y.toInt()}) [${macro.injectionMode.displayLabel}]"
                            } else {
                                "Tap failed at (${step.x.toInt()}, ${step.y.toInt()})"
                            }
                            updateTelemetry(msg)
                            if (!ok) {
                                failExecution(macro, step.label, "Shizuku touch injection failed")
                                return
                            }
                            pc++
                        }

                        is SwipeStep -> {
                            waitForTouchGuard(macro.touchGuardPolicy)
                            val ok = shizukuBridge.swipe(
                                step.startX,
                                step.startY,
                                step.endX,
                                step.endY,
                                step.durationMs,
                                step.interpolateSteps,
                                macro.injectionMode
                            )
                            val msg = "Swipe (${step.startX.toInt()},${step.startY.toInt()}) -> (${step.endX.toInt()},${step.endY.toInt()}) [${macro.injectionMode.displayLabel}]"
                            updateTelemetry(msg)
                            if (!ok) {
                                failExecution(macro, step.label, "Shizuku swipe injection failed")
                                return
                            }
                            pc++
                        }

                        is DelayStep -> {
                            val min = step.minMs.coerceAtLeast(0L)
                            val max = step.maxMs.coerceAtLeast(min)
                            val waitMs = if (max > min) Random.nextLong(min, max + 1) else min
                            updateTelemetry("Wait ${waitMs}ms")
                            delay(waitMs)
                            pc++
                        }

                        is KeyPressStep -> {
                            val ok = shizukuBridge.pressKey(step.keyAction.keyCode)
                            updateTelemetry("Key ${step.keyAction.displayLabel}")
                            if (!ok) {
                                failExecution(macro, step.label, "Key injection failed")
                                return
                            }
                            pc++
                        }

                        is SetVariableStep -> {
                            val newValue = evaluateSetVar(variables[step.variableName], step.operation, step.valueExpression)
                            variables[step.variableName] = newValue
                            _state.update { it.copy(variables = variables.toMap()) }
                            updateTelemetry("${step.variableName} = $newValue")
                            pc++
                        }

                        is LoopStartStep -> {
                            val conditionSatisfied = if (step.conditionVar.isBlank()) {
                                true
                            } else {
                                compareValues(
                                    variables[step.conditionVar] ?: "0",
                                    step.conditionOp,
                                    step.conditionValue
                                )
                            }
                            if (!conditionSatisfied) {
                                pc = findMatchingLoopEnd(steps, pc, step.id) + 1
                            } else {
                                loopStack.addLast(
                                    LoopFrame(
                                        startIndex = pc,
                                        startStepId = step.id,
                                        remainingIterations = step.iterations.coerceAtLeast(0),
                                        infiniteUntilConditionFalse = step.iterations <= 0 && step.conditionVar.isNotBlank()
                                    )
                                )
                                pc++
                            }
                        }

                        is LoopEndStep -> {
                            val top = loopStack.lastOrNull()
                            if (top == null) {
                                pc++
                            } else {
                                val startStep = steps[top.startIndex] as? LoopStartStep
                                val conditionStillTrue = if (startStep != null && startStep.conditionVar.isNotBlank()) {
                                    compareValues(
                                        variables[startStep.conditionVar] ?: "0",
                                        startStep.conditionOp,
                                        startStep.conditionValue
                                    )
                                } else {
                                    true
                                }

                                val shouldContinue = if (top.infiniteUntilConditionFalse) {
                                    conditionStillTrue
                                } else {
                                    top.remainingIterations -= 1
                                    top.remainingIterations > 0 && conditionStillTrue
                                }

                                if (shouldContinue) {
                                    updateTelemetry("Loop repeat (${top.remainingIterations} left)")
                                    pc = top.startIndex + 1
                                } else {
                                    loopStack.removeLast()
                                    pc++
                                }
                            }
                        }

                        is IfColorStep -> {
                            val frame = captureWithRetry(macro.pollingIntervalMs)
                            if (frame == null) {
                                updateTelemetry("No screen frame yet")
                                pc = resolveBranchPc(step.elseAction, step.elseJumpStepId, pc, steps, stepIndexById, loopStack) {
                                    stopRequested = true
                                }
                            } else {
                                val result = screenDetector.matchColor(
                                    frame,
                                    step.x,
                                    step.y,
                                    step.targetHexColor,
                                    step.tolerancePct
                                )
                                frame.recycle()
                                updateTelemetry("Color ${result.sampledHex} (${result.similarityPct}%)")
                                if (result.matched) {
                                    repository.appendLog(
                                        macro.id,
                                        macro.name,
                                        step.label,
                                        "Matched ${step.targetHexColor} at (${step.x},${step.y}) [${result.similarityPct}%]",
                                        LogLevel.SUCCESS
                                    )
                                    if (step.tapOnMatch) {
                                        waitForTouchGuard(macro.touchGuardPolicy)
                                        shizukuBridge.tap(step.x.toFloat(), step.y.toFloat(), mode = macro.injectionMode)
                                    }
                                    pc = resolveBranchPc(step.thenAction, step.thenJumpStepId, pc, steps, stepIndexById, loopStack) {
                                        stopRequested = true
                                    }
                                } else {
                                    pc = resolveBranchPc(step.elseAction, step.elseJumpStepId, pc, steps, stepIndexById, loopStack) {
                                        stopRequested = true
                                    }
                                }
                            }
                        }

                        is IfImageStep -> {
                            val tpl = repository.loadTemplateBitmap(step.templateId)
                            val frame = captureWithRetry(macro.pollingIntervalMs)
                            if (tpl == null || frame == null) {
                                tpl?.recycle()
                                frame?.recycle()
                                updateTelemetry(if (tpl == null) "Missing template asset" else "No screen frame")
                                pc = resolveBranchPc(step.elseAction, step.elseJumpStepId, pc, steps, stepIndexById, loopStack) {
                                    stopRequested = true
                                }
                            } else {
                                val roi = if (step.searchRight > step.searchLeft && step.searchBottom > step.searchTop) {
                                    Rect(step.searchLeft, step.searchTop, step.searchRight, step.searchBottom)
                                } else null
                                val result = screenDetector.matchTemplate(frame, tpl, roi, step.minConfidencePct)
                                tpl.recycle()
                                frame.recycle()
                                updateTelemetry("Image ${step.templateName}: ${result.confidencePct}%")
                                if (result.matched) {
                                    repository.appendLog(
                                        macro.id,
                                        macro.name,
                                        step.label,
                                        "Matched '${step.templateName}' at (${result.centerX},${result.centerY}) [${result.confidencePct}%]",
                                        LogLevel.SUCCESS
                                    )
                                    if (step.tapOnMatch) {
                                        waitForTouchGuard(macro.touchGuardPolicy)
                                        shizukuBridge.tap(result.centerX.toFloat(), result.centerY.toFloat(), mode = macro.injectionMode)
                                    }
                                    pc = resolveBranchPc(step.thenAction, step.thenJumpStepId, pc, steps, stepIndexById, loopStack) {
                                        stopRequested = true
                                    }
                                } else {
                                    pc = resolveBranchPc(step.elseAction, step.elseJumpStepId, pc, steps, stepIndexById, loopStack) {
                                        stopRequested = true
                                    }
                                }
                            }
                        }

                        is IfVariableStep -> {
                            val currentVal = variables[step.variableName] ?: "0"
                            val matched = compareValues(currentVal, step.comparator, step.compareValue)
                            updateTelemetry("If ${step.variableName}($currentVal) -> $matched")
                            pc = if (matched) {
                                resolveBranchPc(step.thenAction, step.thenJumpStepId, pc, steps, stepIndexById, loopStack) {
                                    stopRequested = true
                                }
                            } else {
                                resolveBranchPc(step.elseAction, step.elseJumpStepId, pc, steps, stepIndexById, loopStack) {
                                    stopRequested = true
                                }
                            }
                        }

                        is JumpStep -> {
                            val targetIdx = stepIndexById[step.targetStepId]
                            pc = if (targetIdx != null) targetIdx else pc + 1
                        }

                        is StopStep -> {
                            stopRequested = true
                        }
                    }

                    // Yield briefly between steps so cancellations and tight loops stay responsive
                    delay(12L)
                }

                if (stopRequested) break
                if (infiniteMacro || macroPass < macro.loopCount) {
                    delay(macro.pollingIntervalMs.coerceAtLeast(40L))
                }
            }

            repository.appendLog(macro.id, macro.name, "Engine", "Completed $macroPass run(s)", LogLevel.SUCCESS)
            _state.update {
                it.copy(
                    status = ExecutionStatus.IDLE,
                    currentStepId = null,
                    currentStepLabel = "",
                    lastTelemetry = "Completed ($macroPass pass)"
                )
            }
        } catch (_: CancellationException) {
            repository.appendLog(macro.id, macro.name, "Engine", "Stopped by user", LogLevel.WARN)
        } catch (t: Throwable) {
            failExecution(macro, "Engine", t.message ?: "Unexpected execution error")
        } finally {
            shizukuBridge.releaseActiveTouch()
        }
    }

    private suspend fun captureWithRetry(pollingMs: Long): Bitmap? {
        val first = screenDetector.captureLatestBitmap()
        if (first != null) return first
        delay(pollingMs.coerceIn(60L, 350L))
        return screenDetector.captureLatestBitmap()
    }

    private suspend fun waitIfPaused() {
        while (_state.value.status == ExecutionStatus.PAUSED) {
            delay(80L)
        }
    }

    private suspend fun waitForTouchGuard(policy: TouchGuardPolicy) {
        if (policy == TouchGuardPolicy.OFF || policy.cooldownMs <= 0L) return
        val maxWaitMs = policy.cooldownMs * 2L
        var waitedMs = 0L
        while (scope.isActive && waitedMs < maxWaitMs) {
            val elapsedSinceTouch = overlayCoordinator.msSinceLastUserTouch()
            if (elapsedSinceTouch >= policy.cooldownMs) return
            updateTelemetry("Touch Guard yielding (${policy.displayLabel})...")
            val stepMs = 45L
            delay(stepMs)
            waitedMs += stepMs
        }
    }

    private fun updateTelemetry(msg: String) {
        _state.update { it.copy(lastTelemetry = msg) }
    }

    private suspend fun failExecution(macro: MacroDefinition, stepLabel: String, message: String) {
        repository.appendLog(macro.id, macro.name, stepLabel, message, LogLevel.ERROR)
        _state.update {
            it.copy(
                status = ExecutionStatus.ERROR,
                errorMessage = message,
                lastTelemetry = message
            )
        }
    }

    private fun resolveBranchPc(
        action: BranchAction,
        jumpStepId: String?,
        currentPc: Int,
        steps: List<MacroStep>,
        stepIndexById: Map<String, Int>,
        loopStack: ArrayDeque<LoopFrame>,
        onStop: () -> Unit
    ): Int {
        return when (action) {
            BranchAction.CONTINUE_NEXT -> currentPc + 1
            BranchAction.SKIP_NEXT -> currentPc + 2
            BranchAction.JUMP_TO_STEP -> {
                val target = jumpStepId?.let { stepIndexById[it] }
                target ?: (currentPc + 1)
            }
            BranchAction.BREAK_LOOP -> {
                val top = loopStack.removeLastOrNull()
                if (top != null) {
                    findMatchingLoopEnd(steps, top.startIndex, top.startStepId) + 1
                } else {
                    currentPc + 1
                }
            }
            BranchAction.STOP_MACRO -> {
                onStop()
                steps.size
            }
        }
    }

    private fun findMatchingLoopEnd(steps: List<MacroStep>, startIndex: Int, startStepId: String): Int {
        var depth = 0
        for (i in startIndex until steps.size) {
            val s = steps[i]
            if (s is LoopStartStep) {
                depth++
            } else if (s is LoopEndStep) {
                if (s.loopStartId == startStepId && depth <= 1) return i
                depth--
                if (depth <= 0) return i
            }
        }
        return steps.lastIndex
    }

    private fun evaluateSetVar(currentRaw: String?, op: VarOperation, expr: String): String {
        val currentNum = currentRaw?.toDoubleOrNull() ?: 0.0
        val exprNum = expr.trim().toDoubleOrNull()
        return when (op) {
            VarOperation.SET -> expr.trim()
            VarOperation.INCREMENT -> {
                val delta = exprNum ?: 1.0
                formatNum(currentNum + delta)
            }
            VarOperation.DECREMENT -> {
                val delta = exprNum ?: 1.0
                formatNum(currentNum - delta)
            }
        }
    }

    private fun formatNum(v: Double): String {
        return if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
    }

    private fun compareValues(leftRaw: String, comparator: VarComparator, rightRaw: String): Boolean {
        val lNum = leftRaw.trim().toDoubleOrNull()
        val rNum = rightRaw.trim().toDoubleOrNull()
        if (lNum != null && rNum != null) {
            return when (comparator) {
                VarComparator.EQUALS -> lNum == rNum
                VarComparator.NOT_EQUALS -> lNum != rNum
                VarComparator.GREATER_THAN -> lNum > rNum
                VarComparator.LESS_THAN -> lNum < rNum
                VarComparator.GREATER_OR_EQUAL -> lNum >= rNum
                VarComparator.LESS_OR_EQUAL -> lNum <= rNum
            }
        }
        return when (comparator) {
            VarComparator.EQUALS -> leftRaw == rightRaw
            VarComparator.NOT_EQUALS -> leftRaw != rightRaw
            VarComparator.GREATER_THAN -> leftRaw > rightRaw
            VarComparator.LESS_THAN -> leftRaw < rightRaw
            VarComparator.GREATER_OR_EQUAL -> leftRaw >= rightRaw
            VarComparator.LESS_OR_EQUAL -> leftRaw <= rightRaw
        }
    }
}
