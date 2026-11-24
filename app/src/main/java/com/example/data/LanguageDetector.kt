package com.example.data

import com.example.domain.AssistantLanguage
import com.example.domain.SupportedLanguages

object LanguageDetector {

    private val uttaraKannadaCues = listOf(
        "uttara kannada",
        "ಉತ್ತರ ಕನ್ನಡ",
        "ಮಾರಾಯ",
        "ಮಾರಾಯ್ರೆ",
        "ಹೌದಾ",
        "karwar",
        "sirsi",
        "kumta",
        "honnavar"
    )

    fun detectFromInput(
        input: String,
        currentLanguage: AssistantLanguage
    ): AssistantLanguage {
        val text = input.trim()
        if (text.isEmpty()) return currentLanguage

        val lower = text.lowercase()
        if (uttaraKannadaCues.any { lower.contains(it) }) {
            return SupportedLanguages.findById("kn-UK")
        }

        // Script-based detection across supported Indic, East Asian, Arabic, and Cyrillic scripts
        for (ch in text) {
            val code = ch.code
            when {
                code in 0x0C80..0x0CFF -> {
                    // Kannada script: preserve Uttara Kannada dialect if already active
                    return if (currentLanguage.id == "kn-UK") {
                        currentLanguage
                    } else {
                        SupportedLanguages.findById("kn-IN")
                    }
                }
                code in 0x0900..0x097F -> {
                    return if (currentLanguage.id == "mr-IN") {
                        currentLanguage
                    } else {
                        SupportedLanguages.findById("hi-IN")
                    }
                }
                code in 0x0B80..0x0BFF -> return SupportedLanguages.findById("ta-IN")
                code in 0x0C00..0x0C7F -> return SupportedLanguages.findById("te-IN")
                code in 0x0980..0x09FF -> return SupportedLanguages.findById("bn-IN")
                code in 0x0600..0x06FF -> return SupportedLanguages.findById("ar-SA")
                code in 0x3040..0x30FF -> return SupportedLanguages.findById("ja-JP")
                code in 0xAC00..0xD7AF -> return SupportedLanguages.findById("ko-KR")
                code in 0x4E00..0x9FFF -> return SupportedLanguages.findById("zh-CN")
                code in 0x0400..0x04FF -> return SupportedLanguages.findById("ru-RU")
            }
        }

        return currentLanguage
    }
}
