package com.ramesh.imaxcam

import android.widget.Toast
import androidx.camera.core.ImageCapture
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/** Bottom-right watermark opacity while shooting, per product requirement. */
private const val WATERMARK_ALPHA = 0.3f

@Composable
fun CaptureScreen(onShowCapabilities: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var reports by remember { mutableStateOf<List<CameraReport>>(emptyList()) }
    var selectedCameraId by remember { mutableStateOf<String?>(null) }
    var mode by remember { mutableStateOf(CaptureMode.PHOTO) }
    var selectedResolution by remember { mutableStateOf<CaptureSize?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    val rollDegrees = rememberRollDegrees()

    LaunchedEffect(Unit) {
        reports = CameraCapabilities.probeBackCameras(context)
        val best = reports.firstOrNull()
        selectedCameraId = best?.cameraId
        selectedResolution = best?.maxStill
    }

    val selectedReport = reports.firstOrNull { it.cameraId == selectedCameraId }

    // Switching modes swaps which size list is relevant; default to that mode's max on first entry.
    LaunchedEffect(mode, selectedReport) {
        val r = selectedReport ?: return@LaunchedEffect
        selectedResolution = when (mode) {
            CaptureMode.PHOTO -> r.maxStill
            CaptureMode.VIDEO -> r.maxVideo?.size
        }
    }

    val stillTargetSize = if (mode == CaptureMode.PHOTO) selectedResolution else selectedReport?.maxStill
    val videoQualityLabel = selectedReport?.videoQualities?.firstOrNull { it.size == selectedResolution }?.label

    Box(modifier = Modifier.fillMaxSize()) {
        selectedCameraId?.let { camId ->
            if (mode == CaptureMode.PHOTO) {
                key(camId, stillTargetSize) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            PreviewView(ctx).also { previewView ->
                                previewView.scaleType = PreviewView.ScaleType.FIT_CENTER
                                // COMPATIBLE (TextureView-backed) instead of the CameraX default
                                // PERFORMANCE (SurfaceView-backed) — SurfaceView composites via a
                                // separate hardware layer that can swallow touches meant for
                                // Compose overlays drawn on top of it.
                                previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                CameraEngine(ctx).bindPhoto(
                                    lifecycleOwner, previewView, camId, stillTargetSize
                                ) { capture -> imageCapture = capture }
                            }
                        }
                    )
                }
            } else {
                key(camId, videoQualityLabel) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            PreviewView(ctx).also { previewView ->
                                previewView.scaleType = PreviewView.ScaleType.FIT_CENTER
                                previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                CameraEngine(ctx).bindVideo(
                                    lifecycleOwner, previewView, camId, videoQualityLabel
                                ) { capture -> videoCapture = capture }
                            }
                        }
                    )
                }
            }
        }

        selectedResolution?.let { res ->
            FramingOverlay(captureRatio = res.ratio, modifier = Modifier.fillMaxSize())
        }

        LevelIndicator(rollDegrees = rollDegrees, modifier = Modifier.fillMaxSize())

        Image(
            painter = painterResource(id = R.drawable.irmax_watermark),
            contentDescription = null,
            alpha = WATERMARK_ALPHA,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .height(40.dp)
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            if (selectedReport != null) {
                val lensOptions = reports.map { r -> lensLabel(reports, r) to r.cameraId }
                val resolutionOptions = when (mode) {
                    CaptureMode.PHOTO -> stillResolutionChoices(selectedReport).map { it.label to it.size }
                    CaptureMode.VIDEO -> videoResolutionChoices(selectedReport).map { it.label to it.size }
                }
                val resLabel = selectedResolution?.let { s -> "${s.width}x${s.height}" } ?: "-"

                ControlsBar(
                    lensLabel = lensLabel(reports, selectedReport),
                    lensOptions = lensOptions,
                    // Switching lens/resolution/mode mid-recording tears down the bound camera
                    // use cases out from under the active Recording — ignore those taps while
                    // recording instead of risking a crash mid-capture.
                    onLensSelect = { id -> if (!isRecording) selectedCameraId = id },
                    resolutionLabel = resLabel,
                    resolutionOptions = resolutionOptions,
                    onResolutionSelect = { size -> if (!isRecording) selectedResolution = size },
                    mode = mode,
                    onModeChange = { if (!isRecording) mode = it },
                )
            }
        }

        IconButton(
            onClick = onShowCapabilities,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(12.dp)
        ) {
            Text("ⓘ", color = Color.White)
        }

        if (mode == CaptureMode.PHOTO) {
            ShutterButton(
                onClick = {
                    val capture = imageCapture ?: return@ShutterButton
                    takePhoto(
                        context, capture,
                        onSaved = { file -> Toast.makeText(context, "Saved ${file.name}", Toast.LENGTH_SHORT).show() },
                        onError = { e -> Toast.makeText(context, "Capture failed: ${e.message}", Toast.LENGTH_SHORT).show() }
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
                            context, capture,
                            onFinalized = { file ->
                                isRecording = false
                                Toast.makeText(context, "Saved ${file.name}", Toast.LENGTH_SHORT).show()
                            },
                            onError = { e ->
                                isRecording = false
                                Toast.makeText(context, "Recording failed: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        )
                        isRecording = true
                    } else {
                        activeRecording?.stop()
                        activeRecording = null
                        isRecording = false
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter).padding(28.dp)
            )
        }
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
