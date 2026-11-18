package com.example.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.WindowManager
import com.example.BuildConfig
import com.example.model.InputInjectionMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import rikka.shizuku.Shizuku
import kotlin.math.roundToInt
import kotlin.random.Random

enum class ShizukuConnectionState {
    NOT_INSTALLED,
    NOT_RUNNING,
    PERMISSION_REQUIRED,
    BINDING,
    CONNECTED
}

data class ShizukuStatus(
    val state: ShizukuConnectionState = ShizukuConnectionState.NOT_RUNNING,
    val shizukuVersion: Int = -1,
    val uid: Int = -1,
    val userServiceReady: Boolean = false,
    val lastError: String? = null
)

data class LiveTouchSnapshot(
    val isHolding: Boolean,
    val msSinceLastActivity: Long,
    val source: String
)

data class HardwareInputDiagnostics(
    val physicalTouchDeviceId: Int = 0,
    val physicalTouchDeviceName: String = "Unknown",
    val devInputReadable: Boolean = false,
    val devInputWritable: Boolean = false,
    val uinputWritable: Boolean = false,
    val uhidWritable: Boolean = false,
    val rootAvailable: Boolean = false,
    val touchMonitorRunning: Boolean = false,
    val cooperativeFingerDown: Boolean = false,
    val cooperativeFingerX: Float = 0f,
    val cooperativeFingerY: Float = 0f,
    val cooperativeFingerDownTime: Long = 0L,
    val cooperativeFingerPointerId: Int = 0,
    val lastBypassReport: String = "Tap Probe to inspect InputDispatcher & /dev/input"
)

class ShizukuInputBridge(private val context: Context) {

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _status = MutableStateFlow(ShizukuStatus())
    val status: StateFlow<ShizukuStatus> = _status.asStateFlow()

    private val _diagnostics = MutableStateFlow(HardwareInputDiagnostics())
    val diagnostics: StateFlow<HardwareInputDiagnostics> = _diagnostics.asStateFlow()

    @Volatile
    private var injectorService: IInputInjectorService? = null

    @Volatile
    private var isBound = false

    @Volatile
    private var activeDownTime: Long? = null

    @Volatile
    private var activeLastX: Float = 0f

    @Volatile
    private var activeLastY: Float = 0f

    @Volatile
    private var activeInjectionMode: InputInjectionMode = InputInjectionMode.MOUSE

    @Volatile
    private var activeDeviceId: Int = 0

    private val userServiceArgs by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(context.packageName, InputInjectorUserService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix("shizuku_injector")
            .debuggable(BuildConfig.DEBUG)
            .version(3)
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val stub = IInputInjectorService.Stub.asInterface(service)
            injectorService = stub
            isBound = true
            ioScope.launch {
                val ready = runCatching { stub?.ping() == true }.getOrDefault(false)
                _status.update {
                    it.copy(
                        state = if (ready) ShizukuConnectionState.CONNECTED else ShizukuConnectionState.BINDING,
                        userServiceReady = ready,
                        lastError = if (ready) null else "InputManager reflection unavailable in UserService"
                    )
                }
                if (ready) {
                    refreshCapabilities()
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            injectorService = null
            isBound = false
            refreshStatus()
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        refreshStatus()
        if (hasPermission()) {
            bindService()
        }
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        injectorService = null
        isBound = false
        refreshStatus()
    }

    private val permissionListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    refreshStatus()
                    bindService()
                } else {
                    _status.update {
                        it.copy(
                            state = ShizukuConnectionState.PERMISSION_REQUIRED,
                            lastError = "Shizuku permission denied"
                        )
                    }
                }
            }
        }

    fun registerListeners() {
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionListener)
        } catch (_: Throwable) {
        }
        refreshStatus()
    }

    fun unregisterListeners() {
        try {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
            Shizuku.removeRequestPermissionResultListener(permissionListener)
        } catch (_: Throwable) {
        }
        unbindService()
    }

    fun isShizukuAppInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun launchShizukuApp(): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }

    fun hasPermission(): Boolean {
        return try {
            Shizuku.pingBinder() &&
                !Shizuku.isPreV11() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    fun requestPermission() {
        try {
            if (!Shizuku.pingBinder()) {
                refreshStatus()
                return
            }
            if (Shizuku.isPreV11()) {
                _status.update {
                    it.copy(lastError = "Shizuku v11+ is required")
                }
                return
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                bindService()
            } else {
                Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
            }
        } catch (t: Throwable) {
            _status.update { it.copy(lastError = t.message) }
        }
    }

    fun refreshStatus() {
        val installed = isShizukuAppInstalled()
        val binderAlive = try {
            Shizuku.pingBinder()
        } catch (_: Throwable) {
            false
        }

        if (!binderAlive) {
            _status.update {
                it.copy(
                    state = if (installed) ShizukuConnectionState.NOT_RUNNING else ShizukuConnectionState.NOT_INSTALLED,
                    userServiceReady = false,
                    shizukuVersion = -1,
                    uid = -1
                )
            }
            return
        }

        val granted = hasPermission()
        val version = try { Shizuku.getVersion() } catch (_: Throwable) { -1 }
        val uid = try { Shizuku.getUid() } catch (_: Throwable) { -1 }
        val ready = isBound && injectorService != null

        _status.update {
            it.copy(
                state = when {
                    !granted -> ShizukuConnectionState.PERMISSION_REQUIRED
                    ready -> ShizukuConnectionState.CONNECTED
                    else -> ShizukuConnectionState.BINDING
                },
                shizukuVersion = version,
                uid = uid,
                userServiceReady = ready,
                lastError = null
            )
        }
    }

    fun refreshCapabilities() {
        val service = injectorService ?: return
        ioScope.launch {
            runCatching {
                val raw = service.probeCapabilities()
                val json = JSONObject(raw)
                val devReadable = json.optBoolean("devInputReadable", false)
                val devWritable = json.optBoolean("devInputWritable", false)
                val uinputWritable = json.optBoolean("uinputWritable", false)
                val uhidWritable = json.optBoolean("uhidWritable", false)
                val monitorStarted = if (devReadable) {
                    runCatching { service.startTouchMonitor() }.getOrDefault(false)
                } else {
                    false
                }
                _diagnostics.update {
                    it.copy(
                        physicalTouchDeviceId = json.optInt("touchDeviceId", 0),
                        physicalTouchDeviceName = json.optString("touchDeviceName", "Unknown"),
                        devInputReadable = devReadable,
                        devInputWritable = devWritable,
                        uinputWritable = uinputWritable,
                        uhidWritable = uhidWritable,
                        rootAvailable = json.optBoolean("rootAvailable", false),
                        touchMonitorRunning = monitorStarted || json.optBoolean("touchMonitorRunning", false),
                        lastBypassReport = json.optString("note", raw)
                    )
                }
            }
        }
    }

    fun ensureTouchMonitorStarted() {
        val service = injectorService ?: return
        val diag = _diagnostics.value
        if (diag.touchMonitorRunning) return
        if (!diag.devInputReadable && diag.physicalTouchDeviceId == 0) {
            refreshCapabilities()
            return
        }
        if (diag.devInputReadable) {
            ioScope.launch {
                val started = runCatching { service.startTouchMonitor() }.getOrDefault(false)
                if (started) {
                    _diagnostics.update { it.copy(touchMonitorRunning = true) }
                }
            }
        }
    }

    fun readLiveTouchSnapshot(nowMs: Long = System.currentTimeMillis()): LiveTouchSnapshot {
        val diag = _diagnostics.value
        if (diag.cooperativeFingerDown) {
            return LiveTouchSnapshot(
                isHolding = true,
                msSinceLastActivity = 0L,
                source = "sandbox"
            )
        }
        val service = injectorService
        if (service != null && diag.devInputReadable && diag.touchMonitorRunning) {
            val parts = runCatching { service.readLatestFingerState().split(',') }.getOrNull()
            if (parts != null && parts.size >= 4) {
                val isDown = parts[0] == "1"
                val updatedMs = parts[3].toLongOrNull() ?: 0L
                return if (isDown) {
                    LiveTouchSnapshot(isHolding = true, msSinceLastActivity = 0L, source = "getevent")
                } else {
                    val elapsed = if (updatedMs > 0L) (nowMs - updatedMs).coerceAtLeast(0L) else Long.MAX_VALUE
                    LiveTouchSnapshot(isHolding = false, msSinceLastActivity = elapsed, source = "getevent")
                }
            }
        }
        return LiveTouchSnapshot(isHolding = false, msSinceLastActivity = Long.MAX_VALUE, source = "none")
    }

    fun updateCooperativeFingerState(
        isDown: Boolean,
        screenX: Float,
        screenY: Float,
        downTime: Long,
        deviceId: Int,
        pointerId: Int = 0
    ) {
        _diagnostics.update { cur ->
            cur.copy(
                physicalTouchDeviceId = if (deviceId > 0) deviceId else cur.physicalTouchDeviceId,
                cooperativeFingerDown = isDown,
                cooperativeFingerX = if (isDown) screenX else cur.cooperativeFingerX,
                cooperativeFingerY = if (isDown) screenY else cur.cooperativeFingerY,
                cooperativeFingerDownTime = if (isDown) downTime else 0L,
                cooperativeFingerPointerId = if (isDown) pointerId else 0
            )
        }
    }

    fun bindService() {
        if (!hasPermission() || isBound) return
        _status.update { it.copy(state = ShizukuConnectionState.BINDING, lastError = null) }
        try {
            Shizuku.bindUserService(userServiceArgs, serviceConnection)
        } catch (t: Throwable) {
            _status.update {
                it.copy(lastError = "Failed to bind UserService: ${t.message}")
            }
        }
    }

    fun unbindService() {
        if (!isBound) return
        try {
            runCatching { injectorService?.stopTouchMonitor() }
            Shizuku.unbindUserService(userServiceArgs, serviceConnection, true)
        } catch (_: Throwable) {
        } finally {
            injectorService = null
            isBound = false
            refreshStatus()
        }
    }

    suspend fun tap(
        x: Float,
        y: Float,
        holdMs: Long = 35L,
        jitterPx: Int = 0,
        mode: InputInjectionMode = InputInjectionMode.MOUSE
    ): Boolean = withContext(Dispatchers.IO) {
        val service = injectorService ?: return@withContext false
        val offsetX = if (jitterPx > 0) Random.nextInt(-jitterPx, jitterPx + 1).toFloat() else 0f
        val offsetY = if (jitterPx > 0) Random.nextInt(-jitterPx, jitterPx + 1).toFloat() else 0f
        val tx = (x + offsetX).coerceAtLeast(0f)
        val ty = (y + offsetY).coerceAtLeast(0f)

        when (mode) {
            InputInjectionMode.MOUSE_HOVER_CLICK -> injectMouseHoverClick(service, tx, ty, holdMs)
            InputInjectionMode.CO_TOUCH_MULTIPLEX -> injectCoTouchTap(service, tx, ty, holdMs)
            else -> injectSinglePointerTap(service, tx, ty, holdMs, mode)
        }
    }

    suspend fun swipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 300L,
        steps: Int = 18,
        mode: InputInjectionMode = InputInjectionMode.MOUSE
    ): Boolean = withContext(Dispatchers.IO) {
        val service = injectorService ?: return@withContext false
        val safeSteps = steps.coerceIn(4, 120)
        val stepDelay = (durationMs / safeSteps).coerceAtLeast(4L)

        if (mode == InputInjectionMode.CO_TOUCH_MULTIPLEX) {
            if (_diagnostics.value.devInputWritable) {
                val (psx, psy, pw, ph) = toNaturalPanelCoords(startX, startY)
                val (pex, pey, _, _) = toNaturalPanelCoords(endX, endY)
                val ok = runCatching {
                    service.injectEvdevSwipe(psx, psy, pex, pey, pw, ph, durationMs, safeSteps)
                }.getOrDefault(false)
                val detail = runCatching { service.lastInjectDiagnostic }.getOrDefault("")
                _diagnostics.update { it.copy(lastBypassReport = detail) }
                if (ok) return@withContext true
            }
            val finger = resolveActiveFingerSnapshot(service)
            if (finger != null) {
                return@withContext injectCoTouchSwipe(
                    service = service,
                    initialFinger = finger,
                    startX = startX,
                    startY = startY,
                    endX = endX,
                    endY = endY,
                    steps = safeSteps,
                    stepDelay = stepDelay
                )
            }
        }

        val effectiveMode = if (mode == InputInjectionMode.MOUSE_HOVER_CLICK || mode == InputInjectionMode.CO_TOUCH_MULTIPLEX) {
            InputInjectionMode.TOUCHSCREEN
        } else {
            mode
        }
        val downTime = SystemClock.uptimeMillis()
        activeDownTime = downTime
        activeLastX = startX
        activeLastY = startY
        activeInjectionMode = effectiveMode
        activeDeviceId = 0

        val down = obtainPointerEvent(downTime, downTime, MotionEvent.ACTION_DOWN, startX, startY, effectiveMode)
        val downOk = injectSafely(service, down, MODE_WAIT_FOR_FINISH)
        down.recycle()
        if (!downOk) {
            activeDownTime = null
            return@withContext false
        }

        for (i in 1..safeSteps) {
            delay(stepDelay)
            val t = i.toFloat() / safeSteps.toFloat()
            val curX = startX + (endX - startX) * t
            val curY = startY + (endY - startY) * t
            activeLastX = curX
            activeLastY = curY
            val move = obtainPointerEvent(
                downTime,
                SystemClock.uptimeMillis(),
                MotionEvent.ACTION_MOVE,
                curX,
                curY,
                effectiveMode
            )
            try {
                service.injectInputEvent(move, MODE_ASYNC)
            } catch (_: Throwable) {
            } finally {
                move.recycle()
            }
        }

        val up = obtainPointerEvent(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, endX, endY, effectiveMode)
        val upOk = injectSafely(service, up, MODE_WAIT_FOR_FINISH)
        up.recycle()
        activeDownTime = null
        upOk
    }

    suspend fun pressKey(keyCode: Int): Boolean = withContext(Dispatchers.IO) {
        val service = injectorService ?: return@withContext false
        val downTime = SystemClock.uptimeMillis()
        val down = KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, keyCode, 0).apply {
            source = InputDevice.SOURCE_KEYBOARD
        }
        val up = KeyEvent(downTime, downTime + 20L, KeyEvent.ACTION_UP, keyCode, 0).apply {
            source = InputDevice.SOURCE_KEYBOARD
        }
        try {
            val d = service.injectInputEvent(down, MODE_WAIT_FOR_FINISH)
            val u = service.injectInputEvent(up, MODE_WAIT_FOR_FINISH)
            d && u
        } catch (_: Throwable) {
            false
        }
    }

    fun releaseActiveTouch() {
        val downTime = activeDownTime ?: return
        val lastX = activeLastX
        val lastY = activeLastY
        val mode = activeInjectionMode
        val devId = activeDeviceId
        activeDownTime = null
        val service = injectorService ?: return
        ioScope.launch {
            val cancel = obtainPointerEvent(
                downTime,
                SystemClock.uptimeMillis(),
                MotionEvent.ACTION_CANCEL,
                lastX,
                lastY,
                mode,
                deviceId = devId
            )
            try {
                service.injectInputEvent(cancel, MODE_ASYNC)
            } catch (_: Throwable) {
            } finally {
                cancel.recycle()
            }
        }
    }

    private suspend fun injectSinglePointerTap(
        service: IInputInjectorService,
        tx: Float,
        ty: Float,
        holdMs: Long,
        mode: InputInjectionMode,
        deviceId: Int = 0
    ): Boolean {
        val downTime = SystemClock.uptimeMillis()
        activeDownTime = downTime
        activeLastX = tx
        activeLastY = ty
        activeInjectionMode = mode
        activeDeviceId = deviceId

        val down = obtainPointerEvent(downTime, downTime, MotionEvent.ACTION_DOWN, tx, ty, mode, deviceId = deviceId)
        val downOk = injectSafely(service, down, MODE_WAIT_FOR_FINISH)
        down.recycle()

        if (!downOk) {
            activeDownTime = null
            return false
        }

        if (holdMs > 0) {
            delay(holdMs)
        }

        val up = obtainPointerEvent(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, tx, ty, mode, deviceId = deviceId)
        val upOk = injectSafely(service, up, MODE_WAIT_FOR_FINISH)
        up.recycle()
        activeDownTime = null
        return upOk
    }

    private suspend fun injectMouseHoverClick(
        service: IInputInjectorService,
        tx: Float,
        ty: Float,
        holdMs: Long
    ): Boolean {
        val baseTime = SystemClock.uptimeMillis()
        val hoverEnter = obtainPointerEvent(
            downTime = baseTime,
            eventTime = baseTime,
            action = MotionEvent.ACTION_HOVER_MOVE,
            x = tx,
            y = ty,
            mode = InputInjectionMode.MOUSE_HOVER_CLICK,
            overrideButtonState = 0
        )
        val hoverOk = injectSafely(service, hoverEnter, MODE_WAIT_FOR_FINISH)
        hoverEnter.recycle()
        if (!hoverOk) {
            val detail = runCatching { service.lastInjectDiagnostic }.getOrDefault("rejected")
            _diagnostics.update { it.copy(lastBypassReport = "Hover Move failed: $detail") }
            return false
        }

        val press = obtainPointerEvent(
            downTime = baseTime,
            eventTime = SystemClock.uptimeMillis(),
            action = MotionEvent.ACTION_BUTTON_PRESS,
            x = tx,
            y = ty,
            mode = InputInjectionMode.MOUSE_HOVER_CLICK,
            overrideButtonState = MotionEvent.BUTTON_PRIMARY,
            actionButton = MotionEvent.BUTTON_PRIMARY
        )
        val pressOk = injectSafely(service, press, MODE_WAIT_FOR_FINISH)
        val pressDetail = runCatching { service.lastInjectDiagnostic }.getOrDefault("")
        press.recycle()

        if (holdMs > 0) delay(holdMs)

        val release = obtainPointerEvent(
            downTime = baseTime,
            eventTime = SystemClock.uptimeMillis(),
            action = MotionEvent.ACTION_BUTTON_RELEASE,
            x = tx,
            y = ty,
            mode = InputInjectionMode.MOUSE_HOVER_CLICK,
            overrideButtonState = 0,
            actionButton = MotionEvent.BUTTON_PRIMARY
        )
        val releaseOk = injectSafely(service, release, MODE_WAIT_FOR_FINISH)
        release.recycle()

        _diagnostics.update {
            it.copy(
                lastBypassReport = if (pressOk && releaseOk) {
                    "Hover Button Click dispatched at (${tx.toInt()}, ${ty.toInt()})"
                } else {
                    "Hover Button Click failed (press=$pressOk, rel=$releaseOk) • $pressDetail"
                }
            )
        }
        return pressOk && releaseOk
    }

    private suspend fun injectCoTouchTap(
        service: IInputInjectorService,
        tx: Float,
        ty: Float,
        holdMs: Long
    ): Boolean {
        if (_diagnostics.value.devInputWritable) {
            val (px, py, pw, ph) = toNaturalPanelCoords(tx, ty)
            val evdevOk = runCatching {
                service.injectEvdevTap(px, py, pw, ph, holdMs)
            }.getOrDefault(false)
            val detail = runCatching { service.lastInjectDiagnostic }.getOrDefault("")
            _diagnostics.update { it.copy(lastBypassReport = detail) }
            if (evdevOk) return true
        }

        val finger = resolveActiveFingerSnapshot(service)
        if (finger == null) {
            _diagnostics.update {
                it.copy(lastBypassReport = "Co-Touch idle (no active Finger Slot 0) -> single pointer tap")
            }
            return injectSinglePointerTap(
                service = service,
                tx = tx,
                ty = ty,
                holdMs = holdMs,
                mode = InputInjectionMode.TOUCHSCREEN,
                deviceId = 0
            )
        }

        val pDownAction = MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        val downEventTime = maxOf(SystemClock.uptimeMillis(), finger.downTime + 1L)
        val pDown = obtainDualTouchEvent(
            downTime = finger.downTime,
            eventTime = downEventTime,
            action = pDownAction,
            deviceId = finger.deviceId,
            p0Id = finger.pointerId,
            p0x = finger.x,
            p0y = finger.y,
            p1Id = finger.injectedPointerId,
            p1x = tx,
            p1y = ty
        )
        val downOk = injectSafely(service, pDown, MODE_WAIT_FOR_FINISH)
        pDown.recycle()

        if (!downOk) {
            val detail = runCatching { service.lastInjectDiagnostic }.getOrDefault("unknown")
            _diagnostics.update {
                it.copy(lastBypassReport = "Co-Touch P${finger.injectedPointerId} DOWN blocked: $detail")
            }
            return false
        }

        if (holdMs > 0) {
            delay(holdMs)
            val refreshed = resolveActiveFingerSnapshot(service) ?: finger
            val move = obtainDualTouchEvent(
                downTime = finger.downTime,
                eventTime = maxOf(SystemClock.uptimeMillis(), finger.downTime + 2L),
                action = MotionEvent.ACTION_MOVE,
                deviceId = finger.deviceId,
                p0Id = finger.pointerId,
                p0x = refreshed.x,
                p0y = refreshed.y,
                p1Id = finger.injectedPointerId,
                p1x = tx,
                p1y = ty
            )
            runCatching { service.injectInputEvent(move, MODE_ASYNC) }
            move.recycle()
        }

        val finalFinger = resolveActiveFingerSnapshot(service) ?: finger
        val pUpAction = MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        val pUp = obtainDualTouchEvent(
            downTime = finger.downTime,
            eventTime = maxOf(SystemClock.uptimeMillis(), finger.downTime + 3L),
            action = pUpAction,
            deviceId = finger.deviceId,
            p0Id = finger.pointerId,
            p0x = finalFinger.x,
            p0y = finalFinger.y,
            p1Id = finger.injectedPointerId,
            p1x = tx,
            p1y = ty
        )
        val upOk = injectSafely(service, pUp, MODE_WAIT_FOR_FINISH)
        val detail = if (!upOk) runCatching { service.lastInjectDiagnostic }.getOrDefault("") else ""
        pUp.recycle()

        _diagnostics.update {
            it.copy(
                lastBypassReport = if (upOk) {
                    "Co-Touch P${finger.injectedPointerId} (${tx.toInt()},${ty.toInt()}) + Slot${finger.pointerId} dev=${finger.deviceId} -> OK"
                } else {
                    "Co-Touch P${finger.injectedPointerId} UP failed: $detail"
                }
            )
        }
        return upOk
    }

    private suspend fun injectCoTouchSwipe(
        service: IInputInjectorService,
        initialFinger: FingerSnapshot,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        steps: Int,
        stepDelay: Long
    ): Boolean {
        val pDownAction = MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        val pDown = obtainDualTouchEvent(
            downTime = initialFinger.downTime,
            eventTime = maxOf(SystemClock.uptimeMillis(), initialFinger.downTime + 1L),
            action = pDownAction,
            deviceId = initialFinger.deviceId,
            p0Id = initialFinger.pointerId,
            p0x = initialFinger.x,
            p0y = initialFinger.y,
            p1Id = initialFinger.injectedPointerId,
            p1x = startX,
            p1y = startY
        )
        val downOk = injectSafely(service, pDown, MODE_WAIT_FOR_FINISH)
        pDown.recycle()
        if (!downOk) {
            val detail = runCatching { service.lastInjectDiagnostic }.getOrDefault("rejected")
            _diagnostics.update {
                it.copy(lastBypassReport = "Co-Touch Swipe DOWN blocked: $detail")
            }
            return false
        }

        for (i in 1..steps) {
            delay(stepDelay)
            val t = i.toFloat() / steps.toFloat()
            val curX = startX + (endX - startX) * t
            val curY = startY + (endY - startY) * t
            val liveFinger = resolveActiveFingerSnapshot(service) ?: initialFinger
            val move = obtainDualTouchEvent(
                downTime = initialFinger.downTime,
                eventTime = maxOf(SystemClock.uptimeMillis(), initialFinger.downTime + 2L),
                action = MotionEvent.ACTION_MOVE,
                deviceId = initialFinger.deviceId,
                p0Id = initialFinger.pointerId,
                p0x = liveFinger.x,
                p0y = liveFinger.y,
                p1Id = initialFinger.injectedPointerId,
                p1x = curX,
                p1y = curY
            )
            runCatching { service.injectInputEvent(move, MODE_ASYNC) }
            move.recycle()
        }

        val endFinger = resolveActiveFingerSnapshot(service) ?: initialFinger
        val pUpAction = MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        val pUp = obtainDualTouchEvent(
            downTime = initialFinger.downTime,
            eventTime = maxOf(SystemClock.uptimeMillis(), initialFinger.downTime + 3L),
            action = pUpAction,
            deviceId = initialFinger.deviceId,
            p0Id = initialFinger.pointerId,
            p0x = endFinger.x,
            p0y = endFinger.y,
            p1Id = initialFinger.injectedPointerId,
            p1x = endX,
            p1y = endY
        )
        val upOk = injectSafely(service, pUp, MODE_WAIT_FOR_FINISH)
        pUp.recycle()
        return upOk
    }

    private data class FingerSnapshot(
        val x: Float,
        val y: Float,
        val downTime: Long,
        val deviceId: Int,
        val pointerId: Int
    ) {
        val injectedPointerId: Int get() = if (pointerId == 0) 1 else 0
    }

    private fun resolveActiveFingerSnapshot(service: IInputInjectorService): FingerSnapshot? {
        val diag = _diagnostics.value
        if (diag.devInputReadable && diag.touchMonitorRunning) {
            val parts = runCatching { service.readLatestFingerState().split(',') }.getOrNull()
            if (parts != null && parts.size >= 4 && parts[0] == "1") {
                val nx = parts[1].toFloatOrNull()
                val ny = parts[2].toFloatOrNull()
                if (nx != null && ny != null) {
                    val metrics = context.resources.displayMetrics
                    val fx = if (nx <= 1.0f) nx * metrics.widthPixels else nx
                    val fy = if (ny <= 1.0f) ny * metrics.heightPixels else ny
                    return FingerSnapshot(
                        x = fx,
                        y = fy,
                        downTime = if (diag.cooperativeFingerDownTime > 0L) diag.cooperativeFingerDownTime else SystemClock.uptimeMillis() - 100L,
                        deviceId = diag.physicalTouchDeviceId,
                        pointerId = diag.cooperativeFingerPointerId
                    )
                }
            }
        }
        if (diag.cooperativeFingerDown) {
            return FingerSnapshot(
                x = diag.cooperativeFingerX,
                y = diag.cooperativeFingerY,
                downTime = if (diag.cooperativeFingerDownTime > 0L) diag.cooperativeFingerDownTime else SystemClock.uptimeMillis(),
                deviceId = diag.physicalTouchDeviceId,
                pointerId = diag.cooperativeFingerPointerId
            )
        }
        return null
    }

    private suspend fun injectSafely(
        service: IInputInjectorService,
        event: MotionEvent,
        mode: Int
    ): Boolean {
        return withTimeoutOrNull(2500L) {
            try {
                service.injectInputEvent(event, mode)
            } catch (_: Throwable) {
                false
            }
        } ?: false
    }

    private fun obtainDualTouchEvent(
        downTime: Long,
        eventTime: Long,
        action: Int,
        deviceId: Int,
        p0Id: Int,
        p0x: Float,
        p0y: Float,
        p1Id: Int,
        p1x: Float,
        p1y: Float
    ): MotionEvent {
        val props = arrayOf(
            MotionEvent.PointerProperties().apply {
                id = p0Id
                toolType = MotionEvent.TOOL_TYPE_FINGER
            },
            MotionEvent.PointerProperties().apply {
                id = p1Id
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
        )
        val coords = arrayOf(
            MotionEvent.PointerCoords().apply {
                x = p0x.roundToInt().toFloat()
                y = p0y.roundToInt().toFloat()
                pressure = 1.0f
                size = 1.0f
            },
            MotionEvent.PointerCoords().apply {
                x = p1x.roundToInt().toFloat()
                y = p1y.roundToInt().toFloat()
                pressure = 1.0f
                size = 1.0f
            }
        )
        return MotionEvent.obtain(
            downTime,
            eventTime,
            action,
            2,
            props,
            coords,
            0,
            0,
            1.0f,
            1.0f,
            deviceId,
            0,
            InputDevice.SOURCE_TOUCHSCREEN,
            0
        )
    }

    private fun obtainPointerEvent(
        downTime: Long,
        eventTime: Long,
        action: Int,
        x: Float,
        y: Float,
        mode: InputInjectionMode,
        deviceId: Int = 0,
        overrideButtonState: Int? = null,
        actionButton: Int? = null
    ): MotionEvent {
        val toolType = when (mode) {
            InputInjectionMode.MOUSE, InputInjectionMode.MOUSE_HOVER_CLICK -> MotionEvent.TOOL_TYPE_MOUSE
            InputInjectionMode.STYLUS -> MotionEvent.TOOL_TYPE_STYLUS
            InputInjectionMode.TOUCHSCREEN, InputInjectionMode.CO_TOUCH_MULTIPLEX -> MotionEvent.TOOL_TYPE_FINGER
        }
        val source = when (mode) {
            InputInjectionMode.MOUSE, InputInjectionMode.MOUSE_HOVER_CLICK -> InputDevice.SOURCE_MOUSE
            InputInjectionMode.STYLUS -> InputDevice.SOURCE_STYLUS
            InputInjectionMode.TOUCHSCREEN, InputInjectionMode.CO_TOUCH_MULTIPLEX -> InputDevice.SOURCE_TOUCHSCREEN
        }
        val isActivePress = action == MotionEvent.ACTION_DOWN ||
            action == MotionEvent.ACTION_MOVE ||
            action == MotionEvent.ACTION_BUTTON_PRESS
        val buttonState = overrideButtonState ?: if (mode == InputInjectionMode.MOUSE && isActivePress) {
            MotionEvent.BUTTON_PRIMARY
        } else {
            0
        }

        val props = arrayOf(
            MotionEvent.PointerProperties().apply {
                id = 0
                this.toolType = toolType
            }
        )
        val coords = arrayOf(
            MotionEvent.PointerCoords().apply {
                this.x = x.roundToInt().toFloat()
                this.y = y.roundToInt().toFloat()
                pressure = if (isActivePress) 1.0f else 0.0f
                size = 1.0f
            }
        )

        val event = MotionEvent.obtain(
            downTime,
            eventTime,
            action,
            1,
            props,
            coords,
            0,
            buttonState,
            1.0f,
            1.0f,
            deviceId,
            0,
            source,
            0
        )
        if (actionButton != null) {
            val setOk = runCatching {
                val method = MotionEvent::class.java.getDeclaredMethod("setActionButton", Int::class.javaPrimitiveType)
                method.isAccessible = true
                method.invoke(event, actionButton)
                true
            }.getOrDefault(false)
            if (!setOk) {
                _diagnostics.update { it.copy(lastBypassReport = "Hidden API blocked MotionEvent.setActionButton") }
            }
        }
        return event
    }

    private data class PanelCoords(val x: Float, val y: Float, val w: Int, val h: Int)

    private fun toNaturalPanelCoords(x: Float, y: Float): PanelCoords {
        val (sw, sh) = realDisplaySize()
        @Suppress("DEPRECATION")
        val rotation = runCatching {
            val wm = context.getSystemService(WindowManager::class.java)
            wm?.defaultDisplay?.rotation ?: Surface.ROTATION_0
        }.getOrDefault(Surface.ROTATION_0)

        return when (rotation) {
            Surface.ROTATION_90 -> PanelCoords(
                x = ((1f - (y / sh.toFloat())) * sh).coerceIn(0f, sh.toFloat()),
                y = ((x / sw.toFloat()) * sw).coerceIn(0f, sw.toFloat()),
                w = sh,
                h = sw
            )
            Surface.ROTATION_180 -> PanelCoords(
                x = (sw.toFloat() - x).coerceIn(0f, sw.toFloat()),
                y = (sh.toFloat() - y).coerceIn(0f, sh.toFloat()),
                w = sw,
                h = sh
            )
            Surface.ROTATION_270 -> PanelCoords(
                x = ((y / sh.toFloat()) * sh).coerceIn(0f, sh.toFloat()),
                y = ((1f - (x / sw.toFloat())) * sw).coerceIn(0f, sw.toFloat()),
                w = sh,
                h = sw
            )
            else -> PanelCoords(x = x, y = y, w = sw, h = sh)
        }
    }

    private fun realDisplaySize(): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val wm = context.getSystemService(WindowManager::class.java)
            val bounds = wm?.maximumWindowMetrics?.bounds
            if (bounds != null && bounds.width() > 0 && bounds.height() > 0) {
                return bounds.width() to bounds.height()
            }
        }
        val dm = context.resources.displayMetrics
        return dm.widthPixels.coerceAtLeast(1) to dm.heightPixels.coerceAtLeast(1)
    }

    companion object {
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        private const val SHIZUKU_PERMISSION_REQUEST_CODE = 1042
        private const val MODE_ASYNC = 0
        private const val MODE_WAIT_FOR_FINISH = 2
    }
}
