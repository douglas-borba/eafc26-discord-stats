package com.eafc26.discordstats.application.club

import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.ClubName
import com.eafc26.discordstats.domain.match.GameVersion
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class MonitoredClubServiceTest {
    private val repository = InMemoryMonitoredClubRepository()
    private val instant = Instant.parse("2026-08-09T12:00:00Z")
    private val service = MonitoredClubService(repository, Clock.fixed(instant, ZoneOffset.UTC))

    @Test
    fun `register uses the EA club id as the only identity`() {
        val club = service.register(ClubId("1104972"), ClubName("Associação BF"), EaPlatform("common-gen5"))

        assertThat(club.clubId.value).isEqualTo("1104972")
        assertThat(club.monitoringEnabled).isTrue()
        assertThat(club.discordWebhookSecretReference).isNull()
        assertThat(club.createdAt).isEqualTo(instant)
        assertThat(repository.findAll()).containsExactly(club)
    }

    @Test
    fun `registration honors initial monitoring and equal names do not collapse distinct ids`() {
        val first = service.register(ClubId("100"), ClubName("Same Name"), EaPlatform("common-gen5"), false)
        val second = service.register(ClubId("200"), ClubName("Same Name"), EaPlatform("common-gen5"), true)

        assertThat(first.monitoringEnabled).isFalse()
        assertThat(second.monitoringEnabled).isTrue()
        assertThat(repository.findAll()).containsExactly(first, second)
    }

    @Test
    fun `register never overwrites an existing administrative configuration`() {
        val original = service.register(ClubId("1104972"), ClubName("Associação BF"), EaPlatform("common-gen5"))
        val configured = service.configureWebhook(original.clubId, DiscordWebhookSecretReference("vault:club-1104972"))
        val disabled = service.setMonitoring(configured.clubId, false)

        val registeredAgain = service.register(
            original.clubId,
            ClubName("Different name"),
            EaPlatform("different-platform"),
        )

        assertThat(registeredAgain).isEqualTo(disabled)
        assertThat(repository.findAll()).containsExactly(disabled)
    }

    @Test
    fun `no-op updates preserve updatedAt`() {
        val club = service.register(ClubId("1104972"), ClubName("Associação BF"), EaPlatform("common-gen5"))

        assertThat(service.setMonitoring(club.clubId, true).updatedAt).isEqualTo(club.updatedAt)
        assertThat(service.removeWebhook(club.clubId).updatedAt).isEqualTo(club.updatedAt)
    }

    @Test
    fun `raw webhook URLs cannot become persistent references`() {
        assertThatThrownBy { DiscordWebhookSecretReference("https://discord.com/api/webhooks/id/token") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `forward game version transition changes only operational version`() {
        val original = service.register(
            ClubId("1104972"),
            ClubName("Associação BF"),
            EaPlatform("common-gen5"),
            monitoringEnabled = false,
            gameVersion = GameVersion.FC26,
        )
        val configured = service.configureWebhook(original.clubId, DiscordWebhookSecretReference("vault:association"))
            .copy(accessStatus = ClubAccessStatus.TRIAL)
            .also(repository::save)

        val transitioned = service.transitionGameVersion(configured.clubId, GameVersion.FC27)

        assertThat(transitioned.gameVersion).isEqualTo(GameVersion.FC27)
        assertThat(transitioned.clubId).isEqualTo(configured.clubId)
        assertThat(transitioned.displayName).isEqualTo(configured.displayName)
        assertThat(transitioned.platform).isEqualTo(configured.platform)
        assertThat(transitioned.monitoringEnabled).isEqualTo(configured.monitoringEnabled)
        assertThat(transitioned.discordWebhookSecretReference).isEqualTo(configured.discordWebhookSecretReference)
        assertThat(transitioned.accessStatus).isEqualTo(configured.accessStatus)
        assertThat(transitioned.createdAt).isEqualTo(configured.createdAt)
        assertThat(transitioned.updatedAt).isEqualTo(instant)
    }

    @Test
    fun `downgrade is rejected and unknown club is not created`() {
        val club = service.register(ClubId("1104972"), ClubName("Associação BF"), EaPlatform("common-gen5"), gameVersion = GameVersion.FC27)

        assertThatThrownBy { service.transitionGameVersion(club.clubId, GameVersion.FC26) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { service.transitionGameVersion(ClubId("missing"), GameVersion.FC27) }
            .isInstanceOf(NoSuchElementException::class.java)
        assertThat(repository.findAll()).containsExactly(club)
    }
}

internal class InMemoryMonitoredClubRepository : MonitoredClubRepository {
    private val clubs = linkedMapOf<ClubId, MonitoredClub>()

    override fun save(club: MonitoredClub): MonitoredClub = club.also { clubs[it.clubId] = it }
    override fun findById(clubId: ClubId): MonitoredClub? = clubs[clubId]
    override fun findAll(): List<MonitoredClub> = clubs.values.toList()
    override fun existsById(clubId: ClubId): Boolean = clubId in clubs
    override fun deleteById(clubId: ClubId): Boolean = clubs.remove(clubId) != null
}
