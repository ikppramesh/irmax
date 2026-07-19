package com.ramesh.imaxcam

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.common.Effect
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.transformer.Transformer
import java.io.File

private const val TAG = "IRMAX-VideoCropper"

/**
 * Crops a recorded clip to 1.43:1 (same [imaxCropRect] math as the live guide and photo crop) and
 * bakes in the bottom-right IRMAX watermark, via Media3 Transformer — chosen over hand-rolled
 * MediaCodec/OpenGL or bundling ffmpeg-kit (unmaintained/licensing risk, no re-encode-avoidance
 * benefit here since cropping always requires re-encoding regardless of tool).
 */
@UnstableApi
object VideoCropper {

    fun cropToImaxAndWatermark(
        context: Context,
        sourceFile: File,
        outputFile: File,
        sourceWidth: Int,
        sourceHeight: Int,
        watermarkBitmap: Bitmap,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit,
    ) {
        val crop = imaxCropRect(sourceWidth.toFloat(), sourceHeight.toFloat())
        val cropW = (crop.width.toInt()).let { it - (it % 2) }
        val cropH = (crop.height.toInt()).let { it - (it % 2) }

        val overlaySettings = OverlaySettings.Builder()
            .setAlphaScale(0.3f)
            .setScale(0.10f, 0.10f)
            .setOverlayFrameAnchor(1f, -1f)
            .setBackgroundFrameAnchor(0.85f, -0.85f)
            .build()
        val overlay = BitmapOverlay.createStaticBitmapOverlay(watermarkBitmap, overlaySettings)
        val overlayEffect = OverlayEffect(listOf(overlay))

        val effects = Effects(
            listOf<AudioProcessor>(),
            listOf<Effect>(
                Presentation.createForWidthAndHeight(cropW, cropH, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP),
                overlayEffect,
            )
        )

        val editedMediaItem = EditedMediaItem.Builder(MediaItem.fromUri(sourceFile.toURI().toString()))
            .setEffects(effects)
            .build()

        val transformer = Transformer.Builder(context)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    Log.i(TAG, "Export complete: ${outputFile.name}")
                    onSuccess()
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    Log.e(TAG, "Export failed for ${outputFile.name}", exportException)
                    onError(exportException)
                }
            })
            .build()

        transformer.start(editedMediaItem, outputFile.absolutePath)
    }
}
