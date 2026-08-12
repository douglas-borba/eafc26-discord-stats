package com.eafc26.discordstats.store.postgres

import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.service.SynchronizationGap
import com.eafc26.discordstats.store.PostgresSynchronizationGapStore
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
class PostgresSynchronizationGapStoreTest {
    companion object {
        @Container @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")

        private lateinit var jdbcTemplate: JdbcTemplate

        @JvmStatic fun isDockerAvailable(): Boolean = try {
            org.testcontainers.DockerClientFactory.instance().isDockerAvailable
        } catch (_: Exception) { false }

        @BeforeAll @JvmStatic
        fun migrate() {
            val ds = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            JdbcTemplate(ds).execute("DO $$ BEGIN IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'anon') THEN CREATE ROLE anon NOLOGIN; END IF; END $$")
            Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate()
            jdbcTemplate = JdbcTemplate(ds)
        }
    }

    private val clubId = ClubId("1104972")
    private lateinit var store: PostgresSynchronizationGapStore

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("DELETE FROM synchronization_gaps")
        store = PostgresSynchronizationGapStore(jdbcTemplate)
    }

    @Test
    fun `open gap survives a new store instance and retains its original interval`() {
        store.openGap(SynchronizationGap(clubId, "A", "D"))
        PostgresSynchronizationGapStore(jdbcTemplate).openGap(SynchronizationGap(clubId, "D", "E"))

        val persisted = PostgresSynchronizationGapStore(jdbcTemplate).findOpen(clubId)

        assertThat(persisted).isNotNull
        assertThat(persisted!!).extracting(
            SynchronizationGap::anchorMatchId,
            SynchronizationGap::firstObservableMatchId,
        ).containsExactly("A", "D")
    }
}
