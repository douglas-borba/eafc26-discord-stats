package com.eafc26.discordstats.store.postgres

import com.eafc26.discordstats.application.club.ClubAccessStatus
import com.eafc26.discordstats.application.club.EaPlatform
import com.eafc26.discordstats.application.club.MonitoredClub
import com.eafc26.discordstats.application.club.TrialConsumption
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.ClubName
import com.eafc26.discordstats.domain.match.MatchId
import com.eafc26.discordstats.store.PostgresMonitoredClubRepository
import com.eafc26.discordstats.store.PostgresTrialMatchConsumptionRepository
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.util.concurrent.Callable
import java.util.concurrent.Executors

@Testcontainers
@EnabledIf("isDockerAvailable")
class PostgresTrialMatchConsumptionRepositoryTest {
    companion object {
        @Container @JvmStatic val postgres = PostgreSQLContainer("postgres:16-alpine")
        private lateinit var jdbc: JdbcTemplate
        private lateinit var dataSource: DriverManagerDataSource

        @JvmStatic fun isDockerAvailable(): Boolean = try { org.testcontainers.DockerClientFactory.instance().isDockerAvailable } catch (_: Exception) { false }

        @BeforeAll @JvmStatic fun migrate() {
            dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            jdbc = JdbcTemplate(dataSource)
            jdbc.execute("DO $$ BEGIN IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'anon') THEN CREATE ROLE anon NOLOGIN; END IF; END $$")
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate()
        }
    }

    private val clubId = ClubId("1104972")

    @BeforeEach fun reset() {
        jdbc.update("DELETE FROM trial_match_consumptions")
        jdbc.update("DELETE FROM monitored_clubs")
        PostgresMonitoredClubRepository(jdbc).save(club(ClubAccessStatus.TRIAL))
    }

    @Test fun `same match identity is idempotent and survives a new repository instance`() {
        val first = repository().tryConsume(clubId, MatchId("match-a"), Instant.now())
        val second = repository().tryConsume(clubId, MatchId("match-a"), Instant.now())

        assertThat(first).isInstanceOf(TrialConsumption.Counted::class.java)
        assertThat(second).isEqualTo(TrialConsumption.AlreadyCounted)
        assertThat(repository().count(clubId)).isEqualTo(1)
    }

    @Test fun `two concurrent fourth candidates cannot exceed the exact limit`() {
        repository().tryConsume(clubId, MatchId("one"), Instant.now())
        repository().tryConsume(clubId, MatchId("two"), Instant.now())
        val pool = Executors.newFixedThreadPool(2)
        try {
            val results = pool.invokeAll(listOf(
                Callable { repository().tryConsume(clubId, MatchId("three-a"), Instant.now()) },
                Callable { repository().tryConsume(clubId, MatchId("three-b"), Instant.now()) },
            )).map { it.get() }
            assertThat(results.count { it is TrialConsumption.Counted }).isEqualTo(1)
            assertThat(repository().count(clubId)).isEqualTo(3)
            assertThat(PostgresMonitoredClubRepository(jdbc).findById(clubId)!!.accessStatus).isEqualTo(ClubAccessStatus.TRIAL_EXPIRED)
        } finally { pool.shutdownNow() }
    }

    @Test fun `clubs consume independently`() {
        val other = ClubId("2209944")
        PostgresMonitoredClubRepository(jdbc).save(club(ClubAccessStatus.TRIAL, other))
        repeat(3) { repository().tryConsume(clubId, MatchId("a-$it"), Instant.now()) }
        repository().tryConsume(other, MatchId("b-1"), Instant.now())

        assertThat(repository().count(clubId)).isEqualTo(3)
        assertThat(repository().count(other)).isEqualTo(1)
        assertThat(PostgresMonitoredClubRepository(jdbc).findById(other)!!.accessStatus).isEqualTo(ClubAccessStatus.TRIAL)
    }

    private fun repository() = PostgresTrialMatchConsumptionRepository(jdbc, TransactionTemplate(DataSourceTransactionManager(dataSource)))
    private fun club(status: ClubAccessStatus, id: ClubId = clubId) = MonitoredClub(id, ClubName("Trial $id"), EaPlatform("common-gen5"), true, null, Instant.now(), Instant.now(), status, 3, Instant.now())
}
