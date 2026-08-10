package com.eafc26.discordstats.store

import com.eafc26.discordstats.application.club.DiscordWebhookSecretReference
import com.eafc26.discordstats.application.club.EaPlatform
import com.eafc26.discordstats.application.club.MonitoredClub
import com.eafc26.discordstats.application.club.MonitoredClubRepository
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.ClubName
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.ResultSet
import java.sql.Timestamp

class PostgresMonitoredClubRepository(
    private val jdbcTemplate: JdbcTemplate,
) : MonitoredClubRepository {
    override fun save(club: MonitoredClub): MonitoredClub {
        jdbcTemplate.update(
            """
            INSERT INTO monitored_clubs
                (club_id, display_name, platform, monitoring_enabled,
                 discord_webhook_secret_ref, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (club_id) DO UPDATE SET
                display_name = EXCLUDED.display_name,
                platform = EXCLUDED.platform,
                monitoring_enabled = EXCLUDED.monitoring_enabled,
                discord_webhook_secret_ref = EXCLUDED.discord_webhook_secret_ref,
                updated_at = EXCLUDED.updated_at
            """.trimIndent(),
            club.clubId.value,
            club.displayName.value,
            club.platform.value,
            club.monitoringEnabled,
            club.discordWebhookSecretReference?.value,
            Timestamp.from(club.createdAt),
            Timestamp.from(club.updatedAt),
        )
        return findById(club.clubId)!!
    }

    override fun findById(clubId: ClubId): MonitoredClub? =
        jdbcTemplate.query(
            "SELECT * FROM monitored_clubs WHERE club_id = ?",
            { rs, _ -> map(rs) },
            clubId.value,
        ).firstOrNull()

    override fun findAll(): List<MonitoredClub> =
        jdbcTemplate.query("SELECT * FROM monitored_clubs ORDER BY display_name ASC, club_id ASC") { rs, _ -> map(rs) }

    override fun existsById(clubId: ClubId): Boolean =
        jdbcTemplate.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM monitored_clubs WHERE club_id = ?)",
            Boolean::class.java,
            clubId.value,
        ) == true

    override fun deleteById(clubId: ClubId): Boolean =
        jdbcTemplate.update("DELETE FROM monitored_clubs WHERE club_id = ?", clubId.value) > 0

    private fun map(rs: ResultSet) = MonitoredClub(
        clubId = ClubId(rs.getString("club_id")),
        displayName = ClubName(rs.getString("display_name")),
        platform = EaPlatform(rs.getString("platform")),
        monitoringEnabled = rs.getBoolean("monitoring_enabled"),
        discordWebhookSecretReference = rs.getString("discord_webhook_secret_ref")
            ?.let(::DiscordWebhookSecretReference),
        createdAt = rs.getTimestamp("created_at").toInstant(),
        updatedAt = rs.getTimestamp("updated_at").toInstant(),
    )
}
