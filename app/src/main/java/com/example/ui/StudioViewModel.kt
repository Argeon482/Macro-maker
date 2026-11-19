package com.example.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ShizuMacroApp
import com.example.data.ExecutionLogEntity
import com.example.data.LogLevel
import com.example.data.TemplateAssetEntity
import com.example.engine.RuntimeExecutionState
import com.example.model.BranchAction
import com.example.model.DelayStep
import com.example.model.IfColorStep
import com.example.model.IfImageStep
import com.example.model.IfVariableStep
import com.example.model.InputInjectionMode
import com.example.model.KeyboardKeyCatalog
import com.example.model.LoopEndStep
import com.example.model.LoopStartStep
import com.example.model.MacroDefinition
import com.example.model.MacroStep
import com.example.model.SetVariableStep
import com.example.model.SwipeStep
import com.example.model.TapStep
import com.example.model.VarComparator
import com.example.model.VarOperation
import com.example.service.MacroOverlayService
import com.example.service.OverlayUiState
import com.example.service.PickerMode
import com.example.service.PickerRequest
import com.example.service.PickerResult
import com.example.shizuku.HardwareInputDiagnostics
import com.example.shizuku.ShizukuStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

sealed interface UiNotice {
    data class Message(val text: String, val isError: Boolean = false) : UiNotice
}

class StudioViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ShizuMacroApp
    private val repository = app.repository
    val shizukuBridge = app.shizukuBridge
    val screenDetector = app.screenDetector
    val interpreter = app.interpreter
    val overlayCoordinator = app.overlayCoordinator
    val scriptClipboard = app.scriptClipboard

    val macros: StateFlow<List<MacroDefinition>> = repository.macros
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val templates: StateFlow<List<TemplateAssetEntity>> = repository.templates
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentLogs: StateFlow<List<ExecutionLogEntity>> = repository.recentLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val shizukuStatus: StateFlow<ShizukuStatus> = shizukuBridge.status
    val hardwareDiagnostics: StateFlow<HardwareInputDiagnostics> = shizukuBridge.diagnostics
    val screenCaptureActive: StateFlow<Boolean> = screenDetector.isActive
    val executionState: StateFlow<RuntimeExecutionState> = interpreter.state
    val overlayState: StateFlow<OverlayUiState> = overlayCoordinator.overlayState
    val clipboardItem = scriptClipboard.current

    private val _hasOverlayPermission = MutableStateFlow(canDrawOverlays())
    val hasOverlayPermission: StateFlow<Boolean> = _hasOverlayPermission.asStateFlow()

    private val _notices = MutableSharedFlow<UiNotice>(extraBufferCapacity = 8)
    val notices: SharedFlow<UiNotice> = _notices.asSharedFlow()

    fun notifyMessage(text: String, isError: Boolean = false) {
        _notices.tryEmit(UiNotice.Message(text, isError))
    }

    init {
        viewModelScope.launch {
            overlayCoordinator.pickerResults.collect { result ->
                applyPickerResult(result)
            }
        }
    }

    fun refreshDiagnostics() {
        _hasOverlayPermission.value = canDrawOverlays()
        shizukuBridge.refreshStatus()
        if (shizukuBridge.hasPermission()) {
            shizukuBridge.bindService()
            shizukuBridge.refreshCapabilities()
        }
    }

    fun refreshHardwareCapabilities() {
        shizukuBridge.refreshCapabilities()
    }

    fun updateCooperativeFingerState(
        isDown: Boolean,
        screenX: Float,
        screenY: Float,
        downTime: Long,
        deviceId: Int,
        pointerId: Int = 0
    ) {
        shizukuBridge.updateCooperativeFingerState(isDown, screenX, screenY, downTime, deviceId, pointerId)
    }

    fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(app)

    fun createNewMacro(name: String, targetApp: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val cleanName = name.trim().ifBlank { "Untitled Macro" }
            val macro = MacroDefinition(
                id = UUID.randomUUID().toString(),
                name = cleanName,
                targetAppLabel = targetApp.trim().ifBlank { "Any Screen" },
                steps = listOf(
                    TapStep(label = "Initial Tap", x = 540f, y = 960f),
                    DelayStep(label = "Cooldown", minMs = 450L, maxMs = 650L)
                )
            )
            repository.saveMacro(macro)
            onCreated(macro.id)
        }
    }

    fun createSandboxDemoMacro(onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val loopId = UUID.randomUUID().toString()
            val macro = MacroDefinition(
                id = UUID.randomUUID().toString(),
                name = "Sandbox Loop & Color Flow",
                description = "Demonstrates variable increments, conditional branching, and non-colliding taps in the Touch Sandbox.",
                targetAppLabel = "Touch Sandbox",
                loopCount = 1,
                pollingIntervalMs = 200L,
                steps = listOf(
                    SetVariableStep(label = "Init Counter", variableName = "hits", operation = VarOperation.SET, valueExpression = "0"),
                    LoopStartStep(id = loopId, label = "Repeat 3x", iterations = 3),
                    TapStep(label = "Tap Sandbox Target", x = 540f, y = 1050f, holdMs = 45L, jitterPx = 12),
                    SetVariableStep(label = "Increment Counter", variableName = "hits", operation = VarOperation.INCREMENT, valueExpression = "1"),
                    DelayStep(label = "Pace Interval", minMs = 350L, maxMs = 500L),
                    LoopEndStep(label = "End Loop", loopStartId = loopId),
                    IfVariableStep(
                        label = "Verify Hit Count",
                        variableName = "hits",
                        comparator = VarComparator.GREATER_OR_EQUAL,
                        compareValue = "3",
                        thenAction = BranchAction.CONTINUE_NEXT,
                        elseAction = BranchAction.STOP_MACRO
                    ),
                    SwipeStep(label = "Sweep Gesture", startX = 280f, startY = 1250f, endX = 800f, endY = 1250f, durationMs = 280L)
                )
            )
            repository.saveMacro(macro)
            _notices.tryEmit(UiNotice.Message("Created demo flow '${macro.name}'"))
            onCreated(macro.id)
        }
    }

    fun observeMacro(macroId: String) = repository.observeMacro(macroId)

    fun saveMacro(macro: MacroDefinition) {
        viewModelScope.launch {
            repository.saveMacro(macro)
        }
    }

    fun duplicateMacro(macroId: String) {
        viewModelScope.launch {
            val copy = repository.duplicateMacro(macroId)
            if (copy != null) {
                _notices.tryEmit(UiNotice.Message("Duplicated '${copy.name}'"))
            }
        }
    }

    fun deleteMacro(macroId: String) {
        viewModelScope.launch {
            if (executionState.value.activeMacroId == macroId) {
                interpreter.stop()
            }
            repository.deleteMacro(macroId)
            _notices.tryEmit(UiNotice.Message("Macro deleted"))
        }
    }

    fun runMacroDirectly(macro: MacroDefinition) {
        overlayCoordinator.setLoadedMacro(macro.id, macro.name)
        interpreter.start(macro)
    }

    fun launchFloatingDock(macro: MacroDefinition) {
        if (!canDrawOverlays()) {
            _notices.tryEmit(UiNotice.Message("Grant 'Display over other apps' first.", isError = true))
            return
        }
        overlayCoordinator.setLoadedMacro(macro.id, macro.name)
        val intent = Intent(app, MacroOverlayService::class.java).apply {
            action = MacroOverlayService.ACTION_SHOW_DOCK
            putExtra(MacroOverlayService.EXTRA_MACRO_ID, macro.id)
            putExtra(MacroOverlayService.EXTRA_MACRO_NAME, macro.name)
        }
        startForegroundServiceCompat(intent)
    }

    fun startMediaProjectionService(resultCode: Int, data: Intent) {
        val intent = Intent(app, MacroOverlayService::class.java).apply {
            action = MacroOverlayService.ACTION_START_PROJECTION
            putExtra(MacroOverlayService.EXTRA_RESULT_CODE, resultCode)
            putExtra(MacroOverlayService.EXTRA_RESULT_DATA, data)
        }
        startForegroundServiceCompat(intent)
        _notices.tryEmit(UiNotice.Message("Screen detection capture active"))
    }

    fun stopMediaProjection() {
        screenDetector.stop()
        _notices.tryEmit(UiNotice.Message("Screen capture stopped"))
    }

    fun startScreenPicker(
        macro: MacroDefinition,
        step: MacroStep,
        modeOverride: PickerMode? = null,
        functionId: String? = null
    ) {
        if (!canDrawOverlays()) {
            _notices.tryEmit(UiNotice.Message("Grant 'Display over other apps' to use the visual picker.", isError = true))
            return
        }
        val request = when (step) {
            is TapStep -> PickerRequest(
                macroId = macro.id,
                stepId = step.id,
                mode = modeOverride ?: PickerMode.PICK_TAP,
                functionId = functionId,
                initialX = step.x,
                initialY = step.y,
                returnToAppOnComplete = true
            )
            is SwipeStep -> PickerRequest(
                macroId = macro.id,
                stepId = step.id,
                mode = modeOverride ?: PickerMode.PICK_SWIPE,
                functionId = functionId,
                initialX = step.startX,
                initialY = step.startY,
                initialEndX = step.endX,
                initialEndY = step.endY,
                returnToAppOnComplete = true
            )
            is IfColorStep -> PickerRequest(
                macroId = macro.id,
                stepId = step.id,
                mode = modeOverride ?: PickerMode.PICK_COLOR,
                functionId = functionId,
                initialX = step.x.toFloat(),
                initialY = step.y.toFloat(),
                returnToAppOnComplete = true
            )
            is IfImageStep -> PickerRequest(
                macroId = macro.id,
                stepId = step.id,
                mode = modeOverride ?: PickerMode.PICK_TEMPLATE_CROP,
                functionId = functionId,
                initialLeft = step.searchLeft,
                initialTop = step.searchTop,
                initialRight = step.searchRight,
                initialBottom = step.searchBottom,
                returnToAppOnComplete = true
            )
            else -> return
        }
        overlayCoordinator.setLoadedMacro(macro.id, macro.name)
        overlayCoordinator.startPicker(request)
        val intent = Intent(app, MacroOverlayService::class.java).apply {
            action = MacroOverlayService.ACTION_START_PICKER
        }
        startForegroundServiceCompat(intent)
    }

    fun testShizukuTapInSandbox(
        x: Float,
        y: Float,
        mode: InputInjectionMode = InputInjectionMode.TOUCHSCREEN
    ) {
        viewModelScope.launch {
            val ok = shizukuBridge.tap(x, y, holdMs = 45L, jitterPx = 0, mode = mode)
            if (ok) {
                repository.appendLog(
                    "sandbox",
                    "Touch Sandbox",
                    "Shizuku Test",
                    "Injected ${mode.displayLabel} tap at (${x.toInt()}, ${y.toInt()})",
                    LogLevel.SUCCESS
                )
            } else if (!shizukuStatus.value.userServiceReady) {
                _notices.tryEmit(UiNotice.Message("Shizuku is not connected. Start Shizuku & grant permission to inject system events.", isError = true))
            }
        }
    }

    fun testShizukuSwipeInSandbox(
        sx: Float,
        sy: Float,
        ex: Float,
        ey: Float,
        mode: InputInjectionMode = InputInjectionMode.TOUCHSCREEN
    ) {
        viewModelScope.launch {
            val ok = shizukuBridge.swipe(sx, sy, ex, ey, durationMs = 280L, steps = 16, mode = mode)
            if (ok) {
                repository.appendLog(
                    "sandbox",
                    "Touch Sandbox",
                    "Shizuku Test",
                    "Injected ${mode.displayLabel} swipe (${sx.toInt()},${sy.toInt()})->(${ex.toInt()},${ey.toInt()})",
                    LogLevel.SUCCESS
                )
            } else if (!shizukuStatus.value.userServiceReady) {
                _notices.tryEmit(UiNotice.Message("Shizuku is not connected.", isError = true))
            }
        }
    }

    fun testShizukuKeyInSandbox(
        keyCode: Int,
        holdMs: Long = 35L,
        metaState: Int = 0
    ) {
        viewModelScope.launch {
            val ok = shizukuBridge.pressKey(keyCode, holdMs = holdMs, metaState = metaState)
            val binding = "${KeyboardKeyCatalog.formatMetaPrefix(metaState)}${KeyboardKeyCatalog.labelForKeyCode(keyCode)}"
            if (ok) {
                repository.appendLog(
                    "sandbox",
                    "Touch Sandbox",
                    "Key Test",
                    "Injected Key $binding (${holdMs}ms)",
                    LogLevel.SUCCESS
                )
            } else if (!shizukuStatus.value.userServiceReady) {
                _notices.tryEmit(UiNotice.Message("Shizuku is not connected. Start Shizuku & grant permission first.", isError = true))
            }
        }
    }

    fun exportMacrosToUri(uri: Uri, macroIds: Set<String>? = null) {
        viewModelScope.launch {
            runCatching {
                val payload = repository.exportMacrosToJson(macroIds)
                withContext(Dispatchers.IO) {
                    app.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(payload.toByteArray(Charsets.UTF_8))
                    } ?: error("Unable to open destination file")
                }
            }.onSuccess {
                _notices.tryEmit(UiNotice.Message("Exported macro bundle to JSON"))
            }.onFailure { err ->
                _notices.tryEmit(UiNotice.Message("Export failed: ${err.message}", isError = true))
            }
        }
    }

    fun importMacrosFromUri(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val raw = withContext(Dispatchers.IO) {
                    app.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("Unable to read selected JSON file")
                }
                repository.importMacrosFromJson(raw).getOrThrow()
            }.onSuccess { count ->
                _notices.tryEmit(UiNotice.Message("Imported $count macro(s) from JSON"))
            }.onFailure { err ->
                _notices.tryEmit(UiNotice.Message("Invalid macro JSON: ${err.message}", isError = true))
            }
        }
    }

    fun cropTemplate(
        templateId: String,
        trimLeft: Int,
        trimTop: Int,
        trimRight: Int,
        trimBottom: Int,
        saveAsNew: Boolean,
        onCropped: (TemplateAssetEntity) -> Unit = {}
    ) {
        viewModelScope.launch {
            val result = repository.cropTemplate(
                templateId = templateId,
                trimLeft = trimLeft,
                trimTop = trimTop,
                trimRight = trimRight,
                trimBottom = trimBottom,
                saveAsNew = saveAsNew
            )
            if (result != null) {
                onCropped(result)
                val action = if (saveAsNew) "Saved cropped copy" else "Cropped"
                _notices.tryEmit(UiNotice.Message("$action '${result.name}' (${result.width}×${result.height} px)"))
            } else {
                _notices.tryEmit(UiNotice.Message("Unable to crop template image", isError = true))
            }
        }
    }

    fun deleteTemplate(id: String) {
        viewModelScope.launch {
            repository.deleteTemplate(id)
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            repository.clearLogs()
        }
    }

    private fun applyPickerResult(result: PickerResult) {
        val msg = when (result) {
            is PickerResult.TapPicked -> if (result.stepId.isBlank()) {
                "Added tap at (${result.x.toInt()}, ${result.y.toInt()})"
            } else {
                "Updated tap to (${result.x.toInt()}, ${result.y.toInt()})"
            }
            is PickerResult.SwipePicked -> "Updated swipe gesture from screen picker"
            is PickerResult.ColorPicked -> "Sampled ${result.hexColor} at (${result.x}, ${result.y})"
            is PickerResult.TemplatePicked -> "Captured template '${result.templateName}'"
            is PickerResult.RoiPicked -> "Updated search area (${result.region.left},${result.region.top})–(${result.region.right},${result.region.bottom})"
        }
        _notices.tryEmit(UiNotice.Message(msg))
    }

    private fun startForegroundServiceCompat(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            app.startForegroundService(intent)
        } else {
            app.startService(intent)
        }
    }
}
