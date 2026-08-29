package com.eafc26.discordstats.service

import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.store.EventStatus
import com.eafc26.discordstats.store.OperationalEvent
import com.eafc26.discordstats.store.OperationalEventRepository
import com.eafc26.discordstats.store.DeliveryUncertaintyReason
import com.eafc26.discordstats.store.DiscordPublicationOrigin
import com.eafc26.discordstats.store.PublicationRecord
import com.eafc26.discordstats.store.PublicationState
import org.slf4j.LoggerFactory

/**
 * Type-safe wrapper around [OperationalEventRepository] for recording high-value
 * operational events used by admin diagnostics. Never throws — recording failures
 * are logged as warnings and swallowed so instrumentation never breaks the pipeline.
 */
class OperationalEventRecorder(private val repository: OperationalEventRepository) {
    private val log = LoggerFactory.getLogger(javaClass)

    private fun record(event: OperationalEvent) {
        try {
            repository.save(event)
        } catch (ex: Exception) {
            log.warn("Failed to record operational event: type={}, error={}", event.eventType, ex.message)
        }
    }

    fun pollingStarted(clubId: ClubId) =
        record(OperationalEvent(clubId = clubId, eventType = "POLLING", phase = "START", status = EventStatus.INFO, message = "Polling started"))

    fun pollingCompleted(clubId: ClubId, durationMs: Long) =
        record(OperationalEvent(clubId = clubId, eventType = "POLLING", phase = "COMPLETE", status = EventStatus.SUCCESS, durationMs = durationMs, message = "Polling completed"))

    fun pollingFailed(clubId: ClubId, durationMs: Long, message: String?) =
        record(OperationalEvent(clubId = clubId, eventType = "POLLING", phase = "FAILED", status = EventStatus.FAILURE, durationMs = durationMs, message = message))

    fun eaUnavailable(clubId: ClubId, statusCode: Int, message: String?) =
        record(OperationalEvent(clubId = clubId, eventType = "EA_FETCH", phase = "UNAVAILABLE", status = EventStatus.FAILURE, errorCode = statusCode.toString(), message = message))

    fun eaSuccess(clubId: ClubId, durationMs: Long, message: String? = null) =
        record(OperationalEvent(clubId = clubId, eventType = "EA_FETCH", phase = "SUCCESS", status = EventStatus.SUCCESS, durationMs = durationMs, message = message))

    fun eaFetchExpanded(clubId: ClubId, fromWindow: Int, toWindow: Int, checkpointFound: Boolean) =
        record(OperationalEvent(clubId = clubId, eventType = "EA_FETCH_EXPANDED", phase = "EXPANDED", status = EventStatus.INFO,
            message = "fromWindow=$fromWindow toWindow=$toWindow checkpointFound=$checkpointFound"))

    fun eaCheckpointMissing(clubId: ClubId, window: Int, matchesReturned: Int) =
        record(OperationalEvent(clubId = clubId, eventType = "EA_FETCH", phase = "CHECKPOINT_MISSING", status = EventStatus.WARNING,
            message = "window=$window matchesReturned=$matchesReturned checkpointFound=false"))

    fun adminPollStarted(clubId: ClubId) =
        record(OperationalEvent(clubId = clubId, eventType = "ADMIN_POLL", phase = "START", status = EventStatus.INFO))

    fun adminPollCompleted(clubId: ClubId, durationMs: Long) =
        record(OperationalEvent(clubId = clubId, eventType = "ADMIN_POLL", phase = "SUCCESS", status = EventStatus.SUCCESS, durationMs = durationMs))

    fun adminPollFailed(clubId: ClubId, errorCode: String?, message: String?) =
        record(OperationalEvent(clubId = clubId, eventType = "ADMIN_POLL", phase = "FAILURE", status = EventStatus.FAILURE, errorCode = errorCode, message = message))

    fun eaTest(clubId: ClubId, success: Boolean, durationMs: Long, message: String? = null, errorCode: String? = null) =
        record(OperationalEvent(clubId = clubId, eventType = "EA_TEST", phase = if (success) "SUCCESS" else "FAILURE", status = if (success) EventStatus.SUCCESS else EventStatus.FAILURE, durationMs = durationMs, message = message, errorCode = errorCode))

    fun discordTest(clubId: ClubId, success: Boolean, durationMs: Long, message: String? = null, errorCode: String? = null) =
        record(OperationalEvent(clubId = clubId, eventType = "DISCORD_TEST", phase = if (success) "SUCCESS" else "FAILURE", status = if (success) EventStatus.SUCCESS else EventStatus.FAILURE, durationMs = durationMs, message = message, errorCode = errorCode))

    fun acquisitionStarted(clubId: ClubId, trigger: String) =
        record(OperationalEvent(clubId = clubId, eventType = "ACQUISITION", phase = "START", status = EventStatus.INFO, message = "Trigger: $trigger"))

    fun acquisitionCompleted(clubId: ClubId, durationMs: Long) =
        record(OperationalEvent(clubId = clubId, eventType = "ACQUISITION", phase = "COMPLETE", status = EventStatus.SUCCESS, durationMs = durationMs))

    fun acquisitionFailed(clubId: ClubId, phase: String?, message: String?) =
        record(OperationalEvent(clubId = clubId, eventType = "ACQUISITION", phase = phase, status = EventStatus.FAILURE, message = message))

    fun canonicalPersisted(clubId: ClubId, matchId: String) =
        record(OperationalEvent(clubId = clubId, matchId = matchId, eventType = "CANONICAL", phase = "PERSISTED", status = EventStatus.SUCCESS))

    fun discordPendingCreated(clubId: ClubId, matchId: String) =
        record(OperationalEvent(
            clubId = clubId,
            matchId = matchId,
            eventType = "DISCORD",
            phase = "PENDING_CREATED",
            status = EventStatus.INFO,
            message = "Origem: intenção durável de publicação",
        ))

    fun discordClaimed(clubId: ClubId, matchId: String, origin: DiscordPublicationOrigin) =
        record(OperationalEvent(
            clubId = clubId,
            matchId = matchId,
            eventType = "DISCORD",
            phase = "CLAIMED",
            status = EventStatus.INFO,
            message = "Origem: ${origin.label()}",
        ))

    fun discordRetryScheduled(
        clubId: ClubId,
        matchId: String,
        nextRetryAt: java.time.Instant,
        origin: DiscordPublicationOrigin = DiscordPublicationOrigin.AUTOMATIC_RECONCILIATION,
        httpStatus: Int? = null,
    ) =
        record(OperationalEvent(
            clubId = clubId,
            matchId = matchId,
            eventType = "DISCORD",
            phase = "RETRY_SCHEDULED",
            status = EventStatus.WARNING,
            errorCode = httpStatus?.toString(),
            message = "Origem: ${origin.label()}; próxima tentativa: $nextRetryAt",
        ))

    fun discordRetryExhausted(
        clubId: ClubId,
        matchId: String,
        attempts: Int,
        origin: DiscordPublicationOrigin,
        nextRecoveryAt: java.time.Instant? = null,
        httpStatus: Int? = null,
        reason: String? = null,
    ) =
        record(OperationalEvent(
            clubId = clubId,
            matchId = matchId,
            eventType = "DISCORD",
            phase = "RETRY_EXHAUSTED",
            status = EventStatus.WARNING,
            errorCode = httpStatus?.toString(),
            message = buildString {
                append("Origem: ${origin.label()}; tentativas automáticas: $attempts")
                nextRecoveryAt?.let { append("; recuperação automática: $it") }
                reason?.takeIf { it.isNotBlank() }?.let { append("; motivo: ${sanitizeDiscordDiagnostic(it)}") }
            },
        ))

    fun discordRecoveryAttempt(clubId: ClubId, matchId: String, recoveryAttempt: Int, httpStatus: Int?) =
        record(OperationalEvent(
            clubId = clubId,
            matchId = matchId,
            eventType = "DISCORD",
            phase = "RECOVERY_ATTEMPT",
            status = EventStatus.INFO,
            errorCode = httpStatus?.toString(),
            message = "Origem: ${DiscordPublicationOrigin.AUTOMATIC_RECONCILIATION.label()}; tentativa lenta: $recoveryAttempt",
        ))

    fun discordNoDestinationRecovered(clubId: ClubId, matchId: String) =
        record(OperationalEvent(
            clubId = clubId,
            matchId = matchId,
            eventType = "DISCORD",
            phase = "NO_DESTINATION_RECOVERED",
            status = EventStatus.INFO,
            message = "Origem: recuperação automática",
        ))

    fun editorialSuccess(clubId: ClubId, matchId: String) =
        record(OperationalEvent(clubId = clubId, matchId = matchId, eventType = "EDITORIAL", status = EventStatus.SUCCESS))

    fun editorialFailed(clubId: ClubId, matchId: String, message: String?) =
        record(OperationalEvent(clubId = clubId, matchId = matchId, eventType = "EDITORIAL", status = EventStatus.FAILURE, message = message))

    fun panoramaSuccess(clubId: ClubId, matchId: String) =
        record(OperationalEvent(clubId = clubId, matchId = matchId, eventType = "PANORAMA", status = EventStatus.SUCCESS))

    fun panoramaFailed(clubId: ClubId, matchId: String, message: String?) =
        record(OperationalEvent(clubId = clubId, matchId = matchId, eventType = "PANORAMA", status = EventStatus.FAILURE, message = message))

    fun discordAttempt(
        clubId: ClubId,
        matchId: String,
        origin: DiscordPublicationOrigin = DiscordPublicationOrigin.AUTOMATIC_ACQUISITION,
    ) = record(OperationalEvent(
        clubId = clubId,
        matchId = matchId,
        eventType = "DISCORD",
        phase = "ATTEMPT",
        status = EventStatus.INFO,
        message = "Origem: ${origin.label()}",
    ))

    fun discordSuccess(
        clubId: ClubId,
        matchId: String,
        origin: DiscordPublicationOrigin = DiscordPublicationOrigin.AUTOMATIC_ACQUISITION,
    ) = record(OperationalEvent(
        clubId = clubId,
        matchId = matchId,
        eventType = "DISCORD",
        phase = "DELIVERED",
        status = EventStatus.SUCCESS,
        message = "Origem: ${origin.label()}",
    ))

    fun discordFailed(
        clubId: ClubId,
        matchId: String,
        statusCode: Int?,
        message: String?,
        origin: DiscordPublicationOrigin = DiscordPublicationOrigin.AUTOMATIC_ACQUISITION,
    ) = record(OperationalEvent(
        clubId = clubId,
        matchId = matchId,
        eventType = "DISCORD",
        phase = "FAILED",
        status = EventStatus.FAILURE,
        errorCode = statusCode?.toString(),
        message = "Origem: ${origin.label()}${message?.let { "; ${sanitizeDiscordDiagnostic(it)}" } ?: ""}",
    ))

    fun discordUncertain(
        clubId: ClubId,
        matchId: String,
        reason: DeliveryUncertaintyReason,
        message: String?,
        origin: DiscordPublicationOrigin,
        previousState: PublicationState? = null,
    ) = record(OperationalEvent(
        clubId = clubId,
        matchId = matchId,
        eventType = "DISCORD",
        phase = "UNCERTAIN",
        status = EventStatus.WARNING,
        message = buildString {
            append("Origem: ${origin.label()}; motivo: ${reason.name}")
            previousState?.let { append("; estado anterior: ${it.name}") }
            message?.let { append("; ${sanitizeDiscordDiagnostic(it)}") }
        },
    ))

    fun discordManualResendRequested(clubId: ClubId, matchId: String, previous: PublicationRecord?) =
        record(OperationalEvent(
            clubId = clubId,
            matchId = matchId,
            eventType = "DISCORD",
            phase = "MANUAL_RESEND_REQUESTED",
            status = EventStatus.INFO,
            message = buildString {
                append("Origem: ${DiscordPublicationOrigin.FORCE_PUBLISH.label()}")
                previous?.let {
                    append("; estado anterior: ${it.state.name}")
                    append("; tentativas anteriores: ${it.attemptCount}")
                    it.lastError?.takeIf(String::isNotBlank)?.let { reason -> append("; diagnóstico anterior: ${sanitizeDiscordDiagnostic(reason)}") }
                }
            },
        ))

    fun discordSkipped(clubId: ClubId, matchId: String, reason: String) =
        record(OperationalEvent(clubId = clubId, matchId = matchId, eventType = "DISCORD", phase = "SKIPPED", status = EventStatus.INFO, message = reason))

    fun trialApproved(clubId: ClubId) =
        record(OperationalEvent(clubId = clubId, eventType = "TRIAL", phase = "APPROVED", status = EventStatus.INFO))

    private fun DiscordPublicationOrigin.label(): String = when (this) {
        DiscordPublicationOrigin.AUTOMATIC_ACQUISITION -> "aquisição automática"
        DiscordPublicationOrigin.AUTOMATIC_RECONCILIATION -> "reconciliação automática"
        DiscordPublicationOrigin.FORCE_PUBLISH -> "reenvio manual"
        DiscordPublicationOrigin.STARTUP_RECOVERY -> "recuperação na inicialização"
    }

    private fun sanitizeDiscordDiagnostic(value: String): String = value
        .replace(DISCORD_WEBHOOK_URL, "[Discord webhook]")
        .take(500)

    private companion object {
        val DISCORD_WEBHOOK_URL = Regex("""https?://[^\s]+/api/webhooks/[^\s]+""")
    }
}
