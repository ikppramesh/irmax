package com.ramesh.imaxcam

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.AudioProcessor
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
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import java.io.File

private const val TAG = "IRMAX-VideoCropper"
private const val PROGRESS_POLL_MS = 300L

// Media3 can report onCompleted() even when the underlying encode silently produced almost
// nothing (observed: a 594-byte "output" after a camera HAL hiccup) — a real exported clip is
// always far above this, so treat anything under it as a failure rather than a false success.
private const val MIN_VALID_OUTPUT_BYTES = 20_000L

/**
 * Crops a recorded clip to the selected IMAX ratio (same [imaxCropRect] math as the live guide and
 * photo crop) and bakes in the bottom-right IRMAX watermark, via Media3 Transformer — chosen over
 * hand-rolled MediaCodec/OpenGL or bundling ffmpeg-kit (unmaintained/licensing risk, no
 * re-encode-avoidance benefit here since cropping always requires re-encoding regardless of tool).
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
        ratio: ImaxRatio,
        filter: CameraFilter,
        onProgress: (Int) -> Unit = {},
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit,
    ) {
        val crop = imaxCropRect(sourceWidth.toFloat(), sourceHeight.toFloat(), ratio.value)
        val cropW = (crop.width.toInt()).let { it - (it % 2) }
        val cropH = (crop.height.toInt()).let { it - (it % 2) }

        // Pre-scale the watermark to an exact pixel width (10% of the output frame) so it matches
        // PhotoCropper's sizing precisely — Media3's overlay `scale` multiplies the bitmap's own
        // native pixel size, not a fraction of the output frame, so relying on it alone produced a
        // much smaller mark on video than on photos at the same nominal "0.10" setting.
        val wmWidth = (cropW * WATERMARK_WIDTH_FRACTION).toInt().coerceAtLeast(1)
        val wmHeight = (wmWidth * watermarkBitmap.height / watermarkBitmap.width).coerceAtLeast(1)
        val scaledWatermark = Bitmap.createScaledBitmap(watermarkBitmap, wmWidth, wmHeight, true)

        val overlaySettings = OverlaySettings.Builder()
            .setAlphaScale(WATERMARK_ALPHA)
            .setOverlayFrameAnchor(1f, -1f)
            .setBackgroundFrameAnchor(0.85f, -0.85f)
            .build()
        val overlay = BitmapOverlay.createStaticBitmapOverlay(scaledWatermark, overlaySettings)
        val overlayEffect = OverlayEffect(listOf(overlay))

        val videoEffects = buildList<Effect> {
            add(Presentation.createForWidthAndHeight(cropW, cropH, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP))
            if (filter != CameraFilter.NONE) add(media3RgbMatrixFor(filter))
            add(overlayEffect)
        }
        val effects = Effects(listOf<AudioProcessor>(), videoEffects)

        val editedMediaItem = EditedMediaItem.Builder(MediaItem.fromUri(sourceFile.toURI().toString()))
            .setEffects(effects)
            .build()

        var finished = false
        val handler = Handler(Looper.getMainLooper())
        lateinit var transformer: Transformer

        val progressPoller = object : Runnable {
            override fun run() {
                if (finished) return
                val progressHolder = ProgressHolder()
                val state = transformer.getProgress(progressHolder)
                if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                    onProgress(progressHolder.progress)
                }
                handler.postDelayed(this, PROGRESS_POLL_MS)
            }
        }

        transformer = Transformer.Builder(context)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    finished = true
                    val sizeBytes = outputFile.length()
                    if (sizeBytes < MIN_VALID_OUTPUT_BYTES) {
                        Log.e(TAG, "Export reported success but output is only $sizeBytes bytes for ${outputFile.name} — treating as failed")
                        outputFile.delete()
                        onError(Exception("Export produced an invalid file (camera/encoder likely hiccuped mid-export — try again)"))
                        return
                    }
                    Log.i(TAG, "Export complete: ${outputFile.name} ($sizeBytes bytes)")
                    onProgress(100)
                    onSuccess()
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    finished = true
                    Log.e(TAG, "Export failed for ${outputFile.name}", exportException)
                    onError(exportException)
                }
            })
            .build()

        transformer.start(editedMediaItem, outputFile.absolutePath)
        handler.post(progressPoller)
    }
}
