package com.eafc26.discordstats.config

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.prefs.Preferences

class WebhookConfigServiceTest {

    private lateinit var settingsService: SettingsService
    private lateinit var prefs: Preferences

    @BeforeEach
    fun setup() {
        prefs = Preferences.userNodeForPackage(SettingsService::class.java)
        prefs.clear()
        prefs.flush()
        settingsService = SettingsService()
    }

    @AfterEach
    fun cleanup() {
        prefs.clear()
        prefs.flush()
    }

    private fun makeService(webhookUrl: String = ""): WebhookConfigService {
        if (webhookUrl.isNotBlank()) {
            settingsService.setWebhookUrl(webhookUrl)
        }
        return WebhookConfigService(settingsService)
    }

    // -- isConfigured --

    @Test
    fun `isConfigured returns false when webhook is blank`() {
        assertThat(makeService("").isConfigured()).isFalse()
    }

    @Test
    fun `isConfigured returns true when webhook is set`() {
        assertThat(makeService("https://discord.com/api/webhooks/123/token").isConfigured()).isTrue()
    }

    // -- getMaskedWebhookUrl --

    @Test
    fun `getMaskedWebhookUrl returns empty string when not configured`() {
        assertThat(makeService("").getMaskedWebhookUrl()).isEmpty()
    }

    @Test
    fun `getMaskedWebhookUrl masks middle of URL`() {
        val service = makeService("https://discord.com/api/webhooks/123456789012345678/abcdefghijklmnopqrstuvwxyz1234567890")
        val masked = service.getMaskedWebhookUrl()
        assertThat(masked).contains("****")
        assertThat(masked).startsWith("https://discord.com/api/webhooks/")
    }

    // -- validateUrl --

    @Test
    fun `validateUrl accepts valid discord webhook URL`() {
        makeService().validateUrl("https://discord.com/api/webhooks/123456789/abcdeftoken")
    }

    @Test
    fun `validateUrl rejects blank URL`() {
        assertThatThrownBy { makeService().validateUrl("") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("vazio")
    }

    @Test
    fun `validateUrl rejects URL with wrong prefix`() {
        assertThatThrownBy { makeService().validateUrl("https://example.com/webhook/123/token") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("discord.com/api/webhooks")
    }

    @Test
    fun `validateUrl rejects URL missing token segment`() {
        assertThatThrownBy { makeService().validateUrl("https://discord.com/api/webhooks/123") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `validateUrl rejects URL with empty ID`() {
        assertThatThrownBy { makeService().validateUrl("https://discord.com/api/webhooks//token") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    // -- configure & reset --

    @Test
    fun `configure saves webhook and makes isConfigured true`() {
        val service = makeService()
        service.configure("https://discord.com/api/webhooks/111/mytoken")
        assertThat(service.isConfigured()).isTrue()
        assertThat(service.getWebhookUrl()).isEqualTo("https://discord.com/api/webhooks/111/mytoken")
    }

    @Test
    fun `reset clears webhook and makes isConfigured false`() {
        val service = makeService()
        service.configure("https://discord.com/api/webhooks/111/mytoken")
        service.reset()
        assertThat(service.isConfigured()).isFalse()
        assertThat(service.getWebhookUrl()).isBlank()
    }

    @Test
    fun `configure persists webhook via SettingsService`() {
        val service = makeService()
        service.configure("https://discord.com/api/webhooks/222/persistedtoken")

        // Verify that configure() persisted via SettingsService
        assertThat(settingsService.getWebhookUrl())
            .isEqualTo("https://discord.com/api/webhooks/222/persistedtoken")
    }

    @Test
    fun `network-enabled defaults to false`() {
        val service = makeService()
        assertThat(service.isNetworkEnabled()).isFalse()
    }

    @Test
    fun `setNetworkEnabled persists true`() {
        val service = makeService()
        service.setNetworkEnabled(true)
        assertThat(service.isNetworkEnabled()).isTrue()
    }

    @Test
    fun `setNetworkEnabled can be toggled back to false`() {
        val service = makeService()
        service.setNetworkEnabled(true)
        service.setNetworkEnabled(false)
        assertThat(service.isNetworkEnabled()).isFalse()
    }

    // -- History webhook --

    @Test
    fun `configureHistory saves and retrieves history webhook`() {
        val service = makeService()
        service.configureHistory("https://discord.com/api/webhooks/333/historytoken")
        assertThat(service.isHistoryConfigured()).isTrue()
        assertThat(service.getHistoryWebhookUrl()).isEqualTo("https://discord.com/api/webhooks/333/historytoken")
    }

    @Test
    fun `configureHistory with blank clears history webhook`() {
        val service = makeService()
        service.configureHistory("https://discord.com/api/webhooks/333/historytoken")
        service.configureHistory("")
        assertThat(service.isHistoryConfigured()).isFalse()
    }
}
