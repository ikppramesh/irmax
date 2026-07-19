package com.ramesh.imaxcam

import android.graphics.ColorMatrix
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.RgbMatrix

enum class CameraFilter(val label: String) {
    NONE("Normal"),
    VIVID("Vivid"),
    MONO("B&W"),
    SEPIA("Sepia"),
}

private const val VIVID_SATURATION = 1.6f

// Standard luminance-weighted desaturation coefficients (Rec. 601), shared by MONO and VIVID.
private const val LUMA_R = 0.213f
private const val LUMA_G = 0.715f
private const val LUMA_B = 0.072f

/** Android-side matrix (drawing photos / the live preview layer), used with [android.graphics.ColorMatrixColorFilter]. */
fun androidColorMatrixFor(filter: CameraFilter): ColorMatrix = when (filter) {
    CameraFilter.NONE -> ColorMatrix()
    CameraFilter.VIVID -> ColorMatrix().apply { setSaturation(VIVID_SATURATION) }
    CameraFilter.MONO -> ColorMatrix().apply { setSaturation(0f) }
    CameraFilter.SEPIA -> ColorMatrix(
        floatArrayOf(
            0.393f, 0.769f, 0.189f, 0f, 0f,
            0.349f, 0.686f, 0.168f, 0f, 0f,
            0.272f, 0.534f, 0.131f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    )
}

/**
 * Media3-side matrix (baked into recorded video), same transforms as [androidColorMatrixFor] but
 * expressed as a 4x4 RGBA matrix in COLUMN-MAJOR order (the GL/uniform-mat4 convention Media3's
 * `RgbMatrix` expects) — NOT Android's own row-major `ColorMatrix` layout. Getting this backwards
 * doesn't just look "off": for a desaturation matrix specifically it makes every output channel
 * take on the green weight (0.715), which is exactly why the wrong layout showed up as a strong
 * green tint rather than a subtle color shift.
 */
@UnstableApi
fun media3RgbMatrixFor(filter: CameraFilter): RgbMatrix =
    RgbMatrix { _, _ ->
        when (filter) {
            CameraFilter.NONE -> floatArrayOf(
                1f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f,
                0f, 0f, 1f, 0f,
                0f, 0f, 0f, 1f,
            )
            CameraFilter.MONO -> floatArrayOf(
                LUMA_R, LUMA_R, LUMA_R, 0f,
                LUMA_G, LUMA_G, LUMA_G, 0f,
                LUMA_B, LUMA_B, LUMA_B, 0f,
                0f, 0f, 0f, 1f,
            )
            CameraFilter.VIVID -> {
                val s = VIVID_SATURATION
                val k = 1 - s
                floatArrayOf(
                    LUMA_R * k + s, LUMA_R * k, LUMA_R * k, 0f,
                    LUMA_G * k, LUMA_G * k + s, LUMA_G * k, 0f,
                    LUMA_B * k, LUMA_B * k, LUMA_B * k + s, 0f,
                    0f, 0f, 0f, 1f,
                )
            }
            CameraFilter.SEPIA -> floatArrayOf(
                0.393f, 0.349f, 0.272f, 0f,
                0.769f, 0.686f, 0.534f, 0f,
                0.189f, 0.168f, 0.131f, 0f,
                0f, 0f, 0f, 1f,
            )
        }
    }
