package com.eafc26.discordstats.store

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
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.writeText

class JsonCanonicalMatchRepositoryTest {
    @TempDir
    lateinit var tempDir: Path

    private lateinit var repository: JsonCanonicalMatchRepository

    @BeforeEach
    fun setUp() {
        repository = JsonCanonicalMatchRepository(
            jacksonObjectMapper().findAndRegisterModules(),
            tempDir,
            OUR_CLUB,
        )
    }

    @Test
    fun `save and findById roundtrip the complete canonical match`() {
        val canonical = canonicalMatch("match/unsafe id", 1_718_500_000L)

        repository.save(canonical)

        assertThat(repository.findById(OUR_CLUB, canonical.matchId)).isEqualTo(canonical)
        assertThat(Files.list(canonicalDir(OUR_CLUB)).use { it.count() }).isEqualTo(1)
    }

    @Test
    fun `stored envelope exposes stable versions and explicit polymorphic kinds`() {
        val canonical = canonicalMatch("schema-contract", 1_718_500_000L)
        repository.save(canonical)
        val file = Files.list(canonicalDir(OUR_CLUB)).use { it.findFirst().orElseThrow() }
        val json = jacksonObjectMapper().readTree(file.toFile())

        assertThat(json["schemaVersion"].asInt()).isEqualTo(2)
        assertThat(json["engineVersion"].asText()).isEqualTo("1.0.0")
        assertThat(json["generatedAt"].asText()).isEqualTo("2026-07-30T10:00:00Z")
        assertThat(json.has("matchId")).isFalse()
        assertThat(json["interpretation"].has("appliedRules")).isFalse()
        assertThat(file.toFile().readText()).contains("\"kind\"")
    }

    @Test
    fun `saving the same ID atomically replaces its canonical record`() {
        val original = canonicalMatch("same-id", 1_718_500_000L)
        val replacement = original.copy(generatedAt = original.generatedAt.plusSeconds(10))

        repository.save(original)
        repository.save(replacement)

        assertThat(repository.findAll(OUR_CLUB)).containsExactly(replacement)
        assertThat(Files.list(canonicalDir(OUR_CLUB)).use { it.count() }).isEqualTo(1)
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
    fun `missing repository is empty`() {
        assertThat(repository.findById(OUR_CLUB, MatchId("missing"))).isNull()
        assertThat(repository.findAll(OUR_CLUB)).isEmpty()
        assertThat(repository.metadata(OUR_CLUB).matchCount).isZero()
    }

    @Test
    fun `same match ID from two club perspectives is stored and read independently`() {
        val otherClub = ClubId("opponent")
        val ourPerspective = canonicalMatch("shared-match", 1_718_500_000L, OUR_CLUB)
        val otherPerspective = canonicalMatch("shared-match", 1_718_500_000L, otherClub)

        repository.save(ourPerspective)
        repository.save(otherPerspective)

        assertThat(repository.findById(OUR_CLUB, MatchId("shared-match"))).isEqualTo(ourPerspective)
        assertThat(repository.findById(otherClub, MatchId("shared-match"))).isEqualTo(otherPerspective)
        assertThat(repository.findAll(OUR_CLUB)).containsExactly(ourPerspective)
        assertThat(repository.findAll(otherClub)).containsExactly(otherPerspective)
        assertThat(Files.list(canonicalDir(OUR_CLUB)).use { it.count() }).isEqualTo(1)
        assertThat(Files.list(canonicalDir(otherClub)).use { it.count() }).isEqualTo(1)
    }

    @Test
    fun `legacy default-club files are copied into namespace without deleting originals`() {
        val canonical = canonicalMatch("legacy-match", 1_718_500_000L)
        repository.save(canonical)
        val namespacedFile = Files.list(canonicalDir(OUR_CLUB)).use { it.findFirst().orElseThrow() }
        val legacyDir = tempDir.resolve("canonical-matches")
        Files.createDirectories(legacyDir)
        val legacyFile = legacyDir.resolve(namespacedFile.fileName)
        Files.copy(namespacedFile, legacyFile)
        Files.delete(namespacedFile)
        repository = JsonCanonicalMatchRepository(
            jacksonObjectMapper().findAndRegisterModules(),
            tempDir,
            OUR_CLUB,
        )

        assertThat(repository.findAll(OUR_CLUB)).containsExactly(canonical)
        assertThat(legacyFile).exists()
        assertThat(Files.list(canonicalDir(OUR_CLUB)).use { it.count() }).isEqualTo(1)
    }

    @Test
    fun `updated namespaced content remains authoritative over retained legacy copy`() {
        val legacy = canonicalMatch("legacy-updated", 1_718_500_000L)
        repository.save(legacy)
        val namespacedFile = Files.list(canonicalDir(OUR_CLUB)).use { it.findFirst().orElseThrow() }
        val legacyDir = tempDir.resolve("canonical-matches")
        Files.createDirectories(legacyDir)
        Files.copy(namespacedFile, legacyDir.resolve(namespacedFile.fileName))
        val updated = legacy.copy(generatedAt = legacy.generatedAt.plusSeconds(60))
        repository = JsonCanonicalMatchRepository(
            jacksonObjectMapper().findAndRegisterModules(),
            tempDir,
            OUR_CLUB,
        )

        repository.save(updated)

        assertThat(repository.findById(OUR_CLUB, updated.matchId)).isEqualTo(updated)
        assertThat(Files.list(legacyDir).use { it.count() }).isEqualTo(1)
    }

    @Test
    fun `malformed canonical file fails explicitly`() {
        val root = canonicalDir(OUR_CLUB)
        Files.createDirectories(root)
        root.resolve("broken.json").writeText("{not-json}")

        assertThatThrownBy { repository.findAll(OUR_CLUB) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("cannot be read")
    }

    private fun canonicalMatch(
        id: String,
        timestamp: Long,
        perspectiveClubId: ClubId = OUR_CLUB,
    ): CanonicalMatch {
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
                    "defender" to player(
                        "Defender",
                        "8.0",
                        tacklesMade = "5",
                        tackleAttempts = "6",
                    ),
                    "bagre" to player("Bagre", "5.5"),
                )
            ),
        )
        val footballMatch = (EaMatchMapper().map(source) as MatchNormalizationResult.Success).match
        val interpretation = MatchInterpreter().interpret(footballMatch, perspectiveClubId)
        val stories = MatchStoryExtractor().extract(interpretation)
        return CanonicalMatch.current(
            footballMatch,
            interpretation,
            stories,
            generatedAt = Instant.parse("2026-07-30T10:00:00Z"),
        )
    }

    private fun canonicalDir(clubId: ClubId): Path =
        tempDir.resolve("clubs").resolve(clubId.value).resolve("canonical-matches")

    private fun player(
        name: String,
        rating: String,
        goals: String = "0",
        mom: String = "0",
        tacklesMade: String = "2",
        tackleAttempts: String = "4",
    ) = PlayerEntry(
        playerName = name,
        position = "14",
        rating = rating,
        goals = goals,
        assists = "0",
        shots = "3",
        manOfTheMatch = mom,
        passesMade = "18",
        passAttempts = "20",
        tacklesMade = tacklesMade,
        tackleAttempts = tackleAttempts,
        redCards = "0",
        secondsPlayed = "5400",
    )

    private companion object {
        val OUR_CLUB = ClubId("our-club")
    }
}
