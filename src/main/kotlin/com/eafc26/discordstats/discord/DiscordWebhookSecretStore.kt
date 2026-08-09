package com.eafc26.discordstats.discord

import com.eafc26.discordstats.application.club.DiscordWebhookSecretReference
import com.eafc26.discordstats.domain.match.ClubId

/** Mutable secret boundary used by club administration without exposing stored values. */
interface DiscordWebhookSecretStore : DiscordWebhookSecretResolver {
    fun store(clubId: ClubId, webhookUrl: String): DiscordWebhookSecretReference
    fun remove(reference: DiscordWebhookSecretReference)
}
