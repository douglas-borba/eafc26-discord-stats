package com.eafc26.discordstats.store

import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.GameVersion
import com.eafc26.discordstats.domain.match.MatchId
import com.eafc26.discordstats.explorer.ExplorerObservation
import com.eafc26.discordstats.explorer.ExplorerObservationRepository
import com.eafc26.discordstats.explorer.ObservationCompleteness
import com.eafc26.discordstats.explorer.ObservationIdentityKey
import com.eafc26.discordstats.explorer.ObservationPhraseReconciliationResult
import com.eafc26.discordstats.explorer.ObservationPhraseReconciliationStatus
import com.eafc26.discordstats.explorer.ObservationResearchIdentity
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
            (game_version, club_id, match_id, player_id, phrase, observed_count, completeness, note, observed_position_context, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
        ON CONFLICT (game_version, club_id, match_id, player_id, phrase) DO UPDATE SET
            observed_count = EXCLUDED.observed_count,
            completeness = EXCLUDED.completeness,
            note = EXCLUDED.note,
            observed_position_context = EXCLUDED.observed_position_context,
            updated_at = now()
        RETURNING game_version, club_id, match_id, player_id, phrase, observed_count, completeness, note, observed_position_context, created_at, updated_at
        """.trimIndent(),
        { rs, _ -> read(rs) },
        observation.gameVersion.name,
        observation.clubId.value,
        observation.matchId.value,
        observation.playerId,
        observation.phrase,
        observation.observedCount,
        observation.completeness.name,
        observation.note,
        observation.observedPositionContext,
    )!!

    override fun findForPlayerMatch(
        clubId: ClubId,
        matchId: MatchId,
        playerId: String,
        gameVersion: GameVersion,
    ): List<ExplorerObservation> {
        return jdbcTemplate.query(
            """
            SELECT game_version, club_id, match_id, player_id, phrase, observed_count, completeness, note, observed_position_context, created_at, updated_at
            FROM explorer_observations
            WHERE game_version = ? AND club_id = ? AND match_id = ? AND player_id = ?
            ORDER BY phrase ASC
            """.trimIndent(),
            { rs, _ -> read(rs) },
            gameVersion.name, clubId.value, matchId.value, playerId,
        )
    }

    override fun findForPlayerMatchLimited(
        clubId: ClubId,
        matchId: MatchId,
        playerId: String,
        limit: Int,
        gameVersion: GameVersion,
    ): List<ExplorerObservation> {
        require(limit in 1..101) { "limit must be 1-101" }
        return jdbcTemplate.query(
            """
            SELECT game_version, club_id, match_id, player_id, phrase, observed_count, completeness, note, observed_position_context, created_at, updated_at
            FROM explorer_observations
            WHERE game_version = ? AND club_id = ? AND match_id = ? AND player_id = ?
            ORDER BY phrase ASC
            LIMIT ?
            """.trimIndent(),
            { rs, _ -> read(rs) },
            gameVersion.name, clubId.value, matchId.value, playerId, limit,
        )
    }

    override fun findExact(
        clubId: ClubId,
        matchId: MatchId,
        playerId: String,
        phrase: String,
        gameVersion: GameVersion,
    ): ExplorerObservation? = jdbcTemplate.query(
        """
        SELECT game_version, club_id, match_id, player_id, phrase, observed_count, completeness, note, observed_position_context, created_at, updated_at
        FROM explorer_observations
        WHERE game_version = ? AND club_id = ? AND match_id = ? AND player_id = ? AND phrase = ?
        LIMIT 1
        """.trimIndent(),
        { rs, _ -> read(rs) },
        gameVersion.name, clubId.value, matchId.value, playerId, phrase,
    ).singleOrNull()

    override fun reconcilePhrase(
        clubId: ClubId,
        matchId: MatchId,
        playerId: String,
        sourcePhrase: String,
        targetPhrase: String,
        gameVersion: GameVersion,
    ): ObservationPhraseReconciliationResult {
        val source = findExact(clubId, matchId, playerId, sourcePhrase, gameVersion)
            ?: return ObservationPhraseReconciliationResult(ObservationPhraseReconciliationStatus.SOURCE_NOT_FOUND)
        if (sourcePhrase == targetPhrase) {
            return ObservationPhraseReconciliationResult(ObservationPhraseReconciliationStatus.NO_CHANGE, observation = source)
        }
        val target = findExact(clubId, matchId, playerId, targetPhrase, gameVersion)
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
                WHERE game_version = ? AND club_id = ? AND match_id = ? AND player_id = ? AND phrase = ?
                  AND NOT EXISTS (
                    SELECT 1
                    FROM explorer_observations
                    WHERE game_version = ? AND club_id = ? AND match_id = ? AND player_id = ? AND phrase = ?
                  )
                RETURNING game_version, club_id, match_id, player_id, phrase, observed_count, completeness, note, observed_position_context, created_at, updated_at
                """.trimIndent(),
                { rs, _ -> read(rs) },
                targetPhrase,
                gameVersion.name, clubId.value, matchId.value, playerId, sourcePhrase,
                gameVersion.name, clubId.value, matchId.value, playerId, targetPhrase,
            ).singleOrNull()
            if (updated != null) {
                ObservationPhraseReconciliationResult(ObservationPhraseReconciliationStatus.SUCCESS, observation = updated)
            } else {
                reconciliationFailureAfterConcurrentChange(clubId, matchId, playerId, sourcePhrase, targetPhrase, gameVersion)
            }
        } catch (_: DataIntegrityViolationException) {
            // The unique index remains the final concurrent-write guard. A failed
            // statement leaves the source untouched; re-read only this identity.
            reconciliationFailureAfterConcurrentChange(clubId, matchId, playerId, sourcePhrase, targetPhrase, gameVersion)
        }
    }

    override fun findForPlayerPhrase(clubId: ClubId, playerId: String, phrase: String, limit: Int, gameVersion: GameVersion): List<ExplorerObservation> {
        require(limit in 1..50) { "limit must be 1-50" }
        return jdbcTemplate.query(
            """
            SELECT game_version, club_id, match_id, player_id, phrase, observed_count, completeness, note, observed_position_context, created_at, updated_at
            FROM explorer_observations
            WHERE game_version = ? AND club_id = ? AND player_id = ? AND phrase = ?
            ORDER BY updated_at DESC, id DESC
            LIMIT ?
            """.trimIndent(),
            { rs, _ -> read(rs) },
            gameVersion.name, clubId.value, playerId, phrase, limit,
        )
    }

    override fun findForPlayer(clubId: ClubId, playerId: String, limit: Int, gameVersion: GameVersion): List<ExplorerObservation> {
        require(limit in 1..50) { "limit must be 1-50" }
        return jdbcTemplate.query(
            """
            SELECT game_version, club_id, match_id, player_id, phrase, observed_count, completeness, note, observed_position_context, created_at, updated_at
            FROM explorer_observations
            WHERE game_version = ? AND club_id = ? AND player_id = ?
            ORDER BY updated_at DESC, id DESC
            LIMIT ?
            """.trimIndent(),
            { rs, _ -> read(rs) },
            gameVersion.name, clubId.value, playerId, limit,
        )
    }

    override fun findRecentResearchIdentities(clubId: ClubId, limit: Int, gameVersion: GameVersion): List<ObservationResearchIdentity> {
        require(limit in 1..41) { "limit must be 1-41" }
        return jdbcTemplate.query(
            """
            SELECT player_id, phrase
            FROM explorer_observations
            WHERE game_version = ? AND club_id = ?
            GROUP BY player_id, phrase
            ORDER BY MAX(updated_at) DESC, player_id ASC, phrase ASC
            LIMIT ?
            """.trimIndent(),
            { rs, _ -> ObservationResearchIdentity(rs.getString("player_id"), rs.getString("phrase")) },
            gameVersion.name, clubId.value, limit,
        )
    }

    override fun findRecentForResearchIdentities(
        clubId: ClubId,
        identities: Collection<ObservationResearchIdentity>,
        limit: Int,
        gameVersion: GameVersion,
    ): List<ExplorerObservation> {
        require(identities.size <= 40) { "research identity batch limited to 40" }
        require(limit in 1..21) { "per-identity research evidence limit must be 1-21" }
        if (identities.isEmpty()) return emptyList()
        val uniqueIdentities = identities.toSet()
        val values = uniqueIdentities.joinToString(", ") { "(?, ?)" }
        val parameters = mutableListOf<Any>()
        uniqueIdentities.forEach { identity -> parameters.addAll(listOf(identity.playerId, identity.phrase)) }
        parameters += gameVersion.name
        parameters += clubId.value
        parameters += limit
        return jdbcTemplate.query(
            """
            WITH requested(player_id, phrase) AS (
                VALUES $values
            ), ranked AS (
                SELECT
                    eo.game_version,
                    eo.club_id,
                    eo.match_id,
                    eo.player_id,
                    eo.phrase,
                    eo.observed_count,
                    eo.completeness,
                    eo.note,
                    eo.observed_position_context,
                    eo.created_at,
                    eo.updated_at,
                    eo.id AS row_id,
                    ROW_NUMBER() OVER (
                        PARTITION BY eo.player_id, eo.phrase
                        ORDER BY eo.updated_at DESC, eo.id DESC
                    ) AS evidence_rank
                FROM explorer_observations eo
                INNER JOIN requested requested_identity
                    ON requested_identity.player_id = eo.player_id
                   AND requested_identity.phrase = eo.phrase
                WHERE eo.game_version = ? AND eo.club_id = ?
            )
            SELECT game_version, club_id, match_id, player_id, phrase, observed_count, completeness, note, observed_position_context, created_at, updated_at
            FROM ranked
            WHERE evidence_rank <= ?
            ORDER BY player_id ASC, phrase ASC, updated_at DESC, row_id DESC
            """.trimIndent(),
            { rs, _ -> read(rs) },
            *parameters.toTypedArray(),
        )
    }

    override fun findByIdentities(clubId: ClubId, keys: Collection<ObservationIdentityKey>, gameVersion: GameVersion): List<ExplorerObservation> {
        require(keys.size <= 50) { "batch lookup limited to 50 keys" }
        if (keys.isEmpty()) return emptyList()
        val uniqueKeys = keys.toSet()
        val conditions = uniqueKeys.joinToString(" OR ") { "( match_id = ? AND player_id = ? AND phrase = ? )" }
        val params = mutableListOf<Any>(gameVersion.name, clubId.value)
        uniqueKeys.forEach { key -> params.addAll(listOf(key.matchId.value, key.playerId, key.phrase)) }
        return jdbcTemplate.query(
            """
            SELECT game_version, club_id, match_id, player_id, phrase, observed_count, completeness, note, observed_position_context, created_at, updated_at
            FROM explorer_observations
            WHERE game_version = ? AND club_id = ? AND ($conditions)
            """.trimIndent(),
            { rs, _ -> read(rs) },
            *params.toTypedArray(),
        )
    }

    override fun insertIfAbsent(clubId: ClubId, observations: List<ExplorerObservation>, gameVersion: GameVersion): Int {
        require(observations.size <= 50) { "batch insert limited to 50 observations" }
        require(observations.all { it.clubId == clubId }) { "all observations must belong to the same club" }
        if (observations.isEmpty()) return 0
        val tx = requireNotNull(transactions) { "TransactionTemplate required for atomic bulk insert" }
        return tx.execute { _ ->
            var inserted = 0
            for (observation in observations.map { it.copy(gameVersion = gameVersion) }) {
                val rows = jdbcTemplate.update(
                    """
                    INSERT INTO explorer_observations
                        (game_version, club_id, match_id, player_id, phrase, observed_count, completeness, note, observed_position_context, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
                    ON CONFLICT (game_version, club_id, match_id, player_id, phrase) DO NOTHING
                    """.trimIndent(),
                    observation.gameVersion.name,
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
        gameVersion: GameVersion,
    ): ObservationPhraseReconciliationResult {
        val target = findExact(clubId, matchId, playerId, targetPhrase, gameVersion)
        if (target != null) {
            return ObservationPhraseReconciliationResult(
                ObservationPhraseReconciliationStatus.TARGET_ALREADY_EXISTS,
                existingTarget = target,
            )
        }
        val source = findExact(clubId, matchId, playerId, sourcePhrase, gameVersion)
        return if (source == null) {
            ObservationPhraseReconciliationResult(ObservationPhraseReconciliationStatus.SOURCE_NOT_FOUND)
        } else {
            throw IllegalStateException("Observation phrase reconciliation did not complete")
        }
    }

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
        gameVersion = rs.getString("game_version")?.let(GameVersion::valueOf) ?: GameVersion.FC26,
    )
}
