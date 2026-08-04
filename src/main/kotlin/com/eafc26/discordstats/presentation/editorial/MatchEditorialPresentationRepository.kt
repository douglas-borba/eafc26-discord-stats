package com.eafc26.discordstats.presentation.editorial

import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.MatchId
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import com.fasterxml.jackson.databind.ObjectMapper
import java.sql.ResultSet
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import java.sql.Timestamp
import java.time.Instant

@Repository
@ConditionalOnProperty(name = ["app.postgres.mirror-enabled"], havingValue = "true")
class MatchEditorialPresentationRepository(
    private val jdbcTemplate: JdbcTemplate,
    private val objectMapper: ObjectMapper,
) {

    fun upsertIfNewer(editorial: MatchEditorialPresentation) {
        val presentationJson = objectMapper.writeValueAsString(editorial.presentation)

        jdbcTemplate.update(
            """
            INSERT INTO match_editorial_presentations
                (club_id, match_id, played_at, schema_version, phrase_bank_version,
                 presentation, generated_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?)
            ON CONFLICT (club_id, match_id)
            DO UPDATE SET
                presentation = EXCLUDED.presentation,
                phrase_bank_version = EXCLUDED.phrase_bank_version,
                schema_version = EXCLUDED.schema_version,
                updated_at = EXCLUDED.updated_at
            WHERE
                match_editorial_presentations.schema_version < EXCLUDED.schema_version
            """.trimIndent(),
            editorial.clubId.value,
            editorial.matchId.value,
            Timestamp.from(editorial.playedAt),
            editorial.schemaVersion,
            editorial.phraseBankVersion,
            presentationJson,
            Timestamp.from(editorial.generatedAt),
            Timestamp.from(editorial.updatedAt),
        )
    }

    fun forceUpsert(editorial: MatchEditorialPresentation) {
        val presentationJson = objectMapper.writeValueAsString(editorial.presentation)

        jdbcTemplate.update(
            """
            INSERT INTO match_editorial_presentations
                (club_id, match_id, played_at, schema_version, phrase_bank_version,
                 presentation, generated_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?)
            ON CONFLICT (club_id, match_id)
            DO UPDATE SET
                presentation = EXCLUDED.presentation,
                phrase_bank_version = EXCLUDED.phrase_bank_version,
                schema_version = EXCLUDED.schema_version,
                updated_at = EXCLUDED.updated_at
            """.trimIndent(),
            editorial.clubId.value,
            editorial.matchId.value,
            Timestamp.from(editorial.playedAt),
            editorial.schemaVersion,
            editorial.phraseBankVersion,
            presentationJson,
            Timestamp.from(editorial.generatedAt),
            Timestamp.from(editorial.updatedAt),
        )
    }

    fun findByClubAndMatch(clubId: ClubId, matchId: MatchId): MatchEditorialPresentation? {
        val rows = jdbcTemplate.query(
            """
            SELECT club_id, match_id, played_at, schema_version, phrase_bank_version,
                   presentation, generated_at, updated_at
            FROM match_editorial_presentations
            WHERE club_id = ? AND match_id = ?
            """.trimIndent(),
            { rs, _ -> mapRow(rs) },
            clubId.value,
            matchId.value,
        )
        return rows.firstOrNull()
    }

    fun findByClub(clubId: ClubId, limit: Int = 50): List<MatchEditorialPresentation> {
        return jdbcTemplate.query(
            """
            SELECT club_id, match_id, played_at, schema_version, phrase_bank_version,
                   presentation, generated_at, updated_at
            FROM match_editorial_presentations
            WHERE club_id = ?
            ORDER BY played_at DESC
            LIMIT ?
            """.trimIndent(),
            { rs, _ -> mapRow(rs) },
            clubId.value,
            limit,
        )
    }

    fun countByClub(clubId: ClubId): Int {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM match_editorial_presentations WHERE club_id = ?",
            Int::class.java,
            clubId.value,
        ) ?: 0
    }

    private fun mapRow(rs: ResultSet): MatchEditorialPresentation {
        val presentationJson = rs.getString("presentation")
        val presentation = objectMapper.readValue(
            presentationJson,
            com.eafc26.discordstats.presentation.MatchSummaryPresentation::class.java,
        )

        return MatchEditorialPresentation(
            clubId = ClubId(rs.getString("club_id")),
            matchId = MatchId(rs.getString("match_id")),
            playedAt = rs.getTimestamp("played_at").toInstant(),
            schemaVersion = rs.getInt("schema_version"),
            phraseBankVersion = rs.getString("phrase_bank_version"),
            presentation = presentation,
            generatedAt = rs.getTimestamp("generated_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
        )
    }
}
