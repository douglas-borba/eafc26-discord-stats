package com.eafc26.discordstats.store

import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.MatchId
import com.eafc26.discordstats.explorer.ExplorerObservation
import com.eafc26.discordstats.explorer.ExplorerObservationRepository
import com.eafc26.discordstats.explorer.ObservationCompleteness
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.ResultSet
import java.time.Instant

class PostgresExplorerObservationRepository(
    private val jdbcTemplate: JdbcTemplate,
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
