package com.ramesh.imaxcam

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun CapabilitiesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var reports by remember { mutableStateOf<List<CameraReport>>(emptyList()) }
    var probed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        reports = CameraCapabilities.probeBackCameras(context)
        probed = true
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("Back-facing cameras (highest resolution first)", color = Color.White)
            Button(onClick = onBack, modifier = Modifier.padding(vertical = 8.dp)) {
                Text("Back to viewfinder")
            }

            if (!probed) {
                Text("Probing...", color = Color.Gray)
            } else if (reports.isEmpty()) {
                Text("No back-facing camera found.", color = Color.Red)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    reports.forEachIndexed { index, r ->
                        item {
                            Text("")
                            Text(
                                "Camera id=${r.cameraId}${if (index == 0) "  ← highest resolution" else ""}",
                                color = if (index == 0) Color.Green else Color.White
                            )
                            Text("  Focal length: ${r.focalLengthMm ?: "?"}mm", color = Color.Gray)
                            Text("  Video qualities: ${r.videoQualities.joinToString { it.label }}", color = Color.Yellow)
                        }
                        items(r.stillSizes) { s ->
                            Text("    $s", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
