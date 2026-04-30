package com.piashmsu.aichat.thermal

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.piashmsu.aichat.data.prefs.AppPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Reads battery / thermal sensor data and reports whether generation should be paused.
 *
 * Strategy:
 *   - The phone's battery temperature is exposed in deci-Celsius via the
 *     ACTION_BATTERY_CHANGED sticky broadcast — works on every device, no
 *     permissions required.
 *   - Above the configured pause threshold, [shouldPause] flips to true and
 *     stays true until temperature drops below the resume threshold (hysteresis).
 *
 * Phase 4 will additionally:
 *   - Read PowerManager.getCurrentThermalStatus() (API 29+) for SoC-side info.
 *   - Hook RedMagic Game Space intents to bump fan speed during long
 *     generations.
 */
class ThermalGuard(private val context: Context, private val prefs: AppPrefs) {
    private val _temperatureC = MutableStateFlow<Float?>(null)
    val temperatureC: StateFlow<Float?> = _temperatureC.asStateFlow()

    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    fun snapshot(): ThermalSnapshot {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val temp = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val plugged = (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        val tempC = if (temp <= 0) null else temp / 10f
        _temperatureC.value = tempC

        val s = prefs.snapshot()
        if (s.thermalGuardEnabled && tempC != null) {
            if (_paused.value) {
                if (tempC < s.thermalResumeTemp) _paused.value = false
            } else {
                if (tempC > s.thermalPauseTemp) _paused.value = true
            }
        } else {
            _paused.value = false
        }
        return ThermalSnapshot(temperatureC = tempC, paused = _paused.value, plugged = plugged)
    }
}

data class ThermalSnapshot(val temperatureC: Float?, val paused: Boolean, val plugged: Boolean)
