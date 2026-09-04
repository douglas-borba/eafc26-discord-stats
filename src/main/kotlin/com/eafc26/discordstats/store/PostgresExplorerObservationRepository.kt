package com.eafc26.discordstats.store

import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.MatchId
import com.eafc26.discordstats.explorer.ExplorerObservation
import com.eafc26.discordstats.explorer.ExplorerObservationRepository
import com.eafc26.discordstats.explorer.ObservationCompleteness
import com.eafc26.discordstats.explorer.ObservationIdentityKey
import com.eafc26.discordstats.explorer.ObservationPhraseReconciliationResult
import com.eafc26.discordstats.explorer.ObservationPhraseReconciliationStatus
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.support.TransactionTemplate
import java.sql.ResultSet
import java.time.Instant

class PostgresExplorerObservationRepository(
    private val jdbcTemplate: JdbcTemplate,
    private val transactions: TransactionTemplate? = null,
) : ExplorerObservationRepository {
    override fun save(observation: ExplorerObservation): ExplorerObservation = jdbcTemplate.queryForObject(
        """
        INSERT INTO explorer_observations
            (club_id, match_id, player_id, phrase, observed_count, completeness, note, observed_position_context, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, now(), now())
        ON CONFLICT (club_id, match_id, player_id, phrase) DO UPDATE SET
            observed_count = EXCLUDED.observed_count,
            completeness = EXCLUDED.completeness,
            note = EXCLUDED.note,
            observed_position_context = EXCLUDED.observed_position_context,
            updated_at = now()
        RETURNING club_id, match_id, player_id, phrase, observed_count, completeness, note, observed_position_context, created_at, updated_at
        """.trimIndent(),
        { rs, _ -> read(rs) },
        observation.clubId.value,
        observation.matchId.value,
        observation.playerId,
        observation.phrase,
        observation.observedCount,
        observation.completeness.name,
        observation.note,
        observation.observedPositionContext,
    )!!

    override fun findForPlayerMatch(clubId: ClubId, matchId: MatchId, playerId: String): List<ExplorerObservation> =
        jdbcTemplate.query(
            """
            SELECT club_id, match_id, player_id, phrase, observed_count, completeness, note, observed_position_context, created_at, updated_at
            FROM explorer_observations
            WHERE club_id = ? AND match_id = ? AND player_id = ?
            ORDER BY phrase ASC
            """.trimIndent(),
            { rs, _ -> read(rs) },
            clubId.value, matchId.value, playerId,
        )

    override fun reconcilePhrase(
        clubId: ClubId,
        matchId: MatchId,
        playerId: String,
        sourcePhrase: String,
        targetPhrase: String,
    ): ObservationPhraseReconciliationResult {
        val source = findExact(clubId, matchId, playerId, sourcePhrase)
            ?: return ObservationPhraseReconciliationResult(ObservationPhraseReconciliationStatus.SOURCE_NOT_FOUND)
        if (sourcePhrase == targetPhrase) {
            return ObservationPhraseReconciliationResult(ObservationPhraseReconciliationStatus.NO_CHANGE, observation = source)
        }
        val target = findExact(clubId, matchId, playerId, targetPhrase)
        if (target != null) {
            return ObservationPhraseReconciliationResult(
                ObservationPhraseReconciliationStatus.TARGET_ALREADY_EXISTS,
                existingTarget = target,
            )
        }

        return try {
            val updated = jdbcTemplate.query(
                """
                UPDATE explorer_observations
                SET phrase = ?, updated_at = now()
                WHERE club_id = ? AND match_id = ? AND player_id = ? AND phrase = ?
                  AND NOT EXISTS (
                    SELECT 1
                    FROM explorer_observations
                    WHERE club_id = ? AND match_id = ? AND player_id = ? AND phrase = ?
                  )
                RETURNING club_id, match_id, player_id, phrase, observed_count, completeness, note, observed_position_context, created_at, updated_at
                """.trimIndent(),
                { rs, _ -> read(rs) },
                targetPhrase,
                clubId.value, matchId.value, playerId, sourcePhrase,
                clubId.value, matchId.value, playerId, targetPhrase,
            ).singleOrNull()
            if (updated != null) {
                ObservationPhraseReconciliationResult(ObservationPhraseReconciliationStatus.SUCCESS, observation = updated)
            } else {
                reconciliationFailureAfterConcurrentChange(clubId, matchId, playerId, sourcePhrase, targetPhrase)
            }
        } catch (_: DataIntegrityViolationException) {
            // The unique index remains the final concurrent-write guard. A failed
            // statement leaves the source untouched; re-read only this identity.
            reconciliationFailureAfterConcurrentChange(clubId, matchId, playerId, sourcePhrase, targetPhrase)
        }
    }

    override fun findForPlayerPhrase(clubId: ClubId, playerId: String, phrase: String, limit: Int): List<ExplorerObservation> {
        require(limit in 1..50) { "limit must be 1-50" }
        return jdbcTemplate.query(
            """
            SELECT club_id, match_id, player_id, phrase, observed_count, completeness, note, observed_position_context, created_at, updated_at
            FROM explorer_observations
            WHERE club_id = ? AND player_id = ? AND phrase = ?
            ORDER BY updated_at DESC, id DESC
            LIMIT ?
            """.trimIndent(),
            { rs, _ -> read(rs) },
            clubId.value, playerId, phrase, limit,
        )
    }

    override fun findForPlayer(clubId: ClubId, playerId: String, limit: Int): List<ExplorerObservation> {
        require(limit in 1..50) { "limit must be 1-50" }
        return jdbcTemplate.query(
            """
            SELECT club_id, match_id, player_id, phrase, observed_count, completeness, note, observed_position_context, created_at, updated_at
            FROM explorer_observations
            WHERE club_id = ? AND player_id = ?
            ORDER BY updated_at DESC, id DESC
            LIMIT ?
            """.trimIndent(),
            { rs, _ -> read(rs) },
            clubId.value, playerId, limit,
        )
    }

    override fun findByIdentities(clubId: ClubId, keys: Collection<ObservationIdentityKey>): List<ExplorerObservation> {
        require(keys.size <= 50) { "batch lookup limited to 50 keys" }
        if (keys.isEmpty()) return emptyList()
        val uniqueKeys = keys.toSet()
        val conditions = uniqueKeys.joinToString(" OR ") { "( match_id = ? AND player_id = ? AND phrase = ? )" }
        val params = mutableListOf<Any>(clubId.value)
        uniqueKeys.forEach { key -> params.addAll(listOf(key.matchId.value, key.playerId, key.phrase)) }
        return jdbcTemplate.query(
            """
            SELECT club_id, match_id, player_id, phrase, observed_count, completeness, note, observed_position_context, created_at, updated_at
            FROM explorer_observations
            WHERE club_id = ? AND ($conditions)
            """.trimIndent(),
            { rs, _ -> read(rs) },
            *params.toTypedArray(),
        )
    }

    override fun insertIfAbsent(clubId: ClubId, observations: List<ExplorerObservation>): Int {
        require(observations.size <= 50) { "batch insert limited to 50 observations" }
        require(observations.all { it.clubId == clubId }) { "all observations must belong to the same club" }
        if (observations.isEmpty()) return 0
        val tx = requireNotNull(transactions) { "TransactionTemplate required for atomic bulk insert" }
        return tx.execute { _ ->
            var inserted = 0
            for (observation in observations) {
                val rows = jdbcTemplate.update(
                    """
                    INSERT INTO explorer_observations
                        (club_id, match_id, player_id, phrase, observed_count, completeness, note, observed_position_context, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, now(), now())
                    ON CONFLICT (club_id, match_id, player_id, phrase) DO NOTHING
                    """.trimIndent(),
                    observation.clubId.value,
                    observation.matchId.value,
                    observation.playerId,
                    observation.phrase,
                    observation.observedCount,
                    observation.completeness.name,
                    observation.note,
                    observation.observedPositionContext,
                )
                inserted += rows
            }
            if (inserted != observations.size) {
                throw IllegalStateException(
                    "Concurrent conflict detected: expected ${observations.size} inserts but $inserted succeeded. " +
                        "Another request may have inserted observations after preview. No records were written.",
                )
            }
            inserted
        }!!
    }

    private fun reconciliationFailureAfterConcurrentChange(
        clubId: ClubId,
        matchId: MatchId,
        playerId: String,
        sourcePhrase: String,
        targetPhrase: String,
    ): ObservationPhraseReconciliationResult {
        val target = findExact(clubId, matchId, playerId, targetPhrase)
        if (target != null) {
            return ObservationPhraseReconciliationResult(
                ObservationPhraseReconciliationStatus.TARGET_ALREADY_EXISTS,
                existingTarget = target,
            )
        }
        val source = findExact(clubId, matchId, playerId, sourcePhrase)
        return if (source == null) {
            ObservationPhraseReconciliationResult(ObservationPhraseReconciliationStatus.SOURCE_NOT_FOUND)
        } else {
            throw IllegalStateException("Observation phrase reconciliation did not complete")
        }
    }

    private fun findExact(
        clubId: ClubId,
        matchId: MatchId,
        playerId: String,
        phrase: String,
    ): ExplorerObservation? = jdbcTemplate.query(
        """
        SELECT club_id, match_id, player_id, phrase, observed_count, completeness, note, observed_position_context, created_at, updated_at
        FROM explorer_observations
        WHERE club_id = ? AND match_id = ? AND player_id = ? AND phrase = ?
        """.trimIndent(),
        { rs, _ -> read(rs) },
        clubId.value, matchId.value, playerId, phrase,
    ).singleOrNull()

    private fun read(rs: ResultSet) = ExplorerObservation(
        clubId = ClubId(rs.getString("club_id")),
        matchId = MatchId(rs.getString("match_id")),
        playerId = rs.getString("player_id"),
        phrase = rs.getString("phrase"),
        observedCount = rs.getInt("observed_count"),
        completeness = ObservationCompleteness.valueOf(rs.getString("completeness")),
        note = rs.getString("note"),
        observedPositionContext = rs.getString("observed_position_context"),
        createdAt = rs.getTimestamp("created_at")?.toInstant(),
        updatedAt = rs.getTimestamp("updated_at")?.toInstant(),
    )
}
