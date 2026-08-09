package com.eafc26.discordstats.application.club

class LegacyDefaultClubImporter(
    private val repository: MonitoredClubRepository,
    private val service: MonitoredClubService,
    private val defaultClubProvider: DefaultClubProvider,
) {
    fun importIfAbsent(): MonitoredClub {
        val legacy = defaultClubProvider.get()
        repository.findById(legacy.clubId)?.let { return it }
        val imported = service.register(legacy.clubId, legacy.displayName, legacy.platform)
        return legacy.webhookSecretReference?.let { service.configureWebhook(imported.clubId, it) } ?: imported
    }
}
