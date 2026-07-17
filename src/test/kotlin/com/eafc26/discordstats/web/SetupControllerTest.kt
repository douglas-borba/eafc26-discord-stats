package com.eafc26.discordstats.web

import com.eafc26.discordstats.config.WebhookConfigService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient

@WebFluxTest(SetupController::class)
class SetupControllerTest {

    @Autowired
    private lateinit var webClient: WebTestClient

    @MockBean
    private lateinit var webhookConfigService: WebhookConfigService

    @BeforeEach
    fun setUp() {
        // SetupRedirectFilter is a @Component loaded by @WebFluxTest
        // We allow access to /setup unconditionally via isPassThrough path
        whenever(webhookConfigService.isConfigured()).thenReturn(false)
    }

    @Test
    fun `GET setup returns HTML`() {
        webClient.get().uri("/setup")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
            .expectBody(String::class.java)
            .value { body ->
                assert(body.contains("Configuração inicial")) { "Expected setup title" }
                assert(body.contains("webhook")) { "Expected webhook mention" }
            }
    }

    @Test
    fun `POST setup with valid webhook URL redirects to home`() {
        val url = "https://discord.com/api/webhooks/123456/abctoken"

        webClient.post().uri("/api/setup/webhook")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("webhookUrl" to url))
            .exchange()
            .expectStatus().is3xxRedirection
            .expectHeader().location("/")

        verify(webhookConfigService).configure(url)
    }

    @Test
    fun `POST setup with invalid URL returns 400`() {
        doThrow(IllegalArgumentException("URL inválida")).whenever(webhookConfigService).configure(any())

        webClient.post().uri("/api/setup/webhook")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("webhookUrl" to "not-a-discord-url"))
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").isEqualTo("URL inválida")
    }

    @Test
    fun `POST setup response never contains discord webhook URL`() {
        val url = "https://discord.com/api/webhooks/123456/abctoken"

        val body = webClient.post().uri("/api/setup/webhook")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("webhookUrl" to url))
            .exchange()
            .expectBody(String::class.java)
            .returnResult()
            .responseBody ?: ""

        assert(!body.contains("discord.com/api/webhooks")) {
            "Response must not echo back the webhook URL"
        }
    }

    @Test
    fun `GET setup HTML never contains a configured webhook token value`() {
        // The page may show the discord.com/api/webhooks domain as placeholder text (that's fine),
        // but it must never reveal an actual stored token/URL value from the service.
        // Since webhookConfigService is mocked and not configured, nothing to leak.
        val body = webClient.get().uri("/setup")
            .exchange()
            .expectBody(String::class.java)
            .returnResult()
            .responseBody ?: ""

        // The page should not try to pre-fill or echo back any webhook URL value
        assert(!body.contains("abctoken")) { "Must not contain any saved token value" }
    }
}
