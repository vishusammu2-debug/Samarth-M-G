package com.example.data

import android.util.Log
import com.example.domain.AccessibilityNavAction
import com.example.domain.AssistantLanguage
import com.example.domain.PendingConfirmationAction
import com.example.domain.PhoneControlAction
import com.example.domain.VolumeDirection
import java.util.Locale

private const val TAG = "ProtonCommandRouter"

sealed interface CommandRouteDecision {
    data object DuplicateIgnored : CommandRouteDecision
    data class ConfirmPending(val approved: Boolean) : CommandRouteDecision
    data class ExecuteLocalAction(
        val action: PhoneControlAction,
        val immediateAck: String? = null
    ) : CommandRouteDecision

    data class RequireConfirmation(
        val pending: PendingConfirmationAction
    ) : CommandRouteDecision

    data class SendToAi(
        val normalizedPrompt: String
    ) : CommandRouteDecision
}

/**
 * Deterministic pipeline router:
 * VOICE/TEXT INPUT -> DEDUPLICATION -> INTENT CLASSIFICATION -> COMMAND VALIDATION -> ACTION OR GEMINI.
 */
class ProtonCommandRouter(
    private val duplicateWindowMs: Long = 1_800L
) {
    private var lastNormalizedUtterance: String = ""
    private var lastUtteranceTimestampMs: Long = 0L

    fun route(
        rawInput: String,
        language: AssistantLanguage,
        hasPendingConfirmation: Boolean,
        nowMs: Long = System.currentTimeMillis()
    ): CommandRouteDecision {
        val trimmed = rawInput.trim()
        if (trimmed.isBlank()) return CommandRouteDecision.DuplicateIgnored

        val normalized = trimmed.lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
        safeLogD("[STAGE:INPUT] lang=${language.id} text=\"$trimmed\"")

        if (normalized == lastNormalizedUtterance && (nowMs - lastUtteranceTimestampMs) < duplicateWindowMs) {
            safeLogW("[STAGE:DEDUP] Ignored duplicate utterance within ${duplicateWindowMs}ms: \"$normalized\"")
            return CommandRouteDecision.DuplicateIgnored
        }
        lastNormalizedUtterance = normalized
        lastUtteranceTimestampMs = nowMs

        // 1. Handle confirmation responses when a sensitive action (e.g., phone call) is awaiting approval
        if (hasPendingConfirmation) {
            if (isAffirmative(normalized)) {
                safeLogI("[STAGE:CONFIRM] User approved pending sensitive action")
                return CommandRouteDecision.ConfirmPending(approved = true)
            }
            if (isNegative(normalized)) {
                safeLogI("[STAGE:CONFIRM] User cancelled pending sensitive action")
                return CommandRouteDecision.ConfirmPending(approved = false)
            }
        }

        // 2. Detect call intent first -> ALWAYS require explicit user confirmation before dialing
        if (isCallIntent(normalized)) {
            val target = extractCallTarget(trimmed)
            val digits = Regex("[+0-9][0-9\\-\\s]{2,}").find(trimmed)?.value?.trim()
            val targetDesc = target.ifBlank { digits ?: "phone dialer" }
            val prompt = when (language.id) {
                "kn-UK" -> "$targetDesc ಗೆ ಕಾಲ್ ಮಾಡೋದಾ ಮಾರಾಯ? ದೃಢೀಕರಿಸಲು 'ಹೌದು' ಅಥವಾ Confirm ಒತ್ತು."
                "kn-IN" -> "$targetDesc ಗೆ ಕರೆ ಮಾಡಲು ಡಯಲರ್ ತೆರೆಯಬೇಕೆ? ದಯವಿಟ್ಟು ದೃಢೀಕರಿಸಿ."
                "hi-IN" -> "क्या आप $targetDesc को कॉल करने के लिए डायलर खोलना चाहते हैं? कृपया पुष्टि करें।"
                else -> "Confirm opening the phone dialer for $targetDesc?"
            }
            safeLogI("[STAGE:VALIDATION] Call intent gated behind user confirmation: target=$targetDesc")
            return CommandRouteDecision.RequireConfirmation(
                PendingConfirmationAction(
                    action = PhoneControlAction.OpenDialer(digits),
                    promptText = prompt,
                    targetDescription = targetDesc
                )
            )
        }

        // 3. Classify local Android device commands (zero-latency execution without burning Gemini quota)
        val localAction = classifyLocalIntent(trimmed, normalized)
        if (localAction != null) {
            safeLogI("[STAGE:INTENT] Classified local action=${localAction.displayLabel}")
            val ack = buildQuickAck(localAction, language)
            return CommandRouteDecision.ExecuteLocalAction(
                action = localAction,
                immediateAck = ack
            )
        }

        // 4. General / conversational / reasoning request -> route to Gemini (or Offline Core)
        safeLogI("[STAGE:INTENT] Classified general AI query -> routing to AI engine")
        return CommandRouteDecision.SendToAi(trimmed)
    }

    fun classifyLocalIntent(original: String, lower: String = original.trim().lowercase(Locale.ROOT)): PhoneControlAction? {
        val cleanedOriginal = stripConversationalWrappers(original)
        val cleanedLower = cleanedOriginal.lowercase(Locale.ROOT)
        if (cleanedLower != lower) {
            safeLogD("[STAGE:NORMALIZE] \"$original\" -> \"$cleanedOriginal\"")
        }

        return when {
            // Flashlight controls (EN / KN / HI)
            cleanedLower.contains("flashlight on") || cleanedLower.contains("torch on") ||
                cleanedLower.contains("turn on flashlight") || cleanedLower.contains("turn on the flashlight") ||
                cleanedLower.contains("turn on torch") || cleanedLower.contains("ಟಾರ್ಚ್ ಆನ್") ||
                cleanedLower.contains("ಫ್ಲ್ಯಾಶ್‌ಲೈಟ್ ಆನ್") || cleanedLower.contains("टॉर्च चालू") ||
                cleanedLower.contains("फ्लैशलाइट चालू") || cleanedLower.contains("फ्लैशलाइट ऑन") ->
                PhoneControlAction.ToggleFlashlight(true)

            cleanedLower.contains("flashlight off") || cleanedLower.contains("torch off") ||
                cleanedLower.contains("turn off flashlight") || cleanedLower.contains("turn off the flashlight") ||
                cleanedLower.contains("turn off torch") || cleanedLower.contains("ಟಾರ್ಚ್ ಆಫ್") ||
                cleanedLower.contains("ಫ್ಲ್ಯಾಶ್‌ಲೈಟ್ ಆಫ್") || cleanedLower.contains("टॉर्च बंद") ||
                cleanedLower.contains("फ्लैशलाइट बंद") || cleanedLower.contains("फ्लैशलाइट ऑफ") ->
                PhoneControlAction.ToggleFlashlight(false)

            cleanedLower == "flashlight" || cleanedLower == "torch" || cleanedLower.contains("toggle flashlight") ->
                PhoneControlAction.ToggleFlashlight(null)

            // Time & Date (fast local spoken response)
            isTimeOrDateQuery(cleanedLower) -> {
                val wantsDate = cleanedLower.contains("date") || cleanedLower.contains("day") ||
                    cleanedLower.contains("today") || cleanedLower.contains("ದಿನಾಂಕ") ||
                    cleanedLower.contains("ತಾರೀಖು") || cleanedLower.contains("तारीख")
                PhoneControlAction.TellTimeDate(includeDate = wantsDate)
            }

            // Explicit major system targets before generic app launcher
            cleanedLower == "camera" || cleanedLower == "open camera" ||
                cleanedLower == "launch camera" || cleanedLower == "start camera" ||
                cleanedLower.contains("ಕ್ಯಾಮೆರಾ") || cleanedLower.contains("कैमरा") ->
                PhoneControlAction.OpenCamera

            cleanedLower == "wifi settings" || cleanedLower == "wi-fi settings" ||
                cleanedLower == "open wifi" || cleanedLower == "open wi-fi" ||
                cleanedLower == "open wifi settings" || cleanedLower == "open wi-fi settings" ->
                PhoneControlAction.OpenWifiSettings

            cleanedLower == "bluetooth settings" || cleanedLower == "open bluetooth" ||
                cleanedLower == "open bluetooth settings" ->
                PhoneControlAction.OpenBluetoothSettings

            cleanedLower == "settings" || cleanedLower == "open settings" ||
                cleanedLower == "launch settings" || cleanedLower == "start settings" ||
                cleanedLower == "open device settings" || cleanedLower == "open android settings" ||
                cleanedLower.contains("ಸೆಟ್ಟಿಂಗ್ಸ್") || cleanedLower.contains("सेटिंग्स") ->
                PhoneControlAction.OpenSystemSettings

            // Volume controls
            cleanedLower.contains("volume up") || cleanedLower.contains("increase volume") ||
                cleanedLower.contains("increase media volume") || cleanedLower.contains("turn up volume") ||
                cleanedLower.contains("louder") || cleanedLower.contains("ವಾಲ್ಯೂಮ್ ಜಾಸ್ತಿ") ||
                cleanedLower.contains("आवाज़ बढ़ाओ") || cleanedLower.contains("वॉल्यूम बढ़ाओ") ->
                PhoneControlAction.AdjustVolume(VolumeDirection.UP)

            cleanedLower.contains("volume down") || cleanedLower.contains("decrease volume") ||
                cleanedLower.contains("decrease media volume") || cleanedLower.contains("lower volume") ||
                cleanedLower.contains("quieter") || cleanedLower.contains("ವಾಲ್ಯೂಮ್ ಕಡಿಮೆ") ||
                cleanedLower.contains("आवाज़ कम") || cleanedLower.contains("वॉल्यूम कम") ->
                PhoneControlAction.AdjustVolume(VolumeDirection.DOWN)

            cleanedLower.contains("mute volume") || cleanedLower.contains("mute media") ||
                cleanedLower.contains("mute audio") || cleanedLower == "mute" ->
                PhoneControlAction.AdjustVolume(VolumeDirection.MUTE)

            // Alarms & Timers
            cleanedLower.contains("set alarm") || cleanedLower.contains("set an alarm") || cleanedLower.contains("alarm for") ||
                cleanedLower.contains("wake me up at") || cleanedLower.contains("ಅಲಾರಾಂ") || cleanedLower.contains("अलार्म") -> {
                val timeMatch = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm|a\\.m\\.|p\\.m\\.)?").find(cleanedLower)
                if (timeMatch != null) {
                    var hour = timeMatch.groupValues[1].toIntOrNull() ?: 7
                    val minute = timeMatch.groupValues[2].toIntOrNull() ?: 0
                    val amPm = timeMatch.groupValues[3].replace(".", "")
                    if (amPm == "pm" && hour in 1..11) hour += 12
                    if (amPm == "am" && hour == 12) hour = 0
                    PhoneControlAction.SetAlarm(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
                } else {
                    PhoneControlAction.SetAlarm(7, 0)
                }
            }

            cleanedLower.contains("set timer") || cleanedLower.contains("set a timer") || cleanedLower.contains("timer for") -> {
                val minMatch = Regex("(\\d+)\\s*(minute|minutes|min)").find(cleanedLower)
                val secMatch = Regex("(\\d+)\\s*(second|seconds|sec)").find(cleanedLower)
                val seconds = when {
                    minMatch != null -> (minMatch.groupValues[1].toIntOrNull() ?: 1) * 60
                    secMatch != null -> secMatch.groupValues[1].toIntOrNull() ?: 30
                    else -> 60
                }
                PhoneControlAction.SetTimer(seconds)
            }

            // Web Search
            cleanedLower.startsWith("search ") || cleanedLower.startsWith("google ") ||
                cleanedLower.contains("search the internet for ") || cleanedLower.contains("search online for ") ||
                cleanedLower.contains("look up ") -> {
                val query = cleanedOriginal
                    .replaceFirst(
                        Regex(
                            "^(search the internet for|search online for|search for|search|google|look up)\\s+",
                            RegexOption.IGNORE_CASE
                        ),
                        ""
                    )
                    .trim()
                if (query.isNotBlank()) PhoneControlAction.WebSearch(query) else null
            }

            // Accessibility global actions
            cleanedLower == "go back" || cleanedLower == "navigate back" ->
                PhoneControlAction.AccessibilityNavigation(AccessibilityNavAction.BACK)
            cleanedLower == "go home" || cleanedLower == "home screen" ->
                PhoneControlAction.AccessibilityNavigation(AccessibilityNavAction.HOME)
            cleanedLower.contains("open recents") || cleanedLower.contains("recent apps") ->
                PhoneControlAction.AccessibilityNavigation(AccessibilityNavAction.RECENTS)
            cleanedLower.contains("open notifications") || cleanedLower.contains("show notifications") ->
                PhoneControlAction.AccessibilityNavigation(AccessibilityNavAction.NOTIFICATIONS)
            cleanedLower.contains("quick settings") ->
                PhoneControlAction.AccessibilityNavigation(AccessibilityNavAction.QUICK_SETTINGS)
            cleanedLower.contains("take screenshot") || cleanedLower.contains("take a screenshot") ->
                PhoneControlAction.AccessibilityNavigation(AccessibilityNavAction.TAKE_SCREENSHOT)

            // PROTON PREMIUM Reels Mode voice toggles
            cleanedLower == "reels mode on" || cleanedLower == "enable reels mode" ||
                cleanedLower == "turn on reels mode" || cleanedLower == "start reels mode" ||
                cleanedLower == "activate reels mode" ->
                PhoneControlAction.ToggleReelsMode(true)

            cleanedLower == "reels mode off" || cleanedLower == "disable reels mode" ||
                cleanedLower == "turn off reels mode" || cleanedLower == "stop reels mode" ||
                cleanedLower == "deactivate reels mode" ->
                PhoneControlAction.ToggleReelsMode(false)

            cleanedLower == "reels mode" || cleanedLower == "toggle reels mode" ->
                PhoneControlAction.ToggleReelsMode(null)

            // Universal app launch extraction ("Open <App>", "Launch <App>", "Start <App>", "Run <App>", KN/HI verbs)
            else -> extractAppLaunchAction(cleanedOriginal, cleanedLower)
        }
    }

    private fun extractAppLaunchAction(cleanedOriginal: String, cleanedLower: String): PhoneControlAction? {
        // Explicit localized app keywords (Kannada / Hindi)
        val localizedShortcut = when {
            cleanedLower.contains("ಯೂಟ್ಯೂಬ್") || cleanedLower.contains("यूट्यूब") -> "YouTube"
            cleanedLower.contains("ಕ್ರೋಮ್") || cleanedLower.contains("क्रोम") -> "Chrome"
            cleanedLower.contains("ಜಿಮೇಲ್") || cleanedLower.contains("जीमेल") -> "Gmail"
            cleanedLower.contains("ವಾಟ್ಸಾಪ್") || cleanedLower.contains("व्हाट्सएप") || cleanedLower.contains("व्हाट्सऐप") -> "WhatsApp"
            cleanedLower.contains("ಇನ್‌ಸ್ಟಾಗ್ರಾಮ್") || cleanedLower.contains("इंस्टाग्राम") -> "Instagram"
            cleanedLower.contains("ಪ್ಲೇ ಸ್ಟೋರ್") || cleanedLower.contains("प्ले स्टोर") -> "Play Store"
            else -> null
        }
        if (localizedShortcut != null) {
            safeLogI("[STAGE:APP_EXTRACT] Localized app=\"$localizedShortcut\"")
            return buildOpenAppAction(localizedShortcut)
        }

        // English verb extraction: open / launch / start / run / open up / bring up / switch to / go to
        val prefixRegex = Regex(
            "^(?:open up|bring up|switch to|go to|open|launch|start|run)\\s+(?:the\\s+)?(.+)$",
            RegexOption.IGNORE_CASE
        )
        val prefixMatch = prefixRegex.find(cleanedOriginal)
        if (prefixMatch != null) {
            val extracted = ProtonAppLauncher.cleanAppQuery(prefixMatch.groupValues[1])
            if (extracted.isNotBlank() && !isNonAppObject(extracted)) {
                safeLogI("[STAGE:APP_EXTRACT] Extracted app name=\"$extracted\"")
                return mapExtractedTargetToAction(extracted)
            }
        }

        // Suffix launch verbs (e.g. "YouTube open karo", "WhatsApp kholo", "Instagram ತೆಗಿ")
        val suffixRegex = Regex(
            "^(.+?)\\s+(?:kholo|open karo|chalu karo|ತೆಗಿ|ತೆರೆಯಿರಿ|ಓಪನ್ ಮಾಡು|ಖೋಲೋ|खोलो|चालू करो)$",
            RegexOption.IGNORE_CASE
        )
        val suffixMatch = suffixRegex.find(cleanedOriginal)
        if (suffixMatch != null) {
            val extracted = ProtonAppLauncher.cleanAppQuery(suffixMatch.groupValues[1])
            if (extracted.isNotBlank() && !isNonAppObject(extracted)) {
                safeLogI("[STAGE:APP_EXTRACT] Extracted suffix app name=\"$extracted\"")
                return mapExtractedTargetToAction(extracted)
            }
        }

        // Direct one-word/known-app utterance ("youtube", "whatsapp", "instagram", "play store")
        val directNormalized = ProtonAppLauncher.normalizeForMatch(cleanedOriginal)
        if (directNormalized in setOf("youtube", "chrome", "whatsapp", "instagram", "gmail", "play store", "google play")) {
            safeLogI("[STAGE:APP_EXTRACT] Direct app utterance=\"$cleanedOriginal\"")
            return buildOpenAppAction(cleanedOriginal)
        }

        return null
    }

    private fun mapExtractedTargetToAction(extracted: String): PhoneControlAction {
        val norm = extracted.lowercase(Locale.ROOT)
        return when (norm) {
            "camera" -> PhoneControlAction.OpenCamera
            "settings", "device settings", "system settings", "android settings", "phone settings" ->
                PhoneControlAction.OpenSystemSettings
            "wifi", "wi-fi", "wifi settings", "wi-fi settings" -> PhoneControlAction.OpenWifiSettings
            "bluetooth", "bluetooth settings" -> PhoneControlAction.OpenBluetoothSettings
            else -> buildOpenAppAction(extracted)
        }
    }

    private fun buildOpenAppAction(appName: String): PhoneControlAction.OpenApp {
        val cleaned = ProtonAppLauncher.cleanAppQuery(appName)
        val lowerName = cleaned.lowercase(Locale.ROOT)
        val knownPackages = when {
            lowerName == "youtube" -> listOf("com.google.android.youtube", "com.google.android.apps.youtube.music")
            lowerName == "chrome" || lowerName == "google chrome" -> listOf("com.android.chrome", "com.chrome.beta")
            lowerName == "whatsapp" -> listOf("com.whatsapp", "com.whatsapp.w4b")
            lowerName == "instagram" -> listOf("com.instagram.android")
            lowerName == "gmail" || lowerName == "email" -> listOf("com.google.android.gm")
            lowerName == "play store" || lowerName == "google play" || lowerName == "google play store" ->
                listOf("com.android.vending")
            else -> emptyList()
        }
        return PhoneControlAction.OpenApp(
            appQuery = cleaned,
            knownPackages = knownPackages
        )
    }

    private fun isNonAppObject(extracted: String): Boolean {
        val norm = extracted.lowercase(Locale.ROOT)
        // Avoid hijacking conversational prompts like "open a discussion about..." or "start a timer"
        return norm.startsWith("a ") ||
            norm.startsWith("an ") ||
            norm.startsWith("timer") ||
            norm.startsWith("alarm") ||
            norm.contains("conversation") ||
            norm.contains("flashlight") ||
            norm.contains("torch")
    }

    private fun stripConversationalWrappers(raw: String): String {
        var text = raw.trim()
            .replace(Regex("[.?!,;:\"'`~]+$"), "")
            .trim()

        val leadingPrefixes = Regex(
            "^(?:hey\\s+proton|ok\\s+proton|okay\\s+proton|hi\\s+proton|proton|please|kindly|can\\s+you|could\\s+you|would\\s+you|will\\s+you|help\\s+me|go\\s+ahead\\s+and|just|now)\\b[,\\s]*",
            RegexOption.IGNORE_CASE
        )
        var iterations = 0
        while (iterations < 5) {
            val next = text.replaceFirst(leadingPrefixes, "").trim()
            if (next == text) break
            text = next
            iterations++
        }

        val trailingFillers = Regex(
            "(?:,|\\s)+(?:please|for\\s+me|right\\s+now|now|app|application)$",
            RegexOption.IGNORE_CASE
        )
        var tailIterations = 0
        while (tailIterations < 3) {
            val next = text.replaceFirst(trailingFillers, "").trim()
            if (next == text) break
            text = next
            tailIterations++
        }

        return text.replace(Regex("[.?!,;:\"'`~]+$"), "").trim()
    }

    private fun isTimeOrDateQuery(lower: String): Boolean {
        return lower == "time" || lower == "date" ||
            lower.contains("what time is it") || lower.contains("current time") ||
            lower.contains("tell me the time") || lower.contains("what's the time") ||
            lower.contains("what is the time") || lower.contains("what is today's date") ||
            lower.contains("current date") || lower.contains("what's the date") ||
            lower.contains("ಸಮಯ ಎಷ್ಟು") || lower.contains("ಟೈಮ್ ಎಷ್ಟು") ||
            lower.contains("ದಿನಾಂಕ") || lower.contains("कितने बजे") ||
            lower.contains("आज की तारीख")
    }

    private fun isCallIntent(lower: String): Boolean {
        return lower.startsWith("call ") ||
            lower.startsWith("make a call") ||
            lower.startsWith("phone ") ||
            lower.startsWith("dial ") ||
            lower.contains("open dialer")
    }

    private fun extractCallTarget(original: String): String {
        return original.trim()
            .replaceFirst(Regex("^(make a call to|call|phone|dial|open dialer for|open dialer)\\s*", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    private fun isAffirmative(lower: String): Boolean {
        return lower == "yes" || lower == "confirm" || lower == "ok" || lower == "okay" ||
            lower == "proceed" || lower.contains("yes call") || lower.contains("confirm call") ||
            lower == "ಹೌದು" || lower == "ಆಯ್ತು" || lower == "हाँ"
    }

    private fun isNegative(lower: String): Boolean {
        return lower == "no" || lower == "cancel" || lower == "stop" || lower == "abort" ||
            lower == "don't" || lower == "ಬೇಡ" || lower == "ನಿಲ್ಲಿಸು" || lower == "नहीं"
    }

    private fun buildQuickAck(action: PhoneControlAction, language: AssistantLanguage): String? {
        return when (language.id) {
            "kn-UK" -> when (action) {
                is PhoneControlAction.OpenApp -> "${action.appQuery} ತೆಗಿತಾ ಇದ್ದೇನೆ…"
                is PhoneControlAction.ToggleFlashlight -> "ಆಯ್ತು ಮಾರಾಯ, ಟಾರ್ಚ್ ಚೆಕ್ ಮಾಡ್ತೇನೆ."
                else -> null
            }
            "kn-IN" -> when (action) {
                is PhoneControlAction.OpenApp -> "${action.appQuery} ತೆರೆಯಲಾಗುತ್ತಿದೆ…"
                else -> null
            }
            "hi-IN" -> when (action) {
                is PhoneControlAction.OpenApp -> "${action.appQuery} खोल रहा हूँ…"
                else -> null
            }
            else -> when (action) {
                is PhoneControlAction.OpenApp -> "Opening ${action.appQuery}…"
                is PhoneControlAction.WebSearch -> "Searching for ${action.query}…"
                else -> null
            }
        }
    }

    private fun safeLogD(msg: String) {
        try { Log.d(TAG, msg) } catch (_: Throwable) { }
    }

    private fun safeLogI(msg: String) {
        try { Log.i(TAG, msg) } catch (_: Throwable) { }
    }

    private fun safeLogW(msg: String) {
        try { Log.w(TAG, msg) } catch (_: Throwable) { }
    }
}
