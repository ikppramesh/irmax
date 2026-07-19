package com.ramesh.imaxcam

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun <T> LabeledDropdown(label: String, options: List<Pair<String, T>>, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Text(
            text = label,
            color = Color.White,
            modifier = Modifier
                .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (text, value) ->
                DropdownMenuItem(text = { Text(text) }, onClick = {
                    expanded = false
                    onSelect(value)
                })
            }
        }
    }
}

@Composable
fun ModeToggle(mode: CaptureMode, onChange: (CaptureMode) -> Unit) {
    Row(
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            .padding(2.dp)
    ) {
        CaptureMode.entries.forEach { m ->
            Text(
                text = if (m == CaptureMode.PHOTO) "Photo" else "Video",
                color = if (m == mode) Color.Black else Color.White,
                modifier = Modifier
                    .background(if (m == mode) Color.White else Color.Transparent, RoundedCornerShape(6.dp))
                    .clickable { onChange(m) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
fun RatioToggle(ratio: ImaxRatio, onChange: (ImaxRatio) -> Unit) {
    Row(
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            .padding(2.dp)
    ) {
        ImaxRatio.entries.forEach { r ->
            Text(
                text = r.label,
                color = if (r == ratio) Color.Black else Color.White,
                modifier = Modifier
                    .background(if (r == ratio) Color.White else Color.Transparent, RoundedCornerShape(6.dp))
                    .clickable { onChange(r) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}
