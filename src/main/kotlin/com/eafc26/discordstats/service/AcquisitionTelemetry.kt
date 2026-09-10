package com.eafc26.discordstats.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Emits bounded, payload-free acquisition telemetry. Its only purpose is to
 * make a match identifier traceable across the existing acquisition pipeline.
 */
interface AcquisitionTelemetry {
    fun batch(event: AcquisitionBatchTelemetry)
    fun normalizationRejected(event: NormalizationRejectedTelemetry)
    fun canonicalPersisted(event: CanonicalPersistenceTelemetry)
}

data class AcquisitionBatchTelemetry(
    val clubId: String,
    val trigger: String,
    val window: Int?,
    val receivedMatchIds: List<String>,
    val alreadyExistingMatchIds: List<String>,
    val newMatchIds: List<String>,
)

data class NormalizationRejectedTelemetry(
    val clubId: String,
    val matchId: String,
    val reason: String,
)

data class CanonicalPersistenceTelemetry(
    val clubId: String,
    val matchId: String,
)

@Component
class Slf4jAcquisitionTelemetry : AcquisitionTelemetry {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun batch(event: AcquisitionBatchTelemetry) {
        log.info(
            "EA_ACQUISITION_BATCH clubId={} trigger={} window={} receivedMatchIds={} alreadyExistingMatchIds={} newMatchIds={}",
            event.clubId,
            event.trigger,
            event.window,
            event.receivedMatchIds,
            event.alreadyExistingMatchIds,
            event.newMatchIds,
        )
    }

    override fun normalizationRejected(event: NormalizationRejectedTelemetry) {
        log.warn(
            "EA_NORMALIZATION_REJECTED clubId={} matchId={} reason={}",
            event.clubId,
            event.matchId,
            event.reason,
        )
    }

    override fun canonicalPersisted(event: CanonicalPersistenceTelemetry) {
        log.info("EA_CANONICAL_PERSISTED clubId={} matchId={}", event.clubId, event.matchId)
    }
}

object NoopAcquisitionTelemetry : AcquisitionTelemetry {
    override fun batch(event: AcquisitionBatchTelemetry) = Unit
    override fun normalizationRejected(event: NormalizationRejectedTelemetry) = Unit
    override fun canonicalPersisted(event: CanonicalPersistenceTelemetry) = Unit
}
