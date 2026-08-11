package com.eafc26.discordstats.store

import com.eafc26.discordstats.domain.match.ClubId
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant

/**
 * Postgres-backed implementation of [PublicationStateStore], persisting records to the
 * `discord_publication_state` table. No file migration or locking logic — relies on the
 * database for atomicity via single-statement UPSERT/DELETE operations.
 */
class PostgresPublishedMatchStore(private val jdbcTemplate: JdbcTemplate) : PublicationStateStore {
    private val log = LoggerFactory.getLogger(javaClass)

    private val rowMapper = RowMapper { rs: ResultSet, _: Int ->
        PublicationRecord(
            matchId = rs.getString("match_id"),
            state = PublicationState.valueOf(rs.getString("state")),
            updatedAt = rs.getTimestamp("updated_at").toInstant().epochSecond,
            attemptCount = rs.getInt("attempt_count"),
            lastAttemptAt = rs.getTimestamp("last_attempt_at")?.toInstant()?.epochSecond,
            lastError = rs.getString("last_error"),
            lastHttpStatus = rs.getObject("last_http_status") as? Int,
            baselineReason = rs.getString("baseline_reason")?.let { BaselineReason.valueOf(it) },
        )
    }

    override fun loadRecords(clubId: ClubId): Map<String, PublicationRecord> {
        val records = jdbcTemplate.query(
            "SELECT * FROM discord_publication_state WHERE club_id = ?",
            rowMapper,
            clubId.value,
        )
        return records.associateBy { it.matchId }
    }

    override fun find(clubId: ClubId, matchId: String): PublicationRecord? {
        val records = jdbcTemplate.query(
            "SELECT * FROM discord_publication_state WHERE club_id = ? AND match_id = ?",
            rowMapper,
            clubId.value,
            matchId,
        )
        return records.firstOrNull()
    }

    override fun saveRecord(clubId: ClubId, record: PublicationRecord) {
        jdbcTemplate.update(
            """
            INSERT INTO discord_publication_state
                (club_id, match_id, state, attempt_count, last_attempt_at, last_error, last_http_status, baseline_reason, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, now())
            ON CONFLICT (club_id, match_id) DO UPDATE SET
                state = EXCLUDED.state,
                attempt_count = EXCLUDED.attempt_count,
                last_attempt_at = EXCLUDED.last_attempt_at,
                last_error = EXCLUDED.last_error,
                last_http_status = EXCLUDED.last_http_status,
                baseline_reason = EXCLUDED.baseline_reason,
                updated_at = now()
            """.trimIndent(),
            clubId.value,
            record.matchId,
            record.state.name,
            record.attemptCount,
            record.lastAttemptAt?.let { Timestamp.from(Instant.ofEpochSecond(it)) },
            record.lastError,
            record.lastHttpStatus,
            record.baselineReason?.name,
        )
        log.debug("Saved publication record (postgres): clubId={}, matchId={}, state={}", clubId.value, record.matchId, record.state)
    }

    override fun removeRecord(clubId: ClubId, matchId: String) {
        jdbcTemplate.update(
            "DELETE FROM discord_publication_state WHERE club_id = ? AND match_id = ?",
            clubId.value,
            matchId,
        )
        log.debug("Removed publication record (postgres): clubId={}, matchId={}", clubId.value, matchId)
    }

    override fun resolveAsDelivered(clubId: ClubId, matchId: String) {
        val updated = jdbcTemplate.update(
            """
            UPDATE discord_publication_state
            SET state = ?, updated_at = now()
            WHERE club_id = ? AND match_id = ? AND state <> ?
            """.trimIndent(),
            PublicationState.DELIVERED.name,
            clubId.value,
            matchId,
            PublicationState.DELIVERED.name,
        )
        if (updated > 0) {
            log.info("Publication resolved as DELIVERED (postgres): clubId={}, matchId={}", clubId.value, matchId)
        }
    }

    override fun resolveAsUndelivered(clubId: ClubId, matchId: String) = removeRecord(clubId, matchId)

    override fun loadIds(clubId: ClubId): Set<String> = loadRecords(clubId).keys

    override fun saveIds(clubId: ClubId, ids: Set<String>) {
        ids.sorted().forEach { matchId ->
            jdbcTemplate.update(
                """
                INSERT INTO discord_publication_state
                    (club_id, match_id, state, attempt_count, baseline_reason, updated_at)
                VALUES (?, ?, ?, 0, ?, now())
                ON CONFLICT (club_id, match_id) DO NOTHING
                """.trimIndent(),
                clubId.value,
                matchId,
                PublicationState.BASELINED.name,
                BaselineReason.FIRST_RUN.name,
            )
        }
        log.debug("Publication baseline saved (postgres): clubId={}, count={}", clubId.value, ids.size)
    }

    override fun metadata(clubId: ClubId): PublicationStoreMetadata {
        val records = loadRecords(clubId).values
        return PublicationStoreMetadata(
            clubId = clubId,
            recordCount = records.size,
            latestUpdatedAt = records.maxOfOrNull { it.updatedAt }?.let(Instant::ofEpochSecond),
            states = records.groupingBy { it.state }.eachCount(),
        )
    }

    /**
     * Startup upgrade mirroring the filesystem store's behavior: any record still in
     * DELIVERING state (process died mid-delivery) is upgraded to DELIVERY_UNCERTAIN,
     * across all clubs, in a single statement.
     */
    fun upgradeDeliveringRecords() {
        val updated = jdbcTemplate.update(
            """
            UPDATE discord_publication_state
            SET state = ?, updated_at = now()
            WHERE state = ?
            """.trimIndent(),
            PublicationState.DELIVERY_UNCERTAIN.name,
            PublicationState.DELIVERING.name,
        )
        if (updated > 0) {
            log.warn("Interrupted publications marked DELIVERY_UNCERTAIN (postgres): count={}", updated)
        }
    }
}
