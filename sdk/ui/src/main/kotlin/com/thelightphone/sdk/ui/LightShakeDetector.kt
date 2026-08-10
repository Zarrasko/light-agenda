package com.thelightphone.sdk.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.milliseconds

private const val SHAKE_THRESHOLD_G = 2.7f
private const val SHAKE_COOLDOWN_MS = 800L
private val SHAKE_HAPTIC_DURATION = 60.milliseconds

/**
 * Fires [onShake] when the device is shaken, with a haptic pulse (respecting
 * the user's haptics preference) to confirm the shake registered. Wraps
 * [SensorManager] internally so tool code never has to touch a raw [Context]
 * or system service.
 */
@Composable
fun LightShakeDetector(onShake: () -> Unit) {
    val context = LocalContext.current
    val currentOnShake by rememberUpdatedState(onShake)
    val hapticsEnabled by rememberUpdatedState(LocalHapticsEnabled.current)

    DisposableEffect(context) {
        val sensorManager = context.getSystemService(SensorManager::class.java)
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (sensorManager == null || accelerometer == null) {
            return@DisposableEffect onDispose {}
        }

        var lastShakeAtMs = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val gX = event.values[0] / SensorManager.GRAVITY_EARTH
                val gY = event.values[1] / SensorManager.GRAVITY_EARTH
                val gZ = event.values[2] / SensorManager.GRAVITY_EARTH
                val gForce = sqrt(gX * gX + gY * gY + gZ * gZ)

                if (gForce > SHAKE_THRESHOLD_G) {
                    val now = System.currentTimeMillis()
                    if (now - lastShakeAtMs > SHAKE_COOLDOWN_MS) {
                        lastShakeAtMs = now
                        if (hapticsEnabled) {
                            LightHapticFeedback.vibrateForDuration(context, SHAKE_HAPTIC_DURATION)
                        }
                        currentOnShake()
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }
}
