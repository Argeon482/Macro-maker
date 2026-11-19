package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.TemplateAssetEntity
import com.example.engine.RuntimeExecutionState
import com.example.model.CallFunctionStep
import com.example.model.DelayStep
import com.example.model.IfColorStep
import com.example.model.IfImageStep
import com.example.model.IfVariableStep
import com.example.model.InputInjectionMode
import com.example.model.JumpStep
import com.example.model.KeyPressStep
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
import com.example.model.TapStep
import com.example.model.TouchGuardPolicy
import com.example.model.withEnabled
import com.example.service.PickerMode
import com.example.ui.theme.AmberLogic
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldRun
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.RoseAlert
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.SlateWell
import com.example.ui.theme.TextDim
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletVar
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MacroEditorScreen(
    macro: MacroDefinition?,
    templates: List<TemplateAssetEntity>,
    executionState: RuntimeExecutionState,
    clipboardItem: ScriptClipboardItem?,
    onBack: () -> Unit,
    onSaveMacro: (MacroDefinition) -> Unit,
    onCopyStep: (MacroStep) -> Unit,
    onCopyFunction: (MacroFunction) -> Unit,
    onClearClipboard: () -> Unit,
    onNotify: (String) -> Unit,
    onLaunchDock: (MacroDefinition) -> Unit,
    onRunNow: (MacroDefinition) -> Unit,
    onPickOnScreen: (MacroDefinition, MacroStep, PickerMode, String?) -> Unit,
    onCropTemplate: (
        templateId: String,
        trimLeft: Int,
        trimTop: Int,
        trimRight: Int,
        trimBottom: Int,
        saveAsNew: Boolean,
        onCropped: (TemplateAssetEntity) -> Unit
    ) -> Unit = { _, _, _, _, _, _, _ -> },
    onExportJson: (MacroDefinition) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddStep by remember { mutableStateOf(false) }
    var editingStep by remember { mutableStateOf<MacroStep?>(null) }
    var showMacroSettings by remember { mutableStateOf(false) }
    var activeFunctionId by remember(macro?.id) { mutableStateOf<String?>(null) }
    var showCreateFunctionDialog by remember { mutableStateOf(false) }
    var renamingFunction by remember { mutableStateOf<MacroFunction?>(null) }

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

    val activeFunction = activeFunctionId?.let { fid -> macro.functions.find { it.id == fid } }
    if (activeFunctionId != null && activeFunction == null) {
        activeFunctionId = null
    }
    val displayedSteps = activeFunction?.steps ?: macro.steps

    fun updateDisplayedSteps(newSteps: List<MacroStep>) {
        if (activeFunction == null) {
            onSaveMacro(macro.copy(steps = newSteps))
        } else {
            val updatedFunctions = macro.functions.map { fn ->
                if (fn.id == activeFunction.id) fn.copy(steps = newSteps) else fn
            }
            onSaveMacro(macro.copy(functions = updatedFunctions))
        }
    }

    // Compute loop nesting depth per step for visual indentation
    val depths = remember(displayedSteps) {
        var d = 0
        displayedSteps.map { step ->
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
                            text = "${macro.steps.size} main • ${macro.functions.size} fn • ${if (macro.loopCount <= 0) "∞" else "${macro.loopCount}x"} • ${macro.injectionMode.displayLabel}",
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
                containerColor = if (activeFunction != null) VioletVar else ElectricCyan,
                contentColor = Color(0xFF04131A),
                modifier = Modifier.testTag("add_step_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (activeFunction != null) "Add to ${activeFunction.name}" else "Add Step",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp)
            ) {
                // Scope Bar: Main Flow + Functions + New Fn
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = activeFunction == null,
                        onClick = { activeFunctionId = null },
                        label = { Text("Main Flow (${macro.steps.size})") },
                        modifier = Modifier.testTag("scope_main_chip")
                    )
                    macro.functions.forEach { fn ->
                        FilterChip(
                            selected = activeFunction?.id == fn.id,
                            onClick = { activeFunctionId = fn.id },
                            leadingIcon = {
                                Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(15.dp))
                            },
                            label = { Text("${fn.name} (${fn.steps.size})") },
                            modifier = Modifier.testTag("scope_fn_${fn.id}")
                        )
                    }
                    FilterChip(
                        selected = false,
                        onClick = { showCreateFunctionDialog = true },
                        leadingIcon = {
                            Icon(Icons.Default.Add, contentDescription = null, tint = VioletVar, modifier = Modifier.size(16.dp))
                        },
                        label = { Text("New Fn", color = VioletVar) },
                        modifier = Modifier.testTag("create_function_chip")
                    )
                }

                // Active Clipboard Bar for quick Paste of Step or Function
                if (clipboardItem != null) {
                    Surface(
                        color = SlateWell,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (clipboardItem is ScriptClipboardItem.FunctionClip) VioletVar else ElectricCyan),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        Icons.Default.ContentPaste,
                                        contentDescription = null,
                                        tint = if (clipboardItem is ScriptClipboardItem.FunctionClip) VioletVar else ElectricCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Clipboard: ${clipboardItem.summaryLabel}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = onClearClipboard,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear clipboard", tint = TextSecondary, modifier = Modifier.size(16.dp))
                                }
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                when (clipboardItem) {
                                    is ScriptClipboardItem.SingleStep -> {
                                        Button(
                                            onClick = {
                                                val cloned = MacroCloner.cloneStep(clipboardItem.step)
                                                updateDisplayedSteps(displayedSteps + cloned)
                                                onNotify("Pasted '${cloned.label}'")
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = Color(0xFF04131A)),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                            modifier = Modifier.height(36.dp).testTag("paste_step_btn")
                                        ) {
                                            Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(15.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = if (activeFunction != null) "Paste into ${activeFunction.name}" else "Paste into Main",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        }
                                    }

                                    is ScriptClipboardItem.FunctionClip -> {
                                        Button(
                                            onClick = {
                                                val clonedFn = MacroCloner.cloneFunction(
                                                    clipboardItem.function,
                                                    existingNames = macro.functions.map { it.name }.toSet()
                                                )
                                                onSaveMacro(macro.copy(functions = macro.functions + clonedFn))
                                                activeFunctionId = clonedFn.id
                                                onNotify("Pasted function '${clonedFn.name}'")
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = VioletVar, contentColor = Color(0xFF0E081D)),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                            modifier = Modifier.height(36.dp).testTag("paste_as_function_btn")
                                        ) {
                                            Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(15.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Paste as New Fn", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                        }
                                        if (clipboardItem.function.steps.isNotEmpty()) {
                                            OutlinedButton(
                                                onClick = {
                                                    val clonedSteps = MacroCloner.cloneStepsWithRemappedIds(clipboardItem.function.steps)
                                                    updateDisplayedSteps(displayedSteps + clonedSteps)
                                                    onNotify("Pasted ${clonedSteps.size} steps from '${clipboardItem.function.name}'")
                                                },
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                modifier = Modifier.height(36.dp).testTag("paste_fn_steps_btn")
                                            ) {
                                                Text(
                                                    text = "Paste ${clipboardItem.function.steps.size} Steps Here",
                                                    color = ElectricCyan,
                                                    style = MaterialTheme.typography.labelMedium
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Active Function Header Card (Rename / Copy / Call in Main / Delete)
                if (activeFunction != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SlateSurface),
                        border = BorderStroke(1.dp, VioletVar.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Code, contentDescription = null, tint = VioletVar, modifier = Modifier.size(20.dp))
                                    Column {
                                        Text(
                                            text = "fn ${activeFunction.name}",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = TextPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${activeFunction.steps.size} step(s) • Reusable chunk",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary
                                        )
                                    }
                                }
                                Row {
                                    IconButton(
                                        onClick = { renamingFunction = activeFunction },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Rename function", tint = TextPrimary, modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(
                                        onClick = {
                                            onCopyFunction(activeFunction)
                                            onNotify("Copied function '${activeFunction.name}'")
                                        },
                                        modifier = Modifier.size(40.dp).testTag("copy_function_btn")
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy function", tint = ElectricCyan, modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(
                                        onClick = {
                                            val remaining = macro.functions.filterNot { it.id == activeFunction.id }
                                            activeFunctionId = null
                                            onSaveMacro(macro.copy(functions = remaining))
                                            onNotify("Deleted function '${activeFunction.name}'")
                                        },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete function", tint = RoseAlert, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        val callStep = CallFunctionStep(
                                            label = "Call ${activeFunction.name}",
                                            functionId = activeFunction.id,
                                            functionName = activeFunction.name,
                                            repeatCount = 1
                                        )
                                        onSaveMacro(macro.copy(steps = macro.steps + callStep))
                                        onNotify("Added 'Call ${activeFunction.name}' to Main Flow")
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = EmeraldRun, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Add Call to Main Flow", color = EmeraldRun, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (displayedSteps.isEmpty()) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = SlateSurface),
                                border = BorderStroke(1.dp, HairlineBorder),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (activeFunction != null) {
                                        "Function '${activeFunction.name}' has no steps yet. Tap Add or Paste steps to build this chunk, then call it from Main Flow."
                                    } else {
                                        "This flow has no steps yet. Tap Add Step to add keys, taps, swipes, color/image triggers, loops, or Call Function blocks."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    } else {
                        itemsIndexed(displayedSteps, key = { _, s -> s.id }) { index, step ->
                            val isExecuting = executionState.activeMacroId == macro.id && executionState.currentStepId == step.id
                            val canPasteBelow = clipboardItem is ScriptClipboardItem.SingleStep
                            StepNodeCard(
                                index = index,
                                total = displayedSteps.size,
                                depth = depths.getOrElse(index) { 0 },
                                step = step,
                                functions = macro.functions,
                                isExecuting = isExecuting,
                                canPasteBelow = canPasteBelow,
                                onEdit = { editingStep = step },
                                onOpenFunction = if (step is CallFunctionStep) {
                                    {
                                        val target = macro.functions.find { it.id == step.functionId }
                                            ?: macro.functions.find { it.name == step.functionName }
                                        if (target != null) activeFunctionId = target.id
                                    }
                                } else null,
                                onToggleEnabled = { enabled ->
                                    val updated = displayedSteps.toMutableList()
                                    updated[index] = step.withEnabled(enabled)
                                    updateDisplayedSteps(updated)
                                },
                                onCopy = {
                                    onCopyStep(step)
                                    onNotify("Copied '${step.label}'")
                                },
                                onPasteBelow = {
                                    val clip = clipboardItem as? ScriptClipboardItem.SingleStep ?: return@StepNodeCard
                                    val cloned = MacroCloner.cloneStep(clip.step)
                                    val updated = displayedSteps.toMutableList()
                                    updated.add(index + 1, cloned)
                                    updateDisplayedSteps(updated)
                                    onNotify("Pasted '${cloned.label}' at #${index + 2}")
                                },
                                onMoveUp = {
                                    if (index > 0) {
                                        val updated = displayedSteps.toMutableList()
                                        val item = updated.removeAt(index)
                                        updated.add(index - 1, item)
                                        updateDisplayedSteps(updated)
                                    }
                                },
                                onMoveDown = {
                                    if (index < displayedSteps.lastIndex) {
                                        val updated = displayedSteps.toMutableList()
                                        val item = updated.removeAt(index)
                                        updated.add(index + 1, item)
                                        updateDisplayedSteps(updated)
                                    }
                                },
                                onDelete = {
                                    val updated = displayedSteps.toMutableList()
                                    updated.removeAt(index)
                                    updateDisplayedSteps(updated)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddStep) {
        AddStepBottomSheet(
            functions = macro.functions,
            onDismiss = { showAddStep = false },
            onAddSteps = { newSteps ->
                showAddStep = false
                updateDisplayedSteps(displayedSteps + newSteps)
                if (newSteps.size == 1) {
                    editingStep = newSteps.first()
                }
            }
        )
    }

    editingStep?.let { target ->
        EditStepBottomSheet(
            step = target,
            allSteps = displayedSteps,
            functions = macro.functions,
            templates = templates,
            onPickOnScreen = { updatedStep, mode ->
                editingStep = null
                val updated = displayedSteps.map { if (it.id == updatedStep.id) updatedStep else it }
                val updatedMacro = if (activeFunction == null) {
                    macro.copy(steps = updated)
                } else {
                    macro.copy(functions = macro.functions.map { fn ->
                        if (fn.id == activeFunction.id) fn.copy(steps = updated) else fn
                    })
                }
                onSaveMacro(updatedMacro)
                onPickOnScreen(updatedMacro, updatedStep, mode, activeFunction?.id)
            },
            onCropTemplate = onCropTemplate,
            onSave = { updatedStep ->
                val updated = displayedSteps.map { if (it.id == updatedStep.id) updatedStep else it }
                updateDisplayedSteps(updated)
                editingStep = null
            },
            onDismiss = { editingStep = null }
        )
    }

    if (showCreateFunctionDialog) {
        FunctionNameDialog(
            title = "New Function",
            initialName = "Function ${macro.functions.size + 1}",
            confirmLabel = "Create",
            onDismiss = { showCreateFunctionDialog = false },
            onConfirm = { name ->
                val unique = MacroCloner.generateUniqueName(name, macro.functions.map { it.name }.toSet())
                val created = MacroFunction(id = UUID.randomUUID().toString(), name = unique)
                onSaveMacro(macro.copy(functions = macro.functions + created))
                activeFunctionId = created.id
                showCreateFunctionDialog = false
                onNotify("Created function '$unique'")
            }
        )
    }

    renamingFunction?.let { fnToRename ->
        FunctionNameDialog(
            title = "Rename Function",
            initialName = fnToRename.name,
            confirmLabel = "Rename",
            onDismiss = { renamingFunction = null },
            onConfirm = { nextName ->
                val clean = nextName.trim().ifBlank { fnToRename.name }
                val updatedFunctions = macro.functions.map { fn ->
                    val renamed = if (fn.id == fnToRename.id) fn.copy(name = clean) else fn
                    renamed.copy(
                        steps = renamed.steps.map { s ->
                            if (s is CallFunctionStep && s.functionId == fnToRename.id) {
                                s.copy(functionName = clean)
                            } else s
                        }
                    )
                }
                val updatedMainSteps = macro.steps.map { s ->
                    if (s is CallFunctionStep && s.functionId == fnToRename.id) {
                        s.copy(functionName = clean)
                    } else s
                }
                onSaveMacro(macro.copy(steps = updatedMainSteps, functions = updatedFunctions))
                renamingFunction = null
            }
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
private fun FunctionNameDialog(
    title: String,
    initialName: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SlateSurface,
        title = { Text(title, color = TextPrimary) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Function Name") },
                placeholder = { Text("e.g. Buffs, Heal Check, Loot") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("function_name_input")
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name) },
                colors = ButtonDefaults.buttonColors(containerColor = VioletVar, contentColor = Color(0xFF0E081D)),
                modifier = Modifier.testTag("function_name_confirm_btn")
            ) {
                Text(confirmLabel, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

@Composable
private fun StepNodeCard(
    index: Int,
    total: Int,
    depth: Int,
    step: MacroStep,
    functions: List<MacroFunction>,
    isExecuting: Boolean,
    canPasteBelow: Boolean,
    onEdit: () -> Unit,
    onOpenFunction: (() -> Unit)?,
    onToggleEnabled: (Boolean) -> Unit,
    onCopy: () -> Unit,
    onPasteBelow: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit
) {
    val accent = when (step) {
        is TapStep, is SwipeStep, is DelayStep, is KeyPressStep -> ElectricCyan
        is IfColorStep, is IfImageStep -> EmeraldRun
        is SetVariableStep, is CallFunctionStep -> VioletVar
        is LoopStartStep, is LoopEndStep, is IfVariableStep, is JumpStep -> AmberLogic
        is StopStep -> RoseAlert
    }

    val summary = remember(step, functions) {
        when (step) {
            is TapStep -> "(${step.x.toInt()}, ${step.y.toInt()}) • hold ${step.holdMs}ms • ±${step.jitterPx}px"
            is SwipeStep -> "(${step.startX.toInt()},${step.startY.toInt()}) -> (${step.endX.toInt()},${step.endY.toInt()}) • ${step.durationMs}ms"
            is DelayStep -> if (step.minMs == step.maxMs) "${step.minMs} ms" else "${step.minMs}–${step.maxMs} ms"
            is KeyPressStep -> "KEY_${step.formattedBinding().uppercase()} • ${step.holdMs}ms"
            is SetVariableStep -> "${step.variableName} ${step.operation.name.lowercase()} ${step.valueExpression}"
            is LoopStartStep -> if (step.iterations <= 0) "WHILE ${step.conditionVar} ${step.conditionOp.name} ${step.conditionValue}" else "REPEAT ${step.iterations}x"
            is LoopEndStep -> "END LOOP BLOCK"
            is IfColorStep -> "(${step.x}, ${step.y}) == ${step.targetHexColor} (±${step.tolerancePct}%)"
            is IfImageStep -> "${step.templateName} >= ${step.minConfidencePct}%"
            is IfVariableStep -> "${step.variableName} ${step.comparator.name} ${step.compareValue}"
            is JumpStep -> "-> ${step.targetStepId.take(6)}"
            is CallFunctionStep -> {
                val resolved = functions.find { it.id == step.functionId }
                val fnName = resolved?.name ?: step.functionName.ifBlank { "Unassigned" }
                val rep = if (step.repeatCount > 1) " • ${step.repeatCount}x" else ""
                "CALL fn $fnName (${resolved?.steps?.size ?: 0} steps)$rep"
            }
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
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(64.dp)
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

                    Switch(
                        checked = step.enabled,
                        onCheckedChange = onToggleEnabled,
                        modifier = Modifier.padding(end = 10.dp)
                    )
                }

                HorizontalDivider(color = HairlineBorder.copy(alpha = 0.6f))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onMoveUp, enabled = index > 0, modifier = Modifier.size(44.dp)) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = "Move step up", tint = if (index > 0) TextSecondary else TextDim, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = onMoveDown, enabled = index < total - 1, modifier = Modifier.size(44.dp)) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = "Move step down", tint = if (index < total - 1) TextSecondary else TextDim, modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = onCopy,
                            modifier = Modifier.size(44.dp).testTag("copy_step_btn_$index")
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy step", tint = ElectricCyan, modifier = Modifier.size(17.dp))
                        }
                        if (canPasteBelow) {
                            IconButton(
                                onClick = onPasteBelow,
                                modifier = Modifier.size(44.dp).testTag("paste_below_btn_$index")
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "Paste copied step below", tint = EmeraldRun, modifier = Modifier.size(17.dp))
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (onOpenFunction != null) {
                            TextButton(onClick = onOpenFunction) {
                                Icon(Icons.Default.Code, contentDescription = null, tint = VioletVar, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Open Fn", color = VioletVar, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.size(44.dp)) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete step", tint = RoseAlert, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
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
    var injectionMode by remember(macro) { mutableStateOf(macro.injectionMode) }
    var touchGuardPolicy by remember(macro) { mutableStateOf(macro.touchGuardPolicy) }

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
                Text("Pointer Injection Source", style = MaterialTheme.typography.labelMedium, color = ElectricCyan)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    InputInjectionMode.entries.forEach { mode ->
                        FilterChip(
                            selected = injectionMode == mode,
                            onClick = { injectionMode = mode },
                            label = { Text(mode.displayLabel) }
                        )
                    }
                }
                Text(
                    text = injectionMode.shortDesc,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Text("User Touch Guard", style = MaterialTheme.typography.labelMedium, color = AmberLogic)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TouchGuardPolicy.entries.forEach { policy ->
                        FilterChip(
                            selected = touchGuardPolicy == policy,
                            onClick = { touchGuardPolicy = policy },
                            label = { Text(policy.displayLabel) }
                        )
                    }
                }
                Text(
                    text = touchGuardPolicy.shortDesc,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
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
                            pollingIntervalMs = (pollingMs.toLongOrNull() ?: macro.pollingIntervalMs).coerceAtLeast(50L),
                            injectionMode = injectionMode,
                            touchGuardPolicy = touchGuardPolicy
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
