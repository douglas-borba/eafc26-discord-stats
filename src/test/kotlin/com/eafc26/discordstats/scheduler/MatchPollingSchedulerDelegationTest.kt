package com.eafc26.discordstats.scheduler

import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class MatchPollingSchedulerDelegationTest {
    @Test
    fun `scheduler delegates exactly one cycle to coordinator`() {
        val coordinator: ClubPollingCoordinator = mock()

        MatchPollingScheduler(coordinator).poll()

        verify(coordinator).pollEnabledClubs(MatchPollingScheduler.INTERVAL_MS)
    }
}
