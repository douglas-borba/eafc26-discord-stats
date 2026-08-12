package com.eafc26.discordstats.store

import com.eafc26.discordstats.domain.match.ClubId
import org.springframework.jdbc.core.JdbcTemplate

class AdminAuditLogRepository(private val jdbcTemplate: JdbcTemplate) {
    fun record(adminEmail: String, action: String, clubId: ClubId?, matchId: String? = null, result: String, errorCode: String? = null) {
        jdbcTemplate.update(
            "INSERT INTO admin_audit_log (admin_email, action, club_id, match_id, result, error_code, created_at) VALUES (?, ?, ?, ?, ?, ?, now())",
            adminEmail.take(320), action, clubId?.value, matchId, result, errorCode,
        )
    }
}
