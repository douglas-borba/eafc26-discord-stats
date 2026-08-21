package com.eafc26.discordstats.store.postgres

import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.MatchId
import com.eafc26.discordstats.ea.model.ClubDetails
import com.eafc26.discordstats.ea.model.ClubMatchEntry
import com.eafc26.discordstats.ea.model.MatchResponse
import com.eafc26.discordstats.service.CanonicalMatchFactory
import com.eafc26.discordstats.service.PostgresCanonicalPublicationPersistence
import com.eafc26.discordstats.store.JsonCanonicalMatchRepository
import com.eafc26.discordstats.store.PostgresCanonicalMatchRepository
import com.eafc26.discordstats.store.PostgresPublishedMatchStore
import com.eafc26.discordstats.store.BaselineReason
import com.eafc26.discordstats.store.PublicationRecord
import com.eafc26.discordstats.store.PublicationState
import com.eafc26.discordstats.store.PublicationStateStore
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@Testcontainers
@EnabledIf("isDockerAvailable")
class PostgresPublishedMatchStoreTest {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")

        private lateinit var dataSource: DriverManagerDataSource
        private lateinit var jdbcTemplate: JdbcTemplate

        @JvmStatic
        fun isDockerAvailable(): Boolean = try {
            org.testcontainers.DockerClientFactory.instance().isDockerAvailable
        } catch (_: Exception) { false }

        @BeforeAll
        @JvmStatic
        fun initSchema() {
            dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            JdbcTemplate(dataSource).execute("DO $$ BEGIN IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'anon') THEN CREATE ROLE anon NOLOGIN; END IF; END $$")
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate()
            jdbcTemplate = JdbcTemplate(dataSource)
        }

        private val CLUB_A = ClubId("club-a")
        private val CLUB_B = ClubId("club-b")
    }

    private lateinit var store: PostgresPublishedMatchStore

    @BeforeEach
    fun setUp() {
        store = PostgresPublishedMatchStore(jdbcTemplate)
        jdbcTemplate.update("DELETE FROM discord_publication_state")
        jdbcTemplate.update("DELETE FROM player_match_stats")
        jdbcTemplate.update("DELETE FROM canonical_matches")
    }

    private fun saveCanonicalMetadata(clubId: ClubId, matchId: String, playedAt: Instant) {
        jdbcTemplate.update(
            """
            INSERT INTO canonical_matches
                (club_id, match_id, played_at, canonical_schema_version, payload)
            VALUES (?, ?, ?, 1, '{}'::jsonb)
            """.trimIndent(),
            clubId.value,
            matchId,
            java.sql.Timestamp.from(playedAt),
        )
    }

    @Test
    fun `saveRecord and find round-trip a publication record`() {
        val record = PublicationRecord("match-1", PublicationState.DELIVERED, attemptCount = 1)
        store.saveRecord(CLUB_A, record)

        val found = store.find(CLUB_A, "match-1")
        assertThat(found).isNotNull
        assertThat(found!!.matchId).isEqualTo("match-1")
        assertThat(found.state).isEqualTo(PublicationState.DELIVERED)
        assertThat(found.attemptCount).isEqualTo(1)
    }

    @Test
    fun `DELIVERED survives store recreation`() {
        store.saveRecord(CLUB_A, PublicationRecord("match-1", PublicationState.DELIVERED))

        val newStore = PostgresPublishedMatchStore(jdbcTemplate)
        val found = newStore.find(CLUB_A, "match-1")
        assertThat(found).isNotNull
        assertThat(found!!.state).isEqualTo(PublicationState.DELIVERED)
    }

    @Test
    fun `DELIVERED continues deduplicated after new instance`() {
        store.saveRecord(CLUB_A, PublicationRecord("match-1", PublicationState.DELIVERED))

        val newStore = PostgresPublishedMatchStore(jdbcTemplate)
        val ids = newStore.loadIds(CLUB_A)
        assertThat(ids).contains("match-1")
    }

    @Test
    fun `club isolation - records from one club are invisible to another`() {
        store.saveRecord(CLUB_A, PublicationRecord("match-1", PublicationState.DELIVERED))
        store.saveRecord(CLUB_B, PublicationRecord("match-2", PublicationState.DELIVERED))

        assertThat(store.find(CLUB_A, "match-1")).isNotNull
        assertThat(store.find(CLUB_A, "match-2")).isNull()
        assertThat(store.find(CLUB_B, "match-2")).isNotNull
        assertThat(store.find(CLUB_B, "match-1")).isNull()
    }

    @Test
    fun `DELIVERY_UNCERTAIN persists across instances`() {
        store.saveRecord(CLUB_A, PublicationRecord("match-1", PublicationState.DELIVERY_UNCERTAIN))

        val newStore = PostgresPublishedMatchStore(jdbcTemplate)
        val found = newStore.find(CLUB_A, "match-1")
        assertThat(found!!.state).isEqualTo(PublicationState.DELIVERY_UNCERTAIN)
    }

    @Test
    fun `upgradeDeliveringRecords converts DELIVERING to DELIVERY_UNCERTAIN`() {
        store.saveRecord(CLUB_A, PublicationRecord(
            matchId = "match-1",
            state = PublicationState.DELIVERING,
            attemptCount = 2,
            lastAttemptAt = Instant.now().epochSecond,
        ))
        store.saveRecord(CLUB_A, PublicationRecord("match-2", PublicationState.DELIVERED))

        val recovered = store.upgradeDeliveringRecords()

        assertThat(recovered).singleElement().extracting { it.clubId }.isEqualTo(CLUB_A)
        val record = store.find(CLUB_A, "match-1")!!
        assertThat(record.state).isEqualTo(PublicationState.DELIVERY_UNCERTAIN)
        assertThat(record.attemptCount).isEqualTo(2)
        assertThat(record.lastAttemptAt).isNotNull()
        assertThat(record.lastError).startsWith("STARTUP_RECOVERY:")
        assertThat(store.find(CLUB_A, "match-2")!!.state).isEqualTo(PublicationState.DELIVERED)
    }

    @Test
    fun `removeRecord deletes from store`() {
        store.saveRecord(CLUB_A, PublicationRecord("match-1", PublicationState.DELIVERING))
        store.removeRecord(CLUB_A, "match-1")
        assertThat(store.find(CLUB_A, "match-1")).isNull()
    }

    @Test
    fun `resolveAsDelivered updates state`() {
        store.saveRecord(CLUB_A, PublicationRecord("match-1", PublicationState.DELIVERY_UNCERTAIN))
        store.resolveAsDelivered(CLUB_A, "match-1")
        assertThat(store.find(CLUB_A, "match-1")!!.state).isEqualTo(PublicationState.DELIVERED)
    }

    @Test
    fun `resolveAsUndelivered removes record`() {
        store.saveRecord(CLUB_A, PublicationRecord("match-1", PublicationState.DELIVERY_UNCERTAIN))
        store.resolveAsUndelivered(CLUB_A, "match-1")
        assertThat(store.find(CLUB_A, "match-1")).isNull()
    }

    @Test
    fun `saveIds establishes baseline`() {
        store.saveIds(CLUB_A, setOf("m1", "m2", "m3"))
        val records = store.loadRecords(CLUB_A)
        assertThat(records).hasSize(3)
        assertThat(records.values).allMatch { it.state == PublicationState.BASELINED }
    }

    @Test
    fun `saveIds uses insert only semantics and preserves existing publication states`() {
        val preservedStates = listOf(
            PublicationState.DELIVERED,
            PublicationState.FAILED_TRANSIENT,
            PublicationState.FAILED_PERMANENT,
            PublicationState.DELIVERY_UNCERTAIN,
        )
        preservedStates.forEachIndexed { index, state ->
            store.saveRecord(CLUB_A, PublicationRecord("existing-$index", state))
        }

        store.saveIds(CLUB_A, preservedStates.indices.mapTo(linkedSetOf()) { "existing-$it" } + "new")

        preservedStates.forEachIndexed { index, state ->
            assertThat(store.find(CLUB_A, "existing-$index")!!.state).isEqualTo(state)
        }
        assertThat(store.find(CLUB_A, "new")!!.state).isEqualTo(PublicationState.BASELINED)
    }

    @Test
    fun `metadata counts states correctly`() {
        store.saveRecord(CLUB_A, PublicationRecord("m1", PublicationState.DELIVERED))
        store.saveRecord(CLUB_A, PublicationRecord("m2", PublicationState.DELIVERED))
        store.saveRecord(CLUB_A, PublicationRecord("m3", PublicationState.BASELINED))

        val meta = store.metadata(CLUB_A)
        assertThat(meta.recordCount).isEqualTo(3)
        assertThat(meta.states[PublicationState.DELIVERED]).isEqualTo(2)
        assertThat(meta.states[PublicationState.BASELINED]).isEqualTo(1)
    }

    @Test
    fun `saveRecord with error metadata persists all fields`() {
        val record = PublicationRecord(
            matchId = "match-1",
            state = PublicationState.FAILED_PERMANENT,
            attemptCount = 3,
            lastAttemptAt = Instant.now().epochSecond,
            lastError = "HTTP 404: Not Found",
            lastHttpStatus = 404,
        )
        store.saveRecord(CLUB_A, record)

        val found = store.find(CLUB_A, "match-1")!!
        assertThat(found.state).isEqualTo(PublicationState.FAILED_PERMANENT)
        assertThat(found.attemptCount).isEqualTo(3)
        assertThat(found.lastError).isEqualTo("HTTP 404: Not Found")
        assertThat(found.lastHttpStatus).isEqualTo(404)
    }

    @Test
    fun `createRecordIfAbsent preserves existing delivery history`() {
        store.saveRecord(CLUB_A, PublicationRecord("match-1", PublicationState.DELIVERED, attemptCount = 1))

        val created = store.createRecordIfAbsent(CLUB_A, PublicationRecord("match-1", PublicationState.PENDING))

        assertThat(created).isFalse()
        assertThat(store.find(CLUB_A, "match-1")).isEqualTo(
            PublicationRecord("match-1", PublicationState.DELIVERED, attemptCount = 1),
        )
    }

    @Test
    fun `automatic claim atomically gives exactly one PostgreSQL caller the right to deliver`() {
        store.saveRecord(CLUB_A, PublicationRecord("race", PublicationState.PENDING))
        val expected = requireNotNull(store.find(CLUB_A, "race"))
        val secondStore = PostgresPublishedMatchStore(jdbcTemplate)
        val start = CountDownLatch(1)
        val complete = CountDownLatch(2)
        val executor = Executors.newFixedThreadPool(2)
        val results = java.util.concurrent.CopyOnWriteArrayList<PublicationRecord?>()

        listOf(store, secondStore).forEach { contender ->
            executor.submit {
                start.await(5, TimeUnit.SECONDS)
                results += contender.claimForAutomaticDelivery(CLUB_A, expected, Instant.now())
                complete.countDown()
            }
        }
        start.countDown()
        assertThat(complete.await(10, TimeUnit.SECONDS)).isTrue()
        executor.shutdownNow()

        val winner = results.filterNotNull().single()
        assertThat(winner.state).isEqualTo(PublicationState.DELIVERING)
        assertThat(winner.attemptCount).isEqualTo(1)
        assertThat(store.find(CLUB_A, "race")?.state).isEqualTo(PublicationState.DELIVERING)
    }

    @Test
    fun `automatic candidates are chronological bounded and exclude unsafe states without reading canonical payload`() {
        val now = Instant.parse("2026-08-21T12:00:00Z")
        listOf(
            "pending" to now.minusSeconds(30),
            "retry-one" to now.minusSeconds(20),
            "no-destination" to now.minusSeconds(10),
            "first-run" to now.minusSeconds(5),
            "uncertain" to now.minusSeconds(4),
            "exhausted" to now.minusSeconds(3),
            "delivering" to now.minusSeconds(2),
            "permanent" to now.minusSeconds(1),
        ).forEach { (matchId, playedAt) -> saveCanonicalMetadata(CLUB_A, matchId, playedAt) }
        store.saveRecord(CLUB_A, PublicationRecord("pending", PublicationState.PENDING))
        store.saveRecord(CLUB_A, PublicationRecord(
            "retry-one", PublicationState.FAILED_TRANSIENT,
            attemptCount = 1, lastAttemptAt = now.minusSeconds(61).epochSecond,
        ))
        store.saveRecord(CLUB_A, PublicationRecord(
            "no-destination", PublicationState.BASELINED,
            baselineReason = BaselineReason.NO_DESTINATION,
        ))
        store.saveRecord(CLUB_A, PublicationRecord(
            "first-run", PublicationState.BASELINED,
            baselineReason = BaselineReason.FIRST_RUN,
        ))
        store.saveRecord(CLUB_A, PublicationRecord("uncertain", PublicationState.DELIVERY_UNCERTAIN))
        store.saveRecord(CLUB_A, PublicationRecord("exhausted", PublicationState.RETRY_EXHAUSTED, attemptCount = 5))
        store.saveRecord(CLUB_A, PublicationRecord("delivering", PublicationState.DELIVERING, attemptCount = 1))
        store.saveRecord(CLUB_A, PublicationRecord("permanent", PublicationState.FAILED_PERMANENT, attemptCount = 1))

        val candidates = store.findAutomaticPublicationCandidates(now, limit = 10)

        assertThat(candidates.map { it.record.matchId }).containsExactly("pending", "retry-one", "no-destination")
        assertThat(candidates.map { it.record.state }).containsExactly(
            PublicationState.PENDING,
            PublicationState.FAILED_TRANSIENT,
            PublicationState.BASELINED,
        )
        assertThat(store.findAutomaticPublicationCandidates(now, limit = 2).map { it.record.matchId })
            .containsExactly("pending", "retry-one")
    }

    @Test
    fun `automatic candidate retry backoff uses persisted attempt count`() {
        val now = Instant.parse("2026-08-21T12:00:00Z")
        (0..5).forEach { attempt -> saveCanonicalMetadata(CLUB_A, "retry-$attempt", now.plusSeconds(attempt.toLong())) }
        store.saveRecord(CLUB_A, PublicationRecord("retry-0", PublicationState.FAILED_TRANSIENT, attemptCount = 0))
        store.saveRecord(CLUB_A, PublicationRecord("retry-1", PublicationState.FAILED_TRANSIENT, attemptCount = 1, lastAttemptAt = now.minusSeconds(60).epochSecond))
        store.saveRecord(CLUB_A, PublicationRecord("retry-2", PublicationState.FAILED_TRANSIENT, attemptCount = 2, lastAttemptAt = now.minusSeconds(120).epochSecond))
        store.saveRecord(CLUB_A, PublicationRecord("retry-3", PublicationState.FAILED_TRANSIENT, attemptCount = 3, lastAttemptAt = now.minusSeconds(300).epochSecond))
        store.saveRecord(CLUB_A, PublicationRecord("retry-4", PublicationState.FAILED_TRANSIENT, attemptCount = 4, lastAttemptAt = now.minusSeconds(900).epochSecond))
        store.saveRecord(CLUB_A, PublicationRecord("retry-5", PublicationState.FAILED_TRANSIENT, attemptCount = 5, lastAttemptAt = now.minusSeconds(3600).epochSecond))

        val candidates = store.findAutomaticPublicationCandidates(now, limit = 10)

        assertThat(candidates.map { it.record.matchId }).containsExactly("retry-0", "retry-1", "retry-2", "retry-3", "retry-4")
    }

    @Test
    fun `automatic candidates use match id as deterministic chronological tie breaker`() {
        val playedAt = Instant.parse("2026-08-21T12:00:00Z")
        saveCanonicalMetadata(CLUB_A, "match-z", playedAt)
        saveCanonicalMetadata(CLUB_A, "match-a", playedAt)
        store.saveRecord(CLUB_A, PublicationRecord("match-z", PublicationState.PENDING))
        store.saveRecord(CLUB_A, PublicationRecord("match-a", PublicationState.PENDING))

        val candidates = store.findAutomaticPublicationCandidates(playedAt, limit = 2)

        assertThat(candidates.map { it.record.matchId }).containsExactly("match-a", "match-z")
    }

    @Test
    fun `canonical fact and initial PENDING intent commit together in PostgreSQL`() {
        val canonicalRepository = PostgresCanonicalMatchRepository(
            jdbcTemplate,
            jacksonObjectMapper().findAndRegisterModules(),
        )
        val jsonMirror: JsonCanonicalMatchRepository = mock()
        val persistence = PostgresCanonicalPublicationPersistence(
            canonicalRepository,
            store,
            jsonMirror,
            TransactionTemplate(DataSourceTransactionManager(dataSource)),
        )
        val match = canonical("atomic-pending")

        val created = persistence.persist(match, PublicationRecord("atomic-pending", PublicationState.PENDING))

        assertThat(created).isTrue()
        assertThat(canonicalRepository.findById(CLUB_A, MatchId("atomic-pending"))).isNotNull
        assertThat(store.find(CLUB_A, "atomic-pending")?.state).isEqualTo(PublicationState.PENDING)
        verify(jsonMirror).save(match)
    }

    @Test
    fun `failure while creating initial publication intent rolls back canonical persistence`() {
        val canonicalRepository = PostgresCanonicalMatchRepository(
            jdbcTemplate,
            jacksonObjectMapper().findAndRegisterModules(),
        )
        val jsonMirror: JsonCanonicalMatchRepository = mock()
        val failingStore: PublicationStateStore = mock()
        val pending = PublicationRecord("atomic-rollback", PublicationState.PENDING)
        whenever(failingStore.createRecordIfAbsent(CLUB_A, pending))
            .thenThrow(IllegalStateException("publication state unavailable"))
        val persistence = PostgresCanonicalPublicationPersistence(
            canonicalRepository,
            failingStore,
            jsonMirror,
            TransactionTemplate(DataSourceTransactionManager(dataSource)),
        )
        val match = canonical("atomic-rollback")

        assertThatThrownBy { persistence.persist(match, pending) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("publication state unavailable")

        assertThat(canonicalRepository.findById(CLUB_A, MatchId("atomic-rollback"))).isNull()
        verify(jsonMirror, never()).save(match)
    }

    private fun canonical(matchId: String) = CanonicalMatchFactory().create(
        source = MatchResponse(
            matchId = matchId,
            timestamp = Instant.parse("2026-08-21T12:00:00Z").epochSecond,
            clubs = mapOf(
                CLUB_A.value to ClubMatchEntry(ClubDetails(name = "Club A"), score = "2", result = "1"),
                "opponent" to ClubMatchEntry(ClubDetails(name = "Opponent"), score = "1", result = "0"),
            ),
            players = emptyMap(),
        ),
        perspectiveClubId = CLUB_A.value,
    )
}
