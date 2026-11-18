package com.example.shizuku

import android.annotation.SuppressLint
import android.hardware.input.InputManager
import android.util.Log
import android.view.InputDevice
import android.view.InputEvent
import android.view.MotionEvent
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

class InputInjectorUserService : IInputInjectorService.Stub() {

    private var inputManagerInstance: Any? = null
    private var injectInputEventMethod: Method? = null

    private val monitorRunning = AtomicBoolean(false)
    private var monitorProcess: Process? = null
    private var monitorThread: Thread? = null

    @Volatile
    private var fingerDown: Boolean = false

    @Volatile
    private var fingerX: Float = 0f

    @Volatile
    private var fingerY: Float = 0f

    @Volatile
    private var fingerUpdatedMs: Long = 0L

    @Volatile
    private var cachedTouchMaxX: Float = 1080f

    @Volatile
    private var cachedTouchMaxY: Float = 2400f

    init {
        resolveInputManager()
    }

    @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
    private fun resolveInputManager() {
        try {
            val imClass = InputManager::class.java
            val getInstance = imClass.getDeclaredMethod("getInstance")
            getInstance.isAccessible = true
            inputManagerInstance = getInstance.invoke(null)

            injectInputEventMethod = imClass.getDeclaredMethod(
                "injectInputEvent",
                InputEvent::class.java,
                Int::class.javaPrimitiveType
            )
            injectInputEventMethod?.isAccessible = true
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to reflect InputManager.injectInputEvent", t)
        }
    }

    override fun injectInputEvent(event: InputEvent?, mode: Int): Boolean {
        if (event == null) return false
        val im = inputManagerInstance ?: run {
            resolveInputManager()
            inputManagerInstance
        } ?: return false
        val method = injectInputEventMethod ?: return false
        return try {
            (method.invoke(im, event, mode) as? Boolean) ?: false
        } catch (t: Throwable) {
            Log.e(TAG, "injectInputEvent failed", t)
            false
        }
    }

    override fun ping(): Boolean = inputManagerInstance != null && injectInputEventMethod != null

    override fun probeCapabilities(): String {
        val touchDevice = findPrimaryTouchDevice()
        val touchId = touchDevice?.id ?: 0
        val touchName = touchDevice?.name ?: "None Detected"
        touchDevice?.getMotionRange(MotionEvent.AXIS_X)?.let {
            if (it.max > 0f) cachedTouchMaxX = it.max
        }
        touchDevice?.getMotionRange(MotionEvent.AXIS_Y)?.let {
            if (it.max > 0f) cachedTouchMaxY = it.max
        }

        val readableNode = findReadableTouchEventNode(touchName)
        val devInputReadable = readableNode != null
        val rootAvailable = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/su/bin/su"
        ).any { path -> runCatching { File(path).exists() }.getOrDefault(false) }

        val accessSummary = if (devInputReadable) {
            "evdev readable ($readableNode)"
        } else {
            "/dev/input SELinux-blocked for uid 2000"
        }

        return JSONObject().apply {
            put("touchDeviceId", touchId)
            put("touchDeviceName", touchName)
            put("devInputReadable", devInputReadable)
            put("rootAvailable", rootAvailable)
            put("touchMonitorRunning", monitorRunning.get())
            put("note", "$touchName (id=$touchId) • $accessSummary")
        }.toString()
    }

    override fun startTouchMonitor(): Boolean {
        if (monitorRunning.get()) return true
        val touchDevice = findPrimaryTouchDevice()
        val node = findReadableTouchEventNode(touchDevice?.name ?: "") ?: return false

        touchDevice?.getMotionRange(MotionEvent.AXIS_X)?.let {
            if (it.max > 0f) cachedTouchMaxX = it.max
        }
        touchDevice?.getMotionRange(MotionEvent.AXIS_Y)?.let {
            if (it.max > 0f) cachedTouchMaxY = it.max
        }

        return try {
            val proc = ProcessBuilder("getevent", "-lt", node)
                .redirectErrorStream(true)
                .start()
            monitorProcess = proc
            monitorRunning.set(true)
            monitorThread = Thread({
                try {
                    BufferedReader(InputStreamReader(proc.inputStream)).use { reader ->
                        var curSlot = 0
                        while (monitorRunning.get()) {
                            val line = reader.readLine() ?: break
                            when {
                                line.contains("ABS_MT_SLOT") -> {
                                    curSlot = parseTrailingHexInt(line) ?: 0
                                }
                                line.contains("ABS_MT_TRACKING_ID") && curSlot == 0 -> {
                                    val raw = line.substringAfterLast(' ').trim()
                                    val lifted = raw.equals("ffffffff", ignoreCase = true) || raw == "-1"
                                    fingerDown = !lifted
                                    fingerUpdatedMs = System.currentTimeMillis()
                                }
                                line.contains("BTN_TOUCH") -> {
                                    if (line.contains("DOWN")) {
                                        fingerDown = true
                                        fingerUpdatedMs = System.currentTimeMillis()
                                    } else if (line.contains("UP")) {
                                        fingerDown = false
                                        fingerUpdatedMs = System.currentTimeMillis()
                                    }
                                }
                                line.contains("ABS_MT_POSITION_X") && curSlot == 0 -> {
                                    val v = parseTrailingHexInt(line)
                                    if (v != null && v >= 0) {
                                        fingerX = v.toFloat()
                                        fingerUpdatedMs = System.currentTimeMillis()
                                    }
                                }
                                line.contains("ABS_MT_POSITION_Y") && curSlot == 0 -> {
                                    val v = parseTrailingHexInt(line)
                                    if (v != null && v >= 0) {
                                        fingerY = v.toFloat()
                                        fingerUpdatedMs = System.currentTimeMillis()
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Throwable) {
                } finally {
                    monitorRunning.set(false)
                }
            }, "ShizuTouchMon").apply {
                isDaemon = true
                start()
            }
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to start getevent touch monitor", t)
            monitorRunning.set(false)
            false
        }
    }

    override fun stopTouchMonitor() {
        monitorRunning.set(false)
        runCatching { monitorProcess?.destroy() }
        monitorProcess = null
        monitorThread = null
        fingerDown = false
    }

    override fun readLatestFingerState(): String {
        val downFlag = if (fingerDown) 1 else 0
        val nx = if (cachedTouchMaxX > 0f) (fingerX / cachedTouchMaxX).coerceIn(0f, 1f) else fingerX
        val ny = if (cachedTouchMaxY > 0f) (fingerY / cachedTouchMaxY).coerceIn(0f, 1f) else fingerY
        return "$downFlag,$nx,$ny,$fingerUpdatedMs"
    }

    override fun destroy() {
        stopTouchMonitor()
        exitProcess(0)
    }

    private fun findPrimaryTouchDevice(): InputDevice? {
        val ids = runCatching { InputDevice.getDeviceIds() }.getOrElse { return null }
        return ids.asSequence()
            .mapNotNull { id -> runCatching { InputDevice.getDevice(id) }.getOrNull() }
            .firstOrNull { dev ->
                !dev.isVirtual &&
                    (dev.sources and InputDevice.SOURCE_TOUCHSCREEN) == InputDevice.SOURCE_TOUCHSCREEN
            }
    }

    private fun findReadableTouchEventNode(touchName: String): String? {
        val devInput = File("/dev/input")
        val nodes = runCatching {
            devInput.listFiles { _, name -> name.startsWith("event") }?.sortedBy { it.name }
        }.getOrNull() ?: return null

        var fallbackReadable: String? = null
        for (node in nodes) {
            val canOpen = runCatching {
                FileInputStream(node).use { true }
            }.getOrDefault(false)
            if (!canOpen) continue

            if (fallbackReadable == null) {
                fallbackReadable = node.absolutePath
            }
            val sysName = runCatching {
                File("/sys/class/input/${node.name}/device/name").readText().trim()
            }.getOrDefault("")
            if (touchName.isNotBlank() && sysName.equals(touchName, ignoreCase = true)) {
                return node.absolutePath
            }
        }
        return fallbackReadable
    }

    private fun parseTrailingHexInt(line: String): Int? {
        val token = line.trim().substringAfterLast(' ').trim()
        return token.toLongOrNull(16)?.toInt()
    }

    companion object {
        private const val TAG = "ShizuMacroUserService"
    }
}
