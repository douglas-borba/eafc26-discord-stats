package com.eafc26.discordstats.explorer

import com.eafc26.discordstats.config.WebhookConfigService
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.MatchId
import com.eafc26.discordstats.security.SecurityConfig
import com.eafc26.discordstats.store.AdminAuditLogRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.reactive.server.WebTestClient

@WebFluxTest(AdvancedStatsExplorerController::class)
@Import(SecurityConfig::class)
@TestPropertySource(properties = ["app.security.admin-internal-token=test-admin-token"])
class AdvancedStatsExplorerReconciliationSecurityTest {
    @Autowired private lateinit var client: WebTestClient
    @MockBean private lateinit var explorerService: AdvancedStatsExplorerService
    @MockBean private lateinit var webhookConfigService: WebhookConfigService
    @MockBean private lateinit var auditLog: AdminAuditLogRepository

    private val path = "/api/admin/explorer/clubs/club-1/matches/match-1/players/player-1/observations/reconcile"

    @Test
    fun `reconciliation rejects anonymous requests`() {
        client.mutateWith(csrf()).post().uri(path)
            .bodyValue(mapOf("sourcePhrase" to "otima finta", "targetPhrase" to "Ótima finta"))
            .exchange().expectStatus().isUnauthorized
    }

    @Test
    fun `reconciliation requires CSRF even with the internal administrative token`() {
        client.mutate().defaultHeader("Authorization", "Bearer test-admin-token").build()
            .post().uri(path)
            .bodyValue(mapOf("sourcePhrase" to "otima finta", "targetPhrase" to "Ótima finta"))
            .exchange().expectStatus().isForbidden
    }

    @Test
    fun `reconciliation accepts the authenticated administrative BFF with CSRF`() {
        whenever(explorerService.reconcileObservationPhrase(
            ClubId("club-1"), MatchId("match-1"), "player-1", "otima finta", "Ótima finta",
        )).thenReturn(ObservationPhraseReconciliationResult(ObservationPhraseReconciliationStatus.SUCCESS))
        val authenticated = client.mutate().defaultHeader("Authorization", "Bearer test-admin-token").build()

        authenticated.mutateWith(csrf()).post().uri(path)
            .header("X-Admin-Identity", "admin@example.com")
            .bodyValue(mapOf("sourcePhrase" to "otima finta", "targetPhrase" to "Ótima finta"))
            .exchange().expectStatus().isOk.expectBody().jsonPath("$.status").isEqualTo("SUCCESS")

        verify(auditLog).record(
            "admin@example.com",
            "OBSERVATION_RECONCILIATION",
            ClubId("club-1"),
            "match-1",
            "SUCCESS",
            null,
        )
    }
}
