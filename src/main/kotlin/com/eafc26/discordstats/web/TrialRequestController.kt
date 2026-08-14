package com.eafc26.discordstats.web

import com.eafc26.discordstats.application.club.*
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.ClubName
import com.eafc26.discordstats.store.AdminAuditLogRepository
import com.eafc26.discordstats.service.AcquisitionResult
import com.eafc26.discordstats.service.AcquisitionTrigger
import com.eafc26.discordstats.service.MatchAcquisitionService
import com.eafc26.discordstats.service.TrialApprovalService
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.util.concurrent.ConcurrentHashMap

@RestController
class TrialRequestController(
    private val trials: ObjectProvider<TrialService>,
    private val approvals: ObjectProvider<TrialApprovalService>,
    private val audit: ObjectProvider<AdminAuditLogRepository>,
    private val acquisition: MatchAcquisitionService,
) {
    private val recentRequests = ConcurrentHashMap<String, Long>()

    @PostMapping("/api/trial-requests")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody request: CreateTrialRequest, @RequestHeader("X-Forwarded-For", required = false) forwardedFor: String?): PublicTrialRequestResponse {
        val source = forwardedFor?.substringBefore(',')?.trim().orEmpty()
        val now = System.currentTimeMillis()
        recentRequests.entries.removeIf { now - it.value >= 60_000 }
        if (source.isNotBlank() && recentRequests.size >= 1_000 && !recentRequests.containsKey(source)) {
            throw ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Please try again later")
        }
        if (source.isNotBlank() && now - (recentRequests.put(source, now) ?: 0) < 60_000) {
            throw ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Please try again later")
        }
        val created = service().request(
            clubName = request.clubName.clean("clubName", 160),
            requesterName = request.requesterName.clean("requesterName", 160),
            contact = request.contact.clean("contact", 320),
        )
        return PublicTrialRequestResponse(created.id!!, "pending")
    }

    @GetMapping("/api/admin/trial-requests")
    fun list(): List<TrialRequestResponse> = service().listRequests().map(::present)

    @PostMapping("/api/admin/trial-requests/{requestId}/approve")
    fun approve(
        @PathVariable requestId: Long,
        @RequestBody request: ApproveTrialRequest,
        @RequestHeader("X-Admin-Identity", defaultValue = "nextjs-admin-bff") admin: String,
    ): TrialApprovalResponse {
        val clubId = ClubId(request.clubId.clean("clubId", 255))
        val approval = approvalService().approve(
            requestId,
            clubId,
            ClubName(request.displayName.clean("displayName", 255)),
            EaPlatform(request.platform.clean("platform", 50)),
        )

        val snapshot = when (approval) {
            is TrialApprovalResult.NewTrial -> snapshot(clubId)
            is TrialApprovalResult.ExistingTrial,
            is TrialApprovalResult.ExistingActive -> TrialSnapshotStatus.NOT_REQUIRED
        }
        audit.ifAvailable?.record(admin, "TRIAL_APPROVE", approval.request.clubId, result = snapshot.name)
        return presentApproval(approval, snapshot)
    }

    @PostMapping("/api/admin/trial-requests/{requestId}/reject")
    @Transactional
    fun reject(
        @PathVariable requestId: Long,
        @RequestHeader("X-Admin-Identity", defaultValue = "nextjs-admin-bff") admin: String,
    ): TrialRequestResponse {
        val rejected = service().reject(requestId)
        audit.ifAvailable?.record(admin, "TRIAL_REJECT", null, result = "SUCCESS")
        return present(rejected)
    }

    private fun service(): TrialService = trials.ifAvailable ?: throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Trials require PostgreSQL")
    private fun approvalService(): TrialApprovalService = approvals.ifAvailable ?: throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Trials require PostgreSQL")

    private fun snapshot(clubId: ClubId): TrialSnapshotStatus = when (acquisition.acquire(clubId, AcquisitionTrigger.TRIAL_INITIAL)) {
        is AcquisitionResult.EaUnavailable -> TrialSnapshotStatus.UNAVAILABLE
        AcquisitionResult.Busy -> TrialSnapshotStatus.IN_PROGRESS
        else -> TrialSnapshotStatus.READY
    }

    private fun String.clean(field: String, maximum: Int): String = trim().also {
        if (it.isBlank() || it.length > maximum) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid $field")
    }
    private fun present(request: TrialRequest) = TrialRequestResponse(request.id!!, request.clubName, request.requesterName, request.contact, request.status.name, request.clubId?.value, request.createdAt.toString(), request.approvedAt?.toString(), request.rejectedAt?.toString())
    private fun presentApproval(approval: TrialApprovalResult, snapshot: TrialSnapshotStatus): TrialApprovalResponse {
        val (clubState, message) = when (approval) {
            is TrialApprovalResult.NewTrial -> "TRIAL" to when (snapshot) {
                TrialSnapshotStatus.UNAVAILABLE -> "Solicitação aprovada. Os dados iniciais não puderam ser carregados agora."
                TrialSnapshotStatus.IN_PROGRESS -> "Solicitação aprovada. Os dados iniciais já estão sendo carregados."
                else -> "Solicitação aprovada."
            }
            is TrialApprovalResult.ExistingTrial -> "TRIAL" to "Solicitação aprovada. Este clube já estava em período de teste."
            is TrialApprovalResult.ExistingActive -> "ACTIVE" to "Solicitação aprovada. Este clube já possui acesso ativo."
        }
        return TrialApprovalResponse(
            status = "approved",
            clubId = approval.request.clubId!!.value,
            clubState = clubState,
            snapshot = snapshot.name.lowercase(),
            message = message,
        )
    }
}

data class CreateTrialRequest(val clubName: String, val requesterName: String, val contact: String)
data class ApproveTrialRequest(val clubId: String, val displayName: String, val platform: String)
data class PublicTrialRequestResponse(val id: Long, val status: String)
data class TrialRequestResponse(val id: Long, val clubName: String, val requesterName: String, val contact: String, val status: String, val clubId: String?, val createdAt: String, val approvedAt: String?, val rejectedAt: String?)
data class TrialApprovalResponse(val status: String, val clubId: String, val clubState: String, val snapshot: String, val message: String)

private enum class TrialSnapshotStatus { READY, UNAVAILABLE, IN_PROGRESS, NOT_REQUIRED }
