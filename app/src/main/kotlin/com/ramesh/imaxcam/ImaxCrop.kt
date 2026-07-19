package com.ramesh.imaxcam

/** The two IMAX film ratios the app can frame/crop for. */
enum class ImaxRatio(val value: Float, val label: String, val fileTag: String) {
    R143(1.43f, "1.43:1", "143"),
    R190(1.90f, "1.90:1", "190"),
}

// Shared by the live viewfinder overlay and the photo/video crop pipelines so the watermark you
// see while framing a shot is the same size, relative to the frame, as what actually gets saved.
const val WATERMARK_WIDTH_FRACTION = 0.06f
const val WATERMARK_MARGIN_FRACTION = 0.025f
const val WATERMARK_ALPHA = 0.3f

data class CropRect(val left: Float, val top: Float, val width: Float, val height: Float)

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
