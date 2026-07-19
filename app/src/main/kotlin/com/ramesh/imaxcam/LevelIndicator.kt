package com.ramesh.imaxcam

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.VibrationEffect
import android.os.VibratorManager
import android.view.Surface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import kotlin.math.abs

private const val LEVEL_THRESHOLD_DEGREES = 1.5f

/**
 * Roll angle in degrees (0 = level), using the rotation-vector sensor remapped for whichever
 * landscape rotation the display is actually in — this app is locked to landscape, but that can
 * mean either physical rotation depending on which way the phone is held, and the remap keeps the
 * reading correct either way rather than assuming one.
 */
@Composable
fun rememberRollDegrees(): Float {
    val context = LocalContext.current
    val view = LocalView.current
    var roll by remember { mutableFloatStateOf(0f) }

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val vibrator = (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        val rotationMatrix = FloatArray(9)
        val remappedMatrix = FloatArray(9)
        val orientation = FloatArray(3)
        var wasLevel = false

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

                val displayRotation = view.display?.rotation ?: Surface.ROTATION_0
                val (axisX, axisY) = when (displayRotation) {
                    Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                    Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                    else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                }
                SensorManager.remapCoordinateSystem(rotationMatrix, axisX, axisY, remappedMatrix)
                SensorManager.getOrientation(remappedMatrix, orientation)
                roll = Math.toDegrees(orientation[2].toDouble()).toFloat()

                // Haptic tick on the moment the phone crosses INTO level, not continuously while level.
                val nowLevel = abs(roll) < LEVEL_THRESHOLD_DEGREES
                if (nowLevel && !wasLevel) {
                    vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
                }
                wasLevel = nowLevel
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        if (rotationSensor != null) {
            sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose { sensorManager.unregisterListener(listener) }
    }

    return roll
}

/** A simple artificial-horizon bubble level: a fixed reference tick plus a line that rotates with
 * the phone's tilt and turns green when within [LEVEL_THRESHOLD_DEGREES] of level. */
@Composable
fun LevelIndicator(rollDegrees: Float, modifier: Modifier = Modifier) {
    val isLevel = abs(rollDegrees) < LEVEL_THRESHOLD_DEGREES
    val activeColor = if (isLevel) Color(0xFF4CAF50) else Color.White

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)

        // Fixed reference tick — always horizontal, marks true level in screen space.
        val fixedHalfWidth = 24f
        drawLine(
            Color.White.copy(alpha = 0.6f),
            Offset(center.x - fixedHalfWidth, center.y),
            Offset(center.x + fixedHalfWidth, center.y),
            strokeWidth = 2f
        )

        // Rotating horizon line — rotates opposite the phone's roll so it reads level when the
        // phone itself is level (matches the fixed tick above).
        rotate(degrees = -rollDegrees, pivot = center) {
            val lineHalfWidth = 100f
            drawLine(
                activeColor,
                Offset(center.x - lineHalfWidth, center.y),
                Offset(center.x + lineHalfWidth, center.y),
                strokeWidth = 4f
            )
            drawCircle(activeColor, radius = 5f, center = center)
        }
    }
}
