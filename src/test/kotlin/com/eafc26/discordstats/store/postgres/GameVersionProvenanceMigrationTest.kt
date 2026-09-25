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

/**
 * Regression for V21__add_game_version_provenance.sql, mirroring the V9 club-scope
 * migration precedent ([CanonicalClubScopeMigrationTest]).
 *
 * Proves, against a real PostgreSQL container:
 *  - pre-V21 (FC26-only) rows survive the migration untouched, defaulted to game_version='FC26';
 *  - the recomposed primary keys/unique constraints allow a genuinely distinct FC27
 *    identity to coexist with an FC26 row sharing the same club_id and match_id, without
 *    collision or data loss;
 *  - writing the real Worshipers FC (486460) / match 16540167170290 identity twice
 *    (an upsert, as production acquisition would perform) never produces a duplicate row.
 */
@Testcontainers
@EnabledIf("isDockerAvailable")
class GameVersionProvenanceMigrationTest {

    @Test
    fun `V21 defaults legacy rows to FC26 and isolates FC27 identity without collision or duplication`() {
        val dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        val jdbc = JdbcTemplate(dataSource)
        jdbc.execute("DO \$\$ BEGIN IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'anon') THEN CREATE ROLE anon NOLOGIN; END IF; END \$\$")

        // Migrate to the last schema version before V21, then seed FC26-only history
        // exactly as it exists in production today (no game_version column yet).
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .target(MigrationVersion.fromVersion("20"))
            .load()
            .migrate()

        jdbc.update(
            """INSERT INTO monitored_clubs (club_id, display_name, platform, monitoring_enabled)
                VALUES (?, ?, ?, ?)""".trimIndent(),
            "11262883", "Escolinha BF", "common-gen5", true,
        )
        val legacyPayload = """{"schemaVersion":1,"legacy":"escolinha-history"}"""
        jdbc.update(
            """INSERT INTO canonical_matches
                (match_id, club_id, opponent_club_id, played_at, match_type, canonical_schema_version, payload)
                VALUES (?, ?, ?, now(), ?, ?, ?::jsonb)""".trimIndent(),
            "legacy-match", "11262883", "opponent-club", "leagueMatch", 1, legacyPayload,
        )
        jdbc.update(
            """INSERT INTO player_match_stats (club_id, match_id, player_id, platform_name, played_at)
                VALUES (?, ?, ?, ?, now())""".trimIndent(),
            "11262883", "legacy-match", "legacy-player", "Legacy Player",
        )
        jdbc.update(
            """INSERT INTO explorer_observations (club_id, match_id, player_id, phrase, observed_count)
                VALUES (?, ?, ?, ?, ?)""".trimIndent(),
            "11262883", "legacy-match", "legacy-player", "Bom passe", 1,
        )

        val beforePayloadHash = sha256(
            jdbc.queryForObject(
                "SELECT payload::text FROM canonical_matches WHERE club_id = ? AND match_id = ?",
                String::class.java, "11262883", "legacy-match",
            ),
        )
        val beforeCanonicalCount = jdbc.queryForObject("SELECT COUNT(*) FROM canonical_matches", Int::class.java)
        val beforePlayerStatsCount = jdbc.queryForObject("SELECT COUNT(*) FROM player_match_stats", Int::class.java)
        val beforeObservationCount = jdbc.queryForObject("SELECT COUNT(*) FROM explorer_observations", Int::class.java)

        // Apply V21 (and any later migrations) on top of the seeded FC26 history.
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate()

        // --- FC26 legacy rows: untouched, defaulted, not reprocessed ---
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM canonical_matches", Int::class.java))
            .isEqualTo(beforeCanonicalCount)
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM player_match_stats", Int::class.java))
            .isEqualTo(beforePlayerStatsCount)
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM explorer_observations", Int::class.java))
            .isEqualTo(beforeObservationCount)

        val afterPayloadHash = sha256(
            jdbc.queryForObject(
                "SELECT payload::text FROM canonical_matches WHERE game_version = 'FC26' AND club_id = ? AND match_id = ?",
                String::class.java, "11262883", "legacy-match",
            ),
        )
        assertThat(afterPayloadHash).isEqualTo(beforePayloadHash)

        assertThat(
            jdbc.queryForObject(
                "SELECT game_version FROM monitored_clubs WHERE club_id = ?",
                String::class.java, "11262883",
            ),
        ).isEqualTo("FC26")
        assertThat(
            jdbc.queryForObject(
                "SELECT game_version FROM player_match_stats WHERE club_id = ? AND match_id = ? AND player_id = ?",
                String::class.java, "11262883", "legacy-match", "legacy-player",
            ),
        ).isEqualTo("FC26")
        assertThat(
            jdbc.queryForObject(
                "SELECT game_version FROM explorer_observations WHERE club_id = ? AND match_id = ? AND player_id = ? AND phrase = ?",
                String::class.java, "11262883", "legacy-match", "legacy-player", "Bom passe",
            ),
        ).isEqualTo("FC26")

        assertThat(primaryKeyColumns(jdbc, "canonical_matches"))
            .containsExactly("game_version", "club_id", "match_id")
        assertThat(primaryKeyColumns(jdbc, "player_match_stats"))
            .containsExactly("game_version", "club_id", "match_id", "player_id")
        assertThat(
            jdbc.queryForObject(
                "SELECT column_default FROM information_schema.columns WHERE table_name = 'canonical_matches' AND column_name = 'game_version'",
                String::class.java,
            ),
        ).contains("FC26")
        assertThat(
            checkConstraintDefinition(jdbc, "canonical_matches_game_version_check"),
        ).contains("FC26").contains("FC27")

        // --- FC27 identity coexists with the FC26 row sharing the SAME club_id and match_id,
        //     proving the widened primary key genuinely disambiguates by provenance, not just
        //     by accident of distinct club IDs. ---
        jdbc.update(
            """INSERT INTO canonical_matches
                (game_version, match_id, club_id, opponent_club_id, played_at, match_type, canonical_schema_version, payload)
                VALUES (?, ?, ?, ?, now(), ?, ?, ?::jsonb)""".trimIndent(),
            "FC27", "legacy-match", "11262883", "opponent-club", "leagueMatch", 1, """{"note":"same identity, different era"}""",
        )
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM canonical_matches WHERE club_id = ? AND match_id = ?", Int::class.java, "11262883", "legacy-match"))
            .isEqualTo(2)
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM canonical_matches WHERE game_version = 'FC26' AND club_id = ? AND match_id = ?", Int::class.java, "11262883", "legacy-match"))
            .isEqualTo(1)

        // --- Real Worshipers FC identity: persist, then persist again (upsert), never duplicated. ---
        jdbc.update(
            """INSERT INTO monitored_clubs (club_id, display_name, platform, game_version, monitoring_enabled)
                VALUES (?, ?, ?, ?, ?)""".trimIndent(),
            "486460", "Worshipers FC", "common-gen5", "FC27", true,
        )

        fun upsertWorshipersMatch(narrative: String) {
            jdbc.update(
                """INSERT INTO canonical_matches
                    (game_version, match_id, club_id, opponent_club_id, played_at, match_type, canonical_schema_version, payload)
                    VALUES (?, ?, ?, ?, now(), ?, ?, ?::jsonb)
                    ON CONFLICT (game_version, club_id, match_id) DO UPDATE SET payload = EXCLUDED.payload""".trimIndent(),
                "FC27", "16540167170290", "486460", "208428", "leagueMatch", 1, """{"narrative":"$narrative"}""",
            )
            jdbc.update(
                """INSERT INTO player_match_stats (game_version, club_id, match_id, player_id, platform_name, played_at)
                    VALUES (?, ?, ?, ?, ?, now())
                    ON CONFLICT (game_version, club_id, match_id, player_id) DO UPDATE SET platform_name = EXCLUDED.platform_name""".trimIndent(),
                "FC27", "486460", "16540167170290", "1003722789595", "dbeng_bass",
            )
        }
        upsertWorshipersMatch("first acquisition pass")
        upsertWorshipersMatch("second acquisition pass, same identity")

        assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM canonical_matches WHERE game_version = 'FC27' AND club_id = ? AND match_id = ?",
                Int::class.java, "486460", "16540167170290",
            ),
        ).isEqualTo(1)
        assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM player_match_stats WHERE game_version = 'FC27' AND club_id = ? AND match_id = ? AND player_id = ?",
                Int::class.java, "486460", "16540167170290", "1003722789595",
            ),
        ).isEqualTo(1)
        assertThat(
            jdbc.queryForObject(
                "SELECT payload::text FROM canonical_matches WHERE game_version = 'FC27' AND club_id = ? AND match_id = ?",
                String::class.java, "486460", "16540167170290",
            ),
        ).contains("second acquisition pass")

        // The FC26 legacy history remains completely unaffected by the FC27 writes.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM canonical_matches WHERE game_version = 'FC26'", Int::class.java))
            .isEqualTo(beforeCanonicalCount)
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

    private fun checkConstraintDefinition(jdbc: JdbcTemplate, constraintName: String): String = jdbc.queryForObject(
        "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = ?",
        String::class.java,
        constraintName,
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
