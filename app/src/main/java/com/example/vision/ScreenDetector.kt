package com.example.vision

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

data class ColorMatchResult(
    val matched: Boolean,
    val sampledHex: String,
    val similarityPct: Int
)

data class ImageMatchResult(
    val matched: Boolean,
    val centerX: Int,
    val centerY: Int,
    val confidencePct: Int
)

class ScreenDetector(private val context: Context) {

    private val projectionManager =
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private var screenWidth: Int = 1080
    private var screenHeight: Int = 1920
    private var screenDensity: Int = 320

    private val frameCacheLock = Any()
    private var cachedFrame: Bitmap? = null

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            stop()
        }
    }

    @Suppress("DEPRECATION")
    private fun refreshDisplayMetrics() {
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels.coerceAtLeast(1)
        screenHeight = metrics.heightPixels.coerceAtLeast(1)
        screenDensity = metrics.densityDpi.coerceAtLeast(160)
    }

    @SuppressLint("WrongConstant")
    fun start(resultCode: Int, data: Intent): Boolean {
        stop()
        refreshDisplayMetrics()
        return try {
            val projection = projectionManager.getMediaProjection(resultCode, data) ?: return false
            projection.registerCallback(projectionCallback, Handler(Looper.getMainLooper()))
            val reader = ImageReader.newInstance(
                screenWidth,
                screenHeight,
                PixelFormat.RGBA_8888,
                2
            )
            val display = projection.createVirtualDisplay(
                "ShizuMacroScreenCapture",
                screenWidth,
                screenHeight,
                screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                Handler(Looper.getMainLooper())
            )
            mediaProjection = projection
            imageReader = reader
            virtualDisplay = display
            _isActive.value = true
            true
        } catch (_: Throwable) {
            stop()
            false
        }
    }

    fun stop() {
        _isActive.value = false
        try {
            virtualDisplay?.release()
        } catch (_: Throwable) {
        }
        try {
            imageReader?.close()
        } catch (_: Throwable) {
        }
        try {
            mediaProjection?.unregisterCallback(projectionCallback)
            mediaProjection?.stop()
        } catch (_: Throwable) {
        }
        virtualDisplay = null
        imageReader = null
        mediaProjection = null
        synchronized(frameCacheLock) {
            cachedFrame?.recycle()
            cachedFrame = null
        }
    }

    suspend fun captureLatestBitmap(): Bitmap? = withContext(Dispatchers.Default) {
        val reader = imageReader ?: return@withContext null
        val image = try {
            reader.acquireLatestImage()
        } catch (_: Throwable) {
            null
        }

        if (image == null) {
            return@withContext synchronized(frameCacheLock) {
                val cached = cachedFrame
                if (cached != null && !cached.isRecycled) {
                    cached.copy(Bitmap.Config.ARGB_8888, false)
                } else {
                    null
                }
            }
        }

        try {
            val plane = image.planes.firstOrNull() ?: return@withContext null
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * image.width
            val bitmapWidth = image.width + rowPadding / pixelStride
            val rawBitmap = Bitmap.createBitmap(bitmapWidth, image.height, Bitmap.Config.ARGB_8888)
            rawBitmap.copyPixelsFromBuffer(buffer)
            val resultBitmap = if (bitmapWidth == image.width) {
                rawBitmap
            } else {
                val cropped = Bitmap.createBitmap(rawBitmap, 0, 0, image.width, image.height)
                rawBitmap.recycle()
                cropped
            }
            synchronized(frameCacheLock) {
                cachedFrame?.recycle()
                cachedFrame = resultBitmap.copy(Bitmap.Config.ARGB_8888, false)
            }
            resultBitmap
        } catch (_: Throwable) {
            null
        } finally {
            image.close()
        }
    }

    fun matchColor(
        bitmap: Bitmap,
        x: Int,
        y: Int,
        targetHex: String,
        tolerancePct: Int
    ): ColorMatchResult {
        val safeX = x.coerceIn(0, bitmap.width - 1)
        val safeY = y.coerceIn(0, bitmap.height - 1)
        val pixel = bitmap.getPixel(safeX, safeY)
        val sampledHex = String.format("#%06X", 0xFFFFFF and pixel)

        val targetColor = parseHexColor(targetHex)
        val dr = Color.red(pixel) - Color.red(targetColor)
        val dg = Color.green(pixel) - Color.green(targetColor)
        val db = Color.blue(pixel) - Color.blue(targetColor)
        val distance = sqrt((dr * dr + dg * dg + db * db).toDouble())
        val maxDistance = sqrt(3.0 * 255.0 * 255.0)
        val diffPct = ((distance / maxDistance) * 100.0).toInt().coerceIn(0, 100)
        val similarityPct = 100 - diffPct
        val matched = diffPct <= tolerancePct.coerceIn(0, 100)

        return ColorMatchResult(
            matched = matched,
            sampledHex = sampledHex,
            similarityPct = similarityPct
        )
    }

    suspend fun matchTemplate(
        screen: Bitmap,
        template: Bitmap,
        searchRegion: Rect?,
        minConfidencePct: Int
    ): ImageMatchResult = withContext(Dispatchers.Default) {
        val tplW = template.width
        val tplH = template.height
        if (tplW <= 0 || tplH <= 0 || tplW > screen.width || tplH > screen.height) {
            return@withContext ImageMatchResult(false, 0, 0, 0)
        }

        val roi = if (searchRegion != null && searchRegion.width() >= tplW && searchRegion.height() >= tplH) {
            Rect(
                searchRegion.left.coerceIn(0, screen.width - tplW),
                searchRegion.top.coerceIn(0, screen.height - tplH),
                searchRegion.right.coerceIn(tplW, screen.width),
                searchRegion.bottom.coerceIn(tplH, screen.height)
            )
        } else {
            Rect(0, 0, screen.width, screen.height)
        }

        val screenW = screen.width
        val screenPixels = IntArray(screen.width * screen.height)
        screen.getPixels(screenPixels, 0, screenW, 0, 0, screen.width, screen.height)

        val tplPixels = IntArray(tplW * tplH)
        template.getPixels(tplPixels, 0, tplW, 0, 0, tplW, tplH)

        // Coarse stride for fast sliding-window scan, then local refinement around best candidate
        val coarseStride = max(2, minOf(tplW, tplH) / 8)
        val sampleStep = max(2, minOf(tplW, tplH) / 10)

        var bestScore = -1.0
        var bestX = roi.left
        var bestY = roi.top

        val maxStartX = roi.right - tplW
        val maxStartY = roi.bottom - tplH

        var y = roi.top
        while (y <= maxStartY) {
            var x = roi.left
            while (x <= maxStartX) {
                val score = computePixelSimilarity(
                    screenPixels,
                    screenW,
                    tplPixels,
                    tplW,
                    tplH,
                    x,
                    y,
                    sampleStep
                )
                if (score > bestScore) {
                    bestScore = score
                    bestX = x
                    bestY = y
                }
                x += coarseStride
            }
            y += coarseStride
        }

        // Local refinement around best coarse match
        val refineStartX = (bestX - coarseStride).coerceAtLeast(roi.left)
        val refineEndX = (bestX + coarseStride).coerceAtMost(maxStartX)
        val refineStartY = (bestY - coarseStride).coerceAtLeast(roi.top)
        val refineEndY = (bestY + coarseStride).coerceAtMost(maxStartY)

        for (ry in refineStartY..refineEndY) {
            for (rx in refineStartX..refineEndX) {
                val score = computePixelSimilarity(
                    screenPixels,
                    screenW,
                    tplPixels,
                    tplW,
                    tplH,
                    rx,
                    ry,
                    max(1, sampleStep / 2)
                )
                if (score > bestScore) {
                    bestScore = score
                    bestX = rx
                    bestY = ry
                }
            }
        }

        val confidencePct = (bestScore * 100.0).toInt().coerceIn(0, 100)
        val matched = confidencePct >= minConfidencePct.coerceIn(1, 100)
        ImageMatchResult(
            matched = matched,
            centerX = bestX + tplW / 2,
            centerY = bestY + tplH / 2,
            confidencePct = confidencePct
        )
    }

    private fun computePixelSimilarity(
        screenPixels: IntArray,
        screenW: Int,
        tplPixels: IntArray,
        tplW: Int,
        tplH: Int,
        startX: Int,
        startY: Int,
        step: Int
    ): Double {
        var totalDiff = 0L
        var samples = 0L
        var ty = 0
        while (ty < tplH) {
            val sRow = (startY + ty) * screenW
            val tRow = ty * tplW
            var tx = 0
            while (tx < tplW) {
                val sp = screenPixels[sRow + startX + tx]
                val tp = tplPixels[tRow + tx]
                val dr = abs(((sp shr 16) and 0xFF) - ((tp shr 16) and 0xFF))
                val dg = abs(((sp shr 8) and 0xFF) - ((tp shr 8) and 0xFF))
                val db = abs((sp and 0xFF) - (tp and 0xFF))
                totalDiff += (dr + dg + db)
                samples++
                tx += step
            }
            ty += step
        }
        if (samples == 0L) return 0.0
        val avgDiff = totalDiff.toDouble() / (samples * 3.0 * 255.0)
        return (1.0 - avgDiff).coerceIn(0.0, 1.0)
    }

    companion object {
        fun parseHexColor(hex: String): Int {
            val cleaned = hex.trim().removePrefix("#")
            return runCatching {
                Color.parseColor("#${cleaned.padStart(6, '0').takeLast(6)}")
            }.getOrDefault(Color.GREEN)
        }
    }
}
