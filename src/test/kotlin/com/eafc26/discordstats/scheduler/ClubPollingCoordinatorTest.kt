package com.eafc26.discordstats.scheduler

import com.eafc26.discordstats.application.club.EaPlatform
import com.eafc26.discordstats.application.club.MonitoredClub
import com.eafc26.discordstats.application.club.MonitoredClubRepository
import com.eafc26.discordstats.application.club.MonitoredClubService
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.ClubName
import com.eafc26.discordstats.service.AcquisitionResult
import com.eafc26.discordstats.service.AcquisitionTrigger
import com.eafc26.discordstats.service.MatchAcquisitionService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ClubPollingCoordinatorTest {
    private val clubA = ClubId("100")
    private val clubB = ClubId("200")
    private val clubC = ClubId("300")
    private val fixedNow = Instant.parse("2026-08-09T12:00:00Z")

    @Test
    fun `processes enabled clubs sequentially by club id and skips disabled clubs`() {
        val acquisition: MatchAcquisitionService = mock()
        val status = PollingStatusHolder()
        val coordinator = coordinator(
            clubs = listOf(club(clubB, true), club(clubC, false), club(clubA, true)),
            acquisition = acquisition,
            status = status,
        )
        whenever(acquisition.acquire(clubA, AcquisitionTrigger.SCHEDULER)).thenReturn(success())
        whenever(acquisition.acquire(clubB, AcquisitionTrigger.SCHEDULER)).thenReturn(success())

        val cycle = coordinator.pollEnabledClubs(60_000)

        assertThat(cycle.clubs.map { it.clubId }).containsExactly(clubA, clubB)
        inOrder(acquisition) {
            verify(acquisition).acquire(clubA, AcquisitionTrigger.SCHEDULER)
            verify(acquisition).acquire(clubB, AcquisitionTrigger.SCHEDULER)
        }
        verify(acquisition, never()).acquire(clubC, AcquisitionTrigger.SCHEDULER)
        assertThat(status.current(clubC).enabled).isFalse()
        assertThat(status.current(clubC).nextCheck).isNull()
    }

    @Test
    fun `failure in one club is isolated and later enabled club still runs`() {
        val acquisition: MatchAcquisitionService = mock()
        val status = PollingStatusHolder()
        val coordinator = coordinator(
            clubs = listOf(club(clubA, true), club(clubB, true), club(clubC, true)),
            acquisition = acquisition,
            status = status,
        )
        whenever(acquisition.acquire(clubA, AcquisitionTrigger.SCHEDULER)).thenReturn(success())
        whenever(acquisition.acquire(clubB, AcquisitionTrigger.SCHEDULER)).thenThrow(IllegalStateException("EA timeout"))
        whenever(acquisition.acquire(clubC, AcquisitionTrigger.SCHEDULER)).thenReturn(success())

        val cycle = coordinator.pollEnabledClubs(60_000)

        assertThat(cycle.clubs.map { it.failed }).containsExactly(false, true, false)
        verify(acquisition).acquire(clubC, AcquisitionTrigger.SCHEDULER)
        assertThat(status.current(clubA).lastResult).isEqualTo("Nenhuma partida nova.")
        assertThat(status.current(clubB).lastResult).contains("Falha inesperada")
        assertThat(status.current(clubC).lastResult).isEqualTo("Nenhuma partida nova.")
        assertThat(status.current(clubA).running).isFalse()
        assertThat(status.current(clubB).running).isFalse()
        assertThat(status.current(clubC).running).isFalse()
    }

    @Test
    fun `club without Discord is a successful acquisition result`() {
        val acquisition: MatchAcquisitionService = mock()
        val status = PollingStatusHolder()
        val coordinator = coordinator(listOf(club(clubA, true)), acquisition, status)
        whenever(acquisition.acquire(clubA, AcquisitionTrigger.SCHEDULER))
            .thenReturn(AcquisitionResult.WebhookNotConfigured)

        val cycle = coordinator.pollEnabledClubs(60_000)

        assertThat(cycle.clubs.single().failed).isFalse()
        assertThat(status.current(clubA).lastResult).contains("concluída normalmente")
    }

    @Test
    fun `status timestamps and interval remain independent per club`() {
        val acquisition: MatchAcquisitionService = mock()
        val status = PollingStatusHolder()
        val coordinator = coordinator(listOf(club(clubA, true), club(clubB, true)), acquisition, status)
        whenever(acquisition.acquire(clubA, AcquisitionTrigger.SCHEDULER)).thenReturn(success())
        whenever(acquisition.acquire(clubB, AcquisitionTrigger.SCHEDULER))
            .thenReturn(AcquisitionResult.EaUnavailable(503, "unavailable"))

        coordinator.pollEnabledClubs(60_000)

        assertThat(status.current(clubA).lastCheck).isEqualTo(fixedNow)
        assertThat(status.current(clubB).lastCheck).isEqualTo(fixedNow)
        assertThat(status.current(clubA).nextCheck).isEqualTo(fixedNow.plusSeconds(60))
        assertThat(status.current(clubA).lastResult).isNotEqualTo(status.current(clubB).lastResult)
    }

    @Test
    fun `repository changes are observed by the next cycle without restart`() {
        val acquisition: MatchAcquisitionService = mock()
        val status = PollingStatusHolder()
        val repository = MutableRepository()
        val service = MonitoredClubService(repository, Clock.fixed(fixedNow, ZoneOffset.UTC))
        val coordinator = ClubPollingCoordinator(repository, acquisition, status, Clock.fixed(fixedNow, ZoneOffset.UTC))
        whenever(acquisition.acquire(clubA, AcquisitionTrigger.SCHEDULER)).thenReturn(success())
        whenever(acquisition.acquire(clubB, AcquisitionTrigger.SCHEDULER)).thenReturn(success())

        service.register(clubA, ClubName("Club A"), EaPlatform("common-gen5"), true)
        assertThat(coordinator.pollEnabledClubs(60_000).clubs.map { it.clubId }).containsExactly(clubA)

        service.register(clubB, ClubName("Club B"), EaPlatform("common-gen5"), false)
        service.setMonitoring(clubB, true)
        assertThat(coordinator.pollEnabledClubs(60_000).clubs.map { it.clubId }).containsExactly(clubA, clubB)

        service.setMonitoring(clubB, false)
        assertThat(coordinator.pollEnabledClubs(60_000).clubs.map { it.clubId }).containsExactly(clubA)
        verify(acquisition, org.mockito.kotlin.times(1)).acquire(clubB, AcquisitionTrigger.SCHEDULER)
    }

    private fun coordinator(
        clubs: List<MonitoredClub>,
        acquisition: MatchAcquisitionService,
        status: PollingStatusHolder = PollingStatusHolder(),
    ) = ClubPollingCoordinator(
        monitoredClubs = repository(clubs),
        acquisitionService = acquisition,
        statusHolder = status,
        clock = Clock.fixed(fixedNow, ZoneOffset.UTC),
    )

    private fun repository(clubs: List<MonitoredClub>) = object : MonitoredClubRepository {
        override fun save(club: MonitoredClub) = club
        override fun findById(clubId: ClubId) = clubs.firstOrNull { it.clubId == clubId }
        override fun findAll() = clubs
        override fun existsById(clubId: ClubId) = clubs.any { it.clubId == clubId }
        override fun deleteById(clubId: ClubId) = false
    }

    private fun club(id: ClubId, enabled: Boolean) = MonitoredClub(
        clubId = id,
        displayName = ClubName("Club ${id.value}"),
        platform = EaPlatform("common-gen5"),
        monitoringEnabled = enabled,
        discordWebhookSecretReference = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun success() = AcquisitionResult.Processed(emptyList(), emptyList(), emptyList())

    private class MutableRepository : MonitoredClubRepository {
        private val clubs = linkedMapOf<ClubId, MonitoredClub>()
        override fun save(club: MonitoredClub) = club.also { clubs[it.clubId] = it }
        override fun findById(clubId: ClubId) = clubs[clubId]
        override fun findAll() = clubs.values.toList()
        override fun existsById(clubId: ClubId) = clubId in clubs
        override fun deleteById(clubId: ClubId) = clubs.remove(clubId) != null
    }
}
