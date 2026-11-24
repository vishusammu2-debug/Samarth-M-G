package com.example

import com.example.data.BeneficiaryValidationResult
import com.example.data.CommandRouteDecision
import com.example.data.DevicePerformanceProfiler
import com.example.data.EndpointValidationResult
import com.example.data.InstalledAppEntry
import com.example.data.LanguageDetector
import com.example.data.OfflineAiEngine
import com.example.data.OrderCreationResult
import com.example.data.PaymentVerificationResult
import com.example.data.ProtonAppLauncher
import com.example.data.ProtonCommandRouter
import com.example.data.ProtonPremiumRepository
import com.example.domain.AccessibilityNavAction
import com.example.domain.DeviceTelemetry
import com.example.domain.FloatingBubbleConfig
import com.example.domain.OrbVisualState
import com.example.domain.PhoneControlAction
import com.example.domain.PremiumMembershipState
import com.example.domain.PremiumPlanSpec
import com.example.domain.PremiumStatus
import com.example.domain.ResolvedPerformanceTier
import com.example.domain.SupportedLanguages
import com.example.domain.VolumeDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ExampleUnitTest {

    @Test
    fun languageCatalogProvides19LanguagesIncludingKannadaAndUttaraKannada() {
        assertEquals(19, SupportedLanguages.all.size)
        assertEquals(19, SupportedLanguages.all.map { it.id }.toSet().size)

        val kn = SupportedLanguages.findById("kn-IN")
        val uk = SupportedLanguages.findById("kn-UK")
        assertEquals("Kannada", kn.englishName)
        assertEquals("Uttara Kannada", uk.englishName)
        assertEquals("kn-IN", uk.bcp47Tag)
        assertTrue(!uk.dialectPromptHint.isNullOrBlank())
    }

    @Test
    fun languageDetectorIdentifiesKannadaAndUttaraKannadaCues() {
        val english = SupportedLanguages.findById("en-US")
        val kannadaDetected = LanguageDetector.detectFromInput("ನಮಸ್ಕಾರ ಪ್ರೋಟಾನ್", english)
        assertEquals("kn-IN", kannadaDetected.id)

        val ukDetected = LanguageDetector.detectFromInput("ಹೇಗಿದ್ದೀಯ ಮಾರಾಯ karwar", english)
        assertEquals("kn-UK", ukDetected.id)
    }

    @Test
    fun offlineAiEngineSupportsStandardAndUttaraKannadaAndTelemetry() {
        val engine = OfflineAiEngine()
        val telemetry = DeviceTelemetry(batteryPercent = 82, ramUsagePercent = 48, networkLabel = "OFFLINE")

        val mathReply = engine.processLocally(
            prompt = "calculate 12 * 8",
            language = SupportedLanguages.all.first(),
            telemetry = telemetry,
            executedControlMessage = null
        )
        assertTrue(mathReply.text.contains("96"))

        val statusReply = engine.processLocally(
            prompt = "check system status",
            language = SupportedLanguages.all.first(),
            telemetry = telemetry,
            executedControlMessage = null
        )
        assertTrue(statusReply.text.contains("82%"))
        assertTrue(statusReply.text.contains("48%"))

        val ukGreeting = engine.processLocally(
            prompt = "ನಮಸ್ಕಾರ",
            language = SupportedLanguages.findById("kn-UK"),
            telemetry = telemetry,
            executedControlMessage = null
        )
        assertTrue(ukGreeting.text.contains("ಮಾರಾಯ"))

        val knBattery = engine.processLocally(
            prompt = "ಬ್ಯಾಟರಿ ಎಷ್ಟಿದೆ",
            language = SupportedLanguages.findById("kn-IN"),
            telemetry = telemetry,
            executedControlMessage = null
        )
        assertTrue(knBattery.text.contains("82%"))
    }

    @Test
    fun floatingBubbleConfigAndOrbStatesHaveExpectedDefaults() {
        val config = FloatingBubbleConfig()
        assertEquals(false, config.enabled)
        assertTrue(config.rotatingRingsEnabled)
        assertTrue(config.electricArcsEnabled)
        assertTrue(config.sizeDp in 56f..108f)
        assertTrue(config.opacity in 0.35f..1.0f)
        assertTrue(config.glowIntensity in 0.2f..2.0f)
        assertTrue(config.particleIntensity in 0.0f..1.5f)
        assertEquals(6, OrbVisualState.entries.size)
    }

    @Test
    fun accessibilityActionsIncludeExpectedNavigationCommands() {
        val names = AccessibilityNavAction.entries.map { it.name }.toSet()
        assertTrue(names.contains("BACK"))
        assertTrue(names.contains("HOME"))
        assertTrue(names.contains("RECENTS"))
        assertTrue(names.contains("NOTIFICATIONS"))
        assertTrue(names.contains("QUICK_SETTINGS"))
        assertTrue(names.contains("TAKE_SCREENSHOT"))
    }

    @Test
    fun commandRouterClassifiesAppLaunchesDeviceControlsAndGeneralAiQueries() {
        val router = ProtonCommandRouter(duplicateWindowMs = 1_800L)
        val en = SupportedLanguages.findById("en-US")

        val yt = router.route("Open YouTube", en, hasPendingConfirmation = false, nowMs = 1_000L)
        assertTrue(yt is CommandRouteDecision.ExecuteLocalAction)
        val ytAction = (yt as CommandRouteDecision.ExecuteLocalAction).action
        assertTrue(ytAction is PhoneControlAction.OpenApp && ytAction.appQuery == "YouTube")

        val chrome = router.route("Open Chrome", en, hasPendingConfirmation = false, nowMs = 3_000L)
        assertEquals(
            "Open Chrome",
            (chrome as CommandRouteDecision.ExecuteLocalAction).action.displayLabel
        )

        val gmail = router.route("Open Gmail", en, hasPendingConfirmation = false, nowMs = 5_000L)
        assertEquals(
            "Open Gmail",
            (gmail as CommandRouteDecision.ExecuteLocalAction).action.displayLabel
        )

        val cam = router.route("Open Camera", en, hasPendingConfirmation = false, nowMs = 7_000L)
        assertTrue((cam as CommandRouteDecision.ExecuteLocalAction).action is PhoneControlAction.OpenCamera)

        val settings = router.route("Open Settings", en, hasPendingConfirmation = false, nowMs = 9_000L)
        assertTrue((settings as CommandRouteDecision.ExecuteLocalAction).action is PhoneControlAction.OpenSystemSettings)

        val customApp = router.route("Open Spotify", en, hasPendingConfirmation = false, nowMs = 11_000L)
        val customAction = (customApp as CommandRouteDecision.ExecuteLocalAction).action as PhoneControlAction.OpenApp
        assertEquals("Spotify", customAction.appQuery)

        val torchOn = router.route("Turn on flashlight", en, hasPendingConfirmation = false, nowMs = 13_000L)
        assertEquals(
            PhoneControlAction.ToggleFlashlight(true),
            (torchOn as CommandRouteDecision.ExecuteLocalAction).action
        )

        val volUp = router.route("Increase media volume", en, hasPendingConfirmation = false, nowMs = 15_000L)
        assertEquals(
            PhoneControlAction.AdjustVolume(VolumeDirection.UP),
            (volUp as CommandRouteDecision.ExecuteLocalAction).action
        )

        val timeCmd = router.route("What time is it", en, hasPendingConfirmation = false, nowMs = 17_000L)
        assertTrue((timeCmd as CommandRouteDecision.ExecuteLocalAction).action is PhoneControlAction.TellTimeDate)

        val alarm = router.route("Set alarm for 6:30 am", en, hasPendingConfirmation = false, nowMs = 19_000L)
        val alarmAction = (alarm as CommandRouteDecision.ExecuteLocalAction).action as PhoneControlAction.SetAlarm
        assertEquals(6, alarmAction.hour)
        assertEquals(30, alarmAction.minute)

        val search = router.route("Search quantum computing", en, hasPendingConfirmation = false, nowMs = 21_000L)
        val searchAction = (search as CommandRouteDecision.ExecuteLocalAction).action as PhoneControlAction.WebSearch
        assertEquals("quantum computing", searchAction.query)

        val ai = router.route("Explain black holes simply", en, hasPendingConfirmation = false, nowMs = 23_000L)
        assertTrue(ai is CommandRouteDecision.SendToAi)
    }

    @Test
    fun commandRouterEnforcesCallConfirmationAndDeduplicatesRepeatedUtterances() {
        val router = ProtonCommandRouter(duplicateWindowMs = 1_800L)
        val en = SupportedLanguages.findById("en-US")

        val firstCall = router.route("Call 9876543210", en, hasPendingConfirmation = false, nowMs = 1_000L)
        assertTrue(firstCall is CommandRouteDecision.RequireConfirmation)
        val dialAction = (firstCall as CommandRouteDecision.RequireConfirmation).pending.action
        assertTrue(dialAction is PhoneControlAction.OpenDialer)
        assertEquals("9876543210", (dialAction as PhoneControlAction.OpenDialer).phoneNumber)

        val duplicate = router.route("call   9876543210", en, hasPendingConfirmation = true, nowMs = 1_900L)
        assertTrue(duplicate is CommandRouteDecision.DuplicateIgnored)

        val approved = router.route("Yes", en, hasPendingConfirmation = true, nowMs = 3_000L)
        assertEquals(CommandRouteDecision.ConfirmPending(approved = true), approved)

        val cancelled = router.route("Cancel", en, hasPendingConfirmation = true, nowMs = 5_000L)
        assertEquals(CommandRouteDecision.ConfirmPending(approved = false), cancelled)
    }

    @Test
    fun commandRouterSupportsKannadaAndHindiCommands() {
        val router = ProtonCommandRouter()
        val kn = SupportedLanguages.findById("kn-IN")
        val hi = SupportedLanguages.findById("hi-IN")

        val knTorch = router.route("ಟಾರ್ಚ್ ಆನ್", kn, hasPendingConfirmation = false, nowMs = 1_000L)
        assertEquals(
            PhoneControlAction.ToggleFlashlight(true),
            (knTorch as CommandRouteDecision.ExecuteLocalAction).action
        )

        val hiYoutube = router.route("यूट्यूब खोलो", hi, hasPendingConfirmation = false, nowMs = 3_000L)
        val hiAction = (hiYoutube as CommandRouteDecision.ExecuteLocalAction).action as PhoneControlAction.OpenApp
        assertEquals("YouTube", hiAction.appQuery)
    }

    @Test
    fun adaptiveProfilerComputesLowBalancedAndHighPerformanceTiers() {
        val lowRamTier = DevicePerformanceProfiler.computeRecommendedTier(
            totalRamMb = 2048,
            availRamMb = 500,
            cpuCores = 4,
            refreshRateHz = 60,
            batteryPercent = 80,
            isLowRamDevice = true,
            isPowerSaveMode = false,
            isThermalThrottled = false
        )
        assertEquals(ResolvedPerformanceTier.LOW_END, lowRamTier)

        val thermalThrottledTier = DevicePerformanceProfiler.computeRecommendedTier(
            totalRamMb = 8192,
            availRamMb = 3200,
            cpuCores = 8,
            refreshRateHz = 120,
            batteryPercent = 90,
            isLowRamDevice = false,
            isPowerSaveMode = false,
            isThermalThrottled = true
        )
        assertEquals(ResolvedPerformanceTier.LOW_END, thermalThrottledTier)

        val midRangeTier = DevicePerformanceProfiler.computeRecommendedTier(
            totalRamMb = 6144,
            availRamMb = 2100,
            cpuCores = 8,
            refreshRateHz = 90,
            batteryPercent = 70,
            isLowRamDevice = false,
            isPowerSaveMode = false,
            isThermalThrottled = false
        )
        assertEquals(ResolvedPerformanceTier.BALANCED, midRangeTier)

        val flagshipTier = DevicePerformanceProfiler.computeRecommendedTier(
            totalRamMb = 8192,
            availRamMb = 2800,
            cpuCores = 8,
            refreshRateHz = 120,
            batteryPercent = 85,
            isLowRamDevice = false,
            isPowerSaveMode = false,
            isThermalThrottled = false
        )
        assertEquals(ResolvedPerformanceTier.HIGH_PERFORMANCE, flagshipTier)
    }

    @Test
    fun commandRouterExtractsNaturalLanguageAppLaunchVariations() {
        val router = ProtonCommandRouter(duplicateWindowMs = 1_800L)
        val en = SupportedLanguages.findById("en-US")

        val commands = listOf(
            "Open YouTube" to "Open YouTube",
            "Please open YouTube." to "Open YouTube",
            "Can you launch Chrome?" to "Open Chrome",
            "Proton, start WhatsApp." to "Open WhatsApp",
            "Open Instagram" to "Open Instagram",
            "Open Gmail" to "Open Gmail",
            "Open Camera" to "Launch Camera",
            "Open Settings" to "Open System Settings",
            "Open Play Store" to "Open Play Store"
        )

        var time = 10_000L
        for ((spoken, expectedLabel) in commands) {
            val decision = router.route(spoken, en, hasPendingConfirmation = false, nowMs = time)
            assertTrue("Expected local action for: $spoken", decision is CommandRouteDecision.ExecuteLocalAction)
            val action = (decision as CommandRouteDecision.ExecuteLocalAction).action
            assertEquals("Mismatch for: $spoken", expectedLabel, action.displayLabel)
            time += 2_500L
        }
    }

    @Test
    fun appLauncherResolvesInstalledAppsDynamicallyAndRejectsMissingApps() {
        val installed = listOf(
            InstalledAppEntry("YouTube", "youtube", "com.google.android.youtube"),
            InstalledAppEntry("Chrome", "chrome", "com.android.chrome"),
            InstalledAppEntry("WhatsApp", "whatsapp", "com.whatsapp"),
            InstalledAppEntry("Instagram", "instagram", "com.instagram.android"),
            InstalledAppEntry("Gmail", "gmail", "com.google.android.gm"),
            InstalledAppEntry("Google Play Store", "google play store", "com.android.vending"),
            InstalledAppEntry("Telegram X", "telegram x", "org.thunderdog.challegram")
        )

        assertEquals("com.google.android.youtube", ProtonAppLauncher.findBestAppMatch("YouTube.", installed)?.packageName)
        assertEquals("com.android.chrome", ProtonAppLauncher.findBestAppMatch("Chrome", installed)?.packageName)
        assertEquals("com.whatsapp", ProtonAppLauncher.findBestAppMatch("WhatsApp", installed)?.packageName)
        assertEquals("com.instagram.android", ProtonAppLauncher.findBestAppMatch("Instagram", installed)?.packageName)
        assertEquals("com.google.android.gm", ProtonAppLauncher.findBestAppMatch("Gmail", installed)?.packageName)
        assertEquals("com.android.vending", ProtonAppLauncher.findBestAppMatch("Play Store", installed)?.packageName)
        assertEquals("org.thunderdog.challegram", ProtonAppLauncher.findBestAppMatch("Telegram", installed)?.packageName)
        assertEquals(null, ProtonAppLauncher.findBestAppMatch("NonExistentAppXYZ", installed))
    }

    @Test
    fun premiumPlanSpecsAndSixMonthExpiryEnforcementWorkCorrectly() {
        assertEquals(5, PremiumPlanSpec.PRICE_INR)
        assertEquals("INR", PremiumPlanSpec.CURRENCY)
        assertEquals(6, PremiumPlanSpec.DURATION_MONTHS)
        assertFalse(PremiumPlanSpec.AUTO_RENEWAL)

        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(2026, Calendar.JANUARY, 15, 10, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        val sixMonthsLater = ProtonPremiumRepository.computeSixMonthExpiryMillis(start)
        val expectedCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = sixMonthsLater
        }
        assertEquals(2026, expectedCal.get(Calendar.YEAR))
        assertEquals(Calendar.JULY, expectedCal.get(Calendar.MONTH))
        assertEquals(15, expectedCal.get(Calendar.DAY_OF_MONTH))

        val activeMembership = PremiumMembershipState(
            status = PremiumStatus.ACTIVE,
            purchasedAtMillis = start,
            expiresAtMillis = sixMonthsLater,
            providerOrderId = "order_123",
            providerPaymentId = "pay_456",
            providerName = "Razorpay"
        )
        assertTrue(activeMembership.isPremiumActive(nowMillis = start + 60_000L))
        assertFalse(activeMembership.isPremiumActive(nowMillis = sixMonthsLater))

        val expired = ProtonPremiumRepository.enforceExpiryState(activeMembership, nowMillis = sixMonthsLater + 1_000L)
        assertEquals(PremiumStatus.EXPIRED, expired.status)
        assertFalse(expired.isPremiumActive(nowMillis = sixMonthsLater + 1_000L))
    }

    @Test
    fun verificationEndpointValidationBlocksUnsafeUrlsAndAllowsPublicHttps() {
        assertTrue(
            ProtonPremiumRepository.validateVerificationEndpoint("https://pay.protonassistant.example/api")
                is EndpointValidationResult.Valid
        )
        assertTrue(
            ProtonPremiumRepository.validateVerificationEndpoint("http://pay.protonassistant.example/api")
                is EndpointValidationResult.Invalid
        )
        assertTrue(
            ProtonPremiumRepository.validateVerificationEndpoint("https://localhost:8443/api")
                is EndpointValidationResult.Invalid
        )
        assertTrue(
            ProtonPremiumRepository.validateVerificationEndpoint("https://127.0.0.1/api")
                is EndpointValidationResult.Invalid
        )
        assertTrue(
            ProtonPremiumRepository.validateVerificationEndpoint("https://192.168.1.50/api")
                is EndpointValidationResult.Invalid
        )
        assertTrue(
            ProtonPremiumRepository.validateVerificationEndpoint("https://169.254.169.254/latest")
                is EndpointValidationResult.Invalid
        )
        assertTrue(
            ProtonPremiumRepository.validateVerificationEndpoint("https://10.0.0.5/api")
                is EndpointValidationResult.Invalid
        )
        assertTrue(
            ProtonPremiumRepository.validateVerificationEndpoint("https://user:secret@pay.example.com/api")
                is EndpointValidationResult.Invalid
        )
    }

    @Test
    fun beneficiaryPermissionAndMaskingEnforceAuthorization() {
        assertTrue(
            ProtonPremiumRepository.validateBeneficiaryConfig(
                upiHandle = "accountowner@navi",
                accountLabel = "Owner Name",
                permissionGranted = false
            ) is BeneficiaryValidationResult.Invalid
        )
        assertTrue(
            ProtonPremiumRepository.validateBeneficiaryConfig(
                upiHandle = "not-a-upi-id",
                accountLabel = "Owner Name",
                permissionGranted = true
            ) is BeneficiaryValidationResult.Invalid
        )
        val valid = ProtonPremiumRepository.validateBeneficiaryConfig(
            upiHandle = "accountowner@navi",
            accountLabel = "Owner Name",
            permissionGranted = true
        )
        assertTrue(valid is BeneficiaryValidationResult.Valid)
        assertEquals("ac****@navi", (valid as BeneficiaryValidationResult.Valid).maskedUpi)
    }

    @Test
    fun orderAndServerVerificationPayloadsEnforceStrictFiveRupeeOneTimeSixMonthTerms() {
        val validOrderJson = """
            {
              "orderId": "order_proton_001",
              "provider": "Razorpay UPI",
              "checkoutUrl": "https://checkout.razorpay.com/v1/checkout/order_proton_001",
              "amountInr": 5,
              "currency": "INR",
              "durationMonths": 6,
              "autoRenew": false
            }
        """.trimIndent()
        val parsedOrder = ProtonPremiumRepository.parseAndValidateOrderResponse(validOrderJson)
        assertTrue(parsedOrder is OrderCreationResult.Success)

        val autoRenewOrderJson = """
            {
              "orderId": "order_proton_002",
              "provider": "Razorpay UPI",
              "checkoutUrl": "https://checkout.razorpay.com/v1/checkout/order_proton_002",
              "amountInr": 5,
              "currency": "INR",
              "durationMonths": 6,
              "autoRenew": true
            }
        """.trimIndent()
        assertTrue(ProtonPremiumRepository.parseAndValidateOrderResponse(autoRenewOrderJson) is OrderCreationResult.Failure)

        val now = 1_760_000_000_000L
        val sixMonthsLater = ProtonPremiumRepository.computeSixMonthExpiryMillis(now)
        val validVerifyJson = """
            {
              "verified": true,
              "orderId": "order_proton_001",
              "paymentId": "pay_upi_999",
              "provider": "Razorpay UPI",
              "amountInr": 5,
              "currency": "INR",
              "durationMonths": 6,
              "autoRenew": false,
              "purchasedAtEpochMs": $now,
              "expiresAtEpochMs": $sixMonthsLater
            }
        """.trimIndent()
        val verified = ProtonPremiumRepository.parseAndValidateVerificationPayload(
            jsonBody = validVerifyJson,
            expectedOrderId = "order_proton_001",
            nowMillis = now + 5_000L
        )
        assertTrue(verified is PaymentVerificationResult.Verified)
        assertEquals(sixMonthsLater, (verified as PaymentVerificationResult.Verified).expiresAtMillis)

        val tamperedAmountVerifyJson = validVerifyJson.replace("\"amountInr\": 5", "\"amountInr\": 1")
        assertTrue(
            ProtonPremiumRepository.parseAndValidateVerificationPayload(
                jsonBody = tamperedAmountVerifyJson,
                expectedOrderId = "order_proton_001",
                nowMillis = now + 5_000L
            ) is PaymentVerificationResult.Unverified
        )

        val unverifiedJson = """{"verified": false, "status": "PENDING", "message": "Awaiting UPI settlement"}"""
        val pendingRes = ProtonPremiumRepository.parseAndValidateVerificationPayload(
            jsonBody = unverifiedJson,
            expectedOrderId = "order_proton_001",
            nowMillis = now
        )
        assertTrue(pendingRes is PaymentVerificationResult.Unverified && pendingRes.isPending)
    }

    @Test
    fun commandRouterRoutesReelsModeVoiceCommands() {
        val router = ProtonCommandRouter()
        val en = SupportedLanguages.findById("en-US")

        val enable = router.route("Enable Reels Mode", en, hasPendingConfirmation = false, nowMs = 1_000L)
        assertEquals(
            PhoneControlAction.ToggleReelsMode(true),
            (enable as CommandRouteDecision.ExecuteLocalAction).action
        )

        val disable = router.route("Disable Reels Mode", en, hasPendingConfirmation = false, nowMs = 4_000L)
        assertEquals(
            PhoneControlAction.ToggleReelsMode(false),
            (disable as CommandRouteDecision.ExecuteLocalAction).action
        )
    }
}
