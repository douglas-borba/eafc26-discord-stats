package com.eafc26.discordstats.service

import com.eafc26.discordstats.application.interpretation.MatchInterpreter
import com.eafc26.discordstats.application.repository.CanonicalMatchRepository
import com.eafc26.discordstats.application.repository.CanonicalRepositoryMetadata
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
import com.eafc26.discordstats.store.JsonCanonicalMatchRepository
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant

class PostgresSyncServiceTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var jsonRepo: JsonCanonicalMatchRepository
    private lateinit var fakePg: RecordingRepository
    private lateinit var syncService: PostgresSyncService

    @BeforeEach
    fun setUp() {
        val mapper = jacksonObjectMapper().findAndRegisterModules()
        jsonRepo = JsonCanonicalMatchRepository(mapper, tempDir)
        fakePg = RecordingRepository()
        syncService = PostgresSyncService(jsonRepo, fakePg)
    }

    @Test
    fun `sync sends all local matches to postgres`() {
        jsonRepo.save(testMatch("m1", 1_700_000_000L))
        jsonRepo.save(testMatch("m2", 1_800_000_000L))

        val result = syncService.sync()

        assertThat(result.found).isEqualTo(2)
        assertThat(result.synced).isEqualTo(2)
        assertThat(result.failures).isEmpty()
        assertThat(result.localCount).isEqualTo(2)
        assertThat(result.remoteCount).isEqualTo(2)
        assertThat(fakePg.store).hasSize(2)
    }

    @Test
    fun `sync is idempotent`() {
        jsonRepo.save(testMatch("m1", 1_700_000_000L))

        syncService.sync()
        val result = syncService.sync()

        assertThat(result.found).isEqualTo(1)
        assertThat(result.synced).isEqualTo(1)
        assertThat(result.failures).isEmpty()
        assertThat(fakePg.store).hasSize(1)
    }

    @Test
    fun `sync handles postgres failure gracefully`() {
        jsonRepo.save(testMatch("m1", 1_700_000_000L))
        fakePg.failOnSave = true

        val result = syncService.sync()

        assertThat(result.found).isEqualTo(1)
        assertThat(result.synced).isEqualTo(0)
        assertThat(result.failures).hasSize(1)
        assertThat(result.failures[0].matchId).isEqualTo("m1")
    }

    @Test
    fun `sync recovers after previous failure`() {
        jsonRepo.save(testMatch("m1", 1_700_000_000L))

        fakePg.failOnSave = true
        syncService.sync()

        fakePg.failOnSave = false
        val result = syncService.sync()

        assertThat(result.synced).isEqualTo(1)
        assertThat(result.failures).isEmpty()
        assertThat(fakePg.store).hasSize(1)
    }

    @Test
    fun `sync does not duplicate on repeated calls`() {
        jsonRepo.save(testMatch("m1", 1_700_000_000L))

        syncService.sync()
        syncService.sync()
        syncService.sync()

        assertThat(fakePg.store).hasSize(1)
        assertThat(fakePg.saveCount).isEqualTo(3)
    }

    @Test
    fun `sync with empty local returns zero counts`() {
        val result = syncService.sync()

        assertThat(result.found).isZero()
        assertThat(result.synced).isZero()
        assertThat(result.failures).isEmpty()
    }

    @Test
    fun `sync updates status fields on success`() {
        jsonRepo.save(testMatch("m1", 1_700_000_000L))

        assertThat(syncService.lastSyncStarted).isNull()
        assertThat(syncService.lastSyncCompleted).isNull()

        syncService.sync()

        assertThat(syncService.lastSyncStarted).isNotNull()
        assertThat(syncService.lastSyncCompleted).isNotNull()
        assertThat(syncService.lastSyncFailed).isNull()
        assertThat(syncService.lastSyncResult).isNotNull()
    }

    @Test
    fun `sync records individual failures without total crash`() {
        jsonRepo.save(testMatch("m1", 1_700_000_000L))
        fakePg.failOnSave = true

        val result = syncService.sync()

        assertThat(syncService.lastSyncStarted).isNotNull()
        assertThat(syncService.lastSyncCompleted).isNotNull()
        assertThat(result.failures).hasSize(1)
    }

    @Test
    fun `sync reports duration`() {
        jsonRepo.save(testMatch("m1", 1_700_000_000L))
        val result = syncService.sync()
        assertThat(result.durationMs).isGreaterThanOrEqualTo(0)
    }

    private fun testMatch(id: String, timestamp: Long): CanonicalMatch {
        val source = MatchResponse(
            matchId = id, timestamp = timestamp, matchType = "leagueMatch",
            clubs = linkedMapOf(
                "club" to ClubMatchEntry(details = ClubDetails("FC", "club"), score = "1", result = "1"),
                "opp" to ClubMatchEntry(details = ClubDetails("Opp", "opp"), score = "0", result = "0"),
            ),
            players = mapOf("club" to linkedMapOf("p1" to PlayerEntry(
                playerName = "P", position = "14", rating = "7.0", goals = "0",
                assists = "0", shots = "1", manOfTheMatch = "0", passesMade = "10",
                passAttempts = "12", tacklesMade = "2", tackleAttempts = "3",
                redCards = "0", secondsPlayed = "5400",
            ))),
        )
        val fm = (EaMatchMapper().map(source) as MatchNormalizationResult.Success).match
        val interp = MatchInterpreter().interpret(fm, ClubId("club"))
        val stories = MatchStoryExtractor().extract(interp)
        return CanonicalMatch.current(fm, interp, stories, Instant.parse("2026-07-30T10:00:00Z"))
    }

    private class RecordingRepository : CanonicalMatchRepository {
        val store = linkedMapOf<MatchId, CanonicalMatch>()
        var saveCount = 0
        var failOnSave = false

        override fun save(match: CanonicalMatch) {
            if (failOnSave) throw RuntimeException("simulated postgres failure")
            saveCount++
            store[match.matchId] = match
        }

        override fun findById(matchId: MatchId) = store[matchId]
        override fun findAll() = store.values.toList()
        override fun metadata() = CanonicalRepositoryMetadata(store.size, null, null, null, emptySet(), emptySet())
    }
}
