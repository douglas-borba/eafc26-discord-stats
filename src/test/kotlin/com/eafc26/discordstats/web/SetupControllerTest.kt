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
        whenever(webhookConfigService.isHistoryConfigured()).thenReturn(false)
        whenever(webhookConfigService.getWebhookUrl()).thenReturn("")
        whenever(webhookConfigService.getHistoryWebhookUrl()).thenReturn("")
        whenever(webhookConfigService.getMaskedWebhookUrl()).thenReturn("")
    }

    // ── GET /setup ──────────────────────────────────────────────────────────

    @Test
    fun `GET setup returns HTML with both webhook fields`() {
        webClient.get().uri("/setup")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
            .expectBody(String::class.java)
            .value { body ->
                assert(body.contains("Configuração inicial")) { "Expected setup title" }
                assert(body.contains("estatísticas do clube"))  { "Expected stats field label" }
                assert(body.contains("histórico de partidas"))  { "Expected history field label" }
            }
    }

    @Test
    fun `GET setup HTML never contains a configured webhook token value`() {
        val body = webClient.get().uri("/setup")
            .exchange()
            .expectBody(String::class.java).returnResult().responseBody ?: ""
        assert(!body.contains("abctoken")) { "Must not contain any saved token value" }
    }

    // ── GET /api/setup/webhook ───────────────────────────────────────────────

    @Test
    fun `GET api setup webhook returns both configured flags`() {
        whenever(webhookConfigService.isConfigured()).thenReturn(true)
        whenever(webhookConfigService.isHistoryConfigured()).thenReturn(false)
        whenever(webhookConfigService.getWebhookUrl()).thenReturn("https://discord.com/api/webhooks/1/tok")
        whenever(webhookConfigService.getHistoryWebhookUrl()).thenReturn("")

        webClient.get().uri("/api/setup/webhook")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.configured").isEqualTo(true)
            .jsonPath("$.historyConfigured").isEqualTo(false)
            .jsonPath("$.url").isEqualTo("https://discord.com/api/webhooks/1/tok")
            .jsonPath("$.historyUrl").isEqualTo("")
    }

    // ── POST /api/setup/webhook ──────────────────────────────────────────────

    @Test
    fun `POST setup with both valid URLs redirects to home and saves both`() {
        val stats   = "https://discord.com/api/webhooks/111/statstoken"
        val history = "https://discord.com/api/webhooks/222/historytoken"

        webClient.post().uri("/api/setup/webhook")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("webhookUrl" to stats, "historyWebhookUrl" to history))
            .exchange()
            .expectStatus().is3xxRedirection
            .expectHeader().location("/")

        verify(webhookConfigService).configure(stats)
        verify(webhookConfigService).configureHistory(history)
    }

    @Test
    fun `POST setup without stats URL returns 400 with statsError`() {
        webClient.post().uri("/api/setup/webhook")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("webhookUrl" to "", "historyWebhookUrl" to "https://discord.com/api/webhooks/2/tok"))
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.statsError").isNotEmpty
    }

    @Test
    fun `POST setup without history URL returns 400 with historyError`() {
        webClient.post().uri("/api/setup/webhook")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("webhookUrl" to "https://discord.com/api/webhooks/1/tok", "historyWebhookUrl" to ""))
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.historyError").isNotEmpty
    }

    @Test
    fun `POST setup with both URLs missing returns 400 with both field errors`() {
        webClient.post().uri("/api/setup/webhook")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("webhookUrl" to "", "historyWebhookUrl" to ""))
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.statsError").isNotEmpty
            .jsonPath("$.historyError").isNotEmpty
    }

    @Test
    fun `POST setup response never echoes back the webhook URL`() {
        val stats   = "https://discord.com/api/webhooks/111/statstoken"
        val history = "https://discord.com/api/webhooks/222/historytoken"

        val body = webClient.post().uri("/api/setup/webhook")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("webhookUrl" to stats, "historyWebhookUrl" to history))
            .exchange()
            .expectBody(String::class.java).returnResult().responseBody ?: ""

        assert(!body.contains("discord.com/api/webhooks")) {
            "Response must not echo back the webhook URL"
        }
    }
}
