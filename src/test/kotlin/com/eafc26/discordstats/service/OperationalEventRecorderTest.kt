package com.eafc26.discordstats.service

import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.store.EventStatus
import com.eafc26.discordstats.store.OperationalEvent
import com.eafc26.discordstats.store.OperationalEventRepository
import com.eafc26.discordstats.store.DeliveryUncertaintyReason
import com.eafc26.discordstats.store.DiscordPublicationOrigin
import com.eafc26.discordstats.store.PublicationRecord
import com.eafc26.discordstats.store.PublicationState
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

    @Test
    fun `records immutable diagnostic trail for automatic uncertainty followed by manual resend`() {
        recorder.discordUncertain(
            club,
            "990976744430293",
            DeliveryUncertaintyReason.NETWORK_TIMEOUT,
            "read timed out",
            DiscordPublicationOrigin.AUTOMATIC_ACQUISITION,
        )
        recorder.discordManualResendRequested(
            club,
            "990976744430293",
            PublicationRecord(
                matchId = "990976744430293",
                state = PublicationState.DELIVERY_UNCERTAIN,
                attemptCount = 1,
                lastError = "NETWORK_TIMEOUT: read timed out",
            ),
        )
        recorder.discordSuccess(club, "990976744430293", DiscordPublicationOrigin.FORCE_PUBLISH)

        val events = argumentCaptor<OperationalEvent>()
        verify(repository, org.mockito.Mockito.times(3)).save(events.capture())

        assertThat(events.allValues.map { it.phase }).containsExactly("UNCERTAIN", "MANUAL_RESEND_REQUESTED", "DELIVERED")
        assertThat(events.allValues[0].message).contains("aquisição automática", "NETWORK_TIMEOUT")
        assertThat(events.allValues[1].message).contains("estado anterior: DELIVERY_UNCERTAIN", "NETWORK_TIMEOUT: read timed out")
        assertThat(events.allValues[2].message).contains("reenvio manual")
    }

    @Test
    fun `redacts Discord webhook URLs from publication diagnostics`() {
        recorder.discordUncertain(
            club,
            "match-secret-safe",
            DeliveryUncertaintyReason.NETWORK_EXCEPTION,
            "POST https://discord.com/api/webhooks/123456/secret-token reset by peer",
            DiscordPublicationOrigin.AUTOMATIC_ACQUISITION,
        )

        val event = argumentCaptor<OperationalEvent>()
        verify(repository).save(event.capture())

        assertThat(event.firstValue.message).contains("[Discord webhook]")
        assertThat(event.firstValue.message).doesNotContain("secret-token")
    }
}
