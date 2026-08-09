package com.eafc26.discordstats.architecture

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class MultiClubPhaseOneArchitectureTest {
    private val source = Path.of("src/main/kotlin/com/eafc26/discordstats")

    @Test
    fun `EA ClubId remains the only monitored club identity`() {
        val model = Files.readString(source.resolve("application/club/MonitoredClub.kt"))
        assertThat(model).contains("val clubId: ClubId")
        assertThat(model).doesNotContain("UUID", "tenantId", "managedClubId", "internalId")
    }

    @Test
    fun `multi-club coordinator owns monitored club selection while acquisition remains explicit`() {
        val acquisition = Files.readString(source.resolve("service/MatchAcquisitionService.kt"))
        val scheduler = Files.readString(source.resolve("scheduler/MatchPollingScheduler.kt"))
        val coordinator = Files.readString(source.resolve("scheduler/ClubPollingCoordinator.kt"))
        assertThat(acquisition).doesNotContain("MonitoredClub", "MonitoredClubRepository", "MonitoredClubService")
        assertThat(scheduler).doesNotContain("MonitoredClub", "MonitoredClubRepository", "MonitoredClubService")
        assertThat(acquisition).contains("clubId: ClubId")
        assertThat(scheduler).contains("coordinator.pollEnabledClubs")
        assertThat(scheduler).doesNotContain("DefaultClubProvider", "defaultClubProvider")
        assertThat(coordinator).contains("MonitoredClubRepository", "monitoringEnabled", "sortedBy { it.clubId.value }")
        assertThat(coordinator).doesNotContain("DefaultClubProvider")
    }

    @Test
    fun `database stores only an opaque webhook reference`() {
        val migration = Files.readString(Path.of("src/main/resources/db/migration/V8__create_monitored_clubs.sql"))
        assertThat(migration).contains("club_id", "PRIMARY KEY", "discord_webhook_secret_ref")
        assertThat(migration).doesNotContain("webhook_url", "webhook_token")
        assertThat(migration).contains("discord_webhook_secret_ref !~* '^https?://'")
    }

    @Test
    fun `legacy administrative routes remain scoped to default club`() {
        val matchController = Files.readString(source.resolve("web/MatchController.kt"))
        val publicationController = Files.readString(source.resolve("web/PublicationAdminController.kt"))
        assertThat(matchController).contains(
            "DefaultClubProvider",
            "acquisitionService.acquire(defaultClubProvider.get().clubId",
        )
        assertThat(publicationController).contains("DefaultClubProvider", "defaultClubProvider.get().clubId")
        assertThat(matchController).doesNotContain("ClubPollingCoordinator")
        assertThat(publicationController).doesNotContain("ClubPollingCoordinator")
    }
}
