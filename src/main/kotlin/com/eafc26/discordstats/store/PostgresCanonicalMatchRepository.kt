package com.eafc26.discordstats.store

import com.eafc26.discordstats.application.repository.CanonicalMatchRepository
import com.eafc26.discordstats.application.repository.CanonicalRepositoryMetadata
import com.eafc26.discordstats.canonical.CanonicalMatch
import com.eafc26.discordstats.canonical.CanonicalSchemaVersion
import com.eafc26.discordstats.canonical.EngineVersion
import com.eafc26.discordstats.domain.match.MatchId
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant

class PostgresCanonicalMatchRepository(
    private val jdbcTemplate: JdbcTemplate,
    sourceMapper: ObjectMapper,
) : CanonicalMatchRepository {

    private val objectMapper = CanonicalObjectMapperFactory.create(sourceMapper)

    override fun save(match: CanonicalMatch) {
        val payload = objectMapper.writeValueAsString(match)
        val perspectiveClubId = match.interpretation.perspectiveClubId.value
        val opponentClubId = match.interpretation.result.opponentClub.value
        val matchType = match.footballMatch.competition?.name
        val now = Instant.now()

        jdbcTemplate.update(
            """
            INSERT INTO canonical_matches
                (match_id, club_id, opponent_club_id, played_at, match_type,
                 canonical_schema_version, payload, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)
            ON CONFLICT (match_id) DO UPDATE SET
                club_id = EXCLUDED.club_id,
                opponent_club_id = EXCLUDED.opponent_club_id,
                played_at = EXCLUDED.played_at,
                match_type = EXCLUDED.match_type,
                canonical_schema_version = EXCLUDED.canonical_schema_version,
                payload = EXCLUDED.payload,
                updated_at = EXCLUDED.updated_at
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
        )
    }

    override fun findById(matchId: MatchId): CanonicalMatch? {
        val results = jdbcTemplate.query(
            "SELECT payload FROM canonical_matches WHERE match_id = ?",
            { rs, _ -> readPayload(rs) },
            matchId.value,
        )
        return results.firstOrNull()
    }

    override fun findAll(): List<CanonicalMatch> {
        return jdbcTemplate.query(
            "SELECT payload FROM canonical_matches ORDER BY played_at DESC, match_id ASC",
        ) { rs, _ -> readPayload(rs) }
    }

    override fun metadata(): CanonicalRepositoryMetadata {
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
            """.trimIndent(),
        ) { rs, _ ->
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
        }!!
    }

    private fun readPayload(rs: ResultSet): CanonicalMatch {
        return objectMapper.readValue(rs.getString("payload"), CanonicalMatch::class.java)
    }
}
