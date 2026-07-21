package com.ramesh.imaxcam

import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.view.View
import android.widget.Toast
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay

@Composable
fun CaptureScreen(onShowCapabilities: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var reports by remember { mutableStateOf<List<CameraReport>>(emptyList()) }
    var selectedFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var selectedCameraId by remember { mutableStateOf<String?>(null) }
    var mode by remember { mutableStateOf(CaptureMode.PHOTO) }
    var selectedRatio by remember { mutableStateOf(ImaxRatio.IMAX_GT) }
    var selectedFilter by remember { mutableStateOf(CameraFilter.NONE) }
    var selectedResolution by remember { mutableStateOf<CaptureSize?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var activeRecording by remember { mutableStateOf<VideoRecordingHandle?>(null) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var isTorchOn by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var recordingElapsedSec by remember { mutableStateOf(0) }
    var recordingSizeBytes by remember { mutableStateOf(0L) }
    val rollDegrees = rememberRollDegrees()

    // Re-probe whenever the facing (front/back) flips — front and back expose entirely different
    // lens/resolution sets, so "best resolution available" has to be re-discovered, not assumed.
    LaunchedEffect(selectedFacing) {
        reports = CameraCapabilities.probeCameras(context, selectedFacing)
        val best = reports.firstOrNull()
        selectedCameraId = best?.cameraId
        selectedResolution = best?.maxStill
        isTorchOn = false
    }

    val selectedReport = reports.firstOrNull { it.cameraId == selectedCameraId }
    val hasFlash = boundCamera?.cameraInfo?.hasFlashUnit() == true

    // Switching modes swaps which size list is relevant; default to that mode's max on first entry.
    LaunchedEffect(mode, selectedReport) {
        val r = selectedReport ?: return@LaunchedEffect
        selectedResolution = when (mode) {
            CaptureMode.PHOTO -> r.maxStill
            CaptureMode.VIDEO -> r.maxVideo?.size
        }
    }

    // While recording, tick the elapsed time and poll the raw file's growing size once a second.
    LaunchedEffect(isRecording) {
        if (!isRecording) {
            recordingElapsedSec = 0
            recordingSizeBytes = 0L
            return@LaunchedEffect
        }
        val startMs = System.currentTimeMillis()
        while (isRecording) {
            recordingElapsedSec = ((System.currentTimeMillis() - startMs) / 1000).toInt()
            recordingSizeBytes = activeRecording?.rawFile?.length() ?: 0L
            delay(1000)
        }
    }

    val stillTargetSize = if (mode == CaptureMode.PHOTO) selectedResolution else selectedReport?.maxStill
    val videoQualityLabel = selectedReport?.videoQualities?.firstOrNull { it.size == selectedResolution }?.label

    Box(modifier = Modifier.fillMaxSize()) {
        selectedCameraId?.let { camId ->
            if (mode == CaptureMode.PHOTO) {
                key(camId, stillTargetSize, selectedFacing) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            PreviewView(ctx).also { previewView ->
                                previewView.scaleType = PreviewView.ScaleType.FIT_CENTER
                                // COMPATIBLE (TextureView-backed) instead of the CameraX default
                                // PERFORMANCE (SurfaceView-backed) — SurfaceView composites via a
                                // separate hardware layer that can swallow touches meant for
                                // Compose overlays drawn on top of it, and also can't take the
                                // Paint-based color filter used for the live filter preview below.
                                previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                CameraEngine(ctx).bindPhoto(
                                    lifecycleOwner, previewView, camId, selectedFacing, stillTargetSize,
                                    onImageCaptureReady = { capture -> imageCapture = capture },
                                    onCameraReady = { camera -> boundCamera = camera },
                                )
                            }
                        },
                        update = { previewView -> applyFilterLayer(previewView, selectedFilter) }
                    )
                }
            } else {
                key(camId, videoQualityLabel, selectedFacing) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            PreviewView(ctx).also { previewView ->
                                previewView.scaleType = PreviewView.ScaleType.FIT_CENTER
                                previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                CameraEngine(ctx).bindVideo(
                                    lifecycleOwner, previewView, camId, selectedFacing, videoQualityLabel,
                                    onVideoCaptureReady = { capture -> videoCapture = capture },
                                    onCameraReady = { camera -> boundCamera = camera },
                                )
                            }
                        },
                        update = { previewView -> applyFilterLayer(previewView, selectedFilter) }
                    )
                }
            }
        }

        selectedResolution?.let { res ->
            FramingOverlay(
                captureRatio = res.ratio,
                targetRatio = selectedRatio.value,
                ratioLabel = selectedRatio.label,
                modifier = Modifier.fillMaxSize()
            )
        }

        LevelIndicator(rollDegrees = rollDegrees, modifier = Modifier.fillMaxSize())

        // All controls live in the left pillarbox margin, stacked vertically, so nothing ever
        // sits on top of the actual preview content and the right margin stays clear.
        if (selectedReport != null) {
            val lensOptions = reports.map { r -> lensLabel(reports, r, selectedFacing) to r.cameraId }
            val resolutionOptions = when (mode) {
                CaptureMode.PHOTO -> stillResolutionChoices(selectedReport).map { it.label to it.size }
                CaptureMode.VIDEO -> videoResolutionChoices(selectedReport).map { it.label to it.size }
            }
            val resLabel = selectedResolution?.let { s -> "${s.width}x${s.height}" } ?: "-"

            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row {
                    if (hasFlash) {
                        IconButton(onClick = { if (!isRecording) { isTorchOn = !isTorchOn; boundCamera?.cameraControl?.enableTorch(isTorchOn) } }) {
                            Text(if (isTorchOn) "🔦" else "🔅", color = Color.White)
                        }
                    }
                    IconButton(onClick = {
                        if (!isRecording) {
                            // Turn off torch before the old session unbinds — otherwise an in-flight
                            // enableTorch call can race the rebind and throw inside CameraX's internal
                            // executor (harmless there, but worth avoiding).
                            if (isTorchOn) {
                                boundCamera?.cameraControl?.enableTorch(false)
                                isTorchOn = false
                            }
                            // Clear the id synchronously so the AndroidView's key() never sees a stale
                            // camera id paired with the new facing in the same recomposition — otherwise
                            // CameraX briefly gets asked for "this id, but that facing" (impossible) before
                            // the LaunchedEffect re-probe catches up.
                            selectedCameraId = null
                            selectedFacing = if (selectedFacing == CameraSelector.LENS_FACING_BACK)
                                CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                        }
                    }) {
                        Text("🔄", color = Color.White, fontSize = 28.sp)
                    }
                    IconButton(onClick = onShowCapabilities) {
                        Text("ⓘ", color = Color.White)
                    }
                }
                LabeledDropdown(
                    label = "Ratio: ${selectedRatio.label}",
                    options = ImaxRatio.entries.map { "${it.short} (${it.label})" to it },
                    onSelect = { r -> if (!isRecording) selectedRatio = r }
                )
                ModeToggle(mode = mode, onChange = { if (!isRecording) mode = it })
                LabeledDropdown(
                    label = "Lens: ${lensLabel(reports, selectedReport, selectedFacing)}",
                    options = lensOptions,
                    onSelect = { id -> if (!isRecording) selectedCameraId = id }
                )
                LabeledDropdown(
                    label = "Res: $resLabel",
                    options = resolutionOptions,
                    onSelect = { size -> if (!isRecording) selectedResolution = size }
                )
                LabeledDropdown(
                    label = "Filter: ${selectedFilter.label}",
                    options = CameraFilter.entries.map { it.label to it },
                    onSelect = { f -> if (!isRecording) selectedFilter = f }
                )
            }
        }

        if (isRecording) {
            RecordingStatus(
                elapsedSec = recordingElapsedSec,
                sizeBytes = recordingSizeBytes,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 76.dp)
            )
        }

        if (isExporting) {
            ExportingIndicator(modifier = Modifier.align(Alignment.Center))
        }

        if (mode == CaptureMode.PHOTO) {
            ShutterButton(
                onClick = {
                    val capture = imageCapture ?: return@ShutterButton
                    takePhoto(
                        context, capture, selectedRatio, selectedFilter, scope,
                        onExportStart = { isExporting = true },
                        onSaved = { file ->
                            isExporting = false
                            Toast.makeText(context, "Saved ${file.name}", Toast.LENGTH_SHORT).show()
                        },
                        onError = { e ->
                            isExporting = false
                            Toast.makeText(context, "Capture failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    )
                },
                modifier = Modifier.align(Alignment.BottomCenter).padding(28.dp)
            )
        } else {
            RecordButton(
                isRecording = isRecording,
                onToggle = {
                    if (!isRecording) {
                        val capture = videoCapture ?: return@RecordButton
                        activeRecording = startVideoRecording(
                            context, capture, selectedRatio, selectedFilter,
                            onExportStart = { isExporting = true },
                            onFinalized = { file ->
                                isExporting = false
                                Toast.makeText(context, "Saved ${file.name}", Toast.LENGTH_SHORT).show()
                            },
                            onError = { e ->
                                isExporting = false
                                Toast.makeText(context, "Recording failed: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        )
                        isRecording = true
                    } else {
                        activeRecording?.recording?.stop()
                        activeRecording = null
                        isRecording = false
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter).padding(28.dp)
            )
        }
    }
}

/** Applies the selected color filter to the live preview via a hardware layer Paint — TextureView
 * (PreviewView's COMPATIBLE mode) is a regular View, so this composites correctly, unlike SurfaceView. */
private fun applyFilterLayer(previewView: PreviewView, filter: CameraFilter) {
    if (filter == CameraFilter.NONE) {
        previewView.setLayerType(View.LAYER_TYPE_NONE, null)
    } else {
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(androidColorMatrixFor(filter)) }
        previewView.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
    }
}

@Composable
private fun RecordingStatus(elapsedSec: Int, sizeBytes: Long, modifier: Modifier = Modifier) {
    val minutes = elapsedSec / 60
    val seconds = elapsedSec % 60
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            "%02d:%02d · %s".format(minutes, seconds, sizeLabel(sizeBytes)),
            color = Color.White
        )
    }
}

@Composable
private fun ExportingIndicator(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = Color.White)
        Text("Exporting…", color = Color.White, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun ShutterButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(72.dp)
            .border(4.dp, Color.White, CircleShape)
            .padding(6.dp)
            .background(Color.White, CircleShape)
            .clickable { onClick() }
    )
}

@Composable
private fun RecordButton(isRecording: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(72.dp)
            .border(4.dp, Color.White, CircleShape)
            .padding(6.dp)
            .background(if (isRecording) Color.Red else Color.White, CircleShape)
            .clickable { onToggle() }
    )
}
