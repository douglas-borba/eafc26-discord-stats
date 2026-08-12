package com.eafc26.discordstats.service

import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.store.EventStatus
import com.eafc26.discordstats.store.OperationalEvent
import com.eafc26.discordstats.store.OperationalEventRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify

class OperationalEventRecorderTest {
    private val repository = mock<OperationalEventRepository>()
    private val recorder = OperationalEventRecorder(repository)
    private val club = ClubId("1104972")

    @Test
    fun `records scoped administrative operation outcomes without sensitive values`() {
        recorder.adminPollStarted(club)
        recorder.acquisitionStarted(club, AcquisitionTrigger.ADMIN_POLL.name)
        recorder.adminPollCompleted(club, 12)
        recorder.eaTest(club, success = false, durationMs = 34, message = "EA unavailable", errorCode = "503")
        recorder.discordTest(club, success = false, durationMs = 56, message = "Webhook unavailable", errorCode = "NO_DESTINATION")

        val events = argumentCaptor<OperationalEvent>()
        verify(repository, org.mockito.Mockito.times(5)).save(events.capture())

        assertThat(events.allValues.map { it.eventType }).containsExactly("ADMIN_POLL", "ACQUISITION", "ADMIN_POLL", "EA_TEST", "DISCORD_TEST")
        assertThat(events.allValues).allMatch { it.clubId == club }
        assertThat(events.allValues[1].message).isEqualTo("Trigger: ADMIN_POLL")
        assertThat(events.allValues[2]).extracting(OperationalEvent::phase, OperationalEvent::status).containsExactly("SUCCESS", EventStatus.SUCCESS)
        assertThat(events.allValues[3]).extracting(OperationalEvent::phase, OperationalEvent::errorCode).containsExactly("FAILURE", "503")
        assertThat(events.allValues[4].message).doesNotContain("http")
    }
}
