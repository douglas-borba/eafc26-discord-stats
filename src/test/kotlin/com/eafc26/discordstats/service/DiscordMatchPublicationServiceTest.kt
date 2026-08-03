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
 * Covers all deduplication, concurrency, persistence, and error scenarios for
 * [DiscordMatchPublicationService].
 *
 * Tests use:
 * - A real [PublishedMatchStore] backed by a [TempDir] for persistence scenarios.
 * - A mocked [DiscordWebhookClient] to assert zero or one HTTP calls.
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
        val om = ObjectMapper().registerModule(KotlinModule.Builder().build())
        store = PublishedMatchStore(om)
        val matchSummaryBuilder = MatchSummaryBuilder(PhraseBank(jacksonObjectMapper()))
        service = DiscordMatchPublicationService(store, webhookClient, DiscordRenderer(matchSummaryBuilder))
    }

    @AfterEach
    fun tearDown() {
        if (originalUserHome != null) System.setProperty("user.home", originalUserHome!!)
    }

    // -------------------------------------------------------------------------
    // Helper builders
    // -------------------------------------------------------------------------

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
    // publishIfNeeded — deduplication
    // =========================================================================

    @Nested
    inner class PublishIfNeeded {

        @Test
        fun `published match is skipped - zero HTTP calls`() {
            store.saveIds(setOf("m1"))

            val result = service.publishIfNeeded(canonical("m1"))

            assertThat(result.outcome).isEqualTo(PublicationOutcome.SKIPPED_ALREADY_PUBLISHED)
            assertThat(result.matchId).isEqualTo("m1")
            verify(webhookClient, never()).send(any())
        }

        @Test
        fun `new match is published and persisted`() {
            store.saveIds(setOf("old"))

            val result = service.publishIfNeeded(canonical("new"))

            assertThat(result.outcome).isEqualTo(PublicationOutcome.PUBLISHED)
            assertThat(result.persistedSuccessfully).isTrue()
            verify(webhookClient).send(any())
            assertThat(store.loadIds()).contains("new")
        }

        @Test
        fun `empty store publishes the match`() {
            // No prior saves — store file does not exist

            val result = service.publishIfNeeded(canonical("m1"))

            assertThat(result.outcome).isEqualTo(PublicationOutcome.PUBLISHED)
            verify(webhookClient).send(any())
            assertThat(store.loadIds()).containsExactly("m1")
        }

        @Test
        fun `match id normalization is stable - same id checked twice is skipped`() {
            service.publishIfNeeded(canonical("abc-123"))
            val second = service.publishIfNeeded(canonical("abc-123"))

            assertThat(second.outcome).isEqualTo(PublicationOutcome.SKIPPED_ALREADY_PUBLISHED)
            verify(webhookClient, times(1)).send(any())
        }
    }

    // =========================================================================
    // publishIfNeeded — store persistence survives restart
    // =========================================================================

    @Nested
    inner class PersistenceAfterRestart {

        @Test
        fun `published match is skipped after store is reloaded (restart simulation)`() {
            service.publishIfNeeded(canonical("m1"))
            verify(webhookClient, times(1)).send(any())

            // Simulate restart: create a fresh service reading from the same temp dir
            val restartedStore = PublishedMatchStore(ObjectMapper().registerModule(KotlinModule.Builder().build()))
            val restartedService = DiscordMatchPublicationService(
                restartedStore, webhookClient,
                DiscordRenderer(MatchSummaryBuilder(PhraseBank(jacksonObjectMapper())))
            )

            val result = restartedService.publishIfNeeded(canonical("m1"))

            assertThat(result.outcome).isEqualTo(PublicationOutcome.SKIPPED_ALREADY_PUBLISHED)
            // Total invocations: still 1 — no second HTTP call
            verify(webhookClient, times(1)).send(any())
        }

        @Test
        fun `published match is skipped by scheduler after restart`() {
            store.saveIds(setOf("m1"))

            val result = service.publishIfNeeded(canonical("m1"))

            assertThat(result.outcome).isEqualTo(PublicationOutcome.SKIPPED_ALREADY_PUBLISHED)
            verify(webhookClient, never()).send(any())
        }

        @Test
        fun `store persisted correctly - ids survive stop-start`() {
            service.publishIfNeeded(canonical("m1"))
            service.publishIfNeeded(canonical("m2"))

            val reloaded = PublishedMatchStore(ObjectMapper().registerModule(KotlinModule.Builder().build()))
            assertThat(reloaded.loadIds()).containsExactlyInAnyOrder("m1", "m2")
        }
    }

    // =========================================================================
    // publishIfNeeded — Discord error handling
    // =========================================================================

    @Nested
    inner class DiscordErrors {

        @Test
        fun `failed send does not mark match as published`() {
            doThrow(DiscordDeliveryException("rate limited")).whenever(webhookClient).send(any())

            val result = service.publishIfNeeded(canonical("m1"))

            assertThat(result.outcome).isEqualTo(PublicationOutcome.FAILED_WEBHOOK)
            assertThat(store.loadIds()).isEmpty()
        }

        @Test
        fun `webhook not configured does not mark match as published`() {
            doThrow(IllegalStateException("no webhook url")).whenever(webhookClient).send(any())

            val result = service.publishIfNeeded(canonical("m1"))

            assertThat(result.outcome).isEqualTo(PublicationOutcome.FAILED_NOT_CONFIGURED)
            assertThat(store.loadIds()).isEmpty()
        }

        @Test
        fun `successful send marks match as published`() {
            val result = service.publishIfNeeded(canonical("m1"))

            assertThat(result.outcome).isEqualTo(PublicationOutcome.PUBLISHED)
            assertThat(store.loadIds()).contains("m1")
        }

        @Test
        fun `failed delivery allows retry on next call`() {
            doThrow(DiscordDeliveryException("transient")).whenever(webhookClient).send(any())
            service.publishIfNeeded(canonical("m1")) // first attempt fails

            // Fix the webhook mock
            whenever(webhookClient.send(any())).then { /* no-op, success */ }
            val retry = service.publishIfNeeded(canonical("m1"))

            assertThat(retry.outcome).isEqualTo(PublicationOutcome.PUBLISHED)
            verify(webhookClient, times(2)).send(any())
        }
    }

    // =========================================================================
    // publishIfNeeded — malformed store
    // =========================================================================

    @Nested
    inner class MalformedStore {

        @Test
        fun `malformed store file propagates error - does not silently publish`() {
            val storeFile = AppDataPaths.storeFile
            storeFile.parent.toFile().mkdirs()
            storeFile.toFile().writeText("{corrupted json!}")

            // Should throw, not silently treat as empty and publish
            val thrown = runCatching { service.publishIfNeeded(canonical("m1")) }
            assertThat(thrown.isFailure).isTrue()
            verify(webhookClient, never()).send(any())
        }
    }

    // =========================================================================
    // publishIfNeeded — concurrency (TOCTOU)
    // =========================================================================

    @Nested
    inner class Concurrency {

        @Test
        fun `concurrent publication of same match sends only one HTTP request`() {
            val executor = Executors.newFixedThreadPool(10)
            val sendCount = AtomicInteger(0)
            val allReady = CountDownLatch(10)
            val startGun = CountDownLatch(1)
            val allDone = CountDownLatch(10)

            whenever(webhookClient.send(any())).thenAnswer {
                sendCount.incrementAndGet()
                Unit
            }

            repeat(10) {
                executor.submit {
                    allReady.countDown()
                    startGun.await(5, TimeUnit.SECONDS)
                    service.publishIfNeeded(canonical("race-match"))
                    allDone.countDown()
                }
            }

            allReady.await(2, TimeUnit.SECONDS)
            startGun.countDown()
            allDone.await(10, TimeUnit.SECONDS)

            assertThat(sendCount.get()).isEqualTo(1)
            assertThat(store.loadIds()).containsExactly("race-match")

            executor.shutdown()
        }

        @Test
        fun `concurrent publication - both threads return consistent result`() {
            val executor = Executors.newFixedThreadPool(2)
            val results = mutableListOf<PublicationOutcome>()

            val t1 = executor.submit<PublicationOutcome> { service.publishIfNeeded(canonical("m1")).outcome }
            val t2 = executor.submit<PublicationOutcome> { service.publishIfNeeded(canonical("m1")).outcome }

            val r1 = t1.get(5, TimeUnit.SECONDS)
            val r2 = t2.get(5, TimeUnit.SECONDS)
            results.addAll(listOf(r1, r2))

            // Exactly one PUBLISHED and one SKIPPED_ALREADY_PUBLISHED
            assertThat(results).containsExactlyInAnyOrder(
                PublicationOutcome.PUBLISHED,
                PublicationOutcome.SKIPPED_ALREADY_PUBLISHED,
            )
            // Store contains the match ID exactly once
            assertThat(store.loadIds()).containsExactly("m1")
            verify(webhookClient, times(1)).send(any())

            executor.shutdown()
        }
    }

    // =========================================================================
    // forcePublish
    // =========================================================================

    @Nested
    inner class ForcePublish {

        @Test
        fun `force publish sends even if already published`() {
            store.saveIds(setOf("m1"))

            val result = service.forcePublish(canonical("m1"))

            assertThat(result.outcome).isEqualTo(PublicationOutcome.PUBLISHED)
            verify(webhookClient).send(any())
        }

        @Test
        fun `force publish marks match as published in store`() {
            // Store is empty - force publish should add the match
            service.forcePublish(canonical("m1"))

            assertThat(store.loadIds()).contains("m1")
        }

        @Test
        fun `force publish on already-published match does not duplicate in store`() {
            store.saveIds(setOf("m1"))

            service.forcePublish(canonical("m1"))

            // m1 appears exactly once
            assertThat(store.loadIds()).containsExactly("m1")
        }

        @Test
        fun `scheduler does not re-publish after force publish`() {
            service.forcePublish(canonical("m1"))
            // Simulate scheduler cycle: m1 is now in store
            val schedulerResult = service.publishIfNeeded(canonical("m1"))

            assertThat(schedulerResult.outcome).isEqualTo(PublicationOutcome.SKIPPED_ALREADY_PUBLISHED)
            // Only 1 HTTP call total (from forcePublish)
            verify(webhookClient, times(1)).send(any())
        }

        @Test
        fun `force publish failure does not mark match as published`() {
            doThrow(DiscordDeliveryException("down")).whenever(webhookClient).send(any())

            val result = service.forcePublish(canonical("m1"))

            assertThat(result.outcome).isEqualTo(PublicationOutcome.FAILED_WEBHOOK)
            assertThat(store.loadIds()).isEmpty()
        }
    }

    // =========================================================================
    // Missing store (fresh installation)
    // =========================================================================

    @Nested
    inner class FreshInstallation {

        @Test
        fun `missing store file treated as empty - publish succeeds`() {
            // No store file created — first installation scenario

            val result = service.publishIfNeeded(canonical("m1"))

            assertThat(result.outcome).isEqualTo(PublicationOutcome.PUBLISHED)
            verify(webhookClient).send(any())
        }

        @Test
        fun `second call after first publish is skipped`() {
            service.publishIfNeeded(canonical("m1"))
            val second = service.publishIfNeeded(canonical("m1"))

            assertThat(second.outcome).isEqualTo(PublicationOutcome.SKIPPED_ALREADY_PUBLISHED)
            verify(webhookClient, times(1)).send(any())
        }
    }

    // =========================================================================
    // Persistence failure (disk full, I/O error)
    // =========================================================================

    @Nested
    inner class PersistenceFailure {

        @Test
        fun `delivery succeeds but persistence failure is reported`() {
            val failingStore: PublishedMatchStore = mock()
            whenever(failingStore.loadIds()).thenReturn(emptySet())
            doThrow(RuntimeException("disk full")).whenever(failingStore).saveIds(any())
            val svc = DiscordMatchPublicationService(
                failingStore, webhookClient,
                DiscordRenderer(MatchSummaryBuilder(PhraseBank(jacksonObjectMapper())))
            )

            val result = svc.publishIfNeeded(canonical("m1"))

            assertThat(result.outcome).isEqualTo(PublicationOutcome.PUBLISHED)
            assertThat(result.persistedSuccessfully).isFalse()
            verify(webhookClient).send(any())
        }
    }
}


