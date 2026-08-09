package com.eafc26.discordstats.application.club

import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.ClubName

data class DefaultClubConfiguration(
    val clubId: ClubId,
    val displayName: ClubName,
    val platform: EaPlatform,
    val webhookSecretReference: DiscordWebhookSecretReference?,
)

interface DefaultClubProvider {
    fun get(): DefaultClubConfiguration

    companion object {
        val LEGACY_WEBHOOK_REFERENCE = DiscordWebhookSecretReference("legacy:default")
    }
}
