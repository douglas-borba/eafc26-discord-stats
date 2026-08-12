package com.eafc26.discordstats.application.club

import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.MatchId
import com.eafc26.discordstats.service.OperationalEventRecorder
import java.time.Clock
import java.time.Instant

/** Durable commercial trial lifecycle. Match identity gives counting idempotency. */
class TrialService(
    private val clubs: MonitoredClubRepository,
    private val requests: TrialRequestRepository,
    private val consumption: TrialMatchConsumptionRepository,
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
        // Claim the request before touching club state. The CAS is the cross-instance
        // serialization point: a losing concurrent approval cannot modify a club.
        val approved = requests.transition(requestId, TrialRequestStatus.PENDING, request.copy(status = TrialRequestStatus.APPROVED, clubId = clubId, approvedAt = now, updatedAt = now))
            ?: throw IllegalStateException("Trial request is no longer pending")
        val club = (existing ?: MonitoredClub(clubId, displayName, platform, true, null, createdAt = now, updatedAt = now))
            .copy(displayName = displayName, platform = platform, monitoringEnabled = true, accessStatus = ClubAccessStatus.TRIAL, trialLimit = 3, trialStartedAt = now, updatedAt = now)
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

    /** Called only after a genuinely new canonical match has been persisted. */
    fun reserveNewCanonicalMatch(clubId: ClubId, matchId: MatchId): TrialConsumption = consumption.tryConsume(clubId, matchId, Instant.now(clock)).also { result ->
        if (result is TrialConsumption.Counted) {
            events?.trialMatchCounted(clubId, matchId.value, result.progress.countedMatches, result.progress.limit)
            if (result.progress.expired) events?.trialExpired(clubId, result.progress.countedMatches, result.progress.limit)
        }
    }

    fun progress(club: MonitoredClub): TrialProgress? = when (club.accessStatus) {
        ClubAccessStatus.ACTIVE -> null
        ClubAccessStatus.TRIAL, ClubAccessStatus.TRIAL_EXPIRED -> TrialProgress(consumption.count(club.clubId), club.trialLimit ?: 3, club.accessStatus == ClubAccessStatus.TRIAL_EXPIRED)
    }

    fun restrictsAcquisition(clubId: ClubId): Boolean = clubs.findById(clubId)?.accessStatus == ClubAccessStatus.TRIAL
    fun isExpired(clubId: ClubId): Boolean = clubs.findById(clubId)?.accessStatus == ClubAccessStatus.TRIAL_EXPIRED
}

data class TrialProgress(val countedMatches: Int, val limit: Int, val expired: Boolean)

interface TrialMatchConsumptionRepository {
    /** Atomically locks the club, consumes at most one remaining slot, and may expire it. */
    fun tryConsume(clubId: ClubId, matchId: MatchId, now: Instant): TrialConsumption
    fun count(clubId: ClubId): Int
}

sealed interface TrialConsumption {
    data class Counted(val progress: TrialProgress) : TrialConsumption
    data object AlreadyCounted : TrialConsumption
    data object NotTrialOrLimitReached : TrialConsumption
}

private fun normalize(value: String) = value.trim().lowercase().replace(Regex("\\s+"), " ")
