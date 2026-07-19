package com.ramesh.imaxcam

enum class CaptureMode { PHOTO, VIDEO }

/** Generic label so Photo mode (all still sizes) and Video mode (curated quality tiers) share one dropdown. */
data class ResolutionChoice(val label: String, val size: CaptureSize)

fun stillResolutionChoices(report: CameraReport): List<ResolutionChoice> =
    report.stillSizes.map { ResolutionChoice("${it.width}x${it.height}  (${"%.1f".format(it.megapixels)}MP)", it) }

fun videoResolutionChoices(report: CameraReport): List<ResolutionChoice> =
    report.videoQualities.map { ResolutionChoice("${it.label}  (${it.size.width}x${it.size.height})", it.size) }

/** Derives a human lens label from focal length rank among the cameras actually found on this device — never hardcoded. */
fun lensLabel(all: List<CameraReport>, report: CameraReport): String {
    if (all.size <= 1) return "Main"
    val focals = all.mapNotNull { it.focalLengthMm }
    if (focals.isEmpty() || report.focalLengthMm == null) return "Cam ${report.cameraId}"
    val shortest = focals.min()
    val longest = focals.max()
    return when (report.focalLengthMm) {
        shortest -> "Ultra-wide"
        longest -> if (all.size > 2) "Telephoto" else "Main"
        else -> "Main"
    }
}
