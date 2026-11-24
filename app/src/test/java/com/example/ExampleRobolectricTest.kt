package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.OfflineAiEngine
import com.example.data.SafePhoneControlExecutor
import com.example.domain.DeviceTelemetry
import com.example.domain.PhoneControlAction
import com.example.domain.SupportedLanguages
import com.example.domain.VolumeDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app name matches Proton 2_0`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Proton 2.0", appName)
    }

    @Test
    fun `language catalog provides 17 unique languages`() {
        assertEquals(17, SupportedLanguages.all.size)
        assertEquals(17, SupportedLanguages.all.map { it.bcp47Tag }.toSet().size)
    }

    @Test
    fun `safe phone control parser resolves common voice commands`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val executor = SafePhoneControlExecutor(context)

        assertEquals(
            PhoneControlAction.ToggleFlashlight(true),
            executor.parseDirectCommand("Turn on flashlight")
        )
        assertEquals(
            PhoneControlAction.AdjustVolume(VolumeDirection.UP),
            executor.parseDirectCommand("increase volume")
        )
        assertEquals(
            PhoneControlAction.SetTimer(300),
            executor.parseDirectCommand("set timer for 5 minutes")
        )
        assertEquals(
            PhoneControlAction.OpenWifiSettings,
            executor.parseDirectiveTag("WIFI_SETTINGS")
        )
    }

    @Test
    fun `offline AI engine computes math and telemetry`() {
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
    }
}
