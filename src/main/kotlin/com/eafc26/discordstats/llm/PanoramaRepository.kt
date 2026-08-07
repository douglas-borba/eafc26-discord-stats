package com.eafc26.discordstats.llm

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Instant

@Repository
@ConditionalOnProperty(name = ["app.postgres.mirror-enabled"], havingValue = "true")
class PanoramaRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    fun findByContextKey(clubId: String, contextKey: String): PanoramaRecord? {
        val rows = jdbcTemplate.query(
            """
            SELECT id, club_id, context_key, match_ids, narrative, provider, model,
                   prompt_version, status, error_category, input_tokens, output_tokens,
                   generated_at, created_at
            FROM editorial_panoramas
            WHERE club_id = ? AND context_key = ?
            """.trimIndent(),
            { rs, _ -> mapRow(rs) },
            clubId,
            contextKey,
        )
        return rows.firstOrNull()
    }

    /**
     * Finds a successful panorama for a specific context key.
     * Only returns panoramas that belong to the exact context (same 10 matches).
     * Does NOT return successful panoramas from different contexts.
     */
    fun findSuccessfulByContextKey(clubId: String, contextKey: String): PanoramaRecord? {
        val rows = jdbcTemplate.query(
            """
            SELECT id, club_id, context_key, match_ids, narrative, provider, model,
                   prompt_version, status, error_category, input_tokens, output_tokens,
                   generated_at, created_at
            FROM editorial_panoramas
            WHERE club_id = ? AND context_key = ? AND status = 'success' AND narrative IS NOT NULL
            LIMIT 1
            """.trimIndent(),
            { rs, _ -> mapRow(rs) },
            clubId,
            contextKey,
        )
        return rows.firstOrNull()
    }

    fun upsert(record: PanoramaRecord) {
        jdbcTemplate.update(
            """
            INSERT INTO editorial_panoramas
                (club_id, context_key, match_ids, narrative, provider, model,
                 prompt_version, status, error_category, input_tokens, output_tokens,
                 generated_at, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (club_id, context_key)
            DO UPDATE SET
                narrative = EXCLUDED.narrative,
                provider = EXCLUDED.provider,
                model = EXCLUDED.model,
                status = EXCLUDED.status,
                error_category = EXCLUDED.error_category,
                input_tokens = EXCLUDED.input_tokens,
                output_tokens = EXCLUDED.output_tokens,
                generated_at = EXCLUDED.generated_at
            """.trimIndent(),
            record.clubId,
            record.contextKey,
            record.matchIds.toTypedArray(),
            record.narrative,
            record.provider,
            record.model,
            record.promptVersion,
            record.status,
            record.errorCategory,
            record.inputTokens,
            record.outputTokens,
            java.sql.Timestamp.from(record.generatedAt),
            java.sql.Timestamp.from(record.createdAt),
        )
    }

    private fun mapRow(rs: ResultSet): PanoramaRecord {
        val sqlArray = rs.getArray("match_ids")
        @Suppress("UNCHECKED_CAST")
        val matchIds = (sqlArray.array as Array<String>).toList()
        return PanoramaRecord(
            id = rs.getLong("id"),
            clubId = rs.getString("club_id"),
            contextKey = rs.getString("context_key"),
            matchIds = matchIds,
            narrative = rs.getString("narrative"),
            provider = rs.getString("provider"),
            model = rs.getString("model"),
            promptVersion = rs.getString("prompt_version"),
            status = rs.getString("status"),
            errorCategory = rs.getString("error_category"),
            inputTokens = rs.getObject("input_tokens") as? Int,
            outputTokens = rs.getObject("output_tokens") as? Int,
            generatedAt = rs.getTimestamp("generated_at").toInstant(),
            createdAt = rs.getTimestamp("created_at").toInstant(),
        )
    }
}

data class PanoramaRecord(
    val id: Long? = null,
    val clubId: String,
    val contextKey: String,
    val matchIds: List<String>,
    val narrative: String?,
    val provider: String?,
    val model: String?,
    val promptVersion: String,
    val status: String,
    val errorCategory: String? = null,
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
    val generatedAt: Instant,
    val createdAt: Instant = Instant.now(),
)
