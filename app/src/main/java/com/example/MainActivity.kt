package com.example

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.model.MacroDefinition
import com.example.ui.StudioViewModel
import com.example.ui.UiNotice
import com.example.ui.screens.ConsoleLogsScreen
import com.example.ui.screens.MacroEditorScreen
import com.example.ui.screens.StudioHomeScreen
import com.example.ui.screens.TouchSandboxScreen
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ShizuMacroTheme
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.serialization.Serializable

@Serializable
sealed interface AppRoute {
    @Serializable
    data object StudioHome : AppRoute

    @Serializable
    data class MacroEditor(val macroId: String) : AppRoute

    @Serializable
    data object TouchSandbox : AppRoute

    @Serializable
    data object ConsoleLogs : AppRoute
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShizuMacroTheme {
                ShizuMacroRoot()
            }
        }
    }
}

@Composable
private fun ShizuMacroRoot(
    viewModel: StudioViewModel = viewModel()
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val macros by viewModel.macros.collectAsState()
    val templates by viewModel.templates.collectAsState()
    val logs by viewModel.recentLogs.collectAsState()
    val shizukuStatus by viewModel.shizukuStatus.collectAsState()
    val hasOverlay by viewModel.hasOverlayPermission.collectAsState()
    val captureActive by viewModel.screenCaptureActive.collectAsState()
    val executionState by viewModel.executionState.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var pendingExportMacroIds by remember { mutableStateOf<Set<String>?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshDiagnostics()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshDiagnostics()
        viewModel.notices.collect { notice ->
            if (notice is UiNotice.Message) {
                snackbarHostState.showSnackbar(notice.text)
            }
        }
    }

    val notificationPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    val overlaySettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshDiagnostics()
    }

    val projectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            viewModel.startMediaProjectionService(result.resultCode, result.data!!)
        }
    }

    val importJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importMacrosFromUri(uri)
        }
    }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.exportMacrosToUri(uri, pendingExportMacroIds)
        }
        pendingExportMacroIds = null
    }

    val requestNotificationPermissionIfNeeded: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val openOverlayPermissionSettings: () -> Unit = {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        overlaySettingsLauncher.launch(intent)
    }

    val launchProjectionConsent: () -> Unit = {
        requestNotificationPermissionIfNeeded()
        val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(mgr.createScreenCaptureIntent())
    }

    val triggerExport: (MacroDefinition?) -> Unit = { single ->
        pendingExportMacroIds = single?.let { setOf(it.id) }
        val filename = single?.name?.lowercase()?.replace(Regex("[^a-z0-9]+"), "_")?.trim('_')
            ?.takeIf { it.isNotBlank() } ?: "shizumacro_bundle"
        exportJsonLauncher.launch("$filename.json")
    }

    val showTopLevelNav = backStackEntry?.destination?.hasRoute(AppRoute.MacroEditor::class) != true

    val navItems = listOf(
        Triple(AppRoute.StudioHome, "Studio", Pair(Icons.Filled.Build, Icons.Outlined.Build)),
        Triple(AppRoute.TouchSandbox, "Sandbox", Pair(Icons.Filled.TouchApp, Icons.Outlined.TouchApp)),
        Triple(AppRoute.ConsoleLogs, "Console", Pair(Icons.Filled.Terminal, Icons.Outlined.Terminal))
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 680.dp

        Scaffold(
            containerColor = ObsidianBg,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (showTopLevelNav && !useRail) {
                    NavigationBar(
                        containerColor = SlateSurface,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        navItems.forEach { (route, label, icons) ->
                            val selected = backStackEntry?.destination?.hasRoute(route::class) == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) icons.first else icons.second,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = ElectricCyan,
                                    selectedTextColor = ElectricCyan,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary,
                                    indicatorColor = ElectricCyan.copy(alpha = 0.14f)
                                ),
                                modifier = Modifier.testTag("nav_${label.lowercase()}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (showTopLevelNav && useRail) {
                    NavigationRail(containerColor = SlateSurface) {
                        navItems.forEach { (route, label, icons) ->
                            val selected = backStackEntry?.destination?.hasRoute(route::class) == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) icons.first else icons.second,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = ElectricCyan,
                                    selectedTextColor = ElectricCyan,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary,
                                    indicatorColor = ElectricCyan.copy(alpha = 0.14f)
                                )
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    NavHost(
                        navController = navController,
                        startDestination = AppRoute.StudioHome
                    ) {
                        composable<AppRoute.StudioHome> {
                            StudioHomeScreen(
                                macros = macros,
                                shizukuStatus = shizukuStatus,
                                hasOverlayPermission = hasOverlay,
                                screenCaptureActive = captureActive,
                                executionState = executionState,
                                onRequestShizukuPermission = { viewModel.shizukuBridge.requestPermission() },
                                onOpenShizukuApp = { viewModel.shizukuBridge.launchShizukuApp() },
                                onRefreshDiagnostics = { viewModel.refreshDiagnostics() },
                                onRequestOverlayPermission = openOverlayPermissionSettings,
                                onToggleScreenCapture = {
                                    if (captureActive) viewModel.stopMediaProjection() else launchProjectionConsent()
                                },
                                onCreateMacro = { name, target ->
                                    viewModel.createNewMacro(name, target) { id ->
                                        navController.navigate(AppRoute.MacroEditor(id))
                                    }
                                },
                                onCreateDemoMacro = {
                                    viewModel.createSandboxDemoMacro { id ->
                                        navController.navigate(AppRoute.MacroEditor(id))
                                    }
                                },
                                onOpenMacro = { id ->
                                    navController.navigate(AppRoute.MacroEditor(id))
                                },
                                onLaunchOverlay = { macro ->
                                    requestNotificationPermissionIfNeeded()
                                    if (!viewModel.canDrawOverlays()) {
                                        openOverlayPermissionSettings()
                                    } else {
                                        viewModel.launchFloatingDock(macro)
                                    }
                                },
                                onRunMacro = { macro ->
                                    viewModel.runMacroDirectly(macro)
                                },
                                onDuplicateMacro = { id -> viewModel.duplicateMacro(id) },
                                onDeleteMacro = { id -> viewModel.deleteMacro(id) },
                                onImportJson = {
                                    importJsonLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                                },
                                onExportAllJson = { triggerExport(null) },
                                onExportSingleJson = { macro -> triggerExport(macro) }
                            )
                        }

                        composable<AppRoute.MacroEditor> { entry ->
                            val args = entry.toRoute<AppRoute.MacroEditor>()
                            val macro by viewModel.observeMacro(args.macroId).collectAsState(initial = null)
                            MacroEditorScreen(
                                macro = macro,
                                templates = templates,
                                executionState = executionState,
                                onBack = { navController.popBackStack() },
                                onSaveMacro = { updated -> viewModel.saveMacro(updated) },
                                onLaunchDock = { m ->
                                    requestNotificationPermissionIfNeeded()
                                    if (!viewModel.canDrawOverlays()) {
                                        openOverlayPermissionSettings()
                                    } else {
                                        viewModel.launchFloatingDock(m)
                                    }
                                },
                                onRunNow = { m -> viewModel.runMacroDirectly(m) },
                                onPickOnScreen = { m, step ->
                                    requestNotificationPermissionIfNeeded()
                                    if (!viewModel.canDrawOverlays()) {
                                        openOverlayPermissionSettings()
                                    } else {
                                        viewModel.startScreenPicker(m, step)
                                    }
                                },
                                onExportJson = { m -> triggerExport(m) }
                            )
                        }

                        composable<AppRoute.TouchSandbox> {
                            TouchSandboxScreen(
                                shizukuStatus = shizukuStatus,
                                onTestTap = { x, y -> viewModel.testShizukuTapInSandbox(x, y) },
                                onTestSwipe = { sx, sy, ex, ey -> viewModel.testShizukuSwipeInSandbox(sx, sy, ex, ey) }
                            )
                        }

                        composable<AppRoute.ConsoleLogs> {
                            ConsoleLogsScreen(
                                executionState = executionState,
                                logs = logs,
                                onPauseOrResume = { viewModel.interpreter.pauseOrResume() },
                                onStop = { viewModel.interpreter.stop() },
                                onClearLogs = { viewModel.clearLogs() }
                            )
                        }
                    }
                }
            }
        }
    }
}
