package com.example.domain

data class AssistantLanguage(
    val id: String,
    val code: String,
    val bcp47Tag: String,
    val englishName: String,
    val nativeName: String,
    val dialectPromptHint: String? = null
)

object SupportedLanguages {
    val all: List<AssistantLanguage> = listOf(
        AssistantLanguage("en-US", "EN-US", "en-US", "English", "English"),
        AssistantLanguage("hi-IN", "HI-IN", "hi-IN", "Hindi", "हिन्दी"),
        AssistantLanguage("kn-IN", "KN-IN", "kn-IN", "Kannada", "ಕನ್ನಡ"),
        AssistantLanguage(
            id = "kn-UK",
            code = "KN-UK",
            bcp47Tag = "kn-IN",
            englishName = "Uttara Kannada",
            nativeName = "ಉತ್ತರ ಕನ್ನಡದ ಆಡುಭಾಷೆ",
            dialectPromptHint = "Respond in authentic Uttara Kannada (Karwar, Sirsi, Kumta, Honnavar, Siddapur) colloquial spoken Kannada dialect using natural regional vocabulary and warm coastal/Malnad phrasing, written in clean Kannada script for speech playback."
        ),
        AssistantLanguage("es-ES", "ES-ES", "es-ES", "Spanish", "Español"),
        AssistantLanguage("fr-FR", "FR-FR", "fr-FR", "French", "Français"),
        AssistantLanguage("de-DE", "DE-DE", "de-DE", "German", "Deutsch"),
        AssistantLanguage("ja-JP", "JA-JP", "ja-JP", "Japanese", "日本語"),
        AssistantLanguage("ko-KR", "KO-KR", "ko-KR", "Korean", "한국어"),
        AssistantLanguage("zh-CN", "ZH-CN", "zh-CN", "Chinese", "中文"),
        AssistantLanguage("ar-SA", "AR-SA", "ar-SA", "Arabic", "العربية"),
        AssistantLanguage("pt-BR", "PT-BR", "pt-BR", "Portuguese", "Português"),
        AssistantLanguage("ru-RU", "RU-RU", "ru-RU", "Russian", "Русский"),
        AssistantLanguage("it-IT", "IT-IT", "it-IT", "Italian", "Italiano"),
        AssistantLanguage("ta-IN", "TA-IN", "ta-IN", "Tamil", "தமிழ்"),
        AssistantLanguage("te-IN", "TE-IN", "te-IN", "Telugu", "తెలుగు"),
        AssistantLanguage("bn-IN", "BN-IN", "bn-IN", "Bengali", "বাংলা"),
        AssistantLanguage("mr-IN", "MR-IN", "mr-IN", "Marathi", "मराठी"),
        AssistantLanguage("id-ID", "ID-ID", "id-ID", "Indonesian", "Bahasa Indonesia")
    )

    fun findById(idOrTag: String): AssistantLanguage =
        all.firstOrNull { it.id.equals(idOrTag, ignoreCase = true) }
            ?: all.firstOrNull { it.bcp47Tag.equals(idOrTag, ignoreCase = true) }
            ?: all.first()
}

data class ProtonVoiceOption(
    val name: String,
    val localeTag: String,
    val displayLabel: String,
    val isNetworkRequired: Boolean,
    val isLikelyMale: Boolean = false
)

enum class AiEngineMode(val label: String) {
    ONLINE_GEMINI("GEMINI ONLINE"),
    OFFLINE_LOCAL("OFFLINE CORE")
}

enum class CoreState {
    IDLE,
    WAKE_ARMED,
    LISTENING,
    PROCESSING,
    SPEAKING,
    ERROR
}

enum class OrbVisualState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    ERROR,
    OFFLINE
}

enum class PerformanceProfileMode(val label: String) {
    AUTO("Auto Adaptive"),
    LOW_END("Low-End"),
    BALANCED("Balanced"),
    HIGH_PERFORMANCE("High-Performance")
}

enum class ResolvedPerformanceTier(val label: String) {
    LOW_END("LOW-END"),
    BALANCED("BALANCED"),
    HIGH_PERFORMANCE("HIGH-PERF")
}

data class DeviceCapabilitiesReport(
    val totalRamMb: Int = 4096,
    val availRamMb: Int = 2048,
    val cpuCores: Int = 8,
    val refreshRateHz: Int = 60,
    val isLowRamDevice: Boolean = false,
    val isPowerSaveMode: Boolean = false,
    val thermalStatusLabel: String = "NORMAL",
    val isThermalThrottled: Boolean = false,
    val recommendedTier: ResolvedPerformanceTier = ResolvedPerformanceTier.BALANCED
)

data class FloatingBubbleConfig(
    val enabled: Boolean = false,
    val rotatingRingsEnabled: Boolean = true,
    val electricArcsEnabled: Boolean = true,
    val sizeDp: Float = 76f,
    val animationSpeed: Float = 1.0f,
    val opacity: Float = 0.92f,
    val glowIntensity: Float = 1.0f,
    val particleIntensity: Float = 1.0f
)

enum class AccessibilityNavAction(val label: String, val description: String) {
    BACK("Back", "Navigates one step back in the current app when you ask."),
    HOME("Home", "Returns to your phone's Home launcher screen on command."),
    RECENTS("Recents", "Opens the Android recent apps switcher."),
    NOTIFICATIONS("Notifications", "Expands the system notification shade."),
    QUICK_SETTINGS("Quick Settings", "Opens Android's Quick Settings panel."),
    TAKE_SCREENSHOT("Screenshot", "Captures a screenshot on Android 9+ when requested.")
}

data class DeviceTelemetry(
    val batteryPercent: Int = 100,
    val isCharging: Boolean = false,
    val isNetworkConnected: Boolean = true,
    val networkLabel: String = "CONNECTED",
    val ramUsagePercent: Int = 42,
    val flashlightOn: Boolean = false,
    val capabilities: DeviceCapabilitiesReport = DeviceCapabilitiesReport()
)

sealed interface PhoneControlAction {
    val displayLabel: String

    data class ToggleFlashlight(val enable: Boolean? = null) : PhoneControlAction {
        override val displayLabel: String
            get() = when (enable) {
                true -> "Flashlight ON"
                false -> "Flashlight OFF"
                null -> "Toggle Flashlight"
            }
    }

    data object OpenWifiSettings : PhoneControlAction {
        override val displayLabel: String = "Open Wi-Fi Settings"
    }

    data object OpenBluetoothSettings : PhoneControlAction {
        override val displayLabel: String = "Open Bluetooth Settings"
    }

    data object OpenSystemSettings : PhoneControlAction {
        override val displayLabel: String = "Open System Settings"
    }

    data class AdjustVolume(val direction: VolumeDirection) : PhoneControlAction {
        override val displayLabel: String = when (direction) {
            VolumeDirection.UP -> "Volume Up"
            VolumeDirection.DOWN -> "Volume Down"
            VolumeDirection.MUTE -> "Mute Media"
        }
    }

    data object OpenCamera : PhoneControlAction {
        override val displayLabel: String = "Launch Camera"
    }

    data class OpenApp(
        val appQuery: String,
        val knownPackages: List<String> = emptyList()
    ) : PhoneControlAction {
        override val displayLabel: String = "Open $appQuery"
    }

    data class OpenDialer(val phoneNumber: String? = null) : PhoneControlAction {
        override val displayLabel: String = if (phoneNumber.isNullOrBlank()) "Open Dialer" else "Dial $phoneNumber"
    }

    data class SetAlarm(val hour: Int, val minute: Int, val label: String = "Proton Alarm") : PhoneControlAction {
        override val displayLabel: String = "Set Alarm %02d:%02d".format(hour, minute)
    }

    data class SetTimer(val seconds: Int, val label: String = "Proton Timer") : PhoneControlAction {
        override val displayLabel: String = "Set Timer ${seconds}s"
    }

    data class WebSearch(val query: String) : PhoneControlAction {
        override val displayLabel: String = "Web Search: $query"
    }

    data class TellTimeDate(val includeDate: Boolean = false) : PhoneControlAction {
        override val displayLabel: String = if (includeDate) "Current Date & Time" else "Current Time"
    }

    data class AccessibilityNavigation(val navAction: AccessibilityNavAction) : PhoneControlAction {
        override val displayLabel: String = "Accessibility: ${navAction.label}"
    }

    data class ToggleReelsMode(val enable: Boolean? = null) : PhoneControlAction {
        override val displayLabel: String
            get() = when (enable) {
                true -> "Reels Mode ON"
                false -> "Reels Mode OFF"
                null -> "Toggle Reels Mode"
            }
    }
}

data class PendingConfirmationAction(
    val action: PhoneControlAction,
    val promptText: String,
    val targetDescription: String
)

enum class VolumeDirection {
    UP, DOWN, MUTE
}

data class ChatMessage(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val engineTag: String,
    val timestampMillis: Long = System.currentTimeMillis(),
    val actionExecuted: String? = null,
    val isError: Boolean = false
)

object PremiumPlanSpec {
    const val PLAN_NAME = "PROTON PREMIUM"
    const val PRICE_INR = 5
    const val CURRENCY = "INR"
    const val DURATION_MONTHS = 6
    const val AUTO_RENEWAL = false
}

enum class PremiumStatus(val label: String) {
    LOCKED("LOCKED"),
    PENDING_VERIFICATION("PENDING VERIFICATION"),
    ACTIVE("ACTIVE"),
    EXPIRED("EXPIRED"),
    FAILED("VERIFICATION FAILED")
}

data class PremiumMembershipState(
    val status: PremiumStatus = PremiumStatus.LOCKED,
    val purchasedAtMillis: Long? = null,
    val expiresAtMillis: Long? = null,
    val providerOrderId: String? = null,
    val providerPaymentId: String? = null,
    val providerName: String? = null,
    val beneficiaryUpiHandle: String = "",
    val beneficiaryAccountLabel: String = "",
    val beneficiaryPermissionGranted: Boolean = false,
    val verificationEndpoint: String = "",
    val statusNote: String? = null
) {
    fun isPremiumActive(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val expiry = expiresAtMillis ?: return false
        return status == PremiumStatus.ACTIVE &&
            !providerOrderId.isNullOrBlank() &&
            !providerPaymentId.isNullOrBlank() &&
            expiry > nowMillis
    }

    fun isMembershipExpired(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val expiry = expiresAtMillis ?: return false
        return expiry <= nowMillis && (status == PremiumStatus.ACTIVE || status == PremiumStatus.EXPIRED)
    }
}
