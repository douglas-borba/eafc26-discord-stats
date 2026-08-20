package com.eafc26.discordstats.store

import com.eafc26.discordstats.application.repository.CanonicalMatchRepository
import com.eafc26.discordstats.application.repository.CanonicalRepositoryMetadata
import com.eafc26.discordstats.canonical.CanonicalMatch
import com.eafc26.discordstats.canonical.CanonicalSchemaVersion
import com.eafc26.discordstats.canonical.EngineVersion
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.MatchId
import com.eafc26.discordstats.diagnostics.CanonicalReadDiagnostics
import com.eafc26.discordstats.diagnostics.CanonicalReadOperation
import com.eafc26.discordstats.diagnostics.CanonicalReadOriginContext
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant

class PostgresCanonicalMatchRepository(
    private val jdbcTemplate: JdbcTemplate,
    sourceMapper: ObjectMapper,
    private val readDiagnostics: CanonicalReadDiagnostics = CanonicalReadDiagnostics(),
    private val readOriginContext: CanonicalReadOriginContext = CanonicalReadOriginContext(),
) : CanonicalMatchRepository {

    private val objectMapper = CanonicalObjectMapperFactory.create(sourceMapper)

    override fun save(match: CanonicalMatch) {
        val payload = objectMapper.writeValueAsString(match)
        val perspectiveClubId = match.interpretation.perspectiveClubId.value
        val opponentClubId = match.interpretation.result.opponentClub.value
        val matchType = match.footballMatch.competition?.name
        val now = Instant.now()
        val result = match.interpretation.result

        val ourParticipant = match.footballMatch.participants.find { it.club.id.value == perspectiveClubId }
        val opponentParticipant = match.footballMatch.participants.find { it.club.id.value == opponentClubId }

        jdbcTemplate.update(
            """
            INSERT INTO canonical_matches
                (match_id, club_id, opponent_club_id, played_at, match_type,
                 canonical_schema_version, payload, created_at, updated_at,
                 outcome, our_score, opponent_score, our_club_name, opponent_club_name)
            VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (club_id, match_id) DO UPDATE SET
                opponent_club_id = EXCLUDED.opponent_club_id,
                played_at = EXCLUDED.played_at,
                match_type = EXCLUDED.match_type,
                canonical_schema_version = EXCLUDED.canonical_schema_version,
                payload = EXCLUDED.payload,
                updated_at = EXCLUDED.updated_at,
                outcome = EXCLUDED.outcome,
                our_score = EXCLUDED.our_score,
                opponent_score = EXCLUDED.opponent_score,
                our_club_name = EXCLUDED.our_club_name,
                opponent_club_name = EXCLUDED.opponent_club_name
            """.trimIndent(),
            match.matchId.value,
            perspectiveClubId,
            opponentClubId,
            Timestamp.from(match.footballMatch.playedAt),
            matchType,
            match.schemaVersion.value,
            payload,
            Timestamp.from(now),
            Timestamp.from(now),
            result.outcome.name,
            result.ourScore.goals,
            result.opponentScore.goals,
            ourParticipant?.club?.name?.value,
            opponentParticipant?.club?.name?.value,
        )

        savePlayerStats(match, perspectiveClubId)
    }

    private fun savePlayerStats(match: CanonicalMatch, perspectiveClubId: String) {
        val ourParticipant = match.footballMatch.participants.find { it.club.id.value == perspectiveClubId }
            ?: return

        jdbcTemplate.update(
            "DELETE FROM player_match_stats WHERE club_id = ? AND match_id = ?",
            perspectiveClubId,
            match.matchId.value,
        )

        ourParticipant.players.forEach { playerPerf ->
            jdbcTemplate.update(
                """
                INSERT INTO player_match_stats
                    (club_id, match_id, player_id, platform_name, pro_name, rating,
                     goals, assists, shots, passes_completed, passes_attempted,
                     tackles_completed, tackles_attempted, red_cards, man_of_the_match, played_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                perspectiveClubId,
                match.matchId.value,
                playerPerf.player.id.value,
                playerPerf.player.platformName?.value,
                playerPerf.player.proName?.value,
                playerPerf.rating?.value,
                playerPerf.attacking.goals ?: 0,
                playerPerf.attacking.assists ?: 0,
                playerPerf.attacking.shots ?: 0,
                playerPerf.passing.completed ?: 0,
                playerPerf.passing.attempted ?: 0,
                playerPerf.defending.tacklesCompleted ?: 0,
                playerPerf.defending.tacklesAttempted ?: 0,
                playerPerf.discipline.redCards ?: 0,
                playerPerf.eaRecognition.manOfTheMatch ?: false,
                Timestamp.from(match.footballMatch.playedAt),
            )
        }
    }

    override fun findById(clubId: ClubId, matchId: MatchId): CanonicalMatch? {
        val results = jdbcTemplate.query(
            "SELECT payload FROM canonical_matches WHERE club_id = ? AND match_id = ?",
            { rs, _ -> readPayload(rs) },
            clubId.value,
            matchId.value,
        )
        readDiagnostics.record(
            CanonicalReadOperation.FIND_BY_ID,
            readOriginContext.current(),
            results.size,
            results.sumOf { it.payloadBytes },
        )
        return results.firstOrNull()?.match
    }

    override fun findMatchIds(clubId: ClubId): Set<MatchId> {
        val results = jdbcTemplate.query(
            "SELECT match_id FROM canonical_matches WHERE club_id = ? ORDER BY played_at DESC, match_id ASC",
            { rs, _ -> rs.getString("match_id") },
            clubId.value,
        )
        readDiagnostics.record(
            CanonicalReadOperation.FIND_MATCH_IDS,
            readOriginContext.current(),
            results.size,
            results.sumOf { it.toByteArray(Charsets.UTF_8).size.toLong() },
        )
        return results.mapTo(linkedSetOf(), ::MatchId)
    }

    override fun findLatestMatchId(clubId: ClubId): MatchId? {
        val results = jdbcTemplate.query(
            "SELECT match_id FROM canonical_matches WHERE club_id = ? ORDER BY played_at DESC, match_id ASC LIMIT 1",
            { rs, _ -> rs.getString("match_id") },
            clubId.value,
        )
        readDiagnostics.record(
            CanonicalReadOperation.FIND_LATEST_MATCH_ID,
            readOriginContext.current(),
            results.size,
            results.sumOf { it.toByteArray(Charsets.UTF_8).size.toLong() },
        )
        return results.firstOrNull()?.let(::MatchId)
    }

    override fun findExistingMatchIds(clubId: ClubId, candidateMatchIds: Collection<MatchId>): Set<MatchId> {
        val candidates = candidateMatchIds.map { it.value }.distinct()
        if (candidates.isEmpty()) {
            readDiagnostics.record(
                CanonicalReadOperation.FIND_EXISTING_MATCH_IDS,
                readOriginContext.current(),
                rows = 0,
                estimatedReturnedBytes = 0,
            )
            return emptySet()
        }

        val placeholders = candidates.joinToString(", ") { "?" }
        val results = jdbcTemplate.query(
            "SELECT match_id FROM canonical_matches WHERE club_id = ? AND match_id IN ($placeholders)",
            { rs, _ -> rs.getString("match_id") },
            *arrayOf(clubId.value, *candidates.toTypedArray()),
        )
        val existing = results.toSet()
        readDiagnostics.record(
            CanonicalReadOperation.FIND_EXISTING_MATCH_IDS,
            readOriginContext.current(),
            results.size,
            results.sumOf { it.toByteArray(Charsets.UTF_8).size.toLong() },
        )
        return candidateMatchIds.asSequence()
            .distinct()
            .filter { it.value in existing }
            .toCollection(linkedSetOf())
    }

    override fun findRecentMatchIds(clubId: ClubId, limit: Int): List<MatchId> {
        require(limit >= 0) { "limit must be non-negative" }
        val results = jdbcTemplate.query(
            "SELECT match_id FROM canonical_matches WHERE club_id = ? ORDER BY played_at DESC, match_id ASC LIMIT ?",
            { rs, _ -> rs.getString("match_id") },
            clubId.value,
            limit,
        )
        readDiagnostics.record(
            CanonicalReadOperation.FIND_RECENT_MATCH_IDS,
            readOriginContext.current(),
            results.size,
            results.sumOf { it.toByteArray(Charsets.UTF_8).size.toLong() },
        )
        return results.map(::MatchId)
    }

    override fun findAll(clubId: ClubId): List<CanonicalMatch> {
        val results = jdbcTemplate.query(
            "SELECT payload FROM canonical_matches WHERE club_id = ? ORDER BY played_at DESC, match_id ASC",
            { rs, _ -> readPayload(rs) },
            clubId.value,
        )
        readDiagnostics.record(
            CanonicalReadOperation.FIND_ALL,
            readOriginContext.current(),
            results.size,
            results.sumOf { it.payloadBytes },
        )
        return results.map(PayloadRead::match)
    }

    override fun findRecent(clubId: ClubId, limit: Int): List<CanonicalMatch> {
        require(limit >= 0) { "limit must be non-negative" }
        val results = jdbcTemplate.query(
            "SELECT payload FROM canonical_matches WHERE club_id = ? ORDER BY played_at DESC, match_id ASC LIMIT ?",
            { rs, _ -> readPayload(rs) },
            clubId.value,
            limit,
        )
        readDiagnostics.record(
            CanonicalReadOperation.FIND_RECENT,
            readOriginContext.current(),
            results.size,
            results.sumOf { it.payloadBytes },
        )
        return results.map(PayloadRead::match)
    }

    override fun metadata(clubId: ClubId): CanonicalRepositoryMetadata {
        return jdbcTemplate.queryForObject(
            """
            SELECT
                count(*)                                    AS match_count,
                min(played_at)                              AS oldest,
                max(played_at)                              AS newest,
                max(payload->>'generatedAt')                AS last_generated,
                array_agg(DISTINCT canonical_schema_version) AS schema_versions,
                array_agg(DISTINCT payload->>'engineVersion') AS engine_versions
            FROM canonical_matches
            WHERE club_id = ?
            """.trimIndent(),
            { rs, _ ->
                val count = rs.getInt("match_count")
                val oldest = rs.getTimestamp("oldest")?.toInstant()
                val newest = rs.getTimestamp("newest")?.toInstant()
                val lastGenerated = rs.getString("last_generated")?.let(Instant::parse)
                val schemaVersions = (rs.getArray("schema_versions")?.array as? Array<*>)
                    ?.filterNotNull()
                    ?.map { CanonicalSchemaVersion((it as Number).toInt()) }
                    ?.toSet() ?: emptySet()
                val engineVersions = (rs.getArray("engine_versions")?.array as? Array<*>)
                    ?.filterNotNull()
                    ?.map { EngineVersion(it.toString()) }
                    ?.toSet() ?: emptySet()
                CanonicalRepositoryMetadata(
                    matchCount = count,
                    oldestMatchAt = oldest,
                    newestMatchAt = newest,
                    lastGeneratedAt = lastGenerated,
                    schemaVersions = schemaVersions,
                    engineVersions = engineVersions,
                )
            },
            clubId.value,
        )!!
    }

    private fun readPayload(rs: ResultSet): PayloadRead {
        val payload = rs.getString("payload")
        return PayloadRead(
            match = objectMapper.readValue(payload, CanonicalMatch::class.java),
            payloadBytes = payload.toByteArray(Charsets.UTF_8).size.toLong(),
        )
    }

    private data class PayloadRead(val match: CanonicalMatch, val payloadBytes: Long)
}
