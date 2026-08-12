package com.eafc26.discordstats.application.club

import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.ClubName
import com.eafc26.discordstats.domain.match.MatchId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant

class TrialServiceTest {
    @Test fun `a match identity counts once and expires the trial at three`() {
        val clubs = Clubs()
        val consumption = Consumption(clubs)
        val requests = Requests()
        val service = TrialService(clubs, requests, consumption)
        val clubId = ClubId("1104972")
        clubs.save(MonitoredClub(clubId, ClubName("Example"), EaPlatform("common-gen5"), true, null, Instant.EPOCH, Instant.EPOCH, ClubAccessStatus.TRIAL, 3, Instant.EPOCH))

        service.reserveNewCanonicalMatch(clubId, MatchId("one"))
        service.reserveNewCanonicalMatch(clubId, MatchId("one"))
        service.reserveNewCanonicalMatch(clubId, MatchId("two"))
        val third = service.reserveNewCanonicalMatch(clubId, MatchId("three"))

        assertThat(third).isEqualTo(TrialConsumption.Counted(TrialProgress(3, 3, true)))
        assertThat(clubs.findById(clubId)!!.accessStatus).isEqualTo(ClubAccessStatus.TRIAL_EXPIRED)
        assertThat(clubs.findById(clubId)!!.monitoringEnabled).isFalse()
    }

    @Test fun `approval never downgrades an active club to a trial`() {
        val clubs = Clubs()
        val clubId = ClubId("1104972")
        clubs.save(MonitoredClub(clubId, ClubName("Active"), EaPlatform("common-gen5"), true, null, Instant.EPOCH, Instant.EPOCH, ClubAccessStatus.ACTIVE))
        val request = TrialRequest(1, "Active", "Requester", "contact", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val service = TrialService(clubs, Requests(request), Consumption(clubs))

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
    private class Consumption(private val clubs: Clubs) : TrialMatchConsumptionRepository {
        private val values = mutableSetOf<Pair<ClubId, MatchId>>()
        override fun tryConsume(clubId: ClubId, matchId: MatchId, now: Instant): TrialConsumption {
            if (!values.add(clubId to matchId)) return TrialConsumption.AlreadyCounted
            val count = count(clubId)
            if (count >= 3) {
                clubs.findById(clubId)?.let { clubs.save(it.copy(accessStatus = ClubAccessStatus.TRIAL_EXPIRED, monitoringEnabled = false)) }
            }
            return TrialConsumption.Counted(TrialProgress(count, 3, count >= 3))
        }
        override fun count(clubId: ClubId) = values.count { it.first == clubId }
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
