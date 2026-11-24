package com.example.data

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.example.domain.ProtonVoiceOption
import java.util.Locale

private const val TAG = "ProtonVoiceController"

interface VoiceControllerCallbacks {
    fun onRmsChanged(normalizedLevel: Float)
    fun onPartialTranscript(text: String)
    fun onCommandRecognized(text: String)
    fun onWakeWordDetected()
    fun onListeningStateChanged(active: Boolean, isWakeLoop: Boolean)
    fun onSpeakingStateChanged(speaking: Boolean)
    fun onVoiceError(message: String, isWakeLoop: Boolean)
    fun onAvailableVoicesUpdated(voices: List<ProtonVoiceOption>, isLanguageSupported: Boolean)
    fun onSpeechCompletedReadyForFollowUp()
}

class ProtonVoiceController(
    private val context: Context,
    private val callbacks: VoiceControllerCallbacks
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private var currentLanguageTag = "en-US"
    private var preferredVoiceName: String? = null
    private var preferMaleVoice = true
    private var speechRate = 1.0f
    private var speechVolume = 1.0f
    private var preferOfflineRecognition = false

    private var isListeningActive = false
    private var isWakeModeActive = false
    private var shouldKeepWakeLoop = false
    private var sessionIdCounter = 0L
    private var activeSessionId = 0L
    private var latestPartialText = ""
    private var commandDeliveredForSession = false
    private var retryCountForBusy = 0

    private val watchdogRunnable = Runnable {
        if (isListeningActive && !isWakeModeActive) {
            Log.w(TAG, "[WATCHDOG] SpeechRecognizer timed out cleanly; salvaging partial=\"$latestPartialText\"")
            val salvaged = latestPartialText.trim()
            stopListeningInternal(clearWatchdog = false)
            callbacks.onListeningStateChanged(false, false)
            if (salvaged.length >= 2 && !commandDeliveredForSession) {
                commandDeliveredForSession = true
                callbacks.onCommandRecognized(salvaged)
            } else {
                callbacks.onVoiceError("Listening timed out. Tap the orb and speak clearly.", false)
            }
        }
    }

    private val wakeRestartToken = Any()
    private val speechToken = Any()

    private val wakePhrases = listOf(
        "hey proton",
        "ok proton",
        "hi proton",
        "hello proton",
        "a proton",
        "hey button",
        "ಹೇ ಪ್ರೋಟಾನ್",
        "ಪ್ರೋಟಾನ್",
        "हे प्रोटॉन",
        "proton"
    )

    private val maleVoiceMarkers = listOf(
        "male", "-m-", "_m_", "iom", "iog", "iol", "sfg", "tpd", "tpf", "bho", "khm", "rjs", "en-us-x-iol", "en-us-x-tpd"
    )

    init {
        initTts()
    }

    private fun initTts() {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                Log.i(TAG, "[TTS] Engine initialized successfully")
                applyTtsLanguageAndVoice()
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        Log.d(TAG, "[TTS] Utterance started id=$utteranceId")
                        mainHandler.post { callbacks.onSpeakingStateChanged(true) }
                    }

                    override fun onDone(utteranceId: String?) {
                        Log.d(TAG, "[TTS] Utterance completed id=$utteranceId")
                        mainHandler.post {
                            callbacks.onSpeakingStateChanged(false)
                            callbacks.onSpeechCompletedReadyForFollowUp()
                            if (shouldKeepWakeLoop && !isListeningActive) {
                                scheduleWakeRestart(350L)
                            }
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        Log.w(TAG, "[TTS] Utterance error id=$utteranceId")
                        mainHandler.post { callbacks.onSpeakingStateChanged(false) }
                    }
                })
            } else {
                Log.w(TAG, "[TTS] Initialization failed status=$status")
                mainHandler.post {
                    callbacks.onAvailableVoicesUpdated(emptyList(), false)
                }
            }
        }
    }

    fun setLanguageTag(tag: String) {
        if (currentLanguageTag != tag) {
            currentLanguageTag = tag
            applyTtsLanguageAndVoice()
        }
    }

    fun setPreferredVoice(voiceName: String?) {
        preferredVoiceName = voiceName
        applyTtsLanguageAndVoice()
    }

    fun setVoiceTuning(preferMale: Boolean, rate: Float, volume: Float, offlineRecognition: Boolean) {
        preferMaleVoice = preferMale
        speechRate = rate.coerceIn(0.6f, 1.6f)
        speechVolume = volume.coerceIn(0.2f, 1.0f)
        preferOfflineRecognition = offlineRecognition
        applyTtsLanguageAndVoice()
    }

    private fun applyTtsLanguageAndVoice() {
        if (!ttsReady) return
        val engine = tts ?: return
        val targetLocale = Locale.forLanguageTag(currentLanguageTag)
        val availability = engine.setLanguage(targetLocale)
        val isSupported = availability != TextToSpeech.LANG_MISSING_DATA &&
            availability != TextToSpeech.LANG_NOT_SUPPORTED

        if (!isSupported) {
            Log.w(TAG, "[TTS] Locale $currentLanguageTag missing or unsupported; using fallback")
            engine.language = Locale.getDefault()
        }

        engine.setSpeechRate(speechRate)

        val allVoices = try {
            engine.voices?.toList().orEmpty()
        } catch (e: Exception) {
            Log.w(TAG, "[TTS] Unable to query TTS voices", e)
            emptyList()
        }

        val matchingVoices = allVoices.filter { v ->
            v.locale.language.equals(targetLocale.language, ignoreCase = true)
        }.ifEmpty {
            allVoices.take(10)
        }

        val chosenVoice: Voice? = if (!preferredVoiceName.isNullOrBlank()) {
            allVoices.firstOrNull { it.name == preferredVoiceName }
        } else if (preferMaleVoice) {
            matchingVoices.firstOrNull { isLikelyMaleVoice(it) && !it.isNetworkConnectionRequired }
                ?: matchingVoices.firstOrNull { isLikelyMaleVoice(it) }
                ?: matchingVoices.firstOrNull { !it.isNetworkConnectionRequired }
        } else {
            matchingVoices.firstOrNull { !it.isNetworkConnectionRequired }
        }

        if (chosenVoice != null) {
            try {
                engine.voice = chosenVoice
                Log.d(TAG, "[TTS] Selected voice=${chosenVoice.name} maleHint=${isLikelyMaleVoice(chosenVoice)} rate=$speechRate vol=$speechVolume")
            } catch (e: Exception) {
                Log.w(TAG, "[TTS] Failed applying voice ${chosenVoice.name}", e)
            }
        }

        val options = matchingVoices
            .sortedWith(compareByDescending<Voice> { isLikelyMaleVoice(it) }.thenBy { it.isNetworkConnectionRequired })
            .take(12)
            .mapIndexed { idx, v ->
                val modeTag = if (v.isNetworkConnectionRequired) "Cloud" else "Local"
                val genderTag = if (isLikelyMaleVoice(v)) " • Male" else ""
                ProtonVoiceOption(
                    name = v.name,
                    localeTag = v.locale.toLanguageTag(),
                    displayLabel = "${v.locale.displayLanguage} Voice ${idx + 1} ($modeTag$genderTag)",
                    isNetworkRequired = v.isNetworkConnectionRequired,
                    isLikelyMale = isLikelyMaleVoice(v)
                )
            }

        mainHandler.post {
            callbacks.onAvailableVoicesUpdated(options, isSupported)
        }
    }

    private fun isLikelyMaleVoice(voice: Voice): Boolean {
        val nameLower = voice.name.lowercase(Locale.ROOT)
        val featuresStr = voice.features?.joinToString(" ")?.lowercase(Locale.ROOT).orEmpty()
        return maleVoiceMarkers.any { nameLower.contains(it) || featuresStr.contains(it) }
    }

    fun speak(text: String) {
        val clean = text.trim()
        if (clean.isBlank() || !ttsReady) return
        mainHandler.postAtTime({
            stopListeningInternal()
            val utteranceId = "proton_${System.currentTimeMillis()}"
            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, speechVolume)
            }
            tts?.setSpeechRate(speechRate)
            tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        }, speechToken, android.os.SystemClock.uptimeMillis())
    }

    fun stopSpeaking() {
        mainHandler.post {
            tts?.stop()
            callbacks.onSpeakingStateChanged(false)
        }
    }

    fun isSpeechRecognitionAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun startCommandListening() {
        mainHandler.post {
            if (!isSpeechRecognitionAvailable()) {
                callbacks.onVoiceError("Speech recognition service is unavailable on this device.", false)
                return@post
            }
            stopSpeaking()
            isWakeModeActive = false
            retryCountForBusy = 0
            startRecognizerSession(isWakeLoop = false)
        }
    }

    fun setWakeWordMonitoring(enabled: Boolean) {
        mainHandler.post {
            shouldKeepWakeLoop = enabled
            if (enabled) {
                if (!isSpeechRecognitionAvailable()) {
                    shouldKeepWakeLoop = false
                    callbacks.onVoiceError("Speech recognition unavailable for Hey Proton wake word.", true)
                    return@post
                }
                if (!isListeningActive) {
                    startRecognizerSession(isWakeLoop = true)
                }
            } else if (isWakeModeActive) {
                stopListeningInternal()
            }
        }
    }

    fun stopAllListening() {
        mainHandler.post {
            shouldKeepWakeLoop = false
            stopListeningInternal()
            callbacks.onListeningStateChanged(false, false)
        }
    }

    fun pauseListeningPreserveWakeFlag() {
        mainHandler.post {
            stopListeningInternal()
            callbacks.onListeningStateChanged(false, isWakeModeActive)
        }
    }

    fun resumeWakeIfEnabled() {
        mainHandler.post {
            if (shouldKeepWakeLoop && !isListeningActive) {
                startRecognizerSession(isWakeLoop = true)
            }
        }
    }

    private fun startRecognizerSession(isWakeLoop: Boolean) {
        stopListeningInternal()
        val sessionId = ++sessionIdCounter
        activeSessionId = sessionId
        latestPartialText = ""
        commandDeliveredForSession = false

        try {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
            speechRecognizer = recognizer
            isListeningActive = true
            isWakeModeActive = isWakeLoop

            if (!isWakeLoop) {
                armWatchdog(11_000L)
            }

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    if (sessionId != activeSessionId) return
                    Log.d(TAG, "[STT] Ready for speech session=$sessionId wake=$isWakeLoop lang=$currentLanguageTag")
                    callbacks.onListeningStateChanged(true, isWakeLoop)
                }

                override fun onBeginningOfSpeech() {
                    if (sessionId != activeSessionId) return
                    if (!isWakeLoop) {
                        armWatchdog(12_000L)
                    }
                }

                override fun onRmsChanged(rmsdB: Float) {
                    if (sessionId != activeSessionId) return
                    val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.05f, 1f)
                    callbacks.onRmsChanged(normalized)
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    if (sessionId != activeSessionId) return
                    Log.d(TAG, "[STT] End of speech detected session=$sessionId")
                    if (!isWakeLoop) {
                        // Give SpeechRecognizer up to 3.5s to return final results before watchdog salvages partial text
                        armWatchdog(3_500L)
                        callbacks.onListeningStateChanged(false, false)
                    }
                }

                override fun onError(error: Int) {
                    if (sessionId != activeSessionId) return
                    clearWatchdog()
                    isListeningActive = false
                    callbacks.onListeningStateChanged(false, isWakeLoop)
                    callbacks.onRmsChanged(0f)

                    if (isWakeLoop && shouldKeepWakeLoop) {
                        val delay = if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 850L else 450L
                        scheduleWakeRestart(delay)
                        return
                    }

                    // Salvage meaningful partial transcript if recognizer ended with NO_MATCH/TIMEOUT after hearing speech
                    val salvaged = latestPartialText.trim()
                    if ((error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) &&
                        salvaged.length >= 2 && !commandDeliveredForSession
                    ) {
                        Log.i(TAG, "[STT] Recovering command from partial transcript after error=$error: \"$salvaged\"")
                        commandDeliveredForSession = true
                        callbacks.onCommandRecognized(salvaged)
                        return
                    }

                    if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY && retryCountForBusy < 1) {
                        retryCountForBusy++
                        Log.w(TAG, "[STT] Recognizer busy; auto-retrying once in 450ms")
                        mainHandler.postDelayed({ startRecognizerSession(isWakeLoop = false) }, 450L)
                        return
                    }

                    val message = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error. Check microphone availability."
                        SpeechRecognizer.ERROR_CLIENT -> return
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required."
                        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                            "Speech pack for $currentLanguageTag is not installed on this device. Install it in Google Speech Services or type your command."
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                            "Speech network error. Check connection or download offline speech pack."
                        SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that. Tap the sphere and try again."
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Voice engine was busy. Please tap again."
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Tap the sphere when ready."
                        else -> "Voice recognition error ($error)."
                    }
                    Log.w(TAG, "[STT] Error=$error msg=\"$message\"")
                    callbacks.onVoiceError(message, false)
                }

                override fun onResults(results: Bundle?) {
                    if (sessionId != activeSessionId) return
                    clearWatchdog()
                    isListeningActive = false
                    callbacks.onListeningStateChanged(false, isWakeLoop)
                    callbacks.onRmsChanged(0f)

                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val best = matches?.firstOrNull()?.trim().orEmpty().ifBlank { latestPartialText.trim() }
                    Log.i(TAG, "[STT] Final results session=$sessionId wake=$isWakeLoop text=\"$best\"")

                    if (isWakeLoop) {
                        if (containsWakeWord(best)) {
                            val remainder = stripWakeWord(best)
                            callbacks.onWakeWordDetected()
                            if (remainder.length > 2) {
                                callbacks.onCommandRecognized(remainder)
                            } else {
                                mainHandler.post { startRecognizerSession(isWakeLoop = false) }
                            }
                        } else if (shouldKeepWakeLoop) {
                            scheduleWakeRestart(300L)
                        }
                    } else {
                        if (best.isNotBlank() && !commandDeliveredForSession) {
                            commandDeliveredForSession = true
                            callbacks.onCommandRecognized(best)
                        } else if (shouldKeepWakeLoop) {
                            scheduleWakeRestart(400L)
                        } else {
                            callbacks.onVoiceError("No speech recognized. Please try again.", false)
                        }
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    if (sessionId != activeSessionId) return
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val partial = matches?.firstOrNull()?.trim().orEmpty()
                    if (partial.isNotBlank()) {
                        latestPartialText = partial
                        if (isWakeLoop) {
                            if (containsWakeWord(partial)) {
                                val remainder = stripWakeWord(partial)
                                activeSessionId++ // Prevent stale callbacks from torn-down recognizer
                                callbacks.onWakeWordDetected()
                                mainHandler.post {
                                    if (remainder.length > 3) {
                                        stopListeningInternal()
                                        callbacks.onCommandRecognized(remainder)
                                    } else {
                                        startRecognizerSession(isWakeLoop = false)
                                    }
                                }
                            }
                        } else {
                            callbacks.onPartialTranscript(partial)
                        }
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLanguageTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, currentLanguageTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1300L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 950L)
                if (preferOfflineRecognition) {
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                }
            }

            recognizer.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "[STT] Failed to start SpeechRecognizer", e)
            clearWatchdog()
            isListeningActive = false
            callbacks.onListeningStateChanged(false, isWakeLoop)
            if (!isWakeLoop) {
                callbacks.onVoiceError(e.localizedMessage ?: "Failed to start speech recognizer.", false)
            }
        }
    }

    private fun armWatchdog(delayMs: Long) {
        mainHandler.removeCallbacks(watchdogRunnable)
        mainHandler.postDelayed(watchdogRunnable, delayMs)
    }

    private fun clearWatchdog() {
        mainHandler.removeCallbacks(watchdogRunnable)
    }

    private fun scheduleWakeRestart(delayMs: Long) {
        mainHandler.removeCallbacksAndMessages(wakeRestartToken)
        mainHandler.postAtTime({
            if (shouldKeepWakeLoop && !isListeningActive) {
                startRecognizerSession(isWakeLoop = true)
            }
        }, wakeRestartToken, android.os.SystemClock.uptimeMillis() + delayMs)
    }

    private fun stopListeningInternal(clearWatchdog: Boolean = true) {
        if (clearWatchdog) {
            clearWatchdog()
        }
        mainHandler.removeCallbacksAndMessages(wakeRestartToken)
        activeSessionId++
        isListeningActive = false
        isWakeModeActive = false
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "[STT] Error destroying SpeechRecognizer", e)
        } finally {
            speechRecognizer = null
        }
        callbacks.onRmsChanged(0f)
    }

    private fun containsWakeWord(transcript: String): Boolean {
        val normalized = transcript.lowercase(Locale.ROOT)
        return wakePhrases.any { normalized.contains(it) }
    }

    private fun stripWakeWord(transcript: String): String {
        var result = transcript
        for (phrase in wakePhrases) {
            result = result.replace(Regex(Regex.escape(phrase), RegexOption.IGNORE_CASE), "").trim()
        }
        return result.trim(',', '.', ' ')
    }

    fun release() {
        clearWatchdog()
        mainHandler.removeCallbacksAndMessages(wakeRestartToken)
        mainHandler.removeCallbacksAndMessages(speechToken)
        shouldKeepWakeLoop = false
        stopListeningInternal()
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
    }
}
