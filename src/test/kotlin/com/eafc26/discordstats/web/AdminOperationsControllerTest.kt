package com.eafc26.discordstats.web

import com.eafc26.discordstats.application.club.MonitoredClubRepository
import com.eafc26.discordstats.application.club.TrialService
import com.eafc26.discordstats.config.WebhookConfigService
import com.eafc26.discordstats.discord.DiscordDestination
import com.eafc26.discordstats.discord.DiscordDestinationResolver
import com.eafc26.discordstats.discord.DiscordDeliveryException
import com.eafc26.discordstats.discord.DiscordWebhookClient
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.ea.EaApiResult
import com.eafc26.discordstats.ea.WindowedEaClubsGateway
import com.eafc26.discordstats.ea.model.MatchResponse
import com.eafc26.discordstats.security.SecurityConfig
import com.eafc26.discordstats.service.AcquisitionResult
import com.eafc26.discordstats.service.AcquisitionTrigger
import com.eafc26.discordstats.service.MatchAcquisitionService
import com.eafc26.discordstats.service.OperationalEventRecorder
import com.eafc26.discordstats.store.AdminAuditLogRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.function.client.WebClientResponseException

@WebFluxTest(AdminOperationsController::class)
@Import(SecurityConfig::class)
@TestPropertySource(properties = ["app.security.admin-internal-token=test-admin-token"])
class AdminOperationsControllerTest {
    @Autowired private lateinit var client: WebTestClient
    @MockBean private lateinit var clubs: MonitoredClubRepository
    @MockBean private lateinit var acquisition: MatchAcquisitionService
    @MockBean @Qualifier("production") private lateinit var gateway: WindowedEaClubsGateway
    @MockBean private lateinit var resolver: DiscordDestinationResolver
    @MockBean private lateinit var discord: DiscordWebhookClient
    @MockBean private lateinit var webhookConfigService: WebhookConfigService
    @MockBean private lateinit var eventRecorder: OperationalEventRecorder
    @MockBean private lateinit var auditLog: AdminAuditLogRepository
    @MockBean private lateinit var trials: TrialService

    private val clubId = "1104972"
    @BeforeEach fun setup() {
        client = client.mutate().defaultHeader("Authorization", "Bearer test-admin-token").build()
        whenever(clubs.existsById(ClubId(clubId))).thenReturn(true)
    }

    @Test fun `poll scopes scheduler acquisition to the requested club`() {
        whenever(acquisition.acquire(ClubId(clubId), AcquisitionTrigger.ADMIN_POLL)).thenReturn(AcquisitionResult.NoMatches)

        client.mutateWith(csrf()).post().uri("/api/admin/clubs/$clubId/poll").exchange()
            .expectStatus().isOk.expectBody().jsonPath("$.status").isEqualTo("success")

        verify(acquisition).acquire(ClubId(clubId), AcquisitionTrigger.ADMIN_POLL)
        val audit = Mockito.inOrder(auditLog)
        audit.verify(auditLog).record("nextjs-admin-bff", "ADMIN_POLL", ClubId(clubId), null, "START", null)
        audit.verify(auditLog).record("nextjs-admin-bff", "ADMIN_POLL", ClubId(clubId), null, "SUCCESS", null)
    }

    @Test fun `poll returns EA failure without calling discord directly`() {
        whenever(acquisition.acquire(ClubId(clubId), AcquisitionTrigger.ADMIN_POLL)).thenReturn(AcquisitionResult.EaUnavailable(503, "down"))

        client.mutateWith(csrf()).post().uri("/api/admin/clubs/$clubId/poll").exchange()
            .expectStatus().isOk.expectBody().jsonPath("$.status").isEqualTo("failed")

        verify(discord, never()).send(any(), any())
        val audit = Mockito.inOrder(auditLog)
        audit.verify(auditLog).record("nextjs-admin-bff", "ADMIN_POLL", ClubId(clubId), null, "START", null)
        audit.verify(auditLog).record("nextjs-admin-bff", "ADMIN_POLL", ClubId(clubId), null, "FAILURE", "503")
    }

    @Test fun `poll rejects an unknown club without acquisition`() {
        whenever(clubs.existsById(ClubId("missing"))).thenReturn(false)

        client.mutateWith(csrf()).post().uri("/api/admin/clubs/missing/poll").exchange()
            .expectStatus().isNotFound

        verifyNoInteractions(acquisition)
    }

    @Test fun `poll is blocked for a trial snapshot`() {
        whenever(trials.isTrial(ClubId(clubId))).thenReturn(true)

        client.mutateWith(csrf()).post().uri("/api/admin/clubs/$clubId/poll").exchange()
            .expectStatus().isForbidden.expectBody().jsonPath("$.status").isEqualTo("trial_snapshot")

        verifyNoInteractions(acquisition)
    }

    @Test fun `operational actions require administrative authentication`() {
        val anonymous = client.mutate().defaultHeaders { it.remove("Authorization") }.build()

        anonymous.mutateWith(csrf()).post().uri("/api/admin/clubs/$clubId/poll").exchange()
            .expectStatus().isUnauthorized

        verifyNoInteractions(acquisition)
    }

    @Test fun `poll records the identity received from the authenticated BFF`() {
        whenever(acquisition.acquire(ClubId(clubId), AcquisitionTrigger.ADMIN_POLL)).thenReturn(AcquisitionResult.NoMatches)

        client.mutateWith(csrf()).post().uri("/api/admin/clubs/$clubId/poll")
            .header("X-Admin-Identity", "admin@example.com").exchange().expectStatus().isOk

        verify(auditLog).record("admin@example.com", "ADMIN_POLL", ClubId(clubId), null, "START", null)
    }

    @Test fun `poll does not start acquisition when durable audit cannot be recorded`() {
        Mockito.doThrow(IllegalStateException("database unavailable"))
            .`when`(auditLog).record("nextjs-admin-bff", "ADMIN_POLL", ClubId(clubId), null, "START", null)

        client.mutateWith(csrf()).post().uri("/api/admin/clubs/$clubId/poll").exchange()
            .expectStatus().isEqualTo(503)

        verifyNoInteractions(acquisition)
    }

    @Test fun `EA test uses small window without acquisition or Discord`() {
        whenever(gateway.getLatestMatches(clubId, 5)).thenReturn(EaApiResult.Success(listOf(mock<MatchResponse>())))

        client.mutateWith(csrf()).post().uri("/api/admin/clubs/$clubId/ea/test").exchange()
            .expectStatus().isOk.expectBody().jsonPath("$.matchesReturned").isEqualTo(1)

        verify(gateway).getLatestMatches(clubId, 5)
        verifyNoInteractions(acquisition, discord)
        val audit = Mockito.inOrder(auditLog)
        audit.verify(auditLog).record("nextjs-admin-bff", "EA_TEST", ClubId(clubId), null, "START", null)
        audit.verify(auditLog).record("nextjs-admin-bff", "EA_TEST", ClubId(clubId), null, "SUCCESS", null)
    }

    @Test fun `EA test returns a functional failure without changing acquisition state`() {
        whenever(gateway.getLatestMatches(clubId, 5)).thenReturn(EaApiResult.Unavailable(0, "timeout"))

        client.mutateWith(csrf()).post().uri("/api/admin/clubs/$clubId/ea/test").exchange()
            .expectStatus().isEqualTo(502).expectBody().jsonPath("$.errorCode").isEqualTo("0")

        verifyNoInteractions(acquisition, discord)
        val audit = Mockito.inOrder(auditLog)
        audit.verify(auditLog).record("nextjs-admin-bff", "EA_TEST", ClubId(clubId), null, "START", null)
        audit.verify(auditLog).record("nextjs-admin-bff", "EA_TEST", ClubId(clubId), null, "FAILURE", "0")
    }

    @Test fun `Discord test requires a resolvable destination`() {
        whenever(resolver.resolve(ClubId(clubId))).thenReturn(null)

        client.mutateWith(csrf()).post().uri("/api/admin/clubs/$clubId/discord/test").exchange()
            .expectStatus().isEqualTo(502).expectBody().jsonPath("$.errorCode").isEqualTo("NO_DESTINATION")

        verify(discord, never()).send(any(), any())
        val audit = Mockito.inOrder(auditLog)
        audit.verify(auditLog).record("nextjs-admin-bff", "DISCORD_TEST", ClubId(clubId), null, "START", null)
        audit.verify(auditLog).record("nextjs-admin-bff", "DISCORD_TEST", ClubId(clubId), null, "FAILURE", "NO_DESTINATION")
    }

    @Test fun `Discord test sends only an isolated test payload to its club destination`() {
        val destination = mock<DiscordDestination>()
        whenever(resolver.resolve(ClubId(clubId))).thenReturn(destination)

        client.mutateWith(csrf()).post().uri("/api/admin/clubs/$clubId/discord/test").exchange()
            .expectStatus().isOk.expectBody().jsonPath("$.status").isEqualTo("success")

        verify(discord).send(org.mockito.kotlin.eq(destination), any())
        val audit = Mockito.inOrder(auditLog)
        audit.verify(auditLog).record("nextjs-admin-bff", "DISCORD_TEST", ClubId(clubId), null, "START", null)
        audit.verify(auditLog).record("nextjs-admin-bff", "DISCORD_TEST", ClubId(clubId), null, "SUCCESS", null)
    }

    @Test fun `Discord test returns a safe functional error with Discord HTTP status`() {
        val destination = mock<DiscordDestination>()
        whenever(resolver.resolve(ClubId(clubId))).thenReturn(destination)
        val responseException = WebClientResponseException.create(HttpStatus.BAD_REQUEST.value(), "Bad request", HttpHeaders.EMPTY, ByteArray(0), null)
        doThrow(DiscordDeliveryException("Discord rejected webhook", responseException))
            .whenever(discord).send(org.mockito.kotlin.eq(destination), any())

        client.mutateWith(csrf()).post().uri("/api/admin/clubs/$clubId/discord/test").exchange()
            .expectStatus().isEqualTo(502).expectBody()
            .jsonPath("$.errorCode").isEqualTo("400")
            .jsonPath("$.httpStatus").isEqualTo(400)
    }
}
