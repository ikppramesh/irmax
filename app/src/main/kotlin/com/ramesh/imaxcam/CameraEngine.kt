package com.ramesh.imaxcam

import android.content.Context
import android.util.Log
import android.util.Size
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
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
     * Binds preview + still capture to a specific Camera2 camera id (from [CameraCapabilities]) on
     * the given [facing], or the default camera for that facing if the id isn't found.
     * [targetStillSize] pins ImageCapture to the resolution picked in the UI; CameraX snaps to the
     * closest size the HAL actually supports. [onCameraReady] exposes the bound [Camera] so the UI
     * can drive torch control (`camera.cameraControl.enableTorch(...)`).
     */
    @OptIn(ExperimentalCamera2Interop::class)
    fun bindPhoto(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        cameraId: String? = null,
        facing: Int = CameraSelector.LENS_FACING_BACK,
        targetStillSize: CaptureSize? = null,
        onImageCaptureReady: (ImageCapture) -> Unit = {},
        onCameraReady: (Camera) -> Unit = {},
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
            val selector = selectorFor(cameraId, facing)

            try {
                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
                onImageCaptureReady(imageCapture)
                onCameraReady(camera)
                Log.i(TAG, "Preview+photo bound to camera id=${cameraId ?: "default"} facing=$facing, targetStill=$targetStillSize")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bind camera for id=$cameraId, falling back to default", e)
                runCatching {
                    cameraProvider.unbindAll()
                    val fallbackSelector = defaultSelectorFor(facing)
                    val camera = cameraProvider.bindToLifecycle(lifecycleOwner, fallbackSelector, preview, imageCapture)
                    onImageCaptureReady(imageCapture)
                    onCameraReady(camera)
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
        facing: Int = CameraSelector.LENS_FACING_BACK,
        targetQualityLabel: String? = null,
        onVideoCaptureReady: (VideoCapture<Recorder>) -> Unit = {},
        onCameraReady: (Camera) -> Unit = {},
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
            val selector = selectorFor(cameraId, facing)
            // Deliberately plain SDR: requesting HLG/HDR dynamic range on this device destabilized
            // the camera HAL (triggered repeated vendor extension crashes) and produced corrupt,
            // near-empty Media3 Transformer output. Not worth it for a tier that wasn't even real
            // Dolby Vision to begin with (Samsung gates that behind its own camera app).
            val videoCapture = VideoCapture.withOutput(recorder)

            try {
                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, videoCapture)
                onVideoCaptureReady(videoCapture)
                onCameraReady(camera)
                Log.i(TAG, "Preview+video bound to camera id=${cameraId ?: "default"} facing=$facing, quality=$quality")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bind video for id=$cameraId, falling back to default", e)
                runCatching {
                    cameraProvider.unbindAll()
                    val fallbackSelector = defaultSelectorFor(facing)
                    val camera = cameraProvider.bindToLifecycle(lifecycleOwner, fallbackSelector, preview, videoCapture)
                    onVideoCaptureReady(videoCapture)
                    onCameraReady(camera)
                }
            }
        }, ContextCompat.getMainExecutor(context))
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun selectorFor(cameraId: String?, facing: Int): CameraSelector =
        if (cameraId == null) {
            defaultSelectorFor(facing)
        } else {
            CameraSelector.Builder()
                .requireLensFacing(facing)
                .addCameraFilter { cameraInfos ->
                    cameraInfos.filter { Camera2CameraInfo.from(it).cameraId == cameraId }
                }
                .build()
        }

    private fun defaultSelectorFor(facing: Int): CameraSelector =
        if (facing == CameraSelector.LENS_FACING_FRONT) CameraSelector.DEFAULT_FRONT_CAMERA
        else CameraSelector.DEFAULT_BACK_CAMERA
}
