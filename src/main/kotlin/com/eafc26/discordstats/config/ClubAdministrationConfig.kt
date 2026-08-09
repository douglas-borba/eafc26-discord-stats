package com.eafc26.discordstats.config

import com.eafc26.discordstats.application.club.ClubCatalogService
import com.eafc26.discordstats.application.club.DefaultClubConfiguration
import com.eafc26.discordstats.application.club.DefaultClubProvider
import com.eafc26.discordstats.application.club.EaPlatform
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.ClubName
import com.eafc26.discordstats.ea.EaClubsGateway
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
