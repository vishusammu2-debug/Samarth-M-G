package com.example.data

import android.util.Log
import com.example.domain.AssistantLanguage
import com.example.domain.ChatMessage
import com.example.domain.DeviceTelemetry
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.Locale

private const val TAG = "OnlineAiRepository"

sealed interface StreamChunkEvent {
    data class PartialText(val visibleText: String) : StreamChunkEvent
    data class Completed(val visibleText: String, val directiveTag: String?) : StreamChunkEvent
}

private data class CachedAiReply(
    val text: String,
    val directiveTag: String?,
    val timestampMs: Long
)

class OnlineAiRepository {

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                temperature = 0.6f
                maxOutputTokens = 640
            }
        )
    }

    private val shortReplyCache = HashMap<String, CachedAiReply>()

    fun streamResponse(
        userPrompt: String,
        language: AssistantLanguage,
        telemetry: DeviceTelemetry,
        recentHistory: List<ChatMessage>
    ): Flow<StreamChunkEvent> = flow {
        val cacheKey = "${language.id}:${userPrompt.trim().lowercase(Locale.ROOT)}"
        val now = System.currentTimeMillis()
        val cached = shortReplyCache[cacheKey]
        if (cached != null && (now - cached.timestampMs) < 30_000L && cached.directiveTag == null) {
            Log.d(TAG, "[AI_CACHE] Returning cached response for repeated identical query")
            emit(StreamChunkEvent.Completed(cached.text, null))
            return@flow
        }

        val systemPrompt = buildString {
            appendLine("You are PROTON, a fast, ultra-reliable, helpful Android voice assistant.")
            appendLine("Respond naturally in ${language.englishName} (${language.nativeName}, locale ${language.bcp47Tag}).")
            if (!language.dialectPromptHint.isNullOrBlank()) {
                appendLine(language.dialectPromptHint)
            }
            appendLine("Keep answers clear and concise for spoken TTS playback (1-3 short sentences for quick questions; concise structure for explanations). Avoid markdown asterisks or bullet points.")
            appendLine("Device telemetry: Battery ${telemetry.batteryPercent}% (charging=${telemetry.isCharging}), RAM ${telemetry.ramUsagePercent}%, Net=${telemetry.networkLabel}, Torch=${if (telemetry.flashlightOn) "ON" else "OFF"}.")
            appendLine("If the user wants to open/use an Android app or device control—even indirectly (for example, 'I want to watch videos' -> YouTube, 'check my email' -> Gmail, 'browse the web' -> Chrome, 'message on WhatsApp' -> WhatsApp, 'download an app' -> Play Store, 'take a picture' -> OPEN_CAMERA)—append EXACTLY ONE tag at the very end:")
            appendLine("[ACTION:OPEN_APP:AppName], [ACTION:FLASHLIGHT_ON], [ACTION:FLASHLIGHT_OFF], [ACTION:WIFI_SETTINGS], [ACTION:BLUETOOTH_SETTINGS], [ACTION:SYSTEM_SETTINGS], [ACTION:VOLUME_UP], [ACTION:VOLUME_DOWN], [ACTION:VOLUME_MUTE], [ACTION:OPEN_CAMERA], [ACTION:SET_TIMER:seconds], [ACTION:SET_ALARM:hour24,minute], [ACTION:WEB_SEARCH:query], [ACTION:TELL_TIME], [ACTION:NAV_BACK], [ACTION:NAV_HOME], [ACTION:NAV_RECENTS], [ACTION:OPEN_NOTIFICATIONS], [ACTION:QUICK_SETTINGS], [ACTION:TAKE_SCREENSHOT]")
            appendLine("CRITICAL: Never pretend you launched an app yourself. Give a very short handoff phrase like 'Launching YouTube.' and include [ACTION:OPEN_APP:YouTube] so Android executes it locally. Never emit [ACTION:...] for general knowledge questions.")
        }

        val chatHistory = recentHistory
            .filter { !it.isError && it.text.isNotBlank() }
            .takeLast(8)
            .map { msg ->
                content(role = if (msg.isUser) "user" else "model") {
                    text(msg.text)
                }
            }

        val fullPrompt = "$systemPrompt\n\nUser request: $userPrompt"

        var lastError: Exception? = null
        for (attempt in 1..2) {
            val accumulated = StringBuilder()
            try {
                Log.i(TAG, "[AI_REQUEST] attempt=$attempt lang=${language.id} promptLen=${userPrompt.length}")
                withTimeout(30_000L) {
                    val chat = model.startChat(history = chatHistory)
                    chat.sendMessageStream(fullPrompt).collect { chunk ->
                        val piece = chunk.text.orEmpty()
                        if (piece.isNotEmpty()) {
                            accumulated.append(piece)
                            val visible = stripActionTagForPreview(accumulated.toString())
                            emit(StreamChunkEvent.PartialText(visible))
                        }
                    }
                }

                val raw = accumulated.toString().trim()
                if (raw.isBlank()) {
                    throw IllegalStateException("Gemini returned an empty response.")
                }

                val tagRegex = Regex("\\[ACTION:([^\\]]+)\\]")
                val match = tagRegex.find(raw)
                val directive = match?.groupValues?.getOrNull(1)?.trim()
                val cleaned = raw.replace(tagRegex, "").trim().ifBlank { "Executing command." }

                if (directive == null && shortReplyCache.size > 24) {
                    shortReplyCache.clear()
                }
                if (directive == null) {
                    shortReplyCache[cacheKey] = CachedAiReply(cleaned, null, System.currentTimeMillis())
                }

                Log.i(TAG, "[AI_RESPONSE] attempt=$attempt completed chars=${cleaned.length} directive=$directive")
                emit(StreamChunkEvent.Completed(cleaned, directive))
                return@flow
            } catch (e: CancellationException) {
                if (e is TimeoutCancellationException && attempt == 1 && accumulated.isEmpty()) {
                    Log.w(TAG, "Gemini timed out on attempt 1 with no chunks; retrying once quickly")
                    lastError = e
                    delay(350L)
                    continue
                }
                throw e
            } catch (e: IOException) {
                Log.w(TAG, "Transient network IO error on attempt $attempt: ${e.message}")
                lastError = e
                if (attempt == 1 && accumulated.isEmpty()) {
                    delay(400L)
                    continue
                }
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Firebase AI generation failed on attempt $attempt", e)
                throw e
            }
        }

        if (lastError != null) {
            throw lastError
        }
    }

    private fun stripActionTagForPreview(raw: String): String {
        val idx = raw.indexOf("[ACTION:")
        return if (idx >= 0) raw.substring(0, idx).trimEnd() else raw
    }
}
