package com.eafc26.discordstats.store.postgres

import com.eafc26.discordstats.application.interpretation.MatchInterpreter
import com.eafc26.discordstats.application.story.MatchStoryExtractor
import com.eafc26.discordstats.canonical.CanonicalMatch
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.MatchCompletion
import com.eafc26.discordstats.domain.match.MatchId
import com.eafc26.discordstats.domain.match.PlayerId
import com.eafc26.discordstats.ea.mapping.EaMatchMapper
import com.eafc26.discordstats.ea.mapping.MatchNormalizationResult
import com.eafc26.discordstats.ea.model.ClubDetails
import com.eafc26.discordstats.ea.model.ClubMatchEntry
import com.eafc26.discordstats.ea.model.MatchResponse
import com.eafc26.discordstats.ea.model.PlayerEntry
import com.eafc26.discordstats.store.PostgresCanonicalMatchRepository
import com.eafc26.discordstats.store.PostgresPlayerProfileReadRepository
import com.eafc26.discordstats.presentation.history.HistoricalMatchPresenter
import com.eafc26.discordstats.diagnostics.CanonicalReadDiagnostics
import com.eafc26.discordstats.diagnostics.CanonicalReadOrigin
import com.eafc26.discordstats.diagnostics.CanonicalReadOriginContext
import com.eafc26.discordstats.history.MatchHistoryQuery
import com.eafc26.discordstats.service.MatchHistoryService
import com.eafc26.discordstats.service.PlayerProfileService
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal
import java.time.Instant
import java.nio.file.Files

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
    private lateinit var playerProfileReadRepository: PostgresPlayerProfileReadRepository
    private lateinit var readDiagnostics: CanonicalReadDiagnostics
    private lateinit var readOriginContext: CanonicalReadOriginContext

    @BeforeEach
    fun setUp() {
        readDiagnostics = CanonicalReadDiagnostics()
        readOriginContext = CanonicalReadOriginContext()
        repository = PostgresCanonicalMatchRepository(
            jdbcTemplate,
            jacksonObjectMapper().findAndRegisterModules(),
            readDiagnostics,
            readOriginContext,
        )
        playerProfileReadRepository = PostgresPlayerProfileReadRepository(
            jdbcTemplate,
            readDiagnostics,
            readOriginContext,
        )
        jdbcTemplate.update("DELETE FROM canonical_matches")
    }

    @Test
    fun `repository measures returned canonical data by operation and logical origin`() {
        val first = canonicalMatch("diagnostic-1", 1_718_500_000L)
        val second = canonicalMatch("diagnostic-2", 1_718_600_000L)
        repository.save(first)
        repository.save(second)

        readOriginContext.withOrigin(CanonicalReadOrigin.HISTORY_LIST) { repository.findAll(OUR_CLUB) }
        readOriginContext.withOrigin(CanonicalReadOrigin.DASHBOARD_OVERVIEW) { repository.findRecent(OUR_CLUB, 1) }
        readOriginContext.withOrigin(CanonicalReadOrigin.COMPARISON) { repository.findById(OUR_CLUB, first.matchId) }
        readOriginContext.withOrigin(CanonicalReadOrigin.POLLING_CHECKPOINT) { repository.findMatchIds(OUR_CLUB) }
        readOriginContext.withOrigin(CanonicalReadOrigin.POLLING_CHECKPOINT) { repository.findLatestMatchId(OUR_CLUB) }
        readOriginContext.withOrigin(CanonicalReadOrigin.POLLING_CHECKPOINT) {
            repository.findExistingMatchIds(OUR_CLUB, listOf(first.matchId, MatchId("missing")))
        }
        readOriginContext.withOrigin(CanonicalReadOrigin.LLM_PANORAMA) {
            repository.findRecentMatchIds(OUR_CLUB, 1)
        }
        readOriginContext.withOrigin(CanonicalReadOrigin.DASHBOARD_OVERVIEW) {
            repository.findRecentOverview(OUR_CLUB, 1)
        }
        readOriginContext.withOrigin(CanonicalReadOrigin.HISTORY_LIST) {
            repository.findHistorySummaries(OUR_CLUB)
        }

        val snapshot = readDiagnostics.snapshot()
        val findAll = snapshot.operations.getValue("findAll")
        assertThat(findAll.calls).isEqualTo(1)
        assertThat(findAll.rows).isEqualTo(2)
        assertThat(findAll.estimatedReturnedBytes).isGreaterThan(0)
        assertThat(snapshot.operations.getValue("findRecent").rows).isEqualTo(1)
        assertThat(snapshot.operations.getValue("findById").rows).isEqualTo(1)
        assertThat(snapshot.operations.getValue("findMatchIds").rows).isEqualTo(2)
        assertThat(snapshot.operations.getValue("findLatestMatchId").rows).isEqualTo(1)
        assertThat(snapshot.operations.getValue("findExistingMatchIds").rows).isEqualTo(1)
        assertThat(snapshot.operations.getValue("findRecentMatchIds").rows).isEqualTo(1)
        assertThat(snapshot.operations.getValue("findRecentOverview").rows).isEqualTo(1)
        assertThat(snapshot.operations.getValue("findHistorySummaries").rows).isEqualTo(2)
        assertThat(snapshot.origins.getValue("history.list").estimatedReturnedBytes).isGreaterThan(0)
        assertThat(snapshot.origins.getValue("polling.checkpoint").estimatedReturnedBytes).isGreaterThan(0)
    }

    @Test
    fun `save and findById roundtrip the complete canonical match`() {
        val canonical = canonicalMatch("match-1", 1_718_500_000L)
        repository.save(canonical)
        val found = repository.findById(OUR_CLUB, canonical.matchId)
        assertThat(found).isEqualTo(canonical)
    }

    @Test
    fun `upsert creates on first save`() {
        val canonical = canonicalMatch("new-match", 1_718_500_000L)
        repository.save(canonical)
        assertThat(repository.findById(OUR_CLUB, canonical.matchId)).isNotNull
    }

    @Test
    fun `upsert updates without duplicating on second save`() {
        val original = canonicalMatch("same-id", 1_718_500_000L)
        val replacement = original.copy(generatedAt = original.generatedAt.plusSeconds(10))
        repository.save(original)
        repository.save(replacement)

        assertThat(repository.findAll(OUR_CLUB)).hasSize(1)
        assertThat(repository.findById(OUR_CLUB, MatchId("same-id"))!!.generatedAt)
            .isEqualTo(replacement.generatedAt)
        assertThat(repository.findMatchIds(OUR_CLUB)).containsExactly(MatchId("same-id"))
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

        assertThat(repository.findAll(OUR_CLUB).map { it.matchId.value })
            .containsExactly("a", "b", "old")
    }

    @Test
    fun `findRecent returns only the requested newest records with the same tie break`() {
        val old = canonicalMatch("old", 1_700_000_000L)
        val sameTimeB = canonicalMatch("b", 1_800_000_000L)
        val sameTimeA = canonicalMatch("a", 1_800_000_000L)
        val newest = canonicalMatch("newest", 1_900_000_000L)
        listOf(old, sameTimeB, sameTimeA, newest).forEach(repository::save)

        assertThat(repository.findRecent(OUR_CLUB, 3).map { it.matchId.value })
            .containsExactly("newest", "a", "b")
        assertThat(repository.findRecent(OUR_CLUB, 10).map { it.matchId.value })
            .containsExactly("newest", "a", "b", "old")
        assertThat(repository.findRecent(ClubId("empty-club"), 10)).isEmpty()
    }

    @Test
    fun `findRecent query limits payload retrieval in PostgreSQL`() {
        val source = Files.readString(
            java.nio.file.Path.of("src/main/kotlin/com/eafc26/discordstats/store/PostgresCanonicalMatchRepository.kt")
        )

        assertThat(source).contains(
            "SELECT payload FROM canonical_matches WHERE club_id = ? ORDER BY played_at DESC, match_id ASC LIMIT ?"
        )
    }

    @Test
    fun `findRecentMatchIds applies ordering and limit without reading payload`() {
        val otherClub = ClubId("opponent")
        val ours = (1..12).map { index -> canonicalMatch("match-$index", 1_700_000_000L + index) }
        val other = canonicalMatch("other-match", 1_900_000_000L, otherClub)
        (ours + other).forEach(repository::save)
        jdbcTemplate.update("UPDATE canonical_matches SET payload = ?::jsonb", "{}")

        assertThat(repository.findRecentMatchIds(OUR_CLUB, 0)).isEmpty()
        assertThat(repository.findRecentMatchIds(OUR_CLUB, 3).map { it.value })
            .containsExactly("match-12", "match-11", "match-10")
        assertThat(repository.findRecentMatchIds(OUR_CLUB, 10).map { it.value })
            .containsExactly("match-12", "match-11", "match-10", "match-9", "match-8", "match-7", "match-6", "match-5", "match-4", "match-3")
        assertThat(repository.findRecentMatchIds(OUR_CLUB, 20)).hasSize(12)
        assertThat(repository.findRecentMatchIds(otherClub, 10)).containsExactly(other.matchId)
        assertThat(repository.findRecentMatchIds(ClubId("empty-club"), 10)).isEmpty()
    }

    @Test
    fun `findRecentMatchIds retains canonical match ID tie break`() {
        listOf(
            canonicalMatch("b", 1_800_000_000L),
            canonicalMatch("a", 1_800_000_000L),
            canonicalMatch("old", 1_700_000_000L),
        ).forEach(repository::save)

        assertThat(repository.findRecentMatchIds(OUR_CLUB, 10).map { it.value })
            .containsExactly("a", "b", "old")
    }

    @Test
    fun `findRecentMatchIds query selects only canonical identifiers`() {
        val source = Files.readString(
            java.nio.file.Path.of("src/main/kotlin/com/eafc26/discordstats/store/PostgresCanonicalMatchRepository.kt")
        )

        assertThat(source).contains(
            "SELECT match_id FROM canonical_matches WHERE club_id = ? ORDER BY played_at DESC, match_id ASC LIMIT ?"
        )
    }

    @Test
    fun `findRecentOverview applies ordering and limit without selecting canonical payload`() {
        val otherClub = ClubId("opponent")
        val ours = (1..12).map { index -> canonicalMatch("match-$index", 1_700_000_000L + index) }
        val other = canonicalMatch("other-match", 1_900_000_000L, otherClub)
        (ours + other).forEach(repository::save)
        jdbcTemplate.update("UPDATE canonical_matches SET payload = ?::jsonb", "{}")

        assertThat(repository.findRecentOverview(OUR_CLUB, 0)).isEmpty()
        assertThat(repository.findRecentOverview(OUR_CLUB, 3).map { it.matchId.value })
            .containsExactly("match-12", "match-11", "match-10")
        assertThat(repository.findRecentOverview(OUR_CLUB, 10)).hasSize(10)
        assertThat(repository.findRecentOverview(OUR_CLUB, 20)).hasSize(12)
        assertThat(repository.findRecentOverview(otherClub, 10).map { it.matchId })
            .containsExactly(other.matchId)
        assertThat(repository.findRecentOverview(ClubId("empty-club"), 10)).isEmpty()
    }

    @Test
    fun `findRecentOverview matches full canonical summaries including DNF`() {
        val completed = canonicalMatch("completed", 1_800_000_000L)
        val dnf = canonicalMatch("dnf", 1_900_000_000L, completion = MatchCompletion.dnf(OUR_CLUB))
        repository.save(completed)
        repository.save(dnf)

        val fullSummaries = repository.findRecent(OUR_CLUB, 10).map(HistoricalMatchPresenter::summary)
        val overviewSummaries = repository.findRecentOverview(OUR_CLUB, 10).map(HistoricalMatchPresenter::summary)

        assertThat(overviewSummaries).containsExactlyElementsOf(fullSummaries)
        assertThat(overviewSummaries.first().completionStatus).isEqualTo("DNF")
        assertThat(overviewSummaries.first().dnfClubId).isEqualTo(OUR_CLUB.value)
    }

    @Test
    fun `findRecentOverview retains canonical match ID tie break`() {
        listOf(
            canonicalMatch("b", 1_800_000_000L),
            canonicalMatch("a", 1_800_000_000L),
            canonicalMatch("old", 1_700_000_000L),
        ).forEach(repository::save)

        assertThat(repository.findRecentOverview(OUR_CLUB, 10).map { it.matchId.value })
            .containsExactly("a", "b", "old")
    }

    @Test
    fun `findRecentOverview query selects only scalar overview facts`() {
        val source = Files.readString(
            java.nio.file.Path.of("src/main/kotlin/com/eafc26/discordstats/store/PostgresCanonicalMatchRepository.kt")
        )
        val overviewQuery = source
            .substringAfter("override fun findRecentOverview")
            .substringBefore("override fun findAll")

        assertThat(overviewQuery).contains("SELECT\n                match_id,")
        assertThat(overviewQuery).contains("payload #>> '{footballMatch,completion,status}'")
        assertThat(overviewQuery).doesNotContain("SELECT payload")
    }

    @Test
    fun `findHistorySummaries returns a complete ordered club-scoped projection without loading payload`() {
        val otherClub = ClubId("opponent")
        val ours = (1..101).map { index -> canonicalMatch("match-$index", 1_700_000_000L + index) }
        val other = canonicalMatch("other-match", 1_900_000_000L, perspectiveClubId = otherClub)
        (ours + other).forEach(repository::save)

        // The scalar columns are sufficient. An accidental CanonicalMatch read would fail on this payload.
        jdbcTemplate.update("UPDATE canonical_matches SET payload = ?::jsonb", "{}")

        val summaries = readOriginContext.withOrigin(CanonicalReadOrigin.HISTORY_LIST) {
            repository.findHistorySummaries(OUR_CLUB)
        }

        assertThat(summaries).hasSize(101)
        assertThat(summaries.map { it.matchId.value }.take(3)).containsExactly("match-101", "match-100", "match-99")
        assertThat(summaries).allMatch { it.perspectiveClubId == OUR_CLUB }
        assertThat(repository.findHistorySummaries(otherClub).map { it.matchId })
            .containsExactly(other.matchId)
        assertThat(repository.findHistorySummaries(ClubId("empty-club"))).isEmpty()

        val snapshot = readDiagnostics.snapshot()
        val operation = snapshot.operations.getValue("findHistorySummaries")
        assertThat(operation.calls).isEqualTo(3)
        assertThat(operation.rows).isEqualTo(102)
        assertThat(operation.estimatedReturnedBytes).isGreaterThan(0)
        assertThat(snapshot.origins.getValue("history.list").rows).isEqualTo(101)
    }

    @Test
    fun `findHistorySummaries preserves canonical summary semantics through legacy relational fallbacks`() {
        val normalWin = canonicalMatch("win", 1_800_000_000L, ourScore = "3", opponentScore = "1")
        val normalLoss = canonicalMatch("loss", 1_800_000_100L, ourScore = "1", opponentScore = "3")
        val normalDraw = canonicalMatch("draw", 1_800_000_200L, ourScore = "2", opponentScore = "2")
        val dnfByUs = canonicalMatch(
            "dnf-us",
            1_800_000_300L,
            completion = MatchCompletion.dnf(OUR_CLUB),
        )
        val dnfByOpponent = canonicalMatch(
            "dnf-opponent",
            1_800_000_400L,
            completion = MatchCompletion.dnf(ClubId("opponent")),
        )
        val unknown = canonicalMatch("unknown", 1_800_000_500L, completion = MatchCompletion.UNKNOWN)
        listOf(normalWin, normalLoss, normalDraw, dnfByUs, dnfByOpponent, unknown).forEach(repository::save)

        val expected = repository.findAll(OUR_CLUB).map(HistoricalMatchPresenter::summary)

        jdbcTemplate.update(
            """
            UPDATE canonical_matches
            SET opponent_club_id = NULL,
                match_type = NULL,
                outcome = NULL,
                our_score = NULL,
                opponent_score = NULL,
                our_club_name = NULL,
                opponent_club_name = NULL
            WHERE club_id = ?
            """.trimIndent(),
            OUR_CLUB.value,
        )
        jdbcTemplate.update(
            """
            UPDATE canonical_matches
            SET payload = payload #- '{footballMatch,completion}'
            WHERE club_id = ? AND match_id = ?
            """.trimIndent(),
            OUR_CLUB.value,
            unknown.matchId.value,
        )

        val summaries = repository.findHistorySummaries(OUR_CLUB).map(HistoricalMatchPresenter::summary)

        assertThat(summaries).containsExactlyElementsOf(expected)
        assertThat(summaries.map { it.outcome.code }).contains("WIN", "LOSS", "DRAW")
        assertThat(summaries.first { it.matchId == "dnf-us" }.dnfClubId).isEqualTo(OUR_CLUB.value)
        assertThat(summaries.first { it.matchId == "dnf-opponent" }.dnfClubId).isEqualTo("opponent")
        assertThat(summaries.first { it.matchId == "unknown" }.completionStatus).isEqualTo("UNKNOWN")
    }

    @Test
    fun `findHistorySummaries retains canonical match ID tie break`() {
        listOf(
            canonicalMatch("b", 1_800_000_000L),
            canonicalMatch("a", 1_800_000_000L),
            canonicalMatch("old", 1_700_000_000L),
        ).forEach(repository::save)

        assertThat(repository.findHistorySummaries(OUR_CLUB).map { it.matchId.value })
            .containsExactly("a", "b", "old")
    }

    @Test
    fun `findHistorySummaries query selects scalar facts rather than canonical payload`() {
        val source = Files.readString(
            java.nio.file.Path.of("src/main/kotlin/com/eafc26/discordstats/store/PostgresCanonicalMatchRepository.kt")
        )
        val historyQuery = source
            .substringAfter("override fun findHistorySummaries")
            .substringBefore("override fun findRecent")

        assertThat(historyQuery).contains("SELECT\n                match_id,")
        assertThat(historyQuery).contains("payload #>> '{footballMatch,completion,status}'")
        assertThat(historyQuery).contains("jsonb_array_elements(payload->'footballMatch'->'participants')")
        assertThat(historyQuery).doesNotContain("SELECT payload")
        assertThat(historyQuery).contains("ORDER BY played_at DESC, match_id ASC")
    }

    @Test
    fun `findMatchIds reads only identifiers without deserializing payloads`() {
        val canonical = canonicalMatch("lightweight-id", 1_700_000_000L)
        repository.save(canonical)
        jdbcTemplate.update(
            "UPDATE canonical_matches SET payload = ?::jsonb WHERE club_id = ? AND match_id = ?",
            "{}",
            OUR_CLUB.value,
            canonical.matchId.value,
        )

        assertThat(repository.findMatchIds(OUR_CLUB)).containsExactly(canonical.matchId)
    }

    @Test
    fun `findMatchIds is empty and scoped to the requested club`() {
        val otherClub = ClubId("other-club")
        val ours = canonicalMatch("our-match", 1_700_000_000L)
        repository.save(ours)
        jdbcTemplate.update(
            """
            INSERT INTO canonical_matches
                (match_id, club_id, opponent_club_id, played_at, match_type,
                 canonical_schema_version, payload, created_at, updated_at,
                 outcome, our_score, opponent_score, our_club_name, opponent_club_name)
            SELECT ?, ?, opponent_club_id, played_at, match_type,
                   canonical_schema_version, payload, created_at, updated_at,
                   outcome, our_score, opponent_score, our_club_name, opponent_club_name
            FROM canonical_matches
            WHERE club_id = ? AND match_id = ?
            """.trimIndent(),
            "other-match",
            otherClub.value,
            OUR_CLUB.value,
            ours.matchId.value,
        )

        assertThat(repository.findMatchIds(OUR_CLUB)).containsExactly(ours.matchId)
        assertThat(repository.findMatchIds(otherClub)).containsExactly(MatchId("other-match"))
        assertThat(repository.findMatchIds(ClubId("empty-club"))).isEmpty()
    }

    @Test
    fun `findLatestMatchId is lightweight and uses canonical ordering`() {
        val old = canonicalMatch("old", 1_700_000_000L)
        val sameTimeB = canonicalMatch("b", 1_800_000_000L)
        val sameTimeA = canonicalMatch("a", 1_800_000_000L)
        listOf(old, sameTimeB, sameTimeA).forEach(repository::save)
        jdbcTemplate.update(
            "UPDATE canonical_matches SET payload = ?::jsonb WHERE club_id = ?",
            "{}",
            OUR_CLUB.value,
        )

        assertThat(repository.findLatestMatchId(OUR_CLUB)).isEqualTo(MatchId("a"))
        assertThat(repository.findLatestMatchId(ClubId("empty-club"))).isNull()
    }

    @Test
    fun `findExistingMatchIds reads only bounded candidate identities and isolates clubs`() {
        val otherClub = ClubId("other-club")
        val ours = canonicalMatch("shared", 1_700_000_000L)
        repository.save(ours)
        jdbcTemplate.update(
            "UPDATE canonical_matches SET payload = ?::jsonb WHERE club_id = ? AND match_id = ?",
            "{}",
            OUR_CLUB.value,
            ours.matchId.value,
        )
        jdbcTemplate.update(
            """
            INSERT INTO canonical_matches
                (match_id, club_id, opponent_club_id, played_at, match_type,
                 canonical_schema_version, payload, created_at, updated_at,
                 outcome, our_score, opponent_score, our_club_name, opponent_club_name)
            SELECT ?, ?, opponent_club_id, played_at, match_type,
                   canonical_schema_version, payload, created_at, updated_at,
                   outcome, our_score, opponent_score, our_club_name, opponent_club_name
            FROM canonical_matches
            WHERE club_id = ? AND match_id = ?
            """.trimIndent(),
            "other-only",
            otherClub.value,
            OUR_CLUB.value,
            ours.matchId.value,
        )

        assertThat(repository.findExistingMatchIds(
            OUR_CLUB,
            listOf(MatchId("missing"), ours.matchId, ours.matchId, MatchId("other-only")),
        )).containsExactly(ours.matchId)
        assertThat(repository.findExistingMatchIds(
            otherClub,
            listOf(ours.matchId, MatchId("other-only")),
        )).containsExactly(MatchId("other-only"))
        assertThat(repository.findExistingMatchIds(OUR_CLUB, emptyList())).isEmpty()
    }

    @Test
    fun `bounded polling lookup does not select canonical payload`() {
        val source = Files.readString(
            java.nio.file.Path.of("src/main/kotlin/com/eafc26/discordstats/store/PostgresCanonicalMatchRepository.kt")
        )

        assertThat(source).contains(
            "SELECT match_id FROM canonical_matches WHERE club_id = ? AND match_id IN (\$placeholders)"
        )
    }

    @Test
    fun `findById returns null for missing match`() {
        assertThat(repository.findById(OUR_CLUB, MatchId("missing"))).isNull()
    }

    @Test
    fun `metadata describes stored versions and time range`() {
        val old = canonicalMatch("old", 1_700_000_000L)
        val recent = canonicalMatch("recent", 1_800_000_000L)
            .copy(generatedAt = Instant.parse("2026-07-30T12:00:00Z"))
        repository.save(old)
        repository.save(recent)

        val metadata = repository.metadata(OUR_CLUB)

        assertThat(metadata.matchCount).isEqualTo(2)
        assertThat(metadata.oldestMatchAt).isEqualTo(old.footballMatch.playedAt)
        assertThat(metadata.newestMatchAt).isEqualTo(recent.footballMatch.playedAt)
        assertThat(metadata.lastGeneratedAt).isEqualTo(recent.generatedAt)
        assertThat(metadata.schemaVersions).containsExactly(CanonicalMatch.CURRENT_SCHEMA_VERSION)
        assertThat(metadata.engineVersions).containsExactly(CanonicalMatch.CURRENT_ENGINE_VERSION)
    }

    @Test
    fun `metadata returns zero counts on empty table`() {
        val metadata = repository.metadata(OUR_CLUB)
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
        assertThat(repository.findAll(OUR_CLUB)).hasSize(2)
        assertThat(repository.findById(OUR_CLUB, MatchId("match-1"))).isEqualTo(match1)
        assertThat(repository.findById(OUR_CLUB, MatchId("match-2"))).isEqualTo(match2)
    }

    @Test
    fun `same match ID from two club perspectives remains isolated`() {
        val otherClub = ClubId("opponent")
        val ourPerspective = canonicalMatch("shared-match", 1_718_500_000L, OUR_CLUB)
        val otherPerspective = canonicalMatch("shared-match", 1_718_500_000L, otherClub)

        repository.save(ourPerspective)
        repository.save(otherPerspective)

        assertThat(repository.findById(OUR_CLUB, MatchId("shared-match"))).isEqualTo(ourPerspective)
        assertThat(repository.findById(otherClub, MatchId("shared-match"))).isEqualTo(otherPerspective)
        assertThat(repository.findAll(OUR_CLUB)).containsExactly(ourPerspective)
        assertThat(repository.findAll(otherClub)).containsExactly(otherPerspective)
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM canonical_matches WHERE match_id = ?",
            Int::class.java,
            "shared-match",
        )).isEqualTo(2)
        assertThat(jdbcTemplate.queryForList(
            "SELECT club_id FROM player_match_stats WHERE match_id = ? ORDER BY club_id, player_id",
            String::class.java,
            "shared-match",
        )).containsExactly("opponent", OUR_CLUB.value, OUR_CLUB.value, OUR_CLUB.value)
        assertThat(jdbcTemplate.queryForList(
            "SELECT club_id FROM dashboard_match_detail WHERE match_id = ? ORDER BY club_id",
            String::class.java,
            "shared-match",
        )).containsExactly("opponent", OUR_CLUB.value)
        assertThat(jdbcTemplate.queryForList(
            "SELECT club_id FROM dashboard_player_stats WHERE match_id = ? ORDER BY club_id, player_id",
            String::class.java,
            "shared-match",
        )).containsExactly("opponent", OUR_CLUB.value, OUR_CLUB.value, OUR_CLUB.value)
    }

    @Test
    fun `player stats foreign key requires matching club and match identity`() {
        repository.save(canonicalMatch("foreign-key-match", 1_718_500_000L))

        assertThatThrownBy {
            jdbcTemplate.update(
                """INSERT INTO player_match_stats
                    (club_id, match_id, player_id, played_at)
                    VALUES (?, ?, ?, now())""".trimIndent(),
                "another-club",
                "foreign-key-match",
                "foreign-player",
            )
        }.hasRootCauseInstanceOf(java.sql.SQLException::class.java)
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
        assertThat(mvp["advanced_coverage"]).isEqualTo("UNAVAILABLE")
        assertThat(mvp["advanced_dribbles_completed"]).isNull()
        assertThat(mvp["advanced_beats"]).isNull()
        assertThat(mvp["duration_seconds"]).isEqualTo(5_400)
    }

    @Test
    fun `player profile projection preserves canonical profile facts without selecting payload`() {
        val complete = canonicalMatch(
            "complete", 1_718_500_000L,
            proNames = mapOf("MVP" to "MVP Pro"),
            extraPlayers = 15,
        )
        val unknown = canonicalMatch("unknown", 1_718_500_100L, completion = MatchCompletion.UNKNOWN)
        val partial = canonicalMatch("partial", 1_718_500_150L).withMvpPartialStats()
        val dnf = canonicalMatch("dnf", 1_718_500_200L, completion = MatchCompletion.dnf(OUR_CLUB))
        listOf(complete, unknown, partial, dnf).forEach(repository::save)

        val appearances = playerProfileReadRepository.findAppearances(OUR_CLUB)
        val mvp = appearances.first { it.matchId == MatchId("complete") && it.playerId == PlayerId("mvp") }

        assertThat(mvp.proName).isEqualTo("MVP Pro")
        assertThat(mvp.preferredDisplayName).isEqualTo("MVP Pro")
        assertThat(mvp.awards).containsExactly(com.eafc26.discordstats.domain.interpretation.AwardType.CRAQUE)
        assertThat(appearances.first { it.matchId == MatchId("unknown") }.completion).isEqualTo(MatchCompletion.UNKNOWN)
        assertThat(appearances.first { it.matchId == MatchId("dnf") }.completion).isEqualTo(MatchCompletion.dnf(OUR_CLUB))
        assertThat(appearances.first { it.matchId == MatchId("partial") && it.playerId == PlayerId("mvp") })
            .extracting({ it.rating }, { it.goals }, { it.assists })
            .containsExactly(null, null, null)

        val canonicalHistory = mock<MatchHistoryService>()
        val canonicalMatches = listOf(dnf, partial, unknown, complete)
        whenever(canonicalHistory.list(OUR_CLUB)).thenReturn(canonicalMatches)
        whenever(canonicalHistory.list(OUR_CLUB, MatchHistoryQuery(playerId = PlayerId("mvp")))).thenReturn(canonicalMatches)
        val canonicalProfiles = PlayerProfileService(canonicalHistory).listProfiles(OUR_CLUB)
        val canonicalDetail = PlayerProfileService(canonicalHistory).findById(OUR_CLUB, PlayerId("mvp"))

        readDiagnostics.reset()
        val optimizedHistory = mock<MatchHistoryService>()
        val optimizedProfiles = PlayerProfileService(
            optimizedHistory,
            readOriginContext,
            playerProfileReadRepository,
        ).listProfiles(OUR_CLUB)
        val optimizedDetail = PlayerProfileService(
            optimizedHistory,
            readOriginContext,
            playerProfileReadRepository,
        ).findById(OUR_CLUB, PlayerId("mvp"))

        assertThat(optimizedProfiles.map(::normalizeDecimalScale))
            .containsExactlyElementsOf(canonicalProfiles.map(::normalizeDecimalScale))
        assertThat(optimizedProfiles).hasSize(18)
        assertThat(optimizedDetail?.let(::normalizeDecimalScale))
            .isEqualTo(canonicalDetail?.let(::normalizeDecimalScale))
        verifyNoInteractions(optimizedHistory)
        val diagnostics = readDiagnostics.snapshot()
        assertThat(diagnostics.operations.getValue("findPlayerProfileAppearances").calls).isEqualTo(2)
        assertThat(diagnostics.operations).doesNotContainKey("findAll")
        assertThat(diagnostics.origins.getValue("players").rows).isEqualTo(appearances.size + 4L)

        val source = Files.readString(
            java.nio.file.Path.of("src/main/kotlin/com/eafc26/discordstats/store/PostgresPlayerProfileReadRepository.kt")
        ).substringAfter("private fun query").substringBefore("private fun readAppearance")
        assertThat(source).doesNotContain("SELECT payload")
        assertThat(source).contains("player_match_stats", "cm.payload #>>")
    }

    @Test
    fun `player selector index aggregates eligible history without transferring canonical payload`() {
        val complete = canonicalMatch("index-complete", 1_718_500_000L)
        val unknown = canonicalMatch("index-unknown", 1_718_500_100L, completion = MatchCompletion.UNKNOWN)
        val dnf = canonicalMatch("index-dnf", 1_718_500_200L, completion = MatchCompletion.dnf(OUR_CLUB))
        listOf(complete, unknown, dnf).forEach(repository::save)

        readDiagnostics.reset()
        val index = playerProfileReadRepository.findPlayerIndex(OUR_CLUB)

        assertThat(index).hasSize(3)
        assertThat(index.first { it.playerId == PlayerId("mvp") })
            .extracting({ it.matchCount }, { it.ratedMatchCount }, { it.averageRating })
            .containsExactly(2, 2, BigDecimal("9.20"))
        assertThat(index).allSatisfy { entry ->
            assertThat(entry.matchCount).isEqualTo(2)
        }

        val diagnostics = readDiagnostics.snapshot()
        assertThat(diagnostics.operations.getValue("findPlayerProfileIndex"))
            .extracting({ it.calls }, { it.rows })
            .containsExactly(1L, 3L)
        assertThat(diagnostics.operations).doesNotContainKey("findPlayerProfileAppearances")

        val source = Files.readString(
            java.nio.file.Path.of("src/main/kotlin/com/eafc26/discordstats/store/PostgresPlayerProfileReadRepository.kt")
        ).substringAfter("override fun findPlayerIndex").substringBefore("override fun findAppearances")
        assertThat(source).doesNotContain("SELECT payload")
        assertThat(source).contains("GROUP BY ps.player_id")
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
    fun `player profile projection preserves match ID tie breaking and club isolation`() {
        val timestamp = 1_718_500_000L
        val laterId = canonicalMatch("match-b", timestamp)
        val earlierId = canonicalMatch("match-a", timestamp)
        val otherPerspective = canonicalMatch(
            "other-club-match",
            timestamp,
            perspectiveClubId = ClubId("opponent"),
            opponentPlayerId = "mvp",
        )
        listOf(laterId, earlierId, otherPerspective).forEach(repository::save)

        assertThat(playerProfileReadRepository.findAppearances(OUR_CLUB, PlayerId("mvp")).map { it.matchId.value })
            .containsExactly("match-a", "match-b")
        assertThat(playerProfileReadRepository.findAppearances(OUR_CLUB))
            .noneMatch { it.matchId == MatchId("other-club-match") }
        assertThat(playerProfileReadRepository.findAppearances(ClubId("opponent"), PlayerId("mvp")).map { it.matchId.value })
            .containsExactly("other-club-match")
    }

    @Test
    fun `indexable fields are populated from canonical match`() {
        val match = canonicalMatch("indexed-test", 1_718_500_000L)
        repository.save(match)
        val row = jdbcTemplate.queryForMap("SELECT club_id, opponent_club_id, played_at, match_type, canonical_schema_version FROM canonical_matches WHERE match_id = ?", "indexed-test")
        assertThat(row["club_id"]).isEqualTo(OUR_CLUB.value)
        assertThat(row["opponent_club_id"]).isEqualTo("opponent")
        assertThat(row["canonical_schema_version"]).isEqualTo(2)
    }

    private fun canonicalMatch(
        id: String,
        timestamp: Long,
        perspectiveClubId: ClubId = OUR_CLUB,
        completion: MatchCompletion? = null,
        ourScore: String = "3",
        opponentScore: String = "1",
        proNames: Map<String, String> = emptyMap(),
        extraPlayers: Int = 0,
        opponentPlayerId: String = "opponent-player",
    ): CanonicalMatch {
        val ourPlayers = linkedMapOf(
            "mvp" to player("MVP", "9.2", goals = "2", mom = "1"),
            "defender" to player("Defender", "8.0", tacklesMade = "5", tackleAttempts = "6"),
            "bagre" to player("Bagre", "5.5"),
        )
        repeat(extraPlayers) { index ->
            ourPlayers["extra-$index"] = player("Extra $index", "7.0")
        }
        val source = MatchResponse(
            matchId = id,
            timestamp = timestamp,
            matchType = "leagueMatch",
            clubs = linkedMapOf(
                OUR_CLUB.value to ClubMatchEntry(
                    details = ClubDetails("Our FC", OUR_CLUB.value),
                    score = ourScore,
                    result = if (ourScore.toInt() > opponentScore.toInt()) "1" else if (ourScore == opponentScore) "2" else "0",
                ),
                "opponent" to ClubMatchEntry(
                    details = ClubDetails("Opponent FC", "opponent"),
                    score = opponentScore,
                    result = if (opponentScore.toInt() > ourScore.toInt()) "1" else if (ourScore == opponentScore) "2" else "0",
                ),
            ),
            players = mapOf(
                OUR_CLUB.value to ourPlayers,
                "opponent" to linkedMapOf(
                    opponentPlayerId to player("Opponent Player", "7.0"),
                ),
            ),
        )
        val footballMatch = (EaMatchMapper().map(source, proNames) as MatchNormalizationResult.Success).match
            .let { mapped -> completion?.let { mapped.copy(completion = it) } ?: mapped }
        val interpretation = MatchInterpreter().interpret(footballMatch, perspectiveClubId)
        val stories = MatchStoryExtractor().extract(interpretation)
        return CanonicalMatch.current(footballMatch, interpretation, stories, generatedAt = Instant.parse("2026-07-30T10:00:00Z"))
    }

    private fun CanonicalMatch.withMvpPartialStats(): CanonicalMatch = copy(
        footballMatch = footballMatch.copy(
            participants = footballMatch.participants.map { participant ->
                if (participant.club.id != OUR_CLUB) participant else participant.copy(
                    players = participant.players.map { performance ->
                        if (performance.player.id != PlayerId("mvp")) performance else performance.copy(
                            rating = null,
                            attacking = performance.attacking.copy(goals = null, assists = null),
                        )
                    },
                )
            },
        ),
    )

    private fun player(
        name: String, rating: String, goals: String = "0", mom: String = "0",
        tacklesMade: String = "2", tackleAttempts: String = "4",
    ) = PlayerEntry(
        playerName = name, position = "14", rating = rating, goals = goals,
        assists = "0", shots = "3", manOfTheMatch = mom, passesMade = "18",
        passAttempts = "20", tacklesMade = tacklesMade, tackleAttempts = tackleAttempts,
        redCards = "0", secondsPlayed = "5400",
    )

    private fun normalizeDecimalScale(profile: com.eafc26.discordstats.profile.PlayerProfile) = profile.copy(
        averageRating = profile.averageRating?.stripTrailingZeros(),
        recentMatches = profile.recentMatches.map { match ->
            match.copy(rating = match.rating?.stripTrailingZeros())
        },
    )
}
