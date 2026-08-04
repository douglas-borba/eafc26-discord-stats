package com.eafc26.discordstats.web

import com.eafc26.discordstats.config.WebhookConfigService
import com.eafc26.discordstats.service.MatchAcquisitionService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration
import org.springframework.boot.autoconfigure.security.reactive.ReactiveUserDetailsServiceAutoConfiguration
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.web.reactive.server.WebTestClient

@WebFluxTest(MatchController::class, excludeAutoConfiguration = [ReactiveSecurityAutoConfiguration::class, ReactiveUserDetailsServiceAutoConfiguration::class])
class SetupRedirectFilterTest {

    @Autowired
    private lateinit var webClient: WebTestClient

    @MockBean
    private lateinit var acquisitionService: MatchAcquisitionService

    @MockBean
    private lateinit var webhookConfigService: WebhookConfigService

    @Test
    fun `webhook not configured redirects to setup`() {
        whenever(webhookConfigService.isConfigured()).thenReturn(false)

        webClient.get().uri("/")
            .exchange()
            .expectStatus().is3xxRedirection
            .expectHeader().location("/setup")
    }

    @Test
    fun `match webhook configured allows through to home`() {
        whenever(webhookConfigService.isConfigured()).thenReturn(true)

        webClient.get().uri("/")
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `setup path is always allowed regardless of config state`() {
        whenever(webhookConfigService.isConfigured()).thenReturn(false)

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

        webClient.get().uri("/api/health")
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `static resources are never redirected to setup`() {
        whenever(webhookConfigService.isConfigured()).thenReturn(false)

        listOf("/app-shell.css", "/app-shell.js", "/images/club.png", "/favicon.ico").forEach { path ->
            webClient.get().uri(path).exchange().expectStatus().value { status ->
                assertThat(status in 300..399).describedAs(path).isFalse()
            }
        }
    }
}
