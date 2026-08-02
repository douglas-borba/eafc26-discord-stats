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

    private fun makeService(
        webhookUrl: String = "",
        environmentMatch: String = "",
    ): WebhookConfigService {
        if (webhookUrl.isNotBlank()) {
            settingsService.setWebhookUrl(webhookUrl)
        }
        return WebhookConfigService(
            settingsService,
            AppProperties(
                discord = DiscordProperties(
                    matchWebhookUrl = environmentMatch,
                )
            ),
        )
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
    fun `getMaskedWebhookUrl identifies local configuration without revealing URL`() {
        val service = makeService("https://discord.com/api/webhooks/123456789012345678/abcdefghijklmnopqrstuvwxyz1234567890")
        val masked = service.getMaskedWebhookUrl()
        assertThat(masked).isEqualTo("Configurado localmente")
        assertThat(masked).doesNotContain("123456789012345678")
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

    @Test
    fun `environment match webhook takes precedence over stored value`() {
        val stored = "https://discord.com/api/webhooks/111/storedtoken"
        val environment = "https://discord.com/api/webhooks/222/environmenttoken"
        val service = makeService(webhookUrl = stored, environmentMatch = environment)

        assertThat(service.getWebhookUrl()).isEqualTo(environment)
        assertThat(service.getWebhookSource()).isEqualTo(WebhookConfigurationSource.ENVIRONMENT)
    }

    @Test
    fun `blank environment value falls back to stored value`() {
        val stored = "https://discord.com/api/webhooks/111/storedtoken"
        val service = makeService(webhookUrl = stored, environmentMatch = "   ")

        assertThat(service.getWebhookUrl()).isEqualTo(stored)
        assertThat(service.getWebhookSource()).isEqualTo(WebhookConfigurationSource.STORED)
    }

    @Test
    fun `no environment or stored value is not configured`() {
        val service = makeService()

        assertThat(service.getWebhookSource()).isEqualTo(WebhookConfigurationSource.NOT_CONFIGURED)
    }

    @Test
    fun `environment managed webhook cannot be overwritten locally`() {
        val service = makeService(
            environmentMatch = "https://discord.com/api/webhooks/222/environmenttoken",
        )

        assertThatThrownBy {
            service.configure("https://discord.com/api/webhooks/111/storedtoken")
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessage(WebhookConfigService.ENV_MATCH + " is controlled by the environment.")
    }

    @Test
    fun `invalid environment value fails without exposing secret`() {
        val secret = "plain-http-secret-token"

        assertThatThrownBy {
            makeService(environmentMatch = "http://discord.com/api/webhooks/123/$secret")
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessage(WebhookConfigService.ENV_MATCH + " is invalid.")
            .message().doesNotContain(secret)
    }

    @Test
    fun `only HTTPS webhook URLs are accepted`() {
        assertThatThrownBy {
            makeService().validateUrl("http://discord.com/api/webhooks/123/token")
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("HTTPS")
    }

    @Test
    fun `invalid stored webhook is cleared and never resolved`() {
        settingsService.setWebhookUrl("http://discord.com/api/webhooks/123/legacy-token")

        val service = WebhookConfigService(settingsService, AppProperties())

        assertThat(service.getWebhookSource()).isEqualTo(WebhookConfigurationSource.NOT_CONFIGURED)
        assertThat(settingsService.getWebhookUrl()).isBlank()
    }
}
