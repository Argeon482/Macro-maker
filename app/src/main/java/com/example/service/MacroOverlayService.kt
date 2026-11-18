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
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MacroOverlayService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var app: ShizuMacroApp
    private lateinit var windowManager: WindowManager

    private var dockView: ComposeView? = null
    private var dockParams: WindowManager.LayoutParams? = null
    private var dockLifecycle: OverlayLifecycleOwner? = null

    private var pickerView: ComposeView? = null
    private var pickerParams: WindowManager.LayoutParams? = null
    private var pickerLifecycle: OverlayLifecycleOwner? = null

    override fun onCreate() {
        super.onCreate()
        app = application as ShizuMacroApp
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForegroundCompat(withMediaProjection = app.screenDetector.isActive.value)

        serviceScope.launch {
            app.overlayCoordinator.overlayState.collect { uiState ->
                if (uiState.pickerMode != PickerMode.NONE && uiState.pendingPickerRequest != null) {
                    showPickerOverlay()
                    updatePickerLayoutMode(uiState.isPickerPassThrough)
                } else {
                    removePickerOverlay()
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
                showPickerOverlay()
            }

            ACTION_SHOW_DOCK -> {
                val macroId = intent.getStringExtra(EXTRA_MACRO_ID)
                val macroName = intent.getStringExtra(EXTRA_MACRO_NAME)
                if (macroId != null && macroName != null) {
                    app.overlayCoordinator.setLoadedMacro(macroId, macroName)
                }
                showDockOverlay()
                app.overlayCoordinator.setOverlayShowing(true, macroId, macroName)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        removePickerOverlay()
        removeDockOverlay()
        serviceScope.cancel()
        app.overlayCoordinator.setOverlayShowing(false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

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

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
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
                val captureActive by app.screenDetector.isActive.collectAsState()

                FloatingControlDock(
                    overlayState = overlayState,
                    executionState = execState,
                    shizukuStatus = shizukuStatus,
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
                            val macroId = app.overlayCoordinator.overlayState.value.activeMacroId
                            if (macroId != null) {
                                serviceScope.launch {
                                    val macro = app.repository.getMacro(macroId)
                                    if (macro != null) {
                                        app.interpreter.start(macro)
                                    }
                                }
                            }
                        }
                    },
                    onStop = { app.interpreter.stop() },
                    onQuickPick = {
                        val macroId = app.overlayCoordinator.overlayState.value.activeMacroId ?: ""
                        app.overlayCoordinator.startPicker(
                            PickerRequest(
                                macroId = macroId,
                                stepId = "",
                                mode = PickerMode.PICK_TAP
                            )
                        )
                    },
                    onOpenApp = { bringMainActivityToFront() },
                    onCloseOverlay = {
                        app.interpreter.stop()
                        app.overlayCoordinator.setOverlayShowing(false)
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
        dockView?.let { v ->
            runCatching { windowManager.removeView(v) }
        }
        dockLifecycle?.onDestroy()
        dockView = null
        dockLifecycle = null
        dockParams = null
    }

    private fun showPickerOverlay() {
        if (pickerView != null) return
        val request = app.overlayCoordinator.overlayState.value.pendingPickerRequest ?: return

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
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
                TargetPickerOverlay(
                    request = request,
                    screenCaptureActive = captureActive,
                    isPassThrough = overlayState.isPickerPassThrough,
                    onTogglePassThrough = { passThrough ->
                        app.overlayCoordinator.setPickerPassThrough(passThrough)
                    },
                    onConfirmTap = { x, y ->
                        app.overlayCoordinator.publishPickerResult(
                            PickerResult.TapPicked(request.macroId, request.stepId, x, y)
                        )
                        removePickerOverlay()
                        if (request.stepId.isNotBlank()) bringMainActivityToFront()
                    },
                    onConfirmSwipe = { sx, sy, ex, ey ->
                        app.overlayCoordinator.publishPickerResult(
                            PickerResult.SwipePicked(request.macroId, request.stepId, sx, sy, ex, ey)
                        )
                        removePickerOverlay()
                        if (request.stepId.isNotBlank()) bringMainActivityToFront()
                    },
                    onConfirmColor = { x, y ->
                        serviceScope.launch {
                            // Hide picker momentarily so we sample the actual screen underneath
                            removePickerOverlay()
                            delay(130L)
                            val frame = app.screenDetector.captureLatestBitmap()
                            val hex = if (frame != null) {
                                val safeX = x.coerceIn(0, frame.width - 1)
                                val safeY = y.coerceIn(0, frame.height - 1)
                                val pixel = frame.getPixel(safeX, safeY)
                                frame.recycle()
                                String.format("#%06X", 0xFFFFFF and pixel)
                            } else {
                                "#10B981"
                            }
                            app.overlayCoordinator.publishPickerResult(
                                PickerResult.ColorPicked(request.macroId, request.stepId, x, y, hex)
                            )
                            if (request.stepId.isNotBlank()) bringMainActivityToFront()
                        }
                    },
                    onConfirmCrop = { left, top, right, bottom ->
                        serviceScope.launch {
                            removePickerOverlay()
                            delay(130L)
                            val frame = app.screenDetector.captureLatestBitmap()
                            val rect = Rect(left, top, right, bottom)
                            if (frame != null) {
                                val safeL = left.coerceIn(0, frame.width - 2)
                                val safeT = top.coerceIn(0, frame.height - 2)
                                val safeW = (right - safeL).coerceIn(8, frame.width - safeL)
                                val safeH = (bottom - safeT).coerceIn(8, frame.height - safeT)
                                val cropped = Bitmap.createBitmap(frame, safeL, safeT, safeW, safeH)
                                frame.recycle()
                                val saved = app.repository.saveTemplateBitmap(
                                    "Crop ${safeW}x${safeH}",
                                    cropped
                                )
                                cropped.recycle()
                                app.overlayCoordinator.publishPickerResult(
                                    PickerResult.TemplatePicked(
                                        macroId = request.macroId,
                                        stepId = request.stepId,
                                        templateId = saved.id,
                                        templateName = saved.name,
                                        region = Rect(safeL, safeT, safeL + safeW, safeT + safeH)
                                    )
                                )
                            } else {
                                app.repository.appendLog(
                                    request.macroId,
                                    "Picker",
                                    "Crop",
                                    "Screen capture was inactive; saved region bounds only.",
                                    LogLevel.WARN
                                )
                                app.overlayCoordinator.publishPickerResult(
                                    PickerResult.TemplatePicked(
                                        macroId = request.macroId,
                                        stepId = request.stepId,
                                        templateId = "",
                                        templateName = "No Capture Active",
                                        region = rect
                                    )
                                )
                            }
                            if (request.stepId.isNotBlank()) bringMainActivityToFront()
                        }
                    },
                    onCancel = {
                        app.overlayCoordinator.cancelPicker()
                        removePickerOverlay()
                        if (request.stepId.isNotBlank()) bringMainActivityToFront()
                    }
                )
            }
        }

        pickerParams = params
        pickerLifecycle = lifecycleOwner
        pickerView = view
        runCatching { windowManager.addView(view, params) }
    }

    private fun updatePickerLayoutMode(passThrough: Boolean) {
        val view = pickerView ?: return
        val params = pickerParams ?: return
        if (passThrough) {
            params.width = WindowManager.LayoutParams.WRAP_CONTENT
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            params.y = 140
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        } else {
            params.width = WindowManager.LayoutParams.MATCH_PARENT
            params.height = WindowManager.LayoutParams.MATCH_PARENT
            params.gravity = Gravity.TOP or Gravity.START
            params.y = 0
            params.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        }
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun removePickerOverlay() {
        pickerView?.let { v ->
            runCatching { windowManager.removeView(v) }
        }
        pickerLifecycle?.onDestroy()
        pickerView = null
        pickerParams = null
        pickerLifecycle = null
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
