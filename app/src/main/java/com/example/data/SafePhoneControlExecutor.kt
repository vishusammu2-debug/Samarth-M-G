package com.example.data

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import com.example.ProtonApplication
import com.example.domain.AccessibilityNavAction
import com.example.domain.AssistantLanguage
import com.example.domain.DeviceTelemetry
import com.example.domain.PhoneControlAction
import com.example.domain.SupportedLanguages
import com.example.domain.VolumeDirection
import com.example.service.ProtonAccessibilityService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "SafePhoneControl"

data class ControlExecutionResult(
    val success: Boolean,
    val message: String
)

class SafePhoneControlExecutor(private val context: Context) {

    private val profiler = DevicePerformanceProfiler(context)
    private val router = ProtonCommandRouter()
    private val appLauncher = ProtonAppLauncher(context)

    @Volatile
    private var torchEnabled: Boolean = false

    fun readTelemetry(): DeviceTelemetry {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val batteryPct = if (level >= 0 && scale > 0) ((level * 100) / scale).coerceIn(0, 100) else 100
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNetwork = cm?.activeNetwork
        val caps = cm?.getNetworkCapabilities(activeNetwork)
        val isOnline = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val netLabel = when {
            caps == null -> "OFFLINE"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI ONLINE"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
            else -> if (isOnline) "CONNECTED" else "OFFLINE"
        }

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)
        val ramUsage = if (memInfo.totalMem > 0L) {
            val used = memInfo.totalMem - memInfo.availMem
            ((used * 100L) / memInfo.totalMem).toInt().coerceIn(5, 99)
        } else {
            42
        }

        val capabilities = profiler.inspectCapabilities(batteryPct)

        return DeviceTelemetry(
            batteryPercent = batteryPct,
            isCharging = isCharging,
            isNetworkConnected = isOnline,
            networkLabel = netLabel,
            ramUsagePercent = ramUsage,
            flashlightOn = torchEnabled,
            capabilities = capabilities
        )
    }

    fun execute(
        action: PhoneControlAction,
        language: AssistantLanguage = SupportedLanguages.all.first()
    ): ControlExecutionResult {
        Log.i(TAG, "[EXEC] Starting action=${action.displayLabel}")
        val result = when (action) {
            is PhoneControlAction.ToggleFlashlight -> toggleTorch(action.enable)
            PhoneControlAction.OpenWifiSettings -> launchIntent(
                Intent(Settings.ACTION_WIFI_SETTINGS),
                "Opening Wi-Fi settings."
            )
            PhoneControlAction.OpenBluetoothSettings -> launchIntent(
                Intent(Settings.ACTION_BLUETOOTH_SETTINGS),
                "Opening Bluetooth settings."
            )
            PhoneControlAction.OpenSystemSettings -> launchIntent(
                Intent(Settings.ACTION_SETTINGS),
                "Opening device settings."
            )
            is PhoneControlAction.AdjustVolume -> adjustMediaVolume(action.direction)
            PhoneControlAction.OpenCamera -> launchIntent(
                Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
                "Launching camera."
            )
            is PhoneControlAction.OpenApp -> launchInstalledApp(action.appQuery, action.knownPackages)
            is PhoneControlAction.OpenDialer -> {
                val uri = if (!action.phoneNumber.isNullOrBlank()) {
                    Uri.parse("tel:${Uri.encode(action.phoneNumber)}")
                } else {
                    null
                }
                launchIntent(
                    Intent(Intent.ACTION_DIAL, uri),
                    if (uri != null) "Opening phone dialer for ${action.phoneNumber}." else "Opening phone dialer."
                )
            }
            is PhoneControlAction.SetAlarm -> {
                val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_HOUR, action.hour.coerceIn(0, 23))
                    putExtra(AlarmClock.EXTRA_MINUTES, action.minute.coerceIn(0, 59))
                    putExtra(AlarmClock.EXTRA_MESSAGE, action.label)
                    putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                }
                launchIntent(
                    intent,
                    "Setting alarm for %02d:%02d.".format(action.hour.coerceIn(0, 23), action.minute.coerceIn(0, 59))
                )
            }
            is PhoneControlAction.SetTimer -> {
                val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                    putExtra(AlarmClock.EXTRA_LENGTH, action.seconds.coerceAtLeast(1))
                    putExtra(AlarmClock.EXTRA_MESSAGE, action.label)
                    putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                }
                launchIntent(intent, "Setting timer for ${action.seconds} seconds.")
            }
            is PhoneControlAction.WebSearch -> {
                val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                    putExtra("query", action.query)
                }
                val fallbackUri = Uri.parse("https://www.google.com/search?q=${Uri.encode(action.query)}")
                val pm = context.packageManager
                if (intent.resolveActivity(pm) != null) {
                    launchIntent(intent, "Searching the web for \"${action.query}\".")
                } else {
                    launchIntent(Intent(Intent.ACTION_VIEW, fallbackUri), "Searching the web for \"${action.query}\".")
                }
            }
            is PhoneControlAction.TellTimeDate -> buildTimeDateResult(action.includeDate, language)
            is PhoneControlAction.AccessibilityNavigation -> {
                if (!ProtonAccessibilityService.isServiceEnabled(context)) {
                    ControlExecutionResult(
                        false,
                        "Proton Accessibility Service is not enabled. Open Accessibility Settings in Proton Systems to enable navigation actions."
                    )
                } else {
                    val ok = ProtonAccessibilityService.performUserRequestedAction(action.navAction)
                    if (ok) {
                        ControlExecutionResult(true, "Executed navigation action: ${action.navAction.label}.")
                    } else {
                        ControlExecutionResult(false, "Could not perform ${action.navAction.label} on this Android version.")
                    }
                }
            }
            is PhoneControlAction.ToggleReelsMode -> {
                ControlExecutionResult(false, "Reels Mode is managed through PROTON PREMIUM entitlement gating.")
            }
        }
        Log.i(TAG, "[EXEC] Completed action=${action.displayLabel} success=${result.success} msg=\"${result.message}\"")
        return result
    }

    private fun buildTimeDateResult(includeDate: Boolean, language: AssistantLanguage): ControlExecutionResult {
        val now = Date()
        val timeText = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(now)
        val dateText = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(now)
        val msg = when (language.id) {
            "kn-UK" -> if (includeDate) {
                "ಇವತ್ತು $dateText, ಈಗ ಟೈಮ್ $timeText ಆಗಿದೆ ಮಾರಾಯ."
            } else {
                "ಈಗ ಟೈಮ್ $timeText ಆಗಿದೆ ಮಾರಾಯ."
            }
            "kn-IN" -> if (includeDate) {
                "ಇಂದಿನ ದಿನಾಂಕ $dateText ಮತ್ತು ಪ್ರಸ್ತುತ ಸಮಯ $timeText ಆಗಿದೆ."
            } else {
                "ಪ್ರಸ್ತುತ ಸಮಯ $timeText ಆಗಿದೆ."
            }
            "hi-IN" -> if (includeDate) {
                "आज की तारीख $dateText है और अभी समय $timeText हुआ है।"
            } else {
                "अभी समय $timeText हुआ है।"
            }
            else -> if (includeDate) {
                "Today is $dateText and the current time is $timeText."
            } else {
                "The current time is $timeText."
            }
        }
        return ControlExecutionResult(true, msg)
    }

    private fun launchInstalledApp(appQuery: String, knownPackages: List<String>): ControlExecutionResult {
        val launchResult = appLauncher.launchApplication(appQuery, knownPackages)
        return ControlExecutionResult(
            success = launchResult.success,
            message = launchResult.message
        )
    }

    private fun toggleTorch(targetState: Boolean?): ControlExecutionResult {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            if (cameraId == null) {
                ControlExecutionResult(false, "No hardware flashlight found on this device.")
            } else {
                val next = targetState ?: !torchEnabled
                cameraManager.setTorchMode(cameraId, next)
                torchEnabled = next
                ControlExecutionResult(true, if (next) "Flashlight turned on." else "Flashlight turned off.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Torch toggle failed", e)
            ControlExecutionResult(false, "Unable to toggle flashlight: ${e.localizedMessage ?: "hardware unavailable"}")
        }
    }

    private fun adjustMediaVolume(direction: VolumeDirection): ControlExecutionResult {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val adjustFlag = when (direction) {
                VolumeDirection.UP -> AudioManager.ADJUST_RAISE
                VolumeDirection.DOWN -> AudioManager.ADJUST_LOWER
                VolumeDirection.MUTE -> AudioManager.ADJUST_MUTE
            }
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                adjustFlag,
                AudioManager.FLAG_SHOW_UI
            )
            val msg = when (direction) {
                VolumeDirection.UP -> "Increased media volume."
                VolumeDirection.DOWN -> "Decreased media volume."
                VolumeDirection.MUTE -> "Muted media volume."
            }
            ControlExecutionResult(true, msg)
        } catch (e: Exception) {
            Log.e(TAG, "Volume adjustment failed", e)
            ControlExecutionResult(false, "Could not adjust volume: ${e.localizedMessage ?: "error"}")
        }
    }

    private fun launchIntent(intent: Intent, successMessage: String): ControlExecutionResult {
        return try {
            val launchCtx = ProtonApplication.currentResumedActivity() ?: context
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            if (intent.resolveActivity(context.packageManager) != null) {
                launchCtx.startActivity(intent)
                Log.i(TAG, "[INTENT_LAUNCH] Accepted action=${intent.action}")
                ControlExecutionResult(true, successMessage)
            } else {
                Log.w(TAG, "[INTENT_LAUNCH] No activity resolved for action=${intent.action}")
                ControlExecutionResult(false, "No supported application found on this device to handle that action.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "[INTENT_LAUNCH] Launch failed for action=${intent.action}", e)
            ControlExecutionResult(false, "Action failed: ${e.localizedMessage ?: "unavailable"}")
        }
    }

    fun parseDirectCommand(input: String): PhoneControlAction? = router.classifyLocalIntent(input)

    fun parseDirectiveTag(tagContent: String): PhoneControlAction? {
        val parts = tagContent.trim().split(":")
        val key = parts.firstOrNull()?.uppercase() ?: return null
        val arg = parts.getOrNull(1)?.trim().orEmpty()
        return when (key) {
            "FLASHLIGHT_ON" -> PhoneControlAction.ToggleFlashlight(true)
            "FLASHLIGHT_OFF" -> PhoneControlAction.ToggleFlashlight(false)
            "WIFI_SETTINGS" -> PhoneControlAction.OpenWifiSettings
            "BLUETOOTH_SETTINGS" -> PhoneControlAction.OpenBluetoothSettings
            "SYSTEM_SETTINGS" -> PhoneControlAction.OpenSystemSettings
            "VOLUME_UP" -> PhoneControlAction.AdjustVolume(VolumeDirection.UP)
            "VOLUME_DOWN" -> PhoneControlAction.AdjustVolume(VolumeDirection.DOWN)
            "VOLUME_MUTE" -> PhoneControlAction.AdjustVolume(VolumeDirection.MUTE)
            "OPEN_CAMERA" -> PhoneControlAction.OpenCamera
            "OPEN_APP" -> if (arg.isNotBlank()) {
                val cleaned = ProtonAppLauncher.cleanAppQuery(arg)
                when (cleaned.lowercase(Locale.ROOT)) {
                    "camera" -> PhoneControlAction.OpenCamera
                    "settings", "device settings", "system settings" -> PhoneControlAction.OpenSystemSettings
                    "wifi settings", "wi-fi settings" -> PhoneControlAction.OpenWifiSettings
                    "bluetooth settings" -> PhoneControlAction.OpenBluetoothSettings
                    else -> {
                        val known = when (cleaned.lowercase(Locale.ROOT)) {
                            "youtube" -> listOf("com.google.android.youtube")
                            "chrome", "google chrome" -> listOf("com.android.chrome")
                            "whatsapp" -> listOf("com.whatsapp", "com.whatsapp.w4b")
                            "instagram" -> listOf("com.instagram.android")
                            "gmail" -> listOf("com.google.android.gm")
                            "play store", "google play", "google play store" -> listOf("com.android.vending")
                            else -> emptyList()
                        }
                        PhoneControlAction.OpenApp(cleaned, known)
                    }
                }
            } else null
            "OPEN_DIALER" -> PhoneControlAction.OpenDialer(arg.ifBlank { null })
            "SET_TIMER" -> PhoneControlAction.SetTimer(arg.toIntOrNull() ?: 60)
            "SET_ALARM" -> {
                val hm = arg.split(",")
                val h = hm.getOrNull(0)?.toIntOrNull() ?: 7
                val m = hm.getOrNull(1)?.toIntOrNull() ?: 0
                PhoneControlAction.SetAlarm(h, m)
            }
            "WEB_SEARCH" -> if (arg.isNotBlank()) PhoneControlAction.WebSearch(arg) else null
            "TELL_TIME" -> PhoneControlAction.TellTimeDate(includeDate = true)
            "NAV_BACK" -> PhoneControlAction.AccessibilityNavigation(AccessibilityNavAction.BACK)
            "NAV_HOME" -> PhoneControlAction.AccessibilityNavigation(AccessibilityNavAction.HOME)
            "NAV_RECENTS" -> PhoneControlAction.AccessibilityNavigation(AccessibilityNavAction.RECENTS)
            "OPEN_NOTIFICATIONS" -> PhoneControlAction.AccessibilityNavigation(AccessibilityNavAction.NOTIFICATIONS)
            "QUICK_SETTINGS" -> PhoneControlAction.AccessibilityNavigation(AccessibilityNavAction.QUICK_SETTINGS)
            "TAKE_SCREENSHOT" -> PhoneControlAction.AccessibilityNavigation(AccessibilityNavAction.TAKE_SCREENSHOT)
            else -> null
        }
    }
}
