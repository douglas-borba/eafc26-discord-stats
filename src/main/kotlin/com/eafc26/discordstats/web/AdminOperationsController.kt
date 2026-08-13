package com.eafc26.discordstats.web

import com.eafc26.discordstats.application.club.MonitoredClubRepository
import com.eafc26.discordstats.discord.DiscordDestinationResolver
import com.eafc26.discordstats.discord.DiscordEmbed
import com.eafc26.discordstats.discord.DiscordPayload
import com.eafc26.discordstats.discord.DiscordWebhookClient
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.ea.EaApiResult
import com.eafc26.discordstats.ea.EaClubsGateway
import com.eafc26.discordstats.ea.WindowedEaClubsGateway
import com.eafc26.discordstats.service.AcquisitionResult
import com.eafc26.discordstats.service.AcquisitionTrigger
import com.eafc26.discordstats.service.MatchAcquisitionService
import com.eafc26.discordstats.service.OperationalEventRecorder
import com.eafc26.discordstats.store.AdminAuditLogRepository
import com.eafc26.discordstats.application.club.TrialService
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

@RestController
class AdminOperationsController(
    private val clubs: MonitoredClubRepository,
    private val acquisition: MatchAcquisitionService,
    @Qualifier("production") private val gateway: EaClubsGateway,
    private val destinationResolver: DiscordDestinationResolver,
    private val discordClient: DiscordWebhookClient,
    private val eventRecorder: OperationalEventRecorder? = null,
    private val auditLog: AdminAuditLogRepository? = null,
    private val trials: org.springframework.beans.factory.ObjectProvider<TrialService>? = null,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/api/admin/clubs/{clubId}/poll")
    fun poll(
        @PathVariable clubId: String,
        @RequestHeader("X-Admin-Identity", defaultValue = "nextjs-admin-bff") admin: String,
    ): Mono<ResponseEntity<Map<String, Any?>>> = Mono.fromCallable {
        val club = requireClub(clubId)
        if (trials?.ifAvailable?.isTrial(club) == true) {
            return@fromCallable ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                mapOf<String, Any?>("status" to "trial_snapshot", "message" to "Este clube possui apenas uma prévia inicial. Ative o acompanhamento para buscar novas partidas."),
            )
        }
        val audit = startAudit(admin, "ADMIN_POLL", club)
        val started = System.currentTimeMillis()
        eventRecorder?.adminPollStarted(club)
        try {
            // ADMIN_POLL intentionally retains scheduler-equivalent incremental and first-run semantics.
            val result = acquisition.acquire(club, AcquisitionTrigger.ADMIN_POLL)
            val duration = System.currentTimeMillis() - started
            val body = pollBody(result, duration, acquisition.lastFetchMetrics(club))
            val success = result !is AcquisitionResult.EaUnavailable && result !is AcquisitionResult.Busy
            if (success) eventRecorder?.adminPollCompleted(club, duration)
            else {
                val code = when (result) {
                    is AcquisitionResult.EaUnavailable -> result.statusCode.toString()
                    AcquisitionResult.Busy -> "BUSY"
                    else -> "UNKNOWN"
                }
                val message = (result as? AcquisitionResult.EaUnavailable)?.message
                eventRecorder?.adminPollFailed(club, code, message)
                completeAudit(audit, admin, "ADMIN_POLL", club, result = if (result is AcquisitionResult.Busy) "BUSY" else "FAILURE", errorCode = code)
            }
            if (success) completeAudit(audit, admin, "ADMIN_POLL", club, result = "SUCCESS")
            ResponseEntity.ok(body)
        } catch (ex: ResponseStatusException) {
            throw ex
        } catch (ex: Exception) {
            eventRecorder?.adminPollFailed(club, ex::class.simpleName, ex.message)
            completeAudit(audit, admin, "ADMIN_POLL", club, result = "FAILURE", errorCode = ex::class.simpleName)
            throw ex
        }
    }.subscribeOn(Schedulers.boundedElastic())

    @PostMapping("/api/admin/clubs/{clubId}/ea/test")
    fun testEa(
        @PathVariable clubId: String,
        @RequestHeader("X-Admin-Identity", defaultValue = "nextjs-admin-bff") admin: String,
    ): Mono<ResponseEntity<Map<String, Any?>>> = Mono.fromCallable<ResponseEntity<Map<String, Any?>>> {
        val club = requireClub(clubId)
        val audit = startAudit(admin, "EA_TEST", club)
        val started = System.currentTimeMillis()
        val window = 5
        try {
            val result = if (gateway is WindowedEaClubsGateway) gateway.getLatestMatches(club.value, window) else gateway.getLatestMatches(club.value)
            val duration = System.currentTimeMillis() - started
            when (result) {
                is EaApiResult.Success -> {
                    eventRecorder?.eaTest(club, true, duration, "window=$window matchesReturned=${result.data.size}")
                    completeAudit(audit, admin, "EA_TEST", club, result = "SUCCESS")
                    ResponseEntity.ok(mapOf<String, Any?>("status" to "success", "latencyMs" to duration, "matchesReturned" to result.data.size, "window" to window))
                }
                EaApiResult.NoMatches -> {
                    eventRecorder?.eaTest(club, true, duration, "window=$window matchesReturned=0")
                    completeAudit(audit, admin, "EA_TEST", club, result = "SUCCESS")
                    ResponseEntity.ok(mapOf<String, Any?>("status" to "success", "latencyMs" to duration, "matchesReturned" to 0, "window" to window))
                }
                is EaApiResult.Unavailable -> failure(audit, "EA_TEST", club, admin, duration, result.statusCode.toString(), result.message)
                is EaApiResult.UnexpectedPayload -> failure(audit, "EA_TEST", club, admin, duration, "UNEXPECTED_PAYLOAD", "Resposta inesperada da EA")
            }
        } catch (ex: ResponseStatusException) {
            throw ex
        } catch (ex: Exception) {
            failure(audit, "EA_TEST", club, admin, System.currentTimeMillis() - started, ex::class.simpleName ?: "EA_TEST_ERROR", "Falha ao testar a EA")
        }
    }.subscribeOn(Schedulers.boundedElastic())

    @PostMapping("/api/admin/clubs/{clubId}/discord/test")
    fun testDiscord(
        @PathVariable clubId: String,
        @RequestHeader("X-Admin-Identity", defaultValue = "nextjs-admin-bff") admin: String,
    ): Mono<ResponseEntity<Map<String, Any?>>> = Mono.fromCallable<ResponseEntity<Map<String, Any?>>> {
        val club = requireClub(clubId)
        val audit = startAudit(admin, "DISCORD_TEST", club)
        val destination = destinationResolver.resolve(club)
            ?: return@fromCallable failure(audit, "DISCORD_TEST", club, admin, 0, "NO_DESTINATION", "Webhook não disponível")
        val started = System.currentTimeMillis()
        try {
            discordClient.send(destination, DiscordPayload(listOf(DiscordEmbed(
                title = "EAFC Stats — teste de integração Discord",
                description = "Teste de integração Discord concluído com sucesso.", color = 0x2E86DE, fields = emptyList(),
            ))))
            val duration = System.currentTimeMillis() - started
            eventRecorder?.discordTest(club, true, duration)
            completeAudit(audit, admin, "DISCORD_TEST", club, result = "SUCCESS")
            ResponseEntity.ok(mapOf<String, Any?>("status" to "success", "latencyMs" to duration))
        } catch (ex: ResponseStatusException) {
            throw ex
        } catch (ex: Exception) {
            val httpStatus = (ex.cause as? WebClientResponseException)?.statusCode?.value()
            failure(audit,
                "DISCORD_TEST", club, admin, System.currentTimeMillis() - started,
                httpStatus?.toString() ?: ex::class.simpleName ?: "DISCORD_ERROR",
                "Falha ao enviar teste ao Discord", httpStatus,
            )
        }
    }.subscribeOn(Schedulers.boundedElastic())

    private fun failure(audit: AdminAuditLogRepository, action: String, club: ClubId, admin: String, duration: Long, code: String, message: String, httpStatus: Int? = null): ResponseEntity<Map<String, Any?>> {
        if (action == "EA_TEST") eventRecorder?.eaTest(club, false, duration, message, code) else eventRecorder?.discordTest(club, false, duration, message, code)
        completeAudit(audit, admin, action, club, result = "FAILURE", errorCode = code)
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(mapOf("status" to "failed", "latencyMs" to duration, "errorCode" to code, "message" to message, "httpStatus" to httpStatus))
    }

    private fun startAudit(admin: String, action: String, club: ClubId): AdminAuditLogRepository {
        val audit = auditLog ?: throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Admin audit is unavailable")
        persistAudit(audit, admin, action, club, result = "START", operationMayHaveRun = false)
        return audit
    }

    private fun completeAudit(audit: AdminAuditLogRepository, admin: String, action: String, club: ClubId, result: String, errorCode: String? = null) =
        persistAudit(audit, admin, action, club, result, errorCode, operationMayHaveRun = true)

    private fun persistAudit(
        audit: AdminAuditLogRepository,
        admin: String,
        action: String,
        club: ClubId,
        result: String,
        errorCode: String? = null,
        operationMayHaveRun: Boolean,
    ) {
        try {
            audit.record(admin, action, club, result = result, errorCode = errorCode)
        } catch (ex: Exception) {
            log.error("Admin audit persistence failed: action={}, clubId={}, result={}, errorType={}", action, club.value, result, ex::class.simpleName)
            val reason = if (operationMayHaveRun) "Administrative operation must be verified" else "Administrative operation was not started"
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, reason)
        }
    }

    private fun pollBody(
        result: AcquisitionResult,
        duration: Long,
        metrics: MatchAcquisitionService.AcquisitionFetchMetrics?,
    ): Map<String, Any?> = when (result) {
        is AcquisitionResult.Processed -> mapOf("status" to "success", "durationMs" to duration, "matchesReturned" to metrics?.matchesReturned, "newMatches" to metrics?.newMatches, "published" to result.published.size, "baselineEstablished" to result.baselineEstablished)
        AcquisitionResult.NoMatches -> mapOf("status" to "success", "durationMs" to duration, "matchesReturned" to 0, "newMatches" to 0)
        AcquisitionResult.Busy -> mapOf("status" to "busy", "durationMs" to duration, "message" to "Uma aquisição já está em andamento")
        is AcquisitionResult.EaUnavailable -> mapOf("status" to "failed", "durationMs" to duration, "errorCode" to result.statusCode, "message" to "EA indisponível")
        AcquisitionResult.WebhookNotConfigured -> mapOf("status" to "success", "durationMs" to duration, "newMatches" to 0, "message" to "Aquisição concluída sem webhook")
        is AcquisitionResult.ForceResent -> error("Unsupported poll result")
    }

    private fun requireClub(value: String): ClubId = ClubId(value).also { club ->
        if (!clubs.existsById(club)) throw ResponseStatusException(HttpStatus.NOT_FOUND, "Club not found")
    }
}
