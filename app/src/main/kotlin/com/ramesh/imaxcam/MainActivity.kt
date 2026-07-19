package com.ramesh.imaxcam

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)

class MainActivity : ComponentActivity() {

    private fun hasAllPermissions() =
        REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var granted by remember { mutableStateOf(hasAllPermissions()) }

            val permissionLauncher = rememberLauncherForRequiredPermissions { results ->
                granted = results.values.all { it }
            }

            var showCapabilities by remember { mutableStateOf(false) }

            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    if (!granted) {
                        PermissionRequestScreen(onRequest = { permissionLauncher() })
                    } else if (showCapabilities) {
                        CapabilitiesScreen(onBack = { showCapabilities = false })
                    } else {
                        CaptureScreen(onShowCapabilities = { showCapabilities = true })
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberLauncherForRequiredPermissions(onResult: (Map<String, Boolean>) -> Unit): () -> Unit {
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
        onResult
    )
    return { launcher.launch(REQUIRED_PERMISSIONS) }
}

@Composable
private fun PermissionRequestScreen(onRequest: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.irmax_watermark),
                contentDescription = "IRMAX",
                modifier = Modifier.height(100.dp)
            )
            Text("IRMAX needs camera + microphone access to shoot.", color = Color.White)
            Button(onClick = onRequest) {
                Text("Grant permissions")
            }
        }
    }
}
