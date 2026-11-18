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
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.lang.reflect.Method
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt
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
    private var cachedTouchMinX: Float = 0f

    @Volatile
    private var cachedTouchMaxX: Float = 4095f

    @Volatile
    private var cachedTouchMinY: Float = 0f

    @Volatile
    private var cachedTouchMaxY: Float = 4095f

    @Volatile
    private var cachedEvdevMaxSlot: Int = 9

    @Volatile
    private var evdevSupportsTouchMajor: Boolean = false

    @Volatile
    private var evdevSupportsPressure: Boolean = false

    @Volatile
    private var cachedTouchNodePath: String? = null

    @Volatile
    private var cachedEvdevWritable: Boolean = false

    @Volatile
    private var lastInjectDiagnostic: String = "No events injected yet"

    private val trackingIdSeq = AtomicInteger(9000)

    private var isIInputManagerFallback: Boolean = false

    init {
        exemptHiddenApis()
        resolveInputManager()
    }

    @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
    private fun exemptHiddenApis() {
        runCatching {
            val vmRuntimeClass = Class.forName("dalvik.system.VMRuntime")
            val getRuntime = vmRuntimeClass.getDeclaredMethod("getRuntime")
            getRuntime.isAccessible = true
            val runtime = getRuntime.invoke(null)
            val setExemptions = vmRuntimeClass.getDeclaredMethod(
                "setHiddenApiExemptions",
                Array<String>::class.java
            )
            setExemptions.isAccessible = true
            setExemptions.invoke(runtime, arrayOf("L"))
        }
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
            isIInputManagerFallback = false
        } catch (t: Throwable) {
            Log.w(TAG, "InputManager.getInstance failed, trying IInputManager binder", t)
            resolveViaServiceManager(t)
        }
    }

    @SuppressLint("PrivateApi")
    private fun resolveViaServiceManager(primaryError: Throwable) {
        try {
            val smClass = Class.forName("android.os.ServiceManager")
            val getService = smClass.getDeclaredMethod("getService", String::class.java)
            getService.isAccessible = true
            val binder = getService.invoke(null, "input") as? android.os.IBinder
                ?: error("ServiceManager.getService(\"input\") returned null")
            val stubClass = Class.forName("android.hardware.input.IInputManager\$Stub")
            val asInterface = stubClass.getDeclaredMethod("asInterface", android.os.IBinder::class.java)
            asInterface.isAccessible = true
            val iim = asInterface.invoke(null, binder) ?: error("IInputManager.Stub.asInterface returned null")
            val method = iim.javaClass.getDeclaredMethod(
                "injectInputEvent",
                InputEvent::class.java,
                Int::class.javaPrimitiveType
            )
            method.isAccessible = true
            inputManagerInstance = iim
            injectInputEventMethod = method
            isIInputManagerFallback = true
        } catch (fallbackErr: Throwable) {
            lastInjectDiagnostic = "Reflect error: ${primaryError.javaClass.simpleName}; binder: ${fallbackErr.message}"
            Log.e(TAG, "Failed to resolve InputManager & IInputManager", fallbackErr)
        }
    }

    override fun injectInputEvent(event: InputEvent?, mode: Int): Boolean {
        if (event == null) {
            lastInjectDiagnostic = "null event"
            return false
        }
        val im = inputManagerInstance ?: run {
            resolveInputManager()
            inputManagerInstance
        } ?: return false
        val method = injectInputEventMethod ?: return false
        return try {
            if (event is MotionEvent) {
                if (event.source == InputDevice.SOURCE_UNKNOWN) {
                    event.source = InputDevice.SOURCE_TOUCHSCREEN
                }
                val masked = event.actionMasked
                if ((masked == MotionEvent.ACTION_BUTTON_PRESS || masked == MotionEvent.ACTION_BUTTON_RELEASE) &&
                    event.actionButton == 0
                ) {
                    runCatching {
                        val setBtn = MotionEvent::class.java.getDeclaredMethod("setActionButton", Int::class.javaPrimitiveType)
                        setBtn.isAccessible = true
                        setBtn.invoke(event, MotionEvent.BUTTON_PRIMARY)
                    }
                }
            }
            val ok = (method.invoke(im, event, mode) as? Boolean) ?: false
            val uid = android.os.Process.myUid()
            lastInjectDiagnostic = if (event is MotionEvent) {
                "ok=$ok uid=$uid act=${MotionEvent.actionToString(event.action)} dev=${event.deviceId} ptrs=${event.pointerCount} src=0x${Integer.toHexString(event.source)}"
            } else {
                "ok=$ok uid=$uid key"
            }
            ok
        } catch (t: Throwable) {
            val cause = t.cause ?: t
            lastInjectDiagnostic = "${cause.javaClass.simpleName}: ${cause.message}"
            Log.e(TAG, "injectInputEvent failed: $lastInjectDiagnostic", t)
            false
        }
    }

    override fun ping(): Boolean = inputManagerInstance != null && injectInputEventMethod != null

    override fun getLastInjectDiagnostic(): String = lastInjectDiagnostic

    override fun probeCapabilities(): String {
        val touchDevice = findPrimaryTouchDevice()
        val touchId = touchDevice?.id ?: 0
        val touchName = touchDevice?.name ?: "None Detected"
        touchDevice?.getMotionRange(MotionEvent.AXIS_X)?.let {
            if (it.max > 0f) {
                cachedTouchMinX = it.min
                cachedTouchMaxX = it.max
            }
        }
        touchDevice?.getMotionRange(MotionEvent.AXIS_Y)?.let {
            if (it.max > 0f) {
                cachedTouchMinY = it.min
                cachedTouchMaxY = it.max
            }
        }

        val readableNode = findReadableTouchEventNode(touchName)
        cachedTouchNodePath = readableNode
        val devInputReadable = readableNode != null
        if (readableNode != null) {
            parseEvdevAxisRanges(readableNode)
        }

        val devInputWritable = readableNode != null && testNodeWritable(readableNode)
        cachedEvdevWritable = devInputWritable
        val uinputWritable = testNodeWritable("/dev/uinput")
        val uhidWritable = testNodeWritable("/dev/uhid")

        val rootAvailable = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/su/bin/su"
        ).any { path -> runCatching { File(path).exists() }.getOrDefault(false) }

        val accessSummary = when {
            devInputWritable -> "evdev RW ($readableNode • maxSlot=$cachedEvdevMaxSlot)"
            devInputReadable && uhidWritable -> "evdev RO ($readableNode) • /dev/uhid RW"
            devInputReadable && uinputWritable -> "evdev RO ($readableNode) • /dev/uinput RW"
            devInputReadable -> "evdev RO ($readableNode) • UHID blocked"
            uhidWritable -> "evdev blocked • /dev/uhid RW"
            else -> "/dev/input & /dev/uhid SELinux-blocked"
        }

        val serviceUid = android.os.Process.myUid()
        val imPath = if (isIInputManagerFallback) "IInputManager" else "InputManager"

        return JSONObject().apply {
            put("serviceUid", serviceUid)
            put("imBackend", imPath)
            put("touchDeviceId", touchId)
            put("touchDeviceName", touchName)
            put("devInputReadable", devInputReadable)
            put("devInputWritable", devInputWritable)
            put("uinputWritable", uinputWritable)
            put("uhidWritable", uhidWritable)
            put("rootAvailable", rootAvailable)
            put("touchMonitorRunning", monitorRunning.get())
            put("note", "uid=$serviceUid ($imPath) • $touchName (id=$touchId) • $accessSummary")
        }.toString()
    }

    override fun startTouchMonitor(): Boolean {
        if (monitorRunning.get()) return true
        val touchDevice = findPrimaryTouchDevice()
        val node = findReadableTouchEventNode(touchDevice?.name ?: "") ?: return false
        cachedTouchNodePath = node
        parseEvdevAxisRanges(node)

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
        val spanX = (cachedTouchMaxX - cachedTouchMinX).coerceAtLeast(1f)
        val spanY = (cachedTouchMaxY - cachedTouchMinY).coerceAtLeast(1f)
        val nx = ((fingerX - cachedTouchMinX) / spanX).coerceIn(0f, 1f)
        val ny = ((fingerY - cachedTouchMinY) / spanY).coerceIn(0f, 1f)
        return "$downFlag,$nx,$ny,$fingerUpdatedMs"
    }

    override fun injectEvdevTap(
        screenX: Float,
        screenY: Float,
        screenW: Int,
        screenH: Int,
        holdMs: Long
    ): Boolean {
        val node = cachedTouchNodePath ?: return false
        if (!cachedEvdevWritable) return false
        val macroSlot = cachedEvdevMaxSlot.coerceIn(2, 9)
        val tid = trackingIdSeq.incrementAndGet().let { if (it > 65000) 9000 else it }
        val rx = scaleScreenToEvdevX(screenX, screenW)
        val ry = scaleScreenToEvdevY(screenY, screenH)

        return try {
            FileOutputStream(node, true).use { fos ->
                val downEvents = mutableListOf(
                    Triple(EV_ABS, ABS_MT_SLOT, macroSlot),
                    Triple(EV_ABS, ABS_MT_TRACKING_ID, tid),
                    Triple(EV_ABS, ABS_MT_POSITION_X, rx),
                    Triple(EV_ABS, ABS_MT_POSITION_Y, ry)
                )
                if (evdevSupportsTouchMajor) {
                    downEvents.add(Triple(EV_ABS, ABS_MT_TOUCH_MAJOR, 12))
                }
                if (evdevSupportsPressure) {
                    downEvents.add(Triple(EV_ABS, ABS_MT_PRESSURE, 64))
                }
                val sentBtnTouchDown = !fingerDown
                if (sentBtnTouchDown) {
                    downEvents.add(Triple(EV_KEY, BTN_TOUCH, 1))
                    downEvents.add(Triple(EV_KEY, BTN_TOOL_FINGER, 1))
                }
                downEvents.add(Triple(EV_SYN, SYN_REPORT, 0))
                if (fingerDown) {
                    downEvents.add(Triple(EV_ABS, ABS_MT_SLOT, 0))
                    downEvents.add(Triple(EV_SYN, SYN_REPORT, 0))
                }
                fos.write(encodeInputEvents(downEvents))
                fos.flush()

                if (holdMs > 0) {
                    Thread.sleep(holdMs.coerceIn(10L, 1500L))
                }

                val upEvents = mutableListOf(
                    Triple(EV_ABS, ABS_MT_SLOT, macroSlot),
                    Triple(EV_ABS, ABS_MT_TRACKING_ID, -1)
                )
                if (!fingerDown && sentBtnTouchDown) {
                    upEvents.add(Triple(EV_KEY, BTN_TOUCH, 0))
                    upEvents.add(Triple(EV_KEY, BTN_TOOL_FINGER, 0))
                }
                upEvents.add(Triple(EV_SYN, SYN_REPORT, 0))
                if (fingerDown) {
                    upEvents.add(Triple(EV_ABS, ABS_MT_SLOT, 0))
                    upEvents.add(Triple(EV_SYN, SYN_REPORT, 0))
                }
                fos.write(encodeInputEvents(upEvents))
                fos.flush()
            }
            lastInjectDiagnostic = "evdev Slot$macroSlot tap OK ($rx,$ry) on $node"
            true
        } catch (t: Throwable) {
            lastInjectDiagnostic = "evdev write failed: ${t.javaClass.simpleName}: ${t.message}"
            cachedEvdevWritable = false
            false
        }
    }

    override fun injectEvdevSwipe(
        sx: Float,
        sy: Float,
        ex: Float,
        ey: Float,
        screenW: Int,
        screenH: Int,
        durationMs: Long,
        steps: Int
    ): Boolean {
        val node = cachedTouchNodePath ?: return false
        if (!cachedEvdevWritable) return false
        val macroSlot = cachedEvdevMaxSlot.coerceIn(2, 9)
        val tid = trackingIdSeq.incrementAndGet().let { if (it > 65000) 9000 else it }
        val safeSteps = steps.coerceIn(4, 100)
        val stepDelay = (durationMs / safeSteps).coerceAtLeast(4L)

        return try {
            FileOutputStream(node, true).use { fos ->
                val startRx = scaleScreenToEvdevX(sx, screenW)
                val startRy = scaleScreenToEvdevY(sy, screenH)
                val sentBtnTouchDown = !fingerDown
                val downEvents = mutableListOf(
                    Triple(EV_ABS, ABS_MT_SLOT, macroSlot),
                    Triple(EV_ABS, ABS_MT_TRACKING_ID, tid),
                    Triple(EV_ABS, ABS_MT_POSITION_X, startRx),
                    Triple(EV_ABS, ABS_MT_POSITION_Y, startRy)
                )
                if (evdevSupportsTouchMajor) downEvents.add(Triple(EV_ABS, ABS_MT_TOUCH_MAJOR, 12))
                if (evdevSupportsPressure) downEvents.add(Triple(EV_ABS, ABS_MT_PRESSURE, 64))
                if (sentBtnTouchDown) {
                    downEvents.add(Triple(EV_KEY, BTN_TOUCH, 1))
                    downEvents.add(Triple(EV_KEY, BTN_TOOL_FINGER, 1))
                }
                downEvents.add(Triple(EV_SYN, SYN_REPORT, 0))
                if (fingerDown) {
                    downEvents.add(Triple(EV_ABS, ABS_MT_SLOT, 0))
                    downEvents.add(Triple(EV_SYN, SYN_REPORT, 0))
                }
                fos.write(encodeInputEvents(downEvents))
                fos.flush()

                for (i in 1..safeSteps) {
                    Thread.sleep(stepDelay)
                    val t = i.toFloat() / safeSteps.toFloat()
                    val cx = sx + (ex - sx) * t
                    val cy = sy + (ey - sy) * t
                    val stepEvents = mutableListOf(
                        Triple(EV_ABS, ABS_MT_SLOT, macroSlot),
                        Triple(EV_ABS, ABS_MT_POSITION_X, scaleScreenToEvdevX(cx, screenW)),
                        Triple(EV_ABS, ABS_MT_POSITION_Y, scaleScreenToEvdevY(cy, screenH)),
                        Triple(EV_SYN, SYN_REPORT, 0)
                    )
                    if (fingerDown) {
                        stepEvents.add(Triple(EV_ABS, ABS_MT_SLOT, 0))
                        stepEvents.add(Triple(EV_SYN, SYN_REPORT, 0))
                    }
                    fos.write(encodeInputEvents(stepEvents))
                    fos.flush()
                }

                val upEvents = mutableListOf(
                    Triple(EV_ABS, ABS_MT_SLOT, macroSlot),
                    Triple(EV_ABS, ABS_MT_TRACKING_ID, -1)
                )
                if (!fingerDown && sentBtnTouchDown) {
                    upEvents.add(Triple(EV_KEY, BTN_TOUCH, 0))
                    upEvents.add(Triple(EV_KEY, BTN_TOOL_FINGER, 0))
                }
                upEvents.add(Triple(EV_SYN, SYN_REPORT, 0))
                if (fingerDown) {
                    upEvents.add(Triple(EV_ABS, ABS_MT_SLOT, 0))
                    upEvents.add(Triple(EV_SYN, SYN_REPORT, 0))
                }
                fos.write(encodeInputEvents(upEvents))
                fos.flush()
            }
            lastInjectDiagnostic = "evdev Slot$macroSlot swipe OK on $node"
            true
        } catch (t: Throwable) {
            lastInjectDiagnostic = "evdev swipe failed: ${t.javaClass.simpleName}: ${t.message}"
            cachedEvdevWritable = false
            false
        }
    }

    override fun destroy() {
        stopTouchMonitor()
        exitProcess(0)
    }

    private fun scaleScreenToEvdevX(x: Float, screenW: Int): Int {
        val w = screenW.coerceAtLeast(1).toFloat()
        val norm = (x / w).coerceIn(0f, 1f)
        return (cachedTouchMinX + norm * (cachedTouchMaxX - cachedTouchMinX)).roundToInt()
    }

    private fun scaleScreenToEvdevY(y: Float, screenH: Int): Int {
        val h = screenH.coerceAtLeast(1).toFloat()
        val norm = (y / h).coerceIn(0f, 1f)
        return (cachedTouchMinY + norm * (cachedTouchMaxY - cachedTouchMinY)).roundToInt()
    }

    private fun encodeInputEvents(events: List<Triple<Int, Int, Int>>): ByteArray {
        val is64 = runCatching { android.os.Process.is64Bit() }.getOrDefault(true)
        val stride = if (is64) 24 else 16
        val buf = ByteBuffer.allocate(events.size * stride).order(ByteOrder.LITTLE_ENDIAN)
        for ((type, code, value) in events) {
            if (is64) {
                buf.putLong(0L) // tv_sec
                buf.putLong(0L) // tv_usec
            } else {
                buf.putInt(0)
                buf.putInt(0)
            }
            buf.putShort(type.toShort())
            buf.putShort(code.toShort())
            buf.putInt(value)
        }
        return buf.array()
    }

    private fun testNodeWritable(path: String): Boolean {
        return runCatching {
            FileOutputStream(File(path), true).use { true }
        }.getOrDefault(false)
    }

    private fun parseEvdevAxisRanges(nodePath: String) {
        runCatching {
            val proc = ProcessBuilder("getevent", "-p", nodePath)
                .redirectErrorStream(true)
                .start()
            val out = BufferedReader(InputStreamReader(proc.inputStream)).use { it.readText() }
            proc.waitFor()
            val rangeRegex = Regex("""min\s+(-?\d+),\s+max\s+(-?\d+)""")
            for (line in out.lines()) {
                val trimmed = line.trim()
                val match = rangeRegex.find(trimmed)
                when {
                    trimmed.startsWith("002f") && match != null -> {
                        val maxSlot = match.groupValues[2].toIntOrNull()
                        if (maxSlot != null && maxSlot >= 2) {
                            cachedEvdevMaxSlot = maxSlot
                        }
                    }
                    trimmed.startsWith("0030") -> evdevSupportsTouchMajor = true
                    trimmed.startsWith("003a") -> evdevSupportsPressure = true
                    trimmed.startsWith("0035") && match != null -> {
                        val minX = match.groupValues[1].toFloatOrNull()
                        val maxX = match.groupValues[2].toFloatOrNull()
                        if (minX != null && maxX != null && maxX > minX) {
                            cachedTouchMinX = minX
                            cachedTouchMaxX = maxX
                        }
                    }
                    trimmed.startsWith("0036") && match != null -> {
                        val minY = match.groupValues[1].toFloatOrNull()
                        val maxY = match.groupValues[2].toFloatOrNull()
                        if (minY != null && maxY != null && maxY > minY) {
                            cachedTouchMinY = minY
                            cachedTouchMaxY = maxY
                        }
                    }
                }
            }
        }
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
        private const val EV_SYN = 0x00
        private const val EV_KEY = 0x01
        private const val EV_ABS = 0x03
        private const val SYN_REPORT = 0x00
        private const val BTN_TOOL_FINGER = 0x145
        private const val BTN_TOUCH = 0x14a
        private const val ABS_MT_SLOT = 0x2f
        private const val ABS_MT_TOUCH_MAJOR = 0x30
        private const val ABS_MT_POSITION_X = 0x35
        private const val ABS_MT_POSITION_Y = 0x36
        private const val ABS_MT_TRACKING_ID = 0x39
        private const val ABS_MT_PRESSURE = 0x3a
    }
}
