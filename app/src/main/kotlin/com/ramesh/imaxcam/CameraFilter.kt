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

/** Media3-side matrix (baked into recorded video), same transforms expressed as a 4x4 RGBA matrix. */
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
                LUMA_R, LUMA_G, LUMA_B, 0f,
                LUMA_R, LUMA_G, LUMA_B, 0f,
                LUMA_R, LUMA_G, LUMA_B, 0f,
                0f, 0f, 0f, 1f,
            )
            CameraFilter.VIVID -> {
                val s = VIVID_SATURATION
                val rr = LUMA_R * (1 - s) + s
                val rg = LUMA_G * (1 - s)
                val rb = LUMA_B * (1 - s)
                val gr = LUMA_R * (1 - s)
                val gg = LUMA_G * (1 - s) + s
                val gb = LUMA_B * (1 - s)
                val br = LUMA_R * (1 - s)
                val bg = LUMA_G * (1 - s)
                val bb = LUMA_B * (1 - s) + s
                floatArrayOf(
                    rr, rg, rb, 0f,
                    gr, gg, gb, 0f,
                    br, bg, bb, 0f,
                    0f, 0f, 0f, 1f,
                )
            }
            CameraFilter.SEPIA -> floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f,
                0.349f, 0.686f, 0.168f, 0f,
                0.272f, 0.534f, 0.131f, 0f,
                0f, 0f, 0f, 1f,
            )
        }
    }
