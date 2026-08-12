package com.eafc26.discordstats.application.club

import com.eafc26.discordstats.domain.match.ClubId
import java.time.Instant

enum class TrialRequestStatus { PENDING, APPROVED, REJECTED }

data class TrialRequest(
    val id: Long? = null,
    val clubName: String,
    val requesterName: String,
    val contact: String,
    val status: TrialRequestStatus = TrialRequestStatus.PENDING,
    val clubId: ClubId? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val approvedAt: Instant? = null,
    val rejectedAt: Instant? = null,
)

interface TrialRequestRepository {
    fun create(request: TrialRequest): TrialRequest
    fun findAll(): List<TrialRequest>
    fun findById(id: Long): TrialRequest?
    fun save(request: TrialRequest): TrialRequest
    fun transition(id: Long, expected: TrialRequestStatus, replacement: TrialRequest): TrialRequest?
    fun findRecentPendingEquivalent(clubName: String, contact: String, since: Instant): TrialRequest?
}
