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
    /** Commercial access is deliberately independent from technical monitoring. */
    val accessStatus: ClubAccessStatus = ClubAccessStatus.ACTIVE,
    val trialLimit: Int? = null,
    val trialStartedAt: Instant? = null,
)

enum class ClubAccessStatus {
    TRIAL,
    ACTIVE,
    TRIAL_EXPIRED;

    fun participatesInAutomaticMonitoring(): Boolean = this != TRIAL_EXPIRED
    fun permitsDashboardDepth(): Boolean = this == ACTIVE
}
