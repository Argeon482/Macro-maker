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
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.DeleteOutline
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.TemplateAssetEntity
import com.example.model.BranchAction
import com.example.model.DelayStep
import com.example.model.IfColorStep
import com.example.model.IfImageStep
import com.example.model.IfVariableStep
import com.example.model.InputInjectionMode
import com.example.model.JumpStep
import com.example.model.KeyPressStep
import com.example.model.LoopEndStep
import com.example.model.LoopStartStep
import com.example.model.MacroDefinition
import com.example.model.MacroStep
import com.example.model.SetVariableStep
import com.example.model.StopStep
import com.example.model.SwipeStep
import com.example.model.SystemKeyAction
import com.example.model.TapStep
import com.example.model.VarComparator
import com.example.model.VarOperation
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
    templates: List<TemplateAssetEntity>,
    screenCaptureActive: Boolean,
    onSelectMacro: (MacroDefinition) -> Unit,
    onCreateMacro: (String) -> Unit,
    onUpdateMacro: (MacroDefinition) -> Unit,
    onLaunchPicker: (MacroDefinition, MacroStep, PickerMode) -> Unit,
    onOpenPins: () -> Unit,
    onRequestVision: () -> Unit,
    onRunMacro: (MacroDefinition) -> Unit,
    onClose: () -> Unit
) {
    var editingStep by remember(activeMacro?.id, initialEditingStepId) {
        mutableStateOf(
            if (initialEditingStepId != null) {
                activeMacro?.steps?.find { it.id == initialEditingStepId }
            } else {
                null
            }
        )
    }
    var showAddPanel by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var newMacroName by remember { mutableStateOf("") }
    var creatingMacro by remember { mutableStateOf(false) }

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
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (editingStep != null || showAddPanel || showSettings || creatingMacro) {
                            IconButton(
                                onClick = {
                                    editingStep = null
                                    showAddPanel = false
                                    showSettings = false
                                    creatingMacro = false
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = StudioText)
                            }
                        }
                        Column {
                            Text(
                                text = when {
                                    editingStep != null -> "Edit Node"
                                    showAddPanel -> "Add Automation Node"
                                    showSettings -> "Macro Settings"
                                    creatingMacro -> "New Macro"
                                    else -> "Overlay Studio"
                                },
                                color = StudioText,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = activeMacro?.name ?: "Select or create a macro",
                                color = StudioCyan,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (activeMacro != null && editingStep == null && !showAddPanel) {
                            IconButton(onClick = onOpenPins, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Default.MyLocation, contentDescription = "Edit pins on screen", tint = StudioCyan)
                            }
                        }
                        if (!screenCaptureActive) {
                            IconButton(onClick = onRequestVision, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Default.ScreenshotMonitor, contentDescription = "Enable Vision", tint = StudioAmber)
                            }
                        }
                        if (activeMacro != null && editingStep == null && !showAddPanel) {
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
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = StudioCyan, contentColor = Color(0xFF04131A)),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Text("Create Macro", fontWeight = FontWeight.Bold)
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
                            onAdd = { newSteps ->
                                val updated = activeMacro.copy(steps = activeMacro.steps + newSteps)
                                onUpdateMacro(updated)
                                showAddPanel = false
                                if (newSteps.size == 1) {
                                    editingStep = newSteps.first()
                                }
                            }
                        )
                    }

                    editingStep != null && activeMacro != null -> {
                        val currentStep = activeMacro.steps.find { it.id == editingStep?.id } ?: editingStep!!
                        OverlayStepEditor(
                            step = currentStep,
                            allSteps = activeMacro.steps,
                            templates = templates,
                            screenCaptureActive = screenCaptureActive,
                            onRequestVision = onRequestVision,
                            onLaunchPicker = { updatedStep, mode ->
                                val nextSteps = activeMacro.steps.map { if (it.id == updatedStep.id) updatedStep else it }
                                val savedMacro = activeMacro.copy(steps = nextSteps)
                                onUpdateMacro(savedMacro)
                                onLaunchPicker(savedMacro, updatedStep, mode)
                            },
                            onSave = { updatedStep ->
                                val nextSteps = activeMacro.steps.map { if (it.id == updatedStep.id) updatedStep else it }
                                onUpdateMacro(activeMacro.copy(steps = nextSteps))
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
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
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
                                        onClick = { onSelectMacro(m) },
                                        label = { Text(m.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                    )
                                }
                            }

                            if (activeMacro == null) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("Tap + New to create a macro.", color = StudioMuted)
                                }
                            } else {
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
                                        Text("Add Step", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }

                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    itemsIndexed(activeMacro.steps, key = { _, s -> s.id }) { index, step ->
                                        val accent = stepAccentColor(step)
                                        Surface(
                                            color = StudioPanel,
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(1.dp, StudioBorder),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { editingStep = step }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .width(4.dp)
                                                        .height(40.dp)
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
                                                        text = stepSummaryText(step),
                                                        color = StudioMuted,
                                                        fontSize = 11.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                IconButton(
                                                    onClick = {
                                                        if (index > 0) {
                                                            val list = activeMacro.steps.toMutableList()
                                                            val item = list.removeAt(index)
                                                            list.add(index - 1, item)
                                                            onUpdateMacro(activeMacro.copy(steps = list))
                                                        }
                                                    },
                                                    enabled = index > 0,
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Icon(Icons.Default.ArrowUpward, contentDescription = "Move up", tint = StudioMuted, modifier = Modifier.size(16.dp))
                                                }
                                                IconButton(
                                                    onClick = {
                                                        if (index < activeMacro.steps.lastIndex) {
                                                            val list = activeMacro.steps.toMutableList()
                                                            val item = list.removeAt(index)
                                                            list.add(index + 1, item)
                                                            onUpdateMacro(activeMacro.copy(steps = list))
                                                        }
                                                    },
                                                    enabled = index < activeMacro.steps.lastIndex,
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Icon(Icons.Default.ArrowDownward, contentDescription = "Move down", tint = StudioMuted, modifier = Modifier.size(16.dp))
                                                }
                                                IconButton(
                                                    onClick = {
                                                        val list = activeMacro.steps.toMutableList()
                                                        list.removeAt(index)
                                                        onUpdateMacro(activeMacro.copy(steps = list))
                                                    },
                                                    modifier = Modifier.size(36.dp)
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

        Button(
            onClick = {
                onSave(
                    macro.copy(
                        name = name.trim().ifBlank { macro.name },
                        targetAppLabel = target.trim().ifBlank { macro.targetAppLabel },
                        loopCount = loops.toIntOrNull() ?: macro.loopCount,
                        pollingIntervalMs = (poll.toLongOrNull() ?: macro.pollingIntervalMs).coerceAtLeast(50L),
                        injectionMode = injectionMode
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
        OverlayAddItem("System Key", "Back, Home, or Recents", StudioCyan) { onAdd(listOf(KeyPressStep())) }
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
    templates: List<TemplateAssetEntity>,
    screenCaptureActive: Boolean,
    onRequestVision: () -> Unit,
    onLaunchPicker: (MacroStep, PickerMode) -> Unit,
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
                var keyAction by remember(step) { mutableStateOf(step.keyAction) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SystemKeyAction.entries.forEach { a ->
                        FilterChip(selected = keyAction == a, onClick = { keyAction = a }, label = { Text(a.displayLabel) })
                    }
                }
                OverlaySaveBtn { onSave(step.copy(label = label, enabled = enabled, keyAction = keyAction)) }
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
    is SetVariableStep -> StudioViolet
    is LoopStartStep, is LoopEndStep, is IfVariableStep, is JumpStep -> StudioAmber
    is StopStep -> StudioRose
}

private fun stepSummaryText(step: MacroStep): String = when (step) {
    is TapStep -> "(${step.x.toInt()}, ${step.y.toInt()}) • ${step.holdMs}ms"
    is SwipeStep -> "(${step.startX.toInt()},${step.startY.toInt()})->(${step.endX.toInt()},${step.endY.toInt()})"
    is DelayStep -> "${step.minMs}–${step.maxMs}ms"
    is KeyPressStep -> step.keyAction.displayLabel
    is SetVariableStep -> "${step.variableName} ${step.operation.name} ${step.valueExpression}"
    is LoopStartStep -> if (step.iterations <= 0) "WHILE ${step.conditionVar}" else "REPEAT ${step.iterations}x"
    is LoopEndStep -> "END LOOP"
    is IfColorStep -> "(${step.x},${step.y}) ${step.targetHexColor}"
    is IfImageStep -> "${step.templateName} >= ${step.minConfidencePct}%"
    is IfVariableStep -> "${step.variableName} ${step.comparator.name} ${step.compareValue}"
    is JumpStep -> "-> ${step.targetStepId.take(6)}"
    is StopStep -> "STOP"
}
