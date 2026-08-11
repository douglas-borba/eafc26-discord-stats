package com.eafc26.discordstats.store.postgres

import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.store.BaselineReason
import com.eafc26.discordstats.store.EventStatus
import com.eafc26.discordstats.store.OperationalEvent
import com.eafc26.discordstats.store.OperationalEventRepository
import com.eafc26.discordstats.store.PostgresPublishedMatchStore
import com.eafc26.discordstats.store.PublicationRecord
import com.eafc26.discordstats.store.PublicationState
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
class GapFixesIntegrationTest {

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

        private val CLUB_A = ClubId("club-a")
        private val CLUB_B = ClubId("club-b")
    }

    private lateinit var store: PostgresPublishedMatchStore
    private lateinit var eventRepo: OperationalEventRepository

    @BeforeEach
    fun setUp() {
        store = PostgresPublishedMatchStore(jdbcTemplate)
        eventRepo = OperationalEventRepository(jdbcTemplate)
        jdbcTemplate.update("DELETE FROM discord_publication_state")
        jdbcTemplate.update("DELETE FROM operational_events")
    }

    // --- Gap 1: Baseline reason ---

    @Test
    fun `saveIds sets baseline_reason to FIRST_RUN`() {
        store.saveIds(CLUB_A, setOf("m1", "m2"))
        val records = store.loadRecords(CLUB_A)
        assertThat(records).hasSize(2)
        assertThat(records.values).allMatch { it.state == PublicationState.BASELINED }
        assertThat(records.values).allMatch { it.baselineReason == BaselineReason.FIRST_RUN }
    }

    @Test
    fun `NO_DESTINATION baseline persists and round-trips`() {
        store.saveRecord(CLUB_A, PublicationRecord(
            "m1", PublicationState.BASELINED, baselineReason = BaselineReason.NO_DESTINATION,
        ))
        val found = store.find(CLUB_A, "m1")!!
        assertThat(found.state).isEqualTo(PublicationState.BASELINED)
        assertThat(found.baselineReason).isEqualTo(BaselineReason.NO_DESTINATION)
    }

    @Test
    fun `FIRST_RUN and NO_DESTINATION baselines are distinguishable`() {
        store.saveIds(CLUB_A, setOf("m1"))
        store.saveRecord(CLUB_A, PublicationRecord(
            "m2", PublicationState.BASELINED, baselineReason = BaselineReason.NO_DESTINATION,
        ))
        val records = store.loadRecords(CLUB_A)
        assertThat(records["m1"]!!.baselineReason).isEqualTo(BaselineReason.FIRST_RUN)
        assertThat(records["m2"]!!.baselineReason).isEqualTo(BaselineReason.NO_DESTINATION)
    }

    // --- Gap 2: FAILED_TRANSIENT state ---

    @Test
    fun `FAILED_TRANSIENT persists with error metadata`() {
        val record = PublicationRecord(
            matchId = "m1",
            state = PublicationState.FAILED_TRANSIENT,
            attemptCount = 2,
            lastAttemptAt = java.time.Instant.now().epochSecond,
            lastError = "HTTP 429: Too Many Requests",
            lastHttpStatus = 429,
        )
        store.saveRecord(CLUB_A, record)
        val found = store.find(CLUB_A, "m1")!!
        assertThat(found.state).isEqualTo(PublicationState.FAILED_TRANSIENT)
        assertThat(found.attemptCount).isEqualTo(2)
        assertThat(found.lastHttpStatus).isEqualTo(429)
        assertThat(found.lastError).isEqualTo("HTTP 429: Too Many Requests")
    }

    @Test
    fun `FAILED_TRANSIENT allows retry - not treated as terminal`() {
        store.saveRecord(CLUB_A, PublicationRecord(
            matchId = "m1",
            state = PublicationState.FAILED_TRANSIENT,
            attemptCount = 1,
            lastError = "HTTP 503: Service Unavailable",
            lastHttpStatus = 503,
        ))

        // Simulate retry: overwrite with DELIVERING
        store.saveRecord(CLUB_A, PublicationRecord(
            matchId = "m1",
            state = PublicationState.DELIVERING,
            attemptCount = 2,
            lastAttemptAt = java.time.Instant.now().epochSecond,
        ))
        assertThat(store.find(CLUB_A, "m1")!!.state).isEqualTo(PublicationState.DELIVERING)

        // Simulate success: overwrite with DELIVERED
        store.saveRecord(CLUB_A, PublicationRecord("m1", PublicationState.DELIVERED, attemptCount = 2))
        assertThat(store.find(CLUB_A, "m1")!!.state).isEqualTo(PublicationState.DELIVERED)
    }

    @Test
    fun `FAILED_PERMANENT blocks automatic retry`() {
        store.saveRecord(CLUB_A, PublicationRecord(
            matchId = "m1",
            state = PublicationState.FAILED_PERMANENT,
            attemptCount = 1,
            lastError = "HTTP 404: Not Found",
            lastHttpStatus = 404,
        ))
        val found = store.find(CLUB_A, "m1")!!
        assertThat(found.state).isEqualTo(PublicationState.FAILED_PERMANENT)
        // loadIds includes it — deduplication prevents auto-retry
        assertThat(store.loadIds(CLUB_A)).contains("m1")
    }

    @Test
    fun `FAILED_TRANSIENT preserves attempt count across retries`() {
        store.saveRecord(CLUB_A, PublicationRecord(
            matchId = "m1", state = PublicationState.FAILED_TRANSIENT,
            attemptCount = 1, lastError = "HTTP 500",
        ))
        // Retry fails again
        store.saveRecord(CLUB_A, PublicationRecord(
            matchId = "m1", state = PublicationState.FAILED_TRANSIENT,
            attemptCount = 2, lastError = "HTTP 502",
        ))
        val found = store.find(CLUB_A, "m1")!!
        assertThat(found.attemptCount).isEqualTo(2)
        assertThat(found.lastError).isEqualTo("HTTP 502")
    }

    // --- Gap 3: Skip events ---

    @Test
    fun `skip events persist in operational_events`() {
        eventRepo.save(OperationalEvent(
            clubId = CLUB_A, matchId = "m1",
            eventType = "DISCORD", phase = "SKIPPED",
            status = EventStatus.INFO, message = "ALREADY_DELIVERED",
        ))
        eventRepo.save(OperationalEvent(
            clubId = CLUB_A, matchId = "m2",
            eventType = "DISCORD", phase = "SKIPPED",
            status = EventStatus.INFO, message = "NO_DESTINATION",
        ))
        val events = eventRepo.findByClub(CLUB_A)
        assertThat(events).hasSize(2)
        assertThat(events.map { it.phase }).allMatch { it == "SKIPPED" }
        assertThat(events.map { it.message }).containsExactlyInAnyOrder("ALREADY_DELIVERED", "NO_DESTINATION")
    }

    // --- Gap 4: Pre-send failure leaves trace ---

    @Test
    fun `pre-send failure persists as FAILED_TRANSIENT with error`() {
        store.saveRecord(CLUB_A, PublicationRecord(
            matchId = "m1",
            state = PublicationState.FAILED_TRANSIENT,
            attemptCount = 1,
            lastAttemptAt = java.time.Instant.now().epochSecond,
            lastError = "Pre-send persistence failed: Connection refused",
        ))
        val found = store.find(CLUB_A, "m1")!!
        assertThat(found.state).isEqualTo(PublicationState.FAILED_TRANSIENT)
        assertThat(found.lastError).contains("Pre-send persistence failed")
    }

    // --- Gap 5: Club isolation ---

    @Test
    fun `FAILED_TRANSIENT is isolated by club`() {
        store.saveRecord(CLUB_A, PublicationRecord(
            matchId = "m1", state = PublicationState.FAILED_TRANSIENT,
            attemptCount = 1, lastError = "HTTP 503",
        ))
        store.saveRecord(CLUB_B, PublicationRecord(
            matchId = "m1", state = PublicationState.DELIVERED,
        ))
        assertThat(store.find(CLUB_A, "m1")!!.state).isEqualTo(PublicationState.FAILED_TRANSIENT)
        assertThat(store.find(CLUB_B, "m1")!!.state).isEqualTo(PublicationState.DELIVERED)
    }

    @Test
    fun `baseline reasons are isolated by club`() {
        store.saveIds(CLUB_A, setOf("m1"))
        store.saveRecord(CLUB_B, PublicationRecord(
            "m1", PublicationState.BASELINED, baselineReason = BaselineReason.NO_DESTINATION,
        ))
        assertThat(store.find(CLUB_A, "m1")!!.baselineReason).isEqualTo(BaselineReason.FIRST_RUN)
        assertThat(store.find(CLUB_B, "m1")!!.baselineReason).isEqualTo(BaselineReason.NO_DESTINATION)
    }
}
