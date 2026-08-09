package com.eafc26.discordstats.application.club

import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.ClubName
import java.time.Instant

@JvmInline
value class EaPlatform(val value: String) {
    init {
        require(value.isNotBlank()) { "EA platform must not be blank" }
    }
}

/**
 * Opaque reference to a webhook stored by an external secret mechanism.
 * Raw Discord webhook URLs are deliberately rejected and never persisted here.
 */
@JvmInline
value class DiscordWebhookSecretReference(val value: String) {
    init {
        require(value.isNotBlank()) { "Webhook secret reference must not be blank" }
        require(!value.startsWith("http://") && !value.startsWith("https://")) {
            "A webhook URL must not be stored as a secret reference"
        }
    }
}

data class MonitoredClub(
    val clubId: ClubId,
    val displayName: ClubName,
    val platform: EaPlatform,
    val monitoringEnabled: Boolean,
    val discordWebhookSecretReference: DiscordWebhookSecretReference?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
