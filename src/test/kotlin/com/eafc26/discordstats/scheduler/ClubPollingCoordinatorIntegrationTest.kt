package com.eafc26.discordstats.scheduler

import com.eafc26.discordstats.application.club.EaPlatform
import com.eafc26.discordstats.application.club.MonitoredClub
import com.eafc26.discordstats.application.club.MonitoredClubRepository
import com.eafc26.discordstats.config.AppProperties
import com.eafc26.discordstats.config.EaProperties
import com.eafc26.discordstats.config.PhraseBank
import com.eafc26.discordstats.discord.DiscordDestination
import com.eafc26.discordstats.discord.DiscordDestinationResolver
import com.eafc26.discordstats.discord.DiscordRenderer
import com.eafc26.discordstats.discord.DiscordWebhookClient
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.ClubName
import com.eafc26.discordstats.ea.EaApiResult
import com.eafc26.discordstats.ea.EaClubsGateway
import com.eafc26.discordstats.ea.model.ClubDetails
import com.eafc26.discordstats.ea.model.ClubMatchEntry
import com.eafc26.discordstats.ea.model.MatchResponse
import com.eafc26.discordstats.llm.EditorialContextBuilder
import com.eafc26.discordstats.llm.LlmEditorialService
import com.eafc26.discordstats.llm.LlmProperties
import com.eafc26.discordstats.presentation.MatchSummaryBuilder
import com.eafc26.discordstats.service.AcquisitionStateHolder
import com.eafc26.discordstats.service.CanonicalMatchFactory
import com.eafc26.discordstats.service.DiscordMatchPublicationService
import com.eafc26.discordstats.service.LatestMatchHolder
import com.eafc26.discordstats.service.MatchAcquisitionService
import com.eafc26.discordstats.store.JsonCanonicalMatchRepository
import com.eafc26.discordstats.store.PublicationState
import com.eafc26.discordstats.store.PublishedMatchStore
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.file.Path
import java.time.Instant

class ClubPollingCoordinatorIntegrationTest {
    @TempDir lateinit var tempDir: Path

    private val clubA = ClubId("100")
    private val clubB = ClubId("200")
    private lateinit var originalUserHome: String

    @BeforeEach
    fun setUp() {
        originalUserHome = System.getProperty("user.home")
        System.setProperty("user.home", tempDir.toString())
    }

    @AfterEach
    fun tearDown() {
        System.setProperty("user.home", originalUserHome)
    }

    @Test
    fun `one cycle persists same match id for two clubs and skips Discord only for club without destination`() {
        val mapper = jacksonObjectMapper().findAndRegisterModules()
        val gateway: EaClubsGateway = mock()
        val webhookClient: DiscordWebhookClient = mock()
        val store = PublishedMatchStore(mapper)
        val canonicalRepository = JsonCanonicalMatchRepository(mapper, tempDir.resolve("data"), null)
        val stateHolder = AcquisitionStateHolder()
        val latestHolder = LatestMatchHolder()
        val summaryBuilder = MatchSummaryBuilder(PhraseBank(mapper))
        val llm = LlmEditorialService(EditorialContextBuilder(), null, mock(), LlmProperties(enabled = false))
        val destinationA = DiscordDestination("https://discord.test/club-a")
        val publication = DiscordMatchPublicationService(
            store,
            webhookClient,
            DiscordRenderer(summaryBuilder),
            llm,
            DiscordDestinationResolver { clubId -> if (clubId == clubA) destinationA else null },
        )
        val acquisition = MatchAcquisitionService(
            gateway,
            store,
            publication,
            AppProperties(ea = EaProperties(clubId = clubA.value, clubName = "Club A")),
            stateHolder,
            latestHolder,
            summaryBuilder,
            canonicalRepository,
            CanonicalMatchFactory(),
            null,
            llm,
        )
        store.saveIds(clubA, setOf("old-a"))
        store.saveIds(clubB, setOf("old-b"))
        whenever(gateway.getLatestMatches(clubA.value)).thenReturn(EaApiResult.Success(listOf(match(clubA))))
        whenever(gateway.getLatestMatches(clubB.value)).thenReturn(EaApiResult.Success(listOf(match(clubB))))
        whenever(gateway.getMembersStats(clubA.value)).thenReturn(EaApiResult.Success(emptyList()))
        whenever(gateway.getMembersStats(clubB.value)).thenReturn(EaApiResult.Success(emptyList()))
        val coordinator = ClubPollingCoordinator(repository(), acquisition, PollingStatusHolder())

        val cycle = coordinator.pollEnabledClubs(60_000)

        assertThat(cycle.clubs).hasSize(2).allMatch { !it.failed }
        assertThat(canonicalRepository.findAll(clubA).single().interpretation.perspectiveClubId).isEqualTo(clubA)
        assertThat(canonicalRepository.findAll(clubB).single().interpretation.perspectiveClubId).isEqualTo(clubB)
        assertThat(canonicalRepository.findAll(clubA).single().matchId.value).isEqualTo("same-match")
        assertThat(canonicalRepository.findAll(clubB).single().matchId.value).isEqualTo("same-match")
        assertThat(store.find(clubA, "same-match")?.state).isEqualTo(PublicationState.DELIVERED)
        assertThat(store.find(clubB, "same-match")?.state).isEqualTo(PublicationState.BASELINED)
        assertThat(latestHolder.hasPresentation(clubA)).isTrue()
        assertThat(latestHolder.hasPresentation(clubB)).isTrue()
        verify(webhookClient, times(1)).send(any(), any())
    }

    private fun repository() = object : MonitoredClubRepository {
        private val clubs = listOf(monitored(clubB), monitored(clubA))
        override fun save(club: MonitoredClub) = club
        override fun findById(clubId: ClubId) = clubs.firstOrNull { it.clubId == clubId }
        override fun findAll() = clubs
        override fun existsById(clubId: ClubId) = clubs.any { it.clubId == clubId }
        override fun deleteById(clubId: ClubId) = false
    }

    private fun monitored(clubId: ClubId) = MonitoredClub(
        clubId = clubId,
        displayName = ClubName("Club ${clubId.value}"),
        platform = EaPlatform("common-gen5"),
        monitoringEnabled = true,
        discordWebhookSecretReference = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun match(clubId: ClubId) = MatchResponse(
        matchId = "same-match",
        timestamp = 1_700_000_000,
        clubs = mapOf(
            clubId.value to ClubMatchEntry(
                details = ClubDetails(name = "Club ${clubId.value}"),
                score = "2",
                result = "1",
            ),
            "opponent-${clubId.value}" to ClubMatchEntry(
                details = ClubDetails(name = "Opponent"),
                score = "1",
                result = "0",
            ),
        ),
        players = emptyMap(),
    )
}
