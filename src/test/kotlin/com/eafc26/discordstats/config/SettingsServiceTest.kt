package com.eafc26.discordstats.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.prefs.Preferences

class SettingsServiceTest {

    private lateinit var settingsService: SettingsService
    private lateinit var prefs: Preferences

    @BeforeEach
    fun setup() {
        settingsService = SettingsService()
        prefs = Preferences.userNodeForPackage(SettingsService::class.java)
        // Clear all preferences before each test
        prefs.clear()
        prefs.flush()
    }

    @AfterEach
    fun cleanup() {
        // Clean up preferences after each test
        prefs.clear()
        prefs.flush()
    }

    // -- Webhook URL ----------------------------------------------------------

    @Test
    fun `getWebhookUrl returns empty string when not configured`() {
        assertThat(settingsService.getWebhookUrl()).isEmpty()
    }

    @Test
    fun `setWebhookUrl persists the URL`() {
        val url = "https://discord.com/api/webhooks/123/abc"
        settingsService.setWebhookUrl(url)
        
        assertThat(settingsService.getWebhookUrl()).isEqualTo(url)
    }

    @Test
    fun `setWebhookUrl with blank clears the URL`() {
        settingsService.setWebhookUrl("https://discord.com/api/webhooks/123/abc")
        settingsService.setWebhookUrl("")
        
        assertThat(settingsService.getWebhookUrl()).isEmpty()
    }

    @Test
    fun `isWebhookConfigured returns false when not configured`() {
        assertThat(settingsService.isWebhookConfigured()).isFalse()
    }

    @Test
    fun `isWebhookConfigured returns true when configured`() {
        settingsService.setWebhookUrl("https://discord.com/api/webhooks/123/abc")
        
        assertThat(settingsService.isWebhookConfigured()).isTrue()
    }

    // -- History Webhook URL --------------------------------------------------

    @Test
    fun `getHistoryWebhookUrl returns empty string when not configured`() {
        assertThat(settingsService.getHistoryWebhookUrl()).isEmpty()
    }

    @Test
    fun `setHistoryWebhookUrl persists the URL`() {
        val url = "https://discord.com/api/webhooks/456/def"
        settingsService.setHistoryWebhookUrl(url)
        
        assertThat(settingsService.getHistoryWebhookUrl()).isEqualTo(url)
    }

    @Test
    fun `setHistoryWebhookUrl with blank clears the URL`() {
        settingsService.setHistoryWebhookUrl("https://discord.com/api/webhooks/456/def")
        settingsService.setHistoryWebhookUrl("")
        
        assertThat(settingsService.getHistoryWebhookUrl()).isEmpty()
    }

    @Test
    fun `isHistoryWebhookConfigured returns false when not configured`() {
        assertThat(settingsService.isHistoryWebhookConfigured()).isFalse()
    }

    @Test
    fun `isHistoryWebhookConfigured returns true when configured`() {
        settingsService.setHistoryWebhookUrl("https://discord.com/api/webhooks/456/def")
        
        assertThat(settingsService.isHistoryWebhookConfigured()).isTrue()
    }

    // -- Network Settings -----------------------------------------------------

    @Test
    fun `isNetworkEnabled returns false by default`() {
        assertThat(settingsService.isNetworkEnabled()).isFalse()
    }

    @Test
    fun `setNetworkEnabled persists the value`() {
        settingsService.setNetworkEnabled(true)
        
        assertThat(settingsService.isNetworkEnabled()).isTrue()
    }

    @Test
    fun `setNetworkEnabled can toggle back to false`() {
        settingsService.setNetworkEnabled(true)
        settingsService.setNetworkEnabled(false)
        
        assertThat(settingsService.isNetworkEnabled()).isFalse()
    }

    // -- Club ID --------------------------------------------------------------

    @Test
    fun `getClubId returns empty string when not configured`() {
        assertThat(settingsService.getClubId()).isEmpty()
    }

    @Test
    fun `setClubId persists the value`() {
        settingsService.setClubId("12345")
        
        assertThat(settingsService.getClubId()).isEqualTo("12345")
    }

    @Test
    fun `setClubId with blank clears the value`() {
        settingsService.setClubId("12345")
        settingsService.setClubId("")
        
        assertThat(settingsService.getClubId()).isEmpty()
    }

    // -- Platform -------------------------------------------------------------

    @Test
    fun `getPlatform returns empty string when not configured`() {
        assertThat(settingsService.getPlatform()).isEmpty()
    }

    @Test
    fun `setPlatform persists the value`() {
        settingsService.setPlatform("common-gen5")
        
        assertThat(settingsService.getPlatform()).isEqualTo("common-gen5")
    }

    @Test
    fun `setPlatform with blank clears the value`() {
        settingsService.setPlatform("common-gen5")
        settingsService.setPlatform("")
        
        assertThat(settingsService.getPlatform()).isEmpty()
    }

    // -- Polling Interval -----------------------------------------------------

    @Test
    fun `getPollingIntervalMs returns default when not configured`() {
        assertThat(settingsService.getPollingIntervalMs()).isEqualTo(60_000L)
    }

    @Test
    fun `getPollingIntervalMs returns custom default when specified`() {
        assertThat(settingsService.getPollingIntervalMs(120_000L)).isEqualTo(120_000L)
    }

    @Test
    fun `setPollingIntervalMs persists the value`() {
        settingsService.setPollingIntervalMs(120_000L)
        
        assertThat(settingsService.getPollingIntervalMs()).isEqualTo(120_000L)
    }

    // -- Clear All ------------------------------------------------------------

    @Test
    fun `clearAll removes all saved settings`() {
        settingsService.setWebhookUrl("https://discord.com/api/webhooks/123/abc")
        settingsService.setHistoryWebhookUrl("https://discord.com/api/webhooks/456/def")
        settingsService.setNetworkEnabled(true)
        settingsService.setClubId("12345")
        settingsService.setPlatform("common-gen5")
        settingsService.setPollingIntervalMs(120_000L)
        
        settingsService.clearAll()
        
        assertThat(settingsService.getWebhookUrl()).isEmpty()
        assertThat(settingsService.getHistoryWebhookUrl()).isEmpty()
        assertThat(settingsService.isNetworkEnabled()).isFalse()
        assertThat(settingsService.getClubId()).isEmpty()
        assertThat(settingsService.getPlatform()).isEmpty()
        assertThat(settingsService.getPollingIntervalMs()).isEqualTo(60_000L)
    }

    // -- Persistence across instances -----------------------------------------

    @Test
    fun `settings persist across service instances`() {
        settingsService.setWebhookUrl("https://discord.com/api/webhooks/999/xyz")
        
        // Create a new instance to verify persistence
        val newInstance = SettingsService()
        
        assertThat(newInstance.getWebhookUrl()).isEqualTo("https://discord.com/api/webhooks/999/xyz")
    }
}

