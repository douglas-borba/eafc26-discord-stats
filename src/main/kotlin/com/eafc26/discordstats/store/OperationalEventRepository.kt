package com.eafc26.discordstats.store

import com.eafc26.discordstats.domain.match.ClubId
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import java.sql.ResultSet

/**
 * Postgres-backed repository for [OperationalEvent] diagnostics records.
 */
class OperationalEventRepository(private val jdbcTemplate: JdbcTemplate) {

    private val rowMapper = RowMapper { rs: ResultSet, _: Int ->
        OperationalEvent(
            id = rs.getLong("id"),
            clubId = rs.getString("club_id")?.let { ClubId(it) },
            matchId = rs.getString("match_id"),
            eventType = rs.getString("event_type"),
            phase = rs.getString("phase"),
            status = EventStatus.valueOf(rs.getString("status")),
            message = rs.getString("message"),
            errorCode = rs.getString("error_code"),
            durationMs = rs.getObject("duration_ms") as? Long,
            createdAt = rs.getTimestamp("created_at").toInstant(),
        )
    }

    fun save(event: OperationalEvent) {
        jdbcTemplate.update(
            """
            INSERT INTO operational_events
                (club_id, match_id, event_type, phase, status, message, error_code, duration_ms, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, now())
            """.trimIndent(),
            event.clubId?.value,
            event.matchId,
            event.eventType,
            event.phase,
            event.status.name,
            event.message?.take(500),
            event.errorCode,
            event.durationMs,
        )
    }

    fun findByClub(clubId: ClubId, limit: Int = 50): List<OperationalEvent> =
        jdbcTemplate.query(
            "SELECT * FROM operational_events WHERE club_id = ? ORDER BY created_at DESC LIMIT ?",
            rowMapper,
            clubId.value,
            limit,
        )

    fun findLatestByClubAndType(clubId: ClubId, eventType: String): OperationalEvent? =
        jdbcTemplate.query(
            "SELECT * FROM operational_events WHERE club_id = ? AND event_type = ? ORDER BY created_at DESC LIMIT 1",
            rowMapper,
            clubId.value,
            eventType,
        ).firstOrNull()

    fun findRecent(limit: Int = 50): List<OperationalEvent> =
        jdbcTemplate.query(
            "SELECT * FROM operational_events ORDER BY created_at DESC LIMIT ?",
            rowMapper,
            limit,
        )

    fun countByClubAndTypeAndStatus(clubId: ClubId, eventType: String, status: EventStatus): Int =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM operational_events WHERE club_id = ? AND event_type = ? AND status = ?",
            Int::class.java,
            clubId.value,
            eventType,
            status.name,
        ) ?: 0
}
