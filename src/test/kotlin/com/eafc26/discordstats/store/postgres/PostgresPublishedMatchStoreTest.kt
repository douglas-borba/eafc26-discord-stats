package com.eafc26.discordstats.store.postgres

import com.eafc26.discordstats.domain.match.ClubId
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
import java.time.Instant

@Testcontainers
@EnabledIf("isDockerAvailable")
class PostgresPublishedMatchStoreTest {

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

    @BeforeEach
    fun setUp() {
        store = PostgresPublishedMatchStore(jdbcTemplate)
        jdbcTemplate.update("DELETE FROM discord_publication_state")
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
        store.saveRecord(CLUB_A, PublicationRecord("match-1", PublicationState.DELIVERING))
        store.saveRecord(CLUB_A, PublicationRecord("match-2", PublicationState.DELIVERED))

        store.upgradeDeliveringRecords()

        assertThat(store.find(CLUB_A, "match-1")!!.state).isEqualTo(PublicationState.DELIVERY_UNCERTAIN)
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
}
