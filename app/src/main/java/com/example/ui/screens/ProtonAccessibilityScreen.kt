package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.AccessibilityNavAction
import com.example.domain.PhoneControlAction
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

@Composable
fun ProtonAccessibilityScreen(
    uiState: ProtonUiState,
    onBack: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onDisableAccessibilityService: () -> Unit,
    onExecuteControl: (PhoneControlAction) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val statusColor = if (uiState.isAccessibilityEnabled) ProtonEmerald else ProtonAmber

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
                    .testTag("accessibility_back_button")
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
                    text = "ACCESSIBILITY SETTINGS",
                    style = MaterialTheme.typography.headlineSmall,
                    color = ProtonIceBlue
                )
                Text(
                    text = "User-Authorized System Navigation & Privacy Guardrails",
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
            // 1. Live Service Status & Explicit Manual Control
            item {
                HudBracketCard(accentColor = statusColor) {
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
                                    imageVector = Icons.Filled.AccessibilityNew,
                                    contentDescription = null,
                                    tint = statusColor
                                )
                                Text(
                                    text = "PROTON ACCESSIBILITY SERVICE",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = statusColor
                                )
                            }
                            Surface(
                                color = statusColor.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(6.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.55f))
                            ) {
                                Text(
                                    text = if (uiState.isAccessibilityEnabled) "ENABLED" else "DISABLED",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = statusColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }
                        }

                        Text(
                            text = if (uiState.isAccessibilityEnabled) {
                                "Proton Accessibility Service is active. Global navigation actions only run when you explicitly tap a control or issue a voice/text command."
                            } else {
                                "Proton Accessibility Service is currently off. Review the privacy disclosure below, then tap the button to enable it manually in Android Settings if you want hands-free Back, Home, Recents, Notifications, Quick Settings, or Screenshot actions."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = HudTextSecondary
                        )

                        Button(
                            onClick = onOpenAccessibilitySettings,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ProtonCyan,
                                contentColor = ObsidianVoid
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("accessibility_open_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (uiState.isAccessibilityEnabled) {
                                    "Open Android Accessibility Settings"
                                } else {
                                    "Enable in Android Accessibility Settings"
                                },
                                style = MaterialTheme.typography.labelMedium
                            )
                        }

                        OutlinedButton(
                            onClick = onDisableAccessibilityService,
                            enabled = uiState.isAccessibilityEnabled,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (uiState.isAccessibilityEnabled) ProtonCrimson else HudTextMuted
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("accessibility_disable_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Block,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (uiState.isAccessibilityEnabled) {
                                    "Disable Accessibility Service Immediately"
                                } else {
                                    "Accessibility Service Is Inactive"
                                },
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }

            // 2. Zero-Surveillance Privacy Guarantees
            item {
                HudBracketCard(accentColor = ProtonCyan) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "PRIVACY & SAFETY DISCLOSURE",
                            style = MaterialTheme.typography.titleMedium,
                            color = ProtonCyan
                        )

                        PrivacyGuaranteeRow(
                            icon = Icons.Filled.VisibilityOff,
                            title = "No Window Content Inspection",
                            description = "canRetrieveWindowContent is explicitly set to false. Proton cannot inspect passwords, messages, notifications, or other apps' UI."
                        )
                        PrivacyGuaranteeRow(
                            icon = Icons.Filled.Lock,
                            title = "No Silent Background Monitoring",
                            description = "Accessibility events are ignored (no-op). Proton never logs keystrokes, screen text, or app usage."
                        )
                        PrivacyGuaranteeRow(
                            icon = Icons.Filled.Shield,
                            title = "Explicit Command Execution Only",
                            description = "Proton never attempts to auto-enable accessibility. Only user-triggered global navigation intents run."
                        )
                    }
                }
            }

            // 3. Permitted Actions Catalog & User Test Controls
            item {
                Text(
                    text = "PERMITTED NAVIGATION ACTIONS",
                    style = MaterialTheme.typography.titleMedium,
                    color = ProtonIceBlue
                )
            }

            items(AccessibilityNavAction.entries, key = { it.name }) { navAction ->
                Surface(
                    color = HudPanelSurface.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (uiState.isAccessibilityEnabled) HudBorderCyan else HudBorderCyan.copy(alpha = 0.45f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = uiState.isAccessibilityEnabled) {
                            onExecuteControl(PhoneControlAction.AccessibilityNavigation(navAction))
                        }
                        .testTag("accessibility_action_${navAction.name}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = navAction.label,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = ProtonIceBlue
                            )
                            Text(
                                text = navAction.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = HudTextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Surface(
                            color = if (uiState.isAccessibilityEnabled) {
                                ProtonCyan.copy(alpha = 0.16f)
                            } else {
                                HudPanelElevated
                            },
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (uiState.isAccessibilityEnabled) ProtonCyan else HudTextMuted.copy(alpha = 0.4f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = "Run ${navAction.label}",
                                    tint = if (uiState.isAccessibilityEnabled) ProtonCyan else HudTextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (uiState.isAccessibilityEnabled) "Run" else "Locked",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (uiState.isAccessibilityEnabled) ProtonCyan else HudTextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
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
private fun PrivacyGuaranteeRow(
    icon: ImageVector,
    title: String,
    description: String
) {
    Surface(
        color = HudPanelSurface.copy(alpha = 0.85f),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, HudBorderCyan)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ProtonCyan,
                modifier = Modifier.size(20.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = ProtonIceBlue
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = HudTextSecondary
                )
            }
        }
    }
}
