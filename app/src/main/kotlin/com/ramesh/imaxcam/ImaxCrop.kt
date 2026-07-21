package com.ramesh.imaxcam

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface

/**
 * Theatrical film ratios the app can frame/crop for, tallest to widest — the same lineup as
 * docs/index.html's format comparison page, so the app and the web page never disagree on a
 * ratio's name or number. [label] uses standard decimal notation (e.g. "1.43:1"), not colons
 * throughout, matching real cinema usage.
 */
enum class ImaxRatio(val value: Float, val label: String, val short: String, val fileTag: String) {
    MOVIETONE(1.19f, "1.19:1", "Movietone", "119"),
    ACADEMY(1.37f, "1.37:1", "Academy", "137"),
    IMAX_GT(1.43f, "1.43:1", "IMAX GT", "143"),
    EUROPEAN_FLAT(1.66f, "1.66:1", "Euro Flat", "166"),
    FLAT_35MM(1.85f, "1.85:1", "35mm Flat", "185"),
    IMAX_DIGITAL(1.90f, "1.90:1", "IMAX Digital", "190"),
    SEVENTY_MM(2.20f, "2.20:1", "70mm", "220"),
    SCOPE_35MM(2.39f, "2.39:1", "35mm Scope", "239"),
    CINEMASCOPE(2.55f, "2.55:1", "CinemaScope", "255"),
    CINERAMA(2.59f, "2.59:1", "Cinerama", "259"),
    ULTRA_PANAVISION(2.76f, "2.76:1", "Ultra Panavision", "276"),
}

// Shared by the live viewfinder overlay and the photo/video crop pipelines so the watermark you
// see while framing a shot is the same size, relative to the frame, as what actually gets saved.
const val WATERMARK_WIDTH_FRACTION = 0.06f
const val WATERMARK_MARGIN_FRACTION = 0.025f
const val WATERMARK_ALPHA = 0.3f

// The bottom-left ratio tag ("1.43:1"/"1.90:1") is sized by WIDTH, same as the watermark, but at
// its own fraction — matching the watermark's height made the text visibly wider than the compact
// logo at the same nominal size, so it gets an explicit width budget instead.
const val RATIO_LABEL_WIDTH_FRACTION = 0.05f

data class CropRect(val left: Float, val top: Float, val width: Float, val height: Float)

/**
 * Renders [label] (e.g. "1.43:1") as a standalone white-text bitmap, sized so its rendered width
 * matches [targetWidthPx] (a fraction of the frame, same convention as the watermark's width).
 * Alpha is intentionally NOT baked in here; callers apply [WATERMARK_ALPHA] the same way they do
 * for the watermark image (Paint.alpha for photos, OverlaySettings.setAlphaScale for video, and the
 * `alpha` param of Compose's drawImage for the live preview) so both marks fade together identically.
 */
fun ratioLabelBitmap(label: String, targetWidthPx: Int): Bitmap {
    val probe = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        textSize = 100f
    }
    val probeWidth = probe.measureText(label)
    val textSize = targetWidthPx * (100f / probeWidth)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        typeface = Typeface.DEFAULT_BOLD
        this.textSize = textSize
    }
    val metrics = paint.fontMetrics
    val width = paint.measureText(label).toInt().coerceAtLeast(1)
    val height = (metrics.descent - metrics.ascent).toInt().coerceAtLeast(1)

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    Canvas(bitmap).drawText(label, 0f, -metrics.ascent, paint)
    return bitmap
}

/**
 * Centered crop rect within a WxH source, trimming whichever dimension is in excess to reach
 * [targetRatio]. Shared by [FramingOverlay] (the live guide) and [PhotoCropper]/[VideoCropper] so
 * the on-screen promise and the actual saved crop can never drift apart.
 */
fun imaxCropRect(width: Float, height: Float, targetRatio: Float): CropRect {
    val ratio = width / height
    return if (ratio > targetRatio) {
        val w = height * targetRatio
        CropRect(left = (width - w) / 2f, top = 0f, width = w, height = height)
    } else {
        val h = width / targetRatio
        CropRect(left = 0f, top = (height - h) / 2f, width = width, height = h)
    }
}
