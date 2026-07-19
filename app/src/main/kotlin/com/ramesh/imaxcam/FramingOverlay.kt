package com.ramesh.imaxcam

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke

/** The true full-frame 15/70 IMAX film ratio. */
const val IMAX_143_RATIO = 1.43f

private val DIM_COLOR = Color.Black.copy(alpha = 0.55f)
private val GRID_COLOR = Color.White.copy(alpha = 0.5f)

/**
 * Draws the region of the current capture that will survive a crop to 1.43:1.
 *
 * The math is generic on purpose: a 4:3 still (ratio 1.333, narrower than 1.43) needs its
 * top/bottom trimmed to reach 1.43:1, while a 16:9 video (ratio 1.778, wider than 1.43) needs
 * its left/right trimmed instead. [captureRatio] is whatever the actually-selected size reports,
 * so this handles both directions without assuming which one applies.
 *
 * PreviewView must be set to FIT_CENTER (not the CameraX default FILL_CENTER) for this to be a
 * true WYSIWYG guide — otherwise the widget already crops part of the sensor frame before this
 * overlay even gets a chance to show it.
 */
@Composable
fun FramingOverlay(captureRatio: Double, modifier: Modifier = Modifier) {
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

        // The 1.43:1 guide box within that sensor frame — same math the actual crop uses.
        val crop = imaxCropRect(sensorW, sensorH)
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
    }
}
