package com.eafc26.discordstats.store.postgres

import com.eafc26.discordstats.application.club.DiscordWebhookSecretReference
import com.eafc26.discordstats.application.club.DefaultClubConfiguration
import com.eafc26.discordstats.application.club.DefaultClubProvider
import com.eafc26.discordstats.application.club.EaPlatform
import com.eafc26.discordstats.application.club.LegacyDefaultClubImporter
import com.eafc26.discordstats.application.club.MonitoredClub
import com.eafc26.discordstats.application.club.MonitoredClubService
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.ClubName
import com.eafc26.discordstats.store.PostgresMonitoredClubRepository
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
class PostgresMonitoredClubRepositoryTest {
    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")

        private lateinit var jdbcTemplate: JdbcTemplate

        @JvmStatic
        fun isDockerAvailable(): Boolean = try {
            org.testcontainers.DockerClientFactory.instance().isDockerAvailable
        } catch (_: Exception) {
            false
        }

        @BeforeAll
        @JvmStatic
        fun migrate() {
            val dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            jdbcTemplate = JdbcTemplate(dataSource)
            jdbcTemplate.execute("DO $$ BEGIN IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'anon') THEN CREATE ROLE anon NOLOGIN; END IF; END $$")
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate()
        }
    }

    private lateinit var repository: PostgresMonitoredClubRepository

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("DELETE FROM monitored_clubs")
        repository = PostgresMonitoredClubRepository(jdbcTemplate)
    }

    @Test
    fun `boot import and second club coexist without overwrite after restart`() {
        val legacyProvider = object : DefaultClubProvider {
            override fun get() = DefaultClubConfiguration(
                ClubId("1104972"),
                ClubName("Associação BF"),
                EaPlatform("common-gen5"),
                DefaultClubProvider.LEGACY_WEBHOOK_REFERENCE,
            )
        }
        val service = MonitoredClubService(repository)
        val importer = LegacyDefaultClubImporter(repository, service, legacyProvider)
        val imported = importer.importIfAbsent()
        val association = service.configureWebhook(
            service.setMonitoring(imported.clubId, false).clubId,
            DiscordWebhookSecretReference("vault:association"),
        )
        val second = club("2209944", "Segundo Clube", enabled = true, reference = null)
        repository.save(second)

        val restartedRepository = PostgresMonitoredClubRepository(jdbcTemplate)
        val restartedImporter = LegacyDefaultClubImporter(
            restartedRepository,
            MonitoredClubService(restartedRepository),
            legacyProvider,
        )
        restartedImporter.importIfAbsent()

        assertThat(restartedRepository.findAll().map { it.clubId.value })
            .containsExactly("1104972", "2209944")
        assertThat(restartedRepository.findById(association.clubId)).isEqualTo(association)
        assertThat(restartedRepository.findById(second.clubId)).isEqualTo(second)
        assertThat(restartedRepository.existsById(ClubId("missing"))).isFalse()
    }

    @Test
    fun `upsert preserves createdAt while applying explicit administrative changes`() {
        val original = club("1104972", "Associação BF", enabled = true, reference = null)
        repository.save(original)
        val updated = original.copy(
            monitoringEnabled = false,
            discordWebhookSecretReference = DiscordWebhookSecretReference("vault:association"),
            updatedAt = original.updatedAt.plusSeconds(60),
        )

        repository.save(updated)

        assertThat(repository.findById(original.clubId)).isEqualTo(updated)
        assertThat(repository.findAll()).hasSize(1)
    }

    private fun club(id: String, name: String, enabled: Boolean, reference: String?) = MonitoredClub(
        clubId = ClubId(id),
        displayName = ClubName(name),
        platform = EaPlatform("common-gen5"),
        monitoringEnabled = enabled,
        discordWebhookSecretReference = reference?.let(::DiscordWebhookSecretReference),
        createdAt = Instant.parse("2026-08-09T12:00:00Z"),
        updatedAt = Instant.parse("2026-08-09T12:00:00Z"),
    )
}
