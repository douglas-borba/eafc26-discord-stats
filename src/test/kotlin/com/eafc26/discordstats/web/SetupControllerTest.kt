package com.eafc26.discordstats.web

import com.eafc26.discordstats.config.WebhookConfigService
import com.eafc26.discordstats.config.WebhookConfigurationSource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration
import org.springframework.boot.autoconfigure.security.reactive.ReactiveUserDetailsServiceAutoConfiguration
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient

@WebFluxTest(SetupController::class, excludeAutoConfiguration = [ReactiveSecurityAutoConfiguration::class, ReactiveUserDetailsServiceAutoConfiguration::class])
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
        whenever(webhookConfigService.getWebhookUrl()).thenReturn("")
        whenever(webhookConfigService.getMaskedWebhookUrl()).thenReturn("")
        whenever(webhookConfigService.getWebhookSource()).thenReturn(WebhookConfigurationSource.NOT_CONFIGURED)
    }

    // ── GET /setup ──────────────────────────────────────────────────────────

    @Test
    fun `GET setup returns HTML with only the match webhook field`() {
        webClient.get().uri("/setup")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
            .expectBody(String::class.java)
            .value { body ->
                assert(body.contains("Configuração inicial")) { "Expected setup title" }
                assert(body.contains("partidas do clube")) { "Expected match field label" }
                assert(!body.contains("discord-history-webhook-url")) { "History webhook must be absent" }
            }
    }

    @Test
    fun `GET setup HTML never contains a configured webhook token value`() {
        val body = webClient.get().uri("/setup")
            .exchange()
            .expectBody(String::class.java).returnResult().responseBody ?: ""
        assert(!body.contains("abctoken")) { "Must not contain any saved token value" }
    }

    @Test
    fun `setup uses central csrf fetch and webhook URL input`() {
        val body = webClient.get().uri("/setup")
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java)
            .returnResult()
            .responseBody ?: ""

        assert(body.contains("<script src=\"/app-shell.js\"></script>")) {
            "Setup must load the shared fetch before its inline actions"
        }
        assert(body.contains("name=\"discord-match-webhook-url\""))
        assert(!body.contains("name=\"discord-history-webhook-url\""))
        assert(body.contains("type=\"url\"")) { "Webhook field must be a URL input" }
        assert(!body.contains("type=\"password\"")) { "No password inputs should exist" }
    }

    @Test
    fun `environment managed setup continues without posting configuration`() {
        val body = webClient.get().uri("/setup")
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java)
            .returnResult()
            .responseBody ?: ""

        val environmentGuard = body.indexOf("if (webhookState.stats.source === 'ENVIRONMENT')")
        val redirect = body.indexOf("window.location.href = '/';", environmentGuard)
        val mutation = body.indexOf("fetch('/api/setup/webhook', {", environmentGuard)

        assert(environmentGuard >= 0) { "Expected environment-only continuation guard" }
        assert(redirect > environmentGuard) { "Expected direct navigation for environment-managed webhooks" }
        assert(mutation > redirect) { "Environment guard must run before the setup POST" }
    }

    // ── GET /api/setup/webhook ───────────────────────────────────────────────

    @Test
    fun `GET api setup webhook returns only match configuration status`() {
        whenever(webhookConfigService.isConfigured()).thenReturn(true)
        whenever(webhookConfigService.getWebhookSource()).thenReturn(WebhookConfigurationSource.ENVIRONMENT)

        webClient.get().uri("/api/setup/webhook")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.configured").isEqualTo(true)
            .jsonPath("$.source").isEqualTo("ENVIRONMENT")
            .jsonPath("$.url").doesNotExist()
            .jsonPath("$.historyConfigured").doesNotExist()
    }

    // ── POST /api/setup/webhook ──────────────────────────────────────────────

    @Test
    fun `POST setup with valid URL redirects to home and saves it`() {
        val stats = "https://discord.com/api/webhooks/111/statstoken"

        webClient.post().uri("/api/setup/webhook")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("webhookUrl" to stats))
            .exchange()
            .expectStatus().is3xxRedirection
            .expectHeader().location("/")

        verify(webhookConfigService).configure(stats)
    }

    @Test
    fun `POST setup without stats URL returns 400 with statsError`() {
        webClient.post().uri("/api/setup/webhook")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("webhookUrl" to ""))
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.statsError").isNotEmpty
    }

    @Test
    fun `POST setup response never echoes back the webhook URL`() {
        val stats = "https://discord.com/api/webhooks/111/statstoken"

        val body = webClient.post().uri("/api/setup/webhook")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("webhookUrl" to stats))
            .exchange()
            .expectBody(String::class.java).returnResult().responseBody ?: ""

        assert(!body.contains("discord.com/api/webhooks")) {
            "Response must not echo back the webhook URL"
        }
    }

    @Test
    fun `POST setup does not overwrite environment managed webhook`() {
        whenever(webhookConfigService.isConfigured()).thenReturn(true)
        whenever(webhookConfigService.getWebhookSource()).thenReturn(WebhookConfigurationSource.ENVIRONMENT)
        webClient.post().uri("/api/setup/webhook")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("webhookUrl" to ""))
            .exchange()
            .expectStatus().is3xxRedirection

        verify(webhookConfigService, org.mockito.kotlin.never()).configure(any())
    }
}
