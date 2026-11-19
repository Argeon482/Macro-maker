package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.TemplateAssetEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

private val CropObsidian = Color(0xFF0B0E14)
private val CropSurface = Color(0xFF131822)
private val CropWell = Color(0xFF0F141D)
private val CropBorder = Color(0xFF263042)
private val CropCyan = Color(0xFF22D3EE)
private val CropAmber = Color(0xFFF59E0B)
private val CropEmerald = Color(0xFF10B981)
private val CropText = Color(0xFFF1F5F9)
private val CropMuted = Color(0xFF94A3B8)

enum class CropEdgeSide {
    LEFT, TOP, RIGHT, BOTTOM
}

data class TemplateCropRect(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
    val trimLeft: Int,
    val trimTop: Int,
    val trimRight: Int,
    val trimBottom: Int
)

fun computeTemplateCropRect(
    sourceWidth: Int,
    sourceHeight: Int,
    trimLeft: Int,
    trimTop: Int,
    trimRight: Int,
    trimBottom: Int,
    minSize: Int = 4
): TemplateCropRect {
    val safeSrcW = sourceWidth.coerceAtLeast(1)
    val safeSrcH = sourceHeight.coerceAtLeast(1)
    val minW = minOf(minSize.coerceAtLeast(1), safeSrcW)
    val minH = minOf(minSize.coerceAtLeast(1), safeSrcH)

    val safeLeft = trimLeft.coerceIn(0, (safeSrcW - minW).coerceAtLeast(0))
    val safeRight = trimRight.coerceIn(0, (safeSrcW - safeLeft - minW).coerceAtLeast(0))
    val safeTop = trimTop.coerceIn(0, (safeSrcH - minH).coerceAtLeast(0))
    val safeBottom = trimBottom.coerceIn(0, (safeSrcH - safeTop - minH).coerceAtLeast(0))

    val width = (safeSrcW - safeLeft - safeRight).coerceAtLeast(1)
    val height = (safeSrcH - safeTop - safeBottom).coerceAtLeast(1)
    return TemplateCropRect(
        left = safeLeft,
        top = safeTop,
        width = width,
        height = height,
        trimLeft = safeLeft,
        trimTop = safeTop,
        trimRight = safeRight,
        trimBottom = safeBottom
    )
}

@Composable
fun TemplateThumbnailPreview(
    template: TemplateAssetEntity?,
    onEditCrop: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (template == null) return

    val bitmap by produceState<Bitmap?>(
        initialValue = null,
        key1 = template.id,
        key2 = "${template.width}x${template.height}_${template.createdAt}"
    ) {
        value = withContext(Dispatchers.IO) {
            val file = File(template.filePath)
            if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
        }
    }

    Surface(
        color = CropWell,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, CropBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(CropObsidian)
                    .border(1.dp, CropCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                val bmp = bitmap
                if (bmp != null && !bmp.isRecycled) {
                    val img = remember(bmp) { bmp.asImageBitmap() }
                    Canvas(modifier = Modifier.fillMaxSize().padding(3.dp)) {
                        val scale = min(size.width / img.width.toFloat(), size.height / img.height.toFloat())
                        val drawW = (img.width * scale).roundToInt().coerceAtLeast(1)
                        val drawH = (img.height * scale).roundToInt().coerceAtLeast(1)
                        val ox = ((size.width - drawW) / 2f).roundToInt()
                        val oy = ((size.height - drawH) / 2f).roundToInt()
                        drawImage(
                            image = img,
                            dstOffset = IntOffset(ox, oy),
                            dstSize = IntSize(drawW, drawH),
                            filterQuality = FilterQuality.None
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.Crop,
                        contentDescription = null,
                        tint = CropMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = template.name,
                    color = CropText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${template.width} × ${template.height} px",
                    color = CropCyan,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            OutlinedButton(
                onClick = onEditCrop,
                border = BorderStroke(1.dp, CropAmber.copy(alpha = 0.7f)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier
                    .height(42.dp)
                    .testTag("edit_template_crop_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Crop,
                    contentDescription = "Edit template crop",
                    tint = CropAmber,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = "Trim 1px",
                    color = CropAmber,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun TemplateCropEditorDialog(
    template: TemplateAssetEntity,
    onApplyCrop: (trimLeft: Int, trimTop: Int, trimRight: Int, trimBottom: Int, saveAsNew: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = CropObsidian,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, CropBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 24.dp)
        ) {
            TemplateCropEditorContent(
                template = template,
                onApplyCrop = onApplyCrop,
                onCancel = onDismiss,
                compactMode = false
            )
        }
    }
}

@Composable
fun TemplateCropEditorContent(
    template: TemplateAssetEntity,
    onApplyCrop: (trimLeft: Int, trimTop: Int, trimRight: Int, trimBottom: Int, saveAsNew: Boolean) -> Unit,
    onCancel: () -> Unit,
    compactMode: Boolean = false
) {
    val bitmap by produceState<Bitmap?>(
        initialValue = null,
        key1 = template.id,
        key2 = "${template.width}x${template.height}_${template.createdAt}"
    ) {
        value = withContext(Dispatchers.IO) {
            val file = File(template.filePath)
            if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
        }
    }

    val srcW = (bitmap?.width ?: template.width).coerceAtLeast(1)
    val srcH = (bitmap?.height ?: template.height).coerceAtLeast(1)
    val minCropSize = minOf(4, srcW, srcH).coerceAtLeast(1)

    var trimLeft by remember(template.id, template.createdAt) { mutableIntStateOf(0) }
    var trimTop by remember(template.id, template.createdAt) { mutableIntStateOf(0) }
    var trimRight by remember(template.id, template.createdAt) { mutableIntStateOf(0) }
    var trimBottom by remember(template.id, template.createdAt) { mutableIntStateOf(0) }
    var stepPx by remember { mutableIntStateOf(1) }
    var zoomLevel by remember { mutableIntStateOf(0) } // 0 = Fit, 2, 4, 8
    var showGrid by remember { mutableStateOf(true) }
    var previewCroppedOnly by remember { mutableStateOf(false) }
    var activeSide by remember { mutableStateOf<CropEdgeSide?>(null) }
    var panX by remember(template.id, zoomLevel, previewCroppedOnly) { mutableFloatStateOf(0f) }
    var panY by remember(template.id, zoomLevel, previewCroppedOnly) { mutableFloatStateOf(0f) }

    val cropRect = computeTemplateCropRect(
        sourceWidth = srcW,
        sourceHeight = srcH,
        trimLeft = trimLeft,
        trimTop = trimTop,
        trimRight = trimRight,
        trimBottom = trimBottom,
        minSize = minCropSize
    )
    val hasChanges = cropRect.trimLeft > 0 || cropRect.trimTop > 0 || cropRect.trimRight > 0 || cropRect.trimBottom > 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(if (compactMode) 12.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header (shown in dialog mode; Overlay Studio has its own header)
        if (!compactMode) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Precision Template Crop",
                        style = MaterialTheme.typography.titleMedium,
                        color = CropText,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = template.name,
                        color = CropCyan,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onCancel, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close crop editor", tint = CropMuted)
                }
            }
        }

        // Dimensions summary strip
        Surface(
            color = CropSurface,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, CropBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Original ${srcW}×${srcH}  →  Cropped ${cropRect.width}×${cropRect.height} px",
                        color = if (hasChanges) CropEmerald else CropText,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("crop_dimensions_text")
                    )
                    Text(
                        text = "Trim  L:${cropRect.trimLeft}  T:${cropRect.trimTop}  R:${cropRect.trimRight}  B:${cropRect.trimBottom}",
                        color = CropMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                if (hasChanges) {
                    OutlinedButton(
                        onClick = {
                            trimLeft = 0
                            trimTop = 0
                            trimRight = 0
                            trimBottom = 0
                            activeSide = null
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("crop_reset_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset crop", tint = CropMuted, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Reset", color = CropMuted, fontSize = 11.sp)
                    }
                }
            }
        }

        // Zoomed pixel preview canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compactMode) 200.dp else 248.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(CropWell)
                .border(1.dp, CropBorder, RoundedCornerShape(14.dp))
                .clipToBounds()
                .pointerInput(zoomLevel, previewCroppedOnly) {
                    if (zoomLevel > 0) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            panX += dragAmount.x
                            panY += dragAmount.y
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            val bmp = bitmap
            if (bmp == null || bmp.isRecycled) {
                Text("Loading template image...", color = CropMuted, fontSize = 12.sp)
            } else {
                val imageBitmap = remember(bmp) { bmp.asImageBitmap() }
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Subtle checkerboard backdrop so dark image borders stand out clearly
                    val tilePx = 18f
                    val cols = (size.width / tilePx).toInt() + 1
                    val rows = (size.height / tilePx).toInt() + 1
                    val c1 = Color(0xFF101520)
                    val c2 = Color(0xFF171E2C)
                    for (r in 0..rows) {
                        for (c in 0..cols) {
                            drawRect(
                                color = if ((r + c) % 2 == 0) c1 else c2,
                                topLeft = Offset(c * tilePx, r * tilePx),
                                size = Size(tilePx, tilePx)
                            )
                        }
                    }

                    val pad = 16f
                    val availW = (size.width - pad * 2f).coerceAtLeast(16f)
                    val availH = (size.height - pad * 2f).coerceAtLeast(16f)

                    if (previewCroppedOnly) {
                        val fitScale = min(availW / cropRect.width.toFloat(), availH / cropRect.height.toFloat())
                        val scale = if (zoomLevel == 0) fitScale else fitScale * zoomLevel.toFloat()
                        val drawW = cropRect.width * scale
                        val drawH = cropRect.height * scale
                        val maxPanX = ((drawW - availW) / 2f).coerceAtLeast(0f)
                        val maxPanY = ((drawH - availH) / 2f).coerceAtLeast(0f)
                        val ox = (size.width - drawW) / 2f + panX.coerceIn(-maxPanX, maxPanX)
                        val oy = (size.height - drawH) / 2f + panY.coerceIn(-maxPanY, maxPanY)

                        drawImage(
                            image = imageBitmap,
                            srcOffset = IntOffset(cropRect.left, cropRect.top),
                            srcSize = IntSize(cropRect.width, cropRect.height),
                            dstOffset = IntOffset(ox.roundToInt(), oy.roundToInt()),
                            dstSize = IntSize(drawW.roundToInt().coerceAtLeast(1), drawH.roundToInt().coerceAtLeast(1)),
                            filterQuality = FilterQuality.None
                        )

                        if (showGrid && scale >= 6f) {
                            val gridColor = Color(0x33FFFFFF)
                            for (px in 1 until cropRect.width) {
                                val gx = ox + px * scale
                                if (gx in 0f..size.width) {
                                    drawLine(gridColor, Offset(gx, oy), Offset(gx, oy + drawH), strokeWidth = 1f)
                                }
                            }
                            for (py in 1 until cropRect.height) {
                                val gy = oy + py * scale
                                if (gy in 0f..size.height) {
                                    drawLine(gridColor, Offset(ox, gy), Offset(ox + drawW, gy), strokeWidth = 1f)
                                }
                            }
                        }

                        drawRect(
                            color = CropEmerald,
                            topLeft = Offset(ox, oy),
                            size = Size(drawW, drawH),
                            style = Stroke(width = 2.5f)
                        )
                    } else {
                        val fitScale = min(availW / imageBitmap.width.toFloat(), availH / imageBitmap.height.toFloat())
                        val scale = if (zoomLevel == 0) fitScale else fitScale * zoomLevel.toFloat()
                        val drawW = imageBitmap.width * scale
                        val drawH = imageBitmap.height * scale
                        val maxPanX = ((drawW - availW) / 2f).coerceAtLeast(0f)
                        val maxPanY = ((drawH - availH) / 2f).coerceAtLeast(0f)
                        val ox = (size.width - drawW) / 2f + panX.coerceIn(-maxPanX, maxPanX)
                        val oy = (size.height - drawH) / 2f + panY.coerceIn(-maxPanY, maxPanY)

                        drawImage(
                            image = imageBitmap,
                            dstOffset = IntOffset(ox.roundToInt(), oy.roundToInt()),
                            dstSize = IntSize(drawW.roundToInt().coerceAtLeast(1), drawH.roundToInt().coerceAtLeast(1)),
                            filterQuality = FilterQuality.None
                        )

                        if (showGrid && scale >= 6f) {
                            val gridColor = Color(0x2EFFFFFF)
                            val startCol = floor(((0f - ox) / scale).toDouble()).toInt().coerceAtLeast(1)
                            val endCol = ((size.width - ox) / scale).toInt().coerceAtMost(imageBitmap.width - 1)
                            for (px in startCol..endCol) {
                                val gx = ox + px * scale
                                drawLine(gridColor, Offset(gx, oy), Offset(gx, oy + drawH), strokeWidth = 1f)
                            }
                            val startRow = floor(((0f - oy) / scale).toDouble()).toInt().coerceAtLeast(1)
                            val endRow = ((size.height - oy) / scale).toInt().coerceAtMost(imageBitmap.height - 1)
                            for (py in startRow..endRow) {
                                val gy = oy + py * scale
                                drawLine(gridColor, Offset(ox, gy), Offset(ox + drawW, gy), strokeWidth = 1f)
                            }
                        }

                        val keepL = ox + cropRect.left * scale
                        val keepT = oy + cropRect.top * scale
                        val keepR = ox + (cropRect.left + cropRect.width) * scale
                        val keepB = oy + (cropRect.top + cropRect.height) * scale

                        // Dim trimmed margins outside the kept region
                        val maskColor = Color(0xBB05070B)
                        if (keepT > oy) {
                            drawRect(maskColor, Offset(ox, oy), Size(drawW, keepT - oy))
                        }
                        if (keepB < oy + drawH) {
                            drawRect(maskColor, Offset(ox, keepB), Size(drawW, oy + drawH - keepB))
                        }
                        if (keepL > ox) {
                            drawRect(maskColor, Offset(ox, keepT), Size(keepL - ox, keepB - keepT))
                        }
                        if (keepR < ox + drawW) {
                            drawRect(maskColor, Offset(keepR, keepT), Size(ox + drawW - keepR, keepB - keepT))
                        }

                        // Active crop rectangle border
                        drawRect(
                            color = CropCyan,
                            topLeft = Offset(keepL, keepT),
                            size = Size(keepR - keepL, keepB - keepT),
                            style = Stroke(width = 2.5f)
                        )

                        // Highlight whichever edge was most recently nudged in Amber
                        when (activeSide) {
                            CropEdgeSide.LEFT -> drawLine(CropAmber, Offset(keepL, keepT), Offset(keepL, keepB), strokeWidth = 5f)
                            CropEdgeSide.RIGHT -> drawLine(CropAmber, Offset(keepR, keepT), Offset(keepR, keepB), strokeWidth = 5f)
                            CropEdgeSide.TOP -> drawLine(CropAmber, Offset(keepL, keepT), Offset(keepR, keepT), strokeWidth = 5f)
                            CropEdgeSide.BOTTOM -> drawLine(CropAmber, Offset(keepL, keepB), Offset(keepR, keepB), strokeWidth = 5f)
                            null -> Unit
                        }
                    }
                }
            }
        }

        // Step increment + Zoom + Preview controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Step:", color = CropMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            listOf(1, 5, 10).forEach { inc ->
                FilterChip(
                    selected = stepPx == inc,
                    onClick = { stepPx = inc },
                    label = { Text("${inc}px", fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
                    modifier = Modifier.testTag("crop_step_${inc}px")
                )
            }
            Spacer(Modifier.width(4.dp))
            Text("Zoom:", color = CropMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            listOf(0 to "Fit", 2 to "2x", 4 to "4x", 8 to "8x").forEach { (z, lbl) ->
                FilterChip(
                    selected = zoomLevel == z,
                    onClick = { zoomLevel = z },
                    label = { Text(lbl, fontSize = 11.sp) }
                )
            }
            FilterChip(
                selected = showGrid,
                onClick = { showGrid = !showGrid },
                leadingIcon = { Icon(Icons.Default.GridOn, contentDescription = null, modifier = Modifier.size(14.dp)) },
                label = { Text("Grid", fontSize = 11.sp) }
            )
            FilterChip(
                selected = previewCroppedOnly,
                onClick = { previewCroppedOnly = !previewCroppedOnly },
                leadingIcon = { Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(14.dp)) },
                label = { Text("Cropped", fontSize = 11.sp) }
            )
        }

        // 4-Side 1px Trim Controls (2x2 Grid)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CropSideControlCard(
                    sideLabel = "LEFT",
                    trimmedPx = cropRect.trimLeft,
                    stepPx = stepPx,
                    canDecrease = cropRect.trimLeft > 0,
                    canIncrease = cropRect.width > minCropSize,
                    isActive = activeSide == CropEdgeSide.LEFT,
                    testTagPrefix = "crop_left",
                    onDecrease = {
                        activeSide = CropEdgeSide.LEFT
                        trimLeft = (cropRect.trimLeft - stepPx).coerceAtLeast(0)
                    },
                    onIncrease = {
                        activeSide = CropEdgeSide.LEFT
                        val maxTrim = (srcW - cropRect.trimRight - minCropSize).coerceAtLeast(0)
                        trimLeft = (cropRect.trimLeft + stepPx).coerceAtMost(maxTrim)
                    },
                    modifier = Modifier.weight(1f)
                )
                CropSideControlCard(
                    sideLabel = "RIGHT",
                    trimmedPx = cropRect.trimRight,
                    stepPx = stepPx,
                    canDecrease = cropRect.trimRight > 0,
                    canIncrease = cropRect.width > minCropSize,
                    isActive = activeSide == CropEdgeSide.RIGHT,
                    testTagPrefix = "crop_right",
                    onDecrease = {
                        activeSide = CropEdgeSide.RIGHT
                        trimRight = (cropRect.trimRight - stepPx).coerceAtLeast(0)
                    },
                    onIncrease = {
                        activeSide = CropEdgeSide.RIGHT
                        val maxTrim = (srcW - cropRect.trimLeft - minCropSize).coerceAtLeast(0)
                        trimRight = (cropRect.trimRight + stepPx).coerceAtMost(maxTrim)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CropSideControlCard(
                    sideLabel = "TOP",
                    trimmedPx = cropRect.trimTop,
                    stepPx = stepPx,
                    canDecrease = cropRect.trimTop > 0,
                    canIncrease = cropRect.height > minCropSize,
                    isActive = activeSide == CropEdgeSide.TOP,
                    testTagPrefix = "crop_top",
                    onDecrease = {
                        activeSide = CropEdgeSide.TOP
                        trimTop = (cropRect.trimTop - stepPx).coerceAtLeast(0)
                    },
                    onIncrease = {
                        activeSide = CropEdgeSide.TOP
                        val maxTrim = (srcH - cropRect.trimBottom - minCropSize).coerceAtLeast(0)
                        trimTop = (cropRect.trimTop + stepPx).coerceAtMost(maxTrim)
                    },
                    modifier = Modifier.weight(1f)
                )
                CropSideControlCard(
                    sideLabel = "BOTTOM",
                    trimmedPx = cropRect.trimBottom,
                    stepPx = stepPx,
                    canDecrease = cropRect.trimBottom > 0,
                    canIncrease = cropRect.height > minCropSize,
                    isActive = activeSide == CropEdgeSide.BOTTOM,
                    testTagPrefix = "crop_bottom",
                    onDecrease = {
                        activeSide = CropEdgeSide.BOTTOM
                        trimBottom = (cropRect.trimBottom - stepPx).coerceAtLeast(0)
                    },
                    onIncrease = {
                        activeSide = CropEdgeSide.BOTTOM
                        val maxTrim = (srcH - cropRect.trimTop - minCropSize).coerceAtLeast(0)
                        trimBottom = (cropRect.trimBottom + stepPx).coerceAtMost(maxTrim)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Action Buttons: Save Copy / Apply Crop
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    onApplyCrop(
                        cropRect.trimLeft,
                        cropRect.trimTop,
                        cropRect.trimRight,
                        cropRect.trimBottom,
                        true
                    )
                },
                enabled = hasChanges,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("crop_save_copy_button")
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = if (hasChanges) CropCyan else CropMuted, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text("Save Copy", color = if (hasChanges) CropCyan else CropMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Button(
                onClick = {
                    onApplyCrop(
                        cropRect.trimLeft,
                        cropRect.trimTop,
                        cropRect.trimRight,
                        cropRect.trimBottom,
                        false
                    )
                },
                enabled = hasChanges,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CropCyan,
                    contentColor = Color(0xFF04131A),
                    disabledContainerColor = CropBorder,
                    disabledContentColor = CropMuted
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("crop_apply_button")
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text("Apply Crop", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CropSideControlCard(
    sideLabel: String,
    trimmedPx: Int,
    stepPx: Int,
    canDecrease: Boolean,
    canIncrease: Boolean,
    isActive: Boolean,
    testTagPrefix: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = CropSurface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (isActive) CropAmber else CropBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = sideLabel,
                    color = if (isActive) CropAmber else CropMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "-${trimmedPx}px",
                    color = if (trimmedPx > 0) CropCyan else CropText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onDecrease,
                    enabled = canDecrease,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("${testTagPrefix}_dec")
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Restore $stepPx px on $sideLabel edge",
                        tint = if (canDecrease) CropText else CropMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Button(
                    onClick = onIncrease,
                    enabled = canIncrease,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CropCyan.copy(alpha = 0.2f),
                        contentColor = CropCyan,
                        disabledContainerColor = CropWell,
                        disabledContentColor = CropMuted
                    ),
                    border = BorderStroke(1.dp, if (canIncrease) CropCyan.copy(alpha = 0.6f) else CropBorder),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("${testTagPrefix}_inc")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Trim $stepPx px from $sideLabel edge",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
