package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import android.view.KeyEvent
import com.example.data.TemplateAssetEntity
import com.example.model.BranchAction
import com.example.model.CallFunctionStep
import com.example.model.DelayStep
import com.example.model.IfColorStep
import com.example.model.IfImageStep
import com.example.model.IfVariableStep
import com.example.model.JumpStep
import com.example.model.KeyCategory
import com.example.model.KeyPressStep
import com.example.model.KeyboardKeyCatalog
import com.example.model.LoopEndStep
import com.example.model.LoopStartStep
import com.example.model.MacroFunction
import com.example.model.MacroStep
import com.example.model.SetVariableStep
import com.example.model.StopStep
import com.example.model.SwipeStep
import com.example.model.SystemKeyAction
import com.example.model.TapStep
import com.example.model.VarComparator
import com.example.model.VarOperation
import com.example.service.PickerMode
import com.example.ui.components.TemplateCropEditorDialog
import com.example.ui.components.TemplateThumbnailPreview
import com.example.ui.theme.AmberLogic
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldRun
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.RoseAlert
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.SlateWell
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletVar
import com.example.vision.ScreenDetector
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddStepBottomSheet(
    functions: List<MacroFunction> = emptyList(),
    onDismiss: () -> Unit,
    onAddSteps: (List<MacroStep>) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SlateSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Add Automation Node", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)

            StepCategorySection("TOUCH & INPUT", ElectricCyan) {
                AddNodeTile("Tap Coordinate", "Inject non-colliding single tap", Icons.Default.TouchApp, ElectricCyan) {
                    onAddSteps(listOf(TapStep()))
                }
                AddNodeTile("Swipe Gesture", "Interpolated drag from A to B", Icons.Default.Gesture, ElectricCyan) {
                    onAddSteps(listOf(SwipeStep()))
                }
                AddNodeTile("Wait / Delay", "Fixed or randomized pause (ms)", Icons.Default.HourglassBottom, ElectricCyan) {
                    onAddSteps(listOf(DelayStep()))
                }
                AddNodeTile(
                    "Key Press (Keyboard / Game)",
                    "Send any keyboard, game, or system key (no touch collision)",
                    Icons.Default.Keyboard,
                    ElectricCyan
                ) {
                    onAddSteps(
                        listOf(
                            KeyPressStep(
                                label = "Key Space",
                                customKeyCode = KeyEvent.KEYCODE_SPACE,
                                keyLabel = "Space",
                                holdMs = 35L
                            )
                        )
                    )
                }
            }

            StepCategorySection("SCREEN VISION TRIGGERS", EmeraldRun) {
                AddNodeTile("If Pixel Color", "Check RGB color & tolerance at (x, y)", Icons.Default.Colorize, EmeraldRun) {
                    onAddSteps(listOf(IfColorStep()))
                }
                AddNodeTile("If Image Match", "Crop screen region & match template", Icons.Default.CropFree, EmeraldRun) {
                    onAddSteps(listOf(IfImageStep()))
                }
            }

            StepCategorySection("LOGIC, FUNCTIONS & LOOPS", AmberLogic) {
                AddNodeTile(
                    "Call Function",
                    if (functions.isEmpty()) {
                        "Run a reusable chunk of steps (create one via + New Fn)"
                    } else {
                        "Run one of ${functions.size} function chunk(s) (${functions.joinToString(", ") { it.name }.take(36)})"
                    },
                    Icons.Default.Code,
                    VioletVar
                ) {
                    val firstFn = functions.firstOrNull()
                    onAddSteps(
                        listOf(
                            CallFunctionStep(
                                label = firstFn?.let { "Call ${it.name}" } ?: "Call Function",
                                functionId = firstFn?.id ?: "",
                                functionName = firstFn?.name ?: "",
                                repeatCount = 1
                            )
                        )
                    )
                }
                AddNodeTile("Loop Block (Start + End)", "Repeat N times or while condition holds", Icons.Default.Loop, AmberLogic) {
                    val startId = UUID.randomUUID().toString()
                    onAddSteps(
                        listOf(
                            LoopStartStep(id = startId, label = "Loop 3x", iterations = 3),
                            LoopEndStep(label = "End Loop", loopStartId = startId)
                        )
                    )
                }
                AddNodeTile("Set Variable", "Assign, increment, or decrement state", Icons.Default.Functions, VioletVar) {
                    onAddSteps(listOf(SetVariableStep()))
                }
                AddNodeTile("If Variable", "Branch based on numeric/text compare", Icons.Default.CallSplit, AmberLogic) {
                    onAddSteps(listOf(IfVariableStep()))
                }
                AddNodeTile("Jump To Step", "Transfer flow to another step", Icons.Default.Redo, AmberLogic) {
                    onAddSteps(listOf(JumpStep()))
                }
                AddNodeTile("Stop Macro", "Terminate macro run cleanly", Icons.Default.StopCircle, RoseAlert) {
                    onAddSteps(listOf(StopStep()))
                }
            }
        }
    }
}

@Composable
private fun StepCategorySection(
    title: String,
    accent: Color,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = accent)
        content()
    }
}

@Composable
private fun AddNodeTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = SlateWell,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, HairlineBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditStepBottomSheet(
    step: MacroStep,
    allSteps: List<MacroStep>,
    functions: List<MacroFunction> = emptyList(),
    templates: List<TemplateAssetEntity>,
    onPickOnScreen: (MacroStep, PickerMode) -> Unit,
    onCropTemplate: (
        templateId: String,
        trimLeft: Int,
        trimTop: Int,
        trimRight: Int,
        trimBottom: Int,
        saveAsNew: Boolean,
        onCropped: (TemplateAssetEntity) -> Unit
    ) -> Unit = { _, _, _, _, _, _, _ -> },
    onSave: (MacroStep) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var label by remember(step) { mutableStateOf(step.label) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SlateSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Edit Step", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)

            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Step Label") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            when (step) {
                is TapStep -> {
                    var xStr by remember(step) { mutableStateOf(step.x.toInt().toString()) }
                    var yStr by remember(step) { mutableStateOf(step.y.toInt().toString()) }
                    var holdStr by remember(step) { mutableStateOf(step.holdMs.toString()) }
                    var jitterStr by remember(step) { mutableStateOf(step.jitterPx.toString()) }

                    OutlinedButton(
                        onClick = {
                            val current = step.copy(
                                label = label,
                                x = xStr.toFloatOrNull() ?: step.x,
                                y = yStr.toFloatOrNull() ?: step.y
                            )
                            onSave(current)
                            onPickOnScreen(current, PickerMode.PICK_TAP)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("pick_tap_on_screen_btn")
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = null, tint = ElectricCyan)
                        Spacer(Modifier.width(8.dp))
                        Text("Pick Tap Coordinate on Screen", color = ElectricCyan)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumField("X (px)", xStr, { xStr = it }, Modifier.weight(1f))
                        NumField("Y (px)", yStr, { yStr = it }, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumField("Hold (ms)", holdStr, { holdStr = it }, Modifier.weight(1f))
                        NumField("Jitter (±px)", jitterStr, { jitterStr = it }, Modifier.weight(1f))
                    }

                    SaveStepActions(onDismiss) {
                        onSave(
                            step.copy(
                                label = label.ifBlank { "Tap" },
                                x = xStr.toFloatOrNull() ?: step.x,
                                y = yStr.toFloatOrNull() ?: step.y,
                                holdMs = (holdStr.toLongOrNull() ?: step.holdMs).coerceAtLeast(10L),
                                jitterPx = (jitterStr.toIntOrNull() ?: step.jitterPx).coerceAtLeast(0)
                            )
                        )
                    }
                }

                is SwipeStep -> {
                    var sx by remember(step) { mutableStateOf(step.startX.toInt().toString()) }
                    var sy by remember(step) { mutableStateOf(step.startY.toInt().toString()) }
                    var ex by remember(step) { mutableStateOf(step.endX.toInt().toString()) }
                    var ey by remember(step) { mutableStateOf(step.endY.toInt().toString()) }
                    var dur by remember(step) { mutableStateOf(step.durationMs.toString()) }

                    OutlinedButton(
                        onClick = {
                            val current = step.copy(label = label)
                            onSave(current)
                            onPickOnScreen(current, PickerMode.PICK_SWIPE)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.Gesture, contentDescription = null, tint = AmberLogic)
                        Spacer(Modifier.width(8.dp))
                        Text("Pick Start & End on Screen", color = AmberLogic)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumField("Start X", sx, { sx = it }, Modifier.weight(1f))
                        NumField("Start Y", sy, { sy = it }, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumField("End X", ex, { ex = it }, Modifier.weight(1f))
                        NumField("End Y", ey, { ey = it }, Modifier.weight(1f))
                    }
                    NumField("Duration (ms)", dur, { dur = it }, Modifier.fillMaxWidth())

                    SaveStepActions(onDismiss) {
                        onSave(
                            step.copy(
                                label = label.ifBlank { "Swipe" },
                                startX = sx.toFloatOrNull() ?: step.startX,
                                startY = sy.toFloatOrNull() ?: step.startY,
                                endX = ex.toFloatOrNull() ?: step.endX,
                                endY = ey.toFloatOrNull() ?: step.endY,
                                durationMs = (dur.toLongOrNull() ?: step.durationMs).coerceAtLeast(60L)
                            )
                        )
                    }
                }

                is DelayStep -> {
                    var minStr by remember(step) { mutableStateOf(step.minMs.toString()) }
                    var maxStr by remember(step) { mutableStateOf(step.maxMs.toString()) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumField("Min Delay (ms)", minStr, { minStr = it }, Modifier.weight(1f))
                        NumField("Max Delay (ms)", maxStr, { maxStr = it }, Modifier.weight(1f))
                    }
                    SaveStepActions(onDismiss) {
                        val minV = (minStr.toLongOrNull() ?: step.minMs).coerceAtLeast(0L)
                        val maxV = (maxStr.toLongOrNull() ?: minV).coerceAtLeast(minV)
                        onSave(step.copy(label = label.ifBlank { "Wait" }, minMs = minV, maxMs = maxV))
                    }
                }

                is KeyPressStep -> {
                    var selectedCategory by remember(step) {
                        val code = step.resolvedKeyCode()
                        val matched = KeyboardKeyCatalog.allOptions.firstOrNull { it.keyCode == code }?.category
                            ?: KeyCategory.GAME_ACTION
                        mutableStateOf(matched)
                    }
                    var keyCode by remember(step) { mutableStateOf(step.resolvedKeyCode()) }
                    var keyCodeText by remember(step) { mutableStateOf(step.resolvedKeyCode().toString()) }
                    var keyLabel by remember(step) { mutableStateOf(step.resolvedKeyLabel()) }
                    var holdStr by remember(step) { mutableStateOf(step.holdMs.toString()) }
                    var useShift by remember(step) { mutableStateOf((step.metaState and KeyEvent.META_SHIFT_ON) != 0) }
                    var useCtrl by remember(step) { mutableStateOf((step.metaState and KeyEvent.META_CTRL_ON) != 0) }
                    var useAlt by remember(step) { mutableStateOf((step.metaState and KeyEvent.META_ALT_ON) != 0) }

                    Text("Key Category", style = MaterialTheme.typography.labelMedium, color = ElectricCyan)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        KeyCategory.entries.forEach { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat },
                                label = { Text(cat.title) },
                                modifier = Modifier.testTag("key_cat_${cat.name.lowercase()}")
                            )
                        }
                    }

                    Text(
                        text = "Selected Key: $keyLabel (Android KeyCode $keyCode)",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextPrimary
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        KeyboardKeyCatalog.optionsForCategory(selectedCategory).forEach { opt ->
                            FilterChip(
                                selected = keyCode == opt.keyCode,
                                onClick = {
                                    keyCode = opt.keyCode
                                    keyCodeText = opt.keyCode.toString()
                                    keyLabel = opt.label
                                    if (label.startsWith("Key ") || label == "Key Press" || label == "System Key") {
                                        label = "Key ${opt.label}"
                                    }
                                },
                                label = { Text(opt.label) },
                                modifier = Modifier.testTag("key_opt_${opt.keyCode}")
                            )
                        }
                    }

                    Text("Modifiers (Optional)", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = useShift, onClick = { useShift = !useShift }, label = { Text("Shift") })
                        FilterChip(selected = useCtrl, onClick = { useCtrl = !useCtrl }, label = { Text("Ctrl") })
                        FilterChip(selected = useAlt, onClick = { useAlt = !useAlt }, label = { Text("Alt") })
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumField("Hold (ms)", holdStr, { holdStr = it }, Modifier.weight(1f))
                        NumField(
                            "KeyCode",
                            keyCodeText,
                            { raw ->
                                keyCodeText = raw
                                raw.toIntOrNull()?.let { parsed ->
                                    keyCode = parsed
                                    keyLabel = KeyboardKeyCatalog.labelForKeyCode(parsed)
                                }
                            },
                            Modifier.weight(1f)
                        )
                    }

                    SaveStepActions(onDismiss) {
                        val meta = (if (useShift) KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON else 0) or
                            (if (useCtrl) KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON else 0) or
                            (if (useAlt) KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON else 0)
                        val matchedSys = SystemKeyAction.entries.firstOrNull { it.keyCode == keyCode } ?: step.keyAction
                        val resolvedLabel = keyLabel.ifBlank { KeyboardKeyCatalog.labelForKeyCode(keyCode) }
                        onSave(
                            step.copy(
                                label = label.ifBlank { "Key $resolvedLabel" },
                                keyAction = matchedSys,
                                customKeyCode = keyCode,
                                keyLabel = resolvedLabel,
                                holdMs = (holdStr.toLongOrNull() ?: step.holdMs).coerceIn(10L, 5000L),
                                metaState = meta
                            )
                        )
                    }
                }

                is SetVariableStep -> {
                    var varName by remember(step) { mutableStateOf(step.variableName) }
                    var op by remember(step) { mutableStateOf(step.operation) }
                    var expr by remember(step) { mutableStateOf(step.valueExpression) }

                    OutlinedTextField(
                        value = varName,
                        onValueChange = { varName = it },
                        label = { Text("Variable Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VarOperation.entries.forEach { candidate ->
                            FilterChip(
                                selected = op == candidate,
                                onClick = { op = candidate },
                                label = { Text(candidate.name) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = expr,
                        onValueChange = { expr = it },
                        label = { Text("Value / Delta") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    SaveStepActions(onDismiss) {
                        onSave(
                            step.copy(
                                label = label.ifBlank { "Set $varName" },
                                variableName = varName.trim().ifBlank { "counter" },
                                operation = op,
                                valueExpression = expr
                            )
                        )
                    }
                }

                is LoopStartStep -> {
                    var iterStr by remember(step) { mutableStateOf(step.iterations.toString()) }
                    var condVar by remember(step) { mutableStateOf(step.conditionVar) }
                    var condOp by remember(step) { mutableStateOf(step.conditionOp) }
                    var condVal by remember(step) { mutableStateOf(step.conditionValue) }

                    NumField("Repeat Count (0 = while condition)", iterStr, { iterStr = it }, Modifier.fillMaxWidth())
                    OutlinedTextField(
                        value = condVar,
                        onValueChange = { condVar = it },
                        label = { Text("Optional Guard Variable") },
                        placeholder = { Text("e.g. hits (leave blank for fixed count)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (condVar.isNotBlank()) {
                        ComparatorPicker(condOp) { condOp = it }
                        OutlinedTextField(
                            value = condVal,
                            onValueChange = { condVal = it },
                            label = { Text("Guard Compare Value") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    SaveStepActions(onDismiss) {
                        onSave(
                            step.copy(
                                label = label.ifBlank { "Loop" },
                                iterations = (iterStr.toIntOrNull() ?: step.iterations).coerceAtLeast(0),
                                conditionVar = condVar.trim(),
                                conditionOp = condOp,
                                conditionValue = condVal
                            )
                        )
                    }
                }

                is IfColorStep -> {
                    var xStr by remember(step) { mutableStateOf(step.x.toString()) }
                    var yStr by remember(step) { mutableStateOf(step.y.toString()) }
                    var hex by remember(step) { mutableStateOf(step.targetHexColor) }
                    var tol by remember(step) { mutableStateOf(step.tolerancePct.toString()) }
                    var tapOnMatch by remember(step) { mutableStateOf(step.tapOnMatch) }
                    var thenAction by remember(step) { mutableStateOf(step.thenAction) }
                    var thenJumpId by remember(step) { mutableStateOf(step.thenJumpStepId) }
                    var elseAction by remember(step) { mutableStateOf(step.elseAction) }
                    var elseJumpId by remember(step) { mutableStateOf(step.elseJumpStepId) }

                    OutlinedButton(
                        onClick = {
                            val current = step.copy(label = label)
                            onSave(current)
                            onPickOnScreen(current, PickerMode.PICK_COLOR)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.Colorize, contentDescription = null, tint = EmeraldRun)
                        Spacer(Modifier.width(8.dp))
                        Text("Pick Pixel & Color on Screen", color = EmeraldRun)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumField("X", xStr, { xStr = it }, Modifier.weight(1f))
                        NumField("Y", yStr, { yStr = it }, Modifier.weight(1f))
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(ScreenDetector.parseHexColor(hex)))
                                .border(1.dp, HairlineBorder, RoundedCornerShape(10.dp))
                        )
                        OutlinedTextField(
                            value = hex,
                            onValueChange = { hex = it },
                            label = { Text("Target Hex (#RRGGBB)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        NumField("Tol %", tol, { tol = it }, Modifier.width(90.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Tap coordinate when matched", color = TextPrimary)
                        Switch(checked = tapOnMatch, onCheckedChange = { tapOnMatch = it })
                    }

                    BranchSelector("IF MATCHED (THEN)", thenAction, thenJumpId, allSteps, EmeraldRun) { a, j ->
                        thenAction = a
                        thenJumpId = j
                    }
                    BranchSelector("IF NOT MATCHED (ELSE)", elseAction, elseJumpId, allSteps, AmberLogic) { a, j ->
                        elseAction = a
                        elseJumpId = j
                    }

                    SaveStepActions(onDismiss) {
                        onSave(
                            step.copy(
                                label = label.ifBlank { "If Color" },
                                x = xStr.toIntOrNull() ?: step.x,
                                y = yStr.toIntOrNull() ?: step.y,
                                targetHexColor = hex.trim().ifBlank { "#10B981" },
                                tolerancePct = (tol.toIntOrNull() ?: step.tolerancePct).coerceIn(0, 100),
                                tapOnMatch = tapOnMatch,
                                thenAction = thenAction,
                                thenJumpStepId = thenJumpId,
                                elseAction = elseAction,
                                elseJumpStepId = elseJumpId
                            )
                        )
                    }
                }

                is IfImageStep -> {
                    var selectedTemplateId by remember(step) { mutableStateOf(step.templateId) }
                    var selectedTemplateName by remember(step) { mutableStateOf(step.templateName) }
                    var showCropEditor by remember(step) { mutableStateOf(false) }
                    var sLeft by remember(step) { mutableStateOf(step.searchLeft) }
                    var sTop by remember(step) { mutableStateOf(step.searchTop) }
                    var sRight by remember(step) { mutableStateOf(step.searchRight) }
                    var sBottom by remember(step) { mutableStateOf(step.searchBottom) }
                    var confStr by remember(step) { mutableStateOf(step.minConfidencePct.toString()) }
                    var tapOnMatch by remember(step) { mutableStateOf(step.tapOnMatch) }
                    var thenAction by remember(step) { mutableStateOf(step.thenAction) }
                    var thenJumpId by remember(step) { mutableStateOf(step.thenJumpStepId) }
                    var elseAction by remember(step) { mutableStateOf(step.elseAction) }
                    var elseJumpId by remember(step) { mutableStateOf(step.elseJumpStepId) }

                    val selectedTemplate = templates.find { it.id == selectedTemplateId }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val current = step.copy(
                                    label = label,
                                    templateId = selectedTemplateId,
                                    templateName = selectedTemplateName,
                                    searchLeft = sLeft,
                                    searchTop = sTop,
                                    searchRight = sRight,
                                    searchBottom = sBottom
                                )
                                onSave(current)
                                onPickOnScreen(current, PickerMode.PICK_TEMPLATE_CROP)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Icon(Icons.Default.CropFree, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Crop Image", color = ElectricCyan)
                        }
                        OutlinedButton(
                            onClick = {
                                val current = step.copy(
                                    label = label,
                                    templateId = selectedTemplateId,
                                    templateName = selectedTemplateName,
                                    searchLeft = sLeft,
                                    searchTop = sTop,
                                    searchRight = sRight,
                                    searchBottom = sBottom
                                )
                                onSave(current)
                                onPickOnScreen(current, PickerMode.PICK_SEARCH_ROI)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Text("Search Area", color = EmeraldRun)
                        }
                    }

                    if (selectedTemplate != null) {
                        TemplateThumbnailPreview(
                            template = selectedTemplate,
                            onEditCrop = { showCropEditor = true }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (sRight > sLeft && sBottom > sTop) {
                                "ROI: ($sLeft,$sTop)–($sRight,$sBottom)"
                            } else {
                                "ROI: Full Screen"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                        if (sRight > sLeft && sBottom > sTop) {
                            OutlinedButton(
                                onClick = { sLeft = 0; sTop = 0; sRight = 0; sBottom = 0 },
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text("Reset ROI", color = TextSecondary)
                            }
                        }
                    }

                    if (templates.isNotEmpty()) {
                        Text("Saved Templates", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            templates.forEach { tpl ->
                                FilterChip(
                                    selected = selectedTemplateId == tpl.id,
                                    onClick = {
                                        selectedTemplateId = tpl.id
                                        selectedTemplateName = tpl.name
                                    },
                                    label = { Text("${tpl.name} (${tpl.width}x${tpl.height})") }
                                )
                            }
                        }
                    }

                    if (showCropEditor && selectedTemplate != null) {
                        TemplateCropEditorDialog(
                            template = selectedTemplate,
                            onApplyCrop = { l, t, r, b, saveAsNew ->
                                onCropTemplate(selectedTemplate.id, l, t, r, b, saveAsNew) { cropped ->
                                    selectedTemplateId = cropped.id
                                    selectedTemplateName = cropped.name
                                }
                                showCropEditor = false
                            },
                            onDismiss = { showCropEditor = false }
                        )
                    }

                    NumField("Min Confidence (%)", confStr, { confStr = it }, Modifier.fillMaxWidth())

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Tap matched center", color = TextPrimary)
                        Switch(checked = tapOnMatch, onCheckedChange = { tapOnMatch = it })
                    }

                    BranchSelector("IF MATCHED (THEN)", thenAction, thenJumpId, allSteps, EmeraldRun) { a, j ->
                        thenAction = a
                        thenJumpId = j
                    }
                    BranchSelector("IF NOT MATCHED (ELSE)", elseAction, elseJumpId, allSteps, AmberLogic) { a, j ->
                        elseAction = a
                        elseJumpId = j
                    }

                    SaveStepActions(onDismiss) {
                        onSave(
                            step.copy(
                                label = label.ifBlank { "If Image" },
                                templateId = selectedTemplateId,
                                templateName = selectedTemplateName,
                                searchLeft = sLeft,
                                searchTop = sTop,
                                searchRight = sRight,
                                searchBottom = sBottom,
                                minConfidencePct = (confStr.toIntOrNull() ?: step.minConfidencePct).coerceIn(40, 100),
                                tapOnMatch = tapOnMatch,
                                thenAction = thenAction,
                                thenJumpStepId = thenJumpId,
                                elseAction = elseAction,
                                elseJumpStepId = elseJumpId
                            )
                        )
                    }
                }

                is IfVariableStep -> {
                    var varName by remember(step) { mutableStateOf(step.variableName) }
                    var comp by remember(step) { mutableStateOf(step.comparator) }
                    var cmpVal by remember(step) { mutableStateOf(step.compareValue) }
                    var thenAction by remember(step) { mutableStateOf(step.thenAction) }
                    var thenJumpId by remember(step) { mutableStateOf(step.thenJumpStepId) }
                    var elseAction by remember(step) { mutableStateOf(step.elseAction) }
                    var elseJumpId by remember(step) { mutableStateOf(step.elseJumpStepId) }

                    OutlinedTextField(
                        value = varName,
                        onValueChange = { varName = it },
                        label = { Text("Variable Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    ComparatorPicker(comp) { comp = it }
                    OutlinedTextField(
                        value = cmpVal,
                        onValueChange = { cmpVal = it },
                        label = { Text("Compare Value") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    BranchSelector("IF TRUE (THEN)", thenAction, thenJumpId, allSteps, EmeraldRun) { a, j ->
                        thenAction = a
                        thenJumpId = j
                    }
                    BranchSelector("IF FALSE (ELSE)", elseAction, elseJumpId, allSteps, AmberLogic) { a, j ->
                        elseAction = a
                        elseJumpId = j
                    }
                    SaveStepActions(onDismiss) {
                        onSave(
                            step.copy(
                                label = label.ifBlank { "If $varName" },
                                variableName = varName.trim().ifBlank { "counter" },
                                comparator = comp,
                                compareValue = cmpVal,
                                thenAction = thenAction,
                                thenJumpStepId = thenJumpId,
                                elseAction = elseAction,
                                elseJumpStepId = elseJumpId
                            )
                        )
                    }
                }

                is JumpStep -> {
                    var targetId by remember(step) { mutableStateOf(step.targetStepId) }
                    Text("Target Step", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        allSteps.forEachIndexed { idx, candidate ->
                            FilterChip(
                                selected = targetId == candidate.id,
                                onClick = { targetId = candidate.id },
                                label = { Text("#${idx + 1} ${candidate.label}") }
                            )
                        }
                    }
                    SaveStepActions(onDismiss) {
                        onSave(step.copy(label = label.ifBlank { "Jump" }, targetStepId = targetId))
                    }
                }

                is CallFunctionStep -> {
                    var selectedFnId by remember(step) { mutableStateOf(step.functionId) }
                    var selectedFnName by remember(step) { mutableStateOf(step.functionName) }
                    var repeatStr by remember(step) { mutableStateOf(step.repeatCount.coerceAtLeast(1).toString()) }

                    Text("Target Function", style = MaterialTheme.typography.labelMedium, color = VioletVar)
                    if (functions.isEmpty()) {
                        Text(
                            text = "No functions created yet. Tap '+ New Fn' in the editor scope bar to create reusable chunks of steps.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            functions.forEach { fn ->
                                FilterChip(
                                    selected = selectedFnId == fn.id,
                                    onClick = {
                                        selectedFnId = fn.id
                                        selectedFnName = fn.name
                                        if (label.startsWith("Call ") || label == "Call Function") {
                                            label = "Call ${fn.name}"
                                        }
                                    },
                                    label = { Text("fn ${fn.name} (${fn.steps.size})") }
                                )
                            }
                        }
                    }

                    NumField("Repeat Calls", repeatStr, { repeatStr = it }, Modifier.fillMaxWidth())

                    SaveStepActions(onDismiss) {
                        val resolvedFn = functions.find { it.id == selectedFnId }
                        val resolvedName = resolvedFn?.name ?: selectedFnName
                        onSave(
                            step.copy(
                                label = label.ifBlank { if (resolvedName.isNotBlank()) "Call $resolvedName" else "Call Function" },
                                functionId = resolvedFn?.id ?: selectedFnId,
                                functionName = resolvedName,
                                repeatCount = (repeatStr.toIntOrNull() ?: step.repeatCount).coerceIn(1, 999)
                            )
                        )
                    }
                }

                is LoopEndStep -> {
                    SaveStepActions(onDismiss) {
                        onSave(step.copy(label = label.ifBlank { "End Loop" }))
                    }
                }

                is StopStep -> {
                    SaveStepActions(onDismiss) {
                        onSave(step.copy(label = label.ifBlank { "Stop Macro" }))
                    }
                }
            }
        }
    }
}

@Composable
private fun NumField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { next -> onValueChange(next.filter { it.isDigit() || it == '-' || it == '.' }) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ComparatorPicker(
    selected: VarComparator,
    onSelect: (VarComparator) -> Unit
) {
    val labels = mapOf(
        VarComparator.EQUALS to "==",
        VarComparator.NOT_EQUALS to "!=",
        VarComparator.GREATER_THAN to ">",
        VarComparator.LESS_THAN to "<",
        VarComparator.GREATER_OR_EQUAL to ">=",
        VarComparator.LESS_OR_EQUAL to "<="
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        VarComparator.entries.forEach { cmp ->
            FilterChip(
                selected = selected == cmp,
                onClick = { onSelect(cmp) },
                label = { Text(labels[cmp] ?: cmp.name) }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BranchSelector(
    title: String,
    action: BranchAction,
    jumpStepId: String?,
    allSteps: List<MacroStep>,
    accent: Color,
    onChanged: (BranchAction, String?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = accent)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BranchAction.entries.forEach { candidate ->
                FilterChip(
                    selected = action == candidate,
                    onClick = { onChanged(candidate, jumpStepId) },
                    label = { Text(candidate.name.replace('_', ' ')) }
                )
            }
        }
        if (action == BranchAction.JUMP_TO_STEP) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                allSteps.forEachIndexed { idx, s ->
                    FilterChip(
                        selected = jumpStepId == s.id,
                        onClick = { onChanged(action, s.id) },
                        label = { Text("#${idx + 1} ${s.label}") }
                    )
                }
            }
        }
    }
}

@Composable
private fun SaveStepActions(
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
        ) {
            Text("Cancel", color = TextSecondary)
        }
        Button(
            onClick = onSave,
            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = Color(0xFF04131A)),
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("save_step_btn")
        ) {
            Text("Save Step", fontWeight = FontWeight.Bold)
        }
    }
}
