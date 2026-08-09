package com.eafc26.discordstats.web

import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.scheduler.PollingStatusHolder
import com.eafc26.discordstats.service.AcquisitionStateHolder
import com.eafc26.discordstats.service.AcquisitionTrigger
import com.eafc26.discordstats.support.defaultClubProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PollingStatusControllerClubScopeTest {

    @Test
    fun `legacy status endpoint exposes only the configured default club`() {
        val defaultClub = ClubId("1104972")
        val otherClub = ClubId("2200000")
        val polling = PollingStatusHolder()
        val acquisition = AcquisitionStateHolder()
        acquisition.start(defaultClub, AcquisitionTrigger.MANUAL)
        acquisition.start(otherClub, AcquisitionTrigger.SCHEDULER)
        acquisition.fail(otherClub, "other club failure", "Other failed")
        val controller = PollingStatusController(
            polling,
            acquisition,
            defaultClubProvider(defaultClub),
        )

        val body = controller.status().body!!
        val state = body["acquisition"] as Map<*, *>

        assertThat(body["running"]).isEqualTo(true)
        assertThat(state["trigger"]).isEqualTo("MANUAL")
        assertThat(state["lastError"]).isNull()
    }
}
