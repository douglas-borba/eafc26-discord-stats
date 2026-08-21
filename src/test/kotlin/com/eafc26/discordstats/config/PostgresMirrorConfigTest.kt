package com.eafc26.discordstats.config

import com.eafc26.discordstats.store.MirroringCanonicalMatchRepository
import com.eafc26.discordstats.store.PostgresCanonicalMatchRepository
import com.eafc26.discordstats.store.PostgresPublishedMatchStore
import com.eafc26.discordstats.store.RecoveredPublication
import com.eafc26.discordstats.store.PublicationRecord
import com.eafc26.discordstats.store.PublicationState
import com.eafc26.discordstats.store.DeliveryUncertaintyReason
import com.eafc26.discordstats.store.DiscordPublicationOrigin
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.service.OperationalEventRecorder
import com.eafc26.discordstats.config.postgres.PostgresMirrorConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class PostgresMirrorConfigTest {

    @Test
    fun `mirror-enabled defaults to false in AppProperties`() {
        val props = AppProperties()
        assertThat(props.postgres.mirrorEnabled).isFalse()
    }

    @Test
    fun `acquisition-enabled defaults to true in AppProperties`() {
        val props = AppProperties()
        assertThat(props.acquisition.enabled).isTrue()
    }

    @Test
    fun `sync-enabled defaults to false in AppProperties`() {
        val props = AppProperties()
        assertThat(props.postgres.syncEnabled).isFalse()
    }

    @Test
    fun `sync-interval defaults to 5 minutes in AppProperties`() {
        val props = AppProperties()
        assertThat(props.postgres.syncIntervalMs).isEqualTo(300_000L)
    }

    @Test
    fun `startup recovery records a distinguishable uncertainty diagnostic`() {
        val store = mock<PostgresPublishedMatchStore>()
        val events = mock<OperationalEventRecorder>()
        val recovered = RecoveredPublication(
            clubId = ClubId("1104972"),
            record = PublicationRecord(
                matchId = "990976744430293",
                state = PublicationState.DELIVERING,
                attemptCount = 1,
                lastAttemptAt = 1_724_207_320,
            ),
        )
        whenever(store.upgradeDeliveringRecords()).thenReturn(listOf(recovered))

        PostgresMirrorConfig()
            .publicationStateUpgradeRunner(mock(), store, events)
            .run(mock())

        val invocation = org.mockito.Mockito.mockingDetails(events).invocations.single {
            it.method.name.startsWith("discordUncertain")
        }
        assertThat(invocation.arguments.map { it?.toString() }).containsExactly(
            "1104972",
            "990976744430293",
            DeliveryUncertaintyReason.STARTUP_RECOVERY.name,
            "Registro DELIVERING encontrado na inicialização; a causa original não está disponível.",
            DiscordPublicationOrigin.STARTUP_RECOVERY.name,
            null,
        )
    }
}
