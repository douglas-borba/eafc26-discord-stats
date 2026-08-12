package com.eafc26.discordstats.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app")
data class AppProperties(
    val ea: EaProperties = EaProperties(),
    val polling: PollingProperties = PollingProperties(),
    val discord: DiscordProperties = DiscordProperties(),
    val web: WebProperties = WebProperties(),
    val postgres: PostgresProperties = PostgresProperties(),
    val acquisition: AcquisitionProperties = AcquisitionProperties(),
    val replay: ReplayProperties = ReplayProperties(),
    val security: SecurityProperties = SecurityProperties(),
)

data class SecurityProperties(
    val adminInternalToken: String = "",
)

data class PostgresProperties(
    val mirrorEnabled: Boolean = false,
    val syncEnabled: Boolean = false,
    val syncIntervalMs: Long = 300_000,
)

data class AcquisitionProperties(
    val enabled: Boolean = true,
)

data class WebProperties(
    val networkEnabled: Boolean = false,
)

data class PollingProperties(
    val enabled: Boolean = true,
    val intervalMs: Long = 300_000,
)

data class DiscordProperties(
    val matchWebhookUrl: String = "",
    val secretEncryptionKey: String = "",
)

data class EaProperties(
    val platform: String = "common-gen5",
    val clubId: String = "",
    val clubName: String = "",
    val matchType: String = "leagueMatch",
    val maxResultCount: Int = 20,
    val gatewayBaseUrl: String = "http://127.0.0.1:8081",
    val gatewayInternalToken: String = "",
)

data class ReplayProperties(
    val enabled: Boolean = false,
    val matches: Int? = null,
    val matchId: String? = null,
    val dryRun: Boolean = false,
    val excludeMatchIds: String? = null,
    val clubId: String? = null,
)
