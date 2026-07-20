package com.eafc26.discordstats.web

import com.eafc26.discordstats.config.WebhookConfigService
import com.eafc26.discordstats.service.AcquisitionResult
import com.eafc26.discordstats.service.AcquisitionTrigger
import com.eafc26.discordstats.service.MatchAcquisitionService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient

@WebFluxTest(MatchController::class)
class MatchControllerTest {

    @Autowired
    private lateinit var webClient: WebTestClient

    @MockBean
    private lateinit var acquisitionService: MatchAcquisitionService

    @MockBean
    private lateinit var webhookConfigService: WebhookConfigService

    @BeforeEach
    fun setUp() {
        whenever(webhookConfigService.isConfigured()).thenReturn(true)
    }

    // -- GET / --

    @Test
    fun `GET slash returns HTML page`() {
        webClient.get().uri("/")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
            .expectBody(String::class.java)
            .value { body ->
                assert(body.contains("EA FC 26")) { "Expected title in HTML" }
                assert(body.contains("Atualizar agora")) { "Expected button label in HTML" }
            }
    }

    // -- POST /api/matches/notify-latest --

    @Test
    fun `notify-latest returns sent with summary`() {
        whenever(acquisitionService.acquire(AcquisitionTrigger.MANUAL))
            .thenReturn(AcquisitionResult.Processed(
                published = listOf(AcquisitionResult.MatchSummary("m1", "Test FC 2 × 0 Opp")),
                alreadyPublished = emptyList(),
                failed = emptyList(),
            ))

        webClient.post().uri("/api/matches/notify-latest")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("sent")
            .jsonPath("$.summary").isEqualTo("Test FC 2 × 0 Opp")
    }

    @Test
    fun `notify-latest returns already_published with summary`() {
        whenever(acquisitionService.acquire(AcquisitionTrigger.MANUAL))
            .thenReturn(AcquisitionResult.Processed(
                published = emptyList(),
                alreadyPublished = listOf(AcquisitionResult.MatchSummary("m1", "Test FC 2 × 0 Opp")),
                failed = emptyList(),
            ))

        webClient.post().uri("/api/matches/notify-latest")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("already_published")
            .jsonPath("$.summary").isEqualTo("Test FC 2 × 0 Opp")
    }

    @Test
    fun `notify-latest returns no_matches`() {
        whenever(acquisitionService.acquire(AcquisitionTrigger.MANUAL))
            .thenReturn(AcquisitionResult.NoMatches)

        webClient.post().uri("/api/matches/notify-latest")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("no_matches")
            .jsonPath("$.summary").doesNotExist()
    }

    @Test
    fun `notify-latest returns ea_unavailable as 502`() {
        whenever(acquisitionService.acquire(AcquisitionTrigger.MANUAL))
            .thenReturn(AcquisitionResult.EaUnavailable(503, "Service unavailable"))

        webClient.post().uri("/api/matches/notify-latest")
            .exchange()
            .expectStatus().isEqualTo(502)
            .expectBody()
            .jsonPath("$.status").isEqualTo("ea_unavailable")
    }

    @Test
    fun `notify-latest returns discord_error as 502`() {
        whenever(acquisitionService.acquire(AcquisitionTrigger.MANUAL))
            .thenReturn(AcquisitionResult.Processed(
                published = emptyList(),
                alreadyPublished = emptyList(),
                failed = listOf(AcquisitionResult.MatchFailure("m1", "Test FC 2 × 0 Opp", "Rate limited")),
            ))

        webClient.post().uri("/api/matches/notify-latest")
            .exchange()
            .expectStatus().isEqualTo(502)
            .expectBody()
            .jsonPath("$.status").isEqualTo("discord_error")
    }

    @Test
    fun `notify-latest returns busy as 409`() {
        whenever(acquisitionService.acquire(AcquisitionTrigger.MANUAL))
            .thenReturn(AcquisitionResult.Busy)

        webClient.post().uri("/api/matches/notify-latest")
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.status").isEqualTo("busy")
    }

    @Test
    fun `response has no summary field for no_matches`() {
        whenever(acquisitionService.acquire(AcquisitionTrigger.MANUAL))
            .thenReturn(AcquisitionResult.NoMatches)

        webClient.post().uri("/api/matches/notify-latest")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.summary").doesNotExist()
    }

    @Test
    fun `response has no summary field for ea_unavailable`() {
        whenever(acquisitionService.acquire(AcquisitionTrigger.MANUAL))
            .thenReturn(AcquisitionResult.EaUnavailable(503, "Service unavailable"))

        webClient.post().uri("/api/matches/notify-latest")
            .exchange()
            .expectStatus().isEqualTo(502)
            .expectBody()
            .jsonPath("$.summary").doesNotExist()
    }

    @Test
    fun `response has no summary field for discord_error`() {
        whenever(acquisitionService.acquire(AcquisitionTrigger.MANUAL))
            .thenReturn(AcquisitionResult.Processed(
                published = emptyList(),
                alreadyPublished = emptyList(),
                failed = listOf(AcquisitionResult.MatchFailure("m1", "Summary", "Error")),
            ))

        webClient.post().uri("/api/matches/notify-latest")
            .exchange()
            .expectStatus().isEqualTo(502)
            .expectBody()
            .jsonPath("$.summary").doesNotExist()
    }

    // -- POST /api/matches/resend-latest --

    @Test
    fun `resend-latest returns force_sent with summary`() {
        whenever(acquisitionService.acquire(AcquisitionTrigger.FORCE_RESEND))
            .thenReturn(AcquisitionResult.ForceResent(
                AcquisitionResult.MatchSummary("m1", "Test FC 2 × 0 Opp")
            ))

        webClient.post().uri("/api/matches/resend-latest")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("force_sent")
            .jsonPath("$.summary").isEqualTo("Test FC 2 × 0 Opp")
            .jsonPath("$.message").isEqualTo("Partida reenviada com sucesso.")
    }

    @Test
    fun `resend-latest returns no_matches`() {
        whenever(acquisitionService.acquire(AcquisitionTrigger.FORCE_RESEND))
            .thenReturn(AcquisitionResult.NoMatches)

        webClient.post().uri("/api/matches/resend-latest")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("no_matches")
    }

    @Test
    fun `resend-latest returns ea_unavailable as 502`() {
        whenever(acquisitionService.acquire(AcquisitionTrigger.FORCE_RESEND))
            .thenReturn(AcquisitionResult.EaUnavailable(503, "Service unavailable"))

        webClient.post().uri("/api/matches/resend-latest")
            .exchange()
            .expectStatus().isEqualTo(502)
            .expectBody()
            .jsonPath("$.status").isEqualTo("ea_unavailable")
    }

    @Test
    fun `resend-latest returns discord_error as 502`() {
        whenever(acquisitionService.acquire(AcquisitionTrigger.FORCE_RESEND))
            .thenReturn(AcquisitionResult.Processed(
                published = emptyList(),
                alreadyPublished = emptyList(),
                failed = listOf(AcquisitionResult.MatchFailure("m1", "Summary", "Discord error")),
            ))

        webClient.post().uri("/api/matches/resend-latest")
            .exchange()
            .expectStatus().isEqualTo(502)
            .expectBody()
            .jsonPath("$.status").isEqualTo("discord_error")
    }

    @Test
    fun `resend-latest returns busy as 409`() {
        whenever(acquisitionService.acquire(AcquisitionTrigger.FORCE_RESEND))
            .thenReturn(AcquisitionResult.Busy)

        webClient.post().uri("/api/matches/resend-latest")
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.status").isEqualTo("busy")
    }

    // -- Webhook not configured --

    @Test
    fun `notify-latest returns webhook_not_configured as 502`() {
        whenever(acquisitionService.acquire(AcquisitionTrigger.MANUAL))
            .thenReturn(AcquisitionResult.WebhookNotConfigured)

        webClient.post().uri("/api/matches/notify-latest")
            .exchange()
            .expectStatus().isEqualTo(502)
            .expectBody()
            .jsonPath("$.status").isEqualTo("webhook_not_configured")
    }

    // -- Persistence error --

    @Test
    fun `notify-latest returns sent_persistence_error when persistence fails`() {
        whenever(acquisitionService.acquire(AcquisitionTrigger.MANUAL))
            .thenReturn(AcquisitionResult.Processed(
                published = listOf(AcquisitionResult.MatchSummary("m1", "Test FC 2 × 0 Opp", persistedSuccessfully = false)),
                alreadyPublished = emptyList(),
                failed = emptyList(),
            ))

        webClient.post().uri("/api/matches/notify-latest")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("sent_persistence_error")
            .jsonPath("$.summary").isEqualTo("Test FC 2 × 0 Opp")
    }

    // -- Baseline established --

    @Test
    fun `notify-latest returns baseline_established when first run`() {
        whenever(acquisitionService.acquire(AcquisitionTrigger.MANUAL))
            .thenReturn(AcquisitionResult.Processed(
                published = emptyList(),
                alreadyPublished = emptyList(),
                failed = emptyList(),
                baselineEstablished = true,
            ))

        webClient.post().uri("/api/matches/notify-latest")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("baseline_established")
    }
}
