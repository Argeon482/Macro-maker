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
    MOUSE("Mouse", "Mouse pointer click (ACTION_DOWN/UP)"),
    MOUSE_HOVER_CLICK("Hover Click", "No-DOWN generic button click (tests TouchState bypass)"),
    CO_TOUCH_MULTIPLEX("Co-Touch", "Injects Pointer 1 alongside live Finger Slot 0 on physical device"),
    STYLUS("Stylus", "Separate pen stream (Android 13+ / pen apps)"),
    TOUCHSCREEN("Touch", "Standard finger touch stream")
}

@Serializable
enum class TouchGuardPolicy(val displayLabel: String, val cooldownMs: Long, val shortDesc: String) {
    OFF("Off", 0L, "Inject immediately without waiting for finger activity"),
    YIELD_350MS("Yield 350ms", 350L, "Pause macro taps briefly when external user touch starts"),
    YIELD_800MS("Yield 800ms", 800L, "Yield longer (800ms) during active manual touch bursts")
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
    override val label: String = "System Key",
    override val enabled: Boolean = true,
    val keyAction: SystemKeyAction = SystemKeyAction.BACK
) : MacroStep

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
@SerialName("stop")
data class StopStep(
    override val id: String = UUID.randomUUID().toString(),
    override val label: String = "Stop Macro",
    override val enabled: Boolean = true
) : MacroStep

@Serializable
data class MacroDefinition(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val targetAppLabel: String = "Any Screen",
    val loopCount: Int = 1,
    val pollingIntervalMs: Long = 250L,
    val injectionMode: InputInjectionMode = InputInjectionMode.MOUSE,
    val touchGuardPolicy: TouchGuardPolicy = TouchGuardPolicy.OFF,
    val steps: List<MacroStep> = emptyList(),
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
