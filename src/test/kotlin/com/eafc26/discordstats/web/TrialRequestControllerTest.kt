package com.eafc26.discordstats.web

import com.eafc26.discordstats.application.club.EaPlatform
import com.eafc26.discordstats.application.club.MonitoredClub
import com.eafc26.discordstats.application.club.MonitoredClubRepository
import com.eafc26.discordstats.application.club.TrialRequest
import com.eafc26.discordstats.application.club.TrialRequestRepository
import com.eafc26.discordstats.application.club.TrialRequestStatus
import com.eafc26.discordstats.application.club.TrialService
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.ClubName
import com.eafc26.discordstats.service.AcquisitionResult
import com.eafc26.discordstats.service.AcquisitionTrigger
import com.eafc26.discordstats.service.MatchAcquisitionService
import com.eafc26.discordstats.store.AdminAuditLogRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.ObjectProvider
import java.time.Instant

class TrialRequestControllerTest {
    @Test fun `approval triggers one initial snapshot after creating an unmonitored trial`() {
        val clubId = ClubId("1104972")
        val clubs = Clubs()
        val request = TrialRequest(1, "Example", "Requester", "contact", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val service = TrialService(clubs, Requests(request))
        val services = mock<ObjectProvider<TrialService>>()
        val audit = mock<ObjectProvider<AdminAuditLogRepository>>()
        val acquisition = mock<MatchAcquisitionService>()
        whenever(services.ifAvailable).thenReturn(service)
        whenever(acquisition.acquire(clubId, AcquisitionTrigger.TRIAL_INITIAL)).thenReturn(AcquisitionResult.NoMatches)
        val controller = TrialRequestController(services, audit, acquisition)

        controller.approve(1, ApproveTrialRequest(clubId.value, "Example", EaPlatform("common-gen5").value), "admin@example.com")

        verify(acquisition).acquire(clubId, AcquisitionTrigger.TRIAL_INITIAL)
        assertThat(clubs.findById(clubId)).extracting("monitoringEnabled", "accessStatus")
            .containsExactly(false, com.eafc26.discordstats.application.club.ClubAccessStatus.TRIAL)
    }

    private class Clubs : MonitoredClubRepository {
        private val values = mutableMapOf<ClubId, MonitoredClub>()
        override fun save(club: MonitoredClub) = club.also { values[it.clubId] = it }
        override fun findById(clubId: ClubId) = values[clubId]
        override fun findAll() = values.values.toList()
        override fun existsById(clubId: ClubId) = clubId in values
        override fun deleteById(clubId: ClubId) = values.remove(clubId) != null
    }
    private class Requests(private val request: TrialRequest) : TrialRequestRepository {
        override fun create(request: TrialRequest) = request
        override fun findAll() = listOf(request)
        override fun findById(id: Long) = request.takeIf { it.id == id }
        override fun save(request: TrialRequest) = request
        override fun transition(id: Long, expected: TrialRequestStatus, replacement: TrialRequest) = replacement
        override fun findRecentPendingEquivalent(clubName: String, contact: String, since: Instant) = null
    }
}
