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
import com.eafc26.discordstats.scheduler.PostgresSyncScheduler
import com.eafc26.discordstats.store.JsonCanonicalMatchRepository
import com.eafc26.discordstats.store.PostgresCanonicalMatchRepository
import com.eafc26.discordstats.support.defaultClubProvider
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.junit.jupiter.api.io.TempDir
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@Testcontainers
@EnabledIf("isDockerAvailable")
class PostgresSyncIntegrationTest {

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

        private val OUR_CLUB = ClubId("test-club")
    }

    @TempDir
    lateinit var tempDir: Path

    private lateinit var jsonRepo: JsonCanonicalMatchRepository
    private lateinit var pgRepo: PostgresCanonicalMatchRepository
    private lateinit var syncService: PostgresSyncService

    @BeforeEach
    fun setUp() {
        val mapper = jacksonObjectMapper().findAndRegisterModules()
        jsonRepo = JsonCanonicalMatchRepository(mapper, tempDir, OUR_CLUB)
        pgRepo = PostgresCanonicalMatchRepository(jdbcTemplate, mapper)
        syncService = PostgresSyncService(jsonRepo, pgRepo, defaultClubProvider(OUR_CLUB))
        jdbcTemplate.update("DELETE FROM canonical_matches")
    }

    @Test
    fun `sync transfers local fixtures to postgres`() {
        jsonRepo.save(testMatch("fixture-1", 1_700_000_000L))
        jsonRepo.save(testMatch("fixture-2", 1_800_000_000L))

        val result = syncService.sync()

        assertThat(result.found).isEqualTo(2)
        assertThat(result.synced).isEqualTo(2)
        assertThat(result.failures).isEmpty()
        assertThat(result.localCount).isEqualTo(2)
        assertThat(result.remoteCount).isEqualTo(2)
        assertThat(pgRepo.findById(OUR_CLUB, MatchId("fixture-1"))).isNotNull
        assertThat(pgRepo.findById(OUR_CLUB, MatchId("fixture-2"))).isNotNull
    }

    @Test
    fun `sync with postgres unavailable reports failures without crashing`() {
        jsonRepo.save(testMatch("fixture-1", 1_700_000_000L))

        val brokenRepo = PostgresCanonicalMatchRepository(
            JdbcTemplate(DriverManagerDataSource("jdbc:postgresql://localhost:1/gone", "x", "x")),
            jacksonObjectMapper().findAndRegisterModules(),
        )
        val brokenSync = PostgresSyncService(jsonRepo, brokenRepo, defaultClubProvider(OUR_CLUB))

        val result = brokenSync.sync()

        assertThat(result.found).isEqualTo(1)
        assertThat(result.synced).isEqualTo(0)
        assertThat(result.failures).isNotEmpty
        assertThat(brokenSync.lastSyncStarted).isNotNull()
        assertThat(brokenSync.lastSyncCompleted).isNotNull()
        assertThat(jsonRepo.findAll(OUR_CLUB)).hasSize(1)
    }

    @Test
    fun `recovery after failure syncs pending fixtures`() {
        jsonRepo.save(testMatch("fixture-1", 1_700_000_000L))

        val brokenRepo = PostgresCanonicalMatchRepository(
            JdbcTemplate(DriverManagerDataSource("jdbc:postgresql://localhost:1/gone", "x", "x")),
            jacksonObjectMapper().findAndRegisterModules(),
        )
        val brokenSync = PostgresSyncService(jsonRepo, brokenRepo, defaultClubProvider(OUR_CLUB))
        val failResult = brokenSync.sync()
        assertThat(failResult.failures).isNotEmpty

        val recoverResult = syncService.sync()

        assertThat(recoverResult.synced).isEqualTo(1)
        assertThat(recoverResult.failures).isEmpty()
        assertThat(pgRepo.findById(OUR_CLUB, MatchId("fixture-1"))).isNotNull
        assertThat(syncService.lastSyncCompleted).isNotNull()
    }

    @Test
    fun `recovery preserves existing records without duplication`() {
        jsonRepo.save(testMatch("existing-1", 1_700_000_000L))
        syncService.sync()
        assertThat(pgRepo.findAll(OUR_CLUB)).hasSize(1)

        jsonRepo.save(testMatch("fixture-new", 1_800_000_000L))

        val brokenRepo = PostgresCanonicalMatchRepository(
            JdbcTemplate(DriverManagerDataSource("jdbc:postgresql://localhost:1/gone", "x", "x")),
            jacksonObjectMapper().findAndRegisterModules(),
        )
        PostgresSyncService(jsonRepo, brokenRepo, defaultClubProvider(OUR_CLUB)).sync()

        val recoverResult = syncService.sync()

        assertThat(recoverResult.synced).isEqualTo(2)
        assertThat(recoverResult.failures).isEmpty()
        assertThat(pgRepo.findAll(OUR_CLUB)).hasSize(2)
    }

    @Test
    fun `partial failure syncs valid records and reports the failed one`() {
        jsonRepo.save(testMatch("good-1", 1_700_000_000L))
        jsonRepo.save(testMatch("good-2", 1_800_000_000L))

        val failingRepo = PartialFailureRepository(pgRepo, setOf(MatchId("good-1")))
        val partialSync = PostgresSyncService(jsonRepo, failingRepo, defaultClubProvider(OUR_CLUB))

        val result = partialSync.sync()

        assertThat(result.synced).isEqualTo(1)
        assertThat(result.failures).hasSize(1)
        assertThat(result.failures[0].matchId).isEqualTo("good-1")
        assertThat(pgRepo.findById(OUR_CLUB, MatchId("good-2"))).isNotNull
    }

    @Test
    fun `failed record from partial failure is recoverable on next cycle`() {
        jsonRepo.save(testMatch("recover-me", 1_700_000_000L))

        val failingRepo = PartialFailureRepository(pgRepo, setOf(MatchId("recover-me")))
        PostgresSyncService(jsonRepo, failingRepo, defaultClubProvider(OUR_CLUB)).sync()
        assertThat(pgRepo.findById(OUR_CLUB, MatchId("recover-me"))).isNull()

        val result = syncService.sync()
        assertThat(result.synced).isEqualTo(1)
        assertThat(result.failures).isEmpty()
        assertThat(pgRepo.findById(OUR_CLUB, MatchId("recover-me"))).isNotNull
    }

    @Test
    fun `scheduler overlap prevention blocks concurrent execution`() {
        jsonRepo.save(testMatch("overlap-test", 1_700_000_000L))

        val slowStarted = CountDownLatch(1)
        val slowRelease = CountDownLatch(1)
        val concurrentAttempts = AtomicBoolean(false)

        val slowSync = object : PostgresSyncService(jsonRepo, pgRepo, defaultClubProvider(OUR_CLUB)) {
            override fun sync(): PostgresSyncResult {
                slowStarted.countDown()
                slowRelease.await(5, TimeUnit.SECONDS)
                return super.sync()
            }
        }

        val scheduler = PostgresSyncScheduler(slowSync)
        val executor = Executors.newFixedThreadPool(2)

        executor.submit { scheduler.sync() }
        slowStarted.await(5, TimeUnit.SECONDS)

        executor.submit {
            scheduler.sync()
            concurrentAttempts.set(true)
        }
        Thread.sleep(100)

        slowRelease.countDown()
        executor.shutdown()
        executor.awaitTermination(10, TimeUnit.SECONDS)

        assertThat(concurrentAttempts.get()).isTrue()
        assertThat(pgRepo.findAll(OUR_CLUB)).hasSize(1)
    }

    @Test
    fun `last failure and last success are distinguishable after recovery`() {
        jsonRepo.save(testMatch("status-test", 1_700_000_000L))

        val brokenRepo = PostgresCanonicalMatchRepository(
            JdbcTemplate(DriverManagerDataSource("jdbc:postgresql://localhost:1/gone", "x", "x")),
            jacksonObjectMapper().findAndRegisterModules(),
        )
        val brokenSync = PostgresSyncService(jsonRepo, brokenRepo, defaultClubProvider(OUR_CLUB))
        brokenSync.sync()

        val failTime = brokenSync.lastSyncStarted
        assertThat(brokenSync.lastSyncResult!!.failures).isNotEmpty

        Thread.sleep(10)
        val recoverResult = syncService.sync()

        assertThat(syncService.lastSyncCompleted).isAfter(failTime)
        assertThat(recoverResult.failures).isEmpty()
        assertThat(recoverResult.synced).isEqualTo(1)
    }

    @Test
    fun `scheduler auto-recovery syncs pending fixture after restored connection`() {
        jsonRepo.save(testMatch("auto-recover", 1_700_000_000L))

        val connectionAvailable = AtomicBoolean(false)
        val switchableSync = object : PostgresSyncService(jsonRepo, pgRepo, defaultClubProvider(OUR_CLUB)) {
            override fun sync(): PostgresSyncResult {
                return if (!connectionAvailable.get()) {
                    val brokenRepo = PostgresCanonicalMatchRepository(
                        JdbcTemplate(DriverManagerDataSource("jdbc:postgresql://localhost:1/gone", "x", "x")),
                        jacksonObjectMapper().findAndRegisterModules(),
                    )
                    PostgresSyncService(jsonRepo, brokenRepo, defaultClubProvider(OUR_CLUB)).sync()
                } else {
                    super.sync()
                }
            }
        }

        val scheduler = PostgresSyncScheduler(switchableSync)

        scheduler.sync()
        assertThat(pgRepo.findById(OUR_CLUB, MatchId("auto-recover"))).isNull()

        connectionAvailable.set(true)
        scheduler.sync()
        assertThat(pgRepo.findById(OUR_CLUB, MatchId("auto-recover"))).isNotNull
    }

    private fun testMatch(id: String, timestamp: Long): CanonicalMatch {
        val source = MatchResponse(
            matchId = id, timestamp = timestamp, matchType = "leagueMatch",
            clubs = linkedMapOf(
                OUR_CLUB.value to ClubMatchEntry(
                    details = ClubDetails("Test FC", OUR_CLUB.value), score = "1", result = "1",
                ),
                "opponent" to ClubMatchEntry(
                    details = ClubDetails("Opp FC", "opponent"), score = "0", result = "0",
                ),
            ),
            players = mapOf(
                OUR_CLUB.value to linkedMapOf(
                    "p1" to PlayerEntry(
                        playerName = "Player", position = "14", rating = "7.0", goals = "0",
                        assists = "0", shots = "1", manOfTheMatch = "0", passesMade = "10",
                        passAttempts = "12", tacklesMade = "2", tackleAttempts = "3",
                        redCards = "0", secondsPlayed = "5400",
                    ),
                ),
            ),
        )
        val fm = (EaMatchMapper().map(source) as MatchNormalizationResult.Success).match
        val interp = MatchInterpreter().interpret(fm, OUR_CLUB)
        val stories = MatchStoryExtractor().extract(interp)
        return CanonicalMatch.current(fm, interp, stories, Instant.parse("2026-08-02T20:00:00Z"))
    }

    private class PartialFailureRepository(
        private val delegate: CanonicalMatchRepository,
        private val failIds: Set<MatchId>,
    ) : CanonicalMatchRepository {
        override fun save(match: CanonicalMatch) {
            if (match.matchId in failIds) throw RuntimeException("simulated failure for ${match.matchId.value}")
            delegate.save(match)
        }
        override fun findById(clubId: ClubId, matchId: MatchId) = delegate.findById(clubId, matchId)
        override fun findMatchIds(clubId: ClubId) = delegate.findMatchIds(clubId)
        override fun findAll(clubId: ClubId) = delegate.findAll(clubId)
        override fun metadata(clubId: ClubId) = delegate.metadata(clubId)
    }
}
