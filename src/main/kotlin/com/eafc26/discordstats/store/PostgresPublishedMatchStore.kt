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
            nextAutomaticAttemptAt = rs.getTimestamp("next_automatic_attempt_at")?.toInstant()?.epochSecond,
            recoveryAttemptCount = rs.getInt("recovery_attempt_count"),
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
                (club_id, match_id, state, attempt_count, last_attempt_at, last_error, last_http_status, baseline_reason,
                 next_automatic_attempt_at, recovery_attempt_count, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
            ON CONFLICT (club_id, match_id) DO UPDATE SET
                state = EXCLUDED.state,
                attempt_count = EXCLUDED.attempt_count,
                last_attempt_at = EXCLUDED.last_attempt_at,
                last_error = EXCLUDED.last_error,
                last_http_status = EXCLUDED.last_http_status,
                baseline_reason = EXCLUDED.baseline_reason,
                next_automatic_attempt_at = EXCLUDED.next_automatic_attempt_at,
                recovery_attempt_count = EXCLUDED.recovery_attempt_count,
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
            record.nextAutomaticAttemptAt?.let { Timestamp.from(Instant.ofEpochSecond(it)) },
            record.recoveryAttemptCount,
        )
        log.debug("Saved publication record (postgres): clubId={}, matchId={}, state={}", clubId.value, record.matchId, record.state)
    }

    override fun createRecordIfAbsent(clubId: ClubId, record: PublicationRecord): Boolean {
        val inserted = jdbcTemplate.update(
            """
            INSERT INTO discord_publication_state
                (club_id, match_id, state, attempt_count, last_attempt_at, last_error, last_http_status, baseline_reason,
                 next_automatic_attempt_at, recovery_attempt_count, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
            ON CONFLICT (club_id, match_id) DO NOTHING
            """.trimIndent(),
            clubId.value,
            record.matchId,
            record.state.name,
            record.attemptCount,
            record.lastAttemptAt?.let { Timestamp.from(Instant.ofEpochSecond(it)) },
            record.lastError,
            record.lastHttpStatus,
            record.baselineReason?.name,
            record.nextAutomaticAttemptAt?.let { Timestamp.from(Instant.ofEpochSecond(it)) },
            record.recoveryAttemptCount,
        ) > 0
        if (inserted) {
            log.debug("Created publication record (postgres): clubId={}, matchId={}, state={}", clubId.value, record.matchId, record.state)
        }
        return inserted
    }

    override fun claimForAutomaticDelivery(
        clubId: ClubId,
        expected: PublicationRecord,
        attemptedAt: Instant,
    ): PublicationRecord? {
        require(expected.isAutomaticClaimable()) {
            "State ${expected.state} cannot be claimed for automatic delivery"
        }
        val claimed = jdbcTemplate.query(
            """
            UPDATE discord_publication_state
            SET state = ?,
                attempt_count = attempt_count + 1,
                last_attempt_at = ?,
                next_automatic_attempt_at = NULL,
                recovery_attempt_count = recovery_attempt_count + CASE WHEN state = ? THEN 1 ELSE 0 END,
                updated_at = now()
            WHERE club_id = ?
              AND match_id = ?
              AND state = ?
              AND attempt_count = ?
              AND baseline_reason IS NOT DISTINCT FROM ?
              AND next_automatic_attempt_at IS NOT DISTINCT FROM ?
              AND recovery_attempt_count = ?
            RETURNING match_id, state, updated_at, attempt_count, last_attempt_at,
                      last_error, last_http_status, baseline_reason, next_automatic_attempt_at, recovery_attempt_count
            """.trimIndent(),
            rowMapper,
            PublicationState.DELIVERING.name,
            Timestamp.from(attemptedAt),
            PublicationState.RETRY_EXHAUSTED.name,
            clubId.value,
            expected.matchId,
            expected.state.name,
            expected.attemptCount,
            expected.baselineReason?.name,
            expected.nextAutomaticAttemptAt?.let { Timestamp.from(Instant.ofEpochSecond(it)) },
            expected.recoveryAttemptCount,
        ).firstOrNull()
        if (claimed != null) {
            log.debug("Claimed publication record (postgres): clubId={}, matchId={}, state={}", clubId.value, claimed.matchId, expected.state)
        }
        return claimed
    }

    /**
     * Returns only lightweight, currently eligible automatic work. Canonical payload is
     * intentionally not selected; it is loaded after a successful atomic claim.
     */
    fun findAutomaticPublicationCandidates(
        now: Instant,
        limit: Int,
    ): List<PublicationWorkCandidate> {
        require(limit > 0) { "limit must be positive" }
        return jdbcTemplate.query(
            """
            SELECT ps.club_id, ps.match_id, ps.state, ps.updated_at, ps.attempt_count,
                   ps.last_attempt_at, ps.last_error, ps.last_http_status, ps.baseline_reason,
                   ps.next_automatic_attempt_at, ps.recovery_attempt_count,
                   cm.played_at
            FROM discord_publication_state ps
            JOIN canonical_matches cm
              ON cm.club_id = ps.club_id AND cm.match_id = ps.match_id
            WHERE ps.state = ?
               OR (ps.state = ? AND ps.next_automatic_attempt_at <= ?)
               OR (ps.state = ? AND ps.next_automatic_attempt_at <= ?)
               OR (ps.state = ? AND ps.baseline_reason = ?)
            ORDER BY cm.played_at ASC, ps.match_id ASC
            LIMIT ?
            """.trimIndent(),
            { rs, rowNum ->
                PublicationWorkCandidate(
                    clubId = ClubId(rs.getString("club_id")),
                    record = rowMapper.mapRow(rs, rowNum)!!,
                    playedAt = rs.getTimestamp("played_at").toInstant(),
                )
            },
            PublicationState.PENDING.name,
            PublicationState.FAILED_TRANSIENT.name,
            Timestamp.from(now),
            PublicationState.RETRY_EXHAUSTED.name,
            Timestamp.from(now),
            PublicationState.BASELINED.name,
            BaselineReason.NO_DESTINATION.name,
            limit,
        )
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
     * across all clubs while preserving the original attempt metadata. A corresponding
     * startup-recovery operational event is emitted by the configuration runner.
     */
    fun upgradeDeliveringRecords(): List<RecoveredPublication> {
        val interrupted = jdbcTemplate.query(
            "SELECT * FROM discord_publication_state WHERE state = ?",
            RowMapper { rs, rowNum ->
                RecoveredPublication(
                    clubId = ClubId(rs.getString("club_id")),
                    record = rowMapper.mapRow(rs, rowNum)!!,
                )
            },
            PublicationState.DELIVERING.name,
        )

        val recovered = interrupted.filter { interruptedPublication ->
            val record = interruptedPublication.record
            val diagnostic = DeliveryUncertaintyReason.STARTUP_RECOVERY.diagnosticMessage(
                "Registro DELIVERING encontrado na inicialização; a causa original não está disponível.",
            )
            jdbcTemplate.update(
                """
                UPDATE discord_publication_state
                SET state = ?,
                    last_error = CASE
                        WHEN last_error IS NULL OR btrim(last_error) = '' THEN ?
                        ELSE last_error
                    END,
                    updated_at = now()
                WHERE club_id = ? AND match_id = ? AND state = ?
                """.trimIndent(),
                PublicationState.DELIVERY_UNCERTAIN.name,
                diagnostic,
                interruptedPublication.clubId.value,
                record.matchId,
                PublicationState.DELIVERING.name,
            ) > 0
        }
        if (recovered.isNotEmpty()) {
            log.warn("Interrupted publications marked DELIVERY_UNCERTAIN (postgres): count={}", recovered.size)
        }
        return recovered
    }
}

data class RecoveredPublication(
    val clubId: ClubId,
    val record: PublicationRecord,
)

/** Lightweight publication work selected without reading canonical JSON payloads. */
data class PublicationWorkCandidate(
    val clubId: ClubId,
    val record: PublicationRecord,
    val playedAt: Instant,
)

private fun PublicationRecord.isAutomaticClaimable(): Boolean =
    state == PublicationState.PENDING ||
        state == PublicationState.FAILED_TRANSIENT ||
        state == PublicationState.RETRY_EXHAUSTED ||
        state == PublicationState.BASELINED && baselineReason == BaselineReason.NO_DESTINATION
