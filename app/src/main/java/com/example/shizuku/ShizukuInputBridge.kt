package com.example.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
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

class ShizukuInputBridge(private val context: Context) {

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _status = MutableStateFlow(ShizukuStatus())
    val status: StateFlow<ShizukuStatus> = _status.asStateFlow()

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

    private val userServiceArgs by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(context.packageName, InputInjectorUserService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix("shizuku_injector")
            .debuggable(BuildConfig.DEBUG)
            .version(1)
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

        val downTime = SystemClock.uptimeMillis()
        activeDownTime = downTime
        activeLastX = tx
        activeLastY = ty
        activeInjectionMode = mode

        val down = obtainPointerEvent(downTime, downTime, MotionEvent.ACTION_DOWN, tx, ty, mode)
        val downOk = injectSafely(service, down, MODE_WAIT_FOR_FINISH)
        down.recycle()

        if (!downOk) {
            activeDownTime = null
            return@withContext false
        }

        if (holdMs > 0) {
            delay(holdMs)
        }

        val upTime = SystemClock.uptimeMillis()
        val up = obtainPointerEvent(downTime, upTime, MotionEvent.ACTION_UP, tx, ty, mode)
        val upOk = injectSafely(service, up, MODE_WAIT_FOR_FINISH)
        up.recycle()
        activeDownTime = null

        upOk
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
        val downTime = SystemClock.uptimeMillis()
        activeDownTime = downTime
        activeLastX = startX
        activeLastY = startY
        activeInjectionMode = mode

        val down = obtainPointerEvent(downTime, downTime, MotionEvent.ACTION_DOWN, startX, startY, mode)
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
                mode
            )
            try {
                service.injectInputEvent(move, MODE_ASYNC)
            } catch (_: Throwable) {
            } finally {
                move.recycle()
            }
        }

        val up = obtainPointerEvent(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, endX, endY, mode)
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
        activeDownTime = null
        val service = injectorService ?: return
        ioScope.launch {
            val cancel = obtainPointerEvent(
                downTime,
                SystemClock.uptimeMillis(),
                MotionEvent.ACTION_CANCEL,
                lastX,
                lastY,
                mode
            )
            try {
                service.injectInputEvent(cancel, MODE_ASYNC)
            } catch (_: Throwable) {
            } finally {
                cancel.recycle()
            }
        }
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

    private fun obtainPointerEvent(
        downTime: Long,
        eventTime: Long,
        action: Int,
        x: Float,
        y: Float,
        mode: InputInjectionMode
    ): MotionEvent {
        val toolType = when (mode) {
            InputInjectionMode.MOUSE -> MotionEvent.TOOL_TYPE_MOUSE
            InputInjectionMode.STYLUS -> MotionEvent.TOOL_TYPE_STYLUS
            InputInjectionMode.TOUCHSCREEN -> MotionEvent.TOOL_TYPE_FINGER
        }
        val source = when (mode) {
            InputInjectionMode.MOUSE -> InputDevice.SOURCE_MOUSE
            InputInjectionMode.STYLUS -> InputDevice.SOURCE_STYLUS
            InputInjectionMode.TOUCHSCREEN -> InputDevice.SOURCE_TOUCHSCREEN
        }
        val isActivePress = action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE
        val buttonState = if (mode == InputInjectionMode.MOUSE && isActivePress) {
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

        return MotionEvent.obtain(
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
            0,
            0,
            source,
            0
        )
    }

    companion object {
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        private const val SHIZUKU_PERMISSION_REQUEST_CODE = 1042
        private const val MODE_ASYNC = 0
        private const val MODE_WAIT_FOR_FINISH = 2
    }
}
