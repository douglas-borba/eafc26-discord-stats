package com.eafc26.discordstats.web

import com.eafc26.discordstats.application.club.MonitoredClubRepository
import com.eafc26.discordstats.application.repository.CanonicalMatchRepository
import com.eafc26.discordstats.canonical.CanonicalMatch
import com.eafc26.discordstats.canonical.CanonicalSchemaVersion
import com.eafc26.discordstats.canonical.EngineVersion
import com.eafc26.discordstats.config.WebhookConfigService
import com.eafc26.discordstats.domain.interpretation.MatchInterpretation
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.FootballMatch
import com.eafc26.discordstats.domain.match.MatchId
import com.eafc26.discordstats.domain.story.MatchStories
import com.eafc26.discordstats.security.SecurityConfig
import com.eafc26.discordstats.service.AutoPublishResult
import com.eafc26.discordstats.service.DiscordMatchPublicationService
import com.eafc26.discordstats.service.DiscordPublicationResult
import com.eafc26.discordstats.service.MatchPublicationInspection
import com.eafc26.discordstats.service.PublicationOutcome
import com.eafc26.discordstats.service.PublicationReconciliationService
import com.eafc26.discordstats.service.ReconciliationReport
import com.eafc26.discordstats.service.ReconciliationSummary
import com.eafc26.discordstats.store.PublishedMatchStore
import com.eafc26.discordstats.store.AdminAuditLogRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.Instant

@WebFluxTest(PublicationAdminController::class)
@Import(SecurityConfig::class)
@TestPropertySource(properties = ["app.security.admin-internal-token=test-admin-token"])
class PublicationAdminControllerTest {
    @Autowired private lateinit var client: WebTestClient
    @MockBean private lateinit var store: PublishedMatchStore
    @MockBean private lateinit var canonicalMatchRepository: CanonicalMatchRepository
    @MockBean private lateinit var publicationService: DiscordMatchPublicationService
    @MockBean private lateinit var reconciliationService: PublicationReconciliationService
    @MockBean private lateinit var monitoredClubRepository: MonitoredClubRepository
    @MockBean private lateinit var webhookConfigService: WebhookConfigService
    @MockBean private lateinit var auditLog: AdminAuditLogRepository

    private val clubA = "1104972"
    private val matchId = "match-001"
    private val fixedInstant = Instant.parse("2025-01-15T20:00:00Z")

    private fun anyClubId(): ClubId {
        anyString()
        return ClubId("stub")
    }

    private fun anyMatchId(): MatchId {
        anyString()
        return MatchId("stub")
    }

    @BeforeEach
    fun defaults() {
        client = client.mutate().defaultHeader("Authorization", "Bearer test-admin-token").build()
        whenever(webhookConfigService.isConfigured()).thenReturn(true)
        Mockito.doReturn(true).`when`(monitoredClubRepository).existsById(anyClubId())
    }

    private fun canonical(matchId: String, clubId: String): CanonicalMatch {
        val footballMatch = mock<FootballMatch>()
        val interpretation = mock<MatchInterpretation>()
        val stories = mock<MatchStories>()
        whenever(footballMatch.id).thenReturn(MatchId(matchId))
        whenever(footballMatch.playedAt).thenReturn(fixedInstant)
        whenever(interpretation.matchId).thenReturn(MatchId(matchId))
        whenever(interpretation.perspectiveClubId).thenReturn(ClubId(clubId))
        whenever(stories.matchId).thenReturn(MatchId(matchId))
        return CanonicalMatch(
            schemaVersion = CanonicalSchemaVersion(1),
            engineVersion = EngineVersion("1.0.0"),
            generatedAt = fixedInstant,
            footballMatch = footballMatch,
            interpretation = interpretation,
            stories = stories,
        )
    }

    @Test
    fun `resolve-delivered scopes to the requested club`() {
        client.mutateWith(csrf())
            .post().uri("/api/admin/clubs/$clubA/publication/$matchId/resolve-delivered")
            .exchange().expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("resolved")

        verify(store).resolveAsDelivered(anyClubId(), anyString())
    }

    @Test
    fun `resolve-delivered rejects unknown club with 404`() {
        Mockito.doReturn(false).`when`(monitoredClubRepository).existsById(anyClubId())

        client.mutateWith(csrf())
            .post().uri("/api/admin/clubs/unknown/publication/$matchId/resolve-delivered")
            .exchange().expectStatus().isNotFound

        verify(store, never()).resolveAsDelivered(anyClubId(), anyString())
    }

    @Test
    fun `force-publish scopes to the requested club`() {
        val match = canonical(matchId, clubA)
        Mockito.doReturn(match).`when`(canonicalMatchRepository).findById(anyClubId(), anyMatchId())
        Mockito.doReturn(DiscordPublicationResult(PublicationOutcome.PUBLISHED, matchId))
            .`when`(publicationService).forcePublish(anyClubId(), org.mockito.kotlin.any())

        client.mutateWith(csrf())
            .post().uri("/api/admin/clubs/$clubA/publication/$matchId/force-publish")
            .exchange().expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("success")
            .jsonPath("$.outcome").isEqualTo("PUBLISHED")

        val audit = Mockito.inOrder(auditLog)
        audit.verify(auditLog).record("nextjs-admin-bff", "FORCE_PUBLISH", ClubId(clubA), matchId, "START", null)
        audit.verify(auditLog).record("nextjs-admin-bff", "FORCE_PUBLISH", ClubId(clubA), matchId, "SUCCESS", null)
    }

    @Test
    fun `force-publish returns 404 for nonexistent match`() {
        Mockito.doReturn(null).`when`(canonicalMatchRepository).findById(anyClubId(), anyMatchId())

        client.mutateWith(csrf())
            .post().uri("/api/admin/clubs/$clubA/publication/nonexistent/force-publish")
            .exchange().expectStatus().isNotFound
    }

    @Test
    fun `force-publish does not begin delivery when durable audit is unavailable`() {
        Mockito.doThrow(IllegalStateException("database unavailable"))
            .`when`(auditLog).record("nextjs-admin-bff", "FORCE_PUBLISH", ClubId(clubA), matchId, "START", null)

        client.mutateWith(csrf())
            .post().uri("/api/admin/clubs/$clubA/publication/$matchId/force-publish")
            .exchange().expectStatus().isEqualTo(503)

        verify(publicationService, never()).forcePublish(anyClubId(), org.mockito.kotlin.any())
    }

    @Test
    fun `force-publish records failure when delivery throws`() {
        val match = canonical(matchId, clubA)
        Mockito.doReturn(match).`when`(canonicalMatchRepository).findById(anyClubId(), anyMatchId())
        Mockito.doThrow(IllegalStateException("Discord unavailable"))
            .`when`(publicationService).forcePublish(anyClubId(), org.mockito.kotlin.any())

        client.mutateWith(csrf())
            .post().uri("/api/admin/clubs/$clubA/publication/$matchId/force-publish")
            .exchange().expectStatus().is5xxServerError

        val audit = Mockito.inOrder(auditLog)
        audit.verify(auditLog).record("nextjs-admin-bff", "FORCE_PUBLISH", ClubId(clubA), matchId, "START", null)
        audit.verify(auditLog).record("nextjs-admin-bff", "FORCE_PUBLISH", ClubId(clubA), matchId, "FAILURE", "IllegalStateException")
    }

    @Test
    fun `force-publish rejects unknown club`() {
        Mockito.doReturn(false).`when`(monitoredClubRepository).existsById(anyClubId())

        client.mutateWith(csrf())
            .post().uri("/api/admin/clubs/unknown/publication/$matchId/force-publish")
            .exchange().expectStatus().isNotFound

        verify(publicationService, never()).forcePublish(anyClubId(), org.mockito.kotlin.any())
    }

    @Test
    fun `inspect scopes reconciliation to the requested club`() {
        val report = ReconciliationReport(
            inspections = listOf(
                MatchPublicationInspection(
                    matchId = matchId,
                    matchDate = fixedInstant,
                    ourScore = 3,
                    oppScore = 1,
                    publicationState = null,
                    attemptCount = 0,
                    lastAttemptAt = null,
                    lastError = null,
                    lastHttpStatus = null,
                    safeToAutoPublish = true,
                ),
            ),
            summary = ReconciliationSummary(
                totalInspected = 1,
                delivered = 0,
                neverAttempted = 1,
                delivering = 0,
                uncertain = 0,
                failedPermanent = 0,
                failedTransient = 0,
                baselined = 0,
            ),
        )
        Mockito.doReturn(report).`when`(reconciliationService).inspectLatestPublications(anyClubId(), anyInt())

        client.get().uri("/api/admin/clubs/$clubA/publication/reconcile/inspect")
            .exchange().expectStatus().isOk
            .expectBody()
            .jsonPath("$.inspections[0].matchId").isEqualTo(matchId)
            .jsonPath("$.inspections[0].score").isEqualTo("3 × 1")
            .jsonPath("$.summary.neverAttempted").isEqualTo(1)
            .jsonPath("$.summary.baselined").isEqualTo(0)
    }

    @Test
    fun `auto-publish scopes to the requested club`() {
        Mockito.doReturn(AutoPublishResult(publishedCount = 1, skippedCount = 0, errorCount = 0, published = listOf(matchId), errors = emptyList()))
            .`when`(reconciliationService).autoPublishSafe(anyClubId())

        client.mutateWith(csrf())
            .post().uri("/api/admin/clubs/$clubA/publication/reconcile/auto-publish")
            .exchange().expectStatus().isOk
            .expectBody()
            .jsonPath("$.publishedCount").isEqualTo(1)
            .jsonPath("$.published[0]").isEqualTo(matchId)
    }

    @Test
    fun `unauthenticated request is rejected`() {
        val unauthClient = client.mutate().defaultHeaders { it.remove("Authorization") }.build()

        unauthClient.mutateWith(csrf())
            .post().uri("/api/admin/clubs/$clubA/publication/$matchId/force-publish")
            .exchange().expectStatus().isUnauthorized
    }
}
