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
    fun `new administrative clubs do not enter acquisition or scheduling`() {
        val acquisition = Files.readString(source.resolve("service/MatchAcquisitionService.kt"))
        val scheduler = Files.readString(source.resolve("scheduler/MatchPollingScheduler.kt"))
        assertThat(acquisition).doesNotContain("MonitoredClub", "MonitoredClubRepository", "MonitoredClubService")
        assertThat(scheduler).doesNotContain("MonitoredClub", "MonitoredClubRepository", "MonitoredClubService")
        assertThat(acquisition).contains("props.ea.clubId")
    }

    @Test
    fun `database stores only an opaque webhook reference`() {
        val migration = Files.readString(Path.of("src/main/resources/db/migration/V8__create_monitored_clubs.sql"))
        assertThat(migration).contains("club_id", "PRIMARY KEY", "discord_webhook_secret_ref")
        assertThat(migration).doesNotContain("webhook_url", "webhook_token")
        assertThat(migration).contains("discord_webhook_secret_ref !~* '^https?://'")
    }
}
