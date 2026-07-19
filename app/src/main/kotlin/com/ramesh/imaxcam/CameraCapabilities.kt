package com.ramesh.imaxcam

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.CamcorderProfile
import android.util.Log

private const val TAG = "IRMAX-Caps"

/** A candidate output size, sorted by megapixels so "highest resolution" is a simple pick. */
data class CaptureSize(val width: Int, val height: Int) {
    val megapixels: Double get() = width.toLong() * height / 1_000_000.0
    val ratio: Double get() = width.toDouble() / height.toDouble()
    override fun toString() = "%dx%d (%.1fMP, ratio %.3f)".format(width, height, megapixels, ratio)
}

/** A video quality tier (e.g. "8K UHD") mapped to the actual frame size the device reports for it. */
data class VideoQualityOption(val label: String, val size: CaptureSize)

data class CameraReport(
    val cameraId: String,
    val focalLengthMm: Float?,
    val stillSizes: List<CaptureSize>,
    val videoQualities: List<VideoQualityOption>,
) {
    val maxStill: CaptureSize? get() = stillSizes.maxByOrNull { it.width.toLong() * it.height }
    val maxVideo: VideoQualityOption? get() = videoQualities.maxByOrNull { it.size.width.toLong() * it.size.height }
}

/**
 * Queries the real device at runtime for every back-facing camera (a Fold-class phone exposes
 * separate ids for main/ultrawide/telephoto/etc, not just one) and every supported still/video
 * size on each. Never hardcodes assumed Fold7 specs — Samsung sometimes gates its
 * highest-resolution modes (200MP stills, 8K video) behind vendor-only Camera2 extensions, and
 * which physical lens is even "the main one" varies by camera id, so both have to be discovered
 * from CameraCharacteristics rather than assumed.
 */
object CameraCapabilities {

    // CamcorderProfile quality constants, highest to lowest, checked against what THIS device
    // actually reports via CamcorderProfile.getAll() rather than assumed to exist.
    private val QUALITY_CANDIDATES = listOf(
        CamcorderProfile.QUALITY_8KUHD to "8K UHD",
        CamcorderProfile.QUALITY_4KDCI to "4K DCI",
        CamcorderProfile.QUALITY_2160P to "4K UHD",
        CamcorderProfile.QUALITY_QHD to "QHD",
        CamcorderProfile.QUALITY_2K to "2K",
        CamcorderProfile.QUALITY_1080P to "1080p",
        CamcorderProfile.QUALITY_720P to "720p",
        CamcorderProfile.QUALITY_480P to "480p",
    )

    /** All back-facing cameras, sorted with the highest-resolution still sensor first. */
    fun probeBackCameras(context: Context): List<CameraReport> {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

        val backIds = manager.cameraIdList.filter { id ->
            manager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        }
        if (backIds.isEmpty()) {
            Log.e(TAG, "No back-facing camera found")
            return emptyList()
        }

        val reports = backIds.map { id -> probeOne(manager, id) }
            .sortedByDescending { it.maxStill?.let { s -> s.width.toLong() * s.height } ?: 0L }

        Log.i(TAG, "Found ${reports.size} back-facing camera id(s): ${reports.joinToString { it.cameraId }}")
        reports.forEach { r ->
            Log.i(TAG, "Camera id=${r.cameraId} focalLength=${r.focalLengthMm}mm maxStill=${r.maxStill} maxVideo=${r.maxVideo?.label}=${r.maxVideo?.size}")
            Log.i(TAG, "  Still sizes (${r.stillSizes.size}): ${r.stillSizes.joinToString()}")
            Log.i(TAG, "  Video qualities: ${r.videoQualities.joinToString { "${it.label}=${it.size}" }}")
        }
        return reports
    }

    private fun probeOne(manager: CameraManager, cameraId: String): CameraReport {
        val chars = manager.getCameraCharacteristics(cameraId)
        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val focalLength = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull()

        val standardJpeg = map?.getOutputSizes(ImageFormat.JPEG)
            ?.map { CaptureSize(it.width, it.height) } ?: emptyList()

        // Burst-limited "high resolution" still sizes (e.g. 50MP/108MP/200MP sensor modes),
        // surfaced separately by the platform since they can't sustain full frame rate.
        val highRes = map?.getHighResolutionOutputSizes(ImageFormat.JPEG)
            ?.map { CaptureSize(it.width, it.height) } ?: emptyList()

        val allStillSizes = (standardJpeg + highRes)
            .distinct()
            .sortedByDescending { it.width.toLong() * it.height }

        val videoQualities = QUALITY_CANDIDATES.mapNotNull { (quality, label) ->
            runCatching {
                val profiles = CamcorderProfile.getAll(cameraId, quality) ?: return@runCatching null
                val vp = profiles.videoProfiles.firstOrNull() ?: return@runCatching null
                VideoQualityOption(label, CaptureSize(vp.width, vp.height))
            }.getOrNull()
        }

        return CameraReport(cameraId, focalLength, allStillSizes, videoQualities)
    }
}
