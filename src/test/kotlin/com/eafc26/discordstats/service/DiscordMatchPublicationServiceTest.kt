package com.eafc26.discordstats.service

import com.eafc26.discordstats.config.AppDataPaths
import com.eafc26.discordstats.config.PhraseBank
import com.eafc26.discordstats.discord.DiscordDeliveryException
import com.eafc26.discordstats.discord.DiscordRenderer
import com.eafc26.discordstats.discord.DiscordWebhookClient
import com.eafc26.discordstats.ea.model.ClubDetails
import com.eafc26.discordstats.ea.model.ClubMatchEntry
import com.eafc26.discordstats.ea.model.MatchResponse
import com.eafc26.discordstats.presentation.MatchSummaryBuilder
import com.eafc26.discordstats.store.PublicationRecord
import com.eafc26.discordstats.store.PublicationState
import com.eafc26.discordstats.store.PublishedMatchStore
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Comprehensive tests for [DiscordMatchPublicationService] WAL semantics.
 *
 * Uses a real [PublishedMatchStore] backed by a [TempDir] to exercise
 * actual persistence, migration, and restart behavior.
 */
class DiscordMatchPublicationServiceTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var webhookClient: DiscordWebhookClient
    private lateinit var store: PublishedMatchStore
    private lateinit var service: DiscordMatchPublicationService
    private var originalUserHome: String? = null

    private val clubId = "42"

    @BeforeEach
    fun setUp() {
        originalUserHome = System.getProperty("user.home")
        System.setProperty("user.home", tempDir.toString())
        webhookClient = mock()
        store = makeStore()
        service = makeService(store)
    }

    @AfterEach
    fun tearDown() {
        if (originalUserHome != null) System.setProperty("user.home", originalUserHome!!)
    }

    private fun makeStore() = PublishedMatchStore(ObjectMapper().registerModule(KotlinModule.Builder().build()))
    private fun makeService(s: PublishedMatchStore) = DiscordMatchPublicationService(
        s, webhookClient, DiscordRenderer(MatchSummaryBuilder(PhraseBank(jacksonObjectMapper())))
    )

    private fun canonical(id: String, ourScore: String = "2", oppScore: String = "1") =
        CanonicalMatchFactory().create(
            source = MatchResponse(
                matchId = id,
                timestamp = System.currentTimeMillis() / 1000,
                clubs = mapOf(
                    clubId to ClubMatchEntry(details = ClubDetails(name = "Test FC"), score = ourScore, result = "1"),
                    "opp" to ClubMatchEntry(details = ClubDetails(name = "Rival FC"), score = oppScore, result = "0"),
                ),
                players = emptyMap(),
            ),
            perspectiveClubId = clubId,
        )

    // =========================================================================
    // WAL pre-send write
    // =========================================================================

    @Nested
    inner class WalPreSendWrite {

        @Test
        fun `DELIVERING is written to store before HTTP call`() {
            // Intercept: capture state at the moment HTTP is called
            var stateAtHttpCall: PublicationState? = null
            whenever(webhookClient.send(any())).thenAnswer {
                stateAtHttpCall = store.loadRecords()["m1"]?.state
                Unit
            }

            service.publishIfNeeded(canonical("m1"))

            assertThat(stateAtHttpCall).isEqualTo(PublicationState.DELIVERING)
        }

        @Test
        fun `DELIVERED is written after HTTP 2xx`() {
            service.publishIfNeeded(canonical("m1"))

            assertThat(store.loadRecords()["m1"]?.state).isEqualTo(PublicationState.DELIVERED)
        }

        @Test
        fun `failure to persist DELIVERING → zero HTTP calls (FAILED_BEFORE_SEND)`() {
            val failingStore: PublishedMatchStore = mock()
            whenever(failingStore.loadRecords()).thenReturn(emptyMap())
            doThrow(RuntimeException("disk full")).whenever(failingStore).saveRecord(any())
            val svc = makeService(failingStore)

            val result = svc.publishIfNeeded(canonical("m1"))

            assertThat(result.outcome).isEqualTo(PublicationOutcome.FAILED_BEFORE_SEND)
            verify(webhookClient, never()).send(any())
        }

        @Test
        fun `DELIVERING marker removed when HTTP call fails (safe to retry)`() {
            doThrow(DiscordDeliveryException("rate limited")).whenever(webhookClient).send(any())

            service.publishIfNeeded(canonical("m1"))

            // DELIVERING marker removed → match not in store → retry allowed
            assertThat(store.loadRecords()).doesNotContainKey("m1")
        }

        @Test
        fun `DELIVERING marker removed when webhook not configured`() {
            doThrow(IllegalStateException("no url")).whenever(webhookClient).send(any())

            service.publishIfNeeded(canonical("m1"))

            assertThat(store.loadRecords()).doesNotContainKey("m1")
        }
    }

    // =========================================================================
    // Crash simulation: DELIVERING → DELIVERY_UNCERTAIN on restart
    // =========================================================================

    @Nested
    inner class CrashSimulation {

        /**
         * Simulates: process wrote DELIVERING, then died before HTTP call.
         * On restart, DELIVERING → DELIVERY_UNCERTAIN. No auto-resend.
         */
        @Test
        fun `DELIVERING left in store after crash → DELIVERY_UNCERTAIN after restart → zero resends`() {
            // Simulate crash: write DELIVERING manually (as if pre-send write completed but process died)
            store.saveRecord(PublicationRecord("m1", PublicationState.DELIVERING))

            // Simulate restart: new store instance reads the file
            val restartedStore = makeStore()
            assertThat(restartedStore.loadRecords()["m1"]?.state)
                .isEqualTo(PublicationState.DELIVERY_UNCERTAIN)

            // New service on restarted store
            val restartedService = makeService(restartedStore)
            val result = restartedService.publishIfNeeded(canonical("m1"))

            assertThat(result.outcome).isEqualTo(PublicationOutcome.SKIPPED_DELIVERY_UNCERTAIN)
            verify(webhookClient, never()).send(any())
        }

        /**
         * Simulates: process sent HTTP successfully, then died before writing DELIVERED.
         * The DELIVERING marker remains on disk → DELIVERY_UNCERTAIN after restart → no auto-resend.
         */
        @Test
        fun `crash after HTTP success but before DELIVERED write → DELIVERY_UNCERTAIN → no resend`() {
            // Simulate: DELIVERING is in store, HTTP was sent (message delivered to Discord),
            // but process died before saving DELIVERED.
            store.saveRecord(PublicationRecord("m1", PublicationState.DELIVERING))

            // Restart: DELIVERING → DELIVERY_UNCERTAIN
            val restartedStore = makeStore()
            val restartedService = makeService(restartedStore)

            val result = restartedService.publishIfNeeded(canonical("m1"))

            assertThat(result.outcome).isEqualTo(PublicationOutcome.SKIPPED_DELIVERY_UNCERTAIN)
            verify(webhookClient, never()).send(any())
        }

        @Test
        fun `DELIVERY_UNCERTAIN blocks scheduler (zero HTTP calls)`() {
            store.saveRecord(PublicationRecord("m1", PublicationState.DELIVERY_UNCERTAIN))

            val result = service.publishIfNeeded(canonical("m1"))

            assertThat(result.outcome).isEqualTo(PublicationOutcome.SKIPPED_DELIVERY_UNCERTAIN)
            verify(webhookClient, never()).send(any())
        }

        @Test
        fun `DELIVERY_UNCERTAIN blocks notify-latest (zero HTTP calls)`() {
            store.saveRecord(PublicationRecord("m1", PublicationState.DELIVERY_UNCERTAIN))

            val result = service.publishIfNeeded(canonical("m1"))

            assertThat(result.outcome).isEqualTo(PublicationOutcome.SKIPPED_DELIVERY_UNCERTAIN)
            verify(webhookClient, never()).send(any())
        }
    }

    // =========================================================================
    // Administrative resolution
    // =========================================================================

    @Nested
    inner class AdministrativeResolution {

        @Test
        fun `resolveAsDelivered allows scheduler to skip without HTTP call`() {
            store.saveRecord(PublicationRecord("m1", PublicationState.DELIVERY_UNCERTAIN))
            store.resolveAsDelivered("m1")

            val result = service.publishIfNeeded(canonical("m1"))

            assertThat(result.outcome).isEqualTo(PublicationOutcome.SKIPPED_ALREADY_DELIVERED)
            verify(webhookClient, never()).send(any())
        }

        @Test
        fun `resolveAsUndelivered removes record allowing resend`() {
            store.saveRecord(PublicationRecord("m1", PublicationState.DELIVERY_UNCERTAIN))
            store.resolveAsUndelivered("m1")

            val result = service.publishIfNeeded(canonical("m1"))

            assertThat(result.outcome).isEqualTo(PublicationOutcome.PUBLISHED)
            verify(webhookClient).send(any())
        }

        @Test
        fun `forcePublish sends even if DELIVERY_UNCERTAIN (explicit admin action)`() {
            store.saveRecord(PublicationRecord("m1", PublicationState.DELIVERY_UNCERTAIN))

            val result = service.forcePublish(canonical("m1"))

            assertThat(result.outcome).isEqualTo(PublicationOutcome.PUBLISHED)
            verify(webhookClient).send(any())
            assertThat(store.loadRecords()["m1"]?.state).isEqualTo(PublicationState.DELIVERED)
        }
    }

    // =========================================================================
    // Normal deduplication
    // =========================================================================

    @Nested
    inner class Deduplication {

        @Test
        fun `DELIVERED match is skipped with zero HTTP calls`() {
            store.saveRecord(PublicationRecord("m1", PublicationState.DELIVERED))

            val result = service.publishIfNeeded(canonical("m1"))

            assertThat(result.outcome).isEqualTo(PublicationOutcome.SKIPPED_ALREADY_DELIVERED)
            verify(webhookClient, never()).send(any())
        }

        @Test
        fun `published match is skipped after store reload (restart simulation)`() {
            service.publishIfNeeded(canonical("m1"))
            verify(webhookClient, times(1)).send(any())

            val restartedStore = makeStore()
            val restartedService = makeService(restartedStore)

            val result = restartedService.publishIfNeeded(canonical("m1"))

            assertThat(result.outcome).isEqualTo(PublicationOutcome.SKIPPED_ALREADY_DELIVERED)
            verify(webhookClient, times(1)).send(any()) // still 1, no extra call
        }

        @Test
        fun `second call same session is skipped`() {
            service.publishIfNeeded(canonical("m1"))
            val second = service.publishIfNeeded(canonical("m1"))

            assertThat(second.outcome).isEqualTo(PublicationOutcome.SKIPPED_ALREADY_DELIVERED)
            verify(webhookClient, times(1)).send(any())
        }
    }

    // =========================================================================
    // Store migration: v1 → v2
    // =========================================================================

    @Nested
    inner class StoreMigration {

        @Test
        fun `v1 string-array format is migrated to DELIVERED records`() {
            val storeFile = AppDataPaths.storeFile
            storeFile.parent.toFile().mkdirs()
            storeFile.toFile().writeText("""["id1","id2","id3"]""")

            val migratedStore = makeStore()
            val records = migratedStore.loadRecords()

            assertThat(records).hasSize(3)
            assertThat(records.values.map { it.state }.distinct()).containsExactly(PublicationState.DELIVERED)
            assertThat(records.keys).containsExactlyInAnyOrder("id1", "id2", "id3")
        }

        @Test
        fun `migration creates v1 backup file`() {
            val storeFile = AppDataPaths.storeFile
            storeFile.parent.toFile().mkdirs()
            storeFile.toFile().writeText("""["old-id"]""")

            makeStore() // triggers migration

            val backup = storeFile.resolveSibling("published-matches.json.v1.bak")
            assertThat(backup).exists()
            assertThat(backup.toFile().readText()).contains("old-id")
        }

        @Test
        fun `v1 IDs are considered DELIVERED - scheduler does not republish`() {
            val storeFile = AppDataPaths.storeFile
            storeFile.parent.toFile().mkdirs()
            storeFile.toFile().writeText("""["m1","m2"]""")

            val migratedStore = makeStore()
            val migratedService = makeService(migratedStore)

            val r1 = migratedService.publishIfNeeded(canonical("m1"))
            val r2 = migratedService.publishIfNeeded(canonical("m2"))

            assertThat(r1.outcome).isEqualTo(PublicationOutcome.SKIPPED_ALREADY_DELIVERED)
            assertThat(r2.outcome).isEqualTo(PublicationOutcome.SKIPPED_ALREADY_DELIVERED)
            verify(webhookClient, never()).send(any())
        }

        @Test
        fun `v2 format is read correctly`() {
            val storeFile = AppDataPaths.storeFile
            storeFile.parent.toFile().mkdirs()
            storeFile.toFile().writeText(
                """[{"matchId":"m1","state":"DELIVERED","updatedAt":1722700000}]"""
            )

            val s = makeStore()
            assertThat(s.loadRecords()["m1"]?.state).isEqualTo(PublicationState.DELIVERED)
        }
    }

    // =========================================================================
    // Malformed store
    // =========================================================================

    @Nested
    inner class MalformedStore {

        @Test
        fun `malformed store does not silently publish - throws exception`() {
            val storeFile = AppDataPaths.storeFile
            storeFile.parent.toFile().mkdirs()
            storeFile.toFile().writeText("{corrupted!}")

            val thrown = runCatching { service.publishIfNeeded(canonical("m1")) }
            assertThat(thrown.isFailure).isTrue()
            verify(webhookClient, never()).send(any())
        }
    }

    // =========================================================================
    // Concurrency
    // =========================================================================

    @Nested
    inner class Concurrency {

        @Test
        fun `concurrent publication sends exactly one HTTP request (TOCTOU safe)`() {
            val executor = Executors.newFixedThreadPool(10)
            val sendCount = AtomicInteger(0)
            val allReady = CountDownLatch(10)
            val startGun = CountDownLatch(1)
            val allDone = CountDownLatch(10)

            whenever(webhookClient.send(any())).thenAnswer { sendCount.incrementAndGet(); Unit }

            repeat(10) {
                executor.submit {
                    allReady.countDown()
                    startGun.await(5, TimeUnit.SECONDS)
                    service.publishIfNeeded(canonical("race"))
                    allDone.countDown()
                }
            }

            allReady.await(2, TimeUnit.SECONDS)
            startGun.countDown()
            allDone.await(10, TimeUnit.SECONDS)
            executor.shutdown()

            assertThat(sendCount.get()).isEqualTo(1)
            assertThat(store.loadRecords()["race"]?.state).isEqualTo(PublicationState.DELIVERED)
        }
    }

    // =========================================================================
    // forcePublish
    // =========================================================================

    @Nested
    inner class ForcePublish {

        @Test
        fun `forcePublish sends even if already DELIVERED`() {
            store.saveRecord(PublicationRecord("m1", PublicationState.DELIVERED))

            val result = service.forcePublish(canonical("m1"))

            assertThat(result.outcome).isEqualTo(PublicationOutcome.PUBLISHED)
            verify(webhookClient).send(any())
        }

        @Test
        fun `forcePublish persists DELIVERED after success`() {
            service.forcePublish(canonical("m1"))

            assertThat(store.loadRecords()["m1"]?.state).isEqualTo(PublicationState.DELIVERED)
        }

        @Test
        fun `forcePublish WAL - DELIVERING written before HTTP`() {
            var stateAtHttp: PublicationState? = null
            whenever(webhookClient.send(any())).thenAnswer {
                stateAtHttp = store.loadRecords()["m1"]?.state
                Unit
            }

            service.forcePublish(canonical("m1"))

            assertThat(stateAtHttp).isEqualTo(PublicationState.DELIVERING)
        }

        @Test
        fun `forcePublish failure cleans DELIVERING marker`() {
            doThrow(DiscordDeliveryException("down")).whenever(webhookClient).send(any())

            service.forcePublish(canonical("m1"))

            assertThat(store.loadRecords()).doesNotContainKey("m1")
        }

        @Test
        fun `scheduler does not re-publish after forcePublish`() {
            service.forcePublish(canonical("m1"))

            val schedulerResult = service.publishIfNeeded(canonical("m1"))

            assertThat(schedulerResult.outcome).isEqualTo(PublicationOutcome.SKIPPED_ALREADY_DELIVERED)
            verify(webhookClient, times(1)).send(any())
        }
    }

    // =========================================================================
    // DELIVERED_BUT_STATE_UNCERTAIN (HTTP success + DELIVERED write failure)
    // =========================================================================

    @Nested
    inner class DeliveredButUncertain {

        @Test
        fun `HTTP success + DELIVERED write failure → DELIVERED_BUT_STATE_UNCERTAIN`() {
            val failingStore: PublishedMatchStore = mock()
            whenever(failingStore.loadRecords()).thenReturn(emptyMap())
            // Allow DELIVERING write, fail only DELIVERED write
            whenever(failingStore.saveRecord(argThat { state == PublicationState.DELIVERING })).then { }
            doThrow(RuntimeException("disk full")).whenever(failingStore)
                .saveRecord(argThat { state == PublicationState.DELIVERED })
            val svc = makeService(failingStore)

            val result = svc.publishIfNeeded(canonical("m1"))

            assertThat(result.outcome).isEqualTo(PublicationOutcome.DELIVERED_BUT_STATE_UNCERTAIN)
            assertThat(result.delivered).isTrue()
            verify(webhookClient).send(any())
        }
    }

    // =========================================================================
    // Fresh installation
    // =========================================================================

    @Nested
    inner class FreshInstallation {

        @Test
        fun `missing store file is treated as empty - publish succeeds`() {
            val result = service.publishIfNeeded(canonical("m1"))

            assertThat(result.outcome).isEqualTo(PublicationOutcome.PUBLISHED)
            verify(webhookClient).send(any())
        }
    }

    // =========================================================================
    // Atomic write
    // =========================================================================

    @Nested
    inner class AtomicWrite {

        @Test
        fun `no tmp file left after save`() {
            service.publishIfNeeded(canonical("m1"))

            val storeFile = AppDataPaths.storeFile
            val tmpFiles = storeFile.parent.toFile().listFiles { f -> f.name.endsWith(".tmp") }
            assertThat(tmpFiles ?: emptyArray()).isEmpty()
        }
    }
}
