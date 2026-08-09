package com.eafc26.discordstats.discord

import com.eafc26.discordstats.application.club.DefaultClubProvider
import com.eafc26.discordstats.application.club.DiscordWebhookSecretReference
import com.eafc26.discordstats.config.WebhookConfigService
import com.eafc26.discordstats.domain.match.ClubId
import java.util.prefs.Preferences
import java.util.UUID

/**
 * Stores club webhook secrets outside monitored_clubs and exposes only opaque references.
 * The existing application data volume preserves Java Preferences across restarts.
 */
class PreferencesDiscordWebhookSecretStore(
    private val webhookConfigService: WebhookConfigService,
    private val preferences: Preferences = Preferences.userNodeForPackage(PreferencesDiscordWebhookSecretStore::class.java),
) : DiscordWebhookSecretStore {
    override fun store(clubId: ClubId, webhookUrl: String): DiscordWebhookSecretReference {
        webhookConfigService.validateUrl(webhookUrl)
        val reference = referenceFor(clubId)
        preferences.put(key(reference), webhookUrl.trim())
        preferences.flush()
        return reference
    }

    override fun remove(reference: DiscordWebhookSecretReference) {
        if (reference == DefaultClubProvider.LEGACY_WEBHOOK_REFERENCE) return
        preferences.remove(key(reference))
        preferences.flush()
    }

    override fun resolve(reference: DiscordWebhookSecretReference): DiscordDestination? {
        if (reference == DefaultClubProvider.LEGACY_WEBHOOK_REFERENCE) {
            return webhookConfigService.getWebhookUrl().takeIf(String::isNotBlank)?.let(::DiscordDestination)
        }
        if (!reference.value.startsWith(REFERENCE_PREFIX)) return null
        return preferences.get(key(reference), "").takeIf(String::isNotBlank)?.let(::DiscordDestination)
    }

    private fun referenceFor(clubId: ClubId) =
        DiscordWebhookSecretReference("$REFERENCE_PREFIX${clubId.value}:${UUID.randomUUID()}")
    private fun key(reference: DiscordWebhookSecretReference) = "discord.${reference.value}.webhook"

    companion object {
        private const val REFERENCE_PREFIX = "preferences:club:"
    }
}
