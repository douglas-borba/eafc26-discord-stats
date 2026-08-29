package com.eafc26.discordstats.web

import com.eafc26.discordstats.application.club.ClubCatalogResult
import com.eafc26.discordstats.application.club.ClubCatalogService
import com.eafc26.discordstats.application.club.ClubSearchCandidate
import com.eafc26.discordstats.application.club.DefaultClubProvider
import com.eafc26.discordstats.application.club.DiscordWebhookSecretReference
import com.eafc26.discordstats.application.club.EaPlatform
import com.eafc26.discordstats.application.club.MonitoredClub
import com.eafc26.discordstats.application.club.MonitoredClubService
import com.eafc26.discordstats.config.WebhookConfigService
import com.eafc26.discordstats.discord.DiscordWebhookSecretStore
import com.eafc26.discordstats.presentation.editorial.MatchEditorialPresentationRepository
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.ClubName
import com.eafc26.discordstats.scheduler.PollingStatusHolder
import com.eafc26.discordstats.security.SecurityConfig
import com.eafc26.discordstats.service.AcquisitionStateHolder
import com.eafc26.discordstats.service.LatestMatchHolder
import com.eafc26.discordstats.store.EventStatus
import com.eafc26.discordstats.store.OperationalEvent
import com.eafc26.discordstats.store.OperationalEventRepository
import com.eafc26.discordstats.store.PublicationRecord
import com.eafc26.discordstats.store.PublicationState
import com.eafc26.discordstats.store.PublicationStateStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.never
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.context.TestPropertySource
import java.time.Instant

@WebFluxTest(ClubAdministrationController::class)
@Import(SecurityConfig::class)
@TestPropertySource(properties = ["app.security.admin-internal-token=test-admin-token"])
class ClubAdministrationControllerTest {
    @Autowired private lateinit var client: WebTestClient
    @MockBean private lateinit var monitoredClubs: MonitoredClubService
    @MockBean private lateinit var catalog: ClubCatalogService
    @MockBean private lateinit var secretStore: DiscordWebhookSecretStore
    @MockBean private lateinit var pollingStatus: PollingStatusHolder
    @MockBean private lateinit var acquisitionState: AcquisitionStateHolder
    @MockBean private lateinit var latestMatch: LatestMatchHolder
    @MockBean private lateinit var webhookConfigService: WebhookConfigService
    @MockBean private lateinit var defaultClubProvider: DefaultClubProvider
    @MockBean private lateinit var editorialRepository: MatchEditorialPresentationRepository
    @MockBean private lateinit var eventRepository: OperationalEventRepository
    @MockBean private lateinit var publicationStore: PublicationStateStore

    private val association = club("1104972", "Associação BF", enabled = true, reference = "legacy:default")
    private val brasil = club("8874106", "BRASIL 2030", enabled = false)

    @BeforeEach
    fun defaults() {
        client = client.mutate().defaultHeader("Authorization", "Bearer test-admin-token").build()
        whenever(webhookConfigService.isConfigured()).thenReturn(true)
        whenever(defaultClubProvider.get()).thenReturn(
            com.eafc26.discordstats.application.club.DefaultClubConfiguration(
                clubId = association.clubId,
                displayName = association.displayName,
                platform = association.platform,
                webhookSecretReference = association.discordWebhookSecretReference,
            ),
        )
        whenever(monitoredClubs.find(association.clubId)).thenReturn(association)
        whenever(monitoredClubs.find(brasil.clubId)).thenReturn(brasil)
        whenever(secretStore.resolve(association.discordWebhookSecretReference!!)).thenReturn(mock())
    }

    @Test
    fun `search exposes selection fields without raw EA DTOs`() {
        whenever(catalog.search("Associação")).thenReturn(
            ClubCatalogResult.Found(
                listOf(ClubSearchCandidate(association.clubId, association.displayName, association.platform, 2)),
            ),
        )

        client.get().uri { it.path("/api/admin/clubs/search").queryParam("query", "Associação").build() }
            .exchange().expectStatus().isOk
            .expectBody()
            .jsonPath("$[0].clubId").isEqualTo("1104972")
            .jsonPath("$[0].displayName").isEqualTo("Associação BF")
            .jsonPath("$[0].platform").isEqualTo("common-gen5")
            .jsonPath("$[0].currentDivision").isEqualTo(2)
    }

    @Test
    fun `list is deterministic and never exposes webhook material`() {
        whenever(monitoredClubs.list()).thenReturn(listOf(brasil, association))

        val body = client.get().uri("/api/admin/clubs").exchange().expectStatus().isOk
            .expectBody(String::class.java).returnResult().responseBody.orEmpty()

        assert(body.indexOf("1104972") < body.indexOf("8874106"))
        assert(body.contains("\"discordConfigured\":true"))
        assert(!body.contains("legacy:default"))
        assert(!body.contains("webhookUrl"))
    }

    @Test
    fun `individual lookup returns summary and unknown club returns 404`() {
        client.get().uri("/api/admin/clubs/1104972").exchange().expectStatus().isOk
            .expectBody().jsonPath("$.displayName").isEqualTo("Associação BF")
            .jsonPath("$.discordConfigured").isEqualTo(true)

        client.get().uri("/api/admin/clubs/999").exchange().expectStatus().isNotFound
    }

    @Test
    fun `registration accepts selected identity and is delegated idempotently`() {
        whenever(monitoredClubs.register(brasil.clubId, brasil.displayName, brasil.platform, false)).thenReturn(brasil)

        client.mutateWith(csrf()).post().uri("/api/admin/clubs")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("clubId" to "8874106", "displayName" to "BRASIL 2030", "platform" to "common-gen5", "monitoringEnabled" to false))
            .exchange().expectStatus().isOk
            .expectBody().jsonPath("$.clubId").isEqualTo("8874106")

        verify(monitoredClubs).register(brasil.clubId, brasil.displayName, brasil.platform, false)
    }

    @Test
    fun `monitoring changes existing club and absent club is not created`() {
        val enabled = brasil.copy(monitoringEnabled = true)
        whenever(monitoredClubs.setMonitoring(brasil.clubId, true)).thenReturn(enabled)

        client.mutateWith(csrf()).patch().uri("/api/admin/clubs/8874106/monitoring")
            .contentType(MediaType.APPLICATION_JSON).bodyValue(mapOf("enabled" to true))
            .exchange().expectStatus().isOk
            .expectBody().jsonPath("$.monitoringEnabled").isEqualTo(true)

        client.mutateWith(csrf()).patch().uri("/api/admin/clubs/999/monitoring")
            .contentType(MediaType.APPLICATION_JSON).bodyValue(mapOf("enabled" to true))
            .exchange().expectStatus().isNotFound
        verify(monitoredClubs, never()).setMonitoring(ClubId("999"), true)
    }

    @Test
    fun `webhook configuration stores opaque reference and response never exposes secret`() {
        val url = "https://discord.com/api/webhooks/123/token-secret"
        val reference = DiscordWebhookSecretReference("preferences:club:8874106")
        whenever(secretStore.store(brasil.clubId, url)).thenReturn(reference)
        whenever(secretStore.resolve(reference)).thenReturn(mock())
        whenever(monitoredClubs.configureWebhook(brasil.clubId, reference))
            .thenReturn(brasil.copy(discordWebhookSecretReference = reference))

        val body = client.mutateWith(csrf()).put().uri("/api/admin/clubs/8874106/discord")
            .contentType(MediaType.APPLICATION_JSON).bodyValue(mapOf("webhookUrl" to url))
            .exchange().expectStatus().isOk
            .expectBody(String::class.java).returnResult().responseBody.orEmpty()

        verify(secretStore).store(brasil.clubId, url)
        assert(body.contains("\"discordConfigured\":true"))
        assert(!body.contains("token-secret"))
        assert(!body.contains(reference.value))
    }

    @Test
    fun `dangling webhook reference is reported as requiring reconfiguration without exposing it`() {
        val dangling = brasil.copy(discordWebhookSecretReference = DiscordWebhookSecretReference("postgres:club:8874106:missing"))
        whenever(monitoredClubs.find(brasil.clubId)).thenReturn(dangling)
        whenever(secretStore.resolve(dangling.discordWebhookSecretReference!!)).thenReturn(null)

        val body = client.get().uri("/api/admin/clubs/8874106").exchange().expectStatus().isOk
            .expectBody(String::class.java).returnResult().responseBody.orEmpty()

        assert(body.contains("\"discordConfigured\":false"))
        assert(body.contains("\"discordReferencePresent\":true"))
        assert(body.contains("\"discordDestinationResolvable\":false"))
        assert(!body.contains("postgres:club"))
    }

    @Test
    fun `failed replacement verification preserves the existing webhook reference`() {
        val previous = DiscordWebhookSecretReference("postgres:club:8874106:old")
        val fresh = DiscordWebhookSecretReference("postgres:club:8874106:new")
        val configured = brasil.copy(discordWebhookSecretReference = previous)
        whenever(monitoredClubs.find(brasil.clubId)).thenReturn(configured)
        whenever(secretStore.store(brasil.clubId, "https://discord.com/api/webhooks/123/new-token")).thenReturn(fresh)
        whenever(secretStore.resolve(fresh)).thenReturn(null)

        client.mutateWith(csrf()).put().uri("/api/admin/clubs/8874106/discord")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("webhookUrl" to "https://discord.com/api/webhooks/123/new-token"))
            .exchange().expectStatus().is5xxServerError

        verify(monitoredClubs, never()).configureWebhook(brasil.clubId, fresh)
        verify(secretStore).remove(fresh)
        verify(secretStore, never()).remove(previous)
    }

    @Test
    fun `replacement verifies new destination before changing club reference and then removes old secret`() {
        val previous = DiscordWebhookSecretReference("postgres:club:8874106:old")
        val fresh = DiscordWebhookSecretReference("postgres:club:8874106:new")
        val configured = brasil.copy(discordWebhookSecretReference = previous)
        val updated = configured.copy(discordWebhookSecretReference = fresh)
        val url = "https://discord.com/api/webhooks/123/new-token"
        whenever(monitoredClubs.find(brasil.clubId)).thenReturn(configured)
        whenever(secretStore.store(brasil.clubId, url)).thenReturn(fresh)
        whenever(secretStore.resolve(fresh)).thenReturn(mock())
        whenever(monitoredClubs.configureWebhook(brasil.clubId, fresh)).thenReturn(updated)

        client.mutateWith(csrf()).put().uri("/api/admin/clubs/8874106/discord")
            .contentType(MediaType.APPLICATION_JSON).bodyValue(mapOf("webhookUrl" to url))
            .exchange().expectStatus().isOk

        val inOrder = org.mockito.Mockito.inOrder(secretStore, monitoredClubs)
        inOrder.verify(secretStore).store(brasil.clubId, url)
        inOrder.verify(secretStore).resolve(fresh)
        inOrder.verify(monitoredClubs).configureWebhook(brasil.clubId, fresh)
        inOrder.verify(secretStore).remove(previous)
    }

    @Test
    fun `webhook removal clears reference without returning secret`() {
        val reference = DiscordWebhookSecretReference("preferences:club:8874106")
        val configured = brasil.copy(discordWebhookSecretReference = reference)
        whenever(monitoredClubs.find(brasil.clubId)).thenReturn(configured)
        whenever(monitoredClubs.removeWebhook(brasil.clubId)).thenReturn(brasil)

        client.mutateWith(csrf()).delete().uri("/api/admin/clubs/8874106/discord")
            .exchange().expectStatus().isOk
            .expectBody().jsonPath("$.discordConfigured").isEqualTo(false)

        verify(secretStore).remove(reference)
    }

    @Test
    fun `status aggregates existing scoped holders`() {
        whenever(acquisitionState.current(brasil.clubId)).thenReturn(com.eafc26.discordstats.service.AcquisitionState.idle())
        whenever(pollingStatus.current(brasil.clubId)).thenReturn(com.eafc26.discordstats.scheduler.PollingStatus(enabled = false))
        whenever(latestMatch.presentation(brasil.clubId)).thenReturn(null)

        client.get().uri("/api/admin/clubs/8874106/status").exchange().expectStatus().isOk
            .expectBody()
            .jsonPath("$.monitoringEnabled").isEqualTo(false)
            .jsonPath("$.acquisitionStatus").isEqualTo("IDLE")
            .jsonPath("$.pollingStatus").isEqualTo("DISABLED")
            .jsonPath("$.discordConfigured").isEqualTo(false)
            .jsonPath("$.lastError").doesNotExist()
    }

    @Test
    fun `status distinguishes uncertain Discord delivery from confirmed failure and explains warning health`() {
        whenever(acquisitionState.current(association.clubId)).thenReturn(com.eafc26.discordstats.service.AcquisitionState.idle())
        whenever(pollingStatus.current(association.clubId)).thenReturn(
            com.eafc26.discordstats.scheduler.PollingStatus(lastCheck = Instant.parse("2026-08-21T03:02:00Z")),
        )
        whenever(latestMatch.presentation(association.clubId)).thenReturn(null)
        whenever(eventRepository.findByClub(association.clubId, 50)).thenReturn(listOf(
            OperationalEvent(
                clubId = association.clubId,
                matchId = "990976744430293",
                eventType = "DISCORD",
                phase = "UNCERTAIN",
                status = EventStatus.WARNING,
                message = "Origem: aquisição automática; motivo: NETWORK_TIMEOUT; read timed out",
            ),
        ))
        whenever(eventRepository.findLatestByClubAndType(association.clubId, "POLLING")).thenReturn(null)
        whenever(publicationStore.loadRecords(association.clubId)).thenReturn(mapOf(
            "990976744430293" to PublicationRecord(
                matchId = "990976744430293",
                state = PublicationState.DELIVERY_UNCERTAIN,
                updatedAt = 1_724_207_320,
                attemptCount = 1,
                lastAttemptAt = 1_724_207_320,
                lastError = "NETWORK_TIMEOUT: read timed out",
            ),
        ))

        client.get().uri("/api/admin/clubs/1104972/status").exchange().expectStatus().isOk
            .expectBody()
            .jsonPath("$.healthIndicator").isEqualTo("warning")
            .jsonPath("$.healthReason").isEqualTo("Entrega Discord incerta")
            .jsonPath("$.lastDiscordError").doesNotExist()
            .jsonPath("$.lastDiscordUncertain.matchId").isEqualTo("990976744430293")
            .jsonPath("$.lastDiscordUncertain.reason").isEqualTo("NETWORK_TIMEOUT: read timed out")
            .jsonPath("$.lastDiscordUncertain.attemptCount").isEqualTo(1)
    }

    @Test
    fun `status exposes confirmed Discord failure separately from uncertainty`() {
        whenever(acquisitionState.current(association.clubId)).thenReturn(com.eafc26.discordstats.service.AcquisitionState.idle())
        whenever(pollingStatus.current(association.clubId)).thenReturn(
            com.eafc26.discordstats.scheduler.PollingStatus(lastCheck = Instant.parse("2026-08-21T03:02:00Z")),
        )
        whenever(latestMatch.presentation(association.clubId)).thenReturn(null)
        whenever(eventRepository.findByClub(association.clubId, 50)).thenReturn(listOf(
            OperationalEvent(
                clubId = association.clubId,
                matchId = "match-confirmed-failure",
                eventType = "DISCORD",
                phase = "FAILED",
                status = EventStatus.FAILURE,
                message = "Origem: aquisição automática; HTTP 403",
            ),
        ))
        whenever(eventRepository.findLatestByClubAndType(association.clubId, "POLLING")).thenReturn(null)
        whenever(publicationStore.loadRecords(association.clubId)).thenReturn(mapOf(
            "match-confirmed-failure" to PublicationRecord(
                matchId = "match-confirmed-failure",
                state = PublicationState.FAILED_PERMANENT,
                lastError = "HTTP 403: Forbidden",
                lastHttpStatus = 403,
            ),
        ))

        client.get().uri("/api/admin/clubs/1104972/status").exchange().expectStatus().isOk
            .expectBody()
            .jsonPath("$.healthIndicator").isEqualTo("warning")
            .jsonPath("$.healthReason").isEqualTo("Falha permanente no Discord")
            .jsonPath("$.lastDiscordError").isEqualTo("Origem: aquisição automática; HTTP 403")
            .jsonPath("$.lastDiscordFailure.matchId").isEqualTo("match-confirmed-failure")
            .jsonPath("$.lastDiscordFailure.category").isEqualTo("PERMANENT")
            .jsonPath("$.lastDiscordFailure.reason").isEqualTo("HTTP 403: Forbidden")
            .jsonPath("$.lastDiscordUncertain").doesNotExist()
    }

    @Test
    fun `status preserves parked retry evidence when operational events no longer contain the original failure`() {
        whenever(acquisitionState.current(association.clubId)).thenReturn(com.eafc26.discordstats.service.AcquisitionState.idle())
        whenever(pollingStatus.current(association.clubId)).thenReturn(
            com.eafc26.discordstats.scheduler.PollingStatus(lastCheck = Instant.parse("2026-08-21T03:02:00Z")),
        )
        whenever(latestMatch.presentation(association.clubId)).thenReturn(null)
        whenever(eventRepository.findByClub(association.clubId, 50)).thenReturn(emptyList())
        whenever(eventRepository.findLatestByClubAndType(association.clubId, "POLLING")).thenReturn(null)
        whenever(publicationStore.loadRecords(association.clubId)).thenReturn(mapOf(
            "parked-match" to PublicationRecord(
                matchId = "parked-match",
                state = PublicationState.RETRY_EXHAUSTED,
                updatedAt = 1_724_207_320,
                attemptCount = 5,
                lastAttemptAt = 1_724_207_300,
                lastError = "HTTP 503: Service Unavailable",
                lastHttpStatus = 503,
                nextAutomaticAttemptAt = 1_724_209_120,
            ),
        ))

        client.get().uri("/api/admin/clubs/1104972/status").exchange().expectStatus().isOk
            .expectBody()
            .jsonPath("$.healthIndicator").isEqualTo("warning")
            .jsonPath("$.healthReason").isEqualTo("Publicação Discord aguardando recuperação automática")
            .jsonPath("$.lastDiscordError").isEqualTo("HTTP 503: Service Unavailable")
            .jsonPath("$.lastDiscordFailure.matchId").isEqualTo("parked-match")
            .jsonPath("$.lastDiscordFailure.category").isEqualTo("RETRYABLE")
            .jsonPath("$.lastDiscordFailure.attemptCount").isEqualTo(5)
            .jsonPath("$.lastDiscordFailure.httpStatus").isEqualTo(503)
            .jsonPath("$.lastDiscordFailure.nextAutomaticAttemptAt").exists()
    }

    @Test
    fun `status redacts legacy webhook material from persisted publication diagnostics`() {
        whenever(acquisitionState.current(association.clubId)).thenReturn(com.eafc26.discordstats.service.AcquisitionState.idle())
        whenever(pollingStatus.current(association.clubId)).thenReturn(
            com.eafc26.discordstats.scheduler.PollingStatus(lastCheck = Instant.parse("2026-08-21T03:02:00Z")),
        )
        whenever(latestMatch.presentation(association.clubId)).thenReturn(null)
        whenever(eventRepository.findByClub(association.clubId, 50)).thenReturn(emptyList())
        whenever(eventRepository.findLatestByClubAndType(association.clubId, "POLLING")).thenReturn(null)
        whenever(publicationStore.loadRecords(association.clubId)).thenReturn(mapOf(
            "redacted" to PublicationRecord(
                matchId = "redacted",
                state = PublicationState.RETRY_EXHAUSTED,
                lastError = "POST https://discord.com/api/webhooks/123/secret-token failed",
                nextAutomaticAttemptAt = 1_724_209_120,
            ),
        ))

        val body = client.get().uri("/api/admin/clubs/1104972/status").exchange().expectStatus().isOk
            .expectBody(String::class.java).returnResult().responseBody.orEmpty()

        assertThat(body).contains("[Discord webhook]").doesNotContain("secret-token")
    }

    @Test
    fun `club response exposes commercial access status independently from monitoring`() {
        whenever(monitoredClubs.find(brasil.clubId)).thenReturn(
            brasil.copy(accessStatus = com.eafc26.discordstats.application.club.ClubAccessStatus.TRIAL),
        )

        client.get().uri("/api/admin/clubs/8874106").exchange().expectStatus().isOk
            .expectBody()
            .jsonPath("$.accessStatus").isEqualTo("TRIAL")
            .jsonPath("$.monitoringEnabled").isEqualTo(false)
    }

    @Test
    fun `mutations require csrf while valid csrf permits request`() {
        client.post().uri("/api/admin/clubs")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("clubId" to "8874106", "displayName" to "BRASIL 2030", "platform" to "common-gen5"))
            .exchange().expectStatus().isForbidden

        whenever(monitoredClubs.register(brasil.clubId, brasil.displayName, brasil.platform, true)).thenReturn(brasil.copy(monitoringEnabled = true))
        client.mutateWith(csrf()).post().uri("/api/admin/clubs")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("clubId" to "8874106", "displayName" to "BRASIL 2030", "platform" to "common-gen5"))
            .exchange().expectStatus().isOk
    }

    @Test
    fun `delete existing non-default club returns 204`() {
        whenever(monitoredClubs.remove(brasil.clubId)).thenReturn(brasil)

        client.mutateWith(csrf()).delete().uri("/api/admin/clubs/8874106")
            .exchange().expectStatus().isNoContent

        verify(monitoredClubs).remove(brasil.clubId)
    }

    @Test
    fun `delete unknown club returns 404`() {
        client.mutateWith(csrf()).delete().uri("/api/admin/clubs/999")
            .exchange().expectStatus().isNotFound

        verify(monitoredClubs, never()).remove(ClubId("999"))
    }

    @Test
    fun `delete default club returns 409`() {
        client.mutateWith(csrf()).delete().uri("/api/admin/clubs/1104972")
            .exchange().expectStatus().isEqualTo(409)

        verify(monitoredClubs, never()).remove(association.clubId)
    }

    @Test
    fun `delete club with webhook removes secret before deleting club`() {
        val reference = DiscordWebhookSecretReference("preferences:club:8874106")
        val configured = brasil.copy(discordWebhookSecretReference = reference)
        whenever(monitoredClubs.find(brasil.clubId)).thenReturn(configured)
        whenever(monitoredClubs.removeWebhook(brasil.clubId)).thenReturn(brasil)
        whenever(monitoredClubs.remove(brasil.clubId)).thenReturn(brasil)

        client.mutateWith(csrf()).delete().uri("/api/admin/clubs/8874106")
            .exchange().expectStatus().isNoContent

        val inOrder = org.mockito.Mockito.inOrder(monitoredClubs, secretStore)
        inOrder.verify(monitoredClubs).removeWebhook(brasil.clubId)
        inOrder.verify(secretStore).remove(reference)
        inOrder.verify(monitoredClubs).remove(brasil.clubId)
    }

    private fun club(id: String, name: String, enabled: Boolean, reference: String? = null) = MonitoredClub(
        clubId = ClubId(id), displayName = ClubName(name), platform = EaPlatform("common-gen5"),
        monitoringEnabled = enabled, discordWebhookSecretReference = reference?.let(::DiscordWebhookSecretReference),
        createdAt = Instant.parse("2026-08-09T12:00:00Z"), updatedAt = Instant.parse("2026-08-09T12:00:00Z"),
    )
}
