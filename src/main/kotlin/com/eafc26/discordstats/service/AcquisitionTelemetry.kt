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
    val gameVersion: String = "FC26",
    val trigger: String,
    val window: Int?,
    val receivedMatchIds: List<String>,
    val alreadyExistingMatchIds: List<String>,
    val newMatchIds: List<String>,
    val sourceMatchTypes: List<String> = emptyList(),
)

data class NormalizationRejectedTelemetry(
    val clubId: String,
    val matchId: String,
    val reason: String,
    val gameVersion: String = "FC26",
)

data class CanonicalPersistenceTelemetry(
    val clubId: String,
    val matchId: String,
    val gameVersion: String = "FC26",
)

@Component
class Slf4jAcquisitionTelemetry : AcquisitionTelemetry {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun batch(event: AcquisitionBatchTelemetry) {
        log.info(
            "EA_ACQUISITION_BATCH clubId={} gameVersion={} trigger={} window={} sourceMatchTypes={} receivedMatchIds={} alreadyExistingMatchIds={} newMatchIds={}",
            event.clubId,
            event.gameVersion,
            event.trigger,
            event.window,
            event.sourceMatchTypes,
            event.receivedMatchIds,
            event.alreadyExistingMatchIds,
            event.newMatchIds,
        )
    }

    override fun normalizationRejected(event: NormalizationRejectedTelemetry) {
        log.warn(
            "EA_NORMALIZATION_REJECTED clubId={} gameVersion={} matchId={} reason={}",
            event.clubId,
            event.gameVersion,
            event.matchId,
            event.reason,
        )
    }

    override fun canonicalPersisted(event: CanonicalPersistenceTelemetry) {
        log.info("EA_CANONICAL_PERSISTED clubId={} gameVersion={} matchId={}", event.clubId, event.gameVersion, event.matchId)
    }
}

object NoopAcquisitionTelemetry : AcquisitionTelemetry {
    override fun batch(event: AcquisitionBatchTelemetry) = Unit
    override fun normalizationRejected(event: NormalizationRejectedTelemetry) = Unit
    override fun canonicalPersisted(event: CanonicalPersistenceTelemetry) = Unit
}
