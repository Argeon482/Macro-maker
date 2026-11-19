package com.example.service

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenshotMonitor
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.view.KeyEvent
import com.example.data.TemplateAssetEntity
import com.example.model.BranchAction
import com.example.model.CallFunctionStep
import com.example.model.DelayStep
import com.example.model.IfColorStep
import com.example.model.IfImageStep
import com.example.model.IfVariableStep
import com.example.model.InputInjectionMode
import com.example.model.JumpStep
import com.example.model.KeyCategory
import com.example.model.KeyPressStep
import com.example.model.KeyboardKeyCatalog
import com.example.model.LoopEndStep
import com.example.model.LoopStartStep
import com.example.model.MacroCloner
import com.example.model.MacroDefinition
import com.example.model.MacroFunction
import com.example.model.MacroStep
import com.example.model.ScriptClipboardItem
import com.example.model.SetVariableStep
import com.example.model.StopStep
import com.example.model.SwipeStep
import com.example.model.SystemKeyAction
import com.example.model.TapStep
import com.example.model.TouchGuardPolicy
import com.example.model.VarComparator
import com.example.model.VarOperation
import com.example.ui.components.TemplateCropEditorContent
import com.example.ui.components.TemplateThumbnailPreview
import com.example.vision.ScreenDetector
import java.util.UUID
import kotlin.math.roundToInt

private val StudioObsidian = Color(0xF50B0E14)
private val StudioPanel = Color(0xFA131822)
private val StudioWell = Color(0xFF0F141D)
private val StudioBorder = Color(0xFF263042)
private val StudioCyan = Color(0xFF22D3EE)
private val StudioAmber = Color(0xFFF59E0B)
private val StudioEmerald = Color(0xFF10B981)
private val StudioRose = Color(0xFFF43F5E)
private val StudioViolet = Color(0xFFA78BFA)
private val StudioText = Color(0xFFF1F5F9)
private val StudioMuted = Color(0xFF94A3B8)

@Composable
fun MacroPinsOverlay(
    macro: MacroDefinition,
    onUpdateMacro: (MacroDefinition) -> Unit,
    onEditStep: (MacroStep) -> Unit,
    onDone: () -> Unit
) {
    val editableSteps = remember(macro.id, macro.steps) {
        mutableStateListOf<MacroStep>().apply { addAll(macro.steps) }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x2806080D))
    ) {
        val density = LocalDensity.current
        val maxW = with(density) { maxWidth.toPx() }
        val maxH = with(density) { maxHeight.toPx() }
        if (maxW < 120f || maxH < 120f) return@BoxWithConstraints

        val safeMaxX = maxOf(24f, maxW - 16f)
        val safeMaxY = maxOf(24f, maxH - 16f)
        val safeDragMaxX = maxOf(20f, maxW - 12f)
        val safeDragMaxY = maxOf(20f, maxH - 12f)

        Canvas(modifier = Modifier.fillMaxSize()) {
            editableSteps.forEach { step ->
                if (!step.enabled) return@forEach
                when (step) {
                    is SwipeStep -> {
                        drawLine(
                            color = StudioAmber,
                            start = Offset(step.startX, step.startY),
                            end = Offset(step.endX, step.endY),
                            strokeWidth = 4f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 10f), 0f)
                        )
                    }
                    is IfImageStep -> {
                        if (step.searchRight > step.searchLeft && step.searchBottom > step.searchTop) {
                            drawRect(
                                color = StudioEmerald.copy(alpha = 0.12f),
                                topLeft = Offset(step.searchLeft.toFloat(), step.searchTop.toFloat()),
                                size = Size(
                                    (step.searchRight - step.searchLeft).toFloat(),
                                    (step.searchBottom - step.searchTop).toFloat()
                                )
                            )
                            drawRect(
                                color = StudioEmerald,
                                topLeft = Offset(step.searchLeft.toFloat(), step.searchTop.toFloat()),
                                size = Size(
                                    (step.searchRight - step.searchLeft).toFloat(),
                                    (step.searchBottom - step.searchTop).toFloat()
                                ),
                                style = Stroke(width = 2.5f)
                            )
                        }
                    }
                    else -> Unit
                }
            }
        }

        val halfPinPx = with(density) { 26.dp.toPx() }
        editableSteps.forEachIndexed { index, step ->
            if (!step.enabled) return@forEachIndexed
            when (step) {
                is TapStep -> {
                    PinMarker(
                        x = step.x.coerceIn(16f, safeMaxX),
                        y = step.y.coerceIn(16f, safeMaxY),
                        halfSizePx = halfPinPx,
                        badge = "#${index + 1}",
                        accent = StudioCyan,
                        onDrag = { dx, dy ->
                            val cur = editableSteps[index] as? TapStep ?: return@PinMarker
                            editableSteps[index] = cur.copy(
                                x = (cur.x + dx).coerceIn(12f, safeDragMaxX),
                                y = (cur.y + dy).coerceIn(12f, safeDragMaxY)
                            )
                        },
                        onDragEnd = { onUpdateMacro(macro.copy(steps = editableSteps.toList())) },
                        onTapPin = { onEditStep(editableSteps[index]) }
                    )
                }

                is SwipeStep -> {
                    PinMarker(
                        x = step.startX.coerceIn(16f, safeMaxX),
                        y = step.startY.coerceIn(16f, safeMaxY),
                        halfSizePx = halfPinPx,
                        badge = "#${index + 1}S",
                        accent = StudioCyan,
                        onDrag = { dx, dy ->
                            val cur = editableSteps[index] as? SwipeStep ?: return@PinMarker
                            editableSteps[index] = cur.copy(
                                startX = (cur.startX + dx).coerceIn(12f, safeDragMaxX),
                                startY = (cur.startY + dy).coerceIn(12f, safeDragMaxY)
                            )
                        },
                        onDragEnd = { onUpdateMacro(macro.copy(steps = editableSteps.toList())) },
                        onTapPin = { onEditStep(editableSteps[index]) }
                    )
                    PinMarker(
                        x = step.endX.coerceIn(16f, safeMaxX),
                        y = step.endY.coerceIn(16f, safeMaxY),
                        halfSizePx = halfPinPx,
                        badge = "#${index + 1}E",
                        accent = StudioAmber,
                        onDrag = { dx, dy ->
                            val cur = editableSteps[index] as? SwipeStep ?: return@PinMarker
                            editableSteps[index] = cur.copy(
                                endX = (cur.endX + dx).coerceIn(12f, safeDragMaxX),
                                endY = (cur.endY + dy).coerceIn(12f, safeDragMaxY)
                            )
                        },
                        onDragEnd = { onUpdateMacro(macro.copy(steps = editableSteps.toList())) },
                        onTapPin = { onEditStep(editableSteps[index]) }
                    )
                }

                is IfColorStep -> {
                    PinMarker(
                        x = step.x.toFloat().coerceIn(16f, safeMaxX),
                        y = step.y.toFloat().coerceIn(16f, safeMaxY),
                        halfSizePx = halfPinPx,
                        badge = "#${index + 1}C",
                        accent = StudioEmerald,
                        onDrag = { dx, dy ->
                            val cur = editableSteps[index] as? IfColorStep ?: return@PinMarker
                            editableSteps[index] = cur.copy(
                                x = (cur.x + dx).roundToInt().coerceIn(12, safeDragMaxX.roundToInt()),
                                y = (cur.y + dy).roundToInt().coerceIn(12, safeDragMaxY.roundToInt())
                            )
                        },
                        onDragEnd = { onUpdateMacro(macro.copy(steps = editableSteps.toList())) },
                        onTapPin = { onEditStep(editableSteps[index]) }
                    )
                }

                else -> Unit
            }
        }

        Surface(
            color = StudioPanel,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 40.dp, start = 16.dp, end = 16.dp)
                .border(1.dp, StudioBorder, RoundedCornerShape(14.dp))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Drag pins to move • Tap pin to edit",
                    color = StudioText,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Button(
                    onClick = {
                        onUpdateMacro(macro.copy(steps = editableSteps.toList()))
                        onDone()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StudioCyan, contentColor = Color(0xFF04131A)),
                    modifier = Modifier.height(40.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Save & Hide", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PinMarker(
    x: Float,
    y: Float,
    halfSizePx: Float,
    badge: String,
    accent: Color,
    onDrag: (dx: Float, dy: Float) -> Unit,
    onDragEnd: () -> Unit,
    onTapPin: () -> Unit
) {
    Box(
        modifier = Modifier
            .offset { IntOffset((x - halfSizePx).roundToInt(), (y - halfSizePx).roundToInt()) }
            .size(52.dp)
            .pointerInput(badge) {
                detectDragGestures(
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd
                ) { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            }
            .clickable(onClick = onTapPin),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.28f))
                .border(2.dp, accent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = badge,
                color = StudioText,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OverlayStudioWindow(
    macros: List<MacroDefinition>,
    activeMacro: MacroDefinition?,
    initialEditingStepId: String? = null,
    initialActiveFunctionId: String? = null,
    clipboardItem: ScriptClipboardItem? = null,
    templates: List<TemplateAssetEntity>,
    screenCaptureActive: Boolean,
    onSelectMacro: (MacroDefinition) -> Unit,
    onCreateMacro: (String) -> Unit,
    onUpdateMacro: (MacroDefinition) -> Unit,
    onCopyStep: (MacroStep) -> Unit = {},
    onCopyFunction: (MacroFunction) -> Unit = {},
    onClearClipboard: () -> Unit = {},
    onLaunchPicker: (MacroDefinition, MacroStep, PickerMode, String?) -> Unit,
    onCropTemplate: (
        templateId: String,
        trimLeft: Int,
        trimTop: Int,
        trimRight: Int,
        trimBottom: Int,
        saveAsNew: Boolean,
        onCropped: (TemplateAssetEntity) -> Unit
    ) -> Unit = { _, _, _, _, _, _, _ -> },
    onOpenPins: () -> Unit,
    onRequestVision: () -> Unit,
    onRunMacro: (MacroDefinition) -> Unit,
    onClose: () -> Unit
) {
    var activeFunctionId by remember(activeMacro?.id, initialActiveFunctionId) {
        mutableStateOf(
            initialActiveFunctionId?.takeIf { fid ->
                activeMacro?.functions?.any { it.id == fid } == true
            }
        )
    }
    val activeFunction = activeMacro?.functions?.find { it.id == activeFunctionId }
    val currentSteps = activeFunction?.steps ?: activeMacro?.steps.orEmpty()

    var editingStep by remember(activeMacro?.id, initialEditingStepId, initialActiveFunctionId) {
        mutableStateOf(
            if (initialEditingStepId != null) {
                val fnSteps = initialActiveFunctionId?.let { fid ->
                    activeMacro?.functions?.find { it.id == fid }?.steps
                }
                (fnSteps ?: activeMacro?.steps)?.find { it.id == initialEditingStepId }
            } else {
                null
            }
        )
    }
    var croppingTemplate by remember { mutableStateOf<TemplateAssetEntity?>(null) }
    var showAddPanel by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var newMacroName by remember { mutableStateOf("") }
    var creatingMacro by remember { mutableStateOf(false) }
    var creatingFunction by remember { mutableStateOf(false) }
    var renamingFunction by remember { mutableStateOf(false) }
    var functionNameInput by remember { mutableStateOf("") }

    fun updateCurrentSteps(transform: (List<MacroStep>) -> List<MacroStep>): MacroDefinition? {
        val m = activeMacro ?: return null
        val fid = activeFunctionId
        val updated = if (fid == null) {
            m.copy(steps = transform(m.steps))
        } else {
            m.copy(
                functions = m.functions.map { fn ->
                    if (fn.id == fid) fn.copy(steps = transform(fn.steps)) else fn
                }
            )
        }
        onUpdateMacro(updated)
        return updated
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xAA05070B))
            .padding(horizontal = 14.dp, vertical = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = StudioObsidian,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.86f)
                .border(1.dp, StudioBorder, RoundedCornerShape(20.dp))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(StudioPanel)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (croppingTemplate != null || editingStep != null || showAddPanel || showSettings || creatingMacro || creatingFunction || renamingFunction) {
                            IconButton(
                                onClick = {
                                    if (croppingTemplate != null) {
                                        croppingTemplate = null
                                    } else {
                                        editingStep = null
                                        showAddPanel = false
                                        showSettings = false
                                        creatingMacro = false
                                        creatingFunction = false
                                        renamingFunction = false
                                    }
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = StudioText)
                            }
                        }
                        Column {
                            Text(
                                text = when {
                                    croppingTemplate != null -> "Precision Template Crop"
                                    editingStep != null -> "Edit Node"
                                    showAddPanel -> if (activeFunction != null) "Add to fn ${activeFunction.name}" else "Add Automation Node"
                                    showSettings -> "Macro Settings"
                                    creatingMacro -> "New Macro"
                                    creatingFunction -> "New Function"
                                    renamingFunction -> "Rename Function"
                                    else -> "Overlay Studio"
                                },
                                color = StudioText,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = when {
                                    croppingTemplate != null -> croppingTemplate?.name.orEmpty()
                                    activeMacro == null -> "Select or create a macro"
                                    activeFunction != null -> "${activeMacro.name} • fn ${activeFunction.name}"
                                    else -> activeMacro.name
                                },
                                color = if (activeFunction != null && croppingTemplate == null) StudioViolet else StudioCyan,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (activeMacro != null && croppingTemplate == null && editingStep == null && !showAddPanel && !creatingFunction && !renamingFunction) {
                            IconButton(onClick = onOpenPins, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Default.MyLocation, contentDescription = "Edit pins on screen", tint = StudioCyan)
                            }
                        }
                        if (!screenCaptureActive && croppingTemplate == null) {
                            IconButton(onClick = onRequestVision, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Default.ScreenshotMonitor, contentDescription = "Enable Vision", tint = StudioAmber)
                            }
                        }
                        if (activeMacro != null && croppingTemplate == null && editingStep == null && !showAddPanel && !creatingFunction && !renamingFunction) {
                            IconButton(onClick = { onRunMacro(activeMacro); onClose() }, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Run", tint = StudioEmerald)
                            }
                        }
                        IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close overlay studio", tint = StudioRose)
                        }
                    }
                }

                // Body
                when {
                    creatingMacro -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OverlayTextField("Macro Name", newMacroName, { newMacroName = it })
                            Button(
                                onClick = {
                                    onCreateMacro(newMacroName.trim().ifBlank { "Overlay Macro" })
                                    newMacroName = ""
                                    creatingMacro = false
                                    activeFunctionId = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = StudioCyan, contentColor = Color(0xFF04131A)),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Text("Create Macro", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    creatingFunction && activeMacro != null -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Group steps into a reusable function you can call from Main Flow or other functions.",
                                color = StudioMuted,
                                fontSize = 12.sp
                            )
                            OverlayTextField("Function Name (e.g. LootRoutine)", functionNameInput, { functionNameInput = it })
                            Button(
                                onClick = {
                                    val desired = functionNameInput.trim().ifBlank { "Function ${activeMacro.functions.size + 1}" }
                                    val uniqueName = MacroCloner.generateUniqueName(
                                        desired,
                                        activeMacro.functions.map { it.name }.toSet()
                                    )
                                    val newFn = MacroFunction(name = uniqueName)
                                    onUpdateMacro(activeMacro.copy(functions = activeMacro.functions + newFn))
                                    activeFunctionId = newFn.id
                                    functionNameInput = ""
                                    creatingFunction = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = StudioViolet, contentColor = Color(0xFF0A0614)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("overlay_create_function_confirm")
                            ) {
                                Text("Create Function", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    renamingFunction && activeMacro != null && activeFunction != null -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OverlayTextField("Function Name", functionNameInput, { functionNameInput = it })
                            Button(
                                onClick = {
                                    val newName = functionNameInput.trim().ifBlank { activeFunction.name }
                                    val fid = activeFunction.id
                                    fun syncCalls(list: List<MacroStep>): List<MacroStep> = list.map { step ->
                                        if (step is CallFunctionStep && step.functionId == fid) {
                                            step.copy(functionName = newName)
                                        } else step
                                    }
                                    val updated = activeMacro.copy(
                                        steps = syncCalls(activeMacro.steps),
                                        functions = activeMacro.functions.map { fn ->
                                            val renamed = if (fn.id == fid) fn.copy(name = newName) else fn
                                            renamed.copy(steps = syncCalls(renamed.steps))
                                        }
                                    )
                                    onUpdateMacro(updated)
                                    functionNameInput = ""
                                    renamingFunction = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = StudioViolet, contentColor = Color(0xFF0A0614)),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Text("Save Name", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    showSettings && activeMacro != null -> {
                        OverlayMacroSettings(
                            macro = activeMacro,
                            onSave = { updated ->
                                onUpdateMacro(updated)
                                showSettings = false
                            }
                        )
                    }

                    showAddPanel && activeMacro != null -> {
                        OverlayAddStepList(
                            functions = activeMacro.functions,
                            onAdd = { newSteps ->
                                updateCurrentSteps { it + newSteps }
                                showAddPanel = false
                                if (newSteps.size == 1) {
                                    editingStep = newSteps.first()
                                }
                            }
                        )
                    }

                    croppingTemplate != null && activeMacro != null -> {
                        val tpl = templates.find { it.id == croppingTemplate?.id } ?: croppingTemplate!!
                        TemplateCropEditorContent(
                            template = tpl,
                            compactMode = true,
                            onApplyCrop = { l, t, r, b, saveAsNew ->
                                onCropTemplate(tpl.id, l, t, r, b, saveAsNew) { cropped ->
                                    val editing = editingStep
                                    if (editing is IfImageStep) {
                                        val updatedImg = editing.copy(
                                            templateId = cropped.id,
                                            templateName = cropped.name
                                        )
                                        updateCurrentSteps { list ->
                                            list.map { if (it.id == updatedImg.id) updatedImg else it }
                                        }
                                        editingStep = updatedImg
                                    }
                                    croppingTemplate = null
                                }
                            },
                            onCancel = { croppingTemplate = null }
                        )
                    }

                    editingStep != null && activeMacro != null -> {
                        val currentStep = currentSteps.find { it.id == editingStep?.id } ?: editingStep!!
                        OverlayStepEditor(
                            step = currentStep,
                            allSteps = currentSteps,
                            functions = activeMacro.functions,
                            templates = templates,
                            screenCaptureActive = screenCaptureActive,
                            onRequestVision = onRequestVision,
                            onLaunchPicker = { updatedStep, mode ->
                                val savedMacro = updateCurrentSteps { list ->
                                    list.map { if (it.id == updatedStep.id) updatedStep else it }
                                } ?: activeMacro
                                onLaunchPicker(savedMacro, updatedStep, mode, activeFunctionId)
                            },
                            onOpenTemplateCrop = { tpl, draftStep ->
                                updateCurrentSteps { list ->
                                    list.map { if (it.id == draftStep.id) draftStep else it }
                                }
                                editingStep = draftStep
                                croppingTemplate = tpl
                            },
                            onSave = { updatedStep ->
                                updateCurrentSteps { list ->
                                    list.map { if (it.id == updatedStep.id) updatedStep else it }
                                }
                                editingStep = null
                            }
                        )
                    }

                    else -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Macro switcher bar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilterChip(
                                    selected = false,
                                    onClick = { creatingMacro = true },
                                    label = { Text("+ New", color = StudioCyan) }
                                )
                                macros.forEach { m ->
                                    FilterChip(
                                        selected = m.id == activeMacro?.id,
                                        onClick = {
                                            activeFunctionId = null
                                            onSelectMacro(m)
                                        },
                                        label = { Text(m.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                    )
                                }
                            }

                            if (activeMacro == null) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("Tap + New to create a macro.", color = StudioMuted)
                                }
                            } else {
                                // Scope / Functions bar
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                        .padding(horizontal = 12.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    FilterChip(
                                        selected = activeFunctionId == null,
                                        onClick = { activeFunctionId = null },
                                        label = { Text("Main (${activeMacro.steps.size})", fontSize = 11.sp) },
                                        modifier = Modifier.testTag("overlay_scope_main")
                                    )
                                    activeMacro.functions.forEach { fn ->
                                        FilterChip(
                                            selected = activeFunctionId == fn.id,
                                            onClick = { activeFunctionId = fn.id },
                                            label = {
                                                Text(
                                                    "fn ${fn.name} (${fn.steps.size})",
                                                    fontSize = 11.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        )
                                    }
                                    FilterChip(
                                        selected = false,
                                        onClick = {
                                            functionNameInput = ""
                                            creatingFunction = true
                                        },
                                        label = { Text("+ Fn", color = StudioViolet, fontSize = 11.sp) },
                                        modifier = Modifier.testTag("overlay_add_function_chip")
                                    )
                                }

                                // Active Function controls
                                if (activeFunction != null) {
                                    Surface(
                                        color = StudioWell,
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, StudioViolet.copy(alpha = 0.45f)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState())
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    functionNameInput = activeFunction.name
                                                    renamingFunction = true
                                                },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                modifier = Modifier.height(34.dp)
                                            ) {
                                                Icon(Icons.Default.Edit, contentDescription = "Rename function", tint = StudioText, modifier = Modifier.size(14.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Rename", color = StudioText, fontSize = 11.sp)
                                            }
                                            OutlinedButton(
                                                onClick = { onCopyFunction(activeFunction) },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                modifier = Modifier
                                                    .height(34.dp)
                                                    .testTag("overlay_copy_function_button")
                                            ) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy function", tint = StudioCyan, modifier = Modifier.size(14.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Copy Fn", color = StudioCyan, fontSize = 11.sp)
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    val callStep = CallFunctionStep(
                                                        label = "Call ${activeFunction.name}",
                                                        functionId = activeFunction.id,
                                                        functionName = activeFunction.name,
                                                        repeatCount = 1
                                                    )
                                                    onUpdateMacro(activeMacro.copy(steps = activeMacro.steps + callStep))
                                                    activeFunctionId = null
                                                },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                modifier = Modifier.height(34.dp)
                                            ) {
                                                Icon(Icons.Default.Code, contentDescription = "Add call to Main", tint = StudioViolet, modifier = Modifier.size(14.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Call in Main", color = StudioViolet, fontSize = 11.sp)
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    val remaining = activeMacro.functions.filterNot { it.id == activeFunction.id }
                                                    onUpdateMacro(activeMacro.copy(functions = remaining))
                                                    activeFunctionId = null
                                                },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                modifier = Modifier.height(34.dp)
                                            ) {
                                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete function", tint = StudioRose, modifier = Modifier.size(14.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Delete", color = StudioRose, fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }

                                // Shared Clipboard Bar
                                if (clipboardItem != null) {
                                    Surface(
                                        color = StudioWell,
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, StudioCyan.copy(alpha = 0.45f)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState())
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.ContentPaste, contentDescription = null, tint = StudioCyan, modifier = Modifier.size(14.dp))
                                            when (clipboardItem) {
                                                is ScriptClipboardItem.SingleStep -> {
                                                    Text(
                                                        text = clipboardItem.step.label,
                                                        color = StudioText,
                                                        fontSize = 11.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        maxLines = 1
                                                    )
                                                    Button(
                                                        onClick = {
                                                            val cloned = MacroCloner.cloneStep(clipboardItem.step)
                                                            updateCurrentSteps { it + cloned }
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = StudioCyan, contentColor = Color(0xFF04131A)),
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                        modifier = Modifier
                                                            .height(32.dp)
                                                            .testTag("overlay_paste_step_button")
                                                    ) {
                                                        Text("Paste Here", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                                is ScriptClipboardItem.FunctionClip -> {
                                                    Text(
                                                        text = "fn ${clipboardItem.function.name} (${clipboardItem.function.steps.size})",
                                                        color = StudioText,
                                                        fontSize = 11.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        maxLines = 1
                                                    )
                                                    Button(
                                                        onClick = {
                                                            val clonedFn = MacroCloner.cloneFunction(
                                                                clipboardItem.function,
                                                                activeMacro.functions.map { it.name }.toSet()
                                                            )
                                                            onUpdateMacro(activeMacro.copy(functions = activeMacro.functions + clonedFn))
                                                            activeFunctionId = clonedFn.id
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = StudioViolet, contentColor = Color(0xFF0A0614)),
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                        modifier = Modifier
                                                            .height(32.dp)
                                                            .testTag("overlay_paste_as_fn_button")
                                                    ) {
                                                        Text("As New Fn", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                    OutlinedButton(
                                                        onClick = {
                                                            val clonedSteps = MacroCloner.cloneStepsWithRemappedIds(clipboardItem.function.steps)
                                                            updateCurrentSteps { it + clonedSteps }
                                                        },
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                        modifier = Modifier.height(32.dp)
                                                    ) {
                                                        Text("Paste Steps", color = StudioCyan, fontSize = 11.sp)
                                                    }
                                                }
                                            }
                                            IconButton(
                                                onClick = onClearClipboard,
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "Clear clipboard", tint = StudioMuted, modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { showSettings = true },
                                        modifier = Modifier.weight(1f).height(42.dp)
                                    ) {
                                        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp), tint = StudioText)
                                        Spacer(Modifier.width(6.dp))
                                        Text("${if (activeMacro.loopCount <= 0) "∞" else "${activeMacro.loopCount}x"} • ${activeMacro.pollingIntervalMs}ms", color = StudioText, fontSize = 12.sp)
                                    }
                                    Button(
                                        onClick = { showAddPanel = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = StudioCyan, contentColor = Color(0xFF04131A)),
                                        modifier = Modifier.weight(1f).height(42.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            if (activeFunction != null) "Add to Fn" else "Add Step",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }

                                if (currentSteps.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (activeFunction != null) {
                                                "No steps in fn ${activeFunction.name} yet.\nTap Add to Fn or Paste a copied step."
                                            } else {
                                                "No steps yet. Tap Add Step to begin."
                                            },
                                            color = StudioMuted,
                                            fontSize = 12.sp
                                        )
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        itemsIndexed(currentSteps, key = { _, s -> s.id }) { index, step ->
                                            val accent = stepAccentColor(step)
                                            Surface(
                                                color = StudioPanel,
                                                shape = RoundedCornerShape(12.dp),
                                                border = BorderStroke(1.dp, StudioBorder),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { editingStep = step }
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Box(
                                                            modifier = Modifier
                                                                .width(4.dp)
                                                                .height(34.dp)
                                                                .background(accent, RoundedCornerShape(2.dp))
                                                        )
                                                        Spacer(Modifier.width(10.dp))
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = "#${index + 1} ${step.label}",
                                                                color = if (step.enabled) StudioText else StudioMuted,
                                                                fontSize = 13.sp,
                                                                fontWeight = FontWeight.SemiBold,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            Text(
                                                                text = stepSummaryText(step, activeMacro.functions),
                                                                color = StudioMuted,
                                                                fontSize = 11.sp,
                                                                fontFamily = FontFamily.Monospace,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                    }
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.End,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        if (step is CallFunctionStep && activeMacro.functions.any { it.id == step.functionId }) {
                                                            OutlinedButton(
                                                                onClick = { activeFunctionId = step.functionId },
                                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                                                modifier = Modifier.height(30.dp)
                                                            ) {
                                                                Icon(Icons.Default.Code, contentDescription = "Open function", tint = StudioViolet, modifier = Modifier.size(13.dp))
                                                                Spacer(Modifier.width(4.dp))
                                                                Text("Open Fn", color = StudioViolet, fontSize = 10.sp)
                                                            }
                                                            Spacer(Modifier.width(4.dp))
                                                        }
                                                        IconButton(
                                                            onClick = { onCopyStep(step) },
                                                            modifier = Modifier.size(34.dp)
                                                        ) {
                                                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy step", tint = StudioCyan, modifier = Modifier.size(15.dp))
                                                        }
                                                        if (clipboardItem is ScriptClipboardItem.SingleStep) {
                                                            IconButton(
                                                                onClick = {
                                                                    val cloned = MacroCloner.cloneStep(clipboardItem.step)
                                                                    updateCurrentSteps { list ->
                                                                        list.toMutableList().apply { add(index + 1, cloned) }
                                                                    }
                                                                },
                                                                modifier = Modifier.size(34.dp)
                                                            ) {
                                                                Icon(Icons.Default.ContentPaste, contentDescription = "Paste below", tint = StudioEmerald, modifier = Modifier.size(15.dp))
                                                            }
                                                        }
                                                        IconButton(
                                                            onClick = {
                                                                if (index > 0) {
                                                                    updateCurrentSteps { list ->
                                                                        list.toMutableList().apply {
                                                                            val item = removeAt(index)
                                                                            add(index - 1, item)
                                                                        }
                                                                    }
                                                                }
                                                            },
                                                            enabled = index > 0,
                                                            modifier = Modifier.size(34.dp)
                                                        ) {
                                                            Icon(Icons.Default.ArrowUpward, contentDescription = "Move up", tint = StudioMuted, modifier = Modifier.size(16.dp))
                                                        }
                                                        IconButton(
                                                            onClick = {
                                                                if (index < currentSteps.lastIndex) {
                                                                    updateCurrentSteps { list ->
                                                                        list.toMutableList().apply {
                                                                            val item = removeAt(index)
                                                                            add(index + 1, item)
                                                                        }
                                                                    }
                                                                }
                                                            },
                                                            enabled = index < currentSteps.lastIndex,
                                                            modifier = Modifier.size(34.dp)
                                                        ) {
                                                            Icon(Icons.Default.ArrowDownward, contentDescription = "Move down", tint = StudioMuted, modifier = Modifier.size(16.dp))
                                                        }
                                                        IconButton(
                                                            onClick = {
                                                                updateCurrentSteps { list ->
                                                                    list.toMutableList().apply { removeAt(index) }
                                                                }
                                                            },
                                                            modifier = Modifier.size(34.dp)
                                                        ) {
                                                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = StudioRose, modifier = Modifier.size(16.dp))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OverlayMacroSettings(
    macro: MacroDefinition,
    onSave: (MacroDefinition) -> Unit
) {
    var name by remember(macro) { mutableStateOf(macro.name) }
    var target by remember(macro) { mutableStateOf(macro.targetAppLabel) }
    var loops by remember(macro) { mutableStateOf(macro.loopCount.toString()) }
    var poll by remember(macro) { mutableStateOf(macro.pollingIntervalMs.toString()) }
    var injectionMode by remember(macro) { mutableStateOf(macro.injectionMode) }
    var touchGuardPolicy by remember(macro) { mutableStateOf(macro.touchGuardPolicy) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OverlayTextField("Macro Name", name, { name = it })
        OverlayTextField("Target App Label", target, { target = it })
        OverlayTextField("Passes (0 = Infinite)", loops, { loops = it.filter(Char::isDigit) }, numeric = true)
        OverlayTextField("Vision Poll (ms)", poll, { poll = it.filter(Char::isDigit) }, numeric = true)

        Text("Pointer Injection Source", color = StudioCyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            InputInjectionMode.entries.forEach { mode ->
                FilterChip(
                    selected = injectionMode == mode,
                    onClick = { injectionMode = mode },
                    label = { Text(mode.displayLabel, fontSize = 12.sp) }
                )
            }
        }
        Text(injectionMode.shortDesc, color = StudioMuted, fontSize = 11.sp)

        Text("User Touch Guard", color = StudioAmber, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TouchGuardPolicy.entries.forEach { policy ->
                FilterChip(
                    selected = touchGuardPolicy == policy,
                    onClick = { touchGuardPolicy = policy },
                    label = { Text(policy.displayLabel, fontSize = 12.sp) }
                )
            }
        }
        Text(touchGuardPolicy.shortDesc, color = StudioMuted, fontSize = 11.sp)

        Button(
            onClick = {
                onSave(
                    macro.copy(
                        name = name.trim().ifBlank { macro.name },
                        targetAppLabel = target.trim().ifBlank { macro.targetAppLabel },
                        loopCount = loops.toIntOrNull() ?: macro.loopCount,
                        pollingIntervalMs = (poll.toLongOrNull() ?: macro.pollingIntervalMs).coerceAtLeast(50L),
                        injectionMode = injectionMode,
                        touchGuardPolicy = touchGuardPolicy
                    )
                )
            },
            colors = ButtonDefaults.buttonColors(containerColor = StudioCyan, contentColor = Color(0xFF04131A)),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("Save Settings", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun OverlayAddStepList(
    functions: List<MacroFunction>,
    onAdd: (List<MacroStep>) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OverlayAddItem("Tap Coordinate", "Inject tap at (x, y)", StudioCyan) { onAdd(listOf(TapStep())) }
        OverlayAddItem("Swipe Gesture", "Drag from Start to End", StudioCyan) { onAdd(listOf(SwipeStep())) }
        OverlayAddItem("Wait / Delay", "Pause between actions (ms)", StudioCyan) { onAdd(listOf(DelayStep())) }
        OverlayAddItem("Key Press (Keyboard / Game)", "Send any keyboard, game, or system key", StudioCyan) {
            onAdd(
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
        OverlayAddItem(
            title = "Call Function",
            subtitle = if (functions.isEmpty()) "Run a reusable function (create via + Fn)" else "Run one of ${functions.size} saved functions",
            accent = StudioViolet
        ) {
            val firstFn = functions.firstOrNull()
            onAdd(
                listOf(
                    CallFunctionStep(
                        label = firstFn?.let { "Call ${it.name}" } ?: "Call Function",
                        functionId = firstFn?.id.orEmpty(),
                        functionName = firstFn?.name.orEmpty(),
                        repeatCount = 1
                    )
                )
            )
        }
        OverlayAddItem("If Pixel Color", "Sample & match RGB at (x, y)", StudioEmerald) { onAdd(listOf(IfColorStep())) }
        OverlayAddItem("If Image Match", "Crop template & search region", StudioEmerald) { onAdd(listOf(IfImageStep())) }
        OverlayAddItem("Loop Block (Start + End)", "Repeat N times or while true", StudioAmber) {
            val id = UUID.randomUUID().toString()
            onAdd(listOf(LoopStartStep(id = id, label = "Loop 3x", iterations = 3), LoopEndStep(loopStartId = id)))
        }
        OverlayAddItem("Set Variable", "Set or increment state variable", StudioViolet) { onAdd(listOf(SetVariableStep())) }
        OverlayAddItem("If Variable", "Compare variable & branch", StudioAmber) { onAdd(listOf(IfVariableStep())) }
        OverlayAddItem("Jump To Step", "Jump execution flow", StudioAmber) { onAdd(listOf(JumpStep())) }
        OverlayAddItem("Stop Macro", "Stop execution cleanly", StudioRose) { onAdd(listOf(StopStep())) }
    }
}

@Composable
private fun OverlayAddItem(
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = StudioPanel,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, StudioBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(accent))
            Column {
                Text(title, color = StudioText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = StudioMuted, fontSize = 11.sp)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OverlayStepEditor(
    step: MacroStep,
    allSteps: List<MacroStep>,
    functions: List<MacroFunction>,
    templates: List<TemplateAssetEntity>,
    screenCaptureActive: Boolean,
    onRequestVision: () -> Unit,
    onLaunchPicker: (MacroStep, PickerMode) -> Unit,
    onOpenTemplateCrop: (TemplateAssetEntity, MacroStep) -> Unit = { _, _ -> },
    onSave: (MacroStep) -> Unit
) {
    var label by remember(step) { mutableStateOf(step.label) }
    var enabled by remember(step) { mutableStateOf(step.enabled) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Enabled", color = StudioText, fontSize = 13.sp)
            Switch(checked = enabled, onCheckedChange = { enabled = it })
        }

        OverlayTextField("Label", label, { label = it })

        when (step) {
            is TapStep -> {
                var x by remember(step) { mutableStateOf(step.x.toInt().toString()) }
                var y by remember(step) { mutableStateOf(step.y.toInt().toString()) }
                var hold by remember(step) { mutableStateOf(step.holdMs.toString()) }
                var jitter by remember(step) { mutableStateOf(step.jitterPx.toString()) }

                OutlinedButton(
                    onClick = {
                        onLaunchPicker(
                            step.copy(label = label, enabled = enabled, x = x.toFloatOrNull() ?: step.x, y = y.toFloatOrNull() ?: step.y),
                            PickerMode.PICK_TAP
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = null, tint = StudioCyan)
                    Spacer(Modifier.width(6.dp))
                    Text("Pick Tap on Screen", color = StudioCyan)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OverlayTextField("X", x, { x = it.filter(Char::isDigit) }, Modifier.weight(1f), numeric = true)
                    OverlayTextField("Y", y, { y = it.filter(Char::isDigit) }, Modifier.weight(1f), numeric = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OverlayTextField("Hold ms", hold, { hold = it.filter(Char::isDigit) }, Modifier.weight(1f), numeric = true)
                    OverlayTextField("Jitter px", jitter, { jitter = it.filter(Char::isDigit) }, Modifier.weight(1f), numeric = true)
                }

                OverlaySaveBtn {
                    onSave(
                        step.copy(
                            label = label.ifBlank { "Tap" },
                            enabled = enabled,
                            x = x.toFloatOrNull() ?: step.x,
                            y = y.toFloatOrNull() ?: step.y,
                            holdMs = (hold.toLongOrNull() ?: step.holdMs).coerceAtLeast(10L),
                            jitterPx = (jitter.toIntOrNull() ?: step.jitterPx).coerceAtLeast(0)
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
                        onLaunchPicker(
                            step.copy(
                                label = label,
                                enabled = enabled,
                                startX = sx.toFloatOrNull() ?: step.startX,
                                startY = sy.toFloatOrNull() ?: step.startY,
                                endX = ex.toFloatOrNull() ?: step.endX,
                                endY = ey.toFloatOrNull() ?: step.endY,
                                durationMs = (dur.toLongOrNull() ?: step.durationMs).coerceAtLeast(60L)
                            ),
                            PickerMode.PICK_SWIPE
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) {
                    Icon(Icons.Default.Gesture, contentDescription = null, tint = StudioAmber)
                    Spacer(Modifier.width(6.dp))
                    Text("Pick Swipe on Screen", color = StudioAmber)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OverlayTextField("Start X", sx, { sx = it.filter(Char::isDigit) }, Modifier.weight(1f), numeric = true)
                    OverlayTextField("Start Y", sy, { sy = it.filter(Char::isDigit) }, Modifier.weight(1f), numeric = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OverlayTextField("End X", ex, { ex = it.filter(Char::isDigit) }, Modifier.weight(1f), numeric = true)
                    OverlayTextField("End Y", ey, { ey = it.filter(Char::isDigit) }, Modifier.weight(1f), numeric = true)
                }
                OverlayTextField("Duration ms", dur, { dur = it.filter(Char::isDigit) }, numeric = true)

                OverlaySaveBtn {
                    onSave(
                        step.copy(
                            label = label.ifBlank { "Swipe" },
                            enabled = enabled,
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
                var minS by remember(step) { mutableStateOf(step.minMs.toString()) }
                var maxS by remember(step) { mutableStateOf(step.maxMs.toString()) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OverlayTextField("Min ms", minS, { minS = it.filter(Char::isDigit) }, Modifier.weight(1f), numeric = true)
                    OverlayTextField("Max ms", maxS, { maxS = it.filter(Char::isDigit) }, Modifier.weight(1f), numeric = true)
                }
                OverlaySaveBtn {
                    val minV = (minS.toLongOrNull() ?: step.minMs).coerceAtLeast(0L)
                    val maxV = (maxS.toLongOrNull() ?: minV).coerceAtLeast(minV)
                    onSave(step.copy(label = label.ifBlank { "Wait" }, enabled = enabled, minMs = minV, maxMs = maxV))
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

                Text("Category", color = StudioCyan, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    KeyCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat.title, fontSize = 11.sp) }
                        )
                    }
                }

                Text("Key Binding: $keyLabel (code=$keyCode)", color = StudioText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                            label = { Text(opt.label, fontSize = 11.sp) }
                        )
                    }
                }

                Text("Modifiers (Optional)", color = StudioMuted, fontSize = 11.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = useShift, onClick = { useShift = !useShift }, label = { Text("Shift", fontSize = 11.sp) })
                    FilterChip(selected = useCtrl, onClick = { useCtrl = !useCtrl }, label = { Text("Ctrl", fontSize = 11.sp) })
                    FilterChip(selected = useAlt, onClick = { useAlt = !useAlt }, label = { Text("Alt", fontSize = 11.sp) })
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OverlayTextField("Hold ms", holdStr, { holdStr = it.filter(Char::isDigit) }, Modifier.weight(1f), numeric = true)
                    OverlayTextField(
                        "KeyCode",
                        keyCodeText,
                        { raw ->
                            val clean = raw.filter(Char::isDigit)
                            keyCodeText = clean
                            clean.toIntOrNull()?.let { parsed ->
                                keyCode = parsed
                                keyLabel = KeyboardKeyCatalog.labelForKeyCode(parsed)
                            }
                        },
                        Modifier.weight(1f),
                        numeric = true
                    )
                }

                OverlaySaveBtn {
                    val meta = (if (useShift) KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON else 0) or
                        (if (useCtrl) KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON else 0) or
                        (if (useAlt) KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON else 0)
                    val matchedSys = SystemKeyAction.entries.firstOrNull { it.keyCode == keyCode } ?: step.keyAction
                    val resolvedLabel = keyLabel.ifBlank { KeyboardKeyCatalog.labelForKeyCode(keyCode) }
                    onSave(
                        step.copy(
                            label = label.ifBlank { "Key $resolvedLabel" },
                            enabled = enabled,
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
                OverlayTextField("Variable Name", varName, { varName = it })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    VarOperation.entries.forEach { o -> FilterChip(selected = op == o, onClick = { op = o }, label = { Text(o.name) }) }
                }
                OverlayTextField("Value / Delta", expr, { expr = it })
                OverlaySaveBtn {
                    onSave(step.copy(label = label, enabled = enabled, variableName = varName.trim().ifBlank { "counter" }, operation = op, valueExpression = expr))
                }
            }

            is LoopStartStep -> {
                var iter by remember(step) { mutableStateOf(step.iterations.toString()) }
                var condVar by remember(step) { mutableStateOf(step.conditionVar) }
                var condOp by remember(step) { mutableStateOf(step.conditionOp) }
                var condVal by remember(step) { mutableStateOf(step.conditionValue) }
                OverlayTextField("Iterations (0 = while)", iter, { iter = it.filter(Char::isDigit) }, numeric = true)
                OverlayTextField("Optional Condition Var", condVar, { condVar = it })
                if (condVar.isNotBlank()) {
                    OverlayComparatorChips(condOp) { condOp = it }
                    OverlayTextField("Condition Value", condVal, { condVal = it })
                }
                OverlaySaveBtn {
                    onSave(step.copy(label = label, enabled = enabled, iterations = iter.toIntOrNull() ?: step.iterations, conditionVar = condVar.trim(), conditionOp = condOp, conditionValue = condVal))
                }
            }

            is IfColorStep -> {
                var x by remember(step) { mutableStateOf(step.x.toString()) }
                var y by remember(step) { mutableStateOf(step.y.toString()) }
                var hex by remember(step) { mutableStateOf(step.targetHexColor) }
                var tol by remember(step) { mutableStateOf(step.tolerancePct.toString()) }
                var tapOnMatch by remember(step) { mutableStateOf(step.tapOnMatch) }
                var thenAct by remember(step) { mutableStateOf(step.thenAction) }
                var thenJump by remember(step) { mutableStateOf(step.thenJumpStepId) }
                var elseAct by remember(step) { mutableStateOf(step.elseAction) }
                var elseJump by remember(step) { mutableStateOf(step.elseJumpStepId) }

                if (!screenCaptureActive) {
                    OutlinedButton(onClick = onRequestVision, modifier = Modifier.fillMaxWidth().height(44.dp)) {
                        Text("Enable Vision Capture First", color = StudioAmber)
                    }
                }
                OutlinedButton(
                    onClick = {
                        onLaunchPicker(
                            step.copy(
                                label = label,
                                enabled = enabled,
                                x = x.toIntOrNull() ?: step.x,
                                y = y.toIntOrNull() ?: step.y,
                                targetHexColor = hex.trim().ifBlank { step.targetHexColor },
                                tolerancePct = (tol.toIntOrNull() ?: step.tolerancePct).coerceIn(0, 100)
                            ),
                            PickerMode.PICK_COLOR
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) {
                    Icon(Icons.Default.Colorize, contentDescription = null, tint = StudioEmerald)
                    Spacer(Modifier.width(6.dp))
                    Text("Pick Pixel & Color on Screen", color = StudioEmerald)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OverlayTextField("X", x, { x = it.filter(Char::isDigit) }, Modifier.weight(1f), numeric = true)
                    OverlayTextField("Y", y, { y = it.filter(Char::isDigit) }, Modifier.weight(1f), numeric = true)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(ScreenDetector.parseHexColor(hex)))
                            .border(1.dp, StudioBorder, RoundedCornerShape(8.dp))
                    )
                    OverlayTextField("Hex", hex, { hex = it }, Modifier.weight(1f))
                    OverlayTextField("Tol%", tol, { tol = it.filter(Char::isDigit) }, Modifier.width(80.dp), numeric = true)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Tap on match", color = StudioText, fontSize = 13.sp)
                    Switch(checked = tapOnMatch, onCheckedChange = { tapOnMatch = it })
                }
                OverlayBranchPicker("THEN", thenAct, thenJump, allSteps, StudioEmerald) { a, j -> thenAct = a; thenJump = j }
                OverlayBranchPicker("ELSE", elseAct, elseJump, allSteps, StudioAmber) { a, j -> elseAct = a; elseJump = j }

                OverlaySaveBtn {
                    onSave(
                        step.copy(
                            label = label,
                            enabled = enabled,
                            x = x.toIntOrNull() ?: step.x,
                            y = y.toIntOrNull() ?: step.y,
                            targetHexColor = hex.trim().ifBlank { "#10B981" },
                            tolerancePct = (tol.toIntOrNull() ?: step.tolerancePct).coerceIn(0, 100),
                            tapOnMatch = tapOnMatch,
                            thenAction = thenAct,
                            thenJumpStepId = thenJump,
                            elseAction = elseAct,
                            elseJumpStepId = elseJump
                        )
                    )
                }
            }

            is IfImageStep -> {
                var tplId by remember(step) { mutableStateOf(step.templateId) }
                var tplName by remember(step) { mutableStateOf(step.templateName) }
                var conf by remember(step) { mutableStateOf(step.minConfidencePct.toString()) }
                var tapOnMatch by remember(step) { mutableStateOf(step.tapOnMatch) }
                var sLeft by remember(step) { mutableStateOf(step.searchLeft) }
                var sTop by remember(step) { mutableStateOf(step.searchTop) }
                var sRight by remember(step) { mutableStateOf(step.searchRight) }
                var sBottom by remember(step) { mutableStateOf(step.searchBottom) }
                var thenAct by remember(step) { mutableStateOf(step.thenAction) }
                var thenJump by remember(step) { mutableStateOf(step.thenJumpStepId) }
                var elseAct by remember(step) { mutableStateOf(step.elseAction) }
                var elseJump by remember(step) { mutableStateOf(step.elseJumpStepId) }

                if (!screenCaptureActive) {
                    OutlinedButton(onClick = onRequestVision, modifier = Modifier.fillMaxWidth().height(44.dp)) {
                        Text("Enable Vision Capture First", color = StudioAmber)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            onLaunchPicker(
                                step.copy(
                                    label = label,
                                    enabled = enabled,
                                    templateId = tplId,
                                    templateName = tplName,
                                    searchLeft = sLeft,
                                    searchTop = sTop,
                                    searchRight = sRight,
                                    searchBottom = sBottom
                                ),
                                PickerMode.PICK_TEMPLATE_CROP
                            )
                        },
                        modifier = Modifier.weight(1f).height(46.dp)
                    ) {
                        Icon(Icons.Default.CropFree, contentDescription = null, tint = StudioCyan, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Crop Image", color = StudioCyan, fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            onLaunchPicker(
                                step.copy(
                                    label = label,
                                    enabled = enabled,
                                    templateId = tplId,
                                    templateName = tplName,
                                    searchLeft = sLeft,
                                    searchTop = sTop,
                                    searchRight = sRight,
                                    searchBottom = sBottom
                                ),
                                PickerMode.PICK_SEARCH_ROI
                            )
                        },
                        modifier = Modifier.weight(1f).height(46.dp)
                    ) {
                        Text("Search Area", color = StudioEmerald, fontSize = 12.sp)
                    }
                }
                val selectedTpl = templates.find { it.id == tplId }
                if (selectedTpl != null) {
                    TemplateThumbnailPreview(
                        template = selectedTpl,
                        onEditCrop = {
                            onOpenTemplateCrop(
                                selectedTpl,
                                step.copy(
                                    label = label,
                                    enabled = enabled,
                                    templateId = tplId,
                                    templateName = tplName,
                                    searchLeft = sLeft,
                                    searchTop = sTop,
                                    searchRight = sRight,
                                    searchBottom = sBottom,
                                    minConfidencePct = (conf.toIntOrNull() ?: step.minConfidencePct).coerceIn(40, 100),
                                    tapOnMatch = tapOnMatch,
                                    thenAction = thenAct,
                                    thenJumpStepId = thenJump,
                                    elseAction = elseAct,
                                    elseJumpStepId = elseJump
                                )
                            )
                        }
                    )
                }

                Text(
                    text = if (sRight > sLeft && sBottom > sTop) "ROI: ($sLeft,$sTop)–($sRight,$sBottom)" else "ROI: Full Screen",
                    color = StudioMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                if (sRight > sLeft && sBottom > sTop) {
                    OutlinedButton(
                        onClick = { sLeft = 0; sTop = 0; sRight = 0; sBottom = 0 },
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Reset ROI to Full Screen", color = StudioMuted, fontSize = 11.sp)
                    }
                }

                if (templates.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        templates.forEach { t ->
                            FilterChip(
                                selected = tplId == t.id,
                                onClick = { tplId = t.id; tplName = t.name },
                                label = { Text("${t.name} (${t.width}x${t.height})", fontSize = 11.sp) }
                            )
                        }
                    }
                }

                OverlayTextField("Min Confidence %", conf, { conf = it.filter(Char::isDigit) }, numeric = true)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Tap matched center", color = StudioText, fontSize = 13.sp)
                    Switch(checked = tapOnMatch, onCheckedChange = { tapOnMatch = it })
                }
                OverlayBranchPicker("THEN", thenAct, thenJump, allSteps, StudioEmerald) { a, j -> thenAct = a; thenJump = j }
                OverlayBranchPicker("ELSE", elseAct, elseJump, allSteps, StudioAmber) { a, j -> elseAct = a; elseJump = j }

                OverlaySaveBtn {
                    onSave(
                        step.copy(
                            label = label,
                            enabled = enabled,
                            templateId = tplId,
                            templateName = tplName,
                            searchLeft = sLeft,
                            searchTop = sTop,
                            searchRight = sRight,
                            searchBottom = sBottom,
                            minConfidencePct = (conf.toIntOrNull() ?: step.minConfidencePct).coerceIn(40, 100),
                            tapOnMatch = tapOnMatch,
                            thenAction = thenAct,
                            thenJumpStepId = thenJump,
                            elseAction = elseAct,
                            elseJumpStepId = elseJump
                        )
                    )
                }
            }

            is IfVariableStep -> {
                var varName by remember(step) { mutableStateOf(step.variableName) }
                var comp by remember(step) { mutableStateOf(step.comparator) }
                var cmpVal by remember(step) { mutableStateOf(step.compareValue) }
                var thenAct by remember(step) { mutableStateOf(step.thenAction) }
                var thenJump by remember(step) { mutableStateOf(step.thenJumpStepId) }
                var elseAct by remember(step) { mutableStateOf(step.elseAction) }
                var elseJump by remember(step) { mutableStateOf(step.elseJumpStepId) }

                OverlayTextField("Variable Name", varName, { varName = it })
                OverlayComparatorChips(comp) { comp = it }
                OverlayTextField("Compare Value", cmpVal, { cmpVal = it })
                OverlayBranchPicker("THEN", thenAct, thenJump, allSteps, StudioEmerald) { a, j -> thenAct = a; thenJump = j }
                OverlayBranchPicker("ELSE", elseAct, elseJump, allSteps, StudioAmber) { a, j -> elseAct = a; elseJump = j }
                OverlaySaveBtn {
                    onSave(
                        step.copy(
                            label = label,
                            enabled = enabled,
                            variableName = varName.trim().ifBlank { "counter" },
                            comparator = comp,
                            compareValue = cmpVal,
                            thenAction = thenAct,
                            thenJumpStepId = thenJump,
                            elseAction = elseAct,
                            elseJumpStepId = elseJump
                        )
                    )
                }
            }

            is JumpStep -> {
                var targetId by remember(step) { mutableStateOf(step.targetStepId) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    allSteps.forEachIndexed { idx, s ->
                        FilterChip(selected = targetId == s.id, onClick = { targetId = s.id }, label = { Text("#${idx + 1} ${s.label}", fontSize = 11.sp) })
                    }
                }
                OverlaySaveBtn { onSave(step.copy(label = label, enabled = enabled, targetStepId = targetId)) }
            }

            is CallFunctionStep -> {
                var selectedFnId by remember(step) { mutableStateOf(step.functionId) }
                var selectedFnName by remember(step) { mutableStateOf(step.functionName) }
                var repeatStr by remember(step) { mutableStateOf(step.repeatCount.toString()) }

                Text("Target Function", color = StudioViolet, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                if (functions.isEmpty()) {
                    Text(
                        text = "No functions defined in this macro yet. Tap + Fn in the Studio scope bar to create one.",
                        color = StudioMuted,
                        fontSize = 11.sp
                    )
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        functions.forEach { fn ->
                            FilterChip(
                                selected = selectedFnId == fn.id,
                                onClick = {
                                    selectedFnId = fn.id
                                    selectedFnName = fn.name
                                    if (label.isBlank() || label == "Call Function" || label.startsWith("Call ")) {
                                        label = "Call ${fn.name}"
                                    }
                                },
                                label = { Text("fn ${fn.name} (${fn.steps.size})", fontSize = 11.sp) }
                            )
                        }
                    }
                }
                OverlayTextField("Repeat Count", repeatStr, { repeatStr = it.filter(Char::isDigit) }, numeric = true)
                OverlaySaveBtn {
                    val resolvedName = functions.find { it.id == selectedFnId }?.name ?: selectedFnName
                    onSave(
                        step.copy(
                            label = label.ifBlank { if (resolvedName.isNotBlank()) "Call $resolvedName" else "Call Function" },
                            enabled = enabled,
                            functionId = selectedFnId,
                            functionName = resolvedName,
                            repeatCount = (repeatStr.toIntOrNull() ?: step.repeatCount).coerceAtLeast(1)
                        )
                    )
                }
            }

            is LoopEndStep -> OverlaySaveBtn { onSave(step.copy(label = label, enabled = enabled)) }
            is StopStep -> OverlaySaveBtn { onSave(step.copy(label = label, enabled = enabled)) }
        }
    }
}

@Composable
private fun OverlayTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    numeric: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = StudioMuted, fontSize = 12.sp) },
        singleLine = true,
        keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = StudioText,
            unfocusedTextColor = StudioText,
            focusedBorderColor = StudioCyan,
            unfocusedBorderColor = StudioBorder,
            focusedContainerColor = StudioWell,
            unfocusedContainerColor = StudioWell
        ),
        modifier = modifier
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OverlayComparatorChips(selected: VarComparator, onSelect: (VarComparator) -> Unit) {
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
            FilterChip(selected = selected == cmp, onClick = { onSelect(cmp) }, label = { Text(labels[cmp] ?: cmp.name) })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OverlayBranchPicker(
    title: String,
    action: BranchAction,
    jumpStepId: String?,
    allSteps: List<MacroStep>,
    accent: Color,
    onChanged: (BranchAction, String?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = accent, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BranchAction.entries.forEach { candidate ->
                FilterChip(
                    selected = action == candidate,
                    onClick = { onChanged(candidate, jumpStepId) },
                    label = { Text(candidate.name.replace('_', ' '), fontSize = 10.sp) }
                )
            }
        }
        if (action == BranchAction.JUMP_TO_STEP) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                allSteps.forEachIndexed { idx, s ->
                    FilterChip(
                        selected = jumpStepId == s.id,
                        onClick = { onChanged(action, s.id) },
                        label = { Text("#${idx + 1} ${s.label}", fontSize = 10.sp) }
                    )
                }
            }
        }
    }
}

@Composable
private fun OverlaySaveBtn(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = StudioCyan, contentColor = Color(0xFF04131A)),
        modifier = Modifier.fillMaxWidth().height(48.dp)
    ) {
        Text("Save Node", fontWeight = FontWeight.Bold)
    }
}

private fun stepAccentColor(step: MacroStep): Color = when (step) {
    is TapStep, is SwipeStep, is DelayStep, is KeyPressStep -> StudioCyan
    is IfColorStep, is IfImageStep -> StudioEmerald
    is SetVariableStep, is CallFunctionStep -> StudioViolet
    is LoopStartStep, is LoopEndStep, is IfVariableStep, is JumpStep -> StudioAmber
    is StopStep -> StudioRose
}

private fun stepSummaryText(step: MacroStep, functions: List<MacroFunction> = emptyList()): String = when (step) {
    is TapStep -> "(${step.x.toInt()}, ${step.y.toInt()}) • ${step.holdMs}ms"
    is SwipeStep -> "(${step.startX.toInt()},${step.startY.toInt()})->(${step.endX.toInt()},${step.endY.toInt()})"
    is DelayStep -> "${step.minMs}–${step.maxMs}ms"
    is KeyPressStep -> "KEY_${step.formattedBinding().uppercase()} • ${step.holdMs}ms"
    is SetVariableStep -> "${step.variableName} ${step.operation.name} ${step.valueExpression}"
    is LoopStartStep -> if (step.iterations <= 0) "WHILE ${step.conditionVar}" else "REPEAT ${step.iterations}x"
    is LoopEndStep -> "END LOOP"
    is IfColorStep -> "(${step.x},${step.y}) ${step.targetHexColor}"
    is IfImageStep -> "${step.templateName} >= ${step.minConfidencePct}%"
    is IfVariableStep -> "${step.variableName} ${step.comparator.name} ${step.compareValue}"
    is JumpStep -> "-> ${step.targetStepId.take(6)}"
    is CallFunctionStep -> {
        val fnName = functions.find { it.id == step.functionId }?.name ?: step.functionName.ifBlank { "Unassigned" }
        "fn $fnName() • ${step.repeatCount}x"
    }
    is StopStep -> "STOP"
}
