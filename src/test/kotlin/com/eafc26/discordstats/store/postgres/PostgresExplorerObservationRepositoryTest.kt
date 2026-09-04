package com.eafc26.discordstats.store.postgres

import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.MatchId
import com.eafc26.discordstats.explorer.ExplorerObservation
import com.eafc26.discordstats.explorer.ObservationCompleteness
import com.eafc26.discordstats.explorer.ObservationPhraseReconciliationStatus
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

    @Test fun `loads a bounded same player match vector without leaking other identities`() {
        repository.save(ExplorerObservation(ClubId("club-a"), MatchId("match-a"), "player-a", "Alpha", 1))
        repository.save(ExplorerObservation(ClubId("club-a"), MatchId("match-a"), "player-a", "Beta", 2))
        repository.save(ExplorerObservation(ClubId("club-a"), MatchId("match-a"), "player-a", "Gamma", 3))
        repository.save(ExplorerObservation(ClubId("club-a"), MatchId("match-b"), "player-a", "Other match", 4))
        repository.save(ExplorerObservation(ClubId("club-a"), MatchId("match-a"), "player-b", "Other player", 5))

        val observations = repository.findForPlayerMatchLimited(ClubId("club-a"), MatchId("match-a"), "player-a", 2)

        assertThat(observations.map { it.phrase }).containsExactly("Alpha", "Beta")
        assertThat(observations).allSatisfy {
            assertThat(it.clubId).isEqualTo(ClubId("club-a"))
            assertThat(it.matchId).isEqualTo(MatchId("match-a"))
            assertThat(it.playerId).isEqualTo("player-a")
        }
    }

    @Test fun `atomically reconciles one phrase while preserving its evidence metadata`() {
        val source = repository.save(
            ExplorerObservation(
                ClubId("club-a"), MatchId("match-a"), "player-a", "otimo emepenho ofensivo", 2,
                ObservationCompleteness.AT_LEAST, "live", "CAM",
            ),
        )

        val result = repository.reconcilePhrase(
            ClubId("club-a"), MatchId("match-a"), "player-a", source.phrase, "Ótimo empenho ofensivo",
        )

        assertThat(result.status).isEqualTo(ObservationPhraseReconciliationStatus.SUCCESS)
        assertThat(result.observation).usingRecursiveComparison().ignoringFields("phrase", "updatedAt")
            .isEqualTo(source)
        assertThat(result.observation!!.phrase).isEqualTo("Ótimo empenho ofensivo")
        assertThat(repository.findForPlayerPhrase(ClubId("club-a"), "player-a", source.phrase, 20)).isEmpty()
    }

    @Test fun `target collision leaves both exact evidence rows unchanged`() {
        val variant = repository.save(ExplorerObservation(ClubId("club-a"), MatchId("match-a"), "player-a", "otima finta", 1))
        val target = repository.save(ExplorerObservation(ClubId("club-a"), MatchId("match-a"), "player-a", "Ótima finta", 2))

        val result = repository.reconcilePhrase(ClubId("club-a"), MatchId("match-a"), "player-a", variant.phrase, target.phrase)

        assertThat(result.status).isEqualTo(ObservationPhraseReconciliationStatus.TARGET_ALREADY_EXISTS)
        assertThat(result.existingTarget).isEqualTo(target)
        assertThat(repository.findForPlayerMatch(ClubId("club-a"), MatchId("match-a"), "player-a"))
            .containsExactly(variant, target)
    }
}
