package com.eafc26.discordstats.config

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class WebhookConfigServiceTest {

    @TempDir
    lateinit var tempDir: Path

    private fun makeService(webhookUrl: String = ""): WebhookConfigService {
        val props = AppProperties(discord = DiscordProperties(webhookUrl = webhookUrl))
        return WebhookConfigService(props, configDirOverride = tempDir.resolve("Library/Application Support/EAFC26DiscordStats"))
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
    fun `configure persists webhook to config file`() {
        val service = makeService()
        service.configure("https://discord.com/api/webhooks/222/persistedtoken")

        // Verify that configure() wrote the file
        val configPath = tempDir.resolve("Library/Application Support/EAFC26DiscordStats/config.properties")
        val props = java.util.Properties()
        configPath.toFile().inputStream().use { props.load(it) }
        assertThat(props.getProperty("discord.webhook.url"))
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
}
