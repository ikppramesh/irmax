package com.ramesh.imaxcam

data class CropRect(val left: Float, val top: Float, val width: Float, val height: Float)

/**
 * Centered 1.43:1 crop rect within a WxH source, trimming whichever dimension is in excess.
 * Shared by [FramingOverlay] (the live guide) and [PhotoCropper]/video cropping so the on-screen
 * promise and the actual saved crop can never drift apart.
 */
fun imaxCropRect(width: Float, height: Float, targetRatio: Float = IMAX_143_RATIO): CropRect {
    val ratio = width / height
    return if (ratio > targetRatio) {
        val w = height * targetRatio
        CropRect(left = (width - w) / 2f, top = 0f, width = w, height = height)
    } else {
        val h = width / targetRatio
        CropRect(left = 0f, top = (height - h) / 2f, width = width, height = h)
    }
}
