package com.eafc26.discordstats.store

import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.MatchId
import com.eafc26.discordstats.explorer.ControlledCandidateIdentity
import com.eafc26.discordstats.explorer.ControlledExperimentType
import com.eafc26.discordstats.explorer.ControlledObservation
import com.eafc26.discordstats.explorer.ControlledObservationRepository
import com.eafc26.discordstats.explorer.ObservationCompleteness
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.ResultSet

class PostgresControlledObservationRepository(
    private val jdbcTemplate: JdbcTemplate,
) : ControlledObservationRepository {
    override fun saveIfAbsent(observation: ControlledObservation): ControlledObservation = jdbcTemplate.queryForObject(
        """
        INSERT INTO explorer_controlled_observations
          (club_id, match_id, player_id, phrase, observed_count, completeness, aggregate_index, code, experiment_type, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, now())
        ON CONFLICT (club_id, match_id, player_id, phrase, aggregate_index, code) DO UPDATE
          SET experiment_type = explorer_controlled_observations.experiment_type
        RETURNING club_id, match_id, player_id, phrase, observed_count, completeness, aggregate_index, code, experiment_type, created_at
        """.trimIndent(),
        { rs, _ -> read(rs) },
        observation.clubId.value, observation.matchId.value, observation.playerId, observation.phrase,
        observation.observedCount, observation.completeness.name, observation.aggregateIndex, observation.code, observation.experimentType.name,
    )!!

    override fun findForCandidates(
        clubId: ClubId,
        candidates: Collection<ControlledCandidateIdentity>,
        limitPerCandidate: Int,
    ): List<ControlledObservation> {
        require(candidates.size <= 10) { "controlled candidate batch limited to 10" }
        require(limitPerCandidate in 1..5) { "controlled evidence limit must be 1-5" }
        if (candidates.isEmpty()) return emptyList()
        val unique = candidates.toSet()
        val values = unique.joinToString(", ") { "(?, ?, ?, ?)" }
        val args = mutableListOf<Any>()
        unique.forEach { args.addAll(listOf(it.playerId, it.phrase, it.aggregateIndex, it.code)) }
        args.add(clubId.value)
        args.add(limitPerCandidate)
        return jdbcTemplate.query(
            """
            WITH requested(player_id, phrase, aggregate_index, code) AS (VALUES $values), ranked AS (
              SELECT co.*, row_number() OVER (
                PARTITION BY co.player_id, co.phrase, co.aggregate_index, co.code
                ORDER BY co.created_at DESC, co.id DESC
              ) AS row_number
              FROM explorer_controlled_observations co
              JOIN requested r USING (player_id, phrase, aggregate_index, code)
              WHERE co.club_id = ?
            )
            SELECT club_id, match_id, player_id, phrase, observed_count, completeness, aggregate_index, code, experiment_type, created_at
            FROM ranked WHERE row_number <= ?
            ORDER BY created_at DESC, match_id ASC
            """.trimIndent(),
            { rs, _ -> read(rs) }, *args.toTypedArray(),
        )
    }

    private fun read(rs: ResultSet) = ControlledObservation(
        clubId = ClubId(rs.getString("club_id")),
        matchId = MatchId(rs.getString("match_id")),
        playerId = rs.getString("player_id"),
        phrase = rs.getString("phrase"),
        observedCount = rs.getInt("observed_count"),
        completeness = ObservationCompleteness.valueOf(rs.getString("completeness")),
        aggregateIndex = rs.getInt("aggregate_index"),
        code = rs.getInt("code"),
        experimentType = ControlledExperimentType.valueOf(rs.getString("experiment_type")),
        createdAt = rs.getTimestamp("created_at").toInstant(),
    )
}
