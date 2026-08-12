package com.eafc26.discordstats.config

import com.eafc26.discordstats.application.club.ClubCatalogService
import com.eafc26.discordstats.application.club.DefaultClubConfiguration
import com.eafc26.discordstats.application.club.DefaultClubProvider
import com.eafc26.discordstats.application.club.EaPlatform
import com.eafc26.discordstats.application.club.MonitoredClub
import com.eafc26.discordstats.application.club.MonitoredClubRepository
import com.eafc26.discordstats.application.club.MonitoredClubService
import com.eafc26.discordstats.application.club.ClubAccessPolicy
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.ClubName
import com.eafc26.discordstats.ea.EaClubsGateway
import com.eafc26.discordstats.discord.ContextualDiscordDestinationResolver
import com.eafc26.discordstats.discord.DiscordDestinationResolver
import com.eafc26.discordstats.discord.DiscordWebhookSecretResolver
import com.eafc26.discordstats.discord.DiscordWebhookSecretStore
import com.eafc26.discordstats.discord.PreferencesDiscordWebhookSecretStore
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Instant

@Configuration
class ClubAdministrationConfig {
    @Bean
    fun clubCatalogService(
        @Qualifier("production") gateway: EaClubsGateway,
    ): ClubCatalogService = ClubCatalogService(gateway)

    @Bean
    fun monitoredClubService(repository: MonitoredClubRepository): MonitoredClubService =
        MonitoredClubService(repository)

    @Bean
    fun clubAccessPolicy(service: MonitoredClubService): ClubAccessPolicy = ClubAccessPolicy(service)


    @Bean
    fun defaultClubProvider(
        properties: AppProperties,
        webhookConfigService: WebhookConfigService,
    ): DefaultClubProvider = LegacyDefaultClubProvider(properties, webhookConfigService)

    @Bean
    @ConditionalOnProperty(
        name = ["app.postgres.mirror-enabled"],
        havingValue = "false",
        matchIfMissing = true,
    )
    fun legacyLocalMonitoredClubRepository(defaultClubProvider: DefaultClubProvider): MonitoredClubRepository {
        val legacy = defaultClubProvider.get()
        val now = Instant.now()
        val club = MonitoredClub(
            clubId = legacy.clubId,
            displayName = legacy.displayName,
            platform = legacy.platform,
            monitoringEnabled = true,
            discordWebhookSecretReference = legacy.webhookSecretReference,
            createdAt = now,
            updatedAt = now,
        )
        return LegacyLocalMonitoredClubRepository(club)
    }

    @Bean
    fun discordDestinationResolver(
        repositories: ObjectProvider<MonitoredClubRepository>,
        defaultClubProvider: DefaultClubProvider,
        secretResolver: DiscordWebhookSecretResolver,
    ): DiscordDestinationResolver = ContextualDiscordDestinationResolver(
        repositories.ifAvailable,
        defaultClubProvider,
        secretResolver,
    )

    @Bean
    @ConditionalOnProperty(
        name = ["app.postgres.mirror-enabled"],
        havingValue = "false",
        matchIfMissing = true,
    )
    fun discordWebhookSecretStore(webhookConfigService: WebhookConfigService): DiscordWebhookSecretStore =
        PreferencesDiscordWebhookSecretStore(webhookConfigService)
}

/** Compatibility repository used only when PostgreSQL club administration is disabled. */
private class LegacyLocalMonitoredClubRepository(initialClub: MonitoredClub) : MonitoredClubRepository {
    private val clubs = linkedMapOf(initialClub.clubId to initialClub)

    override fun save(club: MonitoredClub): MonitoredClub = club.also { clubs[it.clubId] = it }
    override fun findById(clubId: ClubId): MonitoredClub? = clubs[clubId]
    override fun findAll(): List<MonitoredClub> = clubs.values.toList()
    override fun existsById(clubId: ClubId): Boolean = clubId in clubs
    override fun deleteById(clubId: ClubId): Boolean = clubs.remove(clubId) != null
}

/** Temporary infrastructure adapter around the legacy single-club configuration. */
private class LegacyDefaultClubProvider(
    properties: AppProperties,
    webhookConfigService: WebhookConfigService,
) : DefaultClubProvider {
    private val configuration = DefaultClubConfiguration(
        clubId = ClubId(properties.ea.clubId),
        displayName = ClubName(properties.ea.clubName),
        platform = EaPlatform(properties.ea.platform),
        webhookSecretReference = if (webhookConfigService.isConfigured()) {
            DefaultClubProvider.LEGACY_WEBHOOK_REFERENCE
        } else {
            null
        },
    )

    override fun get(): DefaultClubConfiguration = configuration
}
