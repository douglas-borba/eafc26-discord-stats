package com.eafc26.discordstats.store

import com.eafc26.discordstats.application.repository.PlayerProfileReadRepository
import com.eafc26.discordstats.diagnostics.CanonicalReadDiagnostics
import com.eafc26.discordstats.diagnostics.CanonicalReadOperation
import com.eafc26.discordstats.diagnostics.CanonicalReadOriginContext
import com.eafc26.discordstats.domain.interpretation.AwardType
import com.eafc26.discordstats.domain.interpretation.MatchOutcome
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.CompetitionType
import com.eafc26.discordstats.domain.match.MatchCompletion
import com.eafc26.discordstats.domain.match.MatchCompletionStatus
import com.eafc26.discordstats.domain.match.MatchId
import com.eafc26.discordstats.domain.match.PlayerId
import com.eafc26.discordstats.profile.PlayerProfileAppearance
import com.eafc26.discordstats.profile.PlayerProfileIndexEntry
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.ResultSet

/**
 * PostgreSQL projection for player profiles.
 *
 * Player statistics come from player_match_stats. The canonical join selects
 * only scalar match metadata and JSONB paths required for completion and the
 * persisted deterministic award winners; it never selects canonical payloads.
 */
class PostgresPlayerProfileReadRepository(
    private val jdbcTemplate: JdbcTemplate,
    private val readDiagnostics: CanonicalReadDiagnostics = CanonicalReadDiagnostics(),
    private val readOriginContext: CanonicalReadOriginContext = CanonicalReadOriginContext(),
) : PlayerProfileReadRepository {

    override fun findPlayerIndex(clubId: ClubId): List<PlayerProfileIndexEntry> {
        val rows = jdbcTemplate.query(
            """
            SELECT
                ps.player_id,
                COALESCE(
                    (array_agg(ps.pro_name ORDER BY ps.played_at DESC, ps.match_id ASC) FILTER (WHERE ps.pro_name IS NOT NULL))[1],
                    (array_agg(ps.platform_name ORDER BY ps.played_at DESC, ps.match_id ASC) FILTER (WHERE ps.platform_name IS NOT NULL))[1],
                    ps.player_id
                ) AS display_name,
                COUNT(*) AS match_count,
                MAX(ps.played_at) AS latest_match_at,
                AVG(ps.rating) AS average_rating,
                COUNT(ps.rating) AS rated_match_count
            FROM player_match_stats ps
            JOIN canonical_matches cm
              ON cm.club_id = ps.club_id
             AND cm.match_id = ps.match_id
            WHERE ps.club_id = ?
              AND COALESCE(cm.payload #>> '{footballMatch,completion,status}', 'UNKNOWN') <> 'DNF'
            GROUP BY ps.player_id
            ORDER BY MAX(ps.played_at) DESC, display_name ASC, ps.player_id ASC
            """.trimIndent(),
            { rs, _ ->
                PlayerProfileIndexEntry(
                    playerId = PlayerId(rs.getString("player_id")),
                    displayName = rs.getString("display_name"),
                    matchCount = rs.getInt("match_count"),
                    latestMatchAt = rs.getTimestamp("latest_match_at").toInstant(),
                    averageRating = rs.getBigDecimal("average_rating")?.setScale(2, java.math.RoundingMode.HALF_UP),
                    ratedMatchCount = rs.getInt("rated_match_count"),
                )
            },
            clubId.value,
        )
        readDiagnostics.record(
            CanonicalReadOperation.FIND_PLAYER_PROFILE_INDEX,
            readOriginContext.current(),
            rows.size,
            rows.sumOf { it.estimatedReturnedBytes() },
        )
        return rows
    }

    override fun findAppearances(clubId: ClubId): List<PlayerProfileAppearance> = query(clubId)

    override fun findAppearances(clubId: ClubId, playerId: PlayerId): List<PlayerProfileAppearance> =
        query(clubId, playerId)

    private fun query(clubId: ClubId, playerId: PlayerId? = null): List<PlayerProfileAppearance> {
        val playerFilter = if (playerId == null) "" else "AND ps.player_id = ?"
        val parameters = buildList {
            add(clubId.value)
            playerId?.let { add(it.value) }
        }.toTypedArray()
        val rows = jdbcTemplate.query(
            """
            SELECT
                ps.player_id,
                ps.platform_name,
                ps.pro_name,
                ps.rating,
                ps.goals,
                ps.assists,
                ps.shots,
                ps.passes_completed,
                ps.passes_attempted,
                ps.tackles_completed,
                ps.tackles_attempted,
                ps.red_cards,
                ps.advanced_coverage,
                ps.advanced_dribbles_completed,
                ps.advanced_beats,
                ps.duration_seconds,
                cm.match_id,
                cm.played_at,
                cm.match_type AS competition,
                cm.our_club_name,
                cm.opponent_club_name,
                cm.our_score,
                cm.opponent_score,
                cm.outcome,
                COALESCE(cm.payload #>> '{footballMatch,completion,status}', 'UNKNOWN') AS completion_status,
                cm.payload #>> '{footballMatch,completion,dnfClubId}' AS dnf_club_id,
                (cm.payload #>> '{interpretation,awards,craque,winnerId}' = ps.player_id) AS is_craque,
                (cm.payload #>> '{interpretation,awards,bagre,winnerId}' = ps.player_id) AS is_bagre,
                (cm.payload #>> '{interpretation,awards,xerife,winnerId}' = ps.player_id) AS is_xerife,
                canonical_player.stats IS NOT NULL AS has_canonical_player,
                canonical_player.stats #>> '{attacking,goals}' AS canonical_goals,
                canonical_player.stats #>> '{attacking,assists}' AS canonical_assists
            FROM player_match_stats ps
            JOIN canonical_matches cm
              ON cm.club_id = ps.club_id
             AND cm.match_id = ps.match_id
            LEFT JOIN LATERAL (
                SELECT player.stats, player.ordinal
                FROM jsonb_array_elements(cm.payload #> '{footballMatch,participants}') participant(entry)
                CROSS JOIN LATERAL jsonb_array_elements(participant.entry #> '{players}') WITH ORDINALITY player(stats, ordinal)
                WHERE participant.entry #>> '{club,id}' = ps.club_id
                  AND player.stats #>> '{player,id}' = ps.player_id
                LIMIT 1
            ) canonical_player ON TRUE
            WHERE ps.club_id = ?
            $playerFilter
            ORDER BY ps.played_at DESC, ps.match_id ASC, canonical_player.ordinal ASC NULLS LAST, ps.player_id ASC
            """.trimIndent(),
            { rs, _ -> readAppearance(rs) },
            *parameters,
        )
        readDiagnostics.record(
            CanonicalReadOperation.FIND_PLAYER_PROFILE_APPEARANCES,
            readOriginContext.current(),
            rows.size,
            rows.sumOf { it.estimatedReturnedBytes() },
        )
        return rows
    }

    private fun readAppearance(rs: ResultSet): PlayerProfileAppearance {
        val completionStatus = MatchCompletionStatus.valueOf(rs.getString("completion_status"))
        val dnfClubId = rs.getString("dnf_club_id")?.let(::ClubId)
        return PlayerProfileAppearance(
            playerId = PlayerId(rs.getString("player_id")),
            platformName = rs.getString("platform_name"),
            proName = rs.getString("pro_name"),
            matchId = MatchId(rs.getString("match_id")),
            playedAt = rs.getTimestamp("played_at").toInstant(),
            competition = rs.getString("competition")?.let(CompetitionType::valueOf),
            ourClubName = rs.getString("our_club_name"),
            opponentClubName = rs.getString("opponent_club_name"),
            ourScore = rs.getInt("our_score"),
            opponentScore = rs.getInt("opponent_score"),
            outcome = MatchOutcome.valueOf(rs.getString("outcome")),
            completion = MatchCompletion(completionStatus, dnfClubId),
            rating = rs.getBigDecimal("rating"),
            // The derived table intentionally stores nullable EA counters as zero for aggregation.
            // The profile projection preserves the canonical nullable values without
            // transferring the canonical JSONB document.
            goals = if (rs.getBoolean("has_canonical_player")) {
                rs.getNullableCanonicalInt("canonical_goals")
            } else {
                rs.getIntOrNull("goals")
            },
            assists = if (rs.getBoolean("has_canonical_player")) {
                rs.getNullableCanonicalInt("canonical_assists")
            } else {
                rs.getIntOrNull("assists")
            },
            shots = rs.getIntOrNull("shots"),
            passesCompleted = rs.getIntOrNull("passes_completed"),
            passesAttempted = rs.getIntOrNull("passes_attempted"),
            tacklesCompleted = rs.getIntOrNull("tackles_completed"),
            tacklesAttempted = rs.getIntOrNull("tackles_attempted"),
            redCards = rs.getIntOrNull("red_cards"),
            awards = buildSet {
                if (rs.getBoolean("is_craque")) add(AwardType.CRAQUE)
                if (rs.getBoolean("is_bagre")) add(AwardType.BAGRE)
                if (rs.getBoolean("is_xerife")) add(AwardType.XERIFE)
            },
            advancedCoverage = rs.getString("advanced_coverage")
                ?.let(com.eafc26.discordstats.domain.match.AdvancedStatsCoverage::valueOf)
                ?: com.eafc26.discordstats.domain.match.AdvancedStatsCoverage.UNAVAILABLE,
            advancedDribblesCompleted = rs.getIntOrNull("advanced_dribbles_completed"),
            advancedBeats = rs.getIntOrNull("advanced_beats"),
            durationSeconds = rs.getIntOrNull("duration_seconds"),
        )
    }

    private fun ResultSet.getIntOrNull(column: String): Int? =
        getObject(column)?.let { (it as Number).toInt() }

    private fun ResultSet.getNullableCanonicalInt(column: String): Int? =
        getString(column)?.toInt()

    private fun PlayerProfileAppearance.estimatedReturnedBytes(): Long = listOfNotNull(
        playerId.value,
        platformName,
        proName,
        matchId.value,
        playedAt.toString(),
        competition?.name,
        ourClubName,
        opponentClubName,
        ourScore.toString(),
        opponentScore.toString(),
        outcome.name,
        completion.status.name,
        completion.dnfClubId?.value,
        rating?.toPlainString(),
        goals?.toString(),
        assists?.toString(),
        shots?.toString(),
        passesCompleted?.toString(),
        passesAttempted?.toString(),
        tacklesCompleted?.toString(),
        tacklesAttempted?.toString(),
        redCards?.toString(),
        advancedCoverage.name,
        advancedDribblesCompleted?.toString(),
        advancedBeats?.toString(),
        durationSeconds?.toString(),
        awards.joinToString(",") { it.name },
    ).sumOf { it.toByteArray(Charsets.UTF_8).size.toLong() }

    private fun PlayerProfileIndexEntry.estimatedReturnedBytes(): Long = listOfNotNull(
        playerId.value, displayName, matchCount.toString(), latestMatchAt.toString(),
        averageRating?.toPlainString(), ratedMatchCount.toString(),
    ).sumOf { it.toByteArray(Charsets.UTF_8).size.toLong() }
}
