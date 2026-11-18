package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.ScreenshotMonitor
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.UploadFile
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.engine.ExecutionStatus
import com.example.engine.RuntimeExecutionState
import com.example.model.IfColorStep
import com.example.model.IfImageStep
import com.example.model.LoopStartStep
import com.example.model.MacroDefinition
import com.example.shizuku.ShizukuConnectionState
import com.example.shizuku.ShizukuStatus
import com.example.ui.theme.AmberLogic
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldRun
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.RoseAlert
import com.example.ui.theme.SlateElevated
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.SlateWell
import com.example.ui.theme.TextDim
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioHomeScreen(
    macros: List<MacroDefinition>,
    shizukuStatus: ShizukuStatus,
    hasOverlayPermission: Boolean,
    screenCaptureActive: Boolean,
    executionState: RuntimeExecutionState,
    onRequestShizukuPermission: () -> Unit,
    onOpenShizukuApp: () -> Unit,
    onRefreshDiagnostics: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onToggleScreenCapture: () -> Unit,
    onCreateMacro: (name: String, targetApp: String) -> Unit,
    onCreateDemoMacro: () -> Unit,
    onOpenMacro: (String) -> Unit,
    onLaunchOverlay: (MacroDefinition) -> Unit,
    onRunMacro: (MacroDefinition) -> Unit,
    onDuplicateMacro: (String) -> Unit,
    onDeleteMacro: (String) -> Unit,
    onImportJson: () -> Unit,
    onExportAllJson: () -> Unit,
    onExportSingleJson: (MacroDefinition) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showShizukuGuide by remember { mutableStateOf(false) }

    val filteredMacros = remember(macros, searchQuery) {
        if (searchQuery.isBlank()) macros
        else macros.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                it.targetAppLabel.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ObsidianBg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ObsidianBg),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    if (shizukuStatus.state == ShizukuConnectionState.CONNECTED) {
                                        EmeraldRun
                                    } else {
                                        AmberLogic
                                    }
                                )
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "ShizuMacro",
                                style = MaterialTheme.typography.headlineSmall,
                                color = TextPrimary
                            )
                            Text(
                                text = "SHIZUKU INPUT STUDIO",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = onImportJson,
                        modifier = Modifier.testTag("import_json_button")
                    ) {
                        Icon(Icons.Default.FileOpen, contentDescription = "Import JSON", tint = TextPrimary)
                    }
                    if (macros.isNotEmpty()) {
                        IconButton(
                            onClick = onExportAllJson,
                            modifier = Modifier.testTag("export_all_json_button")
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = "Export All JSON", tint = TextPrimary)
                        }
                    }
                    IconButton(
                        onClick = onRefreshDiagnostics,
                        modifier = Modifier.testTag("refresh_diagnostics_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh status", tint = TextSecondary)
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = ElectricCyan,
                contentColor = Color(0xFF04131A),
                modifier = Modifier.testTag("new_macro_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("New Macro", fontWeight = FontWeight.Bold)
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    DiagnosticsPanel(
                        shizukuStatus = shizukuStatus,
                        hasOverlayPermission = hasOverlayPermission,
                        screenCaptureActive = screenCaptureActive,
                        onShizukuClick = {
                            when (shizukuStatus.state) {
                                ShizukuConnectionState.PERMISSION_REQUIRED -> onRequestShizukuPermission()
                                ShizukuConnectionState.CONNECTED -> onRefreshDiagnostics()
                                else -> showShizukuGuide = true
                            }
                        },
                        onOverlayClick = onRequestOverlayPermission,
                        onCaptureClick = onToggleScreenCapture
                    )
                }

                if (macros.isNotEmpty()) {
                    item {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Filter macros or target app...", color = TextDim) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextDim) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("macro_search_input")
                        )
                    }
                }

                if (filteredMacros.isEmpty()) {
                    item {
                        EmptyStudioHeroCard(
                            hasAnyMacros = macros.isNotEmpty(),
                            onCreateMacro = { showCreateDialog = true },
                            onImportJson = onImportJson,
                            onCreateDemo = onCreateDemoMacro
                        )
                    }
                } else {
                    items(filteredMacros, key = { it.id }) { macro ->
                        val isRunningThis = executionState.activeMacroId == macro.id &&
                            (executionState.status == ExecutionStatus.RUNNING || executionState.status == ExecutionStatus.PAUSED)
                        MacroCard(
                            macro = macro,
                            isRunning = isRunningThis,
                            onEdit = { onOpenMacro(macro.id) },
                            onLaunchDock = { onLaunchOverlay(macro) },
                            onRunNow = { onRunMacro(macro) },
                            onDuplicate = { onDuplicateMacro(macro.id) },
                            onExport = { onExportSingleJson(macro) },
                            onDelete = { onDeleteMacro(macro.id) }
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateMacroDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, target ->
                showCreateDialog = false
                onCreateMacro(name, target)
            }
        )
    }

    if (showShizukuGuide) {
        ShizukuSetupGuideDialog(
            shizukuStatus = shizukuStatus,
            onOpenShizuku = onOpenShizukuApp,
            onRequestPermission = onRequestShizukuPermission,
            onDismiss = { showShizukuGuide = false }
        )
    }
}

@Composable
private fun DiagnosticsPanel(
    shizukuStatus: ShizukuStatus,
    hasOverlayPermission: Boolean,
    screenCaptureActive: Boolean,
    onShizukuClick: () -> Unit,
    onOverlayClick: () -> Unit,
    onCaptureClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SlateSurface),
        border = BorderStroke(1.dp, HairlineBorder),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SYSTEM DIAGNOSTICS",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
                Text(
                    text = if (shizukuStatus.state == ShizukuConnectionState.CONNECTED) {
                        "UID ${shizukuStatus.uid} • v${shizukuStatus.shizukuVersion}"
                    } else {
                        "NO ACCESSIBILITY NEEDED"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricCyan
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val shizukuColor = when (shizukuStatus.state) {
                    ShizukuConnectionState.CONNECTED -> EmeraldRun
                    ShizukuConnectionState.PERMISSION_REQUIRED, ShizukuConnectionState.BINDING -> AmberLogic
                    else -> RoseAlert
                }
                val shizukuLabel = when (shizukuStatus.state) {
                    ShizukuConnectionState.CONNECTED -> "Connected"
                    ShizukuConnectionState.PERMISSION_REQUIRED -> "Authorize"
                    ShizukuConnectionState.BINDING -> "Binding..."
                    ShizukuConnectionState.NOT_RUNNING -> "Stopped"
                    ShizukuConnectionState.NOT_INSTALLED -> "Missing"
                }

                DiagnosticGateTile(
                    title = "Shizuku",
                    statusText = shizukuLabel,
                    accent = shizukuColor,
                    icon = Icons.Default.Terminal,
                    onClick = onShizukuClick,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("diag_shizuku_tile")
                )

                DiagnosticGateTile(
                    title = "Overlay",
                    statusText = if (hasOverlayPermission) "Ready" else "Grant",
                    accent = if (hasOverlayPermission) EmeraldRun else AmberLogic,
                    icon = Icons.Default.Layers,
                    onClick = onOverlayClick,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("diag_overlay_tile")
                )

                DiagnosticGateTile(
                    title = "Vision",
                    statusText = if (screenCaptureActive) "Active" else "Start",
                    accent = if (screenCaptureActive) ElectricCyan else TextDim,
                    icon = Icons.Default.ScreenshotMonitor,
                    onClick = onCaptureClick,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("diag_vision_tile")
                )
            }
        }
    }
}

@Composable
private fun DiagnosticGateTile(
    title: String,
    statusText: String,
    accent: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = SlateWell,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
        modifier = modifier.height(74.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(icon, contentDescription = title, tint = accent, modifier = Modifier.size(16.dp))
                Text(title, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, maxLines = 1)
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelMedium,
                color = accent,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun EmptyStudioHeroCard(
    hasAnyMacros: Boolean,
    onCreateMacro: () -> Unit,
    onImportJson: () -> Unit,
    onCreateDemo: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SlateSurface),
        border = BorderStroke(1.dp, HairlineBorder),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_studio_hero),
                    contentDescription = "ShizuMacro visual blueprint banner",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, SlateSurface.copy(alpha = 0.92f))
                            )
                        )
                )
                Text(
                    text = if (hasAnyMacros) "No matching macros" else "Zero-Collision Touch Automation",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                )
            }

            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (hasAnyMacros) {
                        "Adjust your search filter or create a new macro sequence."
                    } else {
                        "Build multi-step tap and swipe chains, pixel color & image template triggers, and loop/branch logic injected directly via Shizuku without Accessibility focus collisions."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onCreateMacro,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = Color(0xFF04131A)),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("empty_create_macro_btn")
                    ) {
                        Text("Create Macro", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onImportJson,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("empty_import_json_btn")
                    ) {
                        Text("Import JSON", color = TextPrimary)
                    }
                }

                if (!hasAnyMacros) {
                    TextButton(
                        onClick = onCreateDemo,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("load_sandbox_demo_btn")
                    ) {
                        Text("Generate Sandbox Demo Flow", color = AmberLogic)
                    }
                }
            }
        }
    }
}

@Composable
private fun MacroCard(
    macro: MacroDefinition,
    isRunning: Boolean,
    onEdit: () -> Unit,
    onLaunchDock: () -> Unit,
    onRunNow: () -> Unit,
    onDuplicate: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    val hasColor = macro.steps.any { it is IfColorStep }
    val hasImage = macro.steps.any { it is IfImageStep }
    val hasLoop = macro.steps.any { it is LoopStartStep }

    Card(
        colors = CardDefaults.cardColors(containerColor = SlateSurface),
        border = BorderStroke(1.dp, if (isRunning) EmeraldRun else HairlineBorder),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .testTag("macro_card_${macro.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = macro.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${macro.targetAppLabel} • ${macro.steps.size} steps • ${if (macro.loopCount <= 0) "∞ loops" else "${macro.loopCount}x"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }

                Row {
                    IconButton(onClick = onDuplicate, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate macro", tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onExport, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Download, contentDescription = "Export macro JSON", tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete macro", tint = RoseAlert, modifier = Modifier.size(18.dp))
                    }
                }
            }

            if (hasColor || hasImage || hasLoop) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (hasLoop) StudioBadgeChip("LOOP / LOGIC", AmberLogic)
                    if (hasColor) StudioBadgeChip("COLOR TRIGGER", EmeraldRun)
                    if (hasImage) StudioBadgeChip("IMAGE MATCH", ElectricCyan)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp), tint = TextPrimary)
                    Spacer(Modifier.width(6.dp))
                    Text("Edit Flow", color = TextPrimary)
                }

                Button(
                    onClick = onLaunchDock,
                    colors = ButtonDefaults.buttonColors(containerColor = SlateElevated, contentColor = ElectricCyan),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.RocketLaunch, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Dock", fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = onRunNow,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldRun, contentColor = Color(0xFF031911)),
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Run macro now")
                }
            }
        }
    }
}

@Composable
fun StudioBadgeChip(text: String, accent: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(accent.copy(alpha = 0.14f))
            .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = accent
        )
    }
}

@Composable
private fun CreateMacroDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, targetApp: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var targetApp by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SlateSurface,
        title = { Text("Create New Macro", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Macro Name") },
                    placeholder = { Text("Daily Quest Runner") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_macro_name_input")
                )
                OutlinedTextField(
                    value = targetApp,
                    onValueChange = { targetApp = it },
                    label = { Text("Target App Label (Optional)") },
                    placeholder = { Text("Any Screen") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, targetApp) },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = Color(0xFF04131A)),
                modifier = Modifier.testTag("create_macro_confirm_btn")
            ) {
                Text("Create", fontWeight = FontWeight.Bold)
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
private fun ShizukuSetupGuideDialog(
    shizukuStatus: ShizukuStatus,
    onOpenShizuku: () -> Unit,
    onRequestPermission: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SlateSurface,
        title = { Text("Shizuku Input Engine Setup", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "ShizuMacro injects hardware MotionEvents via Shizuku's privileged shell/root UserService instead of AccessibilityService, preventing user touch collisions.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Surface(
                    color = SlateWell,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, HairlineBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("1. Install & open the Shizuku app.", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        Text("2. Start Shizuku via Wireless Debugging (Android 11+) or ADB.", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        Text("3. Return here and tap Authorize Shizuku.", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                    }
                }
                if (shizukuStatus.lastError != null) {
                    Text(
                        text = shizukuStatus.lastError,
                        style = MaterialTheme.typography.labelSmall,
                        color = RoseAlert
                    )
                }
            }
        },
        confirmButton = {
            if (shizukuStatus.state == ShizukuConnectionState.PERMISSION_REQUIRED) {
                Button(
                    onClick = {
                        onRequestPermission()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = Color(0xFF04131A))
                ) {
                    Text("Authorize Shizuku")
                }
            } else {
                Button(
                    onClick = {
                        onOpenShizuku()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = Color(0xFF04131A))
                ) {
                    Text("Open Shizuku")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = TextSecondary)
            }
        }
    )
}
