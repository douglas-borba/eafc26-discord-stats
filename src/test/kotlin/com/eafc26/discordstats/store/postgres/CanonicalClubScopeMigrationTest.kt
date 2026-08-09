package com.eafc26.discordstats.store.postgres

import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.security.MessageDigest

@Testcontainers
@EnabledIf("isDockerAvailable")
class CanonicalClubScopeMigrationTest {

    @Test
    fun `V9 preserves existing canonical and player data while introducing compound identity`() {
        val dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        val jdbc = JdbcTemplate(dataSource)
        jdbc.execute("DO \$\$ BEGIN IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'anon') THEN CREATE ROLE anon NOLOGIN; END IF; END \$\$")
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .target(MigrationVersion.fromVersion("8"))
            .load()
            .migrate()

        val payload = """{"schemaVersion":1,"legacy":"preserve-me"}"""
        jdbc.update(
            """INSERT INTO canonical_matches
                (match_id, club_id, opponent_club_id, played_at, match_type, canonical_schema_version, payload)
                VALUES (?, ?, ?, now(), ?, ?, ?::jsonb)""".trimIndent(),
            "same-id", "1104972", "opponent", "leagueMatch", 1, payload,
        )
        jdbc.update(
            """INSERT INTO player_match_stats
                (match_id, player_id, platform_name, played_at)
                VALUES (?, ?, ?, now())""".trimIndent(),
            "same-id", "player-1", "Player One",
        )
        val beforePayload = jdbc.queryForObject(
            "SELECT payload::text FROM canonical_matches WHERE match_id = ?",
            String::class.java,
            "same-id",
        )
        val beforeHash = sha256(beforePayload)

        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate()

        val afterPayload = jdbc.queryForObject(
            "SELECT payload::text FROM canonical_matches WHERE club_id = ? AND match_id = ?",
            String::class.java,
            "1104972",
            "same-id",
        )
        assertThat(sha256(afterPayload)).isEqualTo(beforeHash)
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM canonical_matches", Int::class.java)).isEqualTo(1)
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM player_match_stats", Int::class.java)).isEqualTo(1)
        assertThat(jdbc.queryForObject(
            "SELECT club_id FROM player_match_stats WHERE match_id = ? AND player_id = ?",
            String::class.java,
            "same-id",
            "player-1",
        )).isEqualTo("1104972")
        assertThat(primaryKeyColumns(jdbc, "canonical_matches")).containsExactly("club_id", "match_id")
        assertThat(primaryKeyColumns(jdbc, "player_match_stats"))
            .containsExactly("club_id", "match_id", "player_id")
    }

    private fun primaryKeyColumns(jdbc: JdbcTemplate, table: String): List<String> = jdbc.queryForList(
        """SELECT kcu.column_name
            FROM information_schema.table_constraints tc
            JOIN information_schema.key_column_usage kcu
              ON tc.constraint_name = kcu.constraint_name
             AND tc.table_schema = kcu.table_schema
            WHERE tc.table_schema = 'public'
              AND tc.table_name = ?
              AND tc.constraint_type = 'PRIMARY KEY'
            ORDER BY kcu.ordinal_position""".trimIndent(),
        String::class.java,
        table,
    )

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")

        @JvmStatic
        fun isDockerAvailable(): Boolean = try {
            org.testcontainers.DockerClientFactory.instance().isDockerAvailable
        } catch (_: Exception) {
            false
        }
    }
}
