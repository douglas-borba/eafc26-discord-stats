package com.eafc26.discordstats.web

import com.eafc26.discordstats.config.PhraseBank
import com.eafc26.discordstats.config.WebhookConfigService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient

@WebFluxTest(SettingsController::class)
class SettingsControllerTest {

    @Autowired
    private lateinit var webClient: WebTestClient

    @MockBean
    private lateinit var webhookConfigService: WebhookConfigService

    @MockBean
    private lateinit var phraseBank: PhraseBank

    @BeforeEach
    fun setUp() {
        whenever(webhookConfigService.isConfigured()).thenReturn(true)
        whenever(webhookConfigService.isNetworkEnabled()).thenReturn(false)
        whenever(webhookConfigService.logFilePath()).thenReturn("/Users/user/Library/Logs/EAFC26DiscordStats/app.log")
    }

    @Test
    fun `GET settings returns HTML`() {
        webClient.get().uri("/settings")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
            .expectBody(String::class.java)
            .value { body ->
                // Check for title - use regex to handle character encoding variations
                assert(body.contains("Configura") && body.contains("EA FC 26")) { "Expected settings title" }
                // Ensure no actual webhook URL with ID/token is exposed (placeholder is allowed)
                val webhookUrlPattern = Regex("""discord\.com/api/webhooks/\d+/[A-Za-z0-9_-]+""")
                assert(!webhookUrlPattern.containsMatchIn(body)) { "Must not contain actual webhook URL" }
            }
    }

    @Test
    fun `GET api settings info returns configured status without webhook URL`() {
        whenever(webhookConfigService.isConfigured()).thenReturn(true)

        webClient.get().uri("/api/settings/info")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.webhookConfigured").isEqualTo(true)
            .jsonPath("$.networkEnabled").isEqualTo(false)
    }

    @Test
    fun `GET api settings info never includes webhook URL`() {
        val body = webClient.get().uri("/api/settings/info")
            .exchange()
            .expectBody(String::class.java)
            .returnResult()
            .responseBody ?: ""

        assert(!body.contains("discord.com/api/webhooks")) {
            "Settings info must not expose the webhook URL"
        }
        assert(!body.contains("webhookUrl")) {
            "Settings info must not include a webhookUrl field"
        }
    }

    @Test
    fun `GET api settings info includes networkUrl when network enabled`() {
        whenever(webhookConfigService.isNetworkEnabled()).thenReturn(true)

        webClient.get().uri("/api/settings/info")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.networkEnabled").isEqualTo(true)
            .jsonPath("$.networkUrl").exists()
    }

    @Test
    fun `GET api settings info excludes networkUrl when network disabled`() {
        whenever(webhookConfigService.isNetworkEnabled()).thenReturn(false)

        webClient.get().uri("/api/settings/info")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.networkEnabled").isEqualTo(false)
            .jsonPath("$.networkUrl").doesNotExist()
    }

    @Test
    fun `POST reconfigure-webhook resets webhook`() {
        webClient.post().uri("/api/settings/reconfigure-webhook")
            .exchange()
            .expectStatus().is3xxRedirection
            .expectHeader().location("/setup")

        verify(webhookConfigService).reset()
    }

    @Test
    fun `POST network saves enabled=true`() {
        webClient.post().uri("/api/settings/network")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("enabled" to true))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("saved")

        verify(webhookConfigService).setNetworkEnabled(true)
    }

    @Test
    fun `POST network saves enabled=false`() {
        webClient.post().uri("/api/settings/network")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("enabled" to false))
            .exchange()
            .expectStatus().isOk

        verify(webhookConfigService).setNetworkEnabled(false)
    }
}
