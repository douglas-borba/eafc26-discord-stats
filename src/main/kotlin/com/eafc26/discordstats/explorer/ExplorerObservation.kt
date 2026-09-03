package com.eafc26.discordstats.explorer

import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.MatchId
import java.time.Instant

/**
 * User-authored evidence for internal reverse engineering. It is deliberately
 * separate from canonical EA match facts and does not assign sporting meaning.
 */
data class ExplorerObservation(
    val clubId: ClubId,
    val matchId: MatchId,
    val playerId: String,
    val phrase: String,
    val observedCount: Int,
    val completeness: ObservationCompleteness = ObservationCompleteness.AT_LEAST,
    val note: String? = null,
    val observedPositionContext: String? = null,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
) {
    init {
        require(phrase.isNotBlank()) { "phrase must not be blank" }
        require(observedCount >= 0) { "observedCount must be non-negative" }
    }
}

enum class ObservationCompleteness {
    /** The player may have missed messages while playing; observed count is a lower bound. */
    AT_LEAST,
    /** Only for a recording/replay that captured every observed occurrence. */
    EXACT,
}

interface ExplorerObservationRepository {
    /**
     * One row per exact club/match/player/phrase. Saving updates the same
     * annotation instead of merging phrases or inventing semantic aliases.
     */
    fun save(observation: ExplorerObservation): ExplorerObservation

    fun findForPlayerMatch(clubId: ClubId, matchId: MatchId, playerId: String): List<ExplorerObservation>

    /** A bounded annotated investigation set, never a full canonical scan. */
    fun findForPlayerPhrase(clubId: ClubId, playerId: String, phrase: String, limit: Int): List<ExplorerObservation>

    /**
     * Bounded cross-phrase evidence for one player. This supports mechanical
     * collision analysis without scanning canonical history or querying one
     * phrase at a time.
     */
    fun findForPlayer(clubId: ClubId, playerId: String, limit: Int): List<ExplorerObservation>
}

/** Test/local fallback only. Production wires the PostgreSQL implementation. */
class InMemoryExplorerObservationRepository : ExplorerObservationRepository {
    private val observations = linkedMapOf<List<String>, ExplorerObservation>()

    override fun save(observation: ExplorerObservation): ExplorerObservation {
        val key = listOf(observation.clubId.value, observation.matchId.value, observation.playerId, observation.phrase)
        val previous = observations[key]
        val stored = observation.copy(
            createdAt = previous?.createdAt ?: observation.createdAt ?: Instant.now(),
            updatedAt = Instant.now(),
        )
        observations[key] = stored
        return stored
    }

    override fun findForPlayerMatch(clubId: ClubId, matchId: MatchId, playerId: String): List<ExplorerObservation> =
        observations.values.filter { it.clubId == clubId && it.matchId == matchId && it.playerId == playerId }
            .sortedBy { it.phrase }

    override fun findForPlayerPhrase(clubId: ClubId, playerId: String, phrase: String, limit: Int): List<ExplorerObservation> =
        observations.values.filter { it.clubId == clubId && it.playerId == playerId && it.phrase == phrase }
            .sortedByDescending { it.updatedAt }
            .take(limit)

    override fun findForPlayer(clubId: ClubId, playerId: String, limit: Int): List<ExplorerObservation> {
        require(limit in 1..50) { "limit must be 1-50" }
        return observations.values.filter { it.clubId == clubId && it.playerId == playerId }
            .sortedWith(compareByDescending<ExplorerObservation> { it.updatedAt }.thenByDescending { it.createdAt })
            .take(limit)
    }
}
