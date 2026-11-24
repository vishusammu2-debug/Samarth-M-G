package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.example.data.ProtonPremiumRepository
import com.example.domain.AiEngineMode
import com.example.domain.PremiumPlanSpec
import com.example.domain.PremiumStatus
import com.example.ui.ProtonUiState
import com.example.ui.components.HudBracketCard
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
import com.example.ui.theme.ProtonEmerald
import com.example.ui.theme.ProtonIceBlue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProtonPremiumScreen(
    uiState: ProtonUiState,
    onBack: () -> Unit,
    onSaveBeneficiaryConfig: (
        upiHandle: String,
        accountLabel: String,
        permissionGranted: Boolean,
        verificationEndpoint: String
    ) -> Unit,
    onStartAuthorizedCheckout: () -> Unit,
    onVerifyPayment: () -> Unit,
    onToggleReelsMode: (Boolean) -> Unit,
    onSelectAiMode: (AiEngineMode) -> Unit,
    onDismissStatusMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val membership = uiState.premiumMembership
    val isUnlocked = uiState.isPremiumUnlocked

    var upiHandleInput by rememberSaveable { mutableStateOf(membership.beneficiaryUpiHandle) }
    var accountLabelInput by rememberSaveable { mutableStateOf(membership.beneficiaryAccountLabel) }
    var verificationEndpointInput by rememberSaveable { mutableStateOf(membership.verificationEndpoint) }
    var permissionChecked by rememberSaveable { mutableStateOf(membership.beneficiaryPermissionGranted) }

    LaunchedEffect(
        membership.beneficiaryUpiHandle,
        membership.beneficiaryAccountLabel,
        membership.verificationEndpoint,
        membership.beneficiaryPermissionGranted
    ) {
        if (upiHandleInput.isBlank() && membership.beneficiaryUpiHandle.isNotBlank()) {
            upiHandleInput = membership.beneficiaryUpiHandle
        }
        if (accountLabelInput.isBlank() && membership.beneficiaryAccountLabel.isNotBlank()) {
            accountLabelInput = membership.beneficiaryAccountLabel
        }
        if (verificationEndpointInput.isBlank() && membership.verificationEndpoint.isNotBlank()) {
            verificationEndpointInput = membership.verificationEndpoint
        }
        permissionChecked = membership.beneficiaryPermissionGranted
    }

    val statusColor = when (membership.status) {
        PremiumStatus.ACTIVE -> if (isUnlocked) ProtonEmerald else ProtonAmber
        PremiumStatus.PENDING_VERIFICATION -> ProtonAmber
        PremiumStatus.EXPIRED, PremiumStatus.FAILED -> ProtonCrimson
        PremiumStatus.LOCKED -> ProtonCyan
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF071A2F), DeepSpaceNavy, ObsidianVoid)
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
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
                    .testTag("premium_back_button")
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
                    text = stringResource(R.string.premium_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = ProtonIceBlue
                )
                Text(
                    text = stringResource(R.string.premium_plan_badge),
                    style = MaterialTheme.typography.labelSmall,
                    color = ProtonAmber
                )
            }
            Surface(
                color = statusColor.copy(alpha = 0.16f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.65f))
            ) {
                Text(
                    text = if (isUnlocked) "ACTIVE" else membership.status.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .align(Alignment.CenterHorizontally),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Live Feedback Banner
            if (!uiState.premiumStatusMessage.isNullOrBlank()) {
                item {
                    Surface(
                        color = HudPanelElevated.copy(alpha = 0.94f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.7f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onDismissStatusMessage)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = uiState.premiumStatusMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = HudTextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Plan Hero Card
            item {
                HudBracketCard(accentColor = ProtonAmber) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.WorkspacePremium,
                                    contentDescription = null,
                                    tint = ProtonAmber
                                )
                                Text(
                                    text = PremiumPlanSpec.PLAN_NAME,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = ProtonAmber
                                )
                            }
                            Surface(
                                color = ProtonAmber.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, ProtonAmber.copy(alpha = 0.7f))
                            ) {
                                Text(
                                    text = "₹${PremiumPlanSpec.PRICE_INR} ${PremiumPlanSpec.CURRENCY}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = ProtonAmber,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PlanTermChip(label = "DURATION: 6 MONTHS", modifier = Modifier.weight(1f))
                            PlanTermChip(label = "NO AUTO-RENEWAL", modifier = Modifier.weight(1f))
                        }

                        Text(
                            text = stringResource(R.string.premium_no_autorenew_note),
                            style = MaterialTheme.typography.bodyMedium,
                            color = HudTextSecondary
                        )
                    }
                }
            }

            // Payment Status & Expiry Card
            item {
                HudBracketCard(accentColor = statusColor) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "MEMBERSHIP & PAYMENT STATUS",
                            style = MaterialTheme.typography.titleMedium,
                            color = statusColor
                        )
                        StatusDetailRow(
                            label = "Payment Status",
                            value = if (isUnlocked) "CONFIRMED & ACTIVE" else membership.status.label,
                            valueColor = statusColor
                        )
                        StatusDetailRow(
                            label = "Activated On",
                            value = formatTimestamp(membership.purchasedAtMillis),
                            valueColor = HudTextPrimary
                        )
                        StatusDetailRow(
                            label = "Membership Expiry",
                            value = formatTimestamp(membership.expiresAtMillis, emptyText = "Not activated"),
                            valueColor = if (isUnlocked) ProtonEmerald else ProtonAmber
                        )
                        StatusDetailRow(
                            label = "Billing Type",
                            value = "One-Time ₹5 INR • No Auto-Renewal",
                            valueColor = HudTextSecondary
                        )
                        if (!membership.providerName.isNullOrBlank()) {
                            StatusDetailRow(
                                label = "Payment Provider",
                                value = membership.providerName,
                                valueColor = ProtonCyan
                            )
                        }
                        if (!membership.providerOrderId.isNullOrBlank()) {
                            StatusDetailRow(
                                label = "Provider Order ID",
                                value = membership.providerOrderId,
                                valueColor = HudTextSecondary
                            )
                        }
                        if (!membership.providerPaymentId.isNullOrBlank()) {
                            StatusDetailRow(
                                label = "Verified Payment ID",
                                value = membership.providerPaymentId,
                                valueColor = ProtonEmerald
                            )
                        }
                        if (!membership.statusNote.isNullOrBlank()) {
                            Text(
                                text = membership.statusNote,
                                style = MaterialTheme.typography.labelSmall,
                                color = HudTextSecondary
                            )
                        }
                    }
                }
            }

            // PROTON PREMIUM Benefits Card
            item {
                HudBracketCard(accentColor = ProtonCyan) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "INCLUDED PREMIUM BENEFITS",
                            style = MaterialTheme.typography.titleMedium,
                            color = ProtonCyan
                        )

                        BenefitFeatureRow(
                            icon = Icons.Filled.Movie,
                            title = "Reels Mode",
                            description = "Hands-free voice companion optimized for short-video watching with ultra-concise 1-sentence replies and instant voice app/volume controls.",
                            unlocked = isUnlocked
                        )

                        if (isUnlocked) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .background(HudPanelElevated.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Activate Reels Mode",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = ProtonIceBlue
                                )
                                Switch(
                                    checked = uiState.reelsModeEnabled,
                                    onCheckedChange = onToggleReelsMode,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = ObsidianVoid,
                                        checkedTrackColor = ProtonEmerald
                                    ),
                                    modifier = Modifier.testTag("premium_reels_mode_switch")
                                )
                            }
                        }

                        BenefitFeatureRow(
                            icon = Icons.Filled.CloudOff,
                            title = "Offline Mode (Offline Core)",
                            description = "Process commands, device telemetry, calculations, and multilingual quick replies completely locally when offline.",
                            unlocked = isUnlocked
                        )

                        if (isUnlocked) {
                            OutlinedButton(
                                onClick = {
                                    val next = if (uiState.aiMode == AiEngineMode.OFFLINE_LOCAL) {
                                        AiEngineMode.ONLINE_GEMINI
                                    } else {
                                        AiEngineMode.OFFLINE_LOCAL
                                    }
                                    onSelectAiMode(next)
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ProtonCyan),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .testTag("premium_toggle_offline_button")
                            ) {
                                Text(
                                    text = if (uiState.aiMode == AiEngineMode.OFFLINE_LOCAL) {
                                        "Offline Core Active • Switch to Gemini Online"
                                    } else {
                                        "Switch to Offline Core Now"
                                    },
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }

                        BenefitFeatureRow(
                            icon = Icons.Filled.VerifiedUser,
                            title = "6-Month Verified Access",
                            description = "Unlocked solely through server-verified UPI payment confirmation. Zero recurring charges after 6 months.",
                            unlocked = isUnlocked
                        )
                    }
                }
            }

            // Navi Beneficiary & Authorized Verification Setup
            item {
                HudBracketCard(accentColor = HudBorderBright) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AccountBalanceWallet,
                                contentDescription = null,
                                tint = ProtonCyan
                            )
                            Text(
                                text = "NAVI DESTINATION & AUTHORIZED PROVIDER",
                                style = MaterialTheme.typography.titleMedium,
                                color = ProtonIceBlue
                            )
                        }

                        // Recognized Navi Payment Method Summary (from uploaded Navi profile, no credentials stored)
                        Surface(
                            color = HudPanelSurface.copy(alpha = 0.9f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, HudBorderCyan)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "INTENDED PAYMENT DESTINATION: NAVI UPI",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ProtonCyan
                                )
                                StatusDetailRow(
                                    label = "Beneficiary",
                                    value = membership.beneficiaryAccountLabel.ifBlank { "SHOBHA M GUNDA... (SBI ••5871)" },
                                    valueColor = HudTextPrimary
                                )
                                StatusDetailRow(
                                    label = "Linked Bank",
                                    value = "State Bank of India (••5871)",
                                    valueColor = ProtonIceBlue
                                )
                                StatusDetailRow(
                                    label = "UPI Provider",
                                    value = "Navi (@naviaxis)",
                                    valueColor = ProtonEmerald
                                )
                                StatusDetailRow(
                                    label = "Owner Consent",
                                    value = if (membership.beneficiaryPermissionGranted) "Confirmed" else "Permission Required",
                                    valueColor = if (membership.beneficiaryPermissionGranted) ProtonEmerald else ProtonAmber
                                )
                            }
                        }

                        if (membership.beneficiaryUpiHandle.isBlank() ||
                            membership.verificationEndpoint.isBlank() ||
                            !membership.beneficiaryPermissionGranted
                        ) {
                            Surface(
                                color = ProtonAmber.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, ProtonAmber.copy(alpha = 0.6f))
                            ) {
                                Text(
                                    text = stringResource(R.string.premium_navi_notice),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = ProtonAmber,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        val maskedUpi = ProtonPremiumRepository.maskUpiHandle(membership.beneficiaryUpiHandle)
                        if (maskedUpi.isNotBlank()) {
                            Text(
                                text = "Authorized Navi UPI destination: $maskedUpi",
                                style = MaterialTheme.typography.labelSmall,
                                color = ProtonEmerald
                            )
                        }

                        OutlinedTextField(
                            value = accountLabelInput,
                            onValueChange = { accountLabelInput = it },
                            label = { Text("Beneficiary Account Holder Label") },
                            placeholder = { Text("SHOBHA M GUNDA... (SBI ••5871)") },
                            singleLine = true,
                            colors = premiumInputColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("beneficiary_label_input")
                        )

                        OutlinedTextField(
                            value = upiHandleInput,
                            onValueChange = { upiHandleInput = it },
                            label = { Text("Intended Navi UPI ID (@naviaxis)") },
                            placeholder = { Text("Enter your Navi UPI handle (e.g. handle@naviaxis)") },
                            singleLine = true,
                            colors = premiumInputColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("beneficiary_upi_input")
                        )

                        OutlinedTextField(
                            value = verificationEndpointInput,
                            onValueChange = { verificationEndpointInput = it },
                            label = { Text("Authorized HTTPS Payment Server URL") },
                            placeholder = { Text("https://payments.yourdomain.com/api") },
                            singleLine = true,
                            colors = premiumInputColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("verification_endpoint_input")
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { permissionChecked = !permissionChecked }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = permissionChecked,
                                onCheckedChange = { permissionChecked = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = ProtonCyan,
                                    uncheckedColor = HudTextSecondary,
                                    checkmarkColor = ObsidianVoid
                                ),
                                modifier = Modifier.testTag("beneficiary_permission_checkbox")
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "I confirm the Navi account holder granted permission for this destination and the authorized provider handles settlement.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = HudTextSecondary
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                onSaveBeneficiaryConfig(
                                    upiHandleInput,
                                    accountLabelInput,
                                    permissionChecked,
                                    verificationEndpointInput
                                )
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ProtonIceBlue),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("save_premium_config_button")
                        ) {
                            Text(
                                text = "Save Beneficiary Authorization & Server",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Security,
                                contentDescription = null,
                                tint = ProtonEmerald,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Security guarantee: Personal banking credentials are never stored on device, and premium never unlocks from UPI app redirects, QR scans, screenshots, or user-entered transaction IDs.",
                                style = MaterialTheme.typography.labelSmall,
                                color = HudTextMuted
                            )
                        }
                    }
                }
            }

            // Authorized Checkout & Server Verification Buttons
            item {
                HudBracketCard(accentColor = ProtonEmerald) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "AUTHORIZED ₹5 CHECKOUT & VERIFICATION",
                            style = MaterialTheme.typography.titleMedium,
                            color = ProtonEmerald
                        )

                        Button(
                            onClick = onStartAuthorizedCheckout,
                            enabled = !uiState.premiumBusy,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ProtonCyan,
                                contentColor = ObsidianVoid
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("pay_premium_button")
                        ) {
                            if (uiState.premiumBusy) {
                                CircularProgressIndicator(
                                    color = ObsidianVoid,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = "Pay ₹5 One-Time via Authorized Provider",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }

                        OutlinedButton(
                            onClick = onVerifyPayment,
                            enabled = !uiState.premiumBusy && !membership.providerOrderId.isNullOrBlank(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ProtonEmerald),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("verify_premium_button")
                        ) {
                            Text(
                                text = "Verify Payment Status",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanTermChip(
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = HudPanelSurface.copy(alpha = 0.85f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, HudBorderCyan),
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = ProtonIceBlue,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun StatusDetailRow(
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
            style = MaterialTheme.typography.bodyMedium,
            color = HudTextMuted
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BenefitFeatureRow(
    icon: ImageVector,
    title: String,
    description: String,
    unlocked: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    color = (if (unlocked) ProtonEmerald else ProtonAmber).copy(alpha = 0.14f),
                    shape = RoundedCornerShape(10.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (unlocked) ProtonEmerald else ProtonAmber,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = HudTextPrimary
                )
                Icon(
                    imageVector = if (unlocked) Icons.Filled.CheckCircle else Icons.Filled.Lock,
                    contentDescription = if (unlocked) "Unlocked" else "Locked",
                    tint = if (unlocked) ProtonEmerald else ProtonAmber,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = HudTextSecondary
            )
        }
    }
}

@Composable
private fun premiumInputColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = ProtonCyan,
    unfocusedBorderColor = HudBorderCyan,
    focusedLabelColor = ProtonCyan,
    unfocusedLabelColor = HudTextSecondary,
    focusedTextColor = HudTextPrimary,
    unfocusedTextColor = HudTextPrimary,
    focusedContainerColor = HudPanelSurface.copy(alpha = 0.85f),
    unfocusedContainerColor = HudPanelSurface.copy(alpha = 0.65f),
    cursorColor = ProtonCyan
)

private fun formatTimestamp(epochMs: Long?, emptyText: String = "—"): String {
    if (epochMs == null || epochMs <= 0L) return emptyText
    return SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(epochMs))
}
