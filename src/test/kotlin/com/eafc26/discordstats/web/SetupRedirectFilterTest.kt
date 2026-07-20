package com.eafc26.discordstats.web

import com.eafc26.discordstats.config.WebhookConfigService
import com.eafc26.discordstats.service.MatchAcquisitionService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.web.reactive.server.WebTestClient

@WebFluxTest(MatchController::class)
class SetupRedirectFilterTest {

    @Autowired
    private lateinit var webClient: WebTestClient

    @MockBean
    private lateinit var acquisitionService: MatchAcquisitionService

    @MockBean
    private lateinit var webhookConfigService: WebhookConfigService

    @Test
    fun `unconfigured webhook redirects to setup`() {
        whenever(webhookConfigService.isConfigured()).thenReturn(false)

        webClient.get().uri("/")
            .exchange()
            .expectStatus().is3xxRedirection
            .expectHeader().location("/setup")
    }

    @Test
    fun `configured webhook allows through to home`() {
        whenever(webhookConfigService.isConfigured()).thenReturn(true)

        webClient.get().uri("/")
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `setup path is always allowed regardless of config state`() {
        whenever(webhookConfigService.isConfigured()).thenReturn(false)

        // SetupController is not in this @WebFluxTest context so we get 404 rather than redirect
        webClient.get().uri("/setup")
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `api setup path is always allowed regardless of config state`() {
        whenever(webhookConfigService.isConfigured()).thenReturn(false)

        webClient.post().uri("/api/setup/webhook")
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `api health path is always allowed regardless of config state`() {
        whenever(webhookConfigService.isConfigured()).thenReturn(false)

        // /api/health is handled by MatchController (loaded in this context) — returns 200, not redirected
        webClient.get().uri("/api/health")
            .exchange()
            .expectStatus().isOk
    }
}
