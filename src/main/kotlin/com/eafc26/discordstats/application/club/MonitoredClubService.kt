package com.eafc26.discordstats.application.club

import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.ClubName
import com.eafc26.discordstats.domain.match.GameVersion
import java.time.Clock
import java.time.Instant

class MonitoredClubService(
    private val repository: MonitoredClubRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun register(
        clubId: ClubId,
        displayName: ClubName,
        platform: EaPlatform,
        monitoringEnabled: Boolean = true,
        gameVersion: GameVersion = GameVersion.FC26,
    ): MonitoredClub {
        repository.findById(clubId)?.let { return it }
        val now = Instant.now(clock)
        return repository.save(
            MonitoredClub(
                clubId = clubId,
                displayName = displayName,
                platform = platform,
                gameVersion = gameVersion,
                monitoringEnabled = monitoringEnabled,
                discordWebhookSecretReference = null,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    fun find(clubId: ClubId): MonitoredClub? = repository.findById(clubId)

    fun list(): List<MonitoredClub> = repository.findAll()

    fun setMonitoring(clubId: ClubId, enabled: Boolean): MonitoredClub =
        update(clubId) { current -> current.copy(monitoringEnabled = enabled) }

    fun configureWebhook(clubId: ClubId, reference: DiscordWebhookSecretReference): MonitoredClub =
        update(clubId) { current -> current.copy(discordWebhookSecretReference = reference) }

    fun removeWebhook(clubId: ClubId): MonitoredClub =
        update(clubId) { current -> current.copy(discordWebhookSecretReference = null) }

    /**
     * Changes only the operational EA contract for future acquisitions.
     * Historical records are keyed by their own provenance and are untouched.
     */
    fun transitionGameVersion(clubId: ClubId, targetGameVersion: GameVersion): MonitoredClub {
        val current = repository.findById(clubId) ?: throw NoSuchElementException("Monitored club not found")
        val validatedTarget = current.gameVersion.transitionTo(targetGameVersion)
        if (validatedTarget == current.gameVersion) return current
        return repository.updateGameVersion(clubId, validatedTarget, Instant.now(clock))
            ?: throw NoSuchElementException("Monitored club not found")
    }

    fun remove(clubId: ClubId): MonitoredClub {
        val club = repository.findById(clubId) ?: throw NoSuchElementException("Monitored club not found")
        repository.deleteById(clubId)
        return club
    }

    private fun update(clubId: ClubId, change: (MonitoredClub) -> MonitoredClub): MonitoredClub {
        val current = repository.findById(clubId) ?: throw NoSuchElementException("Monitored club not found")
        val changed = change(current)
        if (changed == current) return current
        return repository.save(changed.copy(createdAt = current.createdAt, updatedAt = Instant.now(clock)))
    }
}
