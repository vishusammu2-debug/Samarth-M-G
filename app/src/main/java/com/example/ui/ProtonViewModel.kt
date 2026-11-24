package com.example.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CommandRouteDecision
import com.example.data.DevicePerformanceProfiler
import com.example.data.LanguageDetector
import com.example.data.OfflineAiEngine
import com.example.data.OnlineAiRepository
import com.example.data.OrderCreationResult
import com.example.data.PaymentVerificationResult
import com.example.data.ProtonCommandRouter
import com.example.data.ProtonPreferencesRepository
import com.example.data.ProtonPremiumRepository
import com.example.data.ProtonVoiceController
import com.example.data.SafePhoneControlExecutor
import com.example.data.StreamChunkEvent
import com.example.data.VoiceControllerCallbacks
import com.example.domain.AiEngineMode
import com.example.domain.AssistantLanguage
import com.example.domain.ChatMessage
import com.example.domain.CoreState
import com.example.domain.DeviceTelemetry
import com.example.domain.FloatingBubbleConfig
import com.example.domain.OrbVisualState
import com.example.domain.PendingConfirmationAction
import com.example.domain.PerformanceProfileMode
import com.example.domain.PhoneControlAction
import com.example.domain.PremiumMembershipState
import com.example.domain.PremiumStatus
import com.example.domain.ProtonVoiceOption
import com.example.domain.ResolvedPerformanceTier
import com.example.domain.SupportedLanguages
import com.example.service.OverlayPermissionHelper
import com.example.service.ProtonAccessibilityService
import com.example.service.ProtonCoreStateBus
import com.example.service.ProtonForegroundService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private const val TAG = "ProtonViewModel"

data class ProtonUiState(
    val coreState: CoreState = CoreState.IDLE,
    val orbState: OrbVisualState = OrbVisualState.IDLE,
    val selectedLanguage: AssistantLanguage = SupportedLanguages.all.first(),
    val autoDetectLanguage: Boolean = true,
    val availableVoices: List<ProtonVoiceOption> = emptyList(),
    val selectedVoiceName: String? = null,
    val preferMaleVoice: Boolean = true,
    val speechRate: Float = 1.0f,
    val speechVolume: Float = 1.0f,
    val continuousConversation: Boolean = false,
    val isTtsLanguageSupported: Boolean = true,
    val aiMode: AiEngineMode = AiEngineMode.ONLINE_GEMINI,
    val performanceMode: PerformanceProfileMode = PerformanceProfileMode.AUTO,
    val resolvedPerformanceTier: ResolvedPerformanceTier = ResolvedPerformanceTier.BALANCED,
    val ttsEnabled: Boolean = true,
    val wakeWordEnabled: Boolean = false,
    val hasMicPermission: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val hasNotificationPermission: Boolean = true,
    val floatingBubble: FloatingBubbleConfig = FloatingBubbleConfig(),
    val isBackgroundServiceRunning: Boolean = false,
    val isBubbleCurrentlyVisible: Boolean = false,
    val isAccessibilityEnabled: Boolean = false,
    val showOverlayRationaleDialog: Boolean = false,
    val pendingConfirmation: PendingConfirmationAction? = null,
    val audioLevel: Float = 0f,
    val partialTranscript: String = "",
    val telemetry: DeviceTelemetry = DeviceTelemetry(),
    val messages: List<ChatMessage> = emptyList(),
    val statusBanner: String? = null,
    val streamingMessageId: String? = null,
    val premiumMembership: PremiumMembershipState = PremiumMembershipState(),
    val isPremiumUnlocked: Boolean = false,
    val reelsModeEnabled: Boolean = false,
    val premiumBusy: Boolean = false,
    val premiumStatusMessage: String? = null,
    val pendingCheckoutUrl: String? = null,
    val navigateToPremiumTrigger: Int = 0
)

class ProtonViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsRepo = ProtonPreferencesRepository(application)
    private val premiumRepo = ProtonPremiumRepository()
    private val controlExecutor = SafePhoneControlExecutor(application)
    private val performanceProfiler = DevicePerformanceProfiler(application)
    private val commandRouter = ProtonCommandRouter()
    private val offlineAiEngine = OfflineAiEngine()
    private val onlineAiRepo = OnlineAiRepository()

    private val _uiState = MutableStateFlow(ProtonUiState())
    val uiState: StateFlow<ProtonUiState> = _uiState.asStateFlow()

    private var activeGenerationJob: Job? = null

    private val voiceController: ProtonVoiceController = ProtonVoiceController(
        context = application,
        callbacks = object : VoiceControllerCallbacks {
            override fun onRmsChanged(normalizedLevel: Float) {
                updateState { it.copy(audioLevel = normalizedLevel) }
            }

            override fun onPartialTranscript(text: String) {
                updateState { it.copy(partialTranscript = text) }
            }

            override fun onCommandRecognized(text: String) {
                Log.i(TAG, "[PIPELINE] Recognized voice text=\"$text\" -> forwarding to command router")
                updateState { it.copy(partialTranscript = "") }
                submitUserQuery(text)
            }

            override fun onWakeWordDetected() {
                updateState {
                    it.copy(
                        coreState = CoreState.LISTENING,
                        statusBanner = "Hey Proton detected — listening for command…"
                    )
                }
            }

            override fun onListeningStateChanged(active: Boolean, isWakeLoop: Boolean) {
                updateState { state ->
                    val nextCore = when {
                        state.coreState == CoreState.PROCESSING -> CoreState.PROCESSING
                        active && isWakeLoop -> CoreState.WAKE_ARMED
                        active -> CoreState.LISTENING
                        state.wakeWordEnabled -> CoreState.WAKE_ARMED
                        else -> CoreState.IDLE
                    }
                    state.copy(coreState = nextCore)
                }
            }

            override fun onSpeakingStateChanged(speaking: Boolean) {
                updateState { state ->
                    val nextCore = when {
                        speaking -> CoreState.SPEAKING
                        state.coreState == CoreState.PROCESSING -> CoreState.PROCESSING
                        state.wakeWordEnabled -> CoreState.WAKE_ARMED
                        else -> CoreState.IDLE
                    }
                    state.copy(coreState = nextCore)
                }
            }

            override fun onVoiceError(message: String, isWakeLoop: Boolean) {
                updateState { state ->
                    state.copy(
                        coreState = if (state.wakeWordEnabled) CoreState.WAKE_ARMED else CoreState.IDLE,
                        partialTranscript = "",
                        statusBanner = message
                    )
                }
            }

            override fun onAvailableVoicesUpdated(
                voices: List<ProtonVoiceOption>,
                isLanguageSupported: Boolean
            ) {
                updateState { state ->
                    state.copy(
                        availableVoices = voices,
                        isTtsLanguageSupported = isLanguageSupported
                    )
                }
            }

            override fun onSpeechCompletedReadyForFollowUp() {
                val state = _uiState.value
                if ((state.continuousConversation || state.pendingConfirmation != null) &&
                    !state.wakeWordEnabled &&
                    hasRecordAudioPermission() &&
                    state.coreState != CoreState.PROCESSING
                ) {
                    Log.d(TAG, "[PIPELINE] Continuous/confirmation follow-up: re-arming voice recognition")
                    voiceController.startCommandListening()
                }
            }
        }
    )

    init {
        refreshSystemPermissionsAndServices()
        refreshTelemetry()

        viewModelScope.launch {
            prefsRepo.preferencesFlow.collect { prefs ->
                val nowMillis = System.currentTimeMillis()
                val enforcedMembership = ProtonPremiumRepository.enforceExpiryState(
                    prefs.premiumMembership,
                    nowMillis
                )
                if (enforcedMembership != prefs.premiumMembership) {
                    prefsRepo.savePremiumMembership(enforcedMembership)
                }

                val isPremiumActive = enforcedMembership.isPremiumActive(nowMillis)
                val effectiveAiMode = if (!isPremiumActive && prefs.aiMode == AiEngineMode.OFFLINE_LOCAL) {
                    prefsRepo.setAiMode(AiEngineMode.ONLINE_GEMINI)
                    AiEngineMode.ONLINE_GEMINI
                } else {
                    prefs.aiMode
                }
                val effectiveReelsMode = if (!isPremiumActive && prefs.reelsModeEnabled) {
                    prefsRepo.setReelsModeEnabled(false)
                    false
                } else {
                    prefs.reelsModeEnabled
                }

                val lang = SupportedLanguages.findById(prefs.languageId)
                voiceController.setLanguageTag(lang.bcp47Tag)
                voiceController.setPreferredVoice(prefs.selectedVoiceName)
                voiceController.setVoiceTuning(
                    preferMale = prefs.preferMaleVoice,
                    rate = prefs.speechRate,
                    volume = prefs.speechVolume,
                    offlineRecognition = effectiveAiMode == AiEngineMode.OFFLINE_LOCAL
                )

                val micGranted = hasRecordAudioPermission()
                val overlayGranted = OverlayPermissionHelper.canDrawOverlays(getApplication())

                if (prefs.floatingBubble.enabled && !overlayGranted) {
                    prefsRepo.setFloatingBubbleEnabled(false)
                    ProtonForegroundService.stop(getApplication())
                } else if (prefs.floatingBubble.enabled && overlayGranted) {
                    ProtonForegroundService.startOrUpdate(getApplication())
                }

                updateState { current ->
                    val nextCore = when {
                        current.coreState == CoreState.LISTENING ||
                            current.coreState == CoreState.PROCESSING ||
                            current.coreState == CoreState.SPEAKING -> current.coreState
                        prefs.wakeWordEnabled && micGranted -> CoreState.WAKE_ARMED
                        else -> CoreState.IDLE
                    }
                    val resolvedTier = performanceProfiler.resolveActiveTier(
                        mode = prefs.performanceMode,
                        report = current.telemetry.capabilities,
                        memoryPressureActive = ProtonCoreStateBus.memoryPressureActive.value
                    )
                    current.copy(
                        selectedLanguage = lang,
                        autoDetectLanguage = prefs.autoDetectLanguage,
                        selectedVoiceName = prefs.selectedVoiceName,
                        preferMaleVoice = prefs.preferMaleVoice,
                        speechRate = prefs.speechRate,
                        speechVolume = prefs.speechVolume,
                        continuousConversation = prefs.continuousConversation,
                        aiMode = effectiveAiMode,
                        performanceMode = prefs.performanceMode,
                        resolvedPerformanceTier = resolvedTier,
                        ttsEnabled = prefs.ttsEnabled,
                        wakeWordEnabled = prefs.wakeWordEnabled,
                        floatingBubble = prefs.floatingBubble.copy(
                            enabled = prefs.floatingBubble.enabled && overlayGranted
                        ),
                        hasMicPermission = micGranted,
                        hasOverlayPermission = overlayGranted,
                        coreState = nextCore,
                        premiumMembership = enforcedMembership,
                        isPremiumUnlocked = isPremiumActive,
                        reelsModeEnabled = effectiveReelsMode
                    )
                }

                if (prefs.wakeWordEnabled && micGranted) {
                    voiceController.setWakeWordMonitoring(true)
                } else {
                    voiceController.setWakeWordMonitoring(false)
                }
            }
        }

        viewModelScope.launch {
            ProtonForegroundService.isServiceRunning.collect { running ->
                updateState { it.copy(isBackgroundServiceRunning = running) }
            }
        }

        viewModelScope.launch {
            ProtonForegroundService.isBubbleVisible.collect { visible ->
                updateState { it.copy(isBubbleCurrentlyVisible = visible) }
            }
        }

        viewModelScope.launch {
            ProtonAccessibilityService.isConnected.collect { connected ->
                val actual = connected || ProtonAccessibilityService.isServiceEnabled(getApplication())
                updateState { it.copy(isAccessibilityEnabled = actual) }
            }
        }

        viewModelScope.launch {
            ProtonCoreStateBus.memoryPressureActive.collect { pressure ->
                updateState { state ->
                    val resolvedTier = performanceProfiler.resolveActiveTier(
                        mode = state.performanceMode,
                        report = state.telemetry.capabilities,
                        memoryPressureActive = pressure
                    )
                    state.copy(resolvedPerformanceTier = resolvedTier)
                }
            }
        }

        viewModelScope.launch {
            while (isActive) {
                refreshTelemetry()
                enforceMembershipExpiryIfNeeded()
                delay(15_000L)
            }
        }
    }

    private inline fun updateState(transform: (ProtonUiState) -> ProtonUiState) {
        var resolvedOrb = OrbVisualState.IDLE
        var resolvedAudio = 0f
        var resolvedTier = ResolvedPerformanceTier.BALANCED
        _uiState.update { prev ->
            val updated = transform(prev)
            val orb = resolveOrbState(
                coreState = updated.coreState,
                aiMode = updated.aiMode,
                telemetry = updated.telemetry
            )
            resolvedOrb = orb
            resolvedAudio = updated.audioLevel
            resolvedTier = updated.resolvedPerformanceTier
            updated.copy(orbState = orb)
        }
        ProtonCoreStateBus.updateOrbState(resolvedOrb)
        ProtonCoreStateBus.updateAudioLevel(resolvedAudio)
        ProtonCoreStateBus.updatePerformanceTier(resolvedTier)
    }

    private fun resolveOrbState(
        coreState: CoreState,
        aiMode: AiEngineMode,
        telemetry: DeviceTelemetry
    ): OrbVisualState {
        return when (coreState) {
            CoreState.LISTENING -> OrbVisualState.LISTENING
            CoreState.PROCESSING -> OrbVisualState.THINKING
            CoreState.SPEAKING -> OrbVisualState.SPEAKING
            CoreState.ERROR -> OrbVisualState.ERROR
            CoreState.IDLE, CoreState.WAKE_ARMED -> {
                if (aiMode == AiEngineMode.OFFLINE_LOCAL || !telemetry.isNetworkConnected) {
                    OrbVisualState.OFFLINE
                } else {
                    OrbVisualState.IDLE
                }
            }
        }
    }

    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            getApplication(),
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun refreshSystemPermissionsAndServices() {
        val app = getApplication<Application>()
        val mic = hasRecordAudioPermission()
        val overlay = OverlayPermissionHelper.canDrawOverlays(app)
        val notifications = OverlayPermissionHelper.hasNotificationPermission(app)
        val accessibility = ProtonAccessibilityService.isServiceEnabled(app)
        val serviceRunning = ProtonForegroundService.isServiceRunning.value

        updateState {
            it.copy(
                hasMicPermission = mic,
                hasOverlayPermission = overlay,
                hasNotificationPermission = notifications,
                isAccessibilityEnabled = accessibility,
                isBackgroundServiceRunning = serviceRunning
            )
        }
    }

    fun refreshTelemetry() {
        viewModelScope.launch {
            val telemetry = withContext(Dispatchers.Default) {
                controlExecutor.readTelemetry()
            }
            updateState { state ->
                val tier = performanceProfiler.resolveActiveTier(
                    mode = state.performanceMode,
                    report = telemetry.capabilities,
                    memoryPressureActive = ProtonCoreStateBus.memoryPressureActive.value
                )
                state.copy(
                    telemetry = telemetry,
                    resolvedPerformanceTier = tier
                )
            }
        }
    }

    fun onMicPermissionResult(granted: Boolean) {
        updateState { it.copy(hasMicPermission = granted) }
        if (granted) {
            updateState { it.copy(statusBanner = null) }
            voiceController.startCommandListening()
        } else {
            updateState {
                it.copy(
                    statusBanner = "Microphone permission denied. You can still type commands below."
                )
            }
        }
    }

    fun onNotificationPermissionResult(granted: Boolean) {
        updateState { it.copy(hasNotificationPermission = granted) }
        if (!granted) {
            updateState {
                it.copy(statusBanner = "Notification permission denied; foreground status controls may be hidden by Android.")
            }
        }
    }

    fun triggerVoiceFromOverlay() {
        if (!hasRecordAudioPermission()) {
            updateState { it.copy(statusBanner = "Grant microphone permission to activate Proton voice.") }
            return
        }
        updateState { it.copy(statusBanner = null) }
        voiceController.startCommandListening()
    }

    fun onReactorCoreTapped() {
        val state = _uiState.value
        when (state.coreState) {
            CoreState.SPEAKING -> {
                voiceController.stopSpeaking()
            }
            CoreState.LISTENING -> {
                voiceController.stopAllListening()
                if (state.wakeWordEnabled && state.hasMicPermission) {
                    voiceController.setWakeWordMonitoring(true)
                }
            }
            CoreState.PROCESSING -> {
                activeGenerationJob?.cancel()
                updateState {
                    it.copy(
                        coreState = if (it.wakeWordEnabled) CoreState.WAKE_ARMED else CoreState.IDLE,
                        streamingMessageId = null,
                        statusBanner = "Generation cancelled."
                    )
                }
            }
            else -> {
                if (!state.hasMicPermission) {
                    updateState { it.copy(statusBanner = "Please grant microphone access to speak.") }
                    return
                }
                updateState { it.copy(statusBanner = null) }
                voiceController.startCommandListening()
            }
        }
    }

    fun setFloatingBubbleEnabled(enabled: Boolean) {
        val app = getApplication<Application>()
        if (enabled && !OverlayPermissionHelper.canDrawOverlays(app)) {
            updateState {
                it.copy(
                    hasOverlayPermission = false,
                    showOverlayRationaleDialog = true
                )
            }
            return
        }

        viewModelScope.launch {
            prefsRepo.setFloatingBubbleEnabled(enabled)
            if (enabled) {
                ProtonForegroundService.startOrUpdate(app)
                updateState { it.copy(statusBanner = "Floating 3D Crystal Sphere activated.") }
            } else {
                ProtonForegroundService.stop(app)
                updateState { it.copy(statusBanner = "Floating 3D Crystal Sphere disabled.") }
            }
        }
    }

    fun dismissOverlayRationaleDialog() {
        updateState { it.copy(showOverlayRationaleDialog = false) }
    }

    fun setRotatingRingsEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setRotatingRingsEnabled(enabled) }
    }

    fun setElectricArcsEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setElectricArcsEnabled(enabled) }
    }

    fun setBubbleSizeDp(sizeDp: Float) {
        viewModelScope.launch { prefsRepo.setBubbleSizeDp(sizeDp) }
    }

    fun setAnimationSpeed(speed: Float) {
        viewModelScope.launch { prefsRepo.setAnimationSpeed(speed) }
    }

    fun setBubbleOpacity(opacity: Float) {
        viewModelScope.launch { prefsRepo.setBubbleOpacity(opacity) }
    }

    fun setGlowIntensity(intensity: Float) {
        viewModelScope.launch { prefsRepo.setGlowIntensity(intensity) }
    }

    fun setParticleIntensity(intensity: Float) {
        viewModelScope.launch { prefsRepo.setParticleIntensity(intensity) }
    }

    fun setPerformanceMode(mode: PerformanceProfileMode) {
        viewModelScope.launch {
            prefsRepo.setPerformanceMode(mode)
            val tier = performanceProfiler.resolveActiveTier(
                mode = mode,
                report = _uiState.value.telemetry.capabilities,
                memoryPressureActive = ProtonCoreStateBus.memoryPressureActive.value
            )
            updateState {
                it.copy(
                    performanceMode = mode,
                    resolvedPerformanceTier = tier,
                    statusBanner = "Performance profile: ${mode.label} (${tier.label})"
                )
            }
        }
    }

    fun disableAccessibilityServiceImmediately() {
        val disabled = ProtonAccessibilityService.requestDisableService()
        updateState {
            it.copy(
                isAccessibilityEnabled = ProtonAccessibilityService.isServiceEnabled(getApplication()),
                statusBanner = if (disabled) {
                    "Proton Accessibility Service disabled."
                } else {
                    "Accessibility service is already inactive."
                }
            )
        }
    }

    fun selectLanguage(language: AssistantLanguage) {
        viewModelScope.launch {
            prefsRepo.setLanguageId(language.id)
            voiceController.setLanguageTag(language.bcp47Tag)
            val note = if (language.id == "kn-UK") {
                "Switched to Uttara Kannada dialect (${language.nativeName}) via Kannada (kn-IN) speech + dialect AI."
            } else {
                "Voice & AI language switched to ${language.englishName} (${language.nativeName})."
            }
            updateState {
                it.copy(statusBanner = note)
            }
        }
    }

    fun setAutoDetectLanguage(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setAutoDetectLanguage(enabled)
            updateState {
                it.copy(
                    statusBanner = if (enabled) {
                        "Automatic language & Kannada dialect detection enabled."
                    } else {
                        "Automatic language detection disabled."
                    }
                )
            }
        }
    }

    fun selectVoice(voiceName: String?) {
        viewModelScope.launch {
            prefsRepo.setSelectedVoiceName(voiceName)
            voiceController.setPreferredVoice(voiceName)
            updateState {
                it.copy(
                    statusBanner = if (voiceName.isNullOrBlank()) {
                        "TTS voice reset to adaptive profile."
                    } else {
                        "TTS voice updated."
                    }
                )
            }
        }
    }

    fun setPreferMaleVoice(preferMale: Boolean) {
        viewModelScope.launch {
            prefsRepo.setPreferMaleVoice(preferMale)
            updateState {
                it.copy(
                    statusBanner = if (preferMale) {
                        "Male voice priority enabled for TTS."
                    } else {
                        "System default voice priority selected."
                    }
                )
            }
        }
    }

    fun setSpeechRate(rate: Float) {
        viewModelScope.launch { prefsRepo.setSpeechRate(rate) }
    }

    fun setSpeechVolume(volume: Float) {
        viewModelScope.launch { prefsRepo.setSpeechVolume(volume) }
    }

    fun setContinuousConversation(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setContinuousConversation(enabled)
            updateState {
                it.copy(
                    statusBanner = if (enabled) {
                        "Continuous conversation enabled: Proton will listen for follow-ups after speaking."
                    } else {
                        "Continuous conversation disabled."
                    }
                )
            }
        }
    }

    fun previewSelectedVoice() {
        val lang = _uiState.value.selectedLanguage
        val sample = when (lang.id) {
            "kn-UK" -> "ನಮಸ್ಕಾರ ಮಾರಾಯ, ಪ್ರೋಟಾನ್ ೫.೦ ಧ್ವನಿ ಪರೀಕ್ಷೆ ಸಿದ್ಧವಾಗಿದೆ."
            "kn-IN" -> "ನಮಸ್ಕಾರ, ಪ್ರೋಟಾನ್ ೫.೦ ಧ್ವನಿ ಪರೀಕ್ಷೆ ಸಿದ್ಧವಾಗಿದೆ."
            "hi-IN" -> "नमस्कार, प्रोटॉन 5.0 वॉयस परीक्षण सक्रिय है।"
            else -> "Proton 5.0 neural voice check active in ${lang.englishName}."
        }
        voiceController.speak(sample)
    }

    fun setAiMode(mode: AiEngineMode) {
        viewModelScope.launch {
            val state = _uiState.value
            val activeNow = state.premiumMembership.isPremiumActive(System.currentTimeMillis())
            if (mode == AiEngineMode.OFFLINE_LOCAL && !activeNow) {
                updateState {
                    it.copy(
                        isPremiumUnlocked = false,
                        statusBanner = "Offline Mode is a PROTON PREMIUM feature (₹5 for 6 months).",
                        navigateToPremiumTrigger = it.navigateToPremiumTrigger + 1
                    )
                }
                return@launch
            }
            prefsRepo.setAiMode(mode)
            updateState {
                it.copy(statusBanner = "Active engine: ${mode.label}")
            }
        }
    }

    fun setReelsModeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val state = _uiState.value
            val activeNow = state.premiumMembership.isPremiumActive(System.currentTimeMillis())
            if (enabled && !activeNow) {
                updateState {
                    it.copy(
                        isPremiumUnlocked = false,
                        reelsModeEnabled = false,
                        statusBanner = "Reels Mode unlocks with PROTON PREMIUM (₹5 one-time for 6 months).",
                        navigateToPremiumTrigger = it.navigateToPremiumTrigger + 1
                    )
                }
                return@launch
            }
            prefsRepo.setReelsModeEnabled(enabled)
            updateState {
                it.copy(
                    reelsModeEnabled = enabled,
                    statusBanner = if (enabled) {
                        "Reels Mode enabled: short-video hands-free voice responses active."
                    } else {
                        "Reels Mode disabled."
                    }
                )
            }
        }
    }

    fun saveBeneficiaryAndServerConfig(
        upiHandle: String,
        accountLabel: String,
        permissionGranted: Boolean,
        verificationEndpoint: String
    ) {
        viewModelScope.launch {
            prefsRepo.updateBeneficiaryAndVerificationConfig(
                upiHandle = upiHandle,
                accountLabel = accountLabel,
                permissionGranted = permissionGranted,
                verificationEndpoint = verificationEndpoint
            )
            updateState {
                it.copy(
                    premiumStatusMessage = "Payment destination authorization and verification server saved."
                )
            }
        }
    }

    fun startAuthorizedPremiumCheckout() {
        if (_uiState.value.premiumBusy) return
        val currentMembership = _uiState.value.premiumMembership
        viewModelScope.launch {
            updateState {
                it.copy(
                    premiumBusy = true,
                    premiumStatusMessage = "Creating ₹5 one-time order with authorized payment provider…"
                )
            }
            when (val result = premiumRepo.createAuthorizedOrder(currentMembership)) {
                is OrderCreationResult.Success -> {
                    val updated = currentMembership.copy(
                        status = PremiumStatus.PENDING_VERIFICATION,
                        providerOrderId = result.orderId,
                        providerName = result.providerName,
                        statusNote = "Order ${result.orderId} created via ${result.providerName}. Complete payment in the provider checkout, then tap Verify Payment Status."
                    )
                    prefsRepo.savePremiumMembership(updated)
                    updateState {
                        it.copy(
                            premiumBusy = false,
                            premiumMembership = updated,
                            pendingCheckoutUrl = result.checkoutUrl,
                            premiumStatusMessage = updated.statusNote
                        )
                    }
                }
                is OrderCreationResult.Failure -> {
                    val updated = currentMembership.copy(
                        status = if (currentMembership.isPremiumActive()) PremiumStatus.ACTIVE else PremiumStatus.LOCKED,
                        statusNote = result.reason
                    )
                    prefsRepo.savePremiumMembership(updated)
                    updateState {
                        it.copy(
                            premiumBusy = false,
                            premiumMembership = updated,
                            premiumStatusMessage = result.reason
                        )
                    }
                }
            }
        }
    }

    fun consumePendingCheckoutUrl() {
        updateState { it.copy(pendingCheckoutUrl = null) }
    }

    fun verifyPremiumPayment() {
        if (_uiState.value.premiumBusy) return
        val currentMembership = _uiState.value.premiumMembership
        val orderId = currentMembership.providerOrderId.orEmpty()
        viewModelScope.launch {
            updateState {
                it.copy(
                    premiumBusy = true,
                    premiumStatusMessage = "Verifying ₹5 transaction status with payment server…"
                )
            }
            when (
                val verification = premiumRepo.verifyOrderWithBackend(
                    verificationEndpoint = currentMembership.verificationEndpoint,
                    orderId = orderId
                )
            ) {
                is PaymentVerificationResult.Verified -> {
                    val activeState = currentMembership.copy(
                        status = PremiumStatus.ACTIVE,
                        purchasedAtMillis = verification.purchasedAtMillis,
                        expiresAtMillis = verification.expiresAtMillis,
                        providerOrderId = verification.orderId,
                        providerPaymentId = verification.paymentId,
                        providerName = verification.providerName,
                        statusNote = "Verified ₹5 one-time pass active for 6 months (no auto-renewal)."
                    )
                    prefsRepo.savePremiumMembership(activeState)
                    updateState {
                        it.copy(
                            premiumBusy = false,
                            premiumMembership = activeState,
                            isPremiumUnlocked = true,
                            premiumStatusMessage = activeState.statusNote,
                            statusBanner = "PROTON PREMIUM unlocked! Reels Mode and Offline Mode are now available."
                        )
                    }
                }
                is PaymentVerificationResult.Unverified -> {
                    val nextStatus = when {
                        currentMembership.isPremiumActive() -> PremiumStatus.ACTIVE
                        verification.isPending -> PremiumStatus.PENDING_VERIFICATION
                        else -> PremiumStatus.FAILED
                    }
                    val updated = currentMembership.copy(
                        status = nextStatus,
                        statusNote = verification.reason
                    )
                    prefsRepo.savePremiumMembership(updated)
                    updateState {
                        it.copy(
                            premiumBusy = false,
                            premiumMembership = updated,
                            isPremiumUnlocked = updated.isPremiumActive(),
                            premiumStatusMessage = verification.reason
                        )
                    }
                }
            }
        }
    }

    fun clearPremiumStatusMessage() {
        updateState { it.copy(premiumStatusMessage = null) }
    }

    private fun enforceMembershipExpiryIfNeeded() {
        val current = _uiState.value.premiumMembership
        val now = System.currentTimeMillis()
        val enforced = ProtonPremiumRepository.enforceExpiryState(current, now)
        if (enforced != current) {
            viewModelScope.launch {
                prefsRepo.savePremiumMembership(enforced)
                prefsRepo.setReelsModeEnabled(false)
                if (_uiState.value.aiMode == AiEngineMode.OFFLINE_LOCAL) {
                    prefsRepo.setAiMode(AiEngineMode.ONLINE_GEMINI)
                }
                updateState {
                    it.copy(
                        premiumMembership = enforced,
                        isPremiumUnlocked = false,
                        reelsModeEnabled = false,
                        aiMode = AiEngineMode.ONLINE_GEMINI,
                        statusBanner = enforced.statusNote
                    )
                }
            }
        }
    }

    fun setTtsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setTtsEnabled(enabled)
            if (!enabled) {
                voiceController.stopSpeaking()
            }
        }
    }

    fun setWakeWordEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled && !hasRecordAudioPermission()) {
                updateState {
                    it.copy(statusBanner = "Enable microphone permission first to arm Hey Proton.")
                }
                return@launch
            }
            prefsRepo.setWakeWordEnabled(enabled)
            voiceController.setWakeWordMonitoring(enabled)
            updateState {
                it.copy(
                    statusBanner = if (enabled) {
                        "Hey Proton wake-word listener armed."
                    } else {
                        "Wake-word listener disarmed."
                    }
                )
            }
        }
    }

    fun executeDirectControl(action: PhoneControlAction) {
        if (action is PhoneControlAction.ToggleReelsMode) {
            val target = action.enable ?: !_uiState.value.reelsModeEnabled
            setReelsModeEnabled(target)
            return
        }
        viewModelScope.launch {
            val lang = _uiState.value.selectedLanguage
            val result = withContext(Dispatchers.IO) {
                controlExecutor.execute(action, lang)
            }
            refreshTelemetry()
            val msg = ChatMessage(
                id = UUID.randomUUID().toString(),
                isUser = false,
                text = result.message,
                engineTag = "DEVICE CONTROL",
                actionExecuted = action.displayLabel,
                isError = !result.success
            )
            updateState {
                it.copy(
                    messages = it.messages + msg,
                    statusBanner = result.message
                )
            }
            if (_uiState.value.ttsEnabled) {
                voiceController.speak(result.message)
            }
        }
    }

    fun confirmPendingAction() {
        val pending = _uiState.value.pendingConfirmation ?: return
        updateState { it.copy(pendingConfirmation = null) }
        executeDirectControl(pending.action)
    }

    fun cancelPendingAction() {
        val pending = _uiState.value.pendingConfirmation ?: return
        updateState { it.copy(pendingConfirmation = null) }
        val cancelMsg = "Cancelled ${pending.targetDescription}."
        val assistantMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            isUser = false,
            text = cancelMsg,
            engineTag = "SAFETY GATE"
        )
        updateState {
            it.copy(
                messages = it.messages + assistantMsg,
                statusBanner = cancelMsg
            )
        }
        if (_uiState.value.ttsEnabled) {
            voiceController.speak(cancelMsg)
        }
    }

    fun submitUserQuery(rawPrompt: String) {
        val prompt = rawPrompt.trim()
        if (prompt.isBlank()) return

        val currentLang = _uiState.value.selectedLanguage
        val activeLanguage = if (_uiState.value.autoDetectLanguage) {
            LanguageDetector.detectFromInput(prompt, currentLang)
        } else {
            currentLang
        }

        val hasPending = _uiState.value.pendingConfirmation != null
        Log.i(TAG, "[PIPELINE:SPEECH_TO_ROUTER] input=\"$prompt\" lang=${activeLanguage.id} pending=$hasPending")
        val route = commandRouter.route(prompt, activeLanguage, hasPending)

        when (route) {
            CommandRouteDecision.DuplicateIgnored -> {
                Log.d(TAG, "[PIPELINE] Dropped duplicate command: \"$prompt\"")
                return
            }
            is CommandRouteDecision.ConfirmPending -> {
                val userMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    isUser = true,
                    text = prompt,
                    engineTag = activeLanguage.code
                )
                updateState { it.copy(messages = it.messages + userMsg) }
                if (route.approved) confirmPendingAction() else cancelPendingAction()
                return
            }
            is CommandRouteDecision.RequireConfirmation -> {
                val userMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    isUser = true,
                    text = prompt,
                    engineTag = activeLanguage.code
                )
                val confirmMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    isUser = false,
                    text = route.pending.promptText,
                    engineTag = "CONFIRMATION REQUIRED"
                )
                updateState {
                    it.copy(
                        pendingConfirmation = route.pending,
                        messages = it.messages + userMsg + confirmMsg,
                        statusBanner = route.pending.promptText
                    )
                }
                if (_uiState.value.ttsEnabled) {
                    voiceController.speak(route.pending.promptText)
                }
                return
            }
            is CommandRouteDecision.ExecuteLocalAction -> {
                executeLocalCommandPipeline(prompt, activeLanguage, currentLang, route.action)
                return
            }
            is CommandRouteDecision.SendToAi -> {
                executeAiPipeline(route.normalizedPrompt, activeLanguage, currentLang)
            }
        }
    }

    private fun executeLocalCommandPipeline(
        prompt: String,
        activeLanguage: AssistantLanguage,
        previousLanguage: AssistantLanguage,
        action: PhoneControlAction
    ) {
        activeGenerationJob?.cancel()
        voiceController.stopSpeaking()

        if (activeLanguage.id != previousLanguage.id) {
            voiceController.setLanguageTag(activeLanguage.bcp47Tag)
            viewModelScope.launch { prefsRepo.setLanguageId(activeLanguage.id) }
        }

        if (action is PhoneControlAction.ToggleReelsMode) {
            val userMessage = ChatMessage(
                id = UUID.randomUUID().toString(),
                isUser = true,
                text = prompt,
                engineTag = activeLanguage.code
            )
            val isPremium = _uiState.value.premiumMembership.isPremiumActive(System.currentTimeMillis())
            val nextEnabled = action.enable ?: !_uiState.value.reelsModeEnabled
            if (nextEnabled && !isPremium) {
                val lockedMsg = "Reels Mode requires an active PROTON PREMIUM pass (₹5 for 6 months). Opening upgrade screen."
                val assistantMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    isUser = false,
                    text = lockedMsg,
                    engineTag = "PROTON PREMIUM",
                    isError = false
                )
                updateState {
                    it.copy(
                        selectedLanguage = activeLanguage,
                        isPremiumUnlocked = false,
                        reelsModeEnabled = false,
                        messages = it.messages + userMessage + assistantMsg,
                        coreState = if (it.wakeWordEnabled) CoreState.WAKE_ARMED else CoreState.IDLE,
                        statusBanner = lockedMsg,
                        navigateToPremiumTrigger = it.navigateToPremiumTrigger + 1
                    )
                }
                if (_uiState.value.ttsEnabled) {
                    voiceController.speak(lockedMsg)
                }
                return
            }

            viewModelScope.launch {
                prefsRepo.setReelsModeEnabled(nextEnabled)
                val statusMsg = if (nextEnabled) {
                    "Reels Mode enabled. Hands-free short-video voice controls are ready."
                } else {
                    "Reels Mode disabled."
                }
                val assistantMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    isUser = false,
                    text = statusMsg,
                    engineTag = "PROTON PREMIUM",
                    actionExecuted = if (nextEnabled) "Reels Mode ON" else "Reels Mode OFF"
                )
                updateState {
                    it.copy(
                        selectedLanguage = activeLanguage,
                        reelsModeEnabled = nextEnabled,
                        messages = it.messages + userMessage + assistantMsg,
                        coreState = if (it.wakeWordEnabled) CoreState.WAKE_ARMED else CoreState.IDLE,
                        statusBanner = statusMsg
                    )
                }
                if (_uiState.value.ttsEnabled) {
                    voiceController.speak(statusMsg)
                }
            }
            return
        }

        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            isUser = true,
            text = prompt,
            engineTag = activeLanguage.code
        )

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                controlExecutor.execute(action, activeLanguage)
            }
            val updatedTelemetry = withContext(Dispatchers.Default) {
                controlExecutor.readTelemetry()
            }
            val assistantMsg = ChatMessage(
                id = UUID.randomUUID().toString(),
                isUser = false,
                text = result.message,
                engineTag = "FAST COMMAND ROUTER",
                actionExecuted = action.displayLabel,
                isError = !result.success
            )
            updateState {
                it.copy(
                    selectedLanguage = activeLanguage,
                    telemetry = updatedTelemetry,
                    messages = it.messages + userMessage + assistantMsg,
                    coreState = if (it.wakeWordEnabled) CoreState.WAKE_ARMED else CoreState.IDLE,
                    statusBanner = if (!result.success) result.message else null
                )
            }
            if (_uiState.value.ttsEnabled) {
                voiceController.speak(result.message)
            }
        }
    }

    private fun executeAiPipeline(
        prompt: String,
        activeLanguage: AssistantLanguage,
        previousLanguage: AssistantLanguage
    ) {
        activeGenerationJob?.cancel()
        voiceController.stopSpeaking()

        if (activeLanguage.id != previousLanguage.id) {
            voiceController.setLanguageTag(activeLanguage.bcp47Tag)
            viewModelScope.launch { prefsRepo.setLanguageId(activeLanguage.id) }
        }

        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            isUser = true,
            text = prompt,
            engineTag = activeLanguage.code
        )

        viewModelScope.launch {
            val currentTelemetry = withContext(Dispatchers.Default) {
                controlExecutor.readTelemetry()
            }

            val isPremiumActive = _uiState.value.premiumMembership.isPremiumActive(System.currentTimeMillis())

            if (_uiState.value.aiMode == AiEngineMode.OFFLINE_LOCAL) {
                if (!isPremiumActive) {
                    val lockedNotice = "Offline Mode requires an active PROTON PREMIUM pass (₹5 for 6 months). Switched back to Gemini Online."
                    prefsRepo.setAiMode(AiEngineMode.ONLINE_GEMINI)
                    val assistantMsg = ChatMessage(
                        id = UUID.randomUUID().toString(),
                        isUser = false,
                        text = lockedNotice,
                        engineTag = "PROTON PREMIUM"
                    )
                    updateState {
                        it.copy(
                            selectedLanguage = activeLanguage,
                            telemetry = currentTelemetry,
                            aiMode = AiEngineMode.ONLINE_GEMINI,
                            isPremiumUnlocked = false,
                            messages = it.messages + userMessage + assistantMsg,
                            coreState = CoreState.IDLE,
                            statusBanner = lockedNotice,
                            navigateToPremiumTrigger = it.navigateToPremiumTrigger + 1
                        )
                    }
                    if (_uiState.value.ttsEnabled) {
                        voiceController.speak(lockedNotice)
                    }
                    return@launch
                }

                val reply = withContext(Dispatchers.Default) {
                    offlineAiEngine.processLocally(
                        prompt = prompt,
                        language = activeLanguage,
                        telemetry = currentTelemetry,
                        executedControlMessage = null
                    )
                }
                val assistantMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    isUser = false,
                    text = reply.text,
                    engineTag = AiEngineMode.OFFLINE_LOCAL.label
                )
                updateState {
                    it.copy(
                        selectedLanguage = activeLanguage,
                        telemetry = currentTelemetry,
                        messages = it.messages + userMessage + assistantMsg,
                        coreState = if (it.wakeWordEnabled) CoreState.WAKE_ARMED else CoreState.IDLE,
                        statusBanner = if (activeLanguage.id != previousLanguage.id) {
                            "Auto-detected ${activeLanguage.englishName}."
                        } else null
                    )
                }
                if (_uiState.value.ttsEnabled) {
                    voiceController.speak(reply.text)
                }
                return@launch
            }

            if (!currentTelemetry.isNetworkConnected) {
                if (isPremiumActive) {
                    val offlineReply = withContext(Dispatchers.Default) {
                        offlineAiEngine.processLocally(
                            prompt = prompt,
                            language = activeLanguage,
                            telemetry = currentTelemetry,
                            executedControlMessage = null
                        )
                    }
                    val offlineNotice = "Internet unavailable — answered via Offline Core:\n${offlineReply.text}"
                    val errMsg = ChatMessage(
                        id = UUID.randomUUID().toString(),
                        isUser = false,
                        text = offlineNotice,
                        engineTag = "OFFLINE FALLBACK",
                        isError = false
                    )
                    updateState {
                        it.copy(
                            selectedLanguage = activeLanguage,
                            telemetry = currentTelemetry,
                            messages = it.messages + userMessage + errMsg,
                            coreState = CoreState.IDLE,
                            statusBanner = "No internet connection detected."
                        )
                    }
                    if (_uiState.value.ttsEnabled) {
                        voiceController.speak(offlineReply.text)
                    }
                } else {
                    val lockedOffline = "Internet unavailable. Full Offline Mode unlocks with PROTON PREMIUM (₹5 for 6 months); local device commands remain active."
                    val errMsg = ChatMessage(
                        id = UUID.randomUUID().toString(),
                        isUser = false,
                        text = lockedOffline,
                        engineTag = "NETWORK OFFLINE",
                        isError = true
                    )
                    updateState {
                        it.copy(
                            selectedLanguage = activeLanguage,
                            telemetry = currentTelemetry,
                            messages = it.messages + userMessage + errMsg,
                            coreState = CoreState.IDLE,
                            statusBanner = "No internet connection detected."
                        )
                    }
                    if (_uiState.value.ttsEnabled) {
                        voiceController.speak(lockedOffline)
                    }
                }
                return@launch
            }

            val streamMsgId = UUID.randomUUID().toString()
            val placeholder = ChatMessage(
                id = streamMsgId,
                isUser = false,
                text = "",
                engineTag = AiEngineMode.ONLINE_GEMINI.label
            )

            val historyBeforeTurn = _uiState.value.messages
            updateState {
                it.copy(
                    selectedLanguage = activeLanguage,
                    telemetry = currentTelemetry,
                    messages = it.messages + userMessage + placeholder,
                    coreState = CoreState.PROCESSING,
                    streamingMessageId = streamMsgId,
                    statusBanner = if (activeLanguage.id != previousLanguage.id) {
                        "Auto-detected ${activeLanguage.englishName}."
                    } else null
                )
            }

            val effectivePrompt = if (_uiState.value.reelsModeEnabled && isPremiumActive) {
                "[Reels Mode active: answer in 1 short sentence suitable for hands-free short-video listening] $prompt"
            } else {
                prompt
            }

            activeGenerationJob = launch {
                try {
                    onlineAiRepo.streamResponse(
                        userPrompt = effectivePrompt,
                        language = activeLanguage,
                        telemetry = currentTelemetry,
                        recentHistory = historyBeforeTurn
                    ).collect { event ->
                        when (event) {
                            is StreamChunkEvent.PartialText -> {
                                updateStreamingMessage(streamMsgId, event.visibleText, actionLabel = null)
                            }
                            is StreamChunkEvent.Completed -> {
                                val parsedAction = event.directiveTag?.let { controlExecutor.parseDirectiveTag(it) }
                                if (parsedAction is PhoneControlAction.OpenDialer) {
                                    val pending = PendingConfirmationAction(
                                        action = parsedAction,
                                        promptText = "Confirm opening the dialer?",
                                        targetDescription = parsedAction.phoneNumber ?: "phone dialer"
                                    )
                                    updateStreamingMessage(streamMsgId, event.visibleText, actionLabel = null)
                                    updateState { state ->
                                        state.copy(
                                            pendingConfirmation = pending,
                                            coreState = if (state.wakeWordEnabled) CoreState.WAKE_ARMED else CoreState.IDLE,
                                            streamingMessageId = null
                                        )
                                    }
                                    if (_uiState.value.ttsEnabled) {
                                        voiceController.speak(event.visibleText)
                                    }
                                    return@collect
                                }

                                val controlResult = parsedAction?.let { act ->
                                    Log.i(TAG, "[PIPELINE:GEMINI_ACTION] Executing ${act.displayLabel}")
                                    withContext(Dispatchers.IO) {
                                        act to controlExecutor.execute(act, activeLanguage)
                                    }
                                }
                                refreshTelemetry()
                                val finalText = when {
                                    controlResult == null -> event.visibleText
                                    !controlResult.second.success -> controlResult.second.message
                                    parsedAction is PhoneControlAction.OpenApp ||
                                        parsedAction is PhoneControlAction.OpenCamera ||
                                        parsedAction is PhoneControlAction.OpenSystemSettings -> controlResult.second.message
                                    else -> event.visibleText
                                }
                                updateStreamingMessage(
                                    id = streamMsgId,
                                    text = finalText,
                                    actionLabel = if (controlResult?.second?.success == true) {
                                        controlResult.first.displayLabel
                                    } else {
                                        null
                                    }
                                )
                                updateState { state ->
                                    state.copy(
                                        coreState = if (state.wakeWordEnabled) CoreState.WAKE_ARMED else CoreState.IDLE,
                                        streamingMessageId = null,
                                        statusBanner = if (controlResult?.second?.success == false) {
                                            controlResult.second.message
                                        } else {
                                            null
                                        }
                                    )
                                }
                                if (_uiState.value.ttsEnabled) {
                                    voiceController.speak(finalText)
                                }
                            }
                        }
                    }
                } catch (e: TimeoutCancellationException) {
                    Log.e(TAG, "Gemini request timed out", e)
                    markStreamingError(streamMsgId, "Gemini response timed out. Check your internet connection or try Offline Core.")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Gemini online inference failed", e)
                    markStreamingError(
                        streamMsgId,
                        e.localizedMessage ?: "Gemini AI request failed. Check network or App Check setup."
                    )
                }
            }
        }
    }

    private fun updateStreamingMessage(id: String, text: String, actionLabel: String?) {
        updateState { state ->
            state.copy(
                messages = state.messages.map { msg ->
                    if (msg.id == id) msg.copy(text = text, actionExecuted = actionLabel) else msg
                }
            )
        }
    }

    private fun markStreamingError(id: String, errorMessage: String) {
        updateState { state ->
            state.copy(
                coreState = CoreState.ERROR,
                streamingMessageId = null,
                statusBanner = errorMessage,
                messages = state.messages.map { msg ->
                    if (msg.id == id) {
                        msg.copy(
                            text = errorMessage,
                            engineTag = "AI ERROR",
                            isError = true
                        )
                    } else {
                        msg
                    }
                }
            )
        }
    }

    fun clearBanner() {
        updateState { it.copy(statusBanner = null) }
    }

    fun clearHistory() {
        updateState { it.copy(messages = emptyList(), statusBanner = "Transcript cleared.") }
    }

    fun onHostPause() {
        voiceController.pauseListeningPreserveWakeFlag()
    }

    fun onHostResume() {
        refreshSystemPermissionsAndServices()
        refreshTelemetry()
        enforceMembershipExpiryIfNeeded()
        voiceController.resumeWakeIfEnabled()
    }

    override fun onCleared() {
        super.onCleared()
        activeGenerationJob?.cancel()
        voiceController.release()
    }
}
