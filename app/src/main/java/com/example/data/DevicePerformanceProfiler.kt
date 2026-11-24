package com.example.data

import android.app.ActivityManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import android.view.Display
import com.example.domain.DeviceCapabilitiesReport
import com.example.domain.PerformanceProfileMode
import com.example.domain.ResolvedPerformanceTier

private const val TAG = "DeviceProfiler"

class DevicePerformanceProfiler(private val context: Context) {

    fun inspectCapabilities(batteryPercent: Int): DeviceCapabilitiesReport {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)

        val totalRamMb = (memInfo.totalMem / (1024L * 1024L)).toInt().coerceAtLeast(512)
        val availRamMb = (memInfo.availMem / (1024L * 1024L)).toInt().coerceAtLeast(64)
        val isLowRamDevice = am?.isLowRamDevice == true || memInfo.lowMemory
        val cpuCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

        val refreshRateHz = readDisplayRefreshRateHz()

        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isPowerSave = pm?.isPowerSaveMode == true

        val (thermalLabel, thermalThrottled) = readThermalState(pm)

        val recommended = computeRecommendedTier(
            totalRamMb = totalRamMb,
            availRamMb = availRamMb,
            cpuCores = cpuCores,
            refreshRateHz = refreshRateHz,
            batteryPercent = batteryPercent,
            isLowRamDevice = isLowRamDevice,
            isPowerSaveMode = isPowerSave,
            isThermalThrottled = thermalThrottled
        )

        return DeviceCapabilitiesReport(
            totalRamMb = totalRamMb,
            availRamMb = availRamMb,
            cpuCores = cpuCores,
            refreshRateHz = refreshRateHz,
            isLowRamDevice = isLowRamDevice,
            isPowerSaveMode = isPowerSave,
            thermalStatusLabel = thermalLabel,
            isThermalThrottled = thermalThrottled,
            recommendedTier = recommended
        )
    }

    fun resolveActiveTier(
        mode: PerformanceProfileMode,
        report: DeviceCapabilitiesReport,
        memoryPressureActive: Boolean
    ): ResolvedPerformanceTier {
        if (memoryPressureActive || report.isThermalThrottled) {
            return ResolvedPerformanceTier.LOW_END
        }
        return when (mode) {
            PerformanceProfileMode.AUTO -> report.recommendedTier
            PerformanceProfileMode.LOW_END -> ResolvedPerformanceTier.LOW_END
            PerformanceProfileMode.BALANCED -> ResolvedPerformanceTier.BALANCED
            PerformanceProfileMode.HIGH_PERFORMANCE -> ResolvedPerformanceTier.HIGH_PERFORMANCE
        }
    }

    private fun readDisplayRefreshRateHz(): Int {
        return try {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            val display = dm?.getDisplay(Display.DEFAULT_DISPLAY)
            display?.refreshRate?.toInt()?.coerceIn(30, 240) ?: 60
        } catch (e: Exception) {
            Log.w(TAG, "Unable to read display refresh rate", e)
            60
        }
    }

    private fun readThermalState(pm: PowerManager?): Pair<String, Boolean> {
        if (pm == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return "NORMAL" to false
        }
        return try {
            when (pm.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE,
                PowerManager.THERMAL_STATUS_LIGHT -> "COOL" to false
                PowerManager.THERMAL_STATUS_MODERATE -> "WARM" to false
                PowerManager.THERMAL_STATUS_SEVERE -> "SEVERE" to true
                PowerManager.THERMAL_STATUS_CRITICAL,
                PowerManager.THERMAL_STATUS_EMERGENCY,
                PowerManager.THERMAL_STATUS_SHUTDOWN -> "CRITICAL" to true
                else -> "NORMAL" to false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to read thermal status", e)
            "NORMAL" to false
        }
    }

    companion object {
        fun computeRecommendedTier(
            totalRamMb: Int,
            availRamMb: Int,
            cpuCores: Int,
            refreshRateHz: Int,
            batteryPercent: Int,
            isLowRamDevice: Boolean,
            isPowerSaveMode: Boolean,
            isThermalThrottled: Boolean
        ): ResolvedPerformanceTier {
            if (isLowRamDevice || isPowerSaveMode || isThermalThrottled ||
                totalRamMb <= 3200 || availRamMb <= 450 || batteryPercent <= 15
            ) {
                return ResolvedPerformanceTier.LOW_END
            }
            if (totalRamMb >= 7500 && cpuCores >= 8 && refreshRateHz >= 90 && availRamMb >= 1600) {
                return ResolvedPerformanceTier.HIGH_PERFORMANCE
            }
            return ResolvedPerformanceTier.BALANCED
        }
    }
}
