package com.ramesh.imaxcam

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.runtime.LaunchedEffect
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

// Blocking — the app can't function without these.
private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)

// Requested alongside the required ones but never blocks entry — export progress notifications
// are a nice-to-have, not core functionality, and POST_NOTIFICATIONS only exists on API 33+.
private val OPTIONAL_PERMISSIONS =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) arrayOf(Manifest.permission.POST_NOTIFICATIONS)
    else emptyArray()

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
                granted = REQUIRED_PERMISSIONS.all { results[it] == true }
            }

            // Requested independently of the blocking camera/mic flow — if those were already
            // granted from a previous install, the blocking screen below never shows, so this is
            // the only place notifications would otherwise get asked for.
            val optionalLauncher = rememberLauncherForRequiredPermissions { }
            LaunchedEffect(Unit) {
                val missing = OPTIONAL_PERMISSIONS.filter {
                    ContextCompat.checkSelfPermission(this@MainActivity, it) != PackageManager.PERMISSION_GRANTED
                }
                if (missing.isNotEmpty()) optionalLauncher(missing)
            }

            var showCapabilities by remember { mutableStateOf(false) }

            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    if (!granted) {
                        PermissionRequestScreen(onRequest = { permissionLauncher((REQUIRED_PERMISSIONS + OPTIONAL_PERMISSIONS).toList()) })
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
private fun rememberLauncherForRequiredPermissions(onResult: (Map<String, Boolean>) -> Unit): (List<String>) -> Unit {
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
        onResult
    )
    return { permissions -> launcher.launch(permissions.toTypedArray()) }
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
