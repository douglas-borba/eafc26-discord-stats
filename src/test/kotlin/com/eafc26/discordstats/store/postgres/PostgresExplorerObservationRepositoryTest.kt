package com.eafc26.discordstats.store.postgres

import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.MatchId
import com.eafc26.discordstats.explorer.ExplorerObservation
import com.eafc26.discordstats.explorer.ObservationCompleteness
import com.eafc26.discordstats.store.PostgresExplorerObservationRepository
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
class PostgresExplorerObservationRepositoryTest {
    companion object {
        @Container @JvmStatic val postgres = PostgreSQLContainer("postgres:16-alpine")
        private lateinit var jdbc: JdbcTemplate
        @JvmStatic fun isDockerAvailable(): Boolean = try { org.testcontainers.DockerClientFactory.instance().isDockerAvailable } catch (_: Exception) { false }
        @BeforeAll @JvmStatic fun migrate() {
            val dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            JdbcTemplate(dataSource).execute("DO $$ BEGIN IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'anon') THEN CREATE ROLE anon NOLOGIN; END IF; END $$")
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate()
            jdbc = JdbcTemplate(dataSource)
        }
    }

    private lateinit var repository: PostgresExplorerObservationRepository

    @BeforeEach fun setUp() {
        repository = PostgresExplorerObservationRepository(jdbc)
        jdbc.update("DELETE FROM explorer_observations")
    }

    @Test fun `persists exact phrase and AT LEAST evidence independently from canonical JSON`() {
        val stored = repository.save(ExplorerObservation(ClubId("club-a"), MatchId("match-a"), "player-a", "Bom passe", 4, note = "missed some", observedPositionContext = "ST"))
        val updated = repository.save(stored.copy(observedCount = 5, completeness = ObservationCompleteness.EXACT))

        assertThat(repository.findForPlayerMatch(ClubId("club-a"), MatchId("match-a"), "player-a")).containsExactly(updated)
        assertThat(updated.phrase).isEqualTo("Bom passe")
        assertThat(updated.observedCount).isEqualTo(5)
        assertThat(updated.completeness).isEqualTo(ObservationCompleteness.EXACT)
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM explorer_observations", Int::class.java)).isEqualTo(1)
    }

    @Test fun `loads a bounded cross phrase evidence set for one player`() {
        repository.save(ExplorerObservation(ClubId("club-a"), MatchId("match-a"), "player-a", "Frase A", 1))
        repository.save(ExplorerObservation(ClubId("club-a"), MatchId("match-b"), "player-a", "Frase B", 2))
        repository.save(ExplorerObservation(ClubId("club-a"), MatchId("match-c"), "player-b", "Frase C", 3))

        val observations = repository.findForPlayer(ClubId("club-a"), "player-a", 1)

        assertThat(observations).hasSize(1)
        assertThat(observations.single().playerId).isEqualTo("player-a")
        assertThat(observations.single().clubId).isEqualTo(ClubId("club-a"))
    }
}
