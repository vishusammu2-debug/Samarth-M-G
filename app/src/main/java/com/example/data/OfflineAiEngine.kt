package com.example.data

import com.example.domain.AssistantLanguage
import com.example.domain.DeviceTelemetry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class OfflineResponse(
    val text: String
)

class OfflineAiEngine {

    fun processLocally(
        prompt: String,
        language: AssistantLanguage,
        telemetry: DeviceTelemetry,
        executedControlMessage: String?
    ): OfflineResponse {
        val isUttaraKannada = language.id == "kn-UK"
        val isStandardKannada = language.id == "kn-IN"

        if (executedControlMessage != null) {
            return when {
                isUttaraKannada -> OfflineResponse("ಆಯ್ತು ಮಾರಾಯ, $executedControlMessage")
                isStandardKannada -> OfflineResponse("ಆಫ್‌ಲೈನ್ ಕೋರ್ ಕಾರ್ಯಗತಗೊಳಿಸಿದೆ: $executedControlMessage")
                else -> OfflineResponse("Offline Core executed: $executedControlMessage")
            }
        }

        val q = prompt.trim().lowercase()

        // Time / date queries
        if (q.contains("time") || q.contains("clock") || q.contains("ಸಮಯ") || q.contains("ಟೈಮ್")) {
            val formatted = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
            return when {
                isUttaraKannada -> OfflineResponse("ಈಗ ಟೈಮ್ $formatted ಆಗಿದೆ ಮಾರಾಯ.")
                isStandardKannada -> OfflineResponse("ಪ್ರಸ್ತುತ ಸಮಯ $formatted ಆಗಿದೆ.")
                else -> OfflineResponse("Current system time is $formatted.")
            }
        }
        if (q.contains("date") || q.contains("day") || q.contains("today") || q.contains("ದಿನಾಂಕ")) {
            val formatted = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(Date())
            return when {
                isUttaraKannada -> OfflineResponse("ಇವತ್ತು $formatted ತಾರೀಖು.")
                isStandardKannada -> OfflineResponse("ಇಂದಿನ ದಿನಾಂಕ $formatted.")
                else -> OfflineResponse("Today is $formatted.")
            }
        }

        // System diagnostics
        if (q.contains("battery") || q.contains("power") || q.contains("ಬ್ಯಾಟರಿ")) {
            val chargeText = if (telemetry.isCharging) "and currently charging" else "on battery power"
            return when {
                isUttaraKannada -> OfflineResponse("ಫೋನ್ ಬ್ಯಾಟರಿ ಈಗ ${telemetry.batteryPercent}% ಇದೆ.")
                isStandardKannada -> OfflineResponse("ಬ್ಯಾಟರಿ ಮಟ್ಟ ${telemetry.batteryPercent}% ಇದೆ.")
                else -> OfflineResponse("Power core is at ${telemetry.batteryPercent}% $chargeText.")
            }
        }
        if (q.contains("status") || q.contains("diagnostic") || q.contains("system") || q.contains("ram") || q.contains("memory")) {
            return when {
                isUttaraKannada -> OfflineResponse(
                    "ಸಿಸ್ಟಮ್ ಸ್ಟೇಟಸ್ ನೋಡು: ಬ್ಯಾಟರಿ ${telemetry.batteryPercent}%, RAM ${telemetry.ramUsagePercent}%, ನೆಟ್‌ವರ್ಕ್ ${telemetry.networkLabel}."
                )
                isStandardKannada -> OfflineResponse(
                    "ಸಿಸ್ಟಮ್ ಸ್ಥಿತಿ: ಬ್ಯಾಟರಿ ${telemetry.batteryPercent}%, RAM ${telemetry.ramUsagePercent}%, ನೆಟ್‌ವರ್ಕ್ ${telemetry.networkLabel}."
                )
                else -> OfflineResponse(
                    "System telemetry: Power ${telemetry.batteryPercent}%, RAM usage ${telemetry.ramUsagePercent}%, Network ${telemetry.networkLabel}, Language ${language.englishName} (${language.code})."
                )
            }
        }

        // Simple offline arithmetic
        val mathCandidate = q.replace("calculate", "").replace("what is", "").trim()
        evaluateSimpleMath(mathCandidate)?.let { result ->
            return when {
                isUttaraKannada -> OfflineResponse("ಲೆಕ್ಕದ ಉತ್ತರ $result ಬಂತು ನೋಡು.")
                isStandardKannada -> OfflineResponse("ಲೆಕ್ಕಾಚಾರದ ಫಲಿತಾಂಶ: $result.")
                else -> OfflineResponse("Offline calculation result: $result.")
            }
        }

        // Greetings / Identity
        if (q.contains("hello") || q.contains("hi") || q.contains("hey proton") || q.contains("who are you") ||
            q.contains("ನಮಸ್ಕಾರ") || q.contains("ಹಲೋ") || q.contains("ಹೇಗಿದ್ದೀಯ")
        ) {
            return when {
                isUttaraKannada -> OfflineResponse(
                    "ನಮಸ್ಕಾರ ಮಾರಾಯ! ಪ್ರೋಟಾನ್ ೨.೦ ಆಫ್‌ಲೈನ್ ಕೋರ್ ರೆಡಿ ಇದೆ. ಬ್ಯಾಟರಿ, ಟೈಮ್, ಫ್ಲ್ಯಾಶ್‌ಲೈಟ್ ಮತ್ತೆ ಲೆಕ್ಕ ಎಲ್ಲಾ ಇಲ್ಲಿಂದಲೇ ಮಾಡ್ತೇನೆ."
                )
                isStandardKannada -> OfflineResponse(
                    "ನಮಸ್ಕಾರ! ಪ್ರೋಟಾನ್ ೨.೦ ಆಫ್‌ಲೈನ್ ಕೋರ್ ಸಿದ್ಧವಾಗಿದೆ. ಸಮಯ, ಬ್ಯಾಟರಿ ಸ್ಥಿತಿ, ಸರಳ ಲೆಕ್ಕ ಮತ್ತು ಫೋನ್ ನಿಯಂತ್ರಣಗಳನ್ನು ನಾನು ನಿರ್ವಹಿಸಬಲ್ಲೆ."
                )
                else -> OfflineResponse(
                    "Proton 2.0 Offline Core standing by in ${language.englishName}. I can handle device diagnostics, time, quick math, and safe phone controls offline. Switch to Gemini Online for open-ended reasoning."
                )
            }
        }

        if (q.contains("help") || q.contains("command")) {
            return OfflineResponse(
                "Offline Core commands: flashlight on/off, volume up/down/mute, open Wi-Fi, open Bluetooth, open camera, set timer/alarm, battery status, time, date, or quick math."
            )
        }

        return when {
            isUttaraKannada -> OfflineResponse(
                "ಈಗ ಆಫ್‌ಲೈನ್ ಮೋಡ್‌ನಲ್ಲಿ ಇದ್ದೇನೆ ಮಾರಾಯ. \"$prompt\" ಗೆ ಪೂರ್ತಿ ಉತ್ತರ ಬೇಕಿದ್ರೆ Gemini Online ಆನ್ ಮಾಡು, ಇಲ್ಲಾಂದ್ರೆ ಬ್ಯಾಟರಿ, ಟೈಮ್ ಅಥವಾ ಟಾರ್ಚ್ ಆನ್ ಮಾಡು ಅಂತ ಕೇಳು."
            )
            isStandardKannada -> OfflineResponse(
                "ಆಫ್‌ಲೈನ್ ಕೋರ್ ಸಕ್ರಿಯವಾಗಿದೆ (${language.code}). \"$prompt\" ಪ್ರಶ್ನೆಗೆ Gemini Online ಬಳಸಿ ಅಥವಾ ಬ್ಯಾಟರಿ, ಸಮಯ, ಫ್ಲ್ಯಾಶ್‌ಲೈಟ್ ನಿಯಂತ್ರಣಗಳನ್ನು ಕೇಳಿ."
            )
            else -> OfflineResponse(
                "Offline Core is active (${language.code}). Your query \"$prompt\" requires Gemini Online mode for full generative reasoning, or try a local device command like battery status, flashlight, timer, or Wi-Fi settings."
            )
        }
    }

    private fun evaluateSimpleMath(expr: String): String? {
        val match = Regex("^(-?\\d+(?:\\.\\d+)?)\\s*([+\\-*/x])\\s*(-?\\d+(?:\\.\\d+)?)$").find(expr)
            ?: return null
        val a = match.groupValues[1].toDoubleOrNull() ?: return null
        val op = match.groupValues[2]
        val b = match.groupValues[3].toDoubleOrNull() ?: return null
        val res = when (op) {
            "+" -> a + b
            "-" -> a - b
            "*", "x" -> a * b
            "/" -> if (b == 0.0) return "undefined (division by zero)" else a / b
            else -> return null
        }
        return if (res % 1.0 == 0.0) res.toLong().toString() else "%.4f".format(Locale.US, res).trimEnd('0').trimEnd('.')
    }
}
