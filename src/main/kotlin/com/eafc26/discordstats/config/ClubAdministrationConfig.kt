package com.eafc26.discordstats.config

import com.eafc26.discordstats.application.club.ClubCatalogService
import com.eafc26.discordstats.application.club.DefaultClubConfiguration
import com.eafc26.discordstats.application.club.DefaultClubProvider
import com.eafc26.discordstats.application.club.EaPlatform
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.ClubName
import com.eafc26.discordstats.ea.EaClubsGateway
import com.eafc26.discordstats.application.club.MonitoredClubRepository
import com.eafc26.discordstats.discord.DiscordDestinationResolver
import com.eafc26.discordstats.discord.ContextualDiscordDestinationResolver
import com.eafc26.discordstats.discord.DiscordDestination
import com.eafc26.discordstats.discord.DiscordWebhookSecretResolver
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ClubAdministrationConfig {
    @Bean
    fun clubCatalogService(
        @Qualifier("production") gateway: EaClubsGateway,
    ): ClubCatalogService = ClubCatalogService(gateway)

    @Bean
    fun defaultClubProvider(
        properties: AppProperties,
        webhookConfigService: WebhookConfigService,
    ): DefaultClubProvider = LegacyDefaultClubProvider(properties, webhookConfigService)

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
    fun legacyDiscordWebhookSecretResolver(webhookConfigService: WebhookConfigService) =
        DiscordWebhookSecretResolver { reference ->
            if (reference != DefaultClubProvider.LEGACY_WEBHOOK_REFERENCE) return@DiscordWebhookSecretResolver null
            webhookConfigService.getWebhookUrl()
                .takeIf(String::isNotBlank)
                ?.let(::DiscordDestination)
        }
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
