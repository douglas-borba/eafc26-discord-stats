package com.eafc26.discordstats.application.club

import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.service.OperationalEventRecorder
import java.time.Clock
import java.time.Instant

/** Durable commercial trial lifecycle. A trial is a one-time dashboard snapshot. */
class TrialService(
    private val clubs: MonitoredClubRepository,
    private val requests: TrialRequestRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val events: OperationalEventRecorder? = null,
) {
    fun request(clubName: String, requesterName: String, contact: String): TrialRequest {
        val now = Instant.now(clock)
        requests.findRecentPendingEquivalent(normalize(clubName), normalize(contact), now.minusSeconds(900))?.let { return it }
        return requests.create(TrialRequest(clubName = clubName, requesterName = requesterName, contact = contact, createdAt = now, updatedAt = now))
    }

    fun listRequests(): List<TrialRequest> = requests.findAll()

    fun approve(requestId: Long, clubId: ClubId, displayName: com.eafc26.discordstats.domain.match.ClubName, platform: EaPlatform): TrialRequest {
        val request = requests.findById(requestId) ?: throw NoSuchElementException("Trial request not found")
        require(request.status == TrialRequestStatus.PENDING) { "Trial request is not pending" }
        val now = Instant.now(clock)
        val existing = clubs.findById(clubId)
        require(existing?.accessStatus != ClubAccessStatus.ACTIVE) { "This club is already active" }
        val approved = requests.transition(requestId, TrialRequestStatus.PENDING, request.copy(status = TrialRequestStatus.APPROVED, clubId = clubId, approvedAt = now, updatedAt = now))
            ?: throw IllegalStateException("Trial request is no longer pending")
        val club = (existing ?: MonitoredClub(clubId, displayName, platform, false, null, createdAt = now, updatedAt = now))
            .copy(displayName = displayName, platform = platform, monitoringEnabled = false, accessStatus = ClubAccessStatus.TRIAL, updatedAt = now)
        clubs.save(club)
        events?.trialApproved(clubId)
        return approved
    }

    fun reject(requestId: Long): TrialRequest {
        val request = requests.findById(requestId) ?: throw NoSuchElementException("Trial request not found")
        require(request.status == TrialRequestStatus.PENDING) { "Trial request is not pending" }
        val now = Instant.now(clock)
        return requests.transition(requestId, TrialRequestStatus.PENDING, request.copy(status = TrialRequestStatus.REJECTED, rejectedAt = now, updatedAt = now))
            ?: throw IllegalStateException("Trial request is no longer pending")
    }

    fun isTrial(clubId: ClubId): Boolean = clubs.findById(clubId)?.accessStatus == ClubAccessStatus.TRIAL
}

private fun normalize(value: String) = value.trim().lowercase().replace(Regex("\\s+"), " ")
