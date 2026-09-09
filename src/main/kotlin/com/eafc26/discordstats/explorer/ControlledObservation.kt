package com.eafc26.discordstats.explorer

import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.MatchId
import java.time.Instant

/**
 * Provenance for a deliberate, targeted check of one literal EA feedback
 * phrase against one RAW aggregate identity. It is deliberately separate from
 * [ExplorerObservation]: historical human annotations remain passive evidence.
 */
data class ControlledObservation(
    val clubId: ClubId,
    val matchId: MatchId,
    val playerId: String,
    val phrase: String,
    val observedCount: Int,
    val completeness: ObservationCompleteness,
    val aggregateIndex: Int,
    val code: Int,
    val experimentType: ControlledExperimentType,
    val createdAt: Instant? = null,
) {
    init {
        require(phrase.isNotBlank()) { "phrase must not be blank" }
        require(observedCount >= 0) { "observedCount must be non-negative" }
        require(aggregateIndex in 0..3) { "aggregateIndex must be 0-3" }
        require(code >= 0) { "code must be non-negative" }
    }
}

enum class ControlledExperimentType { COUNT_MATCH, DISCRIMINATION }

data class ControlledCandidateIdentity(
    val playerId: String,
    val phrase: String,
    val aggregateIndex: Int,
    val code: Int,
)

interface ControlledObservationRepository {
    fun saveIfAbsent(observation: ControlledObservation): ControlledObservation

    /** One bounded batched lookup for the queue's selected hypotheses. */
    fun findForCandidates(
        clubId: ClubId,
        candidates: Collection<ControlledCandidateIdentity>,
        limitPerCandidate: Int,
    ): List<ControlledObservation>
}

class InMemoryControlledObservationRepository : ControlledObservationRepository {
    private val observations = linkedMapOf<List<String>, ControlledObservation>()

    override fun saveIfAbsent(observation: ControlledObservation): ControlledObservation {
        val key = listOf(
            observation.clubId.value,
            observation.matchId.value,
            observation.playerId,
            observation.phrase,
            observation.aggregateIndex.toString(),
            observation.code.toString(),
        )
        return observations.getOrPut(key) { observation.copy(createdAt = observation.createdAt ?: Instant.now()) }
    }

    override fun findForCandidates(
        clubId: ClubId,
        candidates: Collection<ControlledCandidateIdentity>,
        limitPerCandidate: Int,
    ): List<ControlledObservation> {
        require(candidates.size <= 10) { "controlled candidate batch limited to 10" }
        require(limitPerCandidate in 1..5) { "controlled evidence limit must be 1-5" }
        val requested = candidates.toSet()
        return observations.values.filter {
            it.clubId == clubId && ControlledCandidateIdentity(it.playerId, it.phrase, it.aggregateIndex, it.code) in requested
        }.groupBy { ControlledCandidateIdentity(it.playerId, it.phrase, it.aggregateIndex, it.code) }
            .values.flatMap { rows -> rows.sortedByDescending { it.createdAt }.take(limitPerCandidate) }
    }
}
