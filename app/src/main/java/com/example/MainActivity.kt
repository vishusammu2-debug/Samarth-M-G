package com.example

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.data.EndpointValidationResult
import com.example.data.ProtonPremiumRepository
import com.example.domain.AiEngineMode
import com.example.security.configureAppCheck
import com.example.service.OverlayPermissionHelper
import com.example.service.ProtonAccessibilityService
import com.example.service.ProtonForegroundService
import com.example.ui.ProtonViewModel
import com.example.ui.screens.ProtonAccessibilityScreen
import com.example.ui.screens.ProtonHudScreen
import com.example.ui.screens.ProtonPremiumScreen
import com.example.ui.screens.ProtonSettingsScreen
import com.example.ui.theme.ProtonTheme
import kotlinx.coroutines.flow.MutableStateFlow

const val ROUTE_HUD = "hud"
const val ROUTE_SETTINGS = "settings"
const val ROUTE_ACCESSIBILITY = "accessibility"
const val ROUTE_PREMIUM = "premium"

class MainActivity : ComponentActivity() {

    private val protonViewModel: ProtonViewModel by viewModels()
    private val pendingRouteNavigation = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Firebase App Check with launch intent before UI, ViewModels, or Firebase calls
        configureAppCheck(this, intent)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        setContent {
            ProtonTheme {
                val uiState by protonViewModel.uiState.collectAsStateWithLifecycle()
                val pendingRoute by pendingRouteNavigation.collectAsStateWithLifecycle()
                val navController = rememberNavController()
                val lifecycleOwner = LocalLifecycleOwner.current

                val micPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                    onResult = { granted ->
                        protonViewModel.onMicPermissionResult(granted)
                    }
                )

                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                    onResult = { granted ->
                        protonViewModel.onNotificationPermissionResult(granted)
                    }
                )

                LaunchedEffect(Unit) {
                    handleIncomingIntent(intent, onRequestMic = {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    })
                }

                LaunchedEffect(pendingRoute) {
                    val target = pendingRoute ?: return@LaunchedEffect
                    navController.navigate(target) {
                        launchSingleTop = true
                    }
                    pendingRouteNavigation.value = null
                }

                LaunchedEffect(uiState.navigateToPremiumTrigger) {
                    if (uiState.navigateToPremiumTrigger > 0) {
                        navController.navigate(ROUTE_PREMIUM) {
                            launchSingleTop = true
                        }
                    }
                }

                LaunchedEffect(uiState.pendingCheckoutUrl) {
                    val rawUrl = uiState.pendingCheckoutUrl ?: return@LaunchedEffect
                    protonViewModel.consumePendingCheckoutUrl()
                    val check = ProtonPremiumRepository.validateVerificationEndpoint(rawUrl)
                    if (check is EndpointValidationResult.Valid) {
                        runCatching {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(check.normalizedBaseUrl)))
                        }
                    }
                }

                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_RESUME -> protonViewModel.onHostResume()
                            Lifecycle.Event.ON_PAUSE -> protonViewModel.onHostPause()
                            else -> Unit
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                val toggleFloatingWithNotificationCheck: (Boolean) -> Unit = { enable ->
                    if (enable &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !OverlayPermissionHelper.hasNotificationPermission(this)
                    ) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    protonViewModel.setFloatingBubbleEnabled(enable)
                }

                NavHost(
                    navController = navController,
                    startDestination = ROUTE_HUD
                ) {
                    composable(ROUTE_HUD) {
                        ProtonHudScreen(
                            uiState = uiState,
                            onReactorCoreTap = {
                                if (!uiState.hasMicPermission) {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                } else {
                                    protonViewModel.onReactorCoreTapped()
                                }
                            },
                            onSubmitQuery = protonViewModel::submitUserQuery,
                            onRequestMicPermission = {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            onToggleAiMode = {
                                val nextMode = if (uiState.aiMode == AiEngineMode.ONLINE_GEMINI) {
                                    AiEngineMode.OFFLINE_LOCAL
                                } else {
                                    AiEngineMode.ONLINE_GEMINI
                                }
                                protonViewModel.setAiMode(nextMode)
                            },
                            onToggleTts = { protonViewModel.setTtsEnabled(!uiState.ttsEnabled) },
                            onToggleWakeWord = { protonViewModel.setWakeWordEnabled(!uiState.wakeWordEnabled) },
                            onToggleFloatingBubble = {
                                toggleFloatingWithNotificationCheck(!uiState.floatingBubble.enabled)
                            },
                            onConfirmOverlaySettings = {
                                protonViewModel.dismissOverlayRationaleDialog()
                                startActivity(OverlayPermissionHelper.createOverlaySettingsIntent(this@MainActivity))
                            },
                            onDismissOverlayRationale = protonViewModel::dismissOverlayRationaleDialog,
                            onConfirmPendingAction = protonViewModel::confirmPendingAction,
                            onCancelPendingAction = protonViewModel::cancelPendingAction,
                            onOpenSettings = {
                                navController.navigate(ROUTE_SETTINGS) {
                                    launchSingleTop = true
                                }
                            },
                            onOpenPremium = {
                                navController.navigate(ROUTE_PREMIUM) {
                                    launchSingleTop = true
                                }
                            },
                            onToggleReelsMode = {
                                protonViewModel.setReelsModeEnabled(!uiState.reelsModeEnabled)
                            },
                            onDismissBanner = protonViewModel::clearBanner
                        )
                    }

                    composable(ROUTE_SETTINGS) {
                        ProtonSettingsScreen(
                            uiState = uiState,
                            onBack = { navController.popBackStack() },
                            onSelectLanguage = protonViewModel::selectLanguage,
                            onToggleAutoDetectLanguage = protonViewModel::setAutoDetectLanguage,
                            onSelectVoice = protonViewModel::selectVoice,
                            onTogglePreferMaleVoice = protonViewModel::setPreferMaleVoice,
                            onSpeechRateChange = protonViewModel::setSpeechRate,
                            onSpeechVolumeChange = protonViewModel::setSpeechVolume,
                            onToggleContinuousConversation = protonViewModel::setContinuousConversation,
                            onPreviewVoice = protonViewModel::previewSelectedVoice,
                            onSelectAiMode = protonViewModel::setAiMode,
                            onSelectPerformanceMode = protonViewModel::setPerformanceMode,
                            onToggleTts = protonViewModel::setTtsEnabled,
                            onToggleWakeWord = { enabled ->
                                if (enabled && !uiState.hasMicPermission) {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                } else {
                                    protonViewModel.setWakeWordEnabled(enabled)
                                }
                            },
                            onToggleFloatingBubble = toggleFloatingWithNotificationCheck,
                            onToggleRotatingRings = protonViewModel::setRotatingRingsEnabled,
                            onToggleElectricArcs = protonViewModel::setElectricArcsEnabled,
                            onBubbleSizeChange = protonViewModel::setBubbleSizeDp,
                            onAnimationSpeedChange = protonViewModel::setAnimationSpeed,
                            onBubbleOpacityChange = protonViewModel::setBubbleOpacity,
                            onGlowIntensityChange = protonViewModel::setGlowIntensity,
                            onParticleIntensityChange = protonViewModel::setParticleIntensity,
                            onOpenOverlaySettings = {
                                startActivity(OverlayPermissionHelper.createOverlaySettingsIntent(this@MainActivity))
                            },
                            onOpenAccessibilityScreen = {
                                navController.navigate(ROUTE_ACCESSIBILITY) {
                                    launchSingleTop = true
                                }
                            },
                            onOpenAccessibilitySettings = {
                                startActivity(ProtonAccessibilityService.createAccessibilitySettingsIntent())
                            },
                            onDisableAccessibilityService = protonViewModel::disableAccessibilityServiceImmediately,
                            onOpenPremiumScreen = {
                                navController.navigate(ROUTE_PREMIUM) {
                                    launchSingleTop = true
                                }
                            },
                            onToggleReelsMode = protonViewModel::setReelsModeEnabled,
                            onExecuteControl = protonViewModel::executeDirectControl,
                            onClearHistory = protonViewModel::clearHistory
                        )
                    }

                    composable(ROUTE_ACCESSIBILITY) {
                        ProtonAccessibilityScreen(
                            uiState = uiState,
                            onBack = { navController.popBackStack() },
                            onOpenAccessibilitySettings = {
                                startActivity(ProtonAccessibilityService.createAccessibilitySettingsIntent())
                            },
                            onDisableAccessibilityService = protonViewModel::disableAccessibilityServiceImmediately,
                            onExecuteControl = protonViewModel::executeDirectControl
                        )
                    }

                    composable(ROUTE_PREMIUM) {
                        ProtonPremiumScreen(
                            uiState = uiState,
                            onBack = { navController.popBackStack() },
                            onSaveBeneficiaryConfig = protonViewModel::saveBeneficiaryAndServerConfig,
                            onStartAuthorizedCheckout = protonViewModel::startAuthorizedPremiumCheckout,
                            onVerifyPayment = protonViewModel::verifyPremiumPayment,
                            onToggleReelsMode = protonViewModel::setReelsModeEnabled,
                            onSelectAiMode = protonViewModel::setAiMode,
                            onDismissStatusMessage = protonViewModel::clearPremiumStatusMessage
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent, onRequestMic = {})
    }

    private fun handleIncomingIntent(intent: Intent?, onRequestMic: () -> Unit) {
        when (intent?.getStringExtra(ProtonForegroundService.EXTRA_PROTON_ACTION)) {
            ProtonForegroundService.ACTION_UI_ACTIVATE_VOICE -> {
                intent.removeExtra(ProtonForegroundService.EXTRA_PROTON_ACTION)
                if (!protonViewModel.uiState.value.hasMicPermission) {
                    onRequestMic()
                } else {
                    protonViewModel.triggerVoiceFromOverlay()
                }
            }
            ProtonForegroundService.ACTION_UI_DASHBOARD -> {
                intent.removeExtra(ProtonForegroundService.EXTRA_PROTON_ACTION)
                protonViewModel.refreshSystemPermissionsAndServices()
            }
            ProtonForegroundService.ACTION_UI_ACCESSIBILITY -> {
                intent.removeExtra(ProtonForegroundService.EXTRA_PROTON_ACTION)
                protonViewModel.refreshSystemPermissionsAndServices()
                pendingRouteNavigation.value = ROUTE_ACCESSIBILITY
            }
        }
    }
}
