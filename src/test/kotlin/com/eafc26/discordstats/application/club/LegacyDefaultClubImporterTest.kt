package com.eafc26.discordstats.application.club

import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.ClubName
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class LegacyDefaultClubImporterTest {
    private val repository = InMemoryMonitoredClubRepository()
    private val service = MonitoredClubService(
        repository,
        Clock.fixed(Instant.parse("2026-08-09T12:00:00Z"), ZoneOffset.UTC),
    )
    private val provider = mock<DefaultClubProvider>()
    private val importer = LegacyDefaultClubImporter(repository, service, provider)
    private val legacy = DefaultClubConfiguration(
        ClubId("1104972"),
        ClubName("Associação BF"),
        EaPlatform("common-gen5"),
        DefaultClubProvider.LEGACY_WEBHOOK_REFERENCE,
    )

    @Test
    fun `first boot imports the legacy club and restart is idempotent`() {
        whenever(provider.get()).thenReturn(legacy)

        val first = importer.importIfAbsent()
        val restarted = importer.importIfAbsent()

        assertThat(repository.findAll()).containsExactly(first)
        assertThat(restarted).isEqualTo(first)
        assertThat(first.discordWebhookSecretReference).isEqualTo(DefaultClubProvider.LEGACY_WEBHOOK_REFERENCE)
    }

    @Test
    fun `restart never reactivates or overwrites the existing club`() {
        whenever(provider.get()).thenReturn(legacy)
        val imported = importer.importIfAbsent()
        val disabled = service.setMonitoring(imported.clubId, false)
        val custom = service.configureWebhook(disabled.clubId, DiscordWebhookSecretReference("vault:custom"))

        val restarted = importer.importIfAbsent()

        assertThat(restarted).isEqualTo(custom)
        assertThat(restarted.monitoringEnabled).isFalse()
        assertThat(restarted.discordWebhookSecretReference?.value).isEqualTo("vault:custom")
        assertThat(restarted.updatedAt).isEqualTo(custom.updatedAt)
    }
}
