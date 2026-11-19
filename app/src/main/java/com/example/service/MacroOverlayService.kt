package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.MainActivity
import com.example.R
import com.example.ShizuMacroApp
import com.example.data.LogLevel
import com.example.engine.ExecutionStatus
import com.example.model.IfColorStep
import com.example.model.IfImageStep
import com.example.model.MacroDefinition
import com.example.model.MacroStep
import com.example.model.SwipeStep
import com.example.model.TapStep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

class MacroOverlayService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var app: ShizuMacroApp
    private lateinit var windowManager: WindowManager

    private var dockView: ComposeView? = null
    private var dockParams: WindowManager.LayoutParams? = null
    private var dockLifecycle: OverlayLifecycleOwner? = null

    private var pinsView: ComposeView? = null
    private var pinsLifecycle: OverlayLifecycleOwner? = null

    private var studioView: ComposeView? = null
    private var studioLifecycle: OverlayLifecycleOwner? = null

    private var pickerView: ComposeView? = null
    private var pickerLifecycle: OverlayLifecycleOwner? = null
    private val pickerSnapshotFlow = MutableStateFlow<Bitmap?>(null)

    private var passThroughView: ComposeView? = null
    private var passThroughParams: WindowManager.LayoutParams? = null
    private var passThroughLifecycle: OverlayLifecycleOwner? = null

    private var touchWatcherView: View? = null

    override fun onCreate() {
        super.onCreate()
        app = application as ShizuMacroApp
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForegroundCompat(withMediaProjection = app.screenDetector.isActive.value)

        serviceScope.launch {
            app.overlayCoordinator.overlayState.collect { uiState ->
                val pickerActive = uiState.pickerMode != PickerMode.NONE && uiState.pendingPickerRequest != null
                if (pickerActive) {
                    if (uiState.isPickerPassThrough) {
                        removePickerOverlay()
                        showPassThroughOverlay()
                    } else {
                        removePassThroughOverlay()
                        showPickerOverlay()
                    }
                } else {
                    removePassThroughOverlay()
                    removePickerOverlay()
                }

                if (!pickerActive && uiState.showPinsOverlay && !uiState.activeMacroId.isNullOrBlank()) {
                    showPinsOverlay()
                } else {
                    removePinsOverlay()
                }

                if (!pickerActive && uiState.showOverlayStudio) {
                    showStudioOverlay()
                } else {
                    removeStudioOverlay()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                app.interpreter.stop()
                app.screenDetector.stop()
                app.overlayCoordinator.cancelPicker()
                app.overlayCoordinator.setOverlayShowing(false)
                removeTouchWatcherOverlay()
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_START_PROJECTION -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
                @Suppress("DEPRECATION")
                val dataIntent = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                if (dataIntent != null) {
                    startForegroundCompat(withMediaProjection = true)
                    app.screenDetector.start(resultCode, dataIntent)
                }
            }

            ACTION_START_PICKER -> {
                showDockOverlay()
                val uiState = app.overlayCoordinator.overlayState.value
                if (uiState.pickerMode != PickerMode.NONE && uiState.pendingPickerRequest != null) {
                    if (uiState.isPickerPassThrough) {
                        showPassThroughOverlay()
                    } else {
                        showPickerOverlay()
                    }
                }
            }

            ACTION_SHOW_DOCK -> {
                val macroId = intent.getStringExtra(EXTRA_MACRO_ID)
                val macroName = intent.getStringExtra(EXTRA_MACRO_NAME)
                if (macroId != null && macroName != null) {
                    app.overlayCoordinator.setLoadedMacro(macroId, macroName)
                }
                showDockOverlay()
                showTouchWatcherOverlay()
                app.shizukuBridge.ensureTouchMonitorStarted()
                app.overlayCoordinator.setOverlayShowing(true, macroId, macroName)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        removeTouchWatcherOverlay()
        removePassThroughOverlay()
        removePickerOverlay()
        removeStudioOverlay()
        removePinsOverlay()
        removeDockOverlay()
        serviceScope.cancel()
        app.overlayCoordinator.setOverlayShowing(false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun overlayLayoutType(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    } else {
        @Suppress("DEPRECATION")
        WindowManager.LayoutParams.TYPE_PHONE
    }

    private fun startForegroundCompat(withMediaProjection: Boolean) {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val fgsType = if (withMediaProjection && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else if (withMediaProjection) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            } else {
                0
            }
            startForeground(NOTIFICATION_ID, notification, fgsType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun showDockOverlay() {
        if (dockView != null) return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayLayoutType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 260
        }

        val lifecycleOwner = OverlayLifecycleOwner().apply { onCreate() }
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setContent {
                val overlayState by app.overlayCoordinator.overlayState.collectAsState()
                val execState by app.interpreter.state.collectAsState()
                val shizukuStatus by app.shizukuBridge.status.collectAsState()
                val hwDiagnostics by app.shizukuBridge.diagnostics.collectAsState()
                val captureActive by app.screenDetector.isActive.collectAsState()

                FloatingControlDock(
                    overlayState = overlayState,
                    executionState = execState,
                    shizukuStatus = shizukuStatus,
                    hardwareDiagnostics = hwDiagnostics,
                    screenCaptureActive = captureActive,
                    onDragDelta = { dx, dy ->
                        dockParams?.let { p ->
                            p.x += dx
                            p.y += dy
                            runCatching { windowManager.updateViewLayout(this, p) }
                        }
                    },
                    onPlayOrPause = {
                        val currentStatus = app.interpreter.state.value.status
                        if (currentStatus == ExecutionStatus.RUNNING || currentStatus == ExecutionStatus.PAUSED) {
                            app.interpreter.pauseOrResume()
                        } else {
                            serviceScope.launch {
                                val macro = resolveActiveMacro()
                                if (macro != null) {
                                    app.overlayCoordinator.setLoadedMacro(macro.id, macro.name)
                                    app.interpreter.start(macro)
                                }
                            }
                        }
                    },
                    onStop = { app.interpreter.stop() },
                    onQuickPick = {
                        serviceScope.launch {
                            val macro = ensureActiveMacro()
                            val tapSteps = macro.steps.filterIsInstance<TapStep>()
                            val targetTap = if (tapSteps.size == 1) tapSteps.first() else null
                            app.overlayCoordinator.startPicker(
                                PickerRequest(
                                    macroId = macro.id,
                                    stepId = targetTap?.id ?: "",
                                    mode = PickerMode.PICK_TAP,
                                    initialX = targetTap?.x ?: 540f,
                                    initialY = targetTap?.y ?: 960f,
                                    returnToAppOnComplete = false
                                )
                            )
                        }
                    },
                    onTogglePins = {
                        serviceScope.launch {
                            val macro = ensureActiveMacro()
                            app.overlayCoordinator.setLoadedMacro(macro.id, macro.name)
                            val currentlyOpen = app.overlayCoordinator.overlayState.value.showPinsOverlay
                            app.overlayCoordinator.setPinsOverlayOpen(!currentlyOpen)
                        }
                    },
                    onOpenOverlayStudio = {
                        serviceScope.launch {
                            val macro = ensureActiveMacro()
                            app.overlayCoordinator.setLoadedMacro(macro.id, macro.name)
                            val currentlyOpen = app.overlayCoordinator.overlayState.value.showOverlayStudio
                            app.overlayCoordinator.setOverlayStudioOpen(!currentlyOpen)
                        }
                    },
                    onOpenApp = { bringMainActivityToFront() },
                    onCloseOverlay = {
                        app.interpreter.stop()
                        app.overlayCoordinator.cancelPicker()
                        app.overlayCoordinator.setOverlayShowing(false)
                        removePassThroughOverlay()
                        removePickerOverlay()
                        removeStudioOverlay()
                        removePinsOverlay()
                        removeDockOverlay()
                        stopSelf()
                    }
                )
            }
        }

        dockParams = params
        dockLifecycle = lifecycleOwner
        dockView = view
        runCatching { windowManager.addView(view, params) }
    }

    private fun removeDockOverlay() {
        dockView?.let { v -> runCatching { windowManager.removeView(v) } }
        dockLifecycle?.onDestroy()
        dockView = null
        dockLifecycle = null
        dockParams = null
    }

    private fun showPinsOverlay() {
        if (pinsView != null) return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayLayoutType(),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        val lifecycleOwner = OverlayLifecycleOwner().apply { onCreate() }
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setContent {
                val overlayState by app.overlayCoordinator.overlayState.collectAsState()
                val macroId = overlayState.activeMacroId ?: ""
                val macroFlow = remember(macroId) { app.repository.observeMacro(macroId) }
                val macro by macroFlow.collectAsState(initial = null)

                macro?.let { currentMacro ->
                    MacroPinsOverlay(
                        macro = currentMacro,
                        onUpdateMacro = { updated ->
                            serviceScope.launch { app.repository.saveMacro(updated) }
                        },
                        onEditStep = { step ->
                            app.overlayCoordinator.setOverlayStudioOpen(true, editingStepId = step.id)
                        },
                        onDone = {
                            app.overlayCoordinator.setPinsOverlayOpen(false)
                        }
                    )
                }
            }
        }

        pinsLifecycle = lifecycleOwner
        pinsView = view
        runCatching { windowManager.addView(view, params) }
    }

    private fun removePinsOverlay() {
        pinsView?.let { v -> runCatching { windowManager.removeView(v) } }
        pinsLifecycle?.onDestroy()
        pinsView = null
        pinsLifecycle = null
    }

    private fun showStudioOverlay() {
        if (studioView != null) return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayLayoutType(),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            @Suppress("DEPRECATION")
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        val lifecycleOwner = OverlayLifecycleOwner().apply { onCreate() }
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setContent {
                val overlayState by app.overlayCoordinator.overlayState.collectAsState()
                val macros by app.repository.macros.collectAsState(initial = emptyList())
                val templates by app.repository.templates.collectAsState(initial = emptyList())
                val captureActive by app.screenDetector.isActive.collectAsState()
                val clipboardItem by app.scriptClipboard.current.collectAsState()

                val activeMacro = macros.find { it.id == overlayState.activeMacroId } ?: macros.firstOrNull()

                OverlayStudioWindow(
                    macros = macros,
                    activeMacro = activeMacro,
                    initialEditingStepId = overlayState.studioEditingStepId,
                    initialActiveFunctionId = overlayState.studioActiveFunctionId,
                    clipboardItem = clipboardItem,
                    templates = templates,
                    screenCaptureActive = captureActive,
                    onSelectMacro = { selected ->
                        app.overlayCoordinator.setLoadedMacro(selected.id, selected.name)
                    },
                    onCreateMacro = { name ->
                        serviceScope.launch {
                            val created = MacroDefinition(
                                id = UUID.randomUUID().toString(),
                                name = name,
                                steps = listOf(TapStep(label = "Initial Tap", x = 540f, y = 960f))
                            )
                            app.repository.saveMacro(created)
                            app.overlayCoordinator.setLoadedMacro(created.id, created.name)
                        }
                    },
                    onUpdateMacro = { updated ->
                        serviceScope.launch {
                            app.repository.saveMacro(updated)
                            app.overlayCoordinator.setLoadedMacro(updated.id, updated.name)
                        }
                    },
                    onCopyStep = { step -> app.scriptClipboard.copyStep(step) },
                    onCopyFunction = { fn -> app.scriptClipboard.copyFunction(fn) },
                    onClearClipboard = { app.scriptClipboard.clear() },
                    onLaunchPicker = { macro, step, mode, functionId ->
                        val request = buildPickerRequest(
                            macroId = macro.id,
                            step = step,
                            mode = mode,
                            functionId = functionId,
                            returnToAppOnComplete = false
                        )
                        app.overlayCoordinator.startPicker(request)
                    },
                    onCropTemplate = { templateId, l, t, r, b, saveAsNew, onCropped ->
                        serviceScope.launch {
                            val cropped = app.repository.cropTemplate(
                                templateId = templateId,
                                trimLeft = l,
                                trimTop = t,
                                trimRight = r,
                                trimBottom = b,
                                saveAsNew = saveAsNew
                            )
                            if (cropped != null) {
                                onCropped(cropped)
                            }
                        }
                    },
                    onOpenPins = {
                        app.overlayCoordinator.setPinsOverlayOpen(true)
                    },
                    onRequestVision = {
                        requestVisionConsent()
                    },
                    onRunMacro = { macro ->
                        app.overlayCoordinator.setLoadedMacro(macro.id, macro.name)
                        app.interpreter.start(macro)
                    },
                    onClose = {
                        app.overlayCoordinator.setOverlayStudioOpen(false)
                    }
                )
            }
        }

        studioLifecycle = lifecycleOwner
        studioView = view
        runCatching { windowManager.addView(view, params) }
    }

    private fun removeStudioOverlay() {
        studioView?.let { v -> runCatching { windowManager.removeView(v) } }
        studioLifecycle?.onDestroy()
        studioView = null
        studioLifecycle = null
    }

    private fun captureCleanPickerSnapshot() {
        if (!app.screenDetector.isActive.value) return
        serviceScope.launch {
            val prevPickerVis = pickerView?.visibility
            val prevDockVis = dockView?.visibility
            pickerView?.visibility = View.INVISIBLE
            dockView?.visibility = View.INVISIBLE
            delay(130L)
            val frame = app.screenDetector.captureLatestBitmap()
            if (prevDockVis != null) dockView?.visibility = prevDockVis
            if (prevPickerVis != null) pickerView?.visibility = prevPickerVis
            if (frame != null) {
                pickerSnapshotFlow.value = frame
            }
        }
    }

    private fun showPickerOverlay() {
        if (pickerView != null) return
        val initialReq = app.overlayCoordinator.overlayState.value.pendingPickerRequest ?: return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayLayoutType(),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        val lifecycleOwner = OverlayLifecycleOwner().apply { onCreate() }
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setContent {
                val captureActive by app.screenDetector.isActive.collectAsState()
                val overlayState by app.overlayCoordinator.overlayState.collectAsState()
                val snapshotBitmap by pickerSnapshotFlow.collectAsState()
                val req = overlayState.pendingPickerRequest

                if (req != null) {
                    val macroFlow = remember(req.macroId) { app.repository.observeMacro(req.macroId) }
                    val macro by macroFlow.collectAsState(initial = null)
                    val targetSubtitle = remember(macro, req.stepId, req.functionId, req.mode) {
                        if (req.stepId.isBlank()) {
                            "Creates new Tap step in ${macro?.name ?: overlayState.activeMacroName}"
                        } else {
                            val fn = req.functionId?.let { fid -> macro?.functions?.find { it.id == fid } }
                                ?: macro?.functions?.find { f -> f.steps.any { it.id == req.stepId } }
                            if (fn != null) {
                                val idx = fn.steps.indexOfFirst { it.id == req.stepId }
                                val step = fn.steps.getOrNull(idx)
                                if (step != null) "fn ${fn.name} • #${idx + 1} ${step.label}" else "fn ${fn.name}"
                            } else {
                                val idx = macro?.steps?.indexOfFirst { it.id == req.stepId } ?: -1
                                val step = macro?.steps?.getOrNull(idx)
                                if (step != null) "Updating #${idx + 1} ${step.label}" else null
                            }
                        }
                    }

                    TargetPickerOverlay(
                        request = req,
                        screenCaptureActive = captureActive,
                        snapshotBitmap = snapshotBitmap,
                        targetSubtitle = targetSubtitle,
                        onRefreshSnapshot = { captureCleanPickerSnapshot() },
                        onRequestVision = {
                            app.overlayCoordinator.setPickerPassThrough(true)
                            requestVisionConsent()
                        },
                        onSwitchApp = { updatedDraft ->
                            app.overlayCoordinator.updatePendingPickerRequest(updatedDraft)
                            app.overlayCoordinator.setPickerPassThrough(true)
                        },
                        onConfirmTap = { x, y ->
                            serviceScope.launch {
                                val result = PickerResult.TapPicked(req.macroId, req.stepId, x, y, req.functionId)
                                val savedStepId = persistPickerResult(result)
                                app.overlayCoordinator.publishPickerResult(result)
                                handlePostPickNavigation(req, savedStepId)
                            }
                        },
                        onConfirmSwipe = { sx, sy, ex, ey ->
                            serviceScope.launch {
                                val result = PickerResult.SwipePicked(req.macroId, req.stepId, sx, sy, ex, ey, req.functionId)
                                val savedStepId = persistPickerResult(result)
                                app.overlayCoordinator.publishPickerResult(result)
                                handlePostPickNavigation(req, savedStepId)
                            }
                        },
                        onConfirmColor = { x, y, sampledHex ->
                            serviceScope.launch {
                                val hex = if (!sampledHex.isNullOrBlank()) {
                                    sampledHex
                                } else {
                                    removePickerOverlay()
                                    delay(140L)
                                    val frame = app.screenDetector.captureLatestBitmap()
                                    if (frame != null) {
                                        val safeX = x.coerceIn(0, frame.width - 1)
                                        val safeY = y.coerceIn(0, frame.height - 1)
                                        val pixel = frame.getPixel(safeX, safeY)
                                        frame.recycle()
                                        String.format("#%06X", 0xFFFFFF and pixel)
                                    } else {
                                        "#10B981"
                                    }
                                }
                                val result = PickerResult.ColorPicked(req.macroId, req.stepId, x, y, hex, req.functionId)
                                val savedStepId = persistPickerResult(result)
                                app.overlayCoordinator.publishPickerResult(result)
                                handlePostPickNavigation(req, savedStepId)
                            }
                        },
                        onConfirmCrop = { left, top, right, bottom ->
                            serviceScope.launch {
                                removePickerOverlay()
                                delay(140L)
                                val frame = app.screenDetector.captureLatestBitmap()
                                val rect = Rect(left, top, right, bottom)
                                val result = if (frame != null) {
                                    val safeL = left.coerceIn(0, (frame.width - 2).coerceAtLeast(0))
                                    val safeT = top.coerceIn(0, (frame.height - 2).coerceAtLeast(0))
                                    val safeW = (right - safeL).coerceIn(8, (frame.width - safeL).coerceAtLeast(8))
                                    val safeH = (bottom - safeT).coerceIn(8, (frame.height - safeT).coerceAtLeast(8))
                                    val cropped = Bitmap.createBitmap(frame, safeL, safeT, safeW, safeH)
                                    frame.recycle()
                                    val saved = app.repository.saveTemplateBitmap(
                                        "Crop ${safeW}x${safeH}",
                                        cropped
                                    )
                                    cropped.recycle()
                                    PickerResult.TemplatePicked(
                                        macroId = req.macroId,
                                        stepId = req.stepId,
                                        templateId = saved.id,
                                        templateName = saved.name,
                                        region = Rect(safeL, safeT, safeL + safeW, safeT + safeH),
                                        functionId = req.functionId
                                    )
                                } else {
                                    app.repository.appendLog(
                                        req.macroId,
                                        "Picker",
                                        "Crop",
                                        "Screen capture was inactive; saved region bounds only.",
                                        LogLevel.WARN
                                    )
                                    PickerResult.TemplatePicked(
                                        macroId = req.macroId,
                                        stepId = req.stepId,
                                        templateId = "",
                                        templateName = "No Capture Active",
                                        region = rect,
                                        functionId = req.functionId
                                    )
                                }
                                val savedStepId = persistPickerResult(result)
                                app.overlayCoordinator.publishPickerResult(result)
                                handlePostPickNavigation(req, savedStepId)
                            }
                        },
                        onConfirmRoi = { left, top, right, bottom ->
                            serviceScope.launch {
                                val result = PickerResult.RoiPicked(
                                    macroId = req.macroId,
                                    stepId = req.stepId,
                                    region = Rect(left, top, right, bottom),
                                    functionId = req.functionId
                                )
                                val savedStepId = persistPickerResult(result)
                                app.overlayCoordinator.publishPickerResult(result)
                                handlePostPickNavigation(req, savedStepId)
                            }
                        },
                        onCancel = {
                            app.overlayCoordinator.cancelPicker()
                            handlePostPickNavigation(req, req.stepId.takeIf { it.isNotBlank() })
                        }
                    )
                }
            }
        }

        pickerLifecycle = lifecycleOwner
        pickerView = view
        runCatching { windowManager.addView(view, params) }

        if (initialReq.mode == PickerMode.PICK_COLOR) {
            captureCleanPickerSnapshot()
        }
    }

    private fun removePickerOverlay() {
        pickerView?.let { v -> runCatching { windowManager.removeView(v) } }
        pickerLifecycle?.onDestroy()
        pickerView = null
        pickerLifecycle = null
        pickerSnapshotFlow.value = null
    }

    private fun showPassThroughOverlay() {
        if (passThroughView != null) return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayLayoutType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 120
            y = 140
        }

        val lifecycleOwner = OverlayLifecycleOwner().apply { onCreate() }
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setContent {
                Box(modifier = Modifier, contentAlignment = Alignment.Center) {
                    PassThroughOverlayChip(
                        onDragDelta = { dx, dy ->
                            passThroughParams?.let { p ->
                                p.x += dx
                                p.y += dy
                                runCatching { windowManager.updateViewLayout(this@apply, p) }
                            }
                        },
                        onResumePicker = {
                            app.overlayCoordinator.setPickerPassThrough(false)
                        },
                        onCancel = {
                            val req = app.overlayCoordinator.overlayState.value.pendingPickerRequest
                            app.overlayCoordinator.cancelPicker()
                            if (req != null) {
                                handlePostPickNavigation(req, req.stepId.takeIf { it.isNotBlank() })
                            }
                        }
                    )
                }
            }
        }

        passThroughParams = params
        passThroughLifecycle = lifecycleOwner
        passThroughView = view
        runCatching { windowManager.addView(view, params) }
    }

    private fun removePassThroughOverlay() {
        passThroughView?.let { v -> runCatching { windowManager.removeView(v) } }
        passThroughLifecycle?.onDestroy()
        passThroughView = null
        passThroughParams = null
        passThroughLifecycle = null
    }

    private fun showTouchWatcherOverlay() {
        if (touchWatcherView != null) return
        val params = WindowManager.LayoutParams(
            1,
            1,
            overlayLayoutType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }
        val watcher = View(this).apply {
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                    app.overlayCoordinator.recordUserTouchActivity()
                }
                false
            }
        }
        touchWatcherView = watcher
        runCatching { windowManager.addView(watcher, params) }
    }

    private fun removeTouchWatcherOverlay() {
        touchWatcherView?.let { v -> runCatching { windowManager.removeView(v) } }
        touchWatcherView = null
    }

    private suspend fun resolveActiveMacro(): MacroDefinition? {
        val activeId = app.overlayCoordinator.overlayState.value.activeMacroId
        if (!activeId.isNullOrBlank()) {
            app.repository.getMacro(activeId)?.let { return it }
        }
        return app.repository.macros.first().firstOrNull()
    }

    private suspend fun ensureActiveMacro(): MacroDefinition {
        resolveActiveMacro()?.let { return it }
        val created = MacroDefinition(
            id = UUID.randomUUID().toString(),
            name = "Overlay Macro",
            steps = emptyList()
        )
        app.repository.saveMacro(created)
        app.overlayCoordinator.setLoadedMacro(created.id, created.name)
        return created
    }

    private suspend fun persistPickerResult(result: PickerResult): String? {
        val macro = if (result.macroId.isNotBlank()) {
            app.repository.getMacro(result.macroId) ?: ensureActiveMacro()
        } else {
            ensureActiveMacro()
        }

        if (result.stepId.isBlank()) {
            val newStep: MacroStep = when (result) {
                is PickerResult.TapPicked -> TapStep(
                    label = "Tap #${macro.steps.size + 1}",
                    x = result.x,
                    y = result.y
                )
                is PickerResult.SwipePicked -> SwipeStep(
                    label = "Swipe #${macro.steps.size + 1}",
                    startX = result.startX,
                    startY = result.startY,
                    endX = result.endX,
                    endY = result.endY
                )
                is PickerResult.ColorPicked -> IfColorStep(
                    label = "Color #${macro.steps.size + 1}",
                    x = result.x,
                    y = result.y,
                    targetHexColor = result.hexColor
                )
                is PickerResult.TemplatePicked -> IfImageStep(
                    label = "Image #${macro.steps.size + 1}",
                    templateId = result.templateId,
                    templateName = result.templateName,
                    searchLeft = (result.region.left - 32).coerceAtLeast(0),
                    searchTop = (result.region.top - 32).coerceAtLeast(0),
                    searchRight = result.region.right + 32,
                    searchBottom = result.region.bottom + 32
                )
                is PickerResult.RoiPicked -> IfImageStep(
                    label = "Image #${macro.steps.size + 1}",
                    searchLeft = result.region.left,
                    searchTop = result.region.top,
                    searchRight = result.region.right,
                    searchBottom = result.region.bottom
                )
            }
            val updatedMacro = macro.copy(steps = macro.steps + newStep)
            app.repository.saveMacro(updatedMacro)
            app.overlayCoordinator.setLoadedMacro(updatedMacro.id, updatedMacro.name)
            return newStep.id
        }

        fun applyResultToStep(step: MacroStep): MacroStep = when (result) {
            is PickerResult.TapPicked -> if (step is TapStep) {
                step.copy(x = result.x, y = result.y)
            } else step

            is PickerResult.SwipePicked -> if (step is SwipeStep) {
                step.copy(
                    startX = result.startX,
                    startY = result.startY,
                    endX = result.endX,
                    endY = result.endY
                )
            } else step

            is PickerResult.ColorPicked -> if (step is IfColorStep) {
                step.copy(x = result.x, y = result.y, targetHexColor = result.hexColor)
            } else step

            is PickerResult.TemplatePicked -> if (step is IfImageStep) {
                val hasExistingRoi = step.searchRight > step.searchLeft && step.searchBottom > step.searchTop
                step.copy(
                    templateId = result.templateId.ifBlank { step.templateId },
                    templateName = result.templateName,
                    searchLeft = if (hasExistingRoi) step.searchLeft else (result.region.left - 32).coerceAtLeast(0),
                    searchTop = if (hasExistingRoi) step.searchTop else (result.region.top - 32).coerceAtLeast(0),
                    searchRight = if (hasExistingRoi) step.searchRight else result.region.right + 32,
                    searchBottom = if (hasExistingRoi) step.searchBottom else result.region.bottom + 32
                )
            } else step

            is PickerResult.RoiPicked -> if (step is IfImageStep) {
                step.copy(
                    searchLeft = result.region.left,
                    searchTop = result.region.top,
                    searchRight = result.region.right,
                    searchBottom = result.region.bottom
                )
            } else step
        }

        var matched = false
        val updatedSteps = macro.steps.map { step ->
            if (step.id != result.stepId) return@map step
            matched = true
            applyResultToStep(step)
        }
        val updatedFunctions = macro.functions.map { fn ->
            if (result.functionId != null && fn.id != result.functionId && fn.steps.none { it.id == result.stepId }) {
                return@map fn
            }
            var fnChanged = false
            val nextFnSteps = fn.steps.map { step ->
                if (step.id != result.stepId) return@map step
                matched = true
                fnChanged = true
                applyResultToStep(step)
            }
            if (fnChanged) fn.copy(steps = nextFnSteps) else fn
        }

        if (matched) {
            val updatedMacro = macro.copy(steps = updatedSteps, functions = updatedFunctions)
            app.repository.saveMacro(updatedMacro)
            app.overlayCoordinator.setLoadedMacro(updatedMacro.id, updatedMacro.name)
        }
        return result.stepId
    }

    private fun handlePostPickNavigation(request: PickerRequest, savedStepId: String?) {
        if (request.returnToAppOnComplete) {
            bringMainActivityToFront()
        } else if (!savedStepId.isNullOrBlank() && request.stepId.isNotBlank()) {
            app.overlayCoordinator.setOverlayStudioOpen(
                open = true,
                editingStepId = savedStepId,
                activeFunctionId = request.functionId
            )
        }
    }

    private fun buildPickerRequest(
        macroId: String,
        step: MacroStep,
        mode: PickerMode,
        functionId: String? = null,
        returnToAppOnComplete: Boolean
    ): PickerRequest = when (step) {
        is TapStep -> PickerRequest(
            macroId = macroId,
            stepId = step.id,
            mode = mode,
            functionId = functionId,
            initialX = step.x,
            initialY = step.y,
            returnToAppOnComplete = returnToAppOnComplete
        )
        is SwipeStep -> PickerRequest(
            macroId = macroId,
            stepId = step.id,
            mode = mode,
            functionId = functionId,
            initialX = step.startX,
            initialY = step.startY,
            initialEndX = step.endX,
            initialEndY = step.endY,
            returnToAppOnComplete = returnToAppOnComplete
        )
        is IfColorStep -> PickerRequest(
            macroId = macroId,
            stepId = step.id,
            mode = mode,
            functionId = functionId,
            initialX = step.x.toFloat(),
            initialY = step.y.toFloat(),
            returnToAppOnComplete = returnToAppOnComplete
        )
        is IfImageStep -> PickerRequest(
            macroId = macroId,
            stepId = step.id,
            mode = mode,
            functionId = functionId,
            initialLeft = step.searchLeft,
            initialTop = step.searchTop,
            initialRight = step.searchRight,
            initialBottom = step.searchBottom,
            returnToAppOnComplete = returnToAppOnComplete
        )
        else -> PickerRequest(
            macroId = macroId,
            stepId = step.id,
            mode = mode,
            functionId = functionId,
            returnToAppOnComplete = returnToAppOnComplete
        )
    }

    private fun requestVisionConsent() {
        app.overlayCoordinator.setOverlayStudioOpen(false)
        app.overlayCoordinator.setPinsOverlayOpen(false)
        val intent = Intent(this, ProjectionRequestActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun bringMainActivityToFront() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        startActivity(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.overlay_service_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.overlay_service_channel_desc)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, MacroOverlayService::class.java).apply { action = ACTION_STOP_SERVICE },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_text))
            .setContentIntent(openIntent)
            .addAction(0, "Stop", stopIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "shizumacro_overlay_channel"
        private const val NOTIFICATION_ID = 7001

        const val ACTION_SHOW_DOCK = "com.example.action.SHOW_DOCK"
        const val ACTION_START_PROJECTION = "com.example.action.START_PROJECTION"
        const val ACTION_START_PICKER = "com.example.action.START_PICKER"
        const val ACTION_STOP_SERVICE = "com.example.action.STOP_SERVICE"

        const val EXTRA_MACRO_ID = "extra_macro_id"
        const val EXTRA_MACRO_NAME = "extra_macro_name"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
    }
}
