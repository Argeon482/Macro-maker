package com.example.service

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.ExecutionStatus
import com.example.engine.RuntimeExecutionState
import com.example.shizuku.HardwareInputDiagnostics
import com.example.shizuku.ShizukuConnectionState
import com.example.shizuku.ShizukuStatus
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
    targetSubtitle: String? = null,
    onSwitchApp: (updatedDraftRequest: PickerRequest) -> Unit,
    onConfirmTap: (x: Float, y: Float) -> Unit,
    onConfirmSwipe: (sx: Float, sy: Float, ex: Float, ey: Float) -> Unit,
    onConfirmColor: (x: Int, y: Int) -> Unit,
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

        val safeTapMaxX = maxOf(41f, maxW - 40f)
        val safeTapMaxY = maxOf(81f, maxH - 120f)
        val safeDragMaxX = maxOf(20f, maxW - 16f)
        val safeDragMaxY = maxOf(20f, maxH - 16f)

        var tapX by remember(request) { mutableFloatStateOf(request.initialX.coerceIn(40f, safeTapMaxX)) }
        var tapY by remember(request) { mutableFloatStateOf(request.initialY.coerceIn(80f, safeTapMaxY)) }

        var endX by remember(request) { mutableFloatStateOf(request.initialEndX.coerceIn(40f, safeTapMaxX)) }
        var endY by remember(request) { mutableFloatStateOf(request.initialEndY.coerceIn(80f, safeTapMaxY)) }

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

        Canvas(modifier = Modifier.fillMaxSize()) {
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
                    drawCircle(accent, radius = 5f, center = Offset(tapX, tapY))
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

        when (request.mode) {
            PickerMode.PICK_TAP, PickerMode.PICK_COLOR -> {
                DraggableHandle(
                    x = tapX,
                    y = tapY,
                    badge = if (request.mode == PickerMode.PICK_COLOR) "COLOR" else "TAP",
                    accent = if (request.mode == PickerMode.PICK_COLOR) StudioEmerald else StudioCyan,
                    onDrag = { dx, dy ->
                        tapX = (tapX + dx).coerceIn(16f, safeDragMaxX)
                        tapY = (tapY + dy).coerceIn(16f, safeDragMaxY)
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

        // Top HUD readout
        Surface(
            color = StudioPanel,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 42.dp, start = 16.dp, end = 16.dp)
                .border(1.dp, StudioBorder, RoundedCornerShape(14.dp))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = when (request.mode) {
                        PickerMode.PICK_COLOR -> Icons.Default.Colorize
                        PickerMode.PICK_TEMPLATE_CROP, PickerMode.PICK_SEARCH_ROI -> Icons.Default.CropFree
                        else -> Icons.Default.MyLocation
                    },
                    contentDescription = null,
                    tint = if (request.mode == PickerMode.PICK_SEARCH_ROI) StudioEmerald else StudioCyan
                )
                Column {
                    Text(
                        text = when (request.mode) {
                            PickerMode.PICK_TAP -> "Drag Reticle to Tap Coordinate"
                            PickerMode.PICK_SWIPE -> "Drag Start & End Swipe Handles"
                            PickerMode.PICK_COLOR -> "Drag Eyedropper to Pixel Target"
                            PickerMode.PICK_TEMPLATE_CROP -> "Crop Target Image Box"
                            PickerMode.PICK_SEARCH_ROI -> "Set Image Search Area (ROI)"
                            PickerMode.NONE -> ""
                        },
                        color = StudioText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
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
                        PickerMode.PICK_TAP, PickerMode.PICK_COLOR -> "X: ${tapX.roundToInt()}  Y: ${tapY.roundToInt()}"
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
                            "$coords  •  Enable Vision Capture for live sampling"
                        } else {
                            coords
                        },
                        color = if (isBoxMode && request.mode == PickerMode.PICK_SEARCH_ROI) StudioEmerald else StudioCyan,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Bottom confirm/cancel bar
        Surface(
            color = StudioPanel,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 36.dp, start = 16.dp, end = 16.dp)
                .border(1.dp, StudioBorder, RoundedCornerShape(16.dp))
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                    Text("Switch App", color = StudioAmber, fontSize = 12.sp)
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
                            PickerMode.PICK_COLOR -> onConfirmColor(tapX.roundToInt(), tapY.roundToInt())
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
