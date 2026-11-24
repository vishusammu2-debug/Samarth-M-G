package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.AiEngineMode
import com.example.domain.FloatingBubbleConfig
import com.example.domain.PerformanceProfileMode
import com.example.domain.PremiumMembershipState
import com.example.domain.PremiumStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.protonDataStore: DataStore<Preferences> by preferencesDataStore(name = "proton_settings")

data class ProtonUserPreferences(
    val languageId: String = "en-US",
    val autoDetectLanguage: Boolean = true,
    val selectedVoiceName: String? = null,
    val preferMaleVoice: Boolean = true,
    val speechRate: Float = 1.0f,
    val speechVolume: Float = 1.0f,
    val continuousConversation: Boolean = false,
    val aiMode: AiEngineMode = AiEngineMode.ONLINE_GEMINI,
    val performanceMode: PerformanceProfileMode = PerformanceProfileMode.AUTO,
    val ttsEnabled: Boolean = true,
    val wakeWordEnabled: Boolean = false,
    val floatingBubble: FloatingBubbleConfig = FloatingBubbleConfig(),
    val overlayPromptDismissed: Boolean = false,
    val reelsModeEnabled: Boolean = false,
    val premiumMembership: PremiumMembershipState = PremiumMembershipState()
)

class ProtonPreferencesRepository(private val context: Context) {

    private object Keys {
        val LANGUAGE_ID = stringPreferencesKey("language_tag")
        val AUTO_DETECT_LANGUAGE = booleanPreferencesKey("auto_detect_language")
        val SELECTED_VOICE_NAME = stringPreferencesKey("selected_voice_name")
        val PREFER_MALE_VOICE = booleanPreferencesKey("prefer_male_voice")
        val SPEECH_RATE = floatPreferencesKey("speech_rate")
        val SPEECH_VOLUME = floatPreferencesKey("speech_volume")
        val CONTINUOUS_CONVERSATION = booleanPreferencesKey("continuous_conversation")
        val AI_MODE = stringPreferencesKey("ai_mode")
        val PERFORMANCE_MODE = stringPreferencesKey("performance_mode")
        val TTS_ENABLED = booleanPreferencesKey("tts_enabled")
        val WAKE_WORD_ENABLED = booleanPreferencesKey("wake_word_enabled")
        val FLOATING_BUBBLE_ENABLED = booleanPreferencesKey("floating_bubble_enabled")
        val ROTATING_RINGS_ENABLED = booleanPreferencesKey("neon_animation_enabled")
        val ELECTRIC_ARCS_ENABLED = booleanPreferencesKey("lightning_effects_enabled")
        val BUBBLE_SIZE_DP = floatPreferencesKey("bubble_size_dp")
        val ANIMATION_SPEED = floatPreferencesKey("animation_speed")
        val BUBBLE_OPACITY = floatPreferencesKey("bubble_opacity")
        val GLOW_INTENSITY = floatPreferencesKey("glow_intensity")
        val PARTICLE_INTENSITY = floatPreferencesKey("particle_intensity")
        val OVERLAY_PROMPT_DISMISSED = booleanPreferencesKey("overlay_prompt_dismissed")
        val REELS_MODE_ENABLED = booleanPreferencesKey("reels_mode_enabled")
        val PREMIUM_STATUS = stringPreferencesKey("premium_status")
        val PREMIUM_PURCHASED_AT = longPreferencesKey("premium_purchased_at")
        val PREMIUM_EXPIRES_AT = longPreferencesKey("premium_expires_at")
        val PREMIUM_ORDER_ID = stringPreferencesKey("premium_order_id")
        val PREMIUM_PAYMENT_ID = stringPreferencesKey("premium_payment_id")
        val PREMIUM_PROVIDER_NAME = stringPreferencesKey("premium_provider_name")
        val PREMIUM_BENEFICIARY_UPI = stringPreferencesKey("premium_beneficiary_upi")
        val PREMIUM_BENEFICIARY_LABEL = stringPreferencesKey("premium_beneficiary_label")
        val PREMIUM_BENEFICIARY_PERMISSION = booleanPreferencesKey("premium_beneficiary_permission")
        val PREMIUM_VERIFICATION_ENDPOINT = stringPreferencesKey("premium_verification_endpoint")
        val PREMIUM_STATUS_NOTE = stringPreferencesKey("premium_status_note")
    }

    val preferencesFlow: Flow<ProtonUserPreferences> = context.protonDataStore.data.map { prefs ->
        val modeName = prefs[Keys.AI_MODE] ?: AiEngineMode.ONLINE_GEMINI.name
        val mode = AiEngineMode.entries.firstOrNull { it.name == modeName } ?: AiEngineMode.ONLINE_GEMINI
        val perfName = prefs[Keys.PERFORMANCE_MODE] ?: PerformanceProfileMode.AUTO.name
        val perfMode = PerformanceProfileMode.entries.firstOrNull { it.name == perfName } ?: PerformanceProfileMode.AUTO
        val premiumStatusName = prefs[Keys.PREMIUM_STATUS] ?: PremiumStatus.LOCKED.name
        val premiumStatus = PremiumStatus.entries.firstOrNull { it.name == premiumStatusName } ?: PremiumStatus.LOCKED

        val storedUpi = prefs[Keys.PREMIUM_BENEFICIARY_UPI]
        val storedLabel = prefs[Keys.PREMIUM_BENEFICIARY_LABEL]
        val defaultLabel = "SHOBHA M GUNDA... (SBI ••5871)"
        val storedEndpoint = prefs[Keys.PREMIUM_VERIFICATION_ENDPOINT]

        val membership = PremiumMembershipState(
            status = premiumStatus,
            purchasedAtMillis = prefs[Keys.PREMIUM_PURCHASED_AT],
            expiresAtMillis = prefs[Keys.PREMIUM_EXPIRES_AT],
            providerOrderId = prefs[Keys.PREMIUM_ORDER_ID]?.takeIf { it.isNotBlank() },
            providerPaymentId = prefs[Keys.PREMIUM_PAYMENT_ID]?.takeIf { it.isNotBlank() },
            providerName = prefs[Keys.PREMIUM_PROVIDER_NAME]?.takeIf { it.isNotBlank() },
            beneficiaryUpiHandle = storedUpi?.takeIf { it.isNotBlank() } ?: "",
            beneficiaryAccountLabel = storedLabel?.takeIf { it.isNotBlank() } ?: defaultLabel,
            beneficiaryPermissionGranted = prefs[Keys.PREMIUM_BENEFICIARY_PERMISSION] ?: false,
            verificationEndpoint = storedEndpoint?.takeIf { it.isNotBlank() } ?: "",
            statusNote = prefs[Keys.PREMIUM_STATUS_NOTE]?.takeIf { it.isNotBlank() }
        )

        ProtonUserPreferences(
            languageId = prefs[Keys.LANGUAGE_ID] ?: "en-US",
            autoDetectLanguage = prefs[Keys.AUTO_DETECT_LANGUAGE] ?: true,
            selectedVoiceName = prefs[Keys.SELECTED_VOICE_NAME]?.takeIf { it.isNotBlank() },
            preferMaleVoice = prefs[Keys.PREFER_MALE_VOICE] ?: true,
            speechRate = (prefs[Keys.SPEECH_RATE] ?: 1.0f).coerceIn(0.6f, 1.6f),
            speechVolume = (prefs[Keys.SPEECH_VOLUME] ?: 1.0f).coerceIn(0.2f, 1.0f),
            continuousConversation = prefs[Keys.CONTINUOUS_CONVERSATION] ?: false,
            aiMode = mode,
            performanceMode = perfMode,
            ttsEnabled = prefs[Keys.TTS_ENABLED] ?: true,
            wakeWordEnabled = prefs[Keys.WAKE_WORD_ENABLED] ?: false,
            floatingBubble = FloatingBubbleConfig(
                enabled = prefs[Keys.FLOATING_BUBBLE_ENABLED] ?: false,
                rotatingRingsEnabled = prefs[Keys.ROTATING_RINGS_ENABLED] ?: true,
                electricArcsEnabled = prefs[Keys.ELECTRIC_ARCS_ENABLED] ?: true,
                sizeDp = (prefs[Keys.BUBBLE_SIZE_DP] ?: 76f).coerceIn(56f, 108f),
                animationSpeed = (prefs[Keys.ANIMATION_SPEED] ?: 1.0f).coerceIn(0.4f, 2.0f),
                opacity = (prefs[Keys.BUBBLE_OPACITY] ?: 0.92f).coerceIn(0.35f, 1.0f),
                glowIntensity = (prefs[Keys.GLOW_INTENSITY] ?: 1.0f).coerceIn(0.2f, 2.0f),
                particleIntensity = (prefs[Keys.PARTICLE_INTENSITY] ?: 1.0f).coerceIn(0.0f, 1.5f)
            ),
            overlayPromptDismissed = prefs[Keys.OVERLAY_PROMPT_DISMISSED] ?: false,
            reelsModeEnabled = prefs[Keys.REELS_MODE_ENABLED] ?: false,
            premiumMembership = membership
        )
    }

    suspend fun setLanguageId(id: String) {
        context.protonDataStore.edit { it[Keys.LANGUAGE_ID] = id }
    }

    suspend fun setAutoDetectLanguage(enabled: Boolean) {
        context.protonDataStore.edit { it[Keys.AUTO_DETECT_LANGUAGE] = enabled }
    }

    suspend fun setSelectedVoiceName(voiceName: String?) {
        context.protonDataStore.edit {
            if (voiceName.isNullOrBlank()) {
                it.remove(Keys.SELECTED_VOICE_NAME)
            } else {
                it[Keys.SELECTED_VOICE_NAME] = voiceName
            }
        }
    }

    suspend fun setPreferMaleVoice(preferMale: Boolean) {
        context.protonDataStore.edit { it[Keys.PREFER_MALE_VOICE] = preferMale }
    }

    suspend fun setSpeechRate(rate: Float) {
        context.protonDataStore.edit { it[Keys.SPEECH_RATE] = rate.coerceIn(0.6f, 1.6f) }
    }

    suspend fun setSpeechVolume(volume: Float) {
        context.protonDataStore.edit { it[Keys.SPEECH_VOLUME] = volume.coerceIn(0.2f, 1.0f) }
    }

    suspend fun setContinuousConversation(enabled: Boolean) {
        context.protonDataStore.edit { it[Keys.CONTINUOUS_CONVERSATION] = enabled }
    }

    suspend fun setAiMode(mode: AiEngineMode) {
        context.protonDataStore.edit { it[Keys.AI_MODE] = mode.name }
    }

    suspend fun setPerformanceMode(mode: PerformanceProfileMode) {
        context.protonDataStore.edit { it[Keys.PERFORMANCE_MODE] = mode.name }
    }

    suspend fun setTtsEnabled(enabled: Boolean) {
        context.protonDataStore.edit { it[Keys.TTS_ENABLED] = enabled }
    }

    suspend fun setWakeWordEnabled(enabled: Boolean) {
        context.protonDataStore.edit { it[Keys.WAKE_WORD_ENABLED] = enabled }
    }

    suspend fun setFloatingBubbleEnabled(enabled: Boolean) {
        context.protonDataStore.edit { it[Keys.FLOATING_BUBBLE_ENABLED] = enabled }
    }

    suspend fun setRotatingRingsEnabled(enabled: Boolean) {
        context.protonDataStore.edit { it[Keys.ROTATING_RINGS_ENABLED] = enabled }
    }

    suspend fun setElectricArcsEnabled(enabled: Boolean) {
        context.protonDataStore.edit { it[Keys.ELECTRIC_ARCS_ENABLED] = enabled }
    }

    suspend fun setBubbleSizeDp(sizeDp: Float) {
        context.protonDataStore.edit { it[Keys.BUBBLE_SIZE_DP] = sizeDp.coerceIn(56f, 108f) }
    }

    suspend fun setAnimationSpeed(speed: Float) {
        context.protonDataStore.edit { it[Keys.ANIMATION_SPEED] = speed.coerceIn(0.4f, 2.0f) }
    }

    suspend fun setBubbleOpacity(opacity: Float) {
        context.protonDataStore.edit { it[Keys.BUBBLE_OPACITY] = opacity.coerceIn(0.35f, 1.0f) }
    }

    suspend fun setGlowIntensity(intensity: Float) {
        context.protonDataStore.edit { it[Keys.GLOW_INTENSITY] = intensity.coerceIn(0.2f, 2.0f) }
    }

    suspend fun setParticleIntensity(intensity: Float) {
        context.protonDataStore.edit { it[Keys.PARTICLE_INTENSITY] = intensity.coerceIn(0.0f, 1.5f) }
    }

    suspend fun setReelsModeEnabled(enabled: Boolean) {
        context.protonDataStore.edit { it[Keys.REELS_MODE_ENABLED] = enabled }
    }

    suspend fun updateBeneficiaryAndVerificationConfig(
        upiHandle: String,
        accountLabel: String,
        permissionGranted: Boolean,
        verificationEndpoint: String
    ) {
        context.protonDataStore.edit {
            it[Keys.PREMIUM_BENEFICIARY_UPI] = upiHandle.trim()
            it[Keys.PREMIUM_BENEFICIARY_LABEL] = accountLabel.trim()
            it[Keys.PREMIUM_BENEFICIARY_PERMISSION] = permissionGranted
            it[Keys.PREMIUM_VERIFICATION_ENDPOINT] = verificationEndpoint.trim()
        }
    }

    suspend fun savePremiumMembership(membership: PremiumMembershipState) {
        context.protonDataStore.edit { prefs ->
            prefs[Keys.PREMIUM_STATUS] = membership.status.name
            if (membership.purchasedAtMillis != null) {
                prefs[Keys.PREMIUM_PURCHASED_AT] = membership.purchasedAtMillis
            } else {
                prefs.remove(Keys.PREMIUM_PURCHASED_AT)
            }
            if (membership.expiresAtMillis != null) {
                prefs[Keys.PREMIUM_EXPIRES_AT] = membership.expiresAtMillis
            } else {
                prefs.remove(Keys.PREMIUM_EXPIRES_AT)
            }
            if (!membership.providerOrderId.isNullOrBlank()) {
                prefs[Keys.PREMIUM_ORDER_ID] = membership.providerOrderId
            } else {
                prefs.remove(Keys.PREMIUM_ORDER_ID)
            }
            if (!membership.providerPaymentId.isNullOrBlank()) {
                prefs[Keys.PREMIUM_PAYMENT_ID] = membership.providerPaymentId
            } else {
                prefs.remove(Keys.PREMIUM_PAYMENT_ID)
            }
            if (!membership.providerName.isNullOrBlank()) {
                prefs[Keys.PREMIUM_PROVIDER_NAME] = membership.providerName
            } else {
                prefs.remove(Keys.PREMIUM_PROVIDER_NAME)
            }
            prefs[Keys.PREMIUM_BENEFICIARY_UPI] = membership.beneficiaryUpiHandle
            prefs[Keys.PREMIUM_BENEFICIARY_LABEL] = membership.beneficiaryAccountLabel
            prefs[Keys.PREMIUM_BENEFICIARY_PERMISSION] = membership.beneficiaryPermissionGranted
            prefs[Keys.PREMIUM_VERIFICATION_ENDPOINT] = membership.verificationEndpoint
            if (!membership.statusNote.isNullOrBlank()) {
                prefs[Keys.PREMIUM_STATUS_NOTE] = membership.statusNote
            } else {
                prefs.remove(Keys.PREMIUM_STATUS_NOTE)
            }
        }
    }
}
