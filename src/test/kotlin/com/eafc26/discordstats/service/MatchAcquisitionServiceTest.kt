package com.eafc26.discordstats.service

import com.eafc26.discordstats.config.AppProperties
import com.eafc26.discordstats.config.EaProperties
import com.eafc26.discordstats.config.PhraseBank
import com.eafc26.discordstats.config.PollingProperties
import com.eafc26.discordstats.discord.DiscordDeliveryException
import com.eafc26.discordstats.discord.DiscordWebhookClient
import com.eafc26.discordstats.ea.EaApiResult
import com.eafc26.discordstats.ea.EaClubsGateway
import com.eafc26.discordstats.ea.model.ClubDetails
import com.eafc26.discordstats.ea.model.ClubMatchEntry
import com.eafc26.discordstats.ea.model.MatchResponse
import com.eafc26.discordstats.ea.model.MemberStats
import com.eafc26.discordstats.ea.model.PlayerEntry
import com.eafc26.discordstats.presentation.MatchSummaryBuilder
import com.eafc26.discordstats.store.PublishedMatchStore
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class MatchAcquisitionServiceTest {

    private lateinit var gateway: EaClubsGateway
    private lateinit var store: PublishedMatchStore
    private lateinit var webhookClient: DiscordWebhookClient
    private lateinit var stateHolder: AcquisitionStateHolder
    private lateinit var latestMatchHolder: LatestMatchHolder
    private lateinit var matchSummaryBuilder: MatchSummaryBuilder
    private lateinit var service: MatchAcquisitionService

    private val clubId = "12345"

    private fun makeService(publishExistingOnFirstRun: Boolean = false): MatchAcquisitionService {
        val props = AppProperties(
            ea = EaProperties(clubId = clubId, clubName = "Test FC"),
            polling = PollingProperties(publishExistingOnFirstRun = publishExistingOnFirstRun),
        )
        return MatchAcquisitionService(gateway, store, webhookClient, props, stateHolder, latestMatchHolder, matchSummaryBuilder)
    }

    @BeforeEach
    fun setUp() {
        gateway = mock()
        store = mock()
        webhookClient = mock()
        stateHolder = AcquisitionStateHolder()  // Use real instance for integration-style tests
        latestMatchHolder = LatestMatchHolder()  // Use real instance
        matchSummaryBuilder = MatchSummaryBuilder(PhraseBank(jacksonObjectMapper()))  // Use real instance
        service = makeService()
        whenever(store.loadIds()).thenReturn(emptySet())
    }

    // -------------------------------------------------------------------------
    // Helper functions
    // -------------------------------------------------------------------------

    private fun match(
        id: String,
        ts: Long = System.currentTimeMillis() / 1000,
        ourScore: String = "2",
        oppScore: String = "1",
    ): MatchResponse = MatchResponse(
        matchId = id,
        timestamp = ts,
        clubs = mapOf(
            clubId to ClubMatchEntry(
                details = ClubDetails(name = "Test FC"),
                score = ourScore,
                result = "1",
            ),
            "opponent" to ClubMatchEntry(
                details = ClubDetails(name = "Opponent FC"),
                score = oppScore,
                result = "0",
            ),
        ),
        players = emptyMap(),
    )

    // -------------------------------------------------------------------------
    // EA API Error Handling
    // -------------------------------------------------------------------------

    @Nested
    inner class EaApiErrors {

        @Test
        fun `returns NoMatches when EA returns no matches`() {
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.NoMatches)

            val result = service.acquire(AcquisitionTrigger.MANUAL)

            assertThat(result).isEqualTo(AcquisitionResult.NoMatches)
            verify(webhookClient, never()).send(any())
        }

        @Test
        fun `returns EaUnavailable when EA API is down`() {
            whenever(gateway.getLatestMatches(clubId))
                .thenReturn(EaApiResult.Unavailable(503, "Service unavailable"))

            val result = service.acquire(AcquisitionTrigger.MANUAL)

            assertThat(result).isInstanceOf(AcquisitionResult.EaUnavailable::class.java)
            val error = result as AcquisitionResult.EaUnavailable
            assertThat(error.statusCode).isEqualTo(503)
            assertThat(error.message).isEqualTo("Service unavailable")
        }

        @Test
        fun `returns EaUnavailable on unexpected payload`() {
            whenever(gateway.getLatestMatches(clubId))
                .thenReturn(EaApiResult.UnexpectedPayload(RuntimeException("Parse error")))

            val result = service.acquire(AcquisitionTrigger.MANUAL)

            assertThat(result).isInstanceOf(AcquisitionResult.EaUnavailable::class.java)
        }
    }

    // -------------------------------------------------------------------------
    // Manual/CLI Trigger (Latest Only)
    // -------------------------------------------------------------------------

    @Nested
    inner class ManualTrigger {

        @Test
        fun `publishes latest match when not already published`() {
            val match = match("m1")
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(match)))

            val result = service.acquire(AcquisitionTrigger.MANUAL)

            assertThat(result).isInstanceOf(AcquisitionResult.Processed::class.java)
            val processed = result as AcquisitionResult.Processed
            assertThat(processed.published).hasSize(1)
            assertThat(processed.published[0].matchId).isEqualTo("m1")
            assertThat(processed.published[0].summary).contains("Test FC")
            verify(webhookClient).send(any())
            verify(store).saveIds(setOf("m1"))
        }

        @Test
        fun `skips already published match`() {
            val match = match("m1")
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(match)))
            whenever(store.loadIds()).thenReturn(setOf("m1"))

            val result = service.acquire(AcquisitionTrigger.MANUAL)

            assertThat(result).isInstanceOf(AcquisitionResult.Processed::class.java)
            val processed = result as AcquisitionResult.Processed
            assertThat(processed.published).isEmpty()
            assertThat(processed.alreadyPublished).hasSize(1)
            assertThat(processed.allSkipped()).isTrue()
            verify(webhookClient, never()).send(any())
        }

        @Test
        fun `CLI trigger behaves same as MANUAL`() {
            val match = match("m1")
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(match)))

            val result = service.acquire(AcquisitionTrigger.CLI)

            assertThat(result).isInstanceOf(AcquisitionResult.Processed::class.java)
            val processed = result as AcquisitionResult.Processed
            assertThat(processed.published).hasSize(1)
        }

        @Test
        fun `picks latest match by timestamp when multiple available`() {
            val older = match("m1", ts = 1000)
            val newer = match("m2", ts = 2000)
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(older, newer)))

            val result = service.acquire(AcquisitionTrigger.MANUAL)

            val processed = result as AcquisitionResult.Processed
            assertThat(processed.published).hasSize(1)
            assertThat(processed.published[0].matchId).isEqualTo("m2")
        }
    }

    // -------------------------------------------------------------------------
    // Scheduler Trigger (All New Matches)
    // -------------------------------------------------------------------------

    @Nested
    inner class SchedulerTrigger {

        @Test
        fun `publishes all new matches sorted by timestamp`() {
            val older = match("m1", ts = 1000)
            val newer = match("m2", ts = 2000)
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(newer, older)))
            whenever(store.loadIds()).thenReturn(setOf("existing"))

            val result = service.acquire(AcquisitionTrigger.SCHEDULER)

            val processed = result as AcquisitionResult.Processed
            assertThat(processed.published).hasSize(2)
            assertThat(processed.published[0].matchId).isEqualTo("m1") // older first
            assertThat(processed.published[1].matchId).isEqualTo("m2") // newer second
            verify(webhookClient, times(2)).send(any())
        }

        @Test
        fun `skips already published matches`() {
            val published = match("m1", ts = 1000)
            val newMatch = match("m2", ts = 2000)
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(published, newMatch)))
            whenever(store.loadIds()).thenReturn(setOf("m1"))

            val result = service.acquire(AcquisitionTrigger.SCHEDULER)

            val processed = result as AcquisitionResult.Processed
            assertThat(processed.published).hasSize(1)
            assertThat(processed.published[0].matchId).isEqualTo("m2")
            verify(webhookClient, times(1)).send(any())
        }

        @Test
        fun `returns nothing new when all matches already published`() {
            val match = match("m1")
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(match)))
            whenever(store.loadIds()).thenReturn(setOf("m1"))

            val result = service.acquire(AcquisitionTrigger.SCHEDULER)

            val processed = result as AcquisitionResult.Processed
            assertThat(processed.published).isEmpty()
            assertThat(processed.alreadyPublished).hasSize(1)
            verify(webhookClient, never()).send(any())
        }

        @Test
        fun `DEV_SIMULATOR is web-only and never delivers to Discord`() {
            val match = match("m1")
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(match)))
            whenever(store.loadIds()).thenReturn(setOf("existing"))

            val result = service.acquire(AcquisitionTrigger.DEV_SIMULATOR)

            val processed = result as AcquisitionResult.Processed
            // Simulations never "publish" - they just cache
            assertThat(processed.simulated).isTrue()
            assertThat(processed.published).isEmpty()
            assertThat(processed.simulatedMatch).isNotNull
            assertThat(processed.simulatedMatch?.matchId).isEqualTo("m1")
            // Discord should NEVER be called
            verify(webhookClient, never()).send(any())
            verify(webhookClient, never()).sendHistory(any())
            // Persistence should NEVER happen
            verify(store, never()).saveIds(any())
        }
    }

    // -------------------------------------------------------------------------
    // First Run Handling
    // -------------------------------------------------------------------------

    @Nested
    inner class FirstRun {

        @Test
        fun `establishes baseline without publishing when publishExistingOnFirstRun is false`() {
            service = makeService(publishExistingOnFirstRun = false)
            val match = match("m1")
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(match)))
            whenever(store.loadIds()).thenReturn(emptySet())

            val result = service.acquire(AcquisitionTrigger.SCHEDULER)

            val processed = result as AcquisitionResult.Processed
            assertThat(processed.baselineEstablished).isTrue()
            assertThat(processed.published).isEmpty()
            verify(webhookClient, never()).send(any())
            verify(store).saveIds(setOf("m1"))
        }

        @Test
        fun `publishes all matches when publishExistingOnFirstRun is true`() {
            service = makeService(publishExistingOnFirstRun = true)
            val older = match("m1", ts = 1000)
            val newer = match("m2", ts = 2000)
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(newer, older)))
            whenever(store.loadIds()).thenReturn(emptySet())

            val result = service.acquire(AcquisitionTrigger.SCHEDULER)

            val processed = result as AcquisitionResult.Processed
            assertThat(processed.baselineEstablished).isFalse()
            assertThat(processed.published).hasSize(2)
            verify(webhookClient, times(2)).send(any())
        }

        @Test
        fun `first run only applies to SCHEDULER trigger`() {
            service = makeService(publishExistingOnFirstRun = false)
            val match = match("m1")
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(match)))
            whenever(store.loadIds()).thenReturn(emptySet())

            // MANUAL trigger doesn't do first-run baseline
            val result = service.acquire(AcquisitionTrigger.MANUAL)

            val processed = result as AcquisitionResult.Processed
            assertThat(processed.baselineEstablished).isFalse()
            assertThat(processed.published).hasSize(1)
            verify(webhookClient).send(any())
        }
    }

    // -------------------------------------------------------------------------
    // Force Resend
    // -------------------------------------------------------------------------

    @Nested
    inner class ForceResend {

        @Test
        fun `force resends even if already published`() {
            val match = match("m1")
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(match)))
            whenever(store.loadIds()).thenReturn(setOf("m1"))

            val result = service.acquire(AcquisitionTrigger.FORCE_RESEND)

            assertThat(result).isInstanceOf(AcquisitionResult.ForceResent::class.java)
            val resent = result as AcquisitionResult.ForceResent
            assertThat(resent.match.matchId).isEqualTo("m1")
            verify(webhookClient).send(any())
        }

        @Test
        fun `force resend does not persist match ID`() {
            val match = match("m1")
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(match)))
            whenever(store.loadIds()).thenReturn(emptySet())

            service.acquire(AcquisitionTrigger.FORCE_RESEND)

            verify(store, never()).saveIds(any())
        }

        @Test
        fun `force resend picks latest match by timestamp`() {
            val older = match("m1", ts = 1000)
            val newer = match("m2", ts = 2000)
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(older, newer)))

            val result = service.acquire(AcquisitionTrigger.FORCE_RESEND)

            val resent = result as AcquisitionResult.ForceResent
            assertThat(resent.match.matchId).isEqualTo("m2")
        }
    }

    // -------------------------------------------------------------------------
    // Discord Error Handling
    // -------------------------------------------------------------------------

    @Nested
    inner class DiscordErrors {

        @Test
        fun `returns WebhookNotConfigured when webhook not set`() {
            val match = match("m1")
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(match)))
            doThrow(IllegalStateException("Webhook not configured")).whenever(webhookClient).send(any())

            val result = service.acquire(AcquisitionTrigger.MANUAL)

            assertThat(result).isEqualTo(AcquisitionResult.WebhookNotConfigured)
            verify(store, never()).saveIds(any())
        }

        @Test
        fun `returns failed match on Discord delivery error for MANUAL`() {
            val match = match("m1")
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(match)))
            doThrow(DiscordDeliveryException("Rate limited")).whenever(webhookClient).send(any())

            val result = service.acquire(AcquisitionTrigger.MANUAL)

            val processed = result as AcquisitionResult.Processed
            assertThat(processed.published).isEmpty()
            assertThat(processed.failed).hasSize(1)
            assertThat(processed.failed[0].reason).contains("Rate limited")
            verify(store, never()).saveIds(any())
        }

        @Test
        fun `continues to next match on Discord error for SCHEDULER`() {
            val m1 = match("m1", ts = 1000)
            val m2 = match("m2", ts = 2000)
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(m1, m2)))
            whenever(store.loadIds()).thenReturn(setOf("existing"))

            // First call fails, second succeeds
            var callCount = 0
            whenever(webhookClient.send(any())).thenAnswer {
                callCount++
                if (callCount == 1) throw DiscordDeliveryException("Temporary error")
            }

            val result = service.acquire(AcquisitionTrigger.SCHEDULER)

            val processed = result as AcquisitionResult.Processed
            assertThat(processed.published).hasSize(1)
            assertThat(processed.published[0].matchId).isEqualTo("m2")
            assertThat(processed.failed).hasSize(1)
            assertThat(processed.failed[0].matchId).isEqualTo("m1")
        }

        @Test
        fun `aborts SCHEDULER on webhook not configured`() {
            val m1 = match("m1", ts = 1000)
            val m2 = match("m2", ts = 2000)
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(m1, m2)))
            whenever(store.loadIds()).thenReturn(setOf("existing"))
            doThrow(IllegalStateException("Webhook not configured")).whenever(webhookClient).send(any())

            val result = service.acquire(AcquisitionTrigger.SCHEDULER)

            assertThat(result).isEqualTo(AcquisitionResult.WebhookNotConfigured)
        }
    }

    // -------------------------------------------------------------------------
    // Persistence
    // -------------------------------------------------------------------------

    @Nested
    inner class Persistence {

        @Test
        fun `marks match as persistence error when store fails`() {
            val match = match("m1")
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(match)))
            doThrow(RuntimeException("Disk full")).whenever(store).saveIds(any())

            val result = service.acquire(AcquisitionTrigger.MANUAL)

            val processed = result as AcquisitionResult.Processed
            assertThat(processed.published).hasSize(1)
            assertThat(processed.published[0].persistedSuccessfully).isFalse()
        }

        @Test
        fun `persists incrementally for SCHEDULER`() {
            val m1 = match("m1", ts = 1000)
            val m2 = match("m2", ts = 2000)
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(m1, m2)))
            whenever(store.loadIds()).thenReturn(setOf("existing"))

            service.acquire(AcquisitionTrigger.SCHEDULER)

            // Should save twice (once per match)
            verify(store, times(2)).saveIds(any())
        }
    }

    // -------------------------------------------------------------------------
    // Concurrency (AcquisitionLock)
    // -------------------------------------------------------------------------

    @Nested
    inner class Concurrency {

        @Test
        fun `returns Busy when another acquisition is in progress`() {
            val executor = Executors.newSingleThreadExecutor()
            val actionStarted = CountDownLatch(1)
            val actionCanFinish = CountDownLatch(1)

            whenever(gateway.getLatestMatches(clubId)).thenAnswer {
                actionStarted.countDown()
                actionCanFinish.await(5, TimeUnit.SECONDS)
                EaApiResult.Success(listOf(match("m1")))
            }

            // Start first acquisition
            executor.submit { service.acquire(AcquisitionTrigger.SCHEDULER) }

            // Wait for it to start
            actionStarted.await(1, TimeUnit.SECONDS)

            // Try second acquisition
            val result = service.acquire(AcquisitionTrigger.MANUAL)

            assertThat(result).isEqualTo(AcquisitionResult.Busy)

            // Cleanup
            actionCanFinish.countDown()
            executor.shutdown()
            executor.awaitTermination(2, TimeUnit.SECONDS)
        }

        @Test
        fun `concurrent acquisitions result in only one execution`() {
            val executor = Executors.newFixedThreadPool(10)
            val executionCount = AtomicInteger(0)
            val allStarted = CountDownLatch(10)
            val canProceed = CountDownLatch(1)
            val allFinished = CountDownLatch(10)

            whenever(gateway.getLatestMatches(clubId)).thenAnswer {
                executionCount.incrementAndGet()
                Thread.sleep(50)
                EaApiResult.Success(listOf(match("m1")))
            }

            repeat(10) {
                executor.submit {
                    allStarted.countDown()
                    canProceed.await(5, TimeUnit.SECONDS)
                    service.acquire(AcquisitionTrigger.MANUAL)
                    allFinished.countDown()
                }
            }

            allStarted.await(1, TimeUnit.SECONDS)
            canProceed.countDown()
            allFinished.await(5, TimeUnit.SECONDS)

            assertThat(executionCount.get()).isEqualTo(1)

            executor.shutdown()
        }
    }

    // -------------------------------------------------------------------------
    // History Webhook
    // -------------------------------------------------------------------------

    @Nested
    inner class HistoryWebhook {

        @Test
        fun `sends to history webhook after main webhook`() {
            val match = match("m1")
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(match)))

            service.acquire(AcquisitionTrigger.MANUAL)

            verify(webhookClient).send(any())
            verify(webhookClient).sendHistory(any())
        }

        @Test
        fun `sends to history webhook on force resend`() {
            val match = match("m1")
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(match)))
            whenever(store.loadIds()).thenReturn(setOf("m1"))

            service.acquire(AcquisitionTrigger.FORCE_RESEND)

            verify(webhookClient).sendHistory(any())
        }
    }

    // -------------------------------------------------------------------------
    // Custom Gateway (for DEV_SIMULATOR)
    // -------------------------------------------------------------------------

    @Nested
    inner class CustomGateway {

        @Test
        fun `uses provided gateway instead of default`() {
            val customGateway: EaClubsGateway = mock()
            val customMatch = match("custom-m1")
            whenever(customGateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(customMatch)))
            whenever(store.loadIds()).thenReturn(setOf("existing"))

            val result = service.acquire(AcquisitionTrigger.DEV_SIMULATOR, customGateway)

            val processed = result as AcquisitionResult.Processed
            // DEV_SIMULATOR returns simulated match, not published
            assertThat(processed.simulated).isTrue()
            assertThat(processed.simulatedMatch?.matchId).isEqualTo("custom-m1")
            verify(gateway, never()).getLatestMatches(any())
            verify(customGateway).getLatestMatches(clubId)
        }
    }

    // -------------------------------------------------------------------------
    // DEV_SIMULATOR Trigger (Web-Only Simulation)
    // -------------------------------------------------------------------------

    @Nested
    inner class DevSimulatorTrigger {

        @Test
        fun `returns simulated result with match summary`() {
            val match = match("sim-1")
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(match)))

            val result = service.acquire(AcquisitionTrigger.DEV_SIMULATOR)

            val processed = result as AcquisitionResult.Processed
            assertThat(processed.simulated).isTrue()
            assertThat(processed.simulatedMatch).isNotNull
            assertThat(processed.simulatedMatch?.matchId).isEqualTo("sim-1")
            assertThat(processed.simulatedMatch?.summary).contains("Test FC")
        }

        @Test
        fun `never calls Discord webhook for simulation`() {
            val match = match("sim-2")
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(match)))

            service.acquire(AcquisitionTrigger.DEV_SIMULATOR)

            verify(webhookClient, never()).send(any())
            verify(webhookClient, never()).sendHistory(any())
        }

        @Test
        fun `never persists simulated matches`() {
            val match = match("sim-3")
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(match)))

            service.acquire(AcquisitionTrigger.DEV_SIMULATOR)

            verify(store, never()).saveIds(any())
        }

        @Test
        fun `caches presentation and marks as simulated`() {
            val match = match("sim-4")
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(match)))

            service.acquire(AcquisitionTrigger.DEV_SIMULATOR)

            assertThat(latestMatchHolder.hasPresentation()).isTrue()
            assertThat(latestMatchHolder.isSimulated()).isTrue()
            assertThat(latestMatchHolder.presentation()?.matchId).isEqualTo("sim-4")
        }

        @Test
        fun `returns empty published and alreadyPublished lists`() {
            val match = match("sim-5")
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(match)))

            val result = service.acquire(AcquisitionTrigger.DEV_SIMULATOR)

            val processed = result as AcquisitionResult.Processed
            assertThat(processed.published).isEmpty()
            assertThat(processed.alreadyPublished).isEmpty()
            assertThat(processed.failed).isEmpty()
        }

        @Test
        fun `does not check deduplication state for simulations`() {
            val match = match("sim-6")
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(match)))
            // Even if match was already published in production, simulation still runs
            whenever(store.loadIds()).thenReturn(setOf("sim-6"))

            val result = service.acquire(AcquisitionTrigger.DEV_SIMULATOR)

            val processed = result as AcquisitionResult.Processed
            assertThat(processed.simulated).isTrue()
            assertThat(processed.simulatedMatch?.matchId).isEqualTo("sim-6")
        }

        @Test
        fun `picks latest match by timestamp for simulation`() {
            val older = match("sim-older", ts = 1000)
            val newer = match("sim-newer", ts = 2000)
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(older, newer)))

            val result = service.acquire(AcquisitionTrigger.DEV_SIMULATOR)

            val processed = result as AcquisitionResult.Processed
            assertThat(processed.simulatedMatch?.matchId).isEqualTo("sim-newer")
        }

        @Test
        fun `does not affect baselineEstablished flag`() {
            val match = match("sim-7")
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(match)))
            whenever(store.loadIds()).thenReturn(emptySet())

            val result = service.acquire(AcquisitionTrigger.DEV_SIMULATOR)

            val processed = result as AcquisitionResult.Processed
            assertThat(processed.baselineEstablished).isFalse()
            assertThat(processed.simulated).isTrue()
        }
    }

    // -------------------------------------------------------------------------
    // State Holder Integration
    // -------------------------------------------------------------------------

    @Nested
    inner class StateHolderIntegration {

        @Test
        fun `updates state holder on successful acquisition`() {
            val match = match("m1")
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(match)))

            service.acquire(AcquisitionTrigger.MANUAL)

            val state = stateHolder.current()
            assertThat(state.currentPhase).isEqualTo(AcquisitionPhase.COMPLETED)
            assertThat(state.trigger).isEqualTo(AcquisitionTrigger.MANUAL)
            assertThat(state.completedPhases).contains(AcquisitionPhase.FETCHING)
            assertThat(state.completedPhases).contains(AcquisitionPhase.PROCESSING)
            assertThat(state.lastError).isNull()
        }

        @Test
        fun `updates state holder on EA failure`() {
            whenever(gateway.getLatestMatches(clubId))
                .thenReturn(EaApiResult.Unavailable(503, "Service unavailable"))

            service.acquire(AcquisitionTrigger.SCHEDULER)

            val state = stateHolder.current()
            assertThat(state.currentPhase).isEqualTo(AcquisitionPhase.FAILED)
            assertThat(state.lastError).contains("Service unavailable")
        }

        @Test
        fun `updates state holder on busy rejection`() {
            val executor = Executors.newSingleThreadExecutor()
            val actionStarted = CountDownLatch(1)
            val actionCanFinish = CountDownLatch(1)

            whenever(gateway.getLatestMatches(clubId)).thenAnswer {
                actionStarted.countDown()
                actionCanFinish.await(5, TimeUnit.SECONDS)
                EaApiResult.Success(listOf(match("m1")))
            }

            // Start first acquisition
            executor.submit { service.acquire(AcquisitionTrigger.SCHEDULER) }

            // Wait for it to start
            actionStarted.await(1, TimeUnit.SECONDS)

            // Try second acquisition - should be rejected
            service.acquire(AcquisitionTrigger.MANUAL)

            val state = stateHolder.current()
            assertThat(state.currentStatus).contains("MANUAL")
            assertThat(state.currentStatus).contains("rejeitada")

            // Cleanup
            actionCanFinish.countDown()
            executor.shutdown()
            executor.awaitTermination(2, TimeUnit.SECONDS)
        }

        @Test
        fun `state holder tracks execution ID`() {
            val match = match("m1")
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(match)))

            service.acquire(AcquisitionTrigger.CLI)

            val state = stateHolder.current()
            assertThat(state.executionId).isNotNull()
            assertThat(state.executionId).hasSize(8)
        }

        @Test
        fun `state holder tracks elapsed duration`() {
            val match = match("m1")
            whenever(gateway.getLatestMatches(clubId)).thenAnswer {
                Thread.sleep(50)
                EaApiResult.Success(listOf(match))
            }

            service.acquire(AcquisitionTrigger.SCHEDULER)

            val state = stateHolder.current()
            assertThat(state.elapsedDuration()?.toMillis()).isGreaterThanOrEqualTo(50)
        }
    }

    // -------------------------------------------------------------------------
    // LatestMatchHolder Integration (Phase 4)
    // -------------------------------------------------------------------------

    @Nested
    inner class LatestMatchHolderIntegration {

        @Test
        fun `caches presentation after successful acquisition`() {
            val match = match("m1")
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(match)))

            service.acquire(AcquisitionTrigger.MANUAL)

            assertThat(latestMatchHolder.hasPresentation()).isTrue()
            assertThat(latestMatchHolder.presentation()?.matchId).isEqualTo("m1")
        }

        @Test
        fun `caches presentation BEFORE checking deduplication`() {
            val match = match("m1")
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(match)))
            whenever(store.loadIds()).thenReturn(setOf("m1")) // Already published

            service.acquire(AcquisitionTrigger.MANUAL)

            // Even though match was skipped (already published), presentation should be cached
            assertThat(latestMatchHolder.hasPresentation()).isTrue()
            assertThat(latestMatchHolder.presentation()?.matchId).isEqualTo("m1")
        }

        @Test
        fun `caches presentation even when Discord delivery fails`() {
            val match = match("m1")
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(match)))
            doThrow(DiscordDeliveryException("Rate limited")).whenever(webhookClient).send(any())

            service.acquire(AcquisitionTrigger.MANUAL)

            // Discord failed, but presentation should still be cached
            assertThat(latestMatchHolder.hasPresentation()).isTrue()
            assertThat(latestMatchHolder.presentation()?.matchId).isEqualTo("m1")
        }

        @Test
        fun `does not cache presentation when EA API fails`() {
            whenever(gateway.getLatestMatches(clubId))
                .thenReturn(EaApiResult.Unavailable(503, "Service unavailable"))

            service.acquire(AcquisitionTrigger.SCHEDULER)

            assertThat(latestMatchHolder.hasPresentation()).isFalse()
        }

        @Test
        fun `does not cache presentation when EA returns no matches`() {
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.NoMatches)

            service.acquire(AcquisitionTrigger.MANUAL)

            assertThat(latestMatchHolder.hasPresentation()).isFalse()
        }

        @Test
        fun `increments version on each acquisition`() {
            val m1 = match("m1")
            val m2 = match("m2")
            whenever(gateway.getLatestMatches(clubId))
                .thenReturn(EaApiResult.Success(listOf(m1)))
                .thenReturn(EaApiResult.Success(listOf(m2)))

            assertThat(latestMatchHolder.version()).isEqualTo(0)

            service.acquire(AcquisitionTrigger.MANUAL)
            assertThat(latestMatchHolder.version()).isEqualTo(1)

            service.acquire(AcquisitionTrigger.MANUAL)
            assertThat(latestMatchHolder.version()).isEqualTo(2)
        }

        @Test
        fun `caches latest match by timestamp for SCHEDULER trigger`() {
            val older = match("m1", ts = 1000)
            val newer = match("m2", ts = 2000)
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(older, newer)))
            whenever(store.loadIds()).thenReturn(setOf("existing"))

            service.acquire(AcquisitionTrigger.SCHEDULER)

            // Should cache the latest match (m2)
            assertThat(latestMatchHolder.presentation()?.matchId).isEqualTo("m2")
        }

        @Test
        fun `caches presentation on first run baseline establishment`() {
            service = makeService(publishExistingOnFirstRun = false)
            val match = match("m1")
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(match)))
            whenever(store.loadIds()).thenReturn(emptySet())

            service.acquire(AcquisitionTrigger.SCHEDULER)

            // Even when establishing baseline (no publish), presentation should be cached
            assertThat(latestMatchHolder.hasPresentation()).isTrue()
            assertThat(latestMatchHolder.presentation()?.matchId).isEqualTo("m1")
        }

        @Test
        fun `caches presentation on force resend`() {
            val match = match("m1")
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(match)))
            whenever(store.loadIds()).thenReturn(setOf("m1"))

            service.acquire(AcquisitionTrigger.FORCE_RESEND)

            assertThat(latestMatchHolder.hasPresentation()).isTrue()
            assertThat(latestMatchHolder.presentation()?.matchId).isEqualTo("m1")
        }

        @Test
        fun `presentation contains valid data`() {
            val match = match("m1", ourScore = "3", oppScore = "2")
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(match)))

            service.acquire(AcquisitionTrigger.CLI)

            val pres = latestMatchHolder.presentation()!!
            assertThat(pres.matchId).isEqualTo("m1")
            assertThat(pres.ourName).isEqualTo("Test FC")
            assertThat(pres.oppName).isEqualTo("Opponent FC")
            assertThat(pres.ourScore).isEqualTo(3)
            assertThat(pres.oppScore).isEqualTo(2)
        }
    }

    // -------------------------------------------------------------------------
    // Virtual Pro names (getMembersStats)
    // -------------------------------------------------------------------------

    @Nested
    inner class ProNamesIntegration {

        /** Build a match with one scorer so goals section is populated. */
        private fun matchWithScorer(id: String, playerName: String): MatchResponse = MatchResponse(
            matchId = id,
            timestamp = System.currentTimeMillis() / 1000,
            clubs = mapOf(
                clubId to ClubMatchEntry(
                    details = ClubDetails(name = "Test FC"),
                    score = "1",
                    result = "1",
                ),
                "opponent" to ClubMatchEntry(
                    details = ClubDetails(name = "Opponent FC"),
                    score = "0",
                    result = "0",
                ),
            ),
            players = mapOf(
                clubId to mapOf(
                    "p1" to PlayerEntry(
                        playerName = playerName,
                        position = "9",
                        goals = "1",
                        assists = "0",
                        rating = "8.0",
                        secondsPlayed = "5400",
                    )
                )
            ),
        )

        @Test
        fun `proName is used as display name in goals section when members stats succeed`() {
            val theMatch = matchWithScorer("m1", "dbeng_bass")
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(theMatch)))
            whenever(gateway.getMembersStats(clubId)).thenReturn(
                EaApiResult.Success(listOf(MemberStats(playerName = "dbeng_bass", proName = "R. Nazário")))
            )

            service.acquire(AcquisitionTrigger.CLI)

            val pres = latestMatchHolder.presentation()!!
            val scorerNames = pres.goals?.scorers?.map { it.name } ?: emptyList()
            assertThat(scorerNames).containsExactly("R. Nazário")
        }

        @Test
        fun `falls back to playerName when getMembersStats returns empty list`() {
            val theMatch = matchWithScorer("m2", "dbeng_bass")
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(theMatch)))
            whenever(gateway.getMembersStats(clubId)).thenReturn(EaApiResult.Success(emptyList()))

            service.acquire(AcquisitionTrigger.CLI)

            val pres = latestMatchHolder.presentation()!!
            val scorerNames = pres.goals?.scorers?.map { it.name } ?: emptyList()
            assertThat(scorerNames).containsExactly("dbeng_bass")
        }

        @Test
        fun `falls back to playerName when getMembersStats returns Unavailable`() {
            val theMatch = matchWithScorer("m3", "dbeng_bass")
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(theMatch)))
            whenever(gateway.getMembersStats(clubId)).thenReturn(EaApiResult.Unavailable(503, "Service unavailable"))

            service.acquire(AcquisitionTrigger.CLI)

            val pres = latestMatchHolder.presentation()!!
            val scorerNames = pres.goals?.scorers?.map { it.name } ?: emptyList()
            assertThat(scorerNames).containsExactly("dbeng_bass")
        }

        @Test
        fun `acquisition succeeds even when getMembersStats fails completely`() {
            val theMatch = matchWithScorer("m4", "dbeng_bass")
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(theMatch)))
            whenever(gateway.getMembersStats(clubId)).thenReturn(EaApiResult.Unavailable(0, "timeout"))

            val result = service.acquire(AcquisitionTrigger.MANUAL)

            assertThat(result).isInstanceOf(AcquisitionResult.Processed::class.java)
        }

        @Test
        fun `default gateway implementation returns empty list (no override required)`() {
            val gateway: EaClubsGateway = object : EaClubsGateway {
                override fun searchClubs(clubName: String) = EaApiResult.Success(emptyList<com.eafc26.discordstats.ea.model.ClubSearchResult>())
                override fun getLatestMatches(clubId: String) = EaApiResult.NoMatches
                // getMembersStats intentionally NOT overridden
            }
            val result = gateway.getMembersStats("any")
            assertThat(result).isInstanceOf(EaApiResult.Success::class.java)
            assertThat((result as EaApiResult.Success).data).isEmpty()
        }
    }
}







