package com.example.service

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenshotMonitor
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.ExecutionStatus
import com.example.engine.RuntimeExecutionState
import com.example.shizuku.HardwareInputDiagnostics
import com.example.shizuku.ShizukuConnectionState
import com.example.shizuku.ShizukuStatus
import kotlin.math.floor
import kotlin.math.roundToInt

private val StudioObsidian = Color(0xEE0B0E14)
private val StudioPanel = Color(0xF2131822)
private val StudioBorder = Color(0xFF263042)
private val StudioCyan = Color(0xFF22D3EE)
private val StudioAmber = Color(0xFFF59E0B)
private val StudioEmerald = Color(0xFF10B981)
private val StudioRose = Color(0xFFF43F5E)
private val StudioText = Color(0xFFF1F5F9)
private val StudioMuted = Color(0xFF94A3B8)

@Composable
fun FloatingControlDock(
    overlayState: OverlayUiState,
    executionState: RuntimeExecutionState,
    shizukuStatus: ShizukuStatus,
    hardwareDiagnostics: HardwareInputDiagnostics = HardwareInputDiagnostics(),
    screenCaptureActive: Boolean,
    onDragDelta: (dx: Int, dy: Int) -> Unit,
    onPlayOrPause: () -> Unit,
    onStop: () -> Unit,
    onQuickPick: () -> Unit,
    onTogglePins: () -> Unit,
    onOpenOverlayStudio: () -> Unit,
    onOpenApp: () -> Unit,
    onCloseOverlay: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val statusColor = when (executionState.status) {
        ExecutionStatus.RUNNING -> StudioEmerald
        ExecutionStatus.PAUSED -> StudioAmber
        ExecutionStatus.ERROR -> StudioRose
        ExecutionStatus.IDLE -> if (shizukuStatus.state == ShizukuConnectionState.CONNECTED) StudioCyan else StudioMuted
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = StudioObsidian,
        tonalElevation = 8.dp,
        shadowElevation = 12.dp,
        modifier = Modifier
            .border(1.dp, StudioBorder, RoundedCornerShape(18.dp))
            .widthIn(min = 160.dp, max = 300.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                onDragDelta(dragAmount.x.roundToInt(), dragAmount.y.roundToInt())
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DragIndicator,
                        contentDescription = "Drag floating dock",
                        tint = StudioMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )

                Spacer(Modifier.width(4.dp))

                IconButton(
                    onClick = onPlayOrPause,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (executionState.status == ExecutionStatus.RUNNING) {
                            Icons.Default.Pause
                        } else {
                            Icons.Default.PlayArrow
                        },
                        contentDescription = if (executionState.status == ExecutionStatus.RUNNING) "Pause macro" else "Run macro",
                        tint = if (executionState.status == ExecutionStatus.RUNNING) StudioAmber else StudioEmerald
                    )
                }

                if (executionState.status == ExecutionStatus.RUNNING || executionState.status == ExecutionStatus.PAUSED) {
                    IconButton(
                        onClick = onStop,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop macro",
                            tint = StudioRose
                        )
                    }
                }

                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse dock" else "Expand dock",
                        tint = StudioText
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = overlayState.activeMacroName,
                        color = StudioText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = executionState.lastTelemetry,
                        color = if (executionState.status == ExecutionStatus.ERROR) StudioRose else StudioCyan,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Shizuku: ${shizukuStatus.state.name.lowercase().replace('_', ' ')}",
                            color = if (shizukuStatus.state == ShizukuConnectionState.CONNECTED) StudioEmerald else StudioAmber,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = if (screenCaptureActive) "Vision ON" else "Vision OFF",
                            color = if (screenCaptureActive) StudioCyan else StudioMuted,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Text(
                        text = if (hardwareDiagnostics.touchMonitorRunning) {
                            "Touch Guard: Live evdev (Slot 0)"
                        } else if (hardwareDiagnostics.devInputReadable) {
                            "Touch Guard: evdev standby"
                        } else {
                            "Touch Guard: Outside-touch watcher"
                        },
                        color = if (hardwareDiagnostics.touchMonitorRunning) StudioEmerald else StudioMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                expanded = false
                                onQuickPick()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(15.dp), tint = StudioCyan)
                            Spacer(Modifier.width(4.dp))
                            Text("Pick", fontSize = 11.sp, color = StudioText)
                        }

                        OutlinedButton(
                            onClick = {
                                expanded = false
                                onTogglePins()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(15.dp), tint = StudioAmber)
                            Spacer(Modifier.width(4.dp))
                            Text("Pins", fontSize = 11.sp, color = StudioText)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                expanded = false
                                onOpenOverlayStudio()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StudioCyan.copy(alpha = 0.2f),
                                contentColor = StudioCyan
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Studio", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        IconButton(
                            onClick = onOpenApp,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open Full App", tint = StudioText)
                        }

                        IconButton(
                            onClick = onCloseOverlay,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close overlay", tint = StudioRose)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PassThroughOverlayChip(
    onDragDelta: (dx: Int, dy: Int) -> Unit,
    onResumePicker: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        color = StudioObsidian,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 10.dp,
        modifier = Modifier
            .border(1.dp, StudioCyan, RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onDragDelta(dragAmount.x.roundToInt(), dragAmount.y.roundToInt())
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DragIndicator,
                    contentDescription = "Drag switcher pill",
                    tint = StudioMuted,
                    modifier = Modifier.size(18.dp)
                )
            }

            Button(
                onClick = onResumePicker,
                colors = ButtonDefaults.buttonColors(containerColor = StudioCyan, contentColor = Color(0xFF061018)),
                modifier = Modifier.height(44.dp)
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Place Target", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            IconButton(onClick = onCancel, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Cancel pick", tint = StudioRose)
            }
        }
    }
}

@Composable
fun TargetPickerOverlay(
    request: PickerRequest,
    screenCaptureActive: Boolean,
    snapshotBitmap: Bitmap? = null,
    targetSubtitle: String? = null,
    onRefreshSnapshot: () -> Unit = {},
    onRequestVision: () -> Unit = {},
    onSwitchApp: (updatedDraftRequest: PickerRequest) -> Unit,
    onConfirmTap: (x: Float, y: Float) -> Unit,
    onConfirmSwipe: (sx: Float, sy: Float, ex: Float, ey: Float) -> Unit,
    onConfirmColor: (x: Int, y: Int, sampledHex: String?) -> Unit,
    onConfirmCrop: (left: Int, top: Int, right: Int, bottom: Int) -> Unit,
    onConfirmRoi: (left: Int, top: Int, right: Int, bottom: Int) -> Unit,
    onCancel: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x5506080D))
    ) {
        val density = LocalDensity.current
        val maxW = with(density) { maxWidth.toPx() }
        val maxH = with(density) { maxHeight.toPx() }
        if (maxW < 180f || maxH < 260f) return@BoxWithConstraints

        val safeTapMaxX = maxOf(41f, maxW - 1f)
        val safeTapMaxY = maxOf(81f, maxH - 1f)
        val safeDragMaxX = maxOf(20f, maxW - 1f)
        val safeDragMaxY = maxOf(20f, maxH - 1f)

        var tapX by remember(request) { mutableFloatStateOf(request.initialX.coerceIn(0f, safeTapMaxX)) }
        var tapY by remember(request) { mutableFloatStateOf(request.initialY.coerceIn(0f, safeTapMaxY)) }

        var endX by remember(request) { mutableFloatStateOf(request.initialEndX.coerceIn(0f, safeTapMaxX)) }
        var endY by remember(request) { mutableFloatStateOf(request.initialEndY.coerceIn(0f, safeTapMaxY)) }

        var showZoomInspector by remember(request) { mutableStateOf(false) }
        var zoomFactor by remember { mutableIntStateOf(10) }

        val validSnapshot = snapshotBitmap?.takeIf { !it.isRecycled && it.width > 1 && it.height > 1 }
        val snapshotImage = remember(validSnapshot) { validSnapshot?.asImageBitmap() }

        // Map overlay coordinates cleanly to screenshot bitmap coordinates
        val bmpScaleX = if (validSnapshot != null && maxW > 0f) validSnapshot.width / maxW else 1f
        val bmpScaleY = if (validSnapshot != null && maxH > 0f) validSnapshot.height / maxH else 1f
        val samplePixelX = if (validSnapshot != null) {
            (tapX * bmpScaleX).roundToInt().coerceIn(0, validSnapshot.width - 1)
        } else {
            tapX.roundToInt().coerceAtLeast(0)
        }
        val samplePixelY = if (validSnapshot != null) {
            (tapY * bmpScaleY).roundToInt().coerceIn(0, validSnapshot.height - 1)
        } else {
            tapY.roundToInt().coerceAtLeast(0)
        }
        val liveSampledPixel: Int? = remember(validSnapshot, samplePixelX, samplePixelY) {
            validSnapshot?.let { bmp ->
                runCatching { bmp.getPixel(samplePixelX, samplePixelY) }.getOrNull()
            }
        }
        val liveSampledHex: String? = remember(liveSampledPixel) {
            liveSampledPixel?.let { String.format("#%06X", 0xFFFFFF and it) }
        }

        val hasPresetRect = request.initialRight > request.initialLeft + 30 && request.initialBottom > request.initialTop + 30
        val initLeft = if (hasPresetRect) request.initialLeft.toFloat() else (request.initialX - 140f)
        val initTop = if (hasPresetRect) request.initialTop.toFloat() else (request.initialY - 110f)
        val initRight = if (hasPresetRect) request.initialRight.toFloat() else (initLeft + 280f)
        val initBottom = if (hasPresetRect) request.initialBottom.toFloat() else (initTop + 220f)

        val safeRectLeftMax = maxOf(40f, maxW - 160f)
        val safeRectTopMax = maxOf(100f, maxH - 220f)
        var cropLeft by remember(request) { mutableFloatStateOf(initLeft.coerceIn(24f, safeRectLeftMax)) }
        var cropTop by remember(request) { mutableFloatStateOf(initTop.coerceIn(90f, safeRectTopMax)) }
        var cropRight by remember(request) {
            mutableFloatStateOf(initRight.coerceIn(cropLeft + 60f, maxOf(cropLeft + 61f, maxW - 24f)))
        }
        var cropBottom by remember(request) {
            mutableFloatStateOf(initBottom.coerceIn(cropTop + 60f, maxOf(cropTop + 61f, maxH - 130f)))
        }

        val isBoxMode = request.mode == PickerMode.PICK_TEMPLATE_CROP || request.mode == PickerMode.PICK_SEARCH_ROI
        val boxAccent = if (request.mode == PickerMode.PICK_SEARCH_ROI) StudioEmerald else StudioCyan

        val tapAnywhereModifier = if (!showZoomInspector && (request.mode == PickerMode.PICK_TAP || request.mode == PickerMode.PICK_COLOR)) {
            Modifier.pointerInput(request.mode, safeDragMaxX, safeDragMaxY) {
                detectTapGestures { offset ->
                    tapX = offset.x.coerceIn(0f, safeDragMaxX)
                    tapY = offset.y.coerceIn(0f, safeDragMaxY)
                }
            }
        } else {
            Modifier
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .then(tapAnywhereModifier)
        ) {
            val step = 72f
            var gx = 0f
            while (gx < size.width) {
                drawLine(Color(0x1822D3EE), Offset(gx, 0f), Offset(gx, size.height), strokeWidth = 1f)
                gx += step
            }
            var gy = 0f
            while (gy < size.height) {
                drawLine(Color(0x1822D3EE), Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1f)
                gy += step
            }

            when (request.mode) {
                PickerMode.PICK_TAP, PickerMode.PICK_COLOR -> {
                    val accent = if (request.mode == PickerMode.PICK_COLOR) StudioEmerald else StudioCyan
                    drawLine(accent.copy(alpha = 0.45f), Offset(0f, tapY), Offset(size.width, tapY), strokeWidth = 1.5f)
                    drawLine(accent.copy(alpha = 0.45f), Offset(tapX, 0f), Offset(tapX, size.height), strokeWidth = 1.5f)
                    drawCircle(accent.copy(alpha = 0.22f), radius = 44f, center = Offset(tapX, tapY))
                    drawCircle(accent, radius = 28f, center = Offset(tapX, tapY), style = Stroke(width = 3f))
                    drawCircle(accent, radius = 4f, center = Offset(tapX, tapY))
                }

                PickerMode.PICK_SWIPE -> {
                    drawLine(
                        color = StudioAmber,
                        start = Offset(tapX, tapY),
                        end = Offset(endX, endY),
                        strokeWidth = 5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f), 0f)
                    )
                    drawCircle(StudioCyan, radius = 26f, center = Offset(tapX, tapY), style = Stroke(width = 3f))
                    drawCircle(StudioAmber, radius = 26f, center = Offset(endX, endY), style = Stroke(width = 3f))
                }

                PickerMode.PICK_TEMPLATE_CROP, PickerMode.PICK_SEARCH_ROI -> {
                    val left = minOf(cropLeft, cropRight)
                    val top = minOf(cropTop, cropBottom)
                    val w = kotlin.math.abs(cropRight - cropLeft)
                    val h = kotlin.math.abs(cropBottom - cropTop)
                    drawRect(
                        color = boxAccent.copy(alpha = 0.14f),
                        topLeft = Offset(left, top),
                        size = Size(w, h)
                    )
                    drawRect(
                        color = boxAccent,
                        topLeft = Offset(left, top),
                        size = Size(w, h),
                        style = Stroke(width = 3f)
                    )
                }

                PickerMode.NONE -> Unit
            }
        }

        if (!showZoomInspector) {
            when (request.mode) {
                PickerMode.PICK_TAP, PickerMode.PICK_COLOR -> {
                    DraggableHandle(
                        x = tapX,
                        y = tapY,
                        badge = if (request.mode == PickerMode.PICK_COLOR) "COLOR" else "TAP",
                        accent = if (request.mode == PickerMode.PICK_COLOR) StudioEmerald else StudioCyan,
                        onDrag = { dx, dy ->
                            tapX = (tapX + dx).coerceIn(0f, safeDragMaxX)
                            tapY = (tapY + dy).coerceIn(0f, safeDragMaxY)
                        }
                    )
                }

                PickerMode.PICK_SWIPE -> {
                    DraggableHandle(
                        x = tapX,
                        y = tapY,
                        badge = "START",
                        accent = StudioCyan,
                        onDrag = { dx, dy ->
                            tapX = (tapX + dx).coerceIn(16f, safeDragMaxX)
                            tapY = (tapY + dy).coerceIn(16f, safeDragMaxY)
                        }
                    )
                    DraggableHandle(
                        x = endX,
                        y = endY,
                        badge = "END",
                        accent = StudioAmber,
                        onDrag = { dx, dy ->
                            endX = (endX + dx).coerceIn(16f, safeDragMaxX)
                            endY = (endY + dy).coerceIn(16f, safeDragMaxY)
                        }
                    )
                }

                PickerMode.PICK_TEMPLATE_CROP, PickerMode.PICK_SEARCH_ROI -> {
                    DraggableHandle(
                        x = cropLeft,
                        y = cropTop,
                        badge = "TL",
                        accent = boxAccent,
                        onDrag = { dx, dy ->
                            cropLeft = (cropLeft + dx).coerceIn(16f, maxOf(17f, cropRight - 48f))
                            cropTop = (cropTop + dy).coerceIn(60f, maxOf(61f, cropBottom - 48f))
                        }
                    )
                    DraggableHandle(
                        x = cropRight,
                        y = cropBottom,
                        badge = "BR",
                        accent = StudioAmber,
                        onDrag = { dx, dy ->
                            cropRight = (cropRight + dx).coerceIn(cropLeft + 48f, maxOf(cropLeft + 49f, maxW - 16f))
                            cropBottom = (cropBottom + dy).coerceIn(cropTop + 48f, maxOf(cropTop + 49f, maxH - 80f))
                        }
                    )
                }

                PickerMode.NONE -> Unit
            }

            // Live floating magnifier loupe during PICK_COLOR when screenshot is available
            if (request.mode == PickerMode.PICK_COLOR && snapshotImage != null && validSnapshot != null) {
                val loupeAtBottom = tapY < maxH * 0.45f
                val loupeAlignment = if (loupeAtBottom) Alignment.BottomEnd else Alignment.TopEnd
                val loupePadding = if (loupeAtBottom) {
                    Modifier.padding(bottom = 112.dp, end = 16.dp)
                } else {
                    Modifier.padding(top = 128.dp, end = 16.dp)
                }
                Surface(
                    color = StudioObsidian,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(2.dp, StudioEmerald),
                    shadowElevation = 10.dp,
                    modifier = Modifier
                        .align(loupeAlignment)
                        .then(loupePadding)
                        .clickable { showZoomInspector = true }
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(116.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, StudioBorder, RoundedCornerShape(10.dp))
                        ) {
                            ZoomedPixelCanvas(
                                imageBitmap = snapshotImage,
                                centerPixelX = samplePixelX,
                                centerPixelY = samplePixelY,
                                zoomCellsHalf = 7,
                                showGridLines = true,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            liveSampledPixel?.let { px ->
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(Color(px))
                                        .border(1.dp, Color.White, CircleShape)
                                )
                            }
                            Text(
                                text = liveSampledHex ?: "TAP TO ZOOM",
                                color = StudioText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Text(
                            text = "Tap to Zoom In",
                            color = StudioEmerald,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Top HUD readout
        Surface(
            color = StudioPanel,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 38.dp, start = 14.dp, end = 14.dp)
                .border(1.dp, StudioBorder, RoundedCornerShape(14.dp))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (request.mode == PickerMode.PICK_COLOR && liveSampledPixel != null) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(liveSampledPixel))
                            .border(1.5.dp, Color.White, RoundedCornerShape(8.dp))
                    )
                } else {
                    Icon(
                        imageVector = when (request.mode) {
                            PickerMode.PICK_COLOR -> Icons.Default.Colorize
                            PickerMode.PICK_TEMPLATE_CROP, PickerMode.PICK_SEARCH_ROI -> Icons.Default.CropFree
                            else -> Icons.Default.MyLocation
                        },
                        contentDescription = null,
                        tint = if (request.mode == PickerMode.PICK_SEARCH_ROI || request.mode == PickerMode.PICK_COLOR) StudioEmerald else StudioCyan
                    )
                }
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = when {
                            showZoomInspector && request.mode == PickerMode.PICK_COLOR -> "Zoomed Pixel Picker (Drag to Pan / Tap Pixel)"
                            request.mode == PickerMode.PICK_TAP -> "Tap or Drag Reticle to Coordinate"
                            request.mode == PickerMode.PICK_SWIPE -> "Drag Start & End Swipe Handles"
                            request.mode == PickerMode.PICK_COLOR -> "Tap or Drag Target • Tap Zoom In for Precision"
                            request.mode == PickerMode.PICK_TEMPLATE_CROP -> "Crop Target Image Box"
                            request.mode == PickerMode.PICK_SEARCH_ROI -> "Set Image Search Area (ROI)"
                            else -> ""
                        },
                        color = StudioText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!targetSubtitle.isNullOrBlank()) {
                        Text(
                            text = targetSubtitle,
                            color = StudioAmber,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    val coords = when (request.mode) {
                        PickerMode.PICK_TAP -> "X: ${tapX.roundToInt()}  Y: ${tapY.roundToInt()}"
                        PickerMode.PICK_COLOR -> {
                            val hexPart = liveSampledHex?.let { "  $it" } ?: ""
                            "X: $samplePixelX  Y: $samplePixelY$hexPart"
                        }
                        PickerMode.PICK_SWIPE -> "(${tapX.roundToInt()},${tapY.roundToInt()}) -> (${endX.roundToInt()},${endY.roundToInt()})"
                        PickerMode.PICK_TEMPLATE_CROP, PickerMode.PICK_SEARCH_ROI -> {
                            val l = minOf(cropLeft, cropRight).roundToInt()
                            val t = minOf(cropTop, cropBottom).roundToInt()
                            val r = maxOf(cropLeft, cropRight).roundToInt()
                            val b = maxOf(cropTop, cropBottom).roundToInt()
                            "($l,$t)–($r,$b)  ${r - l}x${b - t}px"
                        }
                        PickerMode.NONE -> ""
                    }
                    Text(
                        text = if ((request.mode == PickerMode.PICK_COLOR || request.mode == PickerMode.PICK_TEMPLATE_CROP) && !screenCaptureActive) {
                            "$coords  •  Vision Capture OFF"
                        } else {
                            coords
                        },
                        color = if (request.mode == PickerMode.PICK_COLOR || request.mode == PickerMode.PICK_SEARCH_ROI) StudioEmerald else StudioCyan,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                if (request.mode == PickerMode.PICK_COLOR) {
                    if (!screenCaptureActive) {
                        OutlinedButton(
                            onClick = onRequestVision,
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Default.ScreenshotMonitor, contentDescription = null, tint = StudioAmber, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Enable", color = StudioAmber, fontSize = 11.sp)
                        }
                    } else {
                        IconButton(
                            onClick = onRefreshSnapshot,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh screenshot", tint = StudioCyan, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }

        // Expanded Zoomed-In Precision Color Picker
        if (showZoomInspector && request.mode == PickerMode.PICK_COLOR) {
            val halfCells = when (zoomFactor) {
                4 -> 22
                8 -> 12
                16 -> 6
                else -> 9
            }
            Surface(
                color = StudioObsidian,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.5.dp, StudioEmerald),
                shadowElevation = 16.dp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 16.dp)
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Zoom level selector + live RGB telemetry
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(4, 10, 16).forEach { z ->
                                FilterChip(
                                    selected = zoomFactor == z,
                                    onClick = { zoomFactor = z },
                                    label = { Text("${z}x", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                )
                            }
                        }
                        if (liveSampledPixel != null && liveSampledHex != null) {
                            val r = AndroidColor.red(liveSampledPixel)
                            val g = AndroidColor.green(liveSampledPixel)
                            val b = AndroidColor.blue(liveSampledPixel)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(liveSampledPixel))
                                        .border(1.5.dp, Color.White, RoundedCornerShape(8.dp))
                                )
                                Column {
                                    Text(
                                        text = liveSampledHex,
                                        color = StudioText,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = "RGB($r,$g,$b)",
                                        color = StudioMuted,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }

                    // Spacious magnified pixel grid viewport
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF06080D))
                            .border(1.dp, StudioBorder, RoundedCornerShape(14.dp))
                    ) {
                        if (snapshotImage != null && validSnapshot != null) {
                            var dragAccumX by remember { mutableFloatStateOf(0f) }
                            var dragAccumY by remember { mutableFloatStateOf(0f) }
                            val totalCells = halfCells * 2 + 1

                            ZoomedPixelCanvas(
                                imageBitmap = snapshotImage,
                                centerPixelX = samplePixelX,
                                centerPixelY = samplePixelY,
                                zoomCellsHalf = halfCells,
                                showGridLines = zoomFactor >= 8,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(validSnapshot, halfCells, bmpScaleX, bmpScaleY) {
                                        detectTapGestures { offset ->
                                            val cellW = size.width.toFloat() / totalCells
                                            val cellH = size.height.toFloat() / totalCells
                                            val tappedCellX = floor(offset.x / cellW).toInt()
                                            val tappedCellY = floor(offset.y / cellH).toInt()
                                            val deltaPxX = tappedCellX - halfCells
                                            val deltaPxY = tappedCellY - halfCells
                                            val nextPxX = (samplePixelX + deltaPxX).coerceIn(0, validSnapshot.width - 1)
                                            val nextPxY = (samplePixelY + deltaPxY).coerceIn(0, validSnapshot.height - 1)
                                            tapX = (nextPxX / bmpScaleX).coerceIn(0f, safeDragMaxX)
                                            tapY = (nextPxY / bmpScaleY).coerceIn(0f, safeDragMaxY)
                                        }
                                    }
                                    .pointerInput(validSnapshot, halfCells, bmpScaleX, bmpScaleY) {
                                        detectDragGestures(
                                            onDragStart = {
                                                dragAccumX = 0f
                                                dragAccumY = 0f
                                            }
                                        ) { change, dragAmount ->
                                            change.consume()
                                            val cellW = (size.width.toFloat() / totalCells).coerceAtLeast(4f)
                                            val cellH = (size.height.toFloat() / totalCells).coerceAtLeast(4f)
                                            dragAccumX += dragAmount.x
                                            dragAccumY += dragAmount.y
                                            // Natural pan: dragging finger right moves image right -> crosshair moves left
                                            val stepX = (dragAccumX / cellW).toInt()
                                            val stepY = (dragAccumY / cellH).toInt()
                                            if (stepX != 0 || stepY != 0) {
                                                dragAccumX -= stepX * cellW
                                                dragAccumY -= stepY * cellH
                                                val nextPxX = (samplePixelX - stepX).coerceIn(0, validSnapshot.width - 1)
                                                val nextPxY = (samplePixelY - stepY).coerceIn(0, validSnapshot.height - 1)
                                                tapX = (nextPxX / bmpScaleX).coerceIn(0f, safeDragMaxX)
                                                tapY = (nextPxY / bmpScaleY).coerceIn(0f, safeDragMaxY)
                                            }
                                        }
                                    }
                            )
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (screenCaptureActive) {
                                        "No frame captured yet. Tap Reshoot to grab the screen."
                                    } else {
                                        "Enable Vision Capture to zoom into live pixels."
                                    },
                                    color = StudioMuted,
                                    fontSize = 12.sp
                                )
                                Spacer(Modifier.height(10.dp))
                                Button(
                                    onClick = if (screenCaptureActive) onRefreshSnapshot else onRequestVision,
                                    colors = ButtonDefaults.buttonColors(containerColor = StudioCyan, contentColor = Color(0xFF061018))
                                ) {
                                    Text(if (screenCaptureActive) "Reshoot Frame" else "Enable Vision Capture", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // 1px precision D-pad + quick Reshoot / Full Screen buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val nudgePixel: (dx: Int, dy: Int) -> Unit = { dx, dy ->
                            val maxPxW = validSnapshot?.width ?: maxW.roundToInt()
                            val maxPxH = validSnapshot?.height ?: maxH.roundToInt()
                            val nx = (samplePixelX + dx).coerceIn(0, (maxPxW - 1).coerceAtLeast(0))
                            val ny = (samplePixelY + dy).coerceIn(0, (maxPxH - 1).coerceAtLeast(0))
                            tapX = (nx / bmpScaleX).coerceIn(0f, safeDragMaxX)
                            tapY = (ny / bmpScaleY).coerceIn(0f, safeDragMaxY)
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PixelNudgeButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Nudge left 1px") { nudgePixel(-1, 0) }
                            PixelNudgeButton(Icons.Default.KeyboardArrowUp, "Nudge up 1px") { nudgePixel(0, -1) }
                            PixelNudgeButton(Icons.Default.KeyboardArrowDown, "Nudge down 1px") { nudgePixel(0, 1) }
                            PixelNudgeButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Nudge right 1px") { nudgePixel(1, 0) }
                        }

                        OutlinedButton(
                            onClick = { showZoomInspector = false },
                            modifier = Modifier.height(44.dp)
                        ) {
                            Icon(Icons.Default.Fullscreen, contentDescription = null, tint = StudioCyan, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Aim Full", color = StudioCyan, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Bottom confirm/cancel bar (+ Zoom In button for PICK_COLOR, 1px nudges for PICK_TAP)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp, start = 14.dp, end = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (request.mode == PickerMode.PICK_TAP) {
                Surface(
                    color = StudioPanel,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, StudioBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("1px:", color = StudioMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        PixelNudgeButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Left 1px") { tapX = (tapX - 1f).coerceAtLeast(0f) }
                        PixelNudgeButton(Icons.Default.KeyboardArrowUp, "Up 1px") { tapY = (tapY - 1f).coerceAtLeast(0f) }
                        PixelNudgeButton(Icons.Default.KeyboardArrowDown, "Down 1px") { tapY = (tapY + 1f).coerceAtMost(safeDragMaxY) }
                        PixelNudgeButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Right 1px") { tapX = (tapX + 1f).coerceAtMost(safeDragMaxX) }
                    }
                }
            }

            Surface(
                color = StudioPanel,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.border(1.dp, StudioBorder, RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("Cancel", color = StudioMuted, fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            onSwitchApp(
                                request.copy(
                                    initialX = tapX,
                                    initialY = tapY,
                                    initialEndX = endX,
                                    initialEndY = endY,
                                    initialLeft = minOf(cropLeft, cropRight).roundToInt(),
                                    initialTop = minOf(cropTop, cropBottom).roundToInt(),
                                    initialRight = maxOf(cropLeft, cropRight).roundToInt(),
                                    initialBottom = maxOf(cropTop, cropBottom).roundToInt()
                                )
                            )
                        },
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("Switch", color = StudioAmber, fontSize = 12.sp)
                    }
                    if (request.mode == PickerMode.PICK_COLOR && !showZoomInspector) {
                        Button(
                            onClick = {
                                if (validSnapshot == null && screenCaptureActive) {
                                    onRefreshSnapshot()
                                }
                                showZoomInspector = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = StudioEmerald, contentColor = Color(0xFF04140D)),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(Icons.Default.ZoomIn, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Zoom", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                    Button(
                        onClick = {
                            val l = minOf(cropLeft, cropRight).roundToInt()
                            val t = minOf(cropTop, cropBottom).roundToInt()
                            val r = maxOf(cropLeft, cropRight).roundToInt()
                            val b = maxOf(cropTop, cropBottom).roundToInt()
                            when (request.mode) {
                                PickerMode.PICK_TAP -> onConfirmTap(tapX, tapY)
                                PickerMode.PICK_SWIPE -> onConfirmSwipe(tapX, tapY, endX, endY)
                                PickerMode.PICK_COLOR -> onConfirmColor(samplePixelX, samplePixelY, liveSampledHex)
                                PickerMode.PICK_TEMPLATE_CROP -> onConfirmCrop(l, t, r, b)
                                PickerMode.PICK_SEARCH_ROI -> onConfirmRoi(l, t, r, b)
                                PickerMode.NONE -> Unit
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = StudioCyan, contentColor = Color(0xFF061018)),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("Confirm", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomedPixelCanvas(
    imageBitmap: androidx.compose.ui.graphics.ImageBitmap,
    centerPixelX: Int,
    centerPixelY: Int,
    zoomCellsHalf: Int,
    showGridLines: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val totalCells = zoomCellsHalf * 2 + 1
        val cellW = size.width / totalCells
        val cellH = size.height / totalCells

        val srcLeft = centerPixelX - zoomCellsHalf
        val srcTop = centerPixelY - zoomCellsHalf

        // Clamp source rect to image bounds and offset destination accordingly
        val clampedLeft = srcLeft.coerceIn(0, imageBitmap.width - 1)
        val clampedTop = srcTop.coerceIn(0, imageBitmap.height - 1)
        val clampedRight = (srcLeft + totalCells).coerceIn(clampedLeft + 1, imageBitmap.width)
        val clampedBottom = (srcTop + totalCells).coerceIn(clampedTop + 1, imageBitmap.height)

        val srcW = (clampedRight - clampedLeft).coerceAtLeast(1)
        val srcH = (clampedBottom - clampedTop).coerceAtLeast(1)

        val dstLeft = ((clampedLeft - srcLeft) * cellW).roundToInt()
        val dstTop = ((clampedTop - srcTop) * cellH).roundToInt()
        val dstW = (srcW * cellW).roundToInt().coerceAtLeast(1)
        val dstH = (srcH * cellH).roundToInt().coerceAtLeast(1)

        drawImage(
            image = imageBitmap,
            srcOffset = IntOffset(clampedLeft, clampedTop),
            srcSize = IntSize(srcW, srcH),
            dstOffset = IntOffset(dstLeft, dstTop),
            dstSize = IntSize(dstW, dstH),
            filterQuality = FilterQuality.None
        )

        if (showGridLines) {
            val gridColor = Color(0x30FFFFFF)
            for (i in 1 until totalCells) {
                val x = i * cellW
                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                val y = i * cellH
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
        }

        // Highlight exact center pixel cell
        val centerLeft = zoomCellsHalf * cellW
        val centerTop = zoomCellsHalf * cellH
        drawRect(
            color = Color.Black,
            topLeft = Offset(centerLeft - 1.5f, centerTop - 1.5f),
            size = Size(cellW + 3f, cellH + 3f),
            style = Stroke(width = 3.5f)
        )
        drawRect(
            color = StudioEmerald,
            topLeft = Offset(centerLeft, centerTop),
            size = Size(cellW, cellH),
            style = Stroke(width = 2.5f)
        )
    }
}

@Composable
private fun PixelNudgeButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = StudioPanel,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, StudioBorder),
        modifier = Modifier.size(44.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, tint = StudioText, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun DraggableHandle(
    x: Float,
    y: Float,
    badge: String,
    accent: Color,
    onDrag: (dx: Float, dy: Float) -> Unit
) {
    val halfTouchPx = with(LocalDensity.current) { 28.dp.toPx() }
    Box(
        modifier = Modifier
            .offset { IntOffset((x - halfTouchPx).roundToInt(), (y - halfTouchPx).roundToInt()) }
            .size(56.dp)
            .pointerInput(badge) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.25f))
                .border(2.dp, accent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
        }
        Text(
            text = badge,
            color = accent,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .background(StudioObsidian, RoundedCornerShape(4.dp))
                .padding(horizontal = 3.dp)
        )
    }
}
