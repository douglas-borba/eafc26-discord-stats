package com.eafc26.discordstats.config.postgres

import com.eafc26.discordstats.application.repository.CanonicalMatchRepository
import com.eafc26.discordstats.application.repository.PlayerProfileReadRepository
import com.eafc26.discordstats.application.club.LegacyDefaultClubImporter
import com.eafc26.discordstats.application.club.DefaultClubProvider
import com.eafc26.discordstats.application.club.MonitoredClubRepository
import com.eafc26.discordstats.application.club.MonitoredClubService
import com.eafc26.discordstats.config.AppProperties
import com.eafc26.discordstats.config.WebhookConfigService
import com.eafc26.discordstats.discord.DiscordWebhookSecretStore
import com.eafc26.discordstats.discord.PostgresDiscordWebhookSecretStore
import com.eafc26.discordstats.discord.WebhookSecretCryptography
import com.eafc26.discordstats.store.JsonCanonicalMatchRepository
import com.eafc26.discordstats.service.PostgresSyncService
import com.eafc26.discordstats.store.MirroringCanonicalMatchRepository
import com.eafc26.discordstats.store.PostgresCanonicalMatchRepository
import com.eafc26.discordstats.store.PostgresPlayerProfileReadRepository
import com.eafc26.discordstats.store.PostgresMonitoredClubRepository
import com.eafc26.discordstats.store.PostgresPublishedMatchStore
import com.eafc26.discordstats.store.PublicationStateStore
import com.eafc26.discordstats.store.OperationalEventRepository
import com.eafc26.discordstats.store.AdminAuditLogRepository
import com.eafc26.discordstats.service.OperationalEventRecorder
import com.eafc26.discordstats.service.CanonicalPublicationPersistence
import com.eafc26.discordstats.service.PostgresCanonicalPublicationPersistence
import com.eafc26.discordstats.store.DeliveryUncertaintyReason
import com.eafc26.discordstats.store.DiscordPublicationOrigin
import com.eafc26.discordstats.service.SynchronizationGapStore
import com.eafc26.discordstats.store.PostgresSynchronizationGapStore
import com.eafc26.discordstats.store.PostgresTrialRequestRepository
import com.eafc26.discordstats.application.club.TrialRequestRepository
import com.eafc26.discordstats.application.club.TrialService
import com.eafc26.discordstats.diagnostics.CanonicalReadDiagnostics
import com.eafc26.discordstats.diagnostics.CanonicalReadOriginContext
import com.eafc26.discordstats.explorer.AdvancedStatsExplorerService
import com.eafc26.discordstats.explorer.ExplorerObservationRepository
import com.eafc26.discordstats.store.PostgresExplorerObservationRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.flywaydb.core.Flyway
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.boot.ApplicationRunner
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import javax.sql.DataSource

@Configuration
@ConditionalOnProperty(name = ["app.postgres.mirror-enabled"], havingValue = "true")
@Import(DataSourceAutoConfiguration::class)
class PostgresMirrorConfig {

    @Bean
    fun discordWebhookSecretStore(
        jdbcTemplate: JdbcTemplate,
        webhookConfigService: WebhookConfigService,
        properties: AppProperties,
    ): DiscordWebhookSecretStore = PostgresDiscordWebhookSecretStore(
        jdbcTemplate = jdbcTemplate,
        webhookConfigService = webhookConfigService,
        cryptography = WebhookSecretCryptography.fromBase64(properties.discord.secretEncryptionKey),
    )

    @Bean
    fun flyway(dataSource: DataSource): Flyway {
        val flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
        flyway.migrate()
        return flyway
    }

    @Bean
    fun postgresCanonicalMatchRepository(
        jdbcTemplate: JdbcTemplate,
        objectMapper: ObjectMapper,
        canonicalReadDiagnostics: CanonicalReadDiagnostics,
        canonicalReadOriginContext: CanonicalReadOriginContext,
    ): PostgresCanonicalMatchRepository {
        return PostgresCanonicalMatchRepository(jdbcTemplate, objectMapper, canonicalReadDiagnostics, canonicalReadOriginContext)
    }

    /** PostgreSQL is the primary mirror source, so player reads never query JSON secondary storage. */
    @Bean
    fun playerProfileReadRepository(
        jdbcTemplate: JdbcTemplate,
        canonicalReadDiagnostics: CanonicalReadDiagnostics,
        canonicalReadOriginContext: CanonicalReadOriginContext,
    ): PlayerProfileReadRepository = PostgresPlayerProfileReadRepository(
        jdbcTemplate,
        canonicalReadDiagnostics,
        canonicalReadOriginContext,
    )

    @Bean
    fun monitoredClubRepository(jdbcTemplate: JdbcTemplate): MonitoredClubRepository =
        PostgresMonitoredClubRepository(jdbcTemplate)

    @Bean
    fun legacyDefaultClubImporter(
        repository: MonitoredClubRepository,
        service: MonitoredClubService,
        defaultClubProvider: DefaultClubProvider,
    ): LegacyDefaultClubImporter = LegacyDefaultClubImporter(repository, service, defaultClubProvider)

    @Bean
    fun legacyDefaultClubImportRunner(
        flyway: Flyway,
        importer: LegacyDefaultClubImporter,
    ): ApplicationRunner = ApplicationRunner {
        flyway.info()
        importer.importIfAbsent()
    }

    @Bean
    @Primary
    fun mirroringCanonicalMatchRepository(
        jsonRepository: JsonCanonicalMatchRepository,
        postgresRepository: PostgresCanonicalMatchRepository,
    ): CanonicalMatchRepository {
        return MirroringCanonicalMatchRepository(
            primary = postgresRepository,
            secondary = jsonRepository,
        )
    }

    /**
     * Authoritative PostgreSQL transaction that couples a new canonical fact to its
     * initial durable Discord publication state. The JSON mirror remains secondary.
     */
    @Bean
    fun canonicalPublicationPersistence(
        postgresRepository: PostgresCanonicalMatchRepository,
        publicationStore: PostgresPublishedMatchStore,
        jsonRepository: JsonCanonicalMatchRepository,
        transactionManager: PlatformTransactionManager,
    ): CanonicalPublicationPersistence = PostgresCanonicalPublicationPersistence(
        canonicalRepository = postgresRepository,
        publicationStore = publicationStore,
        jsonMirror = jsonRepository,
        transactions = TransactionTemplate(transactionManager),
    )

    @Bean
    fun advancedStatsExplorerService(
        canonicalMatchRepository: CanonicalMatchRepository,
        explorerObservationRepository: ExplorerObservationRepository,
    ): AdvancedStatsExplorerService = AdvancedStatsExplorerService(canonicalMatchRepository, observationRepository = explorerObservationRepository)

    @Bean
    fun explorerObservationRepository(jdbcTemplate: JdbcTemplate): ExplorerObservationRepository =
        PostgresExplorerObservationRepository(jdbcTemplate)

    @Bean
    fun postgresSyncService(
        jsonRepository: JsonCanonicalMatchRepository,
        postgresRepository: PostgresCanonicalMatchRepository,
        defaultClubProvider: DefaultClubProvider,
    ): PostgresSyncService {
        return PostgresSyncService(jsonRepository, postgresRepository, defaultClubProvider)
    }

    @Bean
    fun operationalEventRepository(jdbcTemplate: JdbcTemplate): OperationalEventRepository =
        OperationalEventRepository(jdbcTemplate)

    @Bean
    fun adminAuditLogRepository(jdbcTemplate: JdbcTemplate): AdminAuditLogRepository =
        AdminAuditLogRepository(jdbcTemplate)

    @Bean
    fun operationalEventRecorder(operationalEventRepository: OperationalEventRepository): OperationalEventRecorder =
        OperationalEventRecorder(operationalEventRepository)

    @Bean
    fun synchronizationGapStore(jdbcTemplate: JdbcTemplate): SynchronizationGapStore =
        PostgresSynchronizationGapStore(jdbcTemplate)

    @Bean
    fun trialRequestRepository(jdbcTemplate: JdbcTemplate): TrialRequestRepository = PostgresTrialRequestRepository(jdbcTemplate)

    @Bean
    fun trialService(
        clubs: MonitoredClubRepository,
        requests: TrialRequestRepository,
        events: org.springframework.beans.factory.ObjectProvider<OperationalEventRecorder>,
    ): TrialService = TrialService(clubs, requests, events = events.ifAvailable)

    @Bean
    fun postgresPublishedMatchStoreImpl(jdbcTemplate: JdbcTemplate): PostgresPublishedMatchStore =
        PostgresPublishedMatchStore(jdbcTemplate)

    @Bean
    @Primary
    fun publicationStateStore(store: PostgresPublishedMatchStore): PublicationStateStore = store

    @Bean
    fun publicationStateUpgradeRunner(
        flyway: Flyway,
        store: PostgresPublishedMatchStore,
        events: OperationalEventRecorder,
    ): ApplicationRunner = ApplicationRunner {
        flyway.info()
        store.upgradeDeliveringRecords().forEach { recovered ->
            events.discordUncertain(
                clubId = recovered.clubId,
                matchId = recovered.record.matchId,
                reason = DeliveryUncertaintyReason.STARTUP_RECOVERY,
                message = recovered.record.lastError ?: "Registro DELIVERING encontrado na inicialização; a causa original não está disponível.",
                origin = DiscordPublicationOrigin.STARTUP_RECOVERY,
            )
        }
    }
}
