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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "IRMAX-Capture"

/** Where captured files land — pulled onto the Mac into Source/ for convert_to_imax.sh, per the plan. */
fun imaxCamDir(context: Context): File =
    File(context.getExternalFilesDir(null), "IMAXCam").apply { mkdirs() }

fun takePhoto(
    context: Context,
    imageCapture: ImageCapture,
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
                val croppedFile = File(dir, "IRMAX_${stamp}_143crop.jpg")
                runCatching { PhotoCropper.cropToImaxAndWatermark(context, rawFile, croppedFile) }
                    .onSuccess {
                        GalleryPublisher.publishImage(context, croppedFile)
                        onSaved(croppedFile)
                    }
                    .onFailure { onError(it as? Exception ?: Exception(it)) }
            }

            override fun onError(exc: ImageCaptureException) {
                Log.e(TAG, "Photo capture failed", exc)
                onError(exc)
            }
        }
    )
}

@androidx.annotation.OptIn(UnstableApi::class)
fun startVideoRecording(
    context: Context,
    videoCapture: VideoCapture<Recorder>,
    onFinalized: (File) -> Unit,
    onError: (Exception) -> Unit,
): Recording {
    val dir = imaxCamDir(context)
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val rawFile = File(dir, "IRMAX_${stamp}_RAW.mp4")
    val outputOptions = FileOutputOptions.Builder(rawFile).build()

    return videoCapture.output
        .prepareRecording(context, outputOptions)
        .withAudioEnabled()
        .start(ContextCompat.getMainExecutor(context)) { event ->
            if (event is VideoRecordEvent.Finalize) {
                if (event.hasError()) {
                    Log.e(TAG, "Video recording failed: ${event.error}", event.cause)
                    onError(Exception("Recording error code ${event.error}", event.cause))
                    return@start
                }
                val (w, h) = videoDimensions(rawFile)
                if (w <= 0 || h <= 0) {
                    onError(Exception("Could not read recorded video dimensions"))
                    return@start
                }
                val croppedFile = File(dir, "IRMAX_${stamp}_143crop.mp4")
                val watermark = BitmapFactory.decodeResource(context.resources, R.drawable.irmax_watermark)
                VideoCropper.cropToImaxAndWatermark(
                    context, rawFile, croppedFile, w, h, watermark,
                    onSuccess = {
                        GalleryPublisher.publishVideo(context, croppedFile)
                        onFinalized(croppedFile)
                    },
                    onError = onError,
                )
            }
        }
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
