package com.example.shizuku

import android.annotation.SuppressLint
import android.hardware.input.InputManager
import android.system.Os
import android.system.OsConstants
import android.util.Log
import android.view.InputDevice
import android.view.InputEvent
import android.view.MotionEvent
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileDescriptor
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

    private val uhidLock = Any()

    @Volatile
    private var uhidFd: FileDescriptor? = null

    @Volatile
    private var uhidStarted: Boolean = false

    @Volatile
    private var uhidDeviceId: Int = 0

    private var uhidReaderThread: Thread? = null

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
            if (event is MotionEvent && event.source == InputDevice.SOURCE_UNKNOWN) {
                event.source = InputDevice.SOURCE_TOUCHSCREEN
            } else if (event is android.view.KeyEvent && event.source == InputDevice.SOURCE_UNKNOWN) {
                event.source = InputDevice.SOURCE_KEYBOARD
            }
            val ok = (method.invoke(im, event, mode) as? Boolean) ?: false
            val uid = android.os.Process.myUid()
            lastInjectDiagnostic = when (event) {
                is MotionEvent ->
                    "ok=$ok uid=$uid act=${MotionEvent.actionToString(event.action)} dev=${event.deviceId} ptrs=${event.pointerCount} src=0x${Integer.toHexString(event.source)}"
                is android.view.KeyEvent ->
                    "ok=$ok uid=$uid key=${android.view.KeyEvent.keyCodeToString(event.keyCode)} act=${if (event.action == android.view.KeyEvent.ACTION_DOWN) "DOWN" else "UP"} src=0x${Integer.toHexString(event.source)}"
                else -> "ok=$ok uid=$uid event"
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

    override fun injectUhidTap(
        screenX: Float,
        screenY: Float,
        screenW: Int,
        screenH: Int,
        holdMs: Long
    ): Boolean {
        if (!ensureUhidTouchDevice()) return false
        val hx = scaleScreenToHid(screenX, screenW)
        val hy = scaleScreenToHid(screenY, screenH)
        return try {
            sendUhidTouchReport(touching = true, contactId = 1, x = hx, y = hy)
            if (holdMs > 0) {
                Thread.sleep(holdMs.coerceIn(10L, 1500L))
            }
            sendUhidTouchReport(touching = false, contactId = 1, x = hx, y = hy)
            val devId = refreshUhidDeviceId()
            lastInjectDiagnostic = "UHID Touch OK (dev=$devId, hid=$hx,$hy)"
            true
        } catch (t: Throwable) {
            lastInjectDiagnostic = "UHID tap failed: ${t.javaClass.simpleName}: ${t.message}"
            destroyUhidDevice()
            false
        }
    }

    override fun injectUhidSwipe(
        sx: Float,
        sy: Float,
        ex: Float,
        ey: Float,
        screenW: Int,
        screenH: Int,
        durationMs: Long,
        steps: Int
    ): Boolean {
        if (!ensureUhidTouchDevice()) return false
        val safeSteps = steps.coerceIn(4, 100)
        val stepDelay = (durationMs / safeSteps).coerceAtLeast(4L)
        return try {
            val startHx = scaleScreenToHid(sx, screenW)
            val startHy = scaleScreenToHid(sy, screenH)
            sendUhidTouchReport(touching = true, contactId = 1, x = startHx, y = startHy)

            for (i in 1..safeSteps) {
                Thread.sleep(stepDelay)
                val t = i.toFloat() / safeSteps.toFloat()
                val curHx = scaleScreenToHid(sx + (ex - sx) * t, screenW)
                val curHy = scaleScreenToHid(sy + (ey - sy) * t, screenH)
                sendUhidTouchReport(touching = true, contactId = 1, x = curHx, y = curHy)
            }

            val endHx = scaleScreenToHid(ex, screenW)
            val endHy = scaleScreenToHid(ey, screenH)
            sendUhidTouchReport(touching = false, contactId = 1, x = endHx, y = endHy)
            val devId = refreshUhidDeviceId()
            lastInjectDiagnostic = "UHID Swipe OK (dev=$devId)"
            true
        } catch (t: Throwable) {
            lastInjectDiagnostic = "UHID swipe failed: ${t.javaClass.simpleName}: ${t.message}"
            destroyUhidDevice()
            false
        }
    }

    private fun ensureUhidTouchDevice(): Boolean {
        synchronized(uhidLock) {
            val existing = uhidFd
            if (existing != null && existing.valid()) {
                return true
            }
            return try {
                val fd = Os.open("/dev/uhid", OsConstants.O_RDWR or OsConstants.O_CLOEXEC, 0)
                uhidFd = fd
                uhidStarted = false

                startUhidReaderThread(fd)

                val createBuf = ByteBuffer.allocate(UHID_EVENT_SIZE).order(ByteOrder.LITTLE_ENDIAN)
                createBuf.putInt(UHID_CREATE2)
                putFixedCString(createBuf, UHID_DEVICE_NAME, 128)
                putFixedCString(createBuf, "uhid/shizumacro0", 64)
                putFixedCString(createBuf, "shizumacro-hid-touch", 64)
                createBuf.putShort(UHID_TOUCH_REPORT_DESCRIPTOR.size.toShort())
                createBuf.putShort(BUS_USB.toShort())
                createBuf.putInt(0x18D1) // Google vendor ID
                createBuf.putInt(0x534D) // SM product ID
                createBuf.putInt(1)
                createBuf.putInt(0)
                createBuf.put(UHID_TOUCH_REPORT_DESCRIPTOR)

                writeUhidFully(fd, createBuf.array(), UHID_EVENT_SIZE)

                val deadline = System.currentTimeMillis() + 500L
                while (!uhidStarted && System.currentTimeMillis() < deadline) {
                    Thread.sleep(15L)
                }
                Thread.sleep(60L)
                refreshUhidDeviceId()
                true
            } catch (t: Throwable) {
                lastInjectDiagnostic = "UHID create failed: ${t.javaClass.simpleName}: ${t.message}"
                destroyUhidDeviceLocked()
                false
            }
        }
    }

    private fun startUhidReaderThread(fd: FileDescriptor) {
        uhidReaderThread = Thread({
            val buf = ByteArray(UHID_EVENT_SIZE)
            while (uhidFd === fd && fd.valid()) {
                val n = try {
                    Os.read(fd, buf, 0, buf.size)
                } catch (_: Throwable) {
                    break
                }
                if (n < 4) break
                val bb = ByteBuffer.wrap(buf, 0, n).order(ByteOrder.LITTLE_ENDIAN)
                when (bb.int) {
                    UHID_START -> {
                        uhidStarted = true
                    }
                    UHID_GET_REPORT -> {
                        if (n >= 10) {
                            val reqId = bb.int
                            val rnum = bb.get().toInt() and 0xFF
                            val reply = ByteBuffer.allocate(14).order(ByteOrder.LITTLE_ENDIAN)
                            reply.putInt(UHID_GET_REPORT_REPLY)
                            reply.putInt(reqId)
                            if (rnum == 2) {
                                reply.putShort(0) // err = 0
                                reply.putShort(2) // size = 2
                                reply.put(0x02.toByte()) // Report ID 2
                                reply.put(0x0A.toByte()) // Max Contacts = 10
                            } else {
                                reply.putShort(5.toShort()) // EIO
                                reply.putShort(0)
                            }
                            runCatching { writeUhidFully(fd, reply.array(), reply.capacity()) }
                        }
                    }
                    UHID_SET_REPORT -> {
                        if (n >= 10) {
                            val reqId = bb.int
                            val reply = ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN)
                            reply.putInt(UHID_SET_REPORT_REPLY)
                            reply.putInt(reqId)
                            reply.putShort(0)
                            runCatching { writeUhidFully(fd, reply.array(), reply.capacity()) }
                        }
                    }
                }
            }
        }, "ShizuUhidMon").apply {
            isDaemon = true
            start()
        }
    }

    private fun sendUhidTouchReport(touching: Boolean, contactId: Int, x: Int, y: Int) {
        synchronized(uhidLock) {
            val fd = uhidFd ?: error("UHID device closed")
            val packet = ByteBuffer.allocate(14).order(ByteOrder.LITTLE_ENDIAN)
            packet.putInt(UHID_INPUT2)
            packet.putShort(8) // 8-byte HID input report
            packet.put(0x01.toByte()) // Report ID 1
            packet.put((if (touching) 0x03 else 0x00).toByte()) // Tip Switch + In Range
            packet.put(contactId.toByte())
            packet.putShort(x.coerceIn(0, HID_AXIS_MAX).toShort())
            packet.putShort(y.coerceIn(0, HID_AXIS_MAX).toShort())
            packet.put((if (touching) 0x01 else 0x00).toByte()) // Contact Count
            writeUhidFully(fd, packet.array(), packet.capacity())
        }
    }

    private fun writeUhidFully(fd: FileDescriptor, data: ByteArray, length: Int) {
        var offset = 0
        while (offset < length) {
            val written = Os.write(fd, data, offset, length - offset)
            if (written <= 0) error("Short write to /dev/uhid")
            offset += written
        }
    }

    private fun putFixedCString(buf: ByteBuffer, text: String, fieldLen: Int) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val copyLen = minOf(bytes.size, fieldLen - 1)
        buf.put(bytes, 0, copyLen)
        repeat(fieldLen - copyLen) { buf.put(0) }
    }

    private fun scaleScreenToHid(coord: Float, screenDim: Int): Int {
        val dim = screenDim.coerceAtLeast(1).toFloat()
        val norm = (coord / dim).coerceIn(0f, 1f)
        return (norm * HID_AXIS_MAX).roundToInt().coerceIn(0, HID_AXIS_MAX)
    }

    private fun refreshUhidDeviceId(): Int {
        val dev = runCatching {
            InputDevice.getDeviceIds().asSequence()
                .mapNotNull { id -> runCatching { InputDevice.getDevice(id) }.getOrNull() }
                .firstOrNull { it.name == UHID_DEVICE_NAME }
        }.getOrNull()
        if (dev != null) {
            uhidDeviceId = dev.id
        }
        return uhidDeviceId
    }

    private fun destroyUhidDevice() {
        synchronized(uhidLock) {
            destroyUhidDeviceLocked()
        }
    }

    private fun destroyUhidDeviceLocked() {
        val fd = uhidFd
        uhidFd = null
        uhidStarted = false
        uhidDeviceId = 0
        if (fd != null && fd.valid()) {
            runCatching {
                val destroy = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(UHID_DESTROY)
                Os.write(fd, destroy.array(), 0, 4)
            }
            runCatching { Os.close(fd) }
        }
        uhidReaderThread = null
    }

    override fun destroy() {
        stopTouchMonitor()
        destroyUhidDevice()
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
        private const val UHID_DESTROY = 1
        private const val UHID_START = 2
        private const val UHID_GET_REPORT = 9
        private const val UHID_GET_REPORT_REPLY = 10
        private const val UHID_CREATE2 = 11
        private const val UHID_INPUT2 = 12
        private const val UHID_SET_REPORT = 13
        private const val UHID_SET_REPORT_REPLY = 14
        private const val UHID_EVENT_SIZE = 4376
        private const val BUS_USB = 0x03
        private const val HID_AXIS_MAX = 32767
        private const val UHID_DEVICE_NAME = "ShizuMacro-UHID-Touch"

        // Multi-touch USB HID Digitizer Touch Screen descriptor (Report ID 1 = 8-byte finger input, Report ID 2 = max contacts feature)
        private val UHID_TOUCH_REPORT_DESCRIPTOR = byteArrayOf(
            0x05, 0x0D,             // USAGE_PAGE (Digitizers)
            0x09, 0x04,             // USAGE (Touch Screen)
            0xA1.toByte(), 0x01,    // COLLECTION (Application)
            0x85.toByte(), 0x01,    //   REPORT_ID (1)
            0x09, 0x22,             //   USAGE (Finger)
            0xA1.toByte(), 0x02,    //   COLLECTION (Logical)
            0x09, 0x42,             //     USAGE (Tip Switch)
            0x15, 0x00,             //     LOGICAL_MINIMUM (0)
            0x25, 0x01,             //     LOGICAL_MAXIMUM (1)
            0x75, 0x01,             //     REPORT_SIZE (1)
            0x95.toByte(), 0x01,    //     REPORT_COUNT (1)
            0x81.toByte(), 0x02,    //     INPUT (Data,Var,Abs)
            0x09, 0x32,             //     USAGE (In Range)
            0x81.toByte(), 0x02,    //     INPUT (Data,Var,Abs)
            0x95.toByte(), 0x06,    //     REPORT_COUNT (6)
            0x81.toByte(), 0x03,    //     INPUT (Cnst,Var,Abs)
            0x75, 0x08,             //     REPORT_SIZE (8)
            0x09, 0x51,             //     USAGE (Contact Identifier)
            0x25, 0x0A,             //     LOGICAL_MAXIMUM (10)
            0x95.toByte(), 0x01,    //     REPORT_COUNT (1)
            0x81.toByte(), 0x02,    //     INPUT (Data,Var,Abs)
            0x05, 0x01,             //     USAGE_PAGE (Generic Desktop)
            0x15, 0x00,             //     LOGICAL_MINIMUM (0)
            0x26, 0xFF.toByte(), 0x7F, //  LOGICAL_MAXIMUM (32767)
            0x75, 0x10,             //     REPORT_SIZE (16)
            0x95.toByte(), 0x01,    //     REPORT_COUNT (1)
            0x09, 0x30,             //     USAGE (X)
            0x81.toByte(), 0x02,    //     INPUT (Data,Var,Abs)
            0x09, 0x31,             //     USAGE (Y)
            0x81.toByte(), 0x02,    //     INPUT (Data,Var,Abs)
            0xC0.toByte(),          //   END_COLLECTION
            0x05, 0x0D,             //   USAGE_PAGE (Digitizers)
            0x09, 0x54,             //   USAGE (Contact Count)
            0x15, 0x00,             //   LOGICAL_MINIMUM (0)
            0x25, 0x0A,             //   LOGICAL_MAXIMUM (10)
            0x75, 0x08,             //   REPORT_SIZE (8)
            0x95.toByte(), 0x01,    //   REPORT_COUNT (1)
            0x81.toByte(), 0x02,    //   INPUT (Data,Var,Abs)
            0x85.toByte(), 0x02,    //   REPORT_ID (2)
            0x09, 0x55,             //   USAGE (Contact Count Maximum)
            0x25, 0x0A,             //   LOGICAL_MAXIMUM (10)
            0xB1.toByte(), 0x02,    //   FEATURE (Data,Var,Abs)
            0xC0.toByte()           // END_COLLECTION
        )
    }
}
