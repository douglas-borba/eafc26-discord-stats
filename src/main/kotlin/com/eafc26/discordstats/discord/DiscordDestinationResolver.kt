package com.eafc26.discordstats.discord

import com.eafc26.discordstats.application.club.DefaultClubProvider
import com.eafc26.discordstats.application.club.DiscordWebhookSecretReference
import com.eafc26.discordstats.application.club.MonitoredClubRepository
import com.eafc26.discordstats.domain.match.ClubId

/** A resolved secret that can only be consumed by the Discord infrastructure client. */
class DiscordDestination internal constructor(internal val webhookUrl: String) {
    init {
        require(webhookUrl.isNotBlank()) { "Discord destination must not be blank" }
    }

    override fun toString(): String = "DiscordDestination(configured=true)"
}

/** Port for resolving a club's optional Discord destination. */
fun interface DiscordDestinationResolver {
    fun resolve(clubId: ClubId): DiscordDestination?
}

/** Resolves an opaque monitored-club reference without exposing its secret to callers. */
fun interface DiscordWebhookSecretResolver {
    fun resolve(reference: DiscordWebhookSecretReference): DiscordDestination?
}

/** Current contextual resolver with a narrow fallback for the legacy default club. */
class ContextualDiscordDestinationResolver(
    private val monitoredClubs: MonitoredClubRepository?,
    private val defaultClubProvider: DefaultClubProvider,
    private val secretResolver: DiscordWebhookSecretResolver,
) : DiscordDestinationResolver {
    override fun resolve(clubId: ClubId): DiscordDestination? {
        val monitored = monitoredClubs?.findById(clubId)
        val reference = if (monitored != null) {
            monitored.discordWebhookSecretReference
        } else {
            defaultClubProvider.get().takeIf { it.clubId == clubId }?.webhookSecretReference
        } ?: return null

        return secretResolver.resolve(reference)
    }
}
