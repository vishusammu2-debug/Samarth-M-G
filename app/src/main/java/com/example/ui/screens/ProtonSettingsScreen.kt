package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.AccessibilityNavAction
import com.example.domain.AiEngineMode
import com.example.domain.AssistantLanguage
import com.example.domain.PerformanceProfileMode
import com.example.domain.PhoneControlAction
import com.example.domain.ResolvedPerformanceTier
import com.example.domain.SupportedLanguages
import com.example.domain.VolumeDirection
import com.example.ui.ProtonUiState
import com.example.ui.components.HudBracketCard
import com.example.ui.theme.DeepSpaceNavy
import com.example.ui.theme.HudBorderCyan
import com.example.ui.theme.HudPanelElevated
import com.example.ui.theme.HudPanelSurface
import com.example.ui.theme.HudTextMuted
import com.example.ui.theme.HudTextPrimary
import com.example.ui.theme.HudTextSecondary
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.ProtonAmber
import com.example.ui.theme.ProtonCrimson
import com.example.ui.theme.ProtonCyan
import com.example.ui.theme.ProtonEmerald
import com.example.ui.theme.ProtonIceBlue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ProtonSettingsScreen(
    uiState: ProtonUiState,
    onBack: () -> Unit,
    onSelectLanguage: (AssistantLanguage) -> Unit,
    onToggleAutoDetectLanguage: (Boolean) -> Unit,
    onSelectVoice: (String?) -> Unit,
    onTogglePreferMaleVoice: (Boolean) -> Unit,
    onSpeechRateChange: (Float) -> Unit,
    onSpeechVolumeChange: (Float) -> Unit,
    onToggleContinuousConversation: (Boolean) -> Unit,
    onPreviewVoice: () -> Unit,
    onSelectAiMode: (AiEngineMode) -> Unit,
    onSelectPerformanceMode: (PerformanceProfileMode) -> Unit,
    onToggleTts: (Boolean) -> Unit,
    onToggleWakeWord: (Boolean) -> Unit,
    onToggleFloatingBubble: (Boolean) -> Unit,
    onToggleRotatingRings: (Boolean) -> Unit,
    onToggleElectricArcs: (Boolean) -> Unit,
    onBubbleSizeChange: (Float) -> Unit,
    onAnimationSpeedChange: (Float) -> Unit,
    onBubbleOpacityChange: (Float) -> Unit,
    onGlowIntensityChange: (Float) -> Unit,
    onParticleIntensityChange: (Float) -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenAccessibilityScreen: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onDisableAccessibilityService: () -> Unit,
    onOpenPremiumScreen: () -> Unit,
    onToggleReelsMode: (Boolean) -> Unit,
    onExecuteControl: (PhoneControlAction) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(DeepSpaceNavy, ObsidianVoid)
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("settings_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = ProtonCyan
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "PROTON 5.0 SYSTEMS",
                    style = MaterialTheme.typography.headlineSmall,
                    color = ProtonIceBlue
                )
                Text(
                    text = "Adaptive Engine • 3D Crystal Sphere • 19 Languages",
                    style = MaterialTheme.typography.labelSmall,
                    color = HudTextSecondary
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .align(Alignment.CenterHorizontally),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // PROTON PREMIUM Status & Reels Mode Card
            item {
                val premiumColor = if (uiState.isPremiumUnlocked) ProtonEmerald else ProtonAmber
                val expiryLabel = uiState.premiumMembership.expiresAtMillis?.takeIf { it > 0L }?.let { ts ->
                    SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(ts))
                } ?: "Not activated"
                HudBracketCard(accentColor = premiumColor) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.WorkspacePremium,
                                    contentDescription = null,
                                    tint = premiumColor
                                )
                                Text(
                                    text = "PROTON PREMIUM",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = premiumColor
                                )
                            }
                            StatusTagPill(
                                label = if (uiState.isPremiumUnlocked) "ACTIVE" else "₹5 / 6 MONTHS",
                                color = premiumColor
                            )
                        }

                        Text(
                            text = if (uiState.isPremiumUnlocked) {
                                "Verified 6-month one-time pass active (no auto-renewal). Expires on $expiryLabel. Reels Mode and Offline Mode unlocked."
                            } else {
                                "Unlock Reels Mode and Offline Mode with a one-time ₹5 INR pass for 6 months (no auto-renewal)."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = HudTextSecondary
                        )

                        SwitchSettingRow(
                            title = "Reels Mode",
                            subtitle = if (uiState.isPremiumUnlocked) {
                                "Hands-free voice companion for short-form video watching"
                            } else {
                                "Locked — requires verified PROTON PREMIUM (₹5 / 6 months)"
                            },
                            checked = uiState.reelsModeEnabled && uiState.isPremiumUnlocked,
                            onCheckedChange = onToggleReelsMode,
                            tag = "settings_reels_mode_switch"
                        )

                        Button(
                            onClick = onOpenPremiumScreen,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = premiumColor,
                                contentColor = ObsidianVoid
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("open_premium_screen_button")
                        ) {
                            Text(
                                text = if (uiState.isPremiumUnlocked) {
                                    "View Membership & Expiry Details"
                                } else {
                                    "Unlock PROTON PREMIUM (₹5 • 6 Months)"
                                },
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }

            // 0. Adaptive Device Performance Profile
            item {
                val caps = uiState.telemetry.capabilities
                val tierColor = when (uiState.resolvedPerformanceTier) {
                    ResolvedPerformanceTier.HIGH_PERFORMANCE -> ProtonEmerald
                    ResolvedPerformanceTier.BALANCED -> ProtonCyan
                    ResolvedPerformanceTier.LOW_END -> ProtonAmber
                }
                HudBracketCard(accentColor = tierColor) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Speed,
                                    contentDescription = null,
                                    tint = tierColor
                                )
                                Text(
                                    text = "ADAPTIVE PERFORMANCE",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = tierColor
                                )
                            }
                            StatusTagPill(
                                label = "ACTIVE: ${uiState.resolvedPerformanceTier.label}",
                                color = tierColor
                            )
                        }

                        Text(
                            text = "RAM: ${caps.availRamMb}/${caps.totalRamMb} MB free • CPU: ${caps.cpuCores} cores • Display: ${caps.refreshRateHz} Hz • Thermal: ${caps.thermalStatusLabel}${if (caps.isPowerSaveMode) " • Power Saver ON" else ""}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = HudTextSecondary
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PerformanceProfileMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = uiState.performanceMode == mode,
                                    onClick = { onSelectPerformanceMode(mode) },
                                    label = {
                                        Text(
                                            text = mode.label,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = HudPanelSurface,
                                        labelColor = HudTextSecondary,
                                        selectedContainerColor = tierColor.copy(alpha = 0.18f),
                                        selectedLabelColor = tierColor
                                    ),
                                    modifier = Modifier.testTag("perf_mode_${mode.name}")
                                )
                            }
                        }
                    }
                }
            }

            // 1. Floating Neural Core & Background Service Dashboard (Features 1, 2, 4, 5, 6)
            item {
                HudBracketCard(accentColor = ProtonCyan) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.BubbleChart,
                                    contentDescription = null,
                                    tint = ProtonCyan
                                )
                                Text(
                                    text = "FLOATING 3D CYBER ORB",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = ProtonCyan
                                )
                            }
                            StatusTagPill(
                                label = if (uiState.isBackgroundServiceRunning) "SERVICE ACTIVE" else "STANDBY",
                                color = if (uiState.isBackgroundServiceRunning) ProtonEmerald else HudTextMuted
                            )
                        }

                        // Status Row: Overlay Permission & Bubble State
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StatusTagPill(
                                label = if (uiState.hasOverlayPermission) "OVERLAY GRANTED" else "OVERLAY REQUIRED",
                                color = if (uiState.hasOverlayPermission) ProtonEmerald else ProtonAmber,
                                modifier = Modifier.weight(1f)
                            )
                            StatusTagPill(
                                label = when {
                                    uiState.isBubbleCurrentlyVisible -> "ORB VISIBLE"
                                    uiState.isBackgroundServiceRunning -> "ORB HIDDEN"
                                    else -> "ORB OFF"
                                },
                                color = if (uiState.isBubbleCurrentlyVisible) ProtonCyan else HudTextSecondary,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Overlay Permission Explanation & Settings Shortcut (if not granted)
                        if (!uiState.hasOverlayPermission) {
                            Surface(
                                color = ProtonAmber.copy(alpha = 0.10f),
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    ProtonAmber.copy(alpha = 0.55f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.overlay_permission_title),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = ProtonAmber
                                    )
                                    Text(
                                        text = stringResource(R.string.overlay_permission_explanation),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = HudTextSecondary
                                    )
                                    Button(
                                        onClick = onOpenOverlaySettings,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = ProtonAmber,
                                            contentColor = ObsidianVoid
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .minimumInteractiveComponentSize()
                                            .testTag("grant_overlay_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Layers,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = stringResource(R.string.overlay_permission_open_settings),
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }
                            }
                        }

                        SwitchSettingRow(
                            title = "Floating 3D Cyber Orb",
                            subtitle = "Tap: Voice • Double-tap: Dashboard • Long-press: Quick Controls",
                            checked = uiState.floatingBubble.enabled,
                            onCheckedChange = onToggleFloatingBubble,
                            tag = "floating_bubble_switch"
                        )

                        SwitchSettingRow(
                            title = "Concentric Neon Rings",
                            subtitle = "Counter-rotating cyan, royal-blue & violet rings inside the sphere",
                            checked = uiState.floatingBubble.rotatingRingsEnabled,
                            onCheckedChange = onToggleRotatingRings,
                            tag = "rotating_rings_switch"
                        )

                        SwitchSettingRow(
                            title = "Internal Digital Circuits",
                            subtitle = "Animated micro-circuits and data pulses clipped inside the glass",
                            checked = uiState.floatingBubble.electricArcsEnabled,
                            onCheckedChange = onToggleElectricArcs,
                            tag = "electric_arcs_switch"
                        )

                        HudSliderRow(
                            title = "Sphere Size",
                            valueLabel = "${uiState.floatingBubble.sizeDp.roundToInt()} dp",
                            value = uiState.floatingBubble.sizeDp,
                            valueRange = 56f..108f,
                            onValueChange = onBubbleSizeChange,
                            tag = "bubble_size_slider"
                        )

                        HudSliderRow(
                            title = "Sphere Opacity",
                            valueLabel = "${(uiState.floatingBubble.opacity * 100).roundToInt()}%",
                            value = uiState.floatingBubble.opacity,
                            valueRange = 0.35f..1.0f,
                            onValueChange = onBubbleOpacityChange,
                            tag = "bubble_opacity_slider"
                        )

                        HudSliderRow(
                            title = "Internal Glow Intensity",
                            valueLabel = "${"%.1f".format(uiState.floatingBubble.glowIntensity)}x",
                            value = uiState.floatingBubble.glowIntensity,
                            valueRange = 0.2f..2.0f,
                            onValueChange = onGlowIntensityChange,
                            tag = "glow_intensity_slider"
                        )

                        HudSliderRow(
                            title = "Animation Speed",
                            valueLabel = "${"%.1f".format(uiState.floatingBubble.animationSpeed)}x",
                            value = uiState.floatingBubble.animationSpeed,
                            valueRange = 0.4f..2.0f,
                            onValueChange = onAnimationSpeedChange,
                            tag = "animation_speed_slider"
                        )

                        HudSliderRow(
                            title = "Internal Particle Density",
                            valueLabel = "${(uiState.floatingBubble.particleIntensity * 100).roundToInt()}%",
                            value = uiState.floatingBubble.particleIntensity,
                            valueRange = 0.0f..1.5f,
                            onValueChange = onParticleIntensityChange,
                            tag = "particle_intensity_slider"
                        )
                    }
                }
            }

            // 2. Accessibility Settings Section (Feature 3)
            item {
                HudBracketCard(accentColor = if (uiState.isAccessibilityEnabled) ProtonEmerald else ProtonAmber) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AccessibilityNew,
                                    contentDescription = null,
                                    tint = if (uiState.isAccessibilityEnabled) ProtonEmerald else ProtonAmber
                                )
                                Text(
                                    text = "ACCESSIBILITY SYSTEM",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (uiState.isAccessibilityEnabled) ProtonEmerald else ProtonAmber
                                )
                            }
                            StatusTagPill(
                                label = if (uiState.isAccessibilityEnabled) "ENABLED" else "DISABLED",
                                color = if (uiState.isAccessibilityEnabled) ProtonEmerald else ProtonAmber
                            )
                        }

                        Surface(
                            color = HudPanelSurface.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, HudBorderCyan)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Shield,
                                    contentDescription = null,
                                    tint = ProtonCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = stringResource(R.string.accessibility_service_description),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = HudTextSecondary
                                )
                            }
                        }

                        Button(
                            onClick = onOpenAccessibilityScreen,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ProtonCyan,
                                contentColor = ObsidianVoid
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("open_accessibility_screen_button")
                        ) {
                            Text(
                                text = "Open Dedicated Accessibility Settings",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }

                        OutlinedButton(
                            onClick = onOpenAccessibilitySettings,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ProtonIceBlue),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("open_accessibility_settings_button")
                        ) {
                            Text(
                                text = if (uiState.isAccessibilityEnabled) {
                                    "Manage in Android Accessibility Settings"
                                } else {
                                    "Enable Accessibility Service in Settings"
                                },
                                style = MaterialTheme.typography.labelMedium
                            )
                        }

                        if (uiState.isAccessibilityEnabled) {
                            OutlinedButton(
                                onClick = onDisableAccessibilityService,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ProtonCrimson),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .testTag("disable_accessibility_button")
                            ) {
                                Text(
                                    text = "Disable Accessibility Service Now",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }

                            Text(
                                text = "USER-TRIGGERED NAVIGATION ACTIONS",
                                style = MaterialTheme.typography.labelSmall,
                                color = ProtonIceBlue
                            )

                            AccessibilityNavAction.entries.chunked(3).forEach { chunk ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    chunk.forEach { navAction ->
                                        Surface(
                                            color = HudPanelElevated.copy(alpha = 0.85f),
                                            shape = RoundedCornerShape(8.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, HudBorderCyan),
                                            modifier = Modifier
                                                .weight(1f)
                                                .minimumInteractiveComponentSize()
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    onExecuteControl(
                                                        PhoneControlAction.AccessibilityNavigation(navAction)
                                                    )
                                                }
                                        ) {
                                            Text(
                                                text = navAction.label,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = ProtonIceBlue,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
                                            )
                                        }
                                    }
                                    repeat(3 - chunk.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. AI Engine & Voice Switches
            item {
                HudBracketCard(accentColor = ProtonCyan) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "NEURAL CORE & VOICE",
                            style = MaterialTheme.typography.titleMedium,
                            color = ProtonCyan
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            EngineModeOption(
                                title = "Gemini Online",
                                subtitle = "Cloud AI streaming",
                                icon = Icons.Filled.Cloud,
                                selected = uiState.aiMode == AiEngineMode.ONLINE_GEMINI,
                                onClick = { onSelectAiMode(AiEngineMode.ONLINE_GEMINI) },
                                modifier = Modifier.weight(1f)
                            )
                            EngineModeOption(
                                title = "Offline Core",
                                subtitle = if (uiState.isPremiumUnlocked) {
                                    "Local NLU & controls"
                                } else {
                                    "Locked • Premium ₹5"
                                },
                                icon = Icons.Filled.CloudOff,
                                selected = uiState.aiMode == AiEngineMode.OFFLINE_LOCAL,
                                onClick = { onSelectAiMode(AiEngineMode.OFFLINE_LOCAL) },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        SwitchSettingRow(
                            title = "Spoken TTS Responses",
                            subtitle = "Speak replies aloud in ${uiState.selectedLanguage.englishName}",
                            checked = uiState.ttsEnabled,
                            onCheckedChange = onToggleTts,
                            tag = "tts_switch"
                        )

                        SwitchSettingRow(
                            title = "\"Hey Proton\" Wake-Word",
                            subtitle = "Hands-free foreground wake listener",
                            checked = uiState.wakeWordEnabled,
                            onCheckedChange = onToggleWakeWord,
                            tag = "wake_word_switch"
                        )

                        SwitchSettingRow(
                            title = "Prefer Male Assistant Voice",
                            subtitle = "Prioritize deeper male neural TTS voices when installed on device",
                            checked = uiState.preferMaleVoice,
                            onCheckedChange = onTogglePreferMaleVoice,
                            tag = "prefer_male_voice_switch"
                        )

                        SwitchSettingRow(
                            title = "Continuous Conversation",
                            subtitle = "Automatically re-arm microphone after Proton finishes speaking",
                            checked = uiState.continuousConversation,
                            onCheckedChange = onToggleContinuousConversation,
                            tag = "continuous_conversation_switch"
                        )

                        HudSliderRow(
                            title = "Speech Speed",
                            valueLabel = "${"%.2f".format(uiState.speechRate)}x",
                            value = uiState.speechRate,
                            valueRange = 0.6f..1.6f,
                            onValueChange = onSpeechRateChange,
                            tag = "speech_rate_slider"
                        )

                        HudSliderRow(
                            title = "Voice Output Volume",
                            valueLabel = "${(uiState.speechVolume * 100).roundToInt()}%",
                            value = uiState.speechVolume,
                            valueRange = 0.2f..1.0f,
                            onValueChange = onSpeechVolumeChange,
                            tag = "speech_volume_slider"
                        )

                        // Voice Selection & Preview
                        Text(
                            text = "VOICE SYNTHESIS PROFILE",
                            style = MaterialTheme.typography.labelSmall,
                            color = ProtonIceBlue
                        )

                        if (!uiState.isTtsLanguageSupported) {
                            Surface(
                                color = ProtonAmber.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, ProtonAmber.copy(alpha = 0.55f))
                            ) {
                                Text(
                                    text = "Device TTS voice data for ${uiState.selectedLanguage.englishName} (${uiState.selectedLanguage.bcp47Tag}) is missing or not installed. Falling back to device default voice until language data is downloaded in Android TTS settings.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ProtonAmber,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = uiState.selectedVoiceName == null,
                                onClick = { onSelectVoice(null) },
                                label = {
                                    Text(
                                        text = "System Default",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = HudPanelSurface,
                                    labelColor = HudTextSecondary,
                                    selectedContainerColor = ProtonCyan.copy(alpha = 0.18f),
                                    selectedLabelColor = ProtonCyan
                                ),
                                modifier = Modifier.testTag("voice_default_chip")
                            )

                            uiState.availableVoices.forEach { voice ->
                                FilterChip(
                                    selected = uiState.selectedVoiceName == voice.name,
                                    onClick = { onSelectVoice(voice.name) },
                                    label = {
                                        Text(
                                            text = voice.displayLabel,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = HudPanelSurface,
                                        labelColor = HudTextSecondary,
                                        selectedContainerColor = ProtonCyan.copy(alpha = 0.18f),
                                        selectedLabelColor = ProtonCyan
                                    ),
                                    modifier = Modifier.testTag("voice_${voice.name}")
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = onPreviewVoice,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ProtonCyan),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("test_voice_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.RecordVoiceOver,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Test Voice in ${uiState.selectedLanguage.englishName}",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }

                        OutlinedButton(
                            onClick = onClearHistory,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ProtonCrimson),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("clear_history_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteSweep,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Clear Conversation Transcript",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }

            // 4. Safe Phone Controls Deck
            item {
                HudBracketCard(accentColor = ProtonEmerald) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "SAFE PHONE CONTROLS",
                            style = MaterialTheme.typography.titleMedium,
                            color = ProtonEmerald
                        )
                        Text(
                            text = "Direct hardware & system intent shortcuts without intrusive permissions.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = HudTextSecondary
                        )

                        val controls = listOf(
                            ControlTileSpec(
                                label = if (uiState.telemetry.flashlightOn) "Torch OFF" else "Torch ON",
                                icon = if (uiState.telemetry.flashlightOn) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff,
                                action = PhoneControlAction.ToggleFlashlight(null)
                            ),
                            ControlTileSpec("Volume +", Icons.AutoMirrored.Filled.VolumeUp, PhoneControlAction.AdjustVolume(VolumeDirection.UP)),
                            ControlTileSpec("Volume -", Icons.AutoMirrored.Filled.VolumeDown, PhoneControlAction.AdjustVolume(VolumeDirection.DOWN)),
                            ControlTileSpec("Mute Media", Icons.AutoMirrored.Filled.VolumeOff, PhoneControlAction.AdjustVolume(VolumeDirection.MUTE)),
                            ControlTileSpec("Wi-Fi", Icons.Filled.Wifi, PhoneControlAction.OpenWifiSettings),
                            ControlTileSpec("Bluetooth", Icons.Filled.Bluetooth, PhoneControlAction.OpenBluetoothSettings),
                            ControlTileSpec("Camera", Icons.Filled.CameraAlt, PhoneControlAction.OpenCamera),
                            ControlTileSpec("Dialer", Icons.Filled.Phone, PhoneControlAction.OpenDialer(null)),
                            ControlTileSpec("5m Timer", Icons.Filled.Timer, PhoneControlAction.SetTimer(300)),
                            ControlTileSpec("7:00 Alarm", Icons.Filled.Alarm, PhoneControlAction.SetAlarm(7, 0)),
                            ControlTileSpec("Settings", Icons.Filled.Settings, PhoneControlAction.OpenSystemSettings)
                        )

                        controls.chunked(3).forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowItems.forEach { spec ->
                                    ControlTileButton(
                                        spec = spec,
                                        onClick = { onExecuteControl(spec.action) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                repeat(3 - rowItems.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            // 5. 19-Language Matrix
            item {
                HudBracketCard(accentColor = ProtonAmber) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "19-LANGUAGE MATRIX",
                            style = MaterialTheme.typography.titleMedium,
                            color = ProtonAmber
                        )
                        Text(
                            text = "Configures SpeechRecognizer, TextToSpeech, and Gemini output. Uttara Kannada uses Android Kannada (kn-IN) speech recognition paired with dialect-aware AI/offline responses.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = HudTextSecondary
                        )

                        SwitchSettingRow(
                            title = "Auto-Detect Language & Dialect",
                            subtitle = "Automatically switch script or Uttara Kannada cues on text/voice input",
                            checked = uiState.autoDetectLanguage,
                            onCheckedChange = onToggleAutoDetectLanguage,
                            tag = "auto_detect_language_switch"
                        )

                        SupportedLanguages.all.chunked(2).forEach { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                pair.forEach { lang ->
                                    LanguageOptionTile(
                                        language = lang,
                                        selected = lang.id == uiState.selectedLanguage.id,
                                        onClick = { onSelectLanguage(lang) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (pair.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusTagPill(
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.55f)),
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun HudSliderRow(
    title: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    tag: String
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = HudTextPrimary
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelSmall,
                color = ProtonCyan
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = ProtonCyan,
                activeTrackColor = ProtonCyan,
                inactiveTrackColor = HudPanelSurface
            ),
            modifier = Modifier.testTag(tag)
        )
    }
}

private data class ControlTileSpec(
    val label: String,
    val icon: ImageVector,
    val action: PhoneControlAction
)

@Composable
private fun EngineModeOption(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (selected) ProtonCyan.copy(alpha = 0.16f) else HudPanelSurface,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) ProtonCyan else HudBorderCyan
        ),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) ProtonCyan else HudTextSecondary
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) ProtonIceBlue else HudTextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = HudTextMuted
            )
        }
    }
}

@Composable
private fun SwitchSettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = HudTextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = HudTextSecondary
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ObsidianVoid,
                checkedTrackColor = ProtonCyan,
                uncheckedThumbColor = HudTextSecondary,
                uncheckedTrackColor = HudPanelSurface
            ),
            modifier = Modifier.testTag(tag)
        )
    }
}

@Composable
private fun ControlTileButton(
    spec: ControlTileSpec,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = HudPanelElevated.copy(alpha = 0.85f),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, HudBorderCyan),
        modifier = modifier
            .height(74.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = spec.icon,
                contentDescription = spec.label,
                tint = ProtonCyan,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = spec.label,
                style = MaterialTheme.typography.labelSmall,
                color = HudTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LanguageOptionTile(
    language: AssistantLanguage,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (selected) ProtonCyan.copy(alpha = 0.16f) else HudPanelSurface.copy(alpha = 0.78f),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) ProtonCyan else HudBorderCyan.copy(alpha = 0.7f)
        ),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .testTag("lang_${language.id}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = language.englishName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) ProtonIceBlue else HudTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${language.nativeName} • ${language.code}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) ProtonCyan else HudTextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Selected",
                    tint = ProtonCyan,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
