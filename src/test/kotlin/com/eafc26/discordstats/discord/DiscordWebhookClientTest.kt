package com.eafc26.discordstats.discord

import com.eafc26.discordstats.config.SettingsService
import com.eafc26.discordstats.config.WebhookConfigService
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient
import java.util.prefs.Preferences

class DiscordWebhookClientTest {

    private lateinit var server: MockWebServer
    private lateinit var prefs: Preferences
    private val om = ObjectMapper().registerModule(KotlinModule.Builder().build())

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        prefs = Preferences.userNodeForPackage(SettingsService::class.java)
        prefs.clear()
        prefs.flush()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
        prefs.clear()
        prefs.flush()
    }

    @Test
    fun `blank webhook URL throws IllegalStateException with clear message`() {
        val c = makeClient(webhookUrl = "")
        assertThatThrownBy { c.send(emptyPayload()) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("not configured")
    }

    @Test
    fun `successful HTTP 204 response does not throw`() {
        server.enqueue(MockResponse().setResponseCode(204))
        val c = makeClient(webhookUrl = server.url("/webhook").toString())
        c.send(emptyPayload())
        assertThat(server.takeRequest().method).isEqualTo("POST")
    }

    @Test
    fun `HTTP 400 response throws DiscordDeliveryException`() {
        server.enqueue(MockResponse().setResponseCode(400).setBody("Bad Request"))
        val c = makeClient(webhookUrl = server.url("/webhook").toString())
        assertThatThrownBy { c.send(emptyPayload()) }
            .isInstanceOf(DiscordDeliveryException::class.java)
            .hasMessageContaining("400")
    }

    @Test
    fun `HTTP 500 response throws DiscordDeliveryException`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))
        val c = makeClient(webhookUrl = server.url("/webhook").toString())
        assertThatThrownBy { c.send(emptyPayload()) }
            .isInstanceOf(DiscordDeliveryException::class.java)
            .hasMessageContaining("500")
    }

    @Test
    fun `request body is valid JSON with Content-Type header`() {
        server.enqueue(MockResponse().setResponseCode(204))
        val c = makeClient(webhookUrl = server.url("/webhook").toString())
        c.send(emptyPayload())
        val req = server.takeRequest()
        assertThat(req.getHeader("Content-Type")).contains("application/json")
        assertThat(req.body.readUtf8()).contains("embeds")
    }

    private fun makeClient(webhookUrl: String): DiscordWebhookClient {
        val settingsService = SettingsService()
        if (webhookUrl.isNotBlank()) {
            settingsService.setWebhookUrl(webhookUrl)
        }
        val service = WebhookConfigService(settingsService)
        return DiscordWebhookClient(service, om, WebClient.create())
    }

    private fun emptyPayload() = DiscordPayload(embeds = listOf(
        DiscordEmbed(title = "Test", color = 0, fields = emptyList())
    ))
}
