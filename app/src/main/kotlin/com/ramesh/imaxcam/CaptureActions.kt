package com.ramesh.imaxcam

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "IRMAX-Capture"

/** Where captured files land — pulled onto the Mac into Source/ for convert_to_imax.sh, per the plan. */
fun imaxCamDir(context: Context): File =
    File(context.getExternalFilesDir(null), "IMAXCam").apply { mkdirs() }

/** The in-progress recording's handle plus the raw file CameraX is actively writing to, so the UI can poll its live size. */
data class VideoRecordingHandle(val recording: Recording, val rawFile: File)

fun takePhoto(
    context: Context,
    imageCapture: ImageCapture,
    ratio: ImaxRatio,
    filter: CameraFilter,
    scope: CoroutineScope,
    onExportStart: () -> Unit,
    onSaved: (File) -> Unit,
    onError: (Exception) -> Unit,
) {
    val dir = imaxCamDir(context)
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val rawFile = File(dir, "IRMAX_${stamp}_RAW.jpg")
    val outputOptions = ImageCapture.OutputFileOptions.Builder(rawFile).build()

    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                onExportStart()
                ExportNotifier.showProgress(context, NOTIFICATION_ID_PHOTO, "Saving photo…", progress = 0, indeterminate = true)
                val croppedFile = File(dir, "IRMAX_${stamp}_${ratio.fileTag}crop.jpg")

                // Bitmap decode/crop/compress is real CPU work — keep it off the main thread so the
                // "Saving…" indicator can actually animate instead of the UI freezing under it.
                scope.launch(Dispatchers.IO) {
                    val result = runCatching { PhotoCropper.cropToImaxAndWatermark(context, rawFile, croppedFile, ratio, filter) }
                    withContext(Dispatchers.Main) {
                        result
                            .onSuccess {
                                GalleryPublisher.publishImage(context, croppedFile)
                                ExportNotifier.showDone(
                                    context, NOTIFICATION_ID_PHOTO, "Photo saved",
                                    "${croppedFile.name} · ${sizeLabel(croppedFile.length())}"
                                )
                                onSaved(croppedFile)
                            }
                            .onFailure {
                                ExportNotifier.showError(context, NOTIFICATION_ID_PHOTO, "Photo save failed", it.message ?: "Unknown error")
                                onError(it as? Exception ?: Exception(it))
                            }
                    }
                }
            }

            override fun onError(exc: ImageCaptureException) {
                Log.e(TAG, "Photo capture failed", exc)
                ExportNotifier.showError(context, NOTIFICATION_ID_PHOTO, "Photo capture failed", exc.message ?: "Unknown error")
                onError(exc)
            }
        }
    )
}

@androidx.annotation.OptIn(UnstableApi::class)
fun startVideoRecording(
    context: Context,
    videoCapture: VideoCapture<Recorder>,
    ratio: ImaxRatio,
    filter: CameraFilter,
    onExportStart: () -> Unit,
    onFinalized: (File) -> Unit,
    onError: (Exception) -> Unit,
): VideoRecordingHandle {
    val dir = imaxCamDir(context)
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val rawFile = File(dir, "IRMAX_${stamp}_RAW.mp4")
    val outputOptions = FileOutputOptions.Builder(rawFile).build()

    val recording = videoCapture.output
        .prepareRecording(context, outputOptions)
        .withAudioEnabled()
        .start(ContextCompat.getMainExecutor(context)) { event ->
            if (event is VideoRecordEvent.Finalize) {
                if (event.hasError()) {
                    Log.e(TAG, "Video recording failed: ${event.error}", event.cause)
                    ExportNotifier.cancel(context, NOTIFICATION_ID_VIDEO)
                    onError(Exception("Recording error code ${event.error}", event.cause))
                    return@start
                }
                val (w, h) = videoDimensions(rawFile)
                if (w <= 0 || h <= 0) {
                    ExportNotifier.cancel(context, NOTIFICATION_ID_VIDEO)
                    onError(Exception("Could not read recorded video dimensions"))
                    return@start
                }
                val croppedFile = File(dir, "IRMAX_${stamp}_${ratio.fileTag}crop.mp4")
                val watermark = BitmapFactory.decodeResource(context.resources, R.drawable.irmax_watermark)
                onExportStart()
                ExportNotifier.showProgress(context, NOTIFICATION_ID_VIDEO, "Exporting video…", progress = 0)
                VideoCropper.cropToImaxAndWatermark(
                    context, rawFile, croppedFile, w, h, watermark, ratio, filter,
                    onProgress = { pct ->
                        ExportNotifier.showProgress(context, NOTIFICATION_ID_VIDEO, "Exporting video…", progress = pct)
                    },
                    onSuccess = {
                        GalleryPublisher.publishVideo(context, croppedFile)
                        ExportNotifier.showDone(
                            context, NOTIFICATION_ID_VIDEO, "Video saved",
                            "${croppedFile.name} · ${sizeLabel(croppedFile.length())}"
                        )
                        onFinalized(croppedFile)
                    },
                    onError = {
                        ExportNotifier.showError(context, NOTIFICATION_ID_VIDEO, "Video export failed", it.message ?: "Unknown error")
                        onError(it)
                    },
                )
            }
        }

    return VideoRecordingHandle(recording, rawFile)
}

fun sizeLabel(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

private fun videoDimensions(file: File): Pair<Int, Int> {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(file.path)
        val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        w to h
    } finally {
        retriever.release()
    }
}
