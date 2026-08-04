package com.eafc26.discordstats.presentation.editorial

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.SingleConnectionDataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.DriverManager

/**
 * Tests RLS and permission behavior for match_editorial_presentations.
 *
 * Simulates Supabase environment: anon role has SELECT on table (granted
 * by Supabase), RLS policies restrict row access, and security_invoker
 * view ensures RLS applies through the view.
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MatchEditorialPresentationsSecurityTest {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:15-alpine").apply {
            withDatabaseName("testdb")
            withUsername("test")
            withPassword("test")
        }
    }

    private fun newJdbc(): JdbcTemplate {
        val conn = DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)
        return JdbcTemplate(SingleConnectionDataSource(conn, true))
    }

    @BeforeAll
    fun setup() {
        val jdbc = newJdbc()
        val migration = this::class.java.classLoader
            .getResource("db/migration/V5__match_editorial_presentations.sql")!!
            .readText()

        jdbc.execute("DO \$\$ BEGIN IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'anon') THEN CREATE ROLE anon NOLOGIN; END IF; END \$\$")
        jdbc.execute(migration)
        // Supabase grants SELECT on tables to anon; RLS policies control row access
        jdbc.execute("GRANT SELECT ON match_editorial_presentations TO anon")
    }

    @Test
    fun `RLS is enabled on match_editorial_presentations`() {
        val jdbc = newJdbc()
        val result = jdbc.queryForObject(
            "SELECT relrowsecurity FROM pg_class WHERE relname = 'match_editorial_presentations'",
            Boolean::class.java,
        )
        assertThat(result).isTrue()
    }

    @Test
    fun `anon role can SELECT from view`() {
        val jdbc = newJdbc()
        jdbc.execute("SET ROLE anon")
        val result = jdbc.query(
            "SELECT club_id, match_id, played_at, presentation FROM dashboard_editorial_presentations LIMIT 1"
        ) { rs, _ -> rs.getString("club_id") }
        assertThat(result).isNotNull()
    }

    @Test
    fun `anon role CANNOT INSERT into table`() {
        val jdbc = newJdbc()
        jdbc.execute("SET ROLE anon")
        assertThatThrownBy {
            jdbc.update(
                """
                INSERT INTO match_editorial_presentations
                    (club_id, match_id, played_at, schema_version, phrase_bank_version, presentation)
                VALUES (?, ?, now(), 1, 'test', '{}'::jsonb)
                """.trimIndent(),
                "club-ins",
                "match-ins",
            )
        }.rootCause().hasMessageContaining("permission denied")
    }

    @Test
    fun `anon role CANNOT UPDATE table`() {
        val jdbc = newJdbc()
        jdbc.update(
            """
            INSERT INTO match_editorial_presentations
                (club_id, match_id, played_at, schema_version, phrase_bank_version, presentation)
            VALUES (?, ?, now(), 1, 'test-upd', '{}'::jsonb)
            ON CONFLICT DO NOTHING
            """.trimIndent(),
            "club-upd",
            "match-upd",
        )
        jdbc.execute("SET ROLE anon")
        assertThatThrownBy {
            jdbc.update(
                "UPDATE match_editorial_presentations SET phrase_bank_version = 'hacked' WHERE club_id = ?",
                "club-upd",
            )
        }.rootCause().hasMessageContaining("permission denied")
    }

    @Test
    fun `anon role CANNOT DELETE from table`() {
        val jdbc = newJdbc()
        jdbc.update(
            """
            INSERT INTO match_editorial_presentations
                (club_id, match_id, played_at, schema_version, phrase_bank_version, presentation)
            VALUES (?, ?, now(), 1, 'test-del', '{}'::jsonb)
            ON CONFLICT DO NOTHING
            """.trimIndent(),
            "club-del",
            "match-del",
        )
        jdbc.execute("SET ROLE anon")
        assertThatThrownBy {
            jdbc.update("DELETE FROM match_editorial_presentations WHERE club_id = ?", "club-del")
        }.rootCause().hasMessageContaining("permission denied")
    }

    @Test
    fun `view enforces RLS with security_invoker`() {
        val jdbc = newJdbc()
        val result = jdbc.queryForObject(
            "SELECT reloptions::text FROM pg_class WHERE relname = 'dashboard_editorial_presentations'",
            String::class.java,
        )
        assertThat(result).contains("security_invoker=true")
    }
}
