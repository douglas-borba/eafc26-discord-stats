package com.eafc26.discordstats.store.postgres

import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.store.AdminAuditLogRepository
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@EnabledIf("isDockerAvailable")
class AdminAuditLogRepositoryTest {
    companion object {
        @Container @JvmStatic val postgres = PostgreSQLContainer("postgres:16-alpine")
        private lateinit var jdbc: JdbcTemplate

        @JvmStatic
        fun isDockerAvailable(): Boolean = try {
            org.testcontainers.DockerClientFactory.instance().isDockerAvailable
        } catch (_: Exception) { false }

        @BeforeAll @JvmStatic
        fun migrate() {
            val dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            JdbcTemplate(dataSource).execute("DO $$ BEGIN IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'anon') THEN CREATE ROLE anon NOLOGIN; END IF; END $$")
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate()
            jdbc = JdbcTemplate(dataSource)
        }
    }

    private lateinit var repository: AdminAuditLogRepository

    @BeforeEach
    fun setUp() {
        repository = AdminAuditLogRepository(jdbc)
        jdbc.update("DELETE FROM admin_audit_log")
    }

    @Test
    fun `records successful and failed actions per club without storing secrets`() {
        repository.record("admin@example.com", "ADMIN_POLL", ClubId("club-a"), result = "SUCCESS")
        repository.record("admin@example.com", "DISCORD_TEST", ClubId("club-b"), result = "FAILURE", errorCode = "NO_DESTINATION")

        val rows = jdbc.queryForList("SELECT admin_email, action, club_id, match_id, result, error_code FROM admin_audit_log ORDER BY id")
        assertThat(rows).containsExactly(
            mapOf("admin_email" to "admin@example.com", "action" to "ADMIN_POLL", "club_id" to "club-a", "match_id" to null, "result" to "SUCCESS", "error_code" to null),
            mapOf("admin_email" to "admin@example.com", "action" to "DISCORD_TEST", "club_id" to "club-b", "match_id" to null, "result" to "FAILURE", "error_code" to "NO_DESTINATION"),
        )
    }

    @Test
    fun `keeps audit history independent from monitored club lifecycle and indexed for club and action lookups`() {
        repository.record("admin@example.com", "FORCE_PUBLISH", ClubId("removed-club"), "match-1", result = "SUCCESS")

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_log WHERE club_id = ?", Int::class.java, "removed-club")).isEqualTo(1)
        val indexes = jdbc.queryForList("SELECT indexname FROM pg_indexes WHERE tablename = 'admin_audit_log'")
            .map { it.getValue("indexname") as String }
        assertThat(indexes).contains("idx_admin_audit_log_club_created", "idx_admin_audit_log_action_created")
    }
}
