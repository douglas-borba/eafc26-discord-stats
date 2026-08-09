package com.eafc26.discordstats.discord

import com.eafc26.discordstats.application.club.DefaultClubConfiguration
import com.eafc26.discordstats.application.club.DefaultClubProvider
import com.eafc26.discordstats.application.club.DiscordWebhookSecretReference
import com.eafc26.discordstats.application.club.EaPlatform
import com.eafc26.discordstats.application.club.MonitoredClub
import com.eafc26.discordstats.application.club.MonitoredClubRepository
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.ClubName
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class DiscordDestinationResolverTest {
    private val clubA = ClubId("club-a")
    private val clubB = ClubId("club-b")
    private val refA = DiscordWebhookSecretReference("secret:a")
    private val refB = DiscordWebhookSecretReference("secret:b")

    @Test
    fun `each club resolves only its own opaque webhook reference`() {
        val resolver = resolver(
            mapOf(clubA to monitored(clubA, refA), clubB to monitored(clubB, refB)),
            mapOf(refA to "https://discord.test/a", refB to "https://discord.test/b"),
        )

        assertThat(resolver.resolve(clubA)?.webhookUrl).isEqualTo("https://discord.test/a")
        assertThat(resolver.resolve(clubB)?.webhookUrl).isEqualTo("https://discord.test/b")
    }

    @Test
    fun `club without webhook has no destination and never borrows another club destination`() {
        val resolver = resolver(
            mapOf(clubA to monitored(clubA, null), clubB to monitored(clubB, refB)),
            mapOf(refB to "https://discord.test/b"),
        )

        assertThat(resolver.resolve(clubA)).isNull()
        assertThat(resolver.resolve(clubB)?.webhookUrl).isEqualTo("https://discord.test/b")
    }

    @Test
    fun `registered club without webhook does not fall back to legacy destination`() {
        val resolver = ContextualDiscordDestinationResolver(
            monitoredClubs = repository(mapOf(clubA to monitored(clubA, null))),
            defaultClubProvider = defaultProvider(clubA, refA),
            secretResolver = DiscordWebhookSecretResolver { DiscordDestination("https://discord.test/legacy") },
        )

        assertThat(resolver.resolve(clubA)).isNull()
    }

    private fun resolver(
        clubs: Map<ClubId, MonitoredClub>,
        secrets: Map<DiscordWebhookSecretReference, String>,
    ) = ContextualDiscordDestinationResolver(
        monitoredClubs = repository(clubs),
        defaultClubProvider = defaultProvider(ClubId("legacy"), null),
        secretResolver = DiscordWebhookSecretResolver { reference ->
            secrets[reference]?.let(::DiscordDestination)
        },
    )

    private fun repository(clubs: Map<ClubId, MonitoredClub>) = object : MonitoredClubRepository {
        override fun save(club: MonitoredClub) = club
        override fun findById(clubId: ClubId) = clubs[clubId]
        override fun findAll() = clubs.values.toList()
        override fun existsById(clubId: ClubId) = clubId in clubs
    }

    private fun defaultProvider(clubId: ClubId, reference: DiscordWebhookSecretReference?) =
        object : DefaultClubProvider {
            override fun get() = DefaultClubConfiguration(
                clubId = clubId,
                displayName = ClubName("Legacy"),
                platform = EaPlatform("common-gen5"),
                webhookSecretReference = reference,
            )
        }

    private fun monitored(clubId: ClubId, reference: DiscordWebhookSecretReference?) = MonitoredClub(
        clubId = clubId,
        displayName = ClubName(clubId.value),
        platform = EaPlatform("common-gen5"),
        monitoringEnabled = true,
        discordWebhookSecretReference = reference,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
}
