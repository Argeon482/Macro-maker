package com.example.ui.screens

import android.content.Context
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.model.InputInjectionMode
import com.example.shizuku.HardwareInputDiagnostics
import com.example.shizuku.ShizukuConnectionState
import com.example.shizuku.ShizukuStatus
import com.example.ui.theme.AmberLogic
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldRun
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.RoseAlert
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.SlateWell
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlin.math.hypot

private data class SandboxTouchDot(
    val x: Float,
    val y: Float,
    val isInjectedTool: Boolean
)

/**
 * Pure multi-pointer router shared by the unified Arena AndroidView and unit tests.
 * Routes every active pointer ID independently across Joystick and Target zones so
 * ACTION_POINTER_DOWN (Pointer 1 in Target) never drops even when Pointer 0 holds Joystick.
 */
internal class SandboxMultiPointerRouter(
    private val onJoystickState: (
        isDragging: Boolean,
        puckOffset: Offset,
        incrementMoves: Boolean,
        incrementCancel: Boolean,
        deviceId: Int,
        summary: String
    ) -> Unit,
    private val onCooperativeFingerUpdate: (
        isDown: Boolean,
        screenX: Float,
        screenY: Float,
        downTime: Long,
        deviceId: Int,
        pointerId: Int
    ) -> Unit,
    private val onTargetEvent: (
        localX: Float,
        localY: Float,
        toolLabel: String,
        isInjected: Boolean,
        countHit: Boolean,
        summaryOverride: String?
    ) -> Unit
) {
    var activeJoystickPointerId: Int? = null
        private set
    private var lastHoverButtonPressTime: Long = -1L

    fun reset() {
        activeJoystickPointerId = null
        lastHoverButtonPressTime = -1L
    }

    fun handleTouchEvent(
        event: MotionEvent,
        arenaWinX: Float,
        arenaWinY: Float,
        arenaScreenX: Float,
        arenaScreenY: Float,
        joystickRectInWin: RectF,
        targetRectInWin: RectF,
        selectedModeLabel: String
    ): Boolean {
        val action = event.actionMasked
        val idx = event.actionIndex

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                val pid = event.getPointerId(0)
                val wx = arenaWinX + event.getX(0)
                val wy = arenaWinY + event.getY(0)
                val sx = arenaScreenX + event.getX(0)
                val sy = arenaScreenY + event.getY(0)
                val rawTool = event.getToolType(0)
                val tool = toolName(rawTool)

                if (joystickRectInWin.contains(wx, wy)) {
                    activeJoystickPointerId = pid
                    val puck = clampJoystickOffset(wx, wy, joystickRectInWin)
                    onJoystickState(
                        true,
                        puck,
                        false,
                        false,
                        event.deviceId,
                        "Joystick DOWN ($tool P$pid dev=${event.deviceId})"
                    )
                    onCooperativeFingerUpdate(true, sx, sy, event.downTime, event.deviceId, pid)
                } else if (targetRectInWin.contains(wx, wy)) {
                    val isInjected = rawTool != MotionEvent.TOOL_TYPE_FINGER
                    onTargetEvent(
                        wx - targetRectInWin.left,
                        wy - targetRectInWin.top,
                        tool,
                        isInjected,
                        true,
                        null
                    )
                }
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                val pid = event.getPointerId(idx)
                val wx = arenaWinX + event.getX(idx)
                val wy = arenaWinY + event.getY(idx)
                val sx = arenaScreenX + event.getX(idx)
                val sy = arenaScreenY + event.getY(idx)
                val rawTool = event.getToolType(idx)
                val tool = toolName(rawTool)

                if (targetRectInWin.contains(wx, wy) || (activeJoystickPointerId != null && !joystickRectInWin.contains(wx, wy))) {
                    val localX = (wx - targetRectInWin.left).coerceIn(0f, targetRectInWin.width().coerceAtLeast(1f))
                    val localY = (wy - targetRectInWin.top).coerceIn(0f, targetRectInWin.height().coerceAtLeast(1f))
                    onTargetEvent(
                        localX,
                        localY,
                        "$tool(P$pid)",
                        true,
                        true,
                        null
                    )
                } else if (activeJoystickPointerId == null && joystickRectInWin.contains(wx, wy)) {
                    activeJoystickPointerId = pid
                    val puck = clampJoystickOffset(wx, wy, joystickRectInWin)
                    onJoystickState(
                        true,
                        puck,
                        false,
                        false,
                        event.deviceId,
                        "Joystick DOWN ($tool P$pid dev=${event.deviceId})"
                    )
                    onCooperativeFingerUpdate(true, sx, sy, event.downTime, event.deviceId, pid)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val pid = event.getPointerId(i)
                    val wx = arenaWinX + event.getX(i)
                    val wy = arenaWinY + event.getY(i)
                    val sx = arenaScreenX + event.getX(i)
                    val sy = arenaScreenY + event.getY(i)
                    val rawTool = event.getToolType(i)
                    val tool = toolName(rawTool)

                    if (pid == activeJoystickPointerId) {
                        val puck = clampJoystickOffset(wx, wy, joystickRectInWin)
                        onJoystickState(
                            true,
                            puck,
                            true,
                            false,
                            event.deviceId,
                            "Dragging dev=${event.deviceId} (${puck.x.toInt()}, ${puck.y.toInt()})"
                        )
                        onCooperativeFingerUpdate(true, sx, sy, event.downTime, event.deviceId, pid)
                    } else if (targetRectInWin.contains(wx, wy)) {
                        val isInjected = rawTool != MotionEvent.TOOL_TYPE_FINGER || event.pointerCount > 1
                        onTargetEvent(
                            wx - targetRectInWin.left,
                            wy - targetRectInWin.top,
                            if (event.pointerCount > 1) "$tool(P$pid)" else tool,
                            isInjected,
                            false,
                            null
                        )
                    }
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val pid = event.getPointerId(idx)
                val wx = arenaWinX + event.getX(idx)
                val wy = arenaWinY + event.getY(idx)
                val tool = toolName(event.getToolType(idx))
                if (pid == activeJoystickPointerId) {
                    activeJoystickPointerId = null
                    onJoystickState(false, Offset.Zero, false, false, event.deviceId, "Joystick released (P$pid)")
                    onCooperativeFingerUpdate(false, 0f, 0f, 0L, event.deviceId, pid)
                } else if (targetRectInWin.contains(wx, wy) || activeJoystickPointerId != null) {
                    val localX = (wx - targetRectInWin.left).coerceIn(0f, targetRectInWin.width().coerceAtLeast(1f))
                    val localY = (wy - targetRectInWin.top).coerceIn(0f, targetRectInWin.height().coerceAtLeast(1f))
                    onTargetEvent(localX, localY, "$tool(P$pid)", true, false, "Pointer $pid lifted ($tool)")
                }
            }

            MotionEvent.ACTION_UP -> {
                val pid = event.getPointerId(0)
                val wx = arenaWinX + event.getX(0)
                val wy = arenaWinY + event.getY(0)
                val tool = toolName(event.getToolType(0))
                if (pid == activeJoystickPointerId) {
                    activeJoystickPointerId = null
                    onJoystickState(false, Offset.Zero, false, false, event.deviceId, "Joystick released cleanly")
                    onCooperativeFingerUpdate(false, 0f, 0f, 0L, event.deviceId, pid)
                } else if (targetRectInWin.contains(wx, wy)) {
                    onTargetEvent(
                        wx - targetRectInWin.left,
                        wy - targetRectInWin.top,
                        tool,
                        true,
                        false,
                        "$tool UP (${(wx - targetRectInWin.left).toInt()}, ${(wy - targetRectInWin.top).toInt()})"
                    )
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                val wasDragging = activeJoystickPointerId != null
                activeJoystickPointerId = null
                if (wasDragging) {
                    onJoystickState(
                        false,
                        Offset.Zero,
                        false,
                        true,
                        event.deviceId,
                        "ACTION_CANCEL ($selectedModeLabel, dev=${event.deviceId})"
                    )
                    onCooperativeFingerUpdate(false, 0f, 0f, 0L, event.deviceId, 0)
                }
            }
        }
        return true
    }

    fun handleGenericMotion(
        event: MotionEvent,
        arenaWinX: Float,
        arenaWinY: Float,
        targetRectInWin: RectF
    ): Boolean {
        val action = event.actionMasked
        val idx = event.actionIndex
        val wx = arenaWinX + event.getX(idx)
        val wy = arenaWinY + event.getY(idx)
        if (!targetRectInWin.contains(wx, wy)) return false

        val localX = wx - targetRectInWin.left
        val localY = wy - targetRectInWin.top
        val tool = toolName(event.getToolType(idx))
        return when (action) {
            MotionEvent.ACTION_BUTTON_PRESS -> {
                if (event.eventTime != lastHoverButtonPressTime) {
                    lastHoverButtonPressTime = event.eventTime
                    onTargetEvent(localX, localY, "${tool}_BTN", true, true, null)
                }
                true
            }
            MotionEvent.ACTION_HOVER_MOVE -> {
                onTargetEvent(localX, localY, "${tool}_HVR", true, false, null)
                true
            }
            MotionEvent.ACTION_BUTTON_RELEASE -> {
                onTargetEvent(localX, localY, "${tool}_BTN", true, false, "${tool}_BTN UP")
                true
            }
            else -> false
        }
    }

    fun handleComposeHoverOrButton(
        type: PointerEventType,
        pointerType: PointerType,
        winPos: Offset,
        uptimeMs: Long,
        targetRectInWin: RectF
    ) {
        if (pointerType != PointerType.Mouse) return
        if (!targetRectInWin.contains(winPos.x, winPos.y)) return
        val localX = winPos.x - targetRectInWin.left
        val localY = winPos.y - targetRectInWin.top
        when (type) {
            PointerEventType.Press -> {
                if (uptimeMs != lastHoverButtonPressTime) {
                    lastHoverButtonPressTime = uptimeMs
                    onTargetEvent(localX, localY, "MOUSE_BTN", true, true, null)
                }
            }
            PointerEventType.Move -> {
                onTargetEvent(localX, localY, "MOUSE_HVR", true, false, null)
            }
            PointerEventType.Release -> {
                onTargetEvent(localX, localY, "MOUSE_BTN", true, false, "MOUSE_BTN UP")
            }
            else -> Unit
        }
    }

    private fun clampJoystickOffset(wx: Float, wy: Float, rect: RectF): Offset {
        val cx = rect.centerX()
        val cy = rect.centerY()
        val maxR = (minOf(rect.width(), rect.height()) / 2f - 34f).coerceAtLeast(30f)
        val dx = wx - cx
        val dy = wy - cy
        val dist = hypot(dx, dy)
        return if (dist > maxR && dist > 0f) {
            Offset(dx / dist * maxR, dy / dist * maxR)
        } else {
            Offset(dx, dy)
        }
    }
}

private class RawSandboxArenaView(
    context: Context,
    var onTouchCallback: (view: View, event: MotionEvent) -> Boolean = { _, _ -> true },
    var onGenericCallback: (view: View, event: MotionEvent) -> Boolean = { _, _ -> false }
) : View(context) {
    init {
        isClickable = true
        isFocusable = false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        return onTouchCallback(this, event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        return onGenericCallback(this, event)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TouchSandboxScreen(
    shizukuStatus: ShizukuStatus,
    hardwareDiagnostics: HardwareInputDiagnostics,
    onRefreshCapabilities: () -> Unit,
    onCooperativeFingerUpdate: (isDown: Boolean, screenX: Float, screenY: Float, downTime: Long, deviceId: Int, pointerId: Int) -> Unit,
    onTestTap: (x: Float, y: Float, mode: InputInjectionMode) -> Unit,
    onTestSwipe: (sx: Float, sy: Float, ex: Float, ey: Float, mode: InputInjectionMode) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedMode by remember { mutableStateOf(InputInjectionMode.MOUSE_HOVER_CLICK) }
    var autoBurstRunning by remember { mutableStateOf(false) }
    var liveGuardEnabled by remember { mutableStateOf(false) }
    var queuedGuardTap by remember { mutableStateOf(false) }
    var queuedGuardSwipe by remember { mutableStateOf(false) }

    // Manual finger joystick telemetry
    var isFingerDragging by remember { mutableStateOf(false) }
    var fingerPuckOffset by remember { mutableStateOf(Offset.Zero) }
    var dragMoveSamples by remember { mutableIntStateOf(0) }
    var collisionCancelCount by remember { mutableIntStateOf(0) }
    var fingerDeviceId by remember { mutableIntStateOf(0) }
    var lastFingerSummary by remember { mutableStateOf("Hold & drag joystick while Auto-Tap runs") }

    // Injected target & swipe pad telemetry
    var targetHits by remember { mutableIntStateOf(0) }
    var lastReceivedTool by remember { mutableStateOf("NONE") }
    var lastTargetSummary by remember { mutableStateOf("Waiting for injected tap or hover click") }

    var joystickWindowRect by remember { mutableStateOf(RectF()) }
    var targetWindowRect by remember { mutableStateOf(RectF()) }
    var arenaWindowOrigin by remember { mutableStateOf(Offset.Zero) }
    var padScreenOrigin by remember { mutableStateOf(Offset.Zero) }
    var padSize by remember { mutableStateOf(IntSize.Zero) }
    var targetScreenCenter by remember { mutableStateOf(Offset.Zero) }
    var targetRadiusPx by remember { mutableIntStateOf(64) }
    val recentDots = remember { mutableStateListOf<SandboxTouchDot>() }
    val hostView = LocalView.current

    fun recordHitOrDot(
        x: Float,
        y: Float,
        toolLabel: String,
        isInjected: Boolean,
        countHit: Boolean,
        summaryOverride: String?
    ) {
        lastReceivedTool = toolLabel
        recentDots.add(SandboxTouchDot(x, y, isInjected))
        if (recentDots.size > 28) recentDots.removeAt(0)
        if (summaryOverride != null) {
            lastTargetSummary = summaryOverride
            return
        }
        if (countHit) {
            val cx = padSize.width / 2f
            val cy = padSize.height / 2f
            val hitTarget = padSize.width > 0 && hypot(x - cx, y - cy) <= targetRadiusPx.toFloat()
            if (hitTarget) {
                targetHits++
                lastTargetSummary = "Hit #$targetHits via $toolLabel"
            } else {
                lastTargetSummary = "$toolLabel (${x.toInt()}, ${y.toInt()})"
            }
        } else {
            lastTargetSummary = "$toolLabel (${x.toInt()}, ${y.toInt()})"
        }
    }

    val arenaRouter = remember {
        SandboxMultiPointerRouter(
            onJoystickState = { dragging, puck, incMoves, incCancel, devId, summary ->
                isFingerDragging = dragging
                fingerPuckOffset = puck
                if (incMoves) dragMoveSamples++
                if (incCancel) collisionCancelCount++
                if (devId > 0) fingerDeviceId = devId
                lastFingerSummary = summary
            },
            onCooperativeFingerUpdate = onCooperativeFingerUpdate,
            onTargetEvent = { lx, ly, tool, isInjected, countHit, overrideSummary ->
                recordHitOrDot(lx, ly, tool, isInjected, countHit, overrideSummary)
            }
        )
    }

    LaunchedEffect(Unit) {
        onRefreshCapabilities()
    }

    val touchGuardYielding = liveGuardEnabled &&
        (isFingerDragging || hardwareDiagnostics.cooperativeFingerDown)

    LaunchedEffect(
        touchGuardYielding,
        queuedGuardTap,
        queuedGuardSwipe,
        selectedMode,
        targetScreenCenter,
        padScreenOrigin,
        padSize
    ) {
        if (!touchGuardYielding && (queuedGuardTap || queuedGuardSwipe)) {
            delay(65L)
            if (queuedGuardTap && targetScreenCenter != Offset.Zero) {
                queuedGuardTap = false
                onTestTap(targetScreenCenter.x, targetScreenCenter.y, selectedMode)
            }
            if (queuedGuardSwipe && padSize.width > 0) {
                queuedGuardSwipe = false
                val sx = padScreenOrigin.x + padSize.width * 0.2f
                val ex = padScreenOrigin.x + padSize.width * 0.8f
                val sy = padScreenOrigin.y + padSize.height * 0.82f
                onTestSwipe(sx, sy, ex, sy, selectedMode)
            }
        }
    }

    LaunchedEffect(autoBurstRunning, selectedMode, targetScreenCenter, liveGuardEnabled) {
        if (autoBurstRunning && targetScreenCenter != Offset.Zero) {
            while (autoBurstRunning) {
                val blockedByGuard = liveGuardEnabled &&
                    (isFingerDragging || hardwareDiagnostics.cooperativeFingerDown)
                if (blockedByGuard) {
                    delay(45L)
                } else {
                    onTestTap(targetScreenCenter.x, targetScreenCenter.y, selectedMode)
                    delay(420L)
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ObsidianBg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ObsidianBg),
                title = {
                    Column {
                        Text("Touch Sandbox", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                        Text("INPUTDISPATCHER & CO-TOUCH VERIFIER", style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
                    }
                },
                actions = {
                    IconButton(
                        onClick = onRefreshCapabilities,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Radar, contentDescription = "Probe hardware input", tint = ElectricCyan)
                    }
                    IconButton(
                        onClick = {
                            arenaRouter.reset()
                            targetHits = 0
                            dragMoveSamples = 0
                            collisionCancelCount = 0
                            lastReceivedTool = "NONE"
                            recentDots.clear()
                            lastFingerSummary = "Reset — hold & drag joystick"
                            lastTargetSummary = "Waiting for injected tap or hover click"
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset sandbox counters", tint = TextSecondary)
                    }
                }
            )
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
                    .widthIn(max = 760.dp)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Telemetry, Hardware Probe & Mode Selector Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = SlateSurface),
                    border = BorderStroke(1.dp, if (collisionCancelCount > 0) RoseAlert else HairlineBorder),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Shizuku: ${if (shizukuStatus.state == ShizukuConnectionState.CONNECTED) "READY" else "OFFLINE"} • TouchDev=${if (hardwareDiagnostics.physicalTouchDeviceId > 0) hardwareDiagnostics.physicalTouchDeviceId else fingerDeviceId}",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (shizukuStatus.state == ShizukuConnectionState.CONNECTED) EmeraldRun else AmberLogic,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = if (collisionCancelCount == 0) "0 Collisions" else "$collisionCancelCount Canceled!",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (collisionCancelCount == 0) EmeraldRun else RoseAlert
                            )
                        }

                        val evdevLabel = when {
                            hardwareDiagnostics.devInputWritable ->
                                "evdev: RW (${hardwareDiagnostics.physicalTouchDeviceName})"
                            hardwareDiagnostics.devInputReadable ->
                                "evdev: RO (${hardwareDiagnostics.physicalTouchDeviceName})"
                            else -> "evdev: SELinux-blocked"
                        }
                        val uhidLabel = if (hardwareDiagnostics.uhidWritable) "uhid: RW" else "uhid: Blocked"
                        val monitorLabel = if (hardwareDiagnostics.touchMonitorRunning) "GuardMon: LIVE" else "GuardMon: OFF"
                        Text(
                            text = "$evdevLabel • $uhidLabel • $monitorLabel",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (hardwareDiagnostics.uhidWritable || hardwareDiagnostics.devInputWritable) {
                                EmeraldRun
                            } else {
                                AmberLogic
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            InputInjectionMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = selectedMode == mode,
                                    onClick = { selectedMode = mode },
                                    label = { Text(mode.displayLabel) },
                                    modifier = Modifier.testTag("sandbox_mode_${mode.name.lowercase()}")
                                )
                            }
                            FilterChip(
                                selected = liveGuardEnabled,
                                onClick = {
                                    liveGuardEnabled = !liveGuardEnabled
                                    if (!liveGuardEnabled) {
                                        queuedGuardTap = false
                                        queuedGuardSwipe = false
                                    }
                                },
                                label = {
                                    Text(
                                        text = when {
                                            touchGuardYielding -> "Guard: YIELDING"
                                            liveGuardEnabled -> "Guard: Live Hold"
                                            else -> "Guard: Off"
                                        }
                                    )
                                },
                                modifier = Modifier.testTag("sandbox_guard_toggle")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = when {
                                    touchGuardYielding -> "Guard: YIELDING (Finger held)"
                                    queuedGuardTap || queuedGuardSwipe -> "Guard: QUEUED until release"
                                    isFingerDragging -> "Finger: DRAGGING ($dragMoveSamples)"
                                    else -> "Finger: IDLE ($dragMoveSamples)"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = when {
                                    touchGuardYielding -> AmberLogic
                                    isFingerDragging -> EmeraldRun
                                    else -> TextSecondary
                                }
                            )
                            Text(
                                text = "Last Tool: $lastReceivedTool",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan
                            )
                        }

                        Text(
                            text = "$lastFingerSummary • $lastTargetSummary",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = hardwareDiagnostics.lastBypassReport,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (hardwareDiagnostics.lastBypassReport.contains("blocked", ignoreCase = true) ||
                                hardwareDiagnostics.lastBypassReport.contains("failed", ignoreCase = true)
                            ) RoseAlert else ElectricCyan.copy(alpha = 0.9f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Dual Stress-Test Arena wrapped by a SINGLE unified multi-touch RawSandboxArenaView
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .onGloballyPositioned { coords ->
                            arenaWindowOrigin = coords.positionInWindow()
                        }
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val pe = awaitPointerEvent()
                                    val first = pe.changes.firstOrNull() ?: continue
                                    arenaRouter.handleComposeHoverOrButton(
                                        type = pe.type,
                                        pointerType = first.type,
                                        winPos = first.position + arenaWindowOrigin,
                                        uptimeMs = first.uptimeMillis,
                                        targetRectInWin = targetWindowRect
                                    )
                                }
                            }
                        }
                ) {
                    val sideBySide = maxWidth >= 540.dp
                    if (sideBySide) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            JoystickDragZone(
                                isFingerDragging = isFingerDragging,
                                puckOffset = fingerPuckOffset,
                                onPositionedInWin = { rect -> joystickWindowRect = rect },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )

                            TargetAndSwipePad(
                                targetHits = targetHits,
                                recentDots = recentDots,
                                hostView = hostView,
                                onPositionedInWin = { rect -> targetWindowRect = rect },
                                onPadPositioned = { origin, size ->
                                    padScreenOrigin = origin
                                    padSize = size
                                },
                                onTargetPositioned = { center, radiusPx ->
                                    targetScreenCenter = center
                                    targetRadiusPx = radiusPx
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            TargetAndSwipePad(
                                targetHits = targetHits,
                                recentDots = recentDots,
                                hostView = hostView,
                                onPositionedInWin = { rect -> targetWindowRect = rect },
                                onPadPositioned = { origin, size ->
                                    padScreenOrigin = origin
                                    padSize = size
                                },
                                onTargetPositioned = { center, radiusPx ->
                                    targetScreenCenter = center
                                    targetRadiusPx = radiusPx
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            )

                            JoystickDragZone(
                                isFingerDragging = isFingerDragging,
                                puckOffset = fingerPuckOffset,
                                onPositionedInWin = { rect -> joystickWindowRect = rect },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(0.95f)
                            )
                        }
                    }

                    // Unified Arena receiver: handles Pointer 0 (Joystick) + Pointer 1+ (Target) together
                    val onArenaTouch: (View, MotionEvent) -> Boolean = { view, event ->
                        val winLoc = IntArray(2)
                        val screenLoc = IntArray(2)
                        view.getLocationInWindow(winLoc)
                        view.getLocationOnScreen(screenLoc)
                        arenaRouter.handleTouchEvent(
                            event = event,
                            arenaWinX = winLoc[0].toFloat(),
                            arenaWinY = winLoc[1].toFloat(),
                            arenaScreenX = screenLoc[0].toFloat(),
                            arenaScreenY = screenLoc[1].toFloat(),
                            joystickRectInWin = joystickWindowRect,
                            targetRectInWin = targetWindowRect,
                            selectedModeLabel = selectedMode.displayLabel
                        )
                    }
                    val onArenaGeneric: (View, MotionEvent) -> Boolean = { view, event ->
                        val winLoc = IntArray(2)
                        view.getLocationInWindow(winLoc)
                        arenaRouter.handleGenericMotion(
                            event = event,
                            arenaWinX = winLoc[0].toFloat(),
                            arenaWinY = winLoc[1].toFloat(),
                            targetRectInWin = targetWindowRect
                        )
                    }

                    AndroidView(
                        factory = { ctx ->
                            RawSandboxArenaView(
                                ctx,
                                onTouchCallback = onArenaTouch,
                                onGenericCallback = onArenaGeneric
                            )
                        },
                        update = { view ->
                            view.onTouchCallback = onArenaTouch
                            view.onGenericCallback = onArenaGeneric
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("sandbox_unified_arena")
                    )
                }

                // Trigger Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { autoBurstRunning = !autoBurstRunning },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (autoBurstRunning) RoseAlert else EmeraldRun,
                            contentColor = Color(0xFF04131A)
                        ),
                        modifier = Modifier
                            .weight(1.1f)
                            .height(48.dp)
                            .testTag("sandbox_auto_burst_btn")
                    ) {
                        Icon(
                            imageVector = if (autoBurstRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (autoBurstRunning) "Stop Burst" else "Auto-Tap",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }

                    Button(
                        onClick = {
                            if (touchGuardYielding) {
                                queuedGuardTap = true
                            } else {
                                val cx = if (targetScreenCenter != Offset.Zero) {
                                    targetScreenCenter.x
                                } else {
                                    padScreenOrigin.x + padSize.width / 2f
                                }
                                val cy = if (targetScreenCenter != Offset.Zero) {
                                    targetScreenCenter.y
                                } else {
                                    padScreenOrigin.y + padSize.height / 2f
                                }
                                onTestTap(cx, cy, selectedMode)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = Color(0xFF04131A)),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("sandbox_test_tap_btn")
                    ) {
                        Icon(Icons.Default.TouchApp, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (queuedGuardTap) "Queued" else "Tap", fontWeight = FontWeight.Bold, maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = {
                            if (touchGuardYielding) {
                                queuedGuardSwipe = true
                            } else {
                                val sx = padScreenOrigin.x + padSize.width * 0.2f
                                val ex = padScreenOrigin.x + padSize.width * 0.8f
                                val sy = padScreenOrigin.y + padSize.height * 0.82f
                                onTestSwipe(sx, sy, ex, sy, selectedMode)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("sandbox_test_swipe_btn")
                    ) {
                        Icon(Icons.Default.Gesture, contentDescription = null, modifier = Modifier.size(18.dp), tint = AmberLogic)
                        Spacer(Modifier.width(4.dp))
                        Text(if (queuedGuardSwipe) "Queued" else "Swipe", color = TextPrimary, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun JoystickDragZone(
    isFingerDragging: Boolean,
    puckOffset: Offset,
    onPositionedInWin: (RectF) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(SlateWell)
            .border(
                width = if (isFingerDragging) 2.dp else 1.dp,
                color = if (isFingerDragging) EmeraldRun else HairlineBorder,
                shape = RoundedCornerShape(18.dp)
            )
            .onGloballyPositioned { coords ->
                val pos = coords.positionInWindow()
                onPositionedInWin(
                    RectF(
                        pos.x,
                        pos.y,
                        pos.x + coords.size.width.toFloat(),
                        pos.y + coords.size.height.toFloat()
                    )
                )
            }
            .testTag("sandbox_joystick_pad"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = (minOf(size.width, size.height) * 0.36f).coerceAtLeast(36f)
            drawCircle(
                color = if (isFingerDragging) EmeraldRun.copy(alpha = 0.14f) else ElectricCyan.copy(alpha = 0.08f),
                radius = radius,
                center = center
            )
            drawCircle(
                color = if (isFingerDragging) EmeraldRun else HairlineBorder,
                radius = radius,
                center = center,
                style = Stroke(width = 3f)
            )
            drawLine(HairlineBorder, Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), 1.5f)
            drawLine(HairlineBorder, Offset(center.x, center.y - radius), Offset(center.x, center.y + radius), 1.5f)

            val puckPos = center + puckOffset
            if (isFingerDragging) {
                drawLine(EmeraldRun.copy(alpha = 0.6f), center, puckPos, strokeWidth = 4f)
            }
            drawCircle(
                color = if (isFingerDragging) EmeraldRun else ElectricCyan,
                radius = 28f,
                center = puckPos
            )
        }

        Text(
            text = if (isFingerDragging) "HOLDING & DRAGGING" else "HOLD & DRAG JOYSTICK",
            style = MaterialTheme.typography.labelSmall,
            color = if (isFingerDragging) EmeraldRun else TextSecondary,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp)
        )
    }
}

@Composable
private fun TargetAndSwipePad(
    targetHits: Int,
    recentDots: List<SandboxTouchDot>,
    hostView: View,
    onPositionedInWin: (RectF) -> Unit,
    onPadPositioned: (origin: Offset, size: IntSize) -> Unit,
    onTargetPositioned: (center: Offset, radiusPx: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(SlateWell)
            .border(1.dp, HairlineBorder, RoundedCornerShape(18.dp))
            .onGloballyPositioned { coords ->
                val winLoc = IntArray(2)
                hostView.getLocationOnScreen(winLoc)
                val posInWin = coords.positionInWindow()
                onPositionedInWin(
                    RectF(
                        posInWin.x,
                        posInWin.y,
                        posInWin.x + coords.size.width.toFloat(),
                        posInWin.y + coords.size.height.toFloat()
                    )
                )
                onPadPositioned(
                    Offset(winLoc[0] + posInWin.x, winLoc[1] + posInWin.y),
                    coords.size
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = 72f
            var gx = 0f
            while (gx < size.width) {
                drawLine(Color(0x1422D3EE), Offset(gx, 0f), Offset(gx, size.height), 1f)
                gx += step
            }
            var gy = 0f
            while (gy < size.height) {
                drawLine(Color(0x1422D3EE), Offset(0f, gy), Offset(size.width, gy), 1f)
                gy += step
            }

            recentDots.forEachIndexed { idx, dot ->
                val alpha = ((idx + 1).toFloat() / recentDots.size.coerceAtLeast(1)).coerceIn(0.2f, 1f)
                drawCircle(
                    color = (if (dot.isInjectedTool) AmberLogic else ElectricCyan).copy(alpha = alpha),
                    radius = 14f,
                    center = Offset(dot.x, dot.y),
                    style = Stroke(width = 2.5f)
                )
            }
        }

        Text(
            text = "AUTO-TAP & HOVER TARGET",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp)
        )

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(128.dp)
                .clip(CircleShape)
                .background(if (targetHits % 2 == 0) EmeraldRun.copy(alpha = 0.22f) else AmberLogic.copy(alpha = 0.22f))
                .border(
                    2.dp,
                    if (targetHits % 2 == 0) EmeraldRun else AmberLogic,
                    CircleShape
                )
                .onGloballyPositioned { coords ->
                    val winLoc = IntArray(2)
                    hostView.getLocationOnScreen(winLoc)
                    val posInWin = coords.positionInWindow()
                    onTargetPositioned(
                        Offset(
                            x = winLoc[0] + posInWin.x + coords.size.width / 2f,
                            y = winLoc[1] + posInWin.y + coords.size.height / 2f
                        ),
                        coords.size.width / 2
                    )
                }
                .testTag("sandbox_target_button"),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$targetHits",
                    style = MaterialTheme.typography.headlineLarge,
                    color = TextPrimary
                )
                Text(
                    text = "HITS",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (targetHits % 2 == 0) EmeraldRun else AmberLogic
                )
            }
        }
    }
}

private fun toolName(toolType: Int): String = when (toolType) {
    MotionEvent.TOOL_TYPE_MOUSE -> "MOUSE"
    MotionEvent.TOOL_TYPE_STYLUS -> "STYLUS"
    MotionEvent.TOOL_TYPE_ERASER -> "ERASER"
    MotionEvent.TOOL_TYPE_FINGER -> "FINGER"
    else -> "UNKNOWN"
}
