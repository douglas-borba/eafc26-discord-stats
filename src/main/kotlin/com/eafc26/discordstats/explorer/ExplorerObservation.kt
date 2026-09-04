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

/**
 * Explicit result of an administrator-confirmed literal phrase reconciliation.
 * Phrase identity stays exact; this does not create semantic aliases.
 */
enum class ObservationPhraseReconciliationStatus {
    SUCCESS,
    SOURCE_NOT_FOUND,
    TARGET_ALREADY_EXISTS,
    INVALID_TARGET,
    NO_CHANGE,
}

data class ObservationPhraseReconciliationResult(
    val status: ObservationPhraseReconciliationStatus,
    /** The reconciled observation when [status] is [ObservationPhraseReconciliationStatus.SUCCESS]. */
    val observation: ExplorerObservation? = null,
    /** The exact target observation that blocked the reconciliation, when present. */
    val existingTarget: ExplorerObservation? = null,
)

interface ExplorerObservationRepository {
    /**
     * One row per exact club/match/player/phrase. Saving updates the same
     * annotation instead of merging phrases or inventing semantic aliases.
     */
    fun save(observation: ExplorerObservation): ExplorerObservation

    /** All observations recorded for one exact club/match/player identity. */
    fun findForPlayerMatch(
        clubId: ClubId,
        matchId: MatchId,
        playerId: String,
    ): List<ExplorerObservation>

    /**
     * Bounded evidence vector for one exact club/match/player identity.
     *
     * Production stores override this so the limit is applied by PostgreSQL;
     * the fallback keeps the same semantic order for local/test use.
     */
    fun findForPlayerMatchLimited(
        clubId: ClubId,
        matchId: MatchId,
        playerId: String,
        limit: Int,
    ): List<ExplorerObservation> {
        require(limit in 1..101) { "limit must be 1-101" }
        return findForPlayerMatch(clubId, matchId, playerId).take(limit)
    }

    /** One exact persisted evidence identity, without phrase normalization. */
    fun findExact(
        clubId: ClubId,
        matchId: MatchId,
        playerId: String,
        phrase: String,
    ): ExplorerObservation? =
        findForPlayerMatch(clubId, matchId, playerId).firstOrNull { it.phrase == phrase }

    /**
     * Changes only one exact phrase identity for one player-match. Implementations
     * must never merge counts and must leave both observations untouched on a
     * target identity collision.
     */
    fun reconcilePhrase(
        clubId: ClubId,
        matchId: MatchId,
        playerId: String,
        sourcePhrase: String,
        targetPhrase: String,
    ): ObservationPhraseReconciliationResult

    /** A bounded annotated investigation set, never a full canonical scan. */
    fun findForPlayerPhrase(clubId: ClubId, playerId: String, phrase: String, limit: Int): List<ExplorerObservation>

    /**
     * Bounded cross-phrase evidence for one player. This supports mechanical
     * collision analysis without scanning canonical history or querying one
     * phrase at a time.
     */
    fun findForPlayer(clubId: ClubId, playerId: String, limit: Int): List<ExplorerObservation>

    /**
     * Batch lookup by identity keys. Returns existing observations matching any
     * of the supplied (clubId, matchId, playerId, phrase) tuples. The input
     * collection must not exceed 50 entries.
     */
    fun findByIdentities(clubId: ClubId, keys: Collection<ObservationIdentityKey>): List<ExplorerObservation> {
        require(keys.size <= 50) { "batch lookup limited to 50 keys" }
        return keys.flatMap { key -> findForPlayerMatch(clubId, key.matchId, key.playerId).filter { it.phrase == key.phrase } }
    }

    /**
     * Inserts observations that do not yet exist. If any identity already exists,
     * aborts without writing. Returns the number of rows inserted.
     *
     * Default implementation calls [save] per row; production implementations
     * must use a single atomic transaction with INSERT ... ON CONFLICT DO NOTHING
     * and verify all expected rows were inserted.
     */
    fun insertIfAbsent(clubId: ClubId, observations: List<ExplorerObservation>): Int {
        require(observations.size <= 50) { "batch insert limited to 50 observations" }
        require(observations.all { it.clubId == clubId }) { "all observations must belong to the same club" }
        observations.forEach { save(it) }
        return observations.size
    }
}

data class ObservationIdentityKey(
    val matchId: MatchId,
    val playerId: String,
    val phrase: String,
)

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

    override fun findForPlayerMatch(
        clubId: ClubId,
        matchId: MatchId,
        playerId: String,
    ): List<ExplorerObservation> {
        return observations.values.filter { it.clubId == clubId && it.matchId == matchId && it.playerId == playerId }
            .sortedBy { it.phrase }
    }

    override fun findForPlayerMatchLimited(
        clubId: ClubId,
        matchId: MatchId,
        playerId: String,
        limit: Int,
    ): List<ExplorerObservation> {
        require(limit in 1..101) { "limit must be 1-101" }
        return findForPlayerMatch(clubId, matchId, playerId).take(limit)
    }

    override fun findExact(
        clubId: ClubId,
        matchId: MatchId,
        playerId: String,
        phrase: String,
    ): ExplorerObservation? = observations[listOf(clubId.value, matchId.value, playerId, phrase)]

    @Synchronized
    override fun reconcilePhrase(
        clubId: ClubId,
        matchId: MatchId,
        playerId: String,
        sourcePhrase: String,
        targetPhrase: String,
    ): ObservationPhraseReconciliationResult {
        val sourceKey = listOf(clubId.value, matchId.value, playerId, sourcePhrase)
        val source = observations[sourceKey]
            ?: return ObservationPhraseReconciliationResult(ObservationPhraseReconciliationStatus.SOURCE_NOT_FOUND)
        if (sourcePhrase == targetPhrase) {
            return ObservationPhraseReconciliationResult(ObservationPhraseReconciliationStatus.NO_CHANGE, observation = source)
        }
        val targetKey = listOf(clubId.value, matchId.value, playerId, targetPhrase)
        val target = observations[targetKey]
        if (target != null) {
            return ObservationPhraseReconciliationResult(
                ObservationPhraseReconciliationStatus.TARGET_ALREADY_EXISTS,
                existingTarget = target,
            )
        }

        val reconciled = source.copy(phrase = targetPhrase, updatedAt = Instant.now())
        observations.remove(sourceKey)
        observations[targetKey] = reconciled
        return ObservationPhraseReconciliationResult(
            ObservationPhraseReconciliationStatus.SUCCESS,
            observation = reconciled,
        )
    }

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

    override fun insertIfAbsent(clubId: ClubId, observations: List<ExplorerObservation>): Int {
        require(observations.size <= 50) { "batch insert limited to 50 observations" }
        require(observations.all { it.clubId == clubId }) { "all observations must belong to the same club" }
        var inserted = 0
        for (observation in observations) {
            val key = listOf(observation.clubId.value, observation.matchId.value, observation.playerId, observation.phrase)
            if (key !in this.observations) {
                val stored = observation.copy(
                    createdAt = observation.createdAt ?: Instant.now(),
                    updatedAt = Instant.now(),
                )
                this.observations[key] = stored
                inserted++
            }
        }
        if (inserted != observations.size) {
            error("Expected to insert ${observations.size} but $inserted were absent; aborting")
        }
        return inserted
    }
}
