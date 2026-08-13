package com.eafc26.discordstats.service

import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.store.EventStatus
import com.eafc26.discordstats.store.OperationalEvent
import com.eafc26.discordstats.store.OperationalEventRepository
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

    fun editorialSuccess(clubId: ClubId, matchId: String) =
        record(OperationalEvent(clubId = clubId, matchId = matchId, eventType = "EDITORIAL", status = EventStatus.SUCCESS))

    fun editorialFailed(clubId: ClubId, matchId: String, message: String?) =
        record(OperationalEvent(clubId = clubId, matchId = matchId, eventType = "EDITORIAL", status = EventStatus.FAILURE, message = message))

    fun panoramaSuccess(clubId: ClubId, matchId: String) =
        record(OperationalEvent(clubId = clubId, matchId = matchId, eventType = "PANORAMA", status = EventStatus.SUCCESS))

    fun panoramaFailed(clubId: ClubId, matchId: String, message: String?) =
        record(OperationalEvent(clubId = clubId, matchId = matchId, eventType = "PANORAMA", status = EventStatus.FAILURE, message = message))

    fun discordAttempt(clubId: ClubId, matchId: String) =
        record(OperationalEvent(clubId = clubId, matchId = matchId, eventType = "DISCORD", phase = "ATTEMPT", status = EventStatus.INFO))

    fun discordSuccess(clubId: ClubId, matchId: String) =
        record(OperationalEvent(clubId = clubId, matchId = matchId, eventType = "DISCORD", phase = "DELIVERED", status = EventStatus.SUCCESS))

    fun discordFailed(clubId: ClubId, matchId: String, statusCode: Int?, message: String?) =
        record(OperationalEvent(clubId = clubId, matchId = matchId, eventType = "DISCORD", phase = "FAILED", status = EventStatus.FAILURE, errorCode = statusCode?.toString(), message = message))

    fun discordUncertain(clubId: ClubId, matchId: String, message: String?) =
        record(OperationalEvent(clubId = clubId, matchId = matchId, eventType = "DISCORD", phase = "UNCERTAIN", status = EventStatus.WARNING, message = message))

    fun discordSkipped(clubId: ClubId, matchId: String, reason: String) =
        record(OperationalEvent(clubId = clubId, matchId = matchId, eventType = "DISCORD", phase = "SKIPPED", status = EventStatus.INFO, message = reason))

    fun trialApproved(clubId: ClubId) =
        record(OperationalEvent(clubId = clubId, eventType = "TRIAL", phase = "APPROVED", status = EventStatus.INFO))

}
