package com.eafc26.discordstats.web

import com.eafc26.discordstats.application.club.*
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.ClubName
import com.eafc26.discordstats.store.AdminAuditLogRepository
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.util.concurrent.ConcurrentHashMap

@RestController
class TrialRequestController(
    private val trials: ObjectProvider<TrialService>,
    private val audit: ObjectProvider<AdminAuditLogRepository>,
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
    @Transactional
    fun approve(
        @PathVariable requestId: Long,
        @RequestBody request: ApproveTrialRequest,
        @RequestHeader("X-Admin-Identity", defaultValue = "nextjs-admin-bff") admin: String,
    ): TrialRequestResponse {
        val approved = service().approve(
            requestId,
            ClubId(request.clubId.clean("clubId", 255)),
            ClubName(request.displayName.clean("displayName", 255)),
            EaPlatform(request.platform.clean("platform", 50)),
        )
        audit.ifAvailable?.record(admin, "TRIAL_APPROVE", approved.clubId, result = "SUCCESS")
        return present(approved)
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
    private fun String.clean(field: String, maximum: Int): String = trim().also {
        if (it.isBlank() || it.length > maximum) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid $field")
    }
    private fun present(request: TrialRequest) = TrialRequestResponse(request.id!!, request.clubName, request.requesterName, request.contact, request.status.name, request.clubId?.value, request.createdAt.toString(), request.approvedAt?.toString(), request.rejectedAt?.toString())
}

data class CreateTrialRequest(val clubName: String, val requesterName: String, val contact: String)
data class ApproveTrialRequest(val clubId: String, val displayName: String, val platform: String)
data class PublicTrialRequestResponse(val id: Long, val status: String)
data class TrialRequestResponse(val id: Long, val clubName: String, val requesterName: String, val contact: String, val status: String, val clubId: String?, val createdAt: String, val approvedAt: String?, val rejectedAt: String?)
