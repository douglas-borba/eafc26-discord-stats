package com.eafc26.discordstats.store.postgres

import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.store.EventStatus
import com.eafc26.discordstats.store.OperationalEvent
import com.eafc26.discordstats.store.OperationalEventRepository
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
class OperationalEventRepositoryTest {

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

    private lateinit var repository: OperationalEventRepository

    @BeforeEach
    fun setUp() {
        repository = OperationalEventRepository(jdbcTemplate)
        jdbcTemplate.update("DELETE FROM operational_events")
    }

    @Test
    fun `events persist and can be queried by club`() {
        repository.save(OperationalEvent(clubId = CLUB_A, eventType = "POLLING", status = EventStatus.SUCCESS, message = "OK"))
        repository.save(OperationalEvent(clubId = CLUB_B, eventType = "POLLING", status = EventStatus.SUCCESS, message = "OK"))

        val events = repository.findByClub(CLUB_A)
        assertThat(events).hasSize(1)
        assertThat(events[0].clubId).isEqualTo(CLUB_A)
    }

    @Test
    fun `events isolated between clubs`() {
        repository.save(OperationalEvent(clubId = CLUB_A, eventType = "DISCORD", status = EventStatus.SUCCESS))
        repository.save(OperationalEvent(clubId = CLUB_B, eventType = "EA_FETCH", status = EventStatus.FAILURE))

        assertThat(repository.findByClub(CLUB_A)).hasSize(1)
        assertThat(repository.findByClub(CLUB_A)[0].eventType).isEqualTo("DISCORD")
        assertThat(repository.findByClub(CLUB_B)).hasSize(1)
        assertThat(repository.findByClub(CLUB_B)[0].eventType).isEqualTo("EA_FETCH")
    }

    @Test
    fun `findLatestByClubAndType returns most recent`() {
        repository.save(OperationalEvent(clubId = CLUB_A, eventType = "POLLING", status = EventStatus.SUCCESS, message = "first"))
        repository.save(OperationalEvent(clubId = CLUB_A, eventType = "POLLING", status = EventStatus.FAILURE, message = "second"))

        val latest = repository.findLatestByClubAndType(CLUB_A, "POLLING")
        assertThat(latest).isNotNull
        assertThat(latest!!.message).isEqualTo("second")
        assertThat(latest.status).isEqualTo(EventStatus.FAILURE)
    }

    @Test
    fun `EA error appears in event history`() {
        repository.save(OperationalEvent(clubId = CLUB_A, eventType = "EA_FETCH", status = EventStatus.FAILURE, errorCode = "503", message = "Service Unavailable"))

        val events = repository.findByClub(CLUB_A)
        assertThat(events).hasSize(1)
        assertThat(events[0].eventType).isEqualTo("EA_FETCH")
        assertThat(events[0].status).isEqualTo(EventStatus.FAILURE)
        assertThat(events[0].errorCode).isEqualTo("503")
    }

    @Test
    fun `Discord error appears in event history`() {
        repository.save(OperationalEvent(clubId = CLUB_A, matchId = "m1", eventType = "DISCORD", phase = "FAILED", status = EventStatus.FAILURE, errorCode = "404", message = "Webhook not found"))

        val events = repository.findByClub(CLUB_A)
        assertThat(events).hasSize(1)
        assertThat(events[0].matchId).isEqualTo("m1")
        assertThat(events[0].phase).isEqualTo("FAILED")
    }

    @Test
    fun `findRecent returns events ordered by creation descending`() {
        repository.save(OperationalEvent(clubId = CLUB_A, eventType = "POLLING", status = EventStatus.SUCCESS, message = "first"))
        repository.save(OperationalEvent(clubId = CLUB_B, eventType = "DISCORD", status = EventStatus.SUCCESS, message = "second"))

        val recent = repository.findRecent(limit = 10)
        assertThat(recent).hasSize(2)
        assertThat(recent[0].message).isEqualTo("second")
        assertThat(recent[1].message).isEqualTo("first")
    }

    @Test
    fun `durationMs persists correctly`() {
        repository.save(OperationalEvent(clubId = CLUB_A, eventType = "POLLING", status = EventStatus.SUCCESS, durationMs = 1234))

        val events = repository.findByClub(CLUB_A)
        assertThat(events[0].durationMs).isEqualTo(1234)
    }
}
