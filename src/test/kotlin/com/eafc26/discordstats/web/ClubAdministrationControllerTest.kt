package com.eafc26.discordstats.web

import com.eafc26.discordstats.application.club.ClubCatalogResult
import com.eafc26.discordstats.application.club.ClubCatalogService
import com.eafc26.discordstats.application.club.ClubSearchCandidate
import com.eafc26.discordstats.application.club.DiscordWebhookSecretReference
import com.eafc26.discordstats.application.club.EaPlatform
import com.eafc26.discordstats.application.club.MonitoredClub
import com.eafc26.discordstats.application.club.MonitoredClubService
import com.eafc26.discordstats.config.WebhookConfigService
import com.eafc26.discordstats.discord.DiscordWebhookSecretStore
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.ClubName
import com.eafc26.discordstats.scheduler.PollingStatusHolder
import com.eafc26.discordstats.security.SecurityConfig
import com.eafc26.discordstats.service.AcquisitionStateHolder
import com.eafc26.discordstats.service.LatestMatchHolder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.Instant

@WebFluxTest(ClubAdministrationController::class)
@Import(SecurityConfig::class)
class ClubAdministrationControllerTest {
    @Autowired private lateinit var client: WebTestClient
    @MockBean private lateinit var monitoredClubs: MonitoredClubService
    @MockBean private lateinit var catalog: ClubCatalogService
    @MockBean private lateinit var secretStore: DiscordWebhookSecretStore
    @MockBean private lateinit var pollingStatus: PollingStatusHolder
    @MockBean private lateinit var acquisitionState: AcquisitionStateHolder
    @MockBean private lateinit var latestMatch: LatestMatchHolder
    @MockBean private lateinit var webhookConfigService: WebhookConfigService

    private val association = club("1104972", "Associação BF", enabled = true, reference = "legacy:default")
    private val brasil = club("8874106", "BRASIL 2030", enabled = false)

    @BeforeEach
    fun defaults() {
        whenever(webhookConfigService.isConfigured()).thenReturn(true)
        whenever(monitoredClubs.find(association.clubId)).thenReturn(association)
        whenever(monitoredClubs.find(brasil.clubId)).thenReturn(brasil)
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

    private fun club(id: String, name: String, enabled: Boolean, reference: String? = null) = MonitoredClub(
        clubId = ClubId(id), displayName = ClubName(name), platform = EaPlatform("common-gen5"),
        monitoringEnabled = enabled, discordWebhookSecretReference = reference?.let(::DiscordWebhookSecretReference),
        createdAt = Instant.parse("2026-08-09T12:00:00Z"), updatedAt = Instant.parse("2026-08-09T12:00:00Z"),
    )
}
