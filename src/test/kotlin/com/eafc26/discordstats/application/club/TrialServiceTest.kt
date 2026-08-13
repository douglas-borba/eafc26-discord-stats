package com.eafc26.discordstats.application.club

import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.ClubName
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant

class TrialServiceTest {
    @Test fun `approval creates an unmonitored trial snapshot`() {
        val clubs = Clubs()
        val clubId = ClubId("1104972")
        val service = TrialService(clubs, Requests(TrialRequest(1, "Example", "Requester", "contact", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)))

        service.approve(1, clubId, ClubName("Example"), EaPlatform("common-gen5"))

        assertThat(clubs.findById(clubId)).extracting("accessStatus", "monitoringEnabled")
            .containsExactly(ClubAccessStatus.TRIAL, false)
        assertThat(service.isTrial(clubId)).isTrue()
    }

    @Test fun `approval never downgrades an active club to a trial`() {
        val clubs = Clubs()
        val clubId = ClubId("1104972")
        clubs.save(MonitoredClub(clubId, ClubName("Active"), EaPlatform("common-gen5"), true, null, Instant.EPOCH, Instant.EPOCH, ClubAccessStatus.ACTIVE))
        val service = TrialService(clubs, Requests(TrialRequest(1, "Active", "Requester", "contact", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)))

        assertThatThrownBy { service.approve(1, clubId, ClubName("Active"), EaPlatform("common-gen5")) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("This club is already active")
        assertThat(clubs.findById(clubId)!!.accessStatus).isEqualTo(ClubAccessStatus.ACTIVE)
    }

    private class Clubs : MonitoredClubRepository {
        private val values = mutableMapOf<ClubId, MonitoredClub>()
        override fun save(club: MonitoredClub) = club.also { values[it.clubId] = it }
        override fun findById(clubId: ClubId) = values[clubId]
        override fun findAll() = values.values.toList()
        override fun existsById(clubId: ClubId) = clubId in values
        override fun deleteById(clubId: ClubId) = values.remove(clubId) != null
    }
    private class Requests(private val request: TrialRequest? = null) : TrialRequestRepository {
        override fun create(request: TrialRequest) = request
        override fun findAll() = emptyList<TrialRequest>()
        override fun findById(id: Long) = request?.takeIf { it.id == id }
        override fun save(request: TrialRequest) = request
        override fun transition(id: Long, expected: TrialRequestStatus, replacement: TrialRequest) = replacement
        override fun findRecentPendingEquivalent(clubName: String, contact: String, since: Instant) = null
    }
}
