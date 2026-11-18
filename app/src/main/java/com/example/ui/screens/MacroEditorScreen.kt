package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.TemplateAssetEntity
import com.example.engine.RuntimeExecutionState
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
import com.example.ui.theme.AmberLogic
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldRun
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.RoseAlert
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.TextDim
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletVar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MacroEditorScreen(
    macro: MacroDefinition?,
    templates: List<TemplateAssetEntity>,
    executionState: RuntimeExecutionState,
    onBack: () -> Unit,
    onSaveMacro: (MacroDefinition) -> Unit,
    onLaunchDock: (MacroDefinition) -> Unit,
    onRunNow: (MacroDefinition) -> Unit,
    onPickOnScreen: (MacroDefinition, MacroStep) -> Unit,
    onExportJson: (MacroDefinition) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddStep by remember { mutableStateOf(false) }
    var editingStep by remember { mutableStateOf<MacroStep?>(null) }
    var showMacroSettings by remember { mutableStateOf(false) }

    if (macro == null) {
        Scaffold(
            containerColor = ObsidianBg,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = ObsidianBg),
                    title = { Text("Macro Not Found", color = TextPrimary) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                        }
                    }
                )
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("This macro no longer exists.", color = TextSecondary)
            }
        }
        return
    }

    // Compute loop nesting depth per step for visual indentation
    val depths = remember(macro.steps) {
        var d = 0
        macro.steps.map { step ->
            if (step is LoopEndStep) d = (d - 1).coerceAtLeast(0)
            val currentDepth = d
            if (step is LoopStartStep) d++
            currentDepth
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ObsidianBg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ObsidianBg),
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("editor_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to studio", tint = TextPrimary)
                    }
                },
                title = {
                    Column {
                        Text(
                            text = macro.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${macro.steps.size} nodes • ${if (macro.loopCount <= 0) "∞ passes" else "${macro.loopCount} pass"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showMacroSettings = true }, modifier = Modifier.testTag("macro_settings_btn")) {
                        Icon(Icons.Default.Tune, contentDescription = "Macro settings", tint = TextPrimary)
                    }
                    IconButton(onClick = { onExportJson(macro) }) {
                        Icon(Icons.Default.Download, contentDescription = "Export JSON", tint = TextPrimary)
                    }
                    IconButton(onClick = { onLaunchDock(macro) }, modifier = Modifier.testTag("editor_launch_dock_btn")) {
                        Icon(Icons.Default.RocketLaunch, contentDescription = "Launch floating dock", tint = ElectricCyan)
                    }
                    IconButton(onClick = { onRunNow(macro) }, modifier = Modifier.testTag("editor_run_now_btn")) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Run macro", tint = EmeraldRun)
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddStep = true },
                containerColor = ElectricCyan,
                contentColor = Color(0xFF04131A),
                modifier = Modifier.testTag("add_step_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add Step", fontWeight = FontWeight.Bold)
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (macro.steps.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SlateSurface),
                            border = BorderStroke(1.dp, HairlineBorder),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "This flow has no steps yet. Tap Add Step to add taps, swipes, color/image triggers, loops, or variables.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                } else {
                    itemsIndexed(macro.steps, key = { _, s -> s.id }) { index, step ->
                        val isExecuting = executionState.activeMacroId == macro.id && executionState.currentStepId == step.id
                        StepNodeCard(
                            index = index,
                            total = macro.steps.size,
                            depth = depths.getOrElse(index) { 0 },
                            step = step,
                            isExecuting = isExecuting,
                            onEdit = { editingStep = step },
                            onToggleEnabled = { enabled ->
                                val updated = macro.steps.toMutableList()
                                updated[index] = step.withEnabled(enabled)
                                onSaveMacro(macro.copy(steps = updated))
                            },
                            onMoveUp = {
                                if (index > 0) {
                                    val updated = macro.steps.toMutableList()
                                    val item = updated.removeAt(index)
                                    updated.add(index - 1, item)
                                    onSaveMacro(macro.copy(steps = updated))
                                }
                            },
                            onMoveDown = {
                                if (index < macro.steps.lastIndex) {
                                    val updated = macro.steps.toMutableList()
                                    val item = updated.removeAt(index)
                                    updated.add(index + 1, item)
                                    onSaveMacro(macro.copy(steps = updated))
                                }
                            },
                            onDelete = {
                                val updated = macro.steps.toMutableList()
                                updated.removeAt(index)
                                onSaveMacro(macro.copy(steps = updated))
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddStep) {
        AddStepBottomSheet(
            onDismiss = { showAddStep = false },
            onAddSteps = { newSteps ->
                showAddStep = false
                onSaveMacro(macro.copy(steps = macro.steps + newSteps))
                if (newSteps.size == 1) {
                    editingStep = newSteps.first()
                }
            }
        )
    }

    editingStep?.let { target ->
        EditStepBottomSheet(
            step = target,
            allSteps = macro.steps,
            templates = templates,
            onPickOnScreen = { updatedStep ->
                editingStep = null
                onPickOnScreen(macro, updatedStep)
            },
            onSave = { updatedStep ->
                val updated = macro.steps.map { if (it.id == updatedStep.id) updatedStep else it }
                onSaveMacro(macro.copy(steps = updated))
                editingStep = null
            },
            onDismiss = { editingStep = null }
        )
    }

    if (showMacroSettings) {
        MacroSettingsDialog(
            macro = macro,
            onDismiss = { showMacroSettings = false },
            onConfirm = { updated ->
                showMacroSettings = false
                onSaveMacro(updated)
            }
        )
    }
}

@Composable
private fun StepNodeCard(
    index: Int,
    total: Int,
    depth: Int,
    step: MacroStep,
    isExecuting: Boolean,
    onEdit: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit
) {
    val accent = when (step) {
        is TapStep, is SwipeStep, is DelayStep, is KeyPressStep -> ElectricCyan
        is IfColorStep, is IfImageStep -> EmeraldRun
        is SetVariableStep -> VioletVar
        is LoopStartStep, is LoopEndStep, is IfVariableStep, is JumpStep -> AmberLogic
        is StopStep -> RoseAlert
    }

    val summary = remember(step) {
        when (step) {
            is TapStep -> "(${step.x.toInt()}, ${step.y.toInt()}) • hold ${step.holdMs}ms • ±${step.jitterPx}px"
            is SwipeStep -> "(${step.startX.toInt()},${step.startY.toInt()}) -> (${step.endX.toInt()},${step.endY.toInt()}) • ${step.durationMs}ms"
            is DelayStep -> if (step.minMs == step.maxMs) "${step.minMs} ms" else "${step.minMs}–${step.maxMs} ms"
            is KeyPressStep -> "KEY_${step.keyAction.displayLabel.uppercase()}"
            is SetVariableStep -> "${step.variableName} ${step.operation.name.lowercase()} ${step.valueExpression}"
            is LoopStartStep -> if (step.iterations <= 0) "WHILE ${step.conditionVar} ${step.conditionOp.name} ${step.conditionValue}" else "REPEAT ${step.iterations}x"
            is LoopEndStep -> "END LOOP BLOCK"
            is IfColorStep -> "(${step.x}, ${step.y}) == ${step.targetHexColor} (±${step.tolerancePct}%)"
            is IfImageStep -> "${step.templateName} >= ${step.minConfidencePct}%"
            is IfVariableStep -> "${step.variableName} ${step.comparator.name} ${step.compareValue}"
            is JumpStep -> "-> ${step.targetStepId.take(6)}"
            is StopStep -> "HALT EXECUTION"
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (depth.coerceAtMost(4) * 14).dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (step.enabled) SlateSurface else SlateSurface.copy(alpha = 0.5f)
            ),
            border = BorderStroke(
                width = if (isExecuting) 2.dp else 1.dp,
                color = if (isExecuting) EmeraldRun else HairlineBorder
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onEdit)
                .testTag("step_card_$index")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(70.dp)
                        .background(if (step.enabled) accent else TextDim)
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "#${index + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = accent
                        )
                        Text(
                            text = step.label,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (step.enabled) TextPrimary else TextDim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = onMoveUp, enabled = index > 0, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = "Move step up", tint = if (index > 0) TextSecondary else TextDim, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onMoveDown, enabled = index < total - 1, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = "Move step down", tint = if (index < total - 1) TextSecondary else TextDim, modifier = Modifier.size(16.dp))
                }
                Switch(
                    checked = step.enabled,
                    onCheckedChange = onToggleEnabled,
                    modifier = Modifier.padding(end = 4.dp)
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete step", tint = RoseAlert, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun MacroSettingsDialog(
    macro: MacroDefinition,
    onDismiss: () -> Unit,
    onConfirm: (MacroDefinition) -> Unit
) {
    var name by remember(macro) { mutableStateOf(macro.name) }
    var target by remember(macro) { mutableStateOf(macro.targetAppLabel) }
    var loopCount by remember(macro) { mutableStateOf(macro.loopCount.toString()) }
    var pollingMs by remember(macro) { mutableStateOf(macro.pollingIntervalMs.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SlateSurface,
        title = { Text("Macro Settings", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it },
                    label = { Text("Target App Label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = loopCount,
                    onValueChange = { loopCount = it.filter(Char::isDigit) },
                    label = { Text("Macro Passes (0 = Infinite)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pollingMs,
                    onValueChange = { pollingMs = it.filter(Char::isDigit) },
                    label = { Text("Vision Polling Interval (ms)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        macro.copy(
                            name = name.trim().ifBlank { macro.name },
                            targetAppLabel = target.trim().ifBlank { "Any Screen" },
                            loopCount = loopCount.toIntOrNull() ?: macro.loopCount,
                            pollingIntervalMs = (pollingMs.toLongOrNull() ?: macro.pollingIntervalMs).coerceAtLeast(50L)
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = Color(0xFF04131A))
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

private fun MacroStep.withEnabled(enabled: Boolean): MacroStep = when (this) {
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
    is StopStep -> copy(enabled = enabled)
}
