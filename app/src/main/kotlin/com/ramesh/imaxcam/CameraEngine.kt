package com.ramesh.imaxcam

import android.content.Context
import android.util.Log
import android.util.Size
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner

private const val TAG = "IRMAX-Camera"

/** Maps a [VideoQualityOption] label (from real CamcorderProfile data) to CameraX's Quality enum,
 * which only defines SD/HD/FHD/UHD tiers — tiers without a direct match fall back to the closest. */
private fun qualityForLabel(label: String): Quality = when (label) {
    "8K UHD", "4K DCI", "4K UHD" -> Quality.UHD
    "QHD", "2K", "1080p" -> Quality.FHD
    "720p" -> Quality.HD
    "480p" -> Quality.SD
    else -> Quality.HIGHEST
}

class CameraEngine(private val context: Context) {

    /**
     * Binds preview + still capture to a specific Camera2 camera id (from [CameraCapabilities]),
     * or the default back camera if null/not found. [targetStillSize] pins ImageCapture to the
     * resolution picked in the UI; CameraX snaps to the closest size the HAL actually supports.
     */
    @OptIn(ExperimentalCamera2Interop::class)
    fun bindPhoto(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        cameraId: String? = null,
        targetStillSize: CaptureSize? = null,
        onImageCaptureReady: (ImageCapture) -> Unit = {},
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            val imageCaptureBuilder = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            if (targetStillSize != null) {
                imageCaptureBuilder.setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                Size(targetStillSize.width, targetStillSize.height),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                            )
                        )
                        .build()
                )
            }
            val imageCapture = imageCaptureBuilder.build()
            val selector = selectorFor(cameraId)

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
                onImageCaptureReady(imageCapture)
                Log.i(TAG, "Preview+photo bound to camera id=${cameraId ?: "default back"}, targetStill=$targetStillSize")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bind camera for id=$cameraId, falling back to default", e)
                runCatching {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                    onImageCaptureReady(imageCapture)
                }
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /** Binds preview + video capture, pinned to the closest CameraX Quality tier for [targetQualityLabel]. */
    @OptIn(ExperimentalCamera2Interop::class)
    fun bindVideo(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        cameraId: String? = null,
        targetQualityLabel: String? = null,
        onVideoCaptureReady: (VideoCapture<Recorder>) -> Unit = {},
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            val quality = targetQualityLabel?.let { qualityForLabel(it) } ?: Quality.HIGHEST
            val recorder = Recorder.Builder()
                .setQualitySelector(
                    QualitySelector.from(quality, FallbackStrategy.lowerQualityOrHigherThan(quality))
                )
                .build()
            val videoCapture = VideoCapture.withOutput(recorder)
            val selector = selectorFor(cameraId)

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, videoCapture)
                onVideoCaptureReady(videoCapture)
                Log.i(TAG, "Preview+video bound to camera id=${cameraId ?: "default back"}, quality=$quality")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bind video for id=$cameraId, falling back to default", e)
                runCatching {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, videoCapture)
                    onVideoCaptureReady(videoCapture)
                }
            }
        }, ContextCompat.getMainExecutor(context))
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun selectorFor(cameraId: String?): CameraSelector =
        if (cameraId == null) {
            CameraSelector.DEFAULT_BACK_CAMERA
        } else {
            CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .addCameraFilter { cameraInfos ->
                    cameraInfos.filter { Camera2CameraInfo.from(it).cameraId == cameraId }
                }
                .build()
        }
}
