package com.ramesh.imaxcam

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

private val DIM_COLOR = Color.Black.copy(alpha = 0.55f)
private val GRID_COLOR = Color.White.copy(alpha = 0.5f)

/**
 * Draws the region of the current capture that will survive a crop to [targetRatio], plus a
 * bottom-right watermark preview sized relative to that guide box — so what you see while framing
 * a shot is the same relative size as what [PhotoCropper]/[VideoCropper] actually bakes in, instead
 * of a fixed on-screen size that would look bigger or smaller depending on the current ratio/crop.
 *
 * The crop math is generic on purpose: a 4:3 still (ratio 1.333, narrower than either IMAX ratio)
 * needs its top/bottom trimmed, while a 16:9 video (ratio 1.778) needs its left/right trimmed
 * instead — which direction applies depends on both [captureRatio] and [targetRatio], so both are
 * taken as live values rather than assumed.
 *
 * PreviewView must be set to FIT_CENTER (not the CameraX default FILL_CENTER) for this to be a
 * true WYSIWYG guide — otherwise the widget already crops part of the sensor frame before this
 * overlay even gets a chance to show it.
 */
@Composable
fun FramingOverlay(captureRatio: Double, targetRatio: Float, ratioLabel: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val watermark = remember {
        BitmapFactory.decodeResource(context.resources, R.drawable.irmax_watermark).asImageBitmap()
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val viewW = size.width
        val viewH = size.height
        val viewRatio = viewW / viewH
        val ratio = captureRatio.toFloat()

        // Full sensor frame as letterboxed/pillarboxed by PreviewView's FIT_CENTER within the widget bounds.
        val sensorW: Float
        val sensorH: Float
        if (ratio > viewRatio) {
            sensorW = viewW
            sensorH = viewW / ratio
        } else {
            sensorH = viewH
            sensorW = viewH * ratio
        }
        val sensorLeft = (viewW - sensorW) / 2f
        val sensorTop = (viewH - sensorH) / 2f

        // The IMAX guide box within that sensor frame — same math the actual crop uses.
        val crop = imaxCropRect(sensorW, sensorH, targetRatio)
        val guideW = crop.width
        val guideH = crop.height
        val guideLeft = sensorLeft + crop.left
        val guideTop = sensorTop + crop.top

        // Dim the sensor-visible area that will be cropped away, leaving the guide box clear.
        if (guideH < sensorH) {
            val bandH = (sensorH - guideH) / 2f
            drawRect(DIM_COLOR, topLeft = Offset(sensorLeft, sensorTop), size = Size(sensorW, bandH))
            drawRect(DIM_COLOR, topLeft = Offset(sensorLeft, guideTop + guideH), size = Size(sensorW, bandH))
        }
        if (guideW < sensorW) {
            val bandW = (sensorW - guideW) / 2f
            drawRect(DIM_COLOR, topLeft = Offset(sensorLeft, sensorTop), size = Size(bandW, sensorH))
            drawRect(DIM_COLOR, topLeft = Offset(guideLeft + guideW, sensorTop), size = Size(bandW, sensorH))
        }

        drawRect(
            color = Color.White,
            topLeft = Offset(guideLeft, guideTop),
            size = Size(guideW, guideH),
            style = Stroke(width = 3f)
        )

        // Rule-of-thirds grid within the guide box, for composition alignment.
        val thirdW = guideW / 3f
        val thirdH = guideH / 3f
        for (i in 1..2) {
            val x = guideLeft + thirdW * i
            drawLine(GRID_COLOR, Offset(x, guideTop), Offset(x, guideTop + guideH), strokeWidth = 1.5f)
            val y = guideTop + thirdH * i
            drawLine(GRID_COLOR, Offset(guideLeft, y), Offset(guideLeft + guideW, y), strokeWidth = 1.5f)
        }

        // Watermark preview, sized/positioned identically (relative to the guide box) to the real bake-in.
        val wmWidth = guideW * WATERMARK_WIDTH_FRACTION
        val wmHeight = wmWidth * watermark.height / watermark.width
        val margin = guideW * WATERMARK_MARGIN_FRACTION
        drawImage(
            image = watermark,
            dstOffset = IntOffset(
                (guideLeft + guideW - wmWidth - margin).toInt(),
                (guideTop + guideH - wmHeight - margin).toInt()
            ),
            dstSize = IntSize(wmWidth.toInt(), wmHeight.toInt()),
            alpha = WATERMARK_ALPHA
        )

        // Bottom-left ratio tag, mirroring the watermark's margin/alpha so the live guide matches
        // what PhotoCropper/VideoCropper actually bake in. Sized by WIDTH (its own fraction, not the
        // watermark's) since matching the watermark's height made the text visibly wider than the
        // compact logo at the same nominal size.
        val labelPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            alpha = (WATERMARK_ALPHA * 255).toInt()
            textSize = 100f
        }
        val labelTargetWidth = guideW * RATIO_LABEL_WIDTH_FRACTION
        val probeWidth = labelPaint.measureText(ratioLabel)
        labelPaint.textSize = labelTargetWidth * (100f / probeWidth)
        val labelBaselineY = guideTop + guideH - margin - labelPaint.fontMetrics.descent
        drawContext.canvas.nativeCanvas.drawText(ratioLabel, guideLeft + margin, labelBaselineY, labelPaint)
    }
}
