package com.eafc26.discordstats.dev

import com.eafc26.discordstats.config.AppProperties
import com.eafc26.discordstats.config.EaProperties
import com.eafc26.discordstats.config.PhraseBank
import com.eafc26.discordstats.config.PollingProperties
import com.eafc26.discordstats.config.WebhookConfigService
import com.eafc26.discordstats.discord.DiscordWebhookClient
import com.eafc26.discordstats.presentation.MatchSummaryBuilder
import com.eafc26.discordstats.service.AcquisitionResult
import com.eafc26.discordstats.service.AcquisitionStateHolder
import com.eafc26.discordstats.service.LatestMatchHolder
import com.eafc26.discordstats.service.MatchAcquisitionService
import com.eafc26.discordstats.store.PublishedMatchStore
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DevSimulatorServiceTest {

    private lateinit var fixtureGateway: FixtureEaClubsGateway
    private lateinit var publishedMatchStore: PublishedMatchStore
    private lateinit var latestMatchHolder: LatestMatchHolder
    private lateinit var stateHolder: AcquisitionStateHolder
    private lateinit var matchSummaryBuilder: MatchSummaryBuilder
    private lateinit var acquisitionService: MatchAcquisitionService
    private lateinit var simulatorService: DevSimulatorService
    private lateinit var webhookConfigService: WebhookConfigService
    private lateinit var webhookClient: DiscordWebhookClient

    private val clubId = "1104972"

    @BeforeEach
    fun setUp() {
        val objectMapper = jacksonObjectMapper()
        fixtureGateway = FixtureEaClubsGateway(objectMapper)
        publishedMatchStore = mock()
        latestMatchHolder = LatestMatchHolder()
        stateHolder = AcquisitionStateHolder()
        matchSummaryBuilder = MatchSummaryBuilder(PhraseBank(objectMapper))
        webhookConfigService = mock()
        webhookClient = mock()

        val props = AppProperties(
            ea = EaProperties(clubId = clubId, clubName = "Associação BF"),
            polling = PollingProperties(publishExistingOnFirstRun = false),
        )

        acquisitionService = MatchAcquisitionService(
            fixtureGateway,
            publishedMatchStore,
            webhookClient,
            props,
            stateHolder,
            latestMatchHolder,
            matchSummaryBuilder,
        )

        simulatorService = DevSimulatorService(
            acquisitionService,
            fixtureGateway,
            latestMatchHolder,
            webhookConfigService,
        )

        whenever(publishedMatchStore.loadIds()).thenReturn(emptySet())
        // Enable dev mode by default for most tests
        whenever(webhookConfigService.isDevelopmentModeEnabled()).thenReturn(true)
    }

    @Nested
    inner class SimulateLatest {

        @Test
        fun `simulates acquisition with fixture data and returns simulated result`() {
            fixtureGateway.matchesFixturePath = "fixtures/dev/latest-matches.json"

            val result = simulatorService.simulateLatest()

            // Should return a simulated result, NOT baselineEstablished
            assertThat(result).isInstanceOf(AcquisitionResult.Processed::class.java)
            val processed = result as AcquisitionResult.Processed
            assertThat(processed.simulated).isTrue()
            assertThat(processed.baselineEstablished).isFalse()
            assertThat(processed.simulatedMatch).isNotNull
            assertThat(processed.simulatedMatch?.matchId).isEqualTo("dev-match-001")
        }

        @Test
        fun `caches presentation after simulation`() {
            fixtureGateway.matchesFixturePath = "fixtures/dev/latest-matches.json"

            simulatorService.simulateLatest()

            assertThat(latestMatchHolder.hasPresentation()).isTrue()
            val presentation = latestMatchHolder.presentation()
            assertThat(presentation?.matchId).isEqualTo("dev-match-001")
        }

        @Test
        fun `marks cached presentation as simulated`() {
            fixtureGateway.matchesFixturePath = "fixtures/dev/latest-matches.json"

            simulatorService.simulateLatest()

            assertThat(latestMatchHolder.isSimulated()).isTrue()
            val snapshot = latestMatchHolder.snapshot()
            assertThat(snapshot.simulated).isTrue()
        }

        @Test
        fun `NEVER calls Discord webhook client`() {
            fixtureGateway.matchesFixturePath = "fixtures/dev/latest-matches.json"

            simulatorService.simulateLatest()

            // Verify Discord was NEVER called
            verify(webhookClient, never()).send(any())
            verify(webhookClient, never()).sendHistory(any())
        }

        @Test
        fun `NEVER persists to published match store`() {
            fixtureGateway.matchesFixturePath = "fixtures/dev/latest-matches.json"

            simulatorService.simulateLatest()

            // Verify store was NEVER updated
            verify(publishedMatchStore, never()).saveIds(any())
        }

        @Test
        fun `does not affect deduplication state`() {
            fixtureGateway.matchesFixturePath = "fixtures/dev/latest-matches.json"
            whenever(publishedMatchStore.loadIds()).thenReturn(setOf("existing-match"))

            simulatorService.simulateLatest()

            // Store should only be read, never written
            verify(publishedMatchStore, never()).saveIds(any())
        }

        @Test
        fun `returns empty published list (no Discord delivery)`() {
            fixtureGateway.matchesFixturePath = "fixtures/dev/latest-matches.json"

            val result = simulatorService.simulateLatest()

            val processed = result as AcquisitionResult.Processed
            assertThat(processed.published).isEmpty()
            assertThat(processed.alreadyPublished).isEmpty()
            assertThat(processed.failed).isEmpty()
        }

        @Test
        fun `throws DevelopmentModeDisabledException when dev mode is disabled`() {
            whenever(webhookConfigService.isDevelopmentModeEnabled()).thenReturn(false)

            assertThatThrownBy { simulatorService.simulateLatest() }
                .isInstanceOf(DevelopmentModeDisabledException::class.java)
                .hasMessage("Development mode is disabled")
        }

        @Test
        fun `repeated simulation increments LatestMatchHolder version`() {
            fixtureGateway.matchesFixturePath = "fixtures/dev/latest-matches.json"

            simulatorService.simulateLatest()
            val version1 = latestMatchHolder.version()

            simulatorService.simulateLatest()
            val version2 = latestMatchHolder.version()

            simulatorService.simulateLatest()
            val version3 = latestMatchHolder.version()

            // Each simulation should increment the version
            assertThat(version2).isGreaterThan(version1)
            assertThat(version3).isGreaterThan(version2)
        }

        @Test
        fun `repeated simulation replaces cached presentation each time`() {
            fixtureGateway.matchesFixturePath = "fixtures/dev/latest-matches.json"

            simulatorService.simulateLatest()
            val presentation1 = latestMatchHolder.presentation()

            simulatorService.simulateLatest()
            val presentation2 = latestMatchHolder.presentation()

            // The matchId should be the same (same fixture)
            assertThat(presentation1?.matchId).isEqualTo(presentation2?.matchId)
            // But each is a new presentation object (regenerated)
            assertThat(presentation1).isNotSameAs(presentation2)
        }

        @Test
        fun `simulation processes same fixture multiple times without deduplication block`() {
            fixtureGateway.matchesFixturePath = "fixtures/dev/latest-matches.json"

            // First simulation
            val result1 = simulatorService.simulateLatest()
            assertThat(result1).isInstanceOf(AcquisitionResult.Processed::class.java)
            assertThat((result1 as AcquisitionResult.Processed).simulated).isTrue()

            // Second simulation - should NOT be blocked
            val result2 = simulatorService.simulateLatest()
            assertThat(result2).isInstanceOf(AcquisitionResult.Processed::class.java)
            assertThat((result2 as AcquisitionResult.Processed).simulated).isTrue()

            // Third simulation - should NOT be blocked
            val result3 = simulatorService.simulateLatest()
            assertThat(result3).isInstanceOf(AcquisitionResult.Processed::class.java)
            assertThat((result3 as AcquisitionResult.Processed).simulated).isTrue()
        }
    }

    @Nested
    inner class Reset {

        @Test
        fun `clears latest match holder`() {
            // First simulate to cache a presentation
            fixtureGateway.matchesFixturePath = "fixtures/dev/latest-matches.json"
            simulatorService.simulateLatest()
            assertThat(latestMatchHolder.hasPresentation()).isTrue()

            // Reset
            simulatorService.reset()

            assertThat(latestMatchHolder.hasPresentation()).isFalse()
        }

        @Test
        fun `resets fixture gateway settings`() {
            fixtureGateway.simulateUnavailable = true
            fixtureGateway.matchesFixturePath = "custom/path.json"

            simulatorService.reset()

            assertThat(fixtureGateway.simulateUnavailable).isFalse()
            assertThat(fixtureGateway.matchesFixturePath)
                .isEqualTo(FixtureEaClubsGateway.DEFAULT_MATCHES_FIXTURE)
        }

        @Test
        fun `throws DevelopmentModeDisabledException when dev mode is disabled`() {
            whenever(webhookConfigService.isDevelopmentModeEnabled()).thenReturn(false)

            assertThatThrownBy { simulatorService.reset() }
                .isInstanceOf(DevelopmentModeDisabledException::class.java)
                .hasMessage("Development mode is disabled")
        }
    }

    @Nested
    inner class IsEnabled {

        @Test
        fun `returns true when dev mode is enabled`() {
            whenever(webhookConfigService.isDevelopmentModeEnabled()).thenReturn(true)

            assertThat(simulatorService.isEnabled()).isTrue()
        }

        @Test
        fun `returns false when dev mode is disabled`() {
            whenever(webhookConfigService.isDevelopmentModeEnabled()).thenReturn(false)

            assertThat(simulatorService.isEnabled()).isFalse()
        }
    }
}

