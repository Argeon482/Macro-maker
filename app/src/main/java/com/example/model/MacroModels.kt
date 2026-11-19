package com.example.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class VarOperation {
    SET,
    INCREMENT,
    DECREMENT
}

@Serializable
enum class VarComparator {
    EQUALS,
    NOT_EQUALS,
    GREATER_THAN,
    LESS_THAN,
    GREATER_OR_EQUAL,
    LESS_OR_EQUAL
}

@Serializable
enum class BranchAction {
    CONTINUE_NEXT,
    SKIP_NEXT,
    JUMP_TO_STEP,
    BREAK_LOOP,
    STOP_MACRO
}

@Serializable
enum class SystemKeyAction(val keyCode: Int, val displayLabel: String) {
    BACK(android.view.KeyEvent.KEYCODE_BACK, "Back"),
    HOME(android.view.KeyEvent.KEYCODE_HOME, "Home"),
    RECENTS(android.view.KeyEvent.KEYCODE_APP_SWITCH, "Recents")
}

@Serializable
enum class InputInjectionMode(val displayLabel: String, val shortDesc: String) {
    TOUCHSCREEN("Touch", "Standard finger touch stream (pair with Touch Guard or Keyboard steps)"),
    CO_TOUCH_MULTIPLEX("Co-Touch", "Kernel UHID / evdev touch stream alongside physical finger"),
    STYLUS("Stylus", "Separate pen stream (Android 13+ / pen apps)")
}

@Serializable
enum class TouchGuardPolicy(val displayLabel: String, val cooldownMs: Long, val shortDesc: String) {
    OFF("Off", 0L, "Inject immediately without waiting for finger activity"),
    LIVE_RELEASE("Live Hold", 60L, "Holds while physical finger touches screen (getevent) and fires on lift"),
    YIELD_350MS("Yield 350ms", 350L, "Holds while finger is active plus 350ms release cooldown"),
    YIELD_800MS("Yield 800ms", 800L, "Holds while finger is active plus 800ms release cooldown")
}

@Serializable
sealed interface MacroStep {
    val id: String
    val label: String
    val enabled: Boolean
}

@Serializable
@SerialName("tap")
data class TapStep(
    override val id: String = UUID.randomUUID().toString(),
    override val label: String = "Tap",
    override val enabled: Boolean = true,
    val x: Float = 540f,
    val y: Float = 960f,
    val holdMs: Long = 40L,
    val jitterPx: Int = 4
) : MacroStep

@Serializable
@SerialName("swipe")
data class SwipeStep(
    override val id: String = UUID.randomUUID().toString(),
    override val label: String = "Swipe",
    override val enabled: Boolean = true,
    val startX: Float = 540f,
    val startY: Float = 1400f,
    val endX: Float = 540f,
    val endY: Float = 600f,
    val durationMs: Long = 320L,
    val interpolateSteps: Int = 18
) : MacroStep

@Serializable
@SerialName("delay")
data class DelayStep(
    override val id: String = UUID.randomUUID().toString(),
    override val label: String = "Wait",
    override val enabled: Boolean = true,
    val minMs: Long = 500L,
    val maxMs: Long = 500L
) : MacroStep

@Serializable
@SerialName("key")
data class KeyPressStep(
    override val id: String = UUID.randomUUID().toString(),
    override val label: String = "Key Press",
    override val enabled: Boolean = true,
    val keyAction: SystemKeyAction = SystemKeyAction.BACK,
    val customKeyCode: Int? = null,
    val keyLabel: String = "",
    val holdMs: Long = 35L,
    val metaState: Int = 0
) : MacroStep {
    fun resolvedKeyCode(): Int = customKeyCode ?: keyAction.keyCode

    fun resolvedKeyLabel(): String = when {
        keyLabel.isNotBlank() -> keyLabel
        customKeyCode != null -> KeyboardKeyCatalog.labelForKeyCode(customKeyCode)
        else -> keyAction.displayLabel
    }

    fun formattedBinding(): String =
        "${KeyboardKeyCatalog.formatMetaPrefix(metaState)}${resolvedKeyLabel()}"
}

@Serializable
@SerialName("set_var")
data class SetVariableStep(
    override val id: String = UUID.randomUUID().toString(),
    override val label: String = "Set Variable",
    override val enabled: Boolean = true,
    val variableName: String = "counter",
    val operation: VarOperation = VarOperation.SET,
    val valueExpression: String = "0"
) : MacroStep

@Serializable
@SerialName("loop_start")
data class LoopStartStep(
    override val id: String = UUID.randomUUID().toString(),
    override val label: String = "Loop Start",
    override val enabled: Boolean = true,
    val iterations: Int = 3,
    val conditionVar: String = "",
    val conditionOp: VarComparator = VarComparator.LESS_THAN,
    val conditionValue: String = ""
) : MacroStep

@Serializable
@SerialName("loop_end")
data class LoopEndStep(
    override val id: String = UUID.randomUUID().toString(),
    override val label: String = "Loop End",
    override val enabled: Boolean = true,
    val loopStartId: String = ""
) : MacroStep

@Serializable
@SerialName("if_color")
data class IfColorStep(
    override val id: String = UUID.randomUUID().toString(),
    override val label: String = "If Pixel Color",
    override val enabled: Boolean = true,
    val x: Int = 540,
    val y: Int = 960,
    val targetHexColor: String = "#10B981",
    val tolerancePct: Int = 10,
    val tapOnMatch: Boolean = false,
    val thenAction: BranchAction = BranchAction.CONTINUE_NEXT,
    val thenJumpStepId: String? = null,
    val elseAction: BranchAction = BranchAction.SKIP_NEXT,
    val elseJumpStepId: String? = null
) : MacroStep

@Serializable
@SerialName("if_image")
data class IfImageStep(
    override val id: String = UUID.randomUUID().toString(),
    override val label: String = "If Image Match",
    override val enabled: Boolean = true,
    val templateId: String = "",
    val templateName: String = "Target Template",
    val searchLeft: Int = 0,
    val searchTop: Int = 0,
    val searchRight: Int = 0,
    val searchBottom: Int = 0,
    val minConfidencePct: Int = 85,
    val tapOnMatch: Boolean = true,
    val thenAction: BranchAction = BranchAction.CONTINUE_NEXT,
    val thenJumpStepId: String? = null,
    val elseAction: BranchAction = BranchAction.SKIP_NEXT,
    val elseJumpStepId: String? = null
) : MacroStep

@Serializable
@SerialName("if_var")
data class IfVariableStep(
    override val id: String = UUID.randomUUID().toString(),
    override val label: String = "If Variable",
    override val enabled: Boolean = true,
    val variableName: String = "counter",
    val comparator: VarComparator = VarComparator.GREATER_OR_EQUAL,
    val compareValue: String = "5",
    val thenAction: BranchAction = BranchAction.CONTINUE_NEXT,
    val thenJumpStepId: String? = null,
    val elseAction: BranchAction = BranchAction.SKIP_NEXT,
    val elseJumpStepId: String? = null
) : MacroStep

@Serializable
@SerialName("jump")
data class JumpStep(
    override val id: String = UUID.randomUUID().toString(),
    override val label: String = "Jump To Step",
    override val enabled: Boolean = true,
    val targetStepId: String = ""
) : MacroStep

@Serializable
@SerialName("call_fn")
data class CallFunctionStep(
    override val id: String = UUID.randomUUID().toString(),
    override val label: String = "Call Function",
    override val enabled: Boolean = true,
    val functionId: String = "",
    val functionName: String = "",
    val repeatCount: Int = 1
) : MacroStep

@Serializable
@SerialName("stop")
data class StopStep(
    override val id: String = UUID.randomUUID().toString(),
    override val label: String = "Stop Macro",
    override val enabled: Boolean = true
) : MacroStep

@Serializable
data class MacroFunction(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Function",
    val description: String = "",
    val steps: List<MacroStep> = emptyList()
)

@Serializable
data class MacroDefinition(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val targetAppLabel: String = "Any Screen",
    val loopCount: Int = 1,
    val pollingIntervalMs: Long = 250L,
    val injectionMode: InputInjectionMode = InputInjectionMode.TOUCHSCREEN,
    val touchGuardPolicy: TouchGuardPolicy = TouchGuardPolicy.OFF,
    val steps: List<MacroStep> = emptyList(),
    val functions: List<MacroFunction> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
data class EmbeddedTemplate(
    val id: String,
    val name: String,
    val width: Int,
    val height: Int,
    val pngBase64: String
)

@Serializable
data class MacroExportBundle(
    val schemaVersion: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val macros: List<MacroDefinition>,
    val embeddedTemplates: List<EmbeddedTemplate> = emptyList()
)

fun MacroStep.withEnabled(enabled: Boolean): MacroStep = when (this) {
    is TapStep -> copy(enabled = enabled)
    is SwipeStep -> copy(enabled = enabled)
    is DelayStep -> copy(enabled = enabled)
    is KeyPressStep -> copy(enabled = enabled)
    is SetVariableStep -> copy(enabled = enabled)
    is LoopStartStep -> copy(enabled = enabled)
    is LoopEndStep -> copy(enabled = enabled)
    is IfColorStep -> copy(enabled = enabled)
    is IfImageStep -> copy(enabled = enabled)
    is IfVariableStep -> copy(enabled = enabled)
    is JumpStep -> copy(enabled = enabled)
    is CallFunctionStep -> copy(enabled = enabled)
    is StopStep -> copy(enabled = enabled)
}
