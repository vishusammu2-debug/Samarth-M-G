package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.AiEngineMode
import com.example.domain.ChatMessage
import com.example.domain.CoreState
import com.example.domain.OrbVisualState
import com.example.ui.ProtonUiState
import com.example.ui.components.AudioWaveformBar
import com.example.ui.components.HudBracketCard
import com.example.ui.components.ProtonReactorCore
import com.example.ui.theme.DeepSpaceNavy
import com.example.ui.theme.HudBorderBright
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
import com.example.ui.theme.ProtonDeepBlue
import com.example.ui.theme.ProtonEmerald
import com.example.ui.theme.ProtonIceBlue
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProtonHudScreen(
    uiState: ProtonUiState,
    onReactorCoreTap: () -> Unit,
    onSubmitQuery: (String) -> Unit,
    onRequestMicPermission: () -> Unit,
    onToggleAiMode: () -> Unit,
    onToggleTts: () -> Unit,
    onToggleWakeWord: () -> Unit,
    onToggleFloatingBubble: () -> Unit,
    onConfirmOverlaySettings: () -> Unit,
    onDismissOverlayRationale: () -> Unit,
    onConfirmPendingAction: () -> Unit,
    onCancelPendingAction: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPremium: () -> Unit,
    onToggleReelsMode: () -> Unit,
    onDismissBanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    var inputText by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    val clockPair by produceState(initialValue = currentClockPair()) {
        while (true) {
            value = currentClockPair()
            delay(15_000L)
        }
    }

    LaunchedEffect(uiState.messages.size, uiState.messages.lastOrNull()?.text) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF061B2E),
                        DeepSpaceNavy,
                        ObsidianVoid
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWideLayout = maxWidth >= 700.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Top HUD Bar
                HudTopBar(
                    coreState = uiState.coreState,
                    aiMode = uiState.aiMode,
                    dateText = clockPair.first,
                    timeText = clockPair.second,
                    onOpenSettings = onOpenSettings
                )

                // Main Content Area (Adaptive: side-by-side on tablets/landscape, vertical on compact phones)
                if (isWideLayout) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(0.45f)
                                .fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceEvenly
                        ) {
                            CoreAndTelemetrySection(
                                uiState = uiState,
                                onReactorCoreTap = onReactorCoreTap,
                                compact = false
                            )
                        }
                        Column(
                            modifier = Modifier
                                .weight(0.55f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TranscriptAndBannerSection(
                                uiState = uiState,
                                listState = listState,
                                onRequestMicPermission = onRequestMicPermission,
                                onQuickPrompt = onSubmitQuery,
                                onDismissBanner = onDismissBanner,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .widthIn(max = 600.dp)
                            .align(Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CoreAndTelemetrySection(
                            uiState = uiState,
                            onReactorCoreTap = onReactorCoreTap,
                            compact = true
                        )

                        TranscriptAndBannerSection(
                            uiState = uiState,
                            listState = listState,
                            onRequestMicPermission = onRequestMicPermission,
                            onQuickPrompt = onSubmitQuery,
                            onDismissBanner = onDismissBanner,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Quick Capability Chip Strip
                QuickControlChipRow(
                    uiState = uiState,
                    onOpenPremium = onOpenPremium,
                    onToggleReelsMode = onToggleReelsMode,
                    onOpenLanguage = onOpenSettings,
                    onToggleAiMode = onToggleAiMode,
                    onToggleTts = onToggleTts,
                    onToggleFloatingBubble = onToggleFloatingBubble,
                    onToggleWakeWord = {
                        if (!uiState.hasMicPermission) {
                            onRequestMicPermission()
                        } else {
                            onToggleWakeWord()
                        }
                    }
                )

                // Command Input + Mic Trigger Row
                CommandInputBar(
                    inputText = inputText,
                    onInputChange = { inputText = it },
                    coreState = uiState.coreState,
                    hasMicPermission = uiState.hasMicPermission,
                    onSend = {
                        if (inputText.isNotBlank()) {
                            onSubmitQuery(inputText)
                            inputText = ""
                        }
                    },
                    onMicClick = {
                        if (!uiState.hasMicPermission) {
                            onRequestMicPermission()
                        } else {
                            onReactorCoreTap()
                        }
                    }
                )
            }
        }

        if (uiState.showOverlayRationaleDialog) {
            AlertDialog(
                onDismissRequest = onDismissOverlayRationale,
                containerColor = HudPanelSurface,
                titleContentColor = ProtonIceBlue,
                textContentColor = HudTextSecondary,
                title = {
                    Text(
                        text = stringResource(R.string.overlay_permission_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                text = {
                    Text(
                        text = stringResource(R.string.overlay_permission_explanation),
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    Button(
                        onClick = onConfirmOverlaySettings,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ProtonCyan,
                            contentColor = ObsidianVoid
                        )
                    ) {
                        Text(
                            text = "Open Settings",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismissOverlayRationale) {
                        Text(
                            text = "Not Now",
                            style = MaterialTheme.typography.labelMedium,
                            color = HudTextSecondary
                        )
                    }
                }
            )
        }

        uiState.pendingConfirmation?.let { pending ->
            AlertDialog(
                onDismissRequest = onCancelPendingAction,
                containerColor = HudPanelSurface,
                titleContentColor = ProtonIceBlue,
                textContentColor = HudTextSecondary,
                title = {
                    Text(
                        text = "CONFIRM SENSITIVE ACTION",
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                text = {
                    Text(
                        text = pending.promptText,
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    Button(
                        onClick = onConfirmPendingAction,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ProtonCyan,
                            contentColor = ObsidianVoid
                        ),
                        modifier = Modifier.testTag("confirm_dial_button")
                    ) {
                        Text(
                            text = "Confirm Dial",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = onCancelPendingAction,
                        modifier = Modifier.testTag("cancel_dial_button")
                    ) {
                        Text(
                            text = "Cancel",
                            style = MaterialTheme.typography.labelMedium,
                            color = HudTextSecondary
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun HudTopBar(
    coreState: CoreState,
    aiMode: AiEngineMode,
    dateText: String,
    timeText: String,
    onOpenSettings: () -> Unit
) {
    val statusText = when (coreState) {
        CoreState.LISTENING -> stringResource(R.string.status_listening)
        CoreState.PROCESSING -> stringResource(R.string.status_processing)
        CoreState.SPEAKING -> stringResource(R.string.status_speaking)
        CoreState.WAKE_ARMED -> stringResource(R.string.status_wake_armed)
        CoreState.ERROR -> "ATTN REQUIRED"
        CoreState.IDLE -> if (aiMode == AiEngineMode.OFFLINE_LOCAL) {
            stringResource(R.string.status_offline_core)
        } else {
            stringResource(R.string.status_online)
        }
    }

    val statusColor = when (coreState) {
        CoreState.ERROR -> ProtonCrimson
        CoreState.SPEAKING -> ProtonEmerald
        CoreState.WAKE_ARMED -> ProtonAmber
        else -> ProtonCyan
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Brand Badge (left)
        Column {
            Text(
                text = stringResource(R.string.hud_brand_spaced),
                style = MaterialTheme.typography.titleMedium,
                color = ProtonIceBlue
            )
            Spacer(modifier = Modifier.height(3.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    modifier = Modifier
                        .width(26.dp)
                        .height(4.dp)
                        .background(ProtonCyan, RoundedCornerShape(2.dp))
                )
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .height(4.dp)
                        .background(ProtonCyan.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                )
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .height(4.dp)
                        .background(ProtonCyan.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                )
            }
        }

        // Center Status Pill
        Surface(
            color = HudPanelSurface.copy(alpha = 0.85f),
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.55f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(statusColor, CircleShape)
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Right Clock + Settings
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.labelMedium,
                    color = HudTextPrimary
                )
                Text(
                    text = dateText,
                    style = MaterialTheme.typography.labelSmall,
                    color = HudTextMuted
                )
            }
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("settings_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Tune,
                    contentDescription = stringResource(R.string.cd_settings_button),
                    tint = ProtonCyan
                )
            }
        }
    }
}

@Composable
private fun CoreAndTelemetrySection(
    uiState: ProtonUiState,
    onReactorCoreTap: () -> Unit,
    compact: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Left: Brand title + Reactor Core
        Column(
            modifier = Modifier.weight(1.05f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ProtonReactorCore(
                coreState = uiState.coreState,
                orbState = uiState.orbState,
                audioLevel = uiState.audioLevel,
                contentDescriptionText = stringResource(R.string.cd_reactor_core),
                onClick = onReactorCoreTap,
                coreSize = if (compact) 158.dp else 210.dp,
                performanceTier = uiState.resolvedPerformanceTier,
                modifier = Modifier.testTag("reactor_core_button")
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (uiState.partialTranscript.isNotBlank()) {
                    "\"${uiState.partialTranscript}\""
                } else {
                    when (uiState.coreState) {
                        CoreState.LISTENING -> "Listening in ${uiState.selectedLanguage.englishName}…"
                        CoreState.PROCESSING -> "Synthesizing response…"
                        CoreState.SPEAKING -> "Tap core to stop voice"
                        CoreState.WAKE_ARMED -> "Say \"Hey Proton\" or tap core"
                        CoreState.ERROR -> "Attention required — tap core"
                        CoreState.IDLE -> if (uiState.orbState == OrbVisualState.OFFLINE) {
                            "Offline Core ready • ${uiState.selectedLanguage.englishName}"
                        } else {
                            stringResource(R.string.action_tap_to_speak)
                        }
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (uiState.partialTranscript.isNotBlank()) ProtonIceBlue else HudTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Right: System Status Card (matches J.A.R.V.I.S HUD panel)
        HudBracketCard(
            modifier = Modifier.weight(0.95f),
            accentColor = ProtonCyan
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "SYSTEM STATUS",
                    style = MaterialTheme.typography.labelMedium,
                    color = ProtonIceBlue
                )
                TelemetryRow(
                    label = "POWER",
                    value = "${uiState.telemetry.batteryPercent}%${if (uiState.telemetry.isCharging) "+" else ""}",
                    valueColor = if (uiState.telemetry.batteryPercent <= 20) ProtonAmber else ProtonCyan
                )
                TelemetryRow(
                    label = "RAM",
                    value = "${uiState.telemetry.ramUsagePercent}%",
                    valueColor = ProtonCyan
                )
                TelemetryRow(
                    label = "NET",
                    value = uiState.telemetry.networkLabel,
                    valueColor = if (uiState.telemetry.isNetworkConnected) ProtonEmerald else ProtonCrimson
                )
                TelemetryRow(
                    label = "LANG",
                    value = uiState.selectedLanguage.code,
                    valueColor = ProtonIceBlue
                )
                TelemetryRow(
                    label = "TORCH",
                    value = if (uiState.telemetry.flashlightOn) "ON" else "OFF",
                    valueColor = if (uiState.telemetry.flashlightOn) ProtonAmber else HudTextSecondary
                )
                TelemetryRow(
                    label = "PERF",
                    value = uiState.resolvedPerformanceTier.label,
                    valueColor = ProtonCyan
                )
                TelemetryRow(
                    label = "THERM",
                    value = uiState.telemetry.capabilities.thermalStatusLabel,
                    valueColor = if (uiState.telemetry.capabilities.isThermalThrottled) ProtonCrimson else ProtonEmerald
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(4.dp))

    AudioWaveformBar(
        coreState = uiState.coreState,
        audioLevel = uiState.audioLevel
    )
}

@Composable
private fun TelemetryRow(
    label: String,
    value: String,
    valueColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = HudTextMuted
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TranscriptAndBannerSection(
    uiState: ProtonUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onRequestMicPermission: () -> Unit,
    onQuickPrompt: (String) -> Unit,
    onDismissBanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AnimatedVisibility(
            visible = !uiState.hasMicPermission,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            HudBracketCard(accentColor = ProtonAmber) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.permission_mic_title),
                            style = MaterialTheme.typography.labelMedium,
                            color = ProtonAmber
                        )
                        Text(
                            text = stringResource(R.string.permission_mic_rationale),
                            style = MaterialTheme.typography.bodyMedium,
                            color = HudTextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onRequestMicPermission,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ProtonCyan,
                            contentColor = ObsidianVoid
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("grant_mic_button")
                    ) {
                        Text(
                            text = "Grant",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }

        AnimatedVisibility(visible = !uiState.statusBanner.isNullOrBlank()) {
            Surface(
                color = HudPanelElevated.copy(alpha = 0.9f),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, HudBorderCyan),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onDismissBanner)
            ) {
                Text(
                    text = uiState.statusBanner.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = ProtonIceBlue,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }

        if (uiState.messages.isEmpty()) {
            EmptyHudWelcomeCard(
                languageName = uiState.selectedLanguage.englishName,
                onQuickPrompt = onQuickPrompt,
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(uiState.messages, key = { it.id }) { msg ->
                    HudMessageCard(
                        message = msg,
                        isStreaming = msg.id == uiState.streamingMessageId
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyHudWelcomeCard(
    languageName: String,
    onQuickPrompt: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val starterCommands = listOf(
        "Turn on flashlight",
        "Check system status",
        "Set a 5 minute timer",
        "Tell me a quick space fact"
    )

    HudBracketCard(
        modifier = modifier.fillMaxWidth(),
        accentColor = HudBorderBright
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "PROTON 5.0 • NEURAL INTERFACE",
                    style = MaterialTheme.typography.titleMedium,
                    color = ProtonCyan
                )
                Text(
                    text = "Voice recognition & spoken replies active in $languageName. Tap the core, say \"Hey Proton\", or choose a quick command:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HudTextSecondary
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                starterCommands.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        pair.forEach { cmd ->
                            Surface(
                                color = HudPanelElevated.copy(alpha = 0.78f),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    HudBorderCyan.copy(alpha = 0.7f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onQuickPrompt(cmd) }
                            ) {
                                Text(
                                    text = cmd,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ProtonIceBlue,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HudMessageCard(
    message: ChatMessage,
    isStreaming: Boolean
) {
    val borderColor = when {
        message.isError -> ProtonCrimson.copy(alpha = 0.75f)
        message.isUser -> HudBorderCyan
        else -> ProtonCyan.copy(alpha = 0.65f)
    }
    val containerColor = if (message.isUser) {
        HudPanelSurface.copy(alpha = 0.78f)
    } else {
        HudPanelElevated.copy(alpha = 0.90f)
    }

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (message.isUser) "YOU • ${message.engineTag}" else "PROTON • ${message.engineTag}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (message.isError) ProtonCrimson else if (message.isUser) HudTextSecondary else ProtonCyan
                )
                if (isStreaming) {
                    Text(
                        text = "STREAMING…",
                        style = MaterialTheme.typography.labelSmall,
                        color = ProtonIceBlue
                    )
                }
            }

            Text(
                text = message.text.ifBlank { if (isStreaming) "…" else "" },
                style = MaterialTheme.typography.bodyLarge,
                color = if (message.isError) ProtonCrimson else HudTextPrimary
            )

            if (!message.actionExecuted.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier
                        .background(ProtonDeepBlue.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Bolt,
                        contentDescription = null,
                        tint = ProtonEmerald,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "ACTION: ${message.actionExecuted}",
                        style = MaterialTheme.typography.labelSmall,
                        color = ProtonEmerald
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickControlChipRow(
    uiState: ProtonUiState,
    onOpenPremium: () -> Unit,
    onToggleReelsMode: () -> Unit,
    onOpenLanguage: () -> Unit,
    onToggleAiMode: () -> Unit,
    onToggleTts: () -> Unit,
    onToggleFloatingBubble: () -> Unit,
    onToggleWakeWord: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = uiState.isPremiumUnlocked,
            onClick = onOpenPremium,
            label = {
                Text(
                    text = if (uiState.isPremiumUnlocked) "Premium Active" else "Premium ₹5",
                    style = MaterialTheme.typography.labelSmall
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.WorkspacePremium,
                    contentDescription = "Open PROTON PREMIUM",
                    modifier = Modifier.size(16.dp)
                )
            },
            colors = hudChipColors(selectedTint = ProtonEmerald),
            modifier = Modifier.testTag("premium_chip")
        )

        FilterChip(
            selected = uiState.reelsModeEnabled && uiState.isPremiumUnlocked,
            onClick = onToggleReelsMode,
            label = {
                Text(
                    text = when {
                        !uiState.isPremiumUnlocked -> "Reels (Pro)"
                        uiState.reelsModeEnabled -> "Reels ON"
                        else -> "Reels OFF"
                    },
                    style = MaterialTheme.typography.labelSmall
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = if (uiState.isPremiumUnlocked) Icons.Filled.Movie else Icons.Filled.Lock,
                    contentDescription = "Toggle Reels Mode",
                    modifier = Modifier.size(16.dp)
                )
            },
            colors = hudChipColors(selectedTint = ProtonEmerald),
            modifier = Modifier.testTag("reels_mode_chip")
        )

        FilterChip(
            selected = uiState.floatingBubble.enabled,
            onClick = onToggleFloatingBubble,
            label = {
                Text(
                    text = if (uiState.floatingBubble.enabled) "Bubble ON" else "Bubble OFF",
                    style = MaterialTheme.typography.labelSmall
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.BubbleChart,
                    contentDescription = "Toggle floating bubble",
                    modifier = Modifier.size(16.dp)
                )
            },
            colors = hudChipColors(),
            modifier = Modifier.testTag("floating_bubble_chip")
        )

        FilterChip(
            selected = true,
            onClick = onOpenLanguage,
            label = {
                Text(
                    text = "${uiState.selectedLanguage.code} (19)",
                    style = MaterialTheme.typography.labelSmall
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Translate,
                    contentDescription = stringResource(R.string.cd_language_button),
                    modifier = Modifier.size(16.dp)
                )
            },
            colors = hudChipColors(),
            modifier = Modifier.testTag("language_chip")
        )

        FilterChip(
            selected = uiState.aiMode == AiEngineMode.ONLINE_GEMINI,
            onClick = onToggleAiMode,
            label = {
                Text(
                    text = when {
                        uiState.aiMode == AiEngineMode.ONLINE_GEMINI && !uiState.isPremiumUnlocked -> "Online (Offline Pro)"
                        uiState.aiMode == AiEngineMode.ONLINE_GEMINI -> "Online AI"
                        else -> "Offline Core"
                    },
                    style = MaterialTheme.typography.labelSmall
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = when {
                        uiState.aiMode == AiEngineMode.ONLINE_GEMINI -> Icons.Filled.Cloud
                        uiState.isPremiumUnlocked -> Icons.Filled.CloudOff
                        else -> Icons.Filled.Lock
                    },
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            },
            colors = hudChipColors(),
            modifier = Modifier.testTag("ai_mode_chip")
        )

        FilterChip(
            selected = uiState.ttsEnabled,
            onClick = onToggleTts,
            label = {
                Text(
                    text = if (uiState.ttsEnabled) "Voice ON" else "Voice Muted",
                    style = MaterialTheme.typography.labelSmall
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = if (uiState.ttsEnabled) {
                        Icons.AutoMirrored.Filled.VolumeUp
                    } else {
                        Icons.AutoMirrored.Filled.VolumeOff
                    },
                    contentDescription = stringResource(R.string.cd_tts_toggle),
                    modifier = Modifier.size(16.dp)
                )
            },
            colors = hudChipColors(),
            modifier = Modifier.testTag("tts_chip")
        )

        FilterChip(
            selected = uiState.wakeWordEnabled,
            onClick = onToggleWakeWord,
            label = {
                Text(
                    text = if (uiState.wakeWordEnabled) "Hey Proton ON" else "Hey Proton OFF",
                    style = MaterialTheme.typography.labelSmall
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Hearing,
                    contentDescription = stringResource(R.string.cd_wake_word_toggle),
                    modifier = Modifier.size(16.dp)
                )
            },
            colors = hudChipColors(selectedTint = ProtonAmber),
            modifier = Modifier.testTag("wake_word_chip")
        )
    }
}

@Composable
private fun hudChipColors(selectedTint: Color = ProtonCyan) = FilterChipDefaults.filterChipColors(
    containerColor = HudPanelSurface.copy(alpha = 0.75f),
    labelColor = HudTextSecondary,
    iconColor = HudTextSecondary,
    selectedContainerColor = selectedTint.copy(alpha = 0.18f),
    selectedLabelColor = selectedTint,
    selectedLeadingIconColor = selectedTint
)

@Composable
private fun CommandInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    coreState: CoreState,
    hasMicPermission: Boolean,
    onSend: () -> Unit,
    onMicClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = inputText,
            onValueChange = onInputChange,
            placeholder = {
                Text(
                    text = stringResource(R.string.input_placeholder),
                    style = MaterialTheme.typography.bodyMedium,
                    color = HudTextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = HudTextPrimary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ProtonCyan,
                unfocusedBorderColor = HudBorderCyan,
                focusedContainerColor = HudPanelSurface.copy(alpha = 0.85f),
                unfocusedContainerColor = HudPanelSurface.copy(alpha = 0.65f),
                cursorColor = ProtonCyan
            ),
            shape = RoundedCornerShape(12.dp),
            trailingIcon = {
                IconButton(
                    onClick = onSend,
                    enabled = inputText.isNotBlank(),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("send_command_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.action_send_command),
                        tint = if (inputText.isNotBlank()) ProtonCyan else HudTextMuted
                    )
                }
            },
            modifier = Modifier
                .weight(1f)
                .testTag("command_input_field")
        )

        val isListening = coreState == CoreState.LISTENING
        Surface(
            onClick = onMicClick,
            shape = CircleShape,
            color = if (isListening) ProtonCyan else HudPanelElevated,
            border = androidx.compose.foundation.BorderStroke(
                1.5.dp,
                if (isListening) ProtonIceBlue else ProtonCyan.copy(alpha = 0.8f)
            ),
            modifier = Modifier
                .size(52.dp)
                .testTag("mic_button")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = when {
                        !hasMicPermission -> Icons.Filled.MicOff
                        isListening -> Icons.Filled.GraphicEq
                        else -> Icons.Filled.Mic
                    },
                    contentDescription = stringResource(R.string.cd_mic_button),
                    tint = if (isListening) ObsidianVoid else ProtonCyan,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

private fun currentClockPair(): Pair<String, String> {
    val now = Date()
    val dateFmt = SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(now).uppercase(Locale.getDefault())
    val timeFmt = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(now).uppercase(Locale.getDefault())
    return dateFmt to timeFmt
}
