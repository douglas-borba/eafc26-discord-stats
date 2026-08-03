package com.eafc26.discordstats.store.postgres

import com.eafc26.discordstats.application.interpretation.MatchInterpreter
import com.eafc26.discordstats.application.story.MatchStoryExtractor
import com.eafc26.discordstats.canonical.CanonicalMatch
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.MatchId
import com.eafc26.discordstats.ea.mapping.EaMatchMapper
import com.eafc26.discordstats.ea.mapping.MatchNormalizationResult
import com.eafc26.discordstats.ea.model.ClubDetails
import com.eafc26.discordstats.ea.model.ClubMatchEntry
import com.eafc26.discordstats.ea.model.MatchResponse
import com.eafc26.discordstats.ea.model.PlayerEntry
import com.eafc26.discordstats.store.PostgresCanonicalMatchRepository
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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
import java.time.Instant

@Testcontainers
@EnabledIf("isDockerAvailable")
class PostgresCanonicalMatchRepositoryTest {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")

        private lateinit var jdbcTemplate: JdbcTemplate

        @JvmStatic
        fun isDockerAvailable(): Boolean = try {
            org.testcontainers.DockerClientFactory.instance().isDockerAvailable
        } catch (_: Exception) { false }

        @BeforeAll
        @JvmStatic
        fun initSchema() {
            val ds = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            JdbcTemplate(ds).execute("DO $$ BEGIN IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'anon') THEN CREATE ROLE anon NOLOGIN; END IF; END $$")
            Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate()
            jdbcTemplate = JdbcTemplate(ds)
        }

        private val OUR_CLUB = ClubId("our-club")
    }

    private lateinit var repository: PostgresCanonicalMatchRepository

    @BeforeEach
    fun setUp() {
        repository = PostgresCanonicalMatchRepository(jdbcTemplate, jacksonObjectMapper().findAndRegisterModules())
        jdbcTemplate.update("DELETE FROM canonical_matches")
    }

    @Test
    fun `save and findById roundtrip the complete canonical match`() {
        val canonical = canonicalMatch("match-1", 1_718_500_000L)
        repository.save(canonical)
        val found = repository.findById(canonical.matchId)
        assertThat(found).isEqualTo(canonical)
    }

    @Test
    fun `upsert creates on first save`() {
        val canonical = canonicalMatch("new-match", 1_718_500_000L)
        repository.save(canonical)
        assertThat(repository.findById(canonical.matchId)).isNotNull
    }

    @Test
    fun `upsert updates without duplicating on second save`() {
        val original = canonicalMatch("same-id", 1_718_500_000L)
        val replacement = original.copy(generatedAt = original.generatedAt.plusSeconds(10))
        repository.save(original)
        repository.save(replacement)

        assertThat(repository.findAll()).hasSize(1)
        assertThat(repository.findById(MatchId("same-id"))!!.generatedAt)
            .isEqualTo(replacement.generatedAt)
    }

    @Test
    fun `created_at is preserved on upsert and updated_at changes`() {
        val match = canonicalMatch("timestamp-test", 1_718_500_000L)
        repository.save(match)

        val firstRow = jdbcTemplate.queryForMap("SELECT created_at, updated_at FROM canonical_matches WHERE match_id = ?", "timestamp-test")
        val createdAt1 = firstRow["created_at"]
        val updatedAt1 = firstRow["updated_at"]

        Thread.sleep(50)
        repository.save(match.copy(generatedAt = match.generatedAt.plusSeconds(1)))

        val secondRow = jdbcTemplate.queryForMap("SELECT created_at, updated_at FROM canonical_matches WHERE match_id = ?", "timestamp-test")
        val createdAt2 = secondRow["created_at"]
        val updatedAt2 = secondRow["updated_at"]

        assertThat(createdAt2).isEqualTo(createdAt1)
        assertThat(updatedAt2).isNotEqualTo(updatedAt1)
    }

    @Test
    fun `findAll returns newest match first with deterministic ID tie break`() {
        val old = canonicalMatch("old", 1_700_000_000L)
        val sameTimeB = canonicalMatch("b", 1_800_000_000L)
        val sameTimeA = canonicalMatch("a", 1_800_000_000L)
        listOf(old, sameTimeB, sameTimeA).forEach(repository::save)

        assertThat(repository.findAll().map { it.matchId.value })
            .containsExactly("a", "b", "old")
    }

    @Test
    fun `findById returns null for missing match`() {
        assertThat(repository.findById(MatchId("missing"))).isNull()
    }

    @Test
    fun `metadata describes stored versions and time range`() {
        val old = canonicalMatch("old", 1_700_000_000L)
        val recent = canonicalMatch("recent", 1_800_000_000L)
            .copy(generatedAt = Instant.parse("2026-07-30T12:00:00Z"))
        repository.save(old)
        repository.save(recent)

        val metadata = repository.metadata()

        assertThat(metadata.matchCount).isEqualTo(2)
        assertThat(metadata.oldestMatchAt).isEqualTo(old.footballMatch.playedAt)
        assertThat(metadata.newestMatchAt).isEqualTo(recent.footballMatch.playedAt)
        assertThat(metadata.lastGeneratedAt).isEqualTo(recent.generatedAt)
        assertThat(metadata.schemaVersions).containsExactly(CanonicalMatch.CURRENT_SCHEMA_VERSION)
        assertThat(metadata.engineVersions).containsExactly(CanonicalMatch.CURRENT_ENGINE_VERSION)
    }

    @Test
    fun `metadata returns zero counts on empty table`() {
        val metadata = repository.metadata()
        assertThat(metadata.matchCount).isZero()
        assertThat(metadata.oldestMatchAt).isNull()
        assertThat(metadata.newestMatchAt).isNull()
    }

    @Test
    fun `different IDs remain separate`() {
        val match1 = canonicalMatch("match-1", 1_718_500_000L)
        val match2 = canonicalMatch("match-2", 1_718_600_000L)
        repository.save(match1)
        repository.save(match2)
        assertThat(repository.findAll()).hasSize(2)
        assertThat(repository.findById(MatchId("match-1"))).isEqualTo(match1)
        assertThat(repository.findById(MatchId("match-2"))).isEqualTo(match2)
    }

    @Test
    fun `save populates denormalized read model columns`() {
        val match = canonicalMatch("denorm-test", 1_718_500_000L)
        repository.save(match)
        val row = jdbcTemplate.queryForMap(
            "SELECT outcome, our_score, opponent_score, our_club_name, opponent_club_name FROM canonical_matches WHERE match_id = ?",
            "denorm-test",
        )
        assertThat(row["outcome"]).isEqualTo("WIN")
        assertThat(row["our_score"]).isEqualTo(3)
        assertThat(row["opponent_score"]).isEqualTo(1)
        assertThat(row["our_club_name"]).isEqualTo("Our FC")
        assertThat(row["opponent_club_name"]).isEqualTo("Opponent FC")
    }

    @Test
    fun `save populates player_match_stats table`() {
        val match = canonicalMatch("player-stats-test", 1_718_500_000L)
        repository.save(match)
        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM player_match_stats WHERE match_id = ?",
            Int::class.java,
            "player-stats-test",
        )
        assertThat(count).isEqualTo(3)

        val mvp = jdbcTemplate.queryForMap(
            "SELECT * FROM player_match_stats WHERE match_id = ? AND player_id = ?",
            "player-stats-test", "mvp",
        )
        assertThat(mvp["platform_name"]).isEqualTo("MVP")
        assertThat(mvp["goals"]).isEqualTo(2)
        assertThat(mvp["man_of_the_match"]).isEqualTo(true)
        assertThat(mvp["rating"]).isNotNull
    }

    @Test
    fun `upsert replaces player_match_stats without duplicates`() {
        val match = canonicalMatch("upsert-players", 1_718_500_000L)
        repository.save(match)
        repository.save(match)
        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM player_match_stats WHERE match_id = ?",
            Int::class.java,
            "upsert-players",
        )
        assertThat(count).isEqualTo(3)
    }

    @Test
    fun `indexable fields are populated from canonical match`() {
        val match = canonicalMatch("indexed-test", 1_718_500_000L)
        repository.save(match)
        val row = jdbcTemplate.queryForMap("SELECT club_id, opponent_club_id, played_at, match_type, canonical_schema_version FROM canonical_matches WHERE match_id = ?", "indexed-test")
        assertThat(row["club_id"]).isEqualTo(OUR_CLUB.value)
        assertThat(row["opponent_club_id"]).isEqualTo("opponent")
        assertThat(row["canonical_schema_version"]).isEqualTo(1)
    }

    private fun canonicalMatch(id: String, timestamp: Long): CanonicalMatch {
        val source = MatchResponse(
            matchId = id,
            timestamp = timestamp,
            matchType = "leagueMatch",
            clubs = linkedMapOf(
                OUR_CLUB.value to ClubMatchEntry(
                    details = ClubDetails("Our FC", OUR_CLUB.value),
                    score = "3",
                    result = "1",
                ),
                "opponent" to ClubMatchEntry(
                    details = ClubDetails("Opponent FC", "opponent"),
                    score = "1",
                    result = "0",
                ),
            ),
            players = mapOf(
                OUR_CLUB.value to linkedMapOf(
                    "mvp" to player("MVP", "9.2", goals = "2", mom = "1"),
                    "defender" to player("Defender", "8.0", tacklesMade = "5", tackleAttempts = "6"),
                    "bagre" to player("Bagre", "5.5"),
                )
            ),
        )
        val footballMatch = (EaMatchMapper().map(source) as MatchNormalizationResult.Success).match
        val interpretation = MatchInterpreter().interpret(footballMatch, OUR_CLUB)
        val stories = MatchStoryExtractor().extract(interpretation)
        return CanonicalMatch.current(footballMatch, interpretation, stories, generatedAt = Instant.parse("2026-07-30T10:00:00Z"))
    }

    private fun player(
        name: String, rating: String, goals: String = "0", mom: String = "0",
        tacklesMade: String = "2", tackleAttempts: String = "4",
    ) = PlayerEntry(
        playerName = name, position = "14", rating = rating, goals = goals,
        assists = "0", shots = "3", manOfTheMatch = mom, passesMade = "18",
        passAttempts = "20", tacklesMade = tacklesMade, tackleAttempts = tackleAttempts,
        redCards = "0", secondsPlayed = "5400",
    )
}
