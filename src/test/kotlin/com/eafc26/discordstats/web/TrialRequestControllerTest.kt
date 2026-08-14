package com.eafc26.discordstats.web

import com.eafc26.discordstats.application.club.*
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.ClubName
import com.eafc26.discordstats.service.AcquisitionResult
import com.eafc26.discordstats.service.AcquisitionTrigger
import com.eafc26.discordstats.service.MatchAcquisitionService
import com.eafc26.discordstats.service.TrialApprovalService
import com.eafc26.discordstats.store.AdminAuditLogRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.ObjectProvider
import java.time.Instant

class TrialRequestControllerTest {
    @Test fun `new trial commits approval before a successful initial snapshot`() {
        val fixture = fixture()
        whenever(fixture.acquisition.acquire(fixture.clubId, AcquisitionTrigger.TRIAL_INITIAL)).thenReturn(AcquisitionResult.NoMatches)

        val response = fixture.controller.approve(1, approveRequest(fixture.clubId), "admin@example.com")

        assertThat(response).extracting("status", "clubState", "snapshot", "message")
            .containsExactly("approved", "TRIAL", "ready", "Solicitação aprovada.")
        assertThat(fixture.requests.findById(1)!!.status).isEqualTo(TrialRequestStatus.APPROVED)
        assertThat(fixture.clubs.findById(fixture.clubId)!!.accessStatus).isEqualTo(ClubAccessStatus.TRIAL)
        verify(fixture.acquisition).acquire(fixture.clubId, AcquisitionTrigger.TRIAL_INITIAL)
    }

    @Test fun `EA unavailability keeps a new trial approved after the transaction commits`() {
        val fixture = fixture()
        whenever(fixture.acquisition.acquire(fixture.clubId, AcquisitionTrigger.TRIAL_INITIAL))
            .thenReturn(AcquisitionResult.EaUnavailable(503, "EA unavailable"))

        val response = fixture.controller.approve(1, approveRequest(fixture.clubId), "admin@example.com")

        assertThat(response).extracting("status", "clubState", "snapshot", "message")
            .containsExactly("approved", "TRIAL", "unavailable", "Solicitação aprovada. Os dados iniciais não puderam ser carregados agora.")
        assertThat(fixture.requests.findById(1)!!.status).isEqualTo(TrialRequestStatus.APPROVED)
        assertThat(fixture.clubs.findById(fixture.clubId)!!.accessStatus).isEqualTo(ClubAccessStatus.TRIAL)
    }

    @Test fun `existing trial approves the request without another initial snapshot`() {
        val fixture = fixture(existingStatus = ClubAccessStatus.TRIAL)

        val response = fixture.controller.approve(1, approveRequest(fixture.clubId), "admin@example.com")

        assertThat(response).extracting("clubState", "snapshot", "message")
            .containsExactly("TRIAL", "not_required", "Solicitação aprovada. Este clube já estava em período de teste.")
        assertThat(fixture.requests.findById(1)!!.status).isEqualTo(TrialRequestStatus.APPROVED)
        verify(fixture.acquisition, never()).acquire(fixture.clubId, AcquisitionTrigger.TRIAL_INITIAL)
    }

    @Test fun `existing active club remains active and is not snapshotted again`() {
        val fixture = fixture(existingStatus = ClubAccessStatus.ACTIVE)

        val response = fixture.controller.approve(1, approveRequest(fixture.clubId), "admin@example.com")

        assertThat(response).extracting("clubState", "snapshot", "message")
            .containsExactly("ACTIVE", "not_required", "Solicitação aprovada. Este clube já possui acesso ativo.")
        assertThat(fixture.requests.findById(1)!!.status).isEqualTo(TrialRequestStatus.APPROVED)
        assertThat(fixture.clubs.findById(fixture.clubId)!!.accessStatus).isEqualTo(ClubAccessStatus.ACTIVE)
        verify(fixture.acquisition, never()).acquire(fixture.clubId, AcquisitionTrigger.TRIAL_INITIAL)
    }

    @Test fun `unexpected snapshot exception remains an error after approval is committed`() {
        val fixture = fixture()
        whenever(fixture.acquisition.acquire(fixture.clubId, AcquisitionTrigger.TRIAL_INITIAL)).thenThrow(IllegalStateException("broken acquisition"))

        assertThatThrownBy { fixture.controller.approve(1, approveRequest(fixture.clubId), "admin@example.com") }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("broken acquisition")
        assertThat(fixture.requests.findById(1)!!.status).isEqualTo(TrialRequestStatus.APPROVED)
        assertThat(fixture.clubs.findById(fixture.clubId)!!.accessStatus).isEqualTo(ClubAccessStatus.TRIAL)
    }

    private fun approveRequest(clubId: ClubId) = ApproveTrialRequest(clubId.value, "Example", EaPlatform("common-gen5").value)

    private fun fixture(existingStatus: ClubAccessStatus? = null): Fixture {
        val clubId = ClubId("1104972")
        val clubs = Clubs().apply {
            existingStatus?.let {
                save(MonitoredClub(clubId, ClubName("Existing"), EaPlatform("common-gen5"), true, null, Instant.EPOCH, Instant.EPOCH, it))
            }
        }
        val requests = Requests(TrialRequest(1, "Example", "Requester", "contact", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH))
        val trialService = TrialService(clubs, requests)
        val approvalService = TrialApprovalService(trialService)
        val trials = mock<ObjectProvider<TrialService>>()
        val approvals = mock<ObjectProvider<TrialApprovalService>>()
        val audit = mock<ObjectProvider<AdminAuditLogRepository>>()
        val acquisition = mock<MatchAcquisitionService>()
        whenever(trials.ifAvailable).thenReturn(trialService)
        whenever(approvals.ifAvailable).thenReturn(approvalService)
        return Fixture(
            controller = TrialRequestController(trials, approvals, audit, acquisition),
            clubId = clubId,
            clubs = clubs,
            requests = requests,
            acquisition = acquisition,
        )
    }

    private data class Fixture(
        val controller: TrialRequestController,
        val clubId: ClubId,
        val clubs: Clubs,
        val requests: Requests,
        val acquisition: MatchAcquisitionService,
    )

    private class Clubs : MonitoredClubRepository {
        private val values = mutableMapOf<ClubId, MonitoredClub>()
        override fun save(club: MonitoredClub) = club.also { values[it.clubId] = it }
        override fun findById(clubId: ClubId) = values[clubId]
        override fun findAll() = values.values.toList()
        override fun existsById(clubId: ClubId) = clubId in values
        override fun deleteById(clubId: ClubId) = values.remove(clubId) != null
    }

    private class Requests(request: TrialRequest) : TrialRequestRepository {
        private var current = request
        override fun create(request: TrialRequest) = request
        override fun findAll() = listOf(current)
        override fun findById(id: Long) = current.takeIf { it.id == id }
        override fun save(request: TrialRequest) = request.also { current = it }
        override fun transition(id: Long, expected: TrialRequestStatus, replacement: TrialRequest) =
            replacement.takeIf { current.id == id && current.status == expected }?.also { current = it }
        override fun findRecentPendingEquivalent(clubName: String, contact: String, since: Instant) = null
    }
}
