package com.example.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

sealed interface ScriptClipboardItem {
    val summaryLabel: String

    data class SingleStep(val step: MacroStep) : ScriptClipboardItem {
        override val summaryLabel: String
            get() = "Step: ${step.label}"
    }

    data class FunctionClip(val function: MacroFunction) : ScriptClipboardItem {
        override val summaryLabel: String
            get() = "fn ${function.name} (${function.steps.size} steps)"
    }
}

class ScriptClipboard {
    private val _current = MutableStateFlow<ScriptClipboardItem?>(null)
    val current: StateFlow<ScriptClipboardItem?> = _current.asStateFlow()

    fun copyStep(step: MacroStep) {
        _current.value = ScriptClipboardItem.SingleStep(step)
    }

    fun copyFunction(function: MacroFunction) {
        _current.value = ScriptClipboardItem.FunctionClip(function)
    }

    fun clear() {
        _current.value = null
    }
}

object MacroCloner {
    fun cloneStep(step: MacroStep, newId: String = UUID.randomUUID().toString()): MacroStep =
        cloneStepsWithRemappedIds(listOf(step), mapOf(step.id to newId)).first()

    fun cloneStepsWithRemappedIds(
        steps: List<MacroStep>,
        presetIdMap: Map<String, String> = emptyMap()
    ): List<MacroStep> {
        val idMap = steps.associate { it.id to (presetIdMap[it.id] ?: UUID.randomUUID().toString()) }
        fun remap(targetId: String?): String? = targetId?.let { idMap[it] ?: it }

        return steps.map { step ->
            val freshId = idMap[step.id] ?: UUID.randomUUID().toString()
            when (step) {
                is TapStep -> step.copy(id = freshId)
                is SwipeStep -> step.copy(id = freshId)
                is DelayStep -> step.copy(id = freshId)
                is KeyPressStep -> step.copy(id = freshId)
                is SetVariableStep -> step.copy(id = freshId)
                is LoopStartStep -> step.copy(id = freshId)
                is LoopEndStep -> step.copy(
                    id = freshId,
                    loopStartId = idMap[step.loopStartId] ?: step.loopStartId
                )
                is IfColorStep -> step.copy(
                    id = freshId,
                    thenJumpStepId = remap(step.thenJumpStepId),
                    elseJumpStepId = remap(step.elseJumpStepId)
                )
                is IfImageStep -> step.copy(
                    id = freshId,
                    thenJumpStepId = remap(step.thenJumpStepId),
                    elseJumpStepId = remap(step.elseJumpStepId)
                )
                is IfVariableStep -> step.copy(
                    id = freshId,
                    thenJumpStepId = remap(step.thenJumpStepId),
                    elseJumpStepId = remap(step.elseJumpStepId)
                )
                is JumpStep -> step.copy(
                    id = freshId,
                    targetStepId = idMap[step.targetStepId] ?: step.targetStepId
                )
                is CallFunctionStep -> step.copy(id = freshId)
                is StopStep -> step.copy(id = freshId)
            }
        }
    }

    fun cloneFunction(
        function: MacroFunction,
        existingNames: Set<String> = emptySet()
    ): MacroFunction {
        val uniqueName = generateUniqueName(function.name, existingNames)
        return function.copy(
            id = UUID.randomUUID().toString(),
            name = uniqueName,
            steps = cloneStepsWithRemappedIds(function.steps)
        )
    }

    fun generateUniqueName(baseName: String, existingNames: Set<String>): String {
        val clean = baseName.trim().ifBlank { "Function" }
        if (clean !in existingNames) return clean
        val copyBase = if (clean.endsWith(" (Copy)")) clean else "$clean (Copy)"
        if (copyBase !in existingNames) return copyBase
        var index = 2
        while ("$clean ($index)" in existingNames) {
            index++
        }
        return "$clean ($index)"
    }
}
