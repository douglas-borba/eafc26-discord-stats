package com.eafc26.discordstats.discord

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

class DiscordWebhookClientTest {

    private lateinit var server: MockWebServer
    private val om = ObjectMapper().registerModule(KotlinModule.Builder().build())

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `blank webhook URL throws IllegalStateException with clear message`() {
        assertThatThrownBy { DiscordDestination("") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("must not be blank")
    }

    @Test
    fun `successful HTTP 204 response does not throw`() {
        server.enqueue(MockResponse().setResponseCode(204))
        val c = makeClient()
        c.send(destination(), emptyPayload())
        assertThat(server.takeRequest().method).isEqualTo("POST")
    }

    @Test
    fun `HTTP 400 response throws DiscordDeliveryException`() {
        server.enqueue(MockResponse().setResponseCode(400).setBody("Bad Request"))
        val c = makeClient()
        assertThatThrownBy { c.send(destination(), emptyPayload()) }
            .isInstanceOf(DiscordDeliveryException::class.java)
            .hasMessageContaining("400")
    }

    @Test
    fun `HTTP 500 response throws DiscordDeliveryException`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))
        val c = makeClient()
        assertThatThrownBy { c.send(destination(), emptyPayload()) }
            .isInstanceOf(DiscordDeliveryException::class.java)
            .hasMessageContaining("500")
    }

    @Test
    fun `request body is valid JSON with Content-Type header`() {
        server.enqueue(MockResponse().setResponseCode(204))
        val c = makeClient()
        c.send(destination(), emptyPayload())
        val req = server.takeRequest()
        assertThat(req.getHeader("Content-Type")).contains("application/json")
        assertThat(req.body.readUtf8()).contains("embeds")
    }

    private fun makeClient(): DiscordWebhookClient {
        return DiscordWebhookClient(om, WebClient.create())
    }

    private fun destination() = DiscordDestination(server.url("/webhook").toString())

    private fun emptyPayload() = DiscordPayload(embeds = listOf(
        DiscordEmbed(title = "Test", color = 0, fields = emptyList())
    ))
}
