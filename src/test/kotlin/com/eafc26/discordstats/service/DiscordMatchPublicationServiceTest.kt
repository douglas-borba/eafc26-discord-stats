package com.eafc26.discordstats.service

import com.eafc26.discordstats.config.AppDataPaths
import com.eafc26.discordstats.config.PhraseBank
import com.eafc26.discordstats.discord.DiscordDeliveryException
import com.eafc26.discordstats.discord.DiscordDestination
import com.eafc26.discordstats.discord.DiscordDestinationResolver
import com.eafc26.discordstats.discord.DiscordPayload
import com.eafc26.discordstats.discord.DiscordRenderer
import com.eafc26.discordstats.discord.DiscordWebhookClient
import com.eafc26.discordstats.llm.LlmEditorialService
import com.eafc26.discordstats.llm.LlmProperties
import com.eafc26.discordstats.llm.EditorialContextBuilder
import com.eafc26.discordstats.ea.model.ClubDetails
import com.eafc26.discordstats.ea.model.ClubMatchEntry
import com.eafc26.discordstats.ea.model.MatchResponse
import com.eafc26.discordstats.presentation.MatchSummaryBuilder
import com.eafc26.discordstats.store.PublicationRecord
import com.eafc26.discordstats.store.PublicationRetryPolicy
import com.eafc26.discordstats.store.PublicationState
import com.eafc26.discordstats.store.PublicationStateStore
import com.eafc26.discordstats.store.PublishedMatchStore
import com.eafc26.discordstats.store.EventStatus
import com.eafc26.discordstats.store.OperationalEvent
import com.eafc26.discordstats.store.OperationalEventRepository
import com.eafc26.discordstats.domain.match.ClubId
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
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.net.SocketTimeoutException
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
    private val CLUB_ID = ClubId(clubId)

    private fun PublishedMatchStore.loadRecords() = loadRecords(CLUB_ID)
    private fun PublishedMatchStore.loadIds() = loadIds(CLUB_ID)
    private fun PublishedMatchStore.saveRecord(record: PublicationRecord) = saveRecord(CLUB_ID, record)
    private fun PublishedMatchStore.saveIds(ids: Set<String>) = saveIds(CLUB_ID, ids)
    private fun PublishedMatchStore.removeRecord(matchId: String) = removeRecord(CLUB_ID, matchId)
    private fun PublishedMatchStore.resolveAsDelivered(matchId: String) = resolveAsDelivered(CLUB_ID, matchId)
    private fun PublishedMatchStore.resolveAsUndelivered(matchId: String) = resolveAsUndelivered(CLUB_ID, matchId)

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
    private fun makeService(
        s: PublicationStateStore,
        eventRecorder: OperationalEventRecorder? = null,
    ) = DiscordMatchPublicationService(
        s, webhookClient, DiscordRenderer(MatchSummaryBuilder(PhraseBank(jacksonObjectMapper()))),
        LlmEditorialService(
            com.eafc26.discordstats.llm.EditorialContextBuilder(),
            null,
            mock(),
            LlmProperties(enabled = false),
        ),
        DiscordDestinationResolver { DiscordDestination("https://discord.test/default") },
        eventRecorder = eventRecorder,
    )

    private fun canonical(id: String, ourScore: String = "2", oppScore: String = "1") =
        canonicalFor(CLUB_ID, id, ourScore, oppScore)

    private fun opponentDnfCanonical(id: String) = CanonicalMatchFactory().create(
        source = MatchResponse(
            matchId = id,
            timestamp = System.currentTimeMillis() / 1000,
            clubs = mapOf(
                CLUB_ID.value to ClubMatchEntry(
                    details = ClubDetails(name = "Test FC"), score = "3", result = "1", winnerByDnf = "1",
                ),
                "opp" to ClubMatchEntry(
                    details = ClubDetails(name = "Rival FC"), score = "0", result = "0", winnerByDnf = "0",
                ),
            ),
            players = mapOf(
                CLUB_ID.value to mapOf(
                    "scorer" to com.eafc26.discordstats.ea.model.PlayerEntry(
                        playerName = "Scorer",
                        position = "14",
                        rating = "8.5",
                        goals = "2",
                        assists = "1",
                        passesMade = "24",
                        passAttempts = "28",
                    ),
                    "support" to com.eafc26.discordstats.ea.model.PlayerEntry(
                        playerName = "Support",
                        position = "14",
                        rating = "7.8",
                        goals = "1",
                    ),
                ),
            ),
        ),
        perspectiveClubId = CLUB_ID.value,
    )

    private fun canonicalFor(perspective: ClubId, id: String, ourScore: String = "2", oppScore: String = "1") =
        CanonicalMatchFactory().create(
            source = MatchResponse(
                matchId = id,
                timestamp = System.currentTimeMillis() / 1000,
                clubs = mapOf(
                    perspective.value to ClubMatchEntry(details = ClubDetails(name = "Test FC"), score = ourScore, result = "1"),
                    "opp" to ClubMatchEntry(details = ClubDetails(name = "Rival FC"), score = oppScore, result = "0"),
                ),
                players = emptyMap(),
            ),
            perspectiveClubId = perspective.value,
        )

    private fun serviceWith(
        resolver: DiscordDestinationResolver,
        eventRecorder: OperationalEventRecorder? = null,
    ) = DiscordMatchPublicationService(
        store,
        webhookClient,
        DiscordRenderer(MatchSummaryBuilder(PhraseBank(jacksonObjectMapper()))),
        LlmEditorialService(EditorialContextBuilder(), null, mock(), LlmProperties(enabled = false)),
        resolver,
        eventRecorder = eventRecorder,
    )

    private class DeliveredWriteFailingStore(
        private val delegate: PublicationStateStore,
    ) : PublicationStateStore by delegate {
        override fun saveRecord(clubId: ClubId, record: PublicationRecord) {
            if (record.state == PublicationState.DELIVERED) {
                throw IllegalStateException("state storage unavailable")
            }
            delegate.saveRecord(clubId, record)
        }
    }

    @Nested
    inner class ClubIsolation {
        private val clubA = ClubId("club-a")
        private val clubB = ClubId("club-b")
        private val destinationA = DiscordDestination("https://discord.test/a")
        private val destinationB = DiscordDestination("https://discord.test/b")

        @Test
        fun `DELIVERED for club A never blocks same match id for club B and each uses its destination`() {
            val scoped = serviceWith(DiscordDestinationResolver { club ->
                when (club) {
                    clubA -> destinationA
                    clubB -> destinationB
                    else -> null
                }
            })

            assertThat(scoped.publishIfNeeded(canonicalFor(clubA, "same")).outcome)
                .isEqualTo(PublicationOutcome.PUBLISHED)
            assertThat(scoped.publishIfNeeded(canonicalFor(clubB, "same")).outcome)
                .isEqualTo(PublicationOutcome.PUBLISHED)

            assertThat(store.find(clubA, "same")?.state).isEqualTo(PublicationState.DELIVERED)
            assertThat(store.find(clubB, "same")?.state).isEqualTo(PublicationState.DELIVERED)
            val destinations = argumentCaptor<DiscordDestination>()
            verify(webhookClient, times(2)).send(destinations.capture(), any())
            assertThat(destinations.allValues).containsExactly(destinationA, destinationB)
        }

        @Test
        fun `FAILED_PERMANENT for club A does not affect club B`() {
            store.saveRecord(clubA, PublicationRecord("same", PublicationState.FAILED_PERMANENT))
            val scoped = serviceWith(DiscordDestinationResolver { destinationB })

            val result = scoped.publishIfNeeded(canonicalFor(clubB, "same"))

            assertThat(result.outcome).isEqualTo(PublicationOutcome.PUBLISHED)
            assertThat(store.find(clubA, "same")?.state).isEqualTo(PublicationState.FAILED_PERMANENT)
            assertThat(store.find(clubB, "same")?.state).isEqualTo(PublicationState.DELIVERED)
        }

        @Test
        fun `force publish changes only the requested club namespace`() {
            store.saveRecord(clubA, PublicationRecord("same", PublicationState.FAILED_PERMANENT))
            store.saveRecord(clubB, PublicationRecord("same", PublicationState.DELIVERY_UNCERTAIN))
            val scoped = serviceWith(DiscordDestinationResolver { club ->
                if (club == clubA) destinationA else destinationB
            })

            val result = scoped.forcePublish(clubA, canonicalFor(clubA, "same"))

            assertThat(result.outcome).isEqualTo(PublicationOutcome.PUBLISHED)
            assertThat(store.find(clubA, "same")?.state).isEqualTo(PublicationState.DELIVERED)
            assertThat(store.find(clubB, "same")?.state).isEqualTo(PublicationState.DELIVERY_UNCERTAIN)
        }

        @Test
        fun `club without destination continues and is baselined without HTTP`() {
            val scoped = serviceWith(DiscordDestinationResolver { null })

            val result = scoped.publishIfNeeded(canonicalFor(clubA, "no-discord"))

            assertThat(result.outcome).isEqualTo(PublicationOutcome.SKIPPED_NO_DESTINATION)
            assertThat(store.find(clubA, "no-discord")?.state).isEqualTo(PublicationState.BASELINED)
            verify(webhookClient, never()).send(any(), any())
        }
    }

    // =========================================================================
    // WAL pre-send write
    // =========================================================================

    @Nested
    inner class WalPreSendWrite {

        @Test
        fun `DELIVERING is written to store before HTTP call`() {
            // Intercept: capture state at the moment HTTP is called
            var stateAtHttpCall: PublicationState? = null
            whenever(webhookClient.send(any(), any())).thenAnswer {
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
            doThrow(RuntimeException("disk full")).whenever(failingStore).saveRecord(clubIdEq(CLUB_ID), any())
            val svc = makeService(failingStore)

            val result = svc.publishIfNeeded(canonical("m1"))

            assertThat(result.outcome).isEqualTo(PublicationOutcome.FAILED_BEFORE_SEND)
            verify(webhookClient, never()).send(any(), any())
        }

        @Test
        fun `DELIVERING becomes FAILED_TRANSIENT when Discord returns transient HTTP error`() {
            val httpError = WebClientResponseException.create(429, "Too Many Requests", HttpHeaders.EMPTY, ByteArray(0), null)
            doThrow(DiscordDeliveryException("rate limited", httpError)).whenever(webhookClient).send(any(), any())

            val result = service.publishIfNeeded(canonical("m1"))

            assertThat(result.outcome).isEqualTo(PublicationOutcome.FAILED_HTTP)
            assertThat(store.loadRecords()["m1"]?.state).isEqualTo(PublicationState.FAILED_TRANSIENT)
            assertThat(store.loadRecords()["m1"]?.lastHttpStatus).isEqualTo(429)
        }

        @Test
        fun `HTTP 429 honors explicit Retry-After without shortening the bounded retry delay`() {
            val headers = HttpHeaders().apply { set(HttpHeaders.RETRY_AFTER, "120") }
            val httpError = WebClientResponseException.create(429, "Too Many Requests", headers, ByteArray(0), null)
            doThrow(DiscordDeliveryException("rate limited", httpError)).whenever(webhookClient).send(any(), any())
            val before = java.time.Instant.now()

            service.publishIfNeeded(canonical("rate-limit-delay"))

            val record = requireNotNull(store.find(CLUB_ID, "rate-limit-delay"))
            assertThat(record.state).isEqualTo(PublicationState.FAILED_TRANSIENT)
            assertThat(java.time.Instant.ofEpochSecond(requireNotNull(record.nextAutomaticAttemptAt)))
                .isAfterOrEqualTo(before.truncatedTo(java.time.temporal.ChronoUnit.SECONDS).plusSeconds(120))
        }

        @Test
        fun `DELIVERING upgraded to DELIVERY_UNCERTAIN when network error is ambiguous`() {
            val networkError = DiscordDeliveryException("connection reset", java.io.IOException("Connection reset by peer"))
            doThrow(networkError).whenever(webhookClient).send(any(), any())

            val result = service.publishIfNeeded(canonical("m1"))

            // Ambiguous: request may have reached Discord → DELIVERY_UNCERTAIN, NOT removed
            assertThat(result.outcome).isEqualTo(PublicationOutcome.FAILED_AMBIGUOUS)
            val record = store.loadRecords()["m1"]!!
            assertThat(record.state).isEqualTo(PublicationState.DELIVERY_UNCERTAIN)
            assertThat(record.attemptCount).isEqualTo(1)
            assertThat(record.lastAttemptAt).isNotNull()
            assertThat(record.lastError).startsWith("NETWORK_EXCEPTION:")
        }

        @Test
        fun `timeout uncertainty preserves attempt metadata and classified reason`() {
            doThrow(DiscordDeliveryException("read timed out", SocketTimeoutException("Read timed out")))
                .whenever(webhookClient).send(any(), any())

            val result = service.publishIfNeeded(canonical("m-timeout"))

            val record = store.loadRecords()["m-timeout"]!!
            assertThat(result.outcome).isEqualTo(PublicationOutcome.FAILED_AMBIGUOUS)
            assertThat(record.state).isEqualTo(PublicationState.DELIVERY_UNCERTAIN)
            assertThat(record.attemptCount).isEqualTo(1)
            assertThat(record.lastAttemptAt).isNotNull()
            assertThat(record.lastError).startsWith("NETWORK_TIMEOUT:")
        }

        @Test
        fun `uncertain state redacts webhook URL from persisted diagnostic`() {
            doThrow(DiscordDeliveryException(
                "POST https://discord.com/api/webhooks/123456/secret-token reset by peer",
                java.io.IOException("Connection reset"),
            )).whenever(webhookClient).send(any(), any())

            service.publishIfNeeded(canonical("m-redacted"))

            assertThat(store.loadRecords()["m-redacted"]!!.lastError)
                .contains("[Discord webhook]")
                .doesNotContain("secret-token")
        }

        @Test
        fun `uncertainty preserves prior transient diagnostic when a retry becomes ambiguous`() {
            store.saveRecord(PublicationRecord(
                matchId = "m-retry-context",
                state = PublicationState.FAILED_TRANSIENT,
                attemptCount = 1,
                lastAttemptAt = 1_724_207_200,
                lastError = "HTTP 429: rate limited",
                lastHttpStatus = 429,
            ))
            doThrow(DiscordDeliveryException("connection reset", java.io.IOException("Connection reset")))
                .whenever(webhookClient).send(any(), any())

            service.publishIfNeeded(canonical("m-retry-context"))

            val record = store.loadRecords()["m-retry-context"]!!
            assertThat(record.state).isEqualTo(PublicationState.DELIVERY_UNCERTAIN)
            assertThat(record.attemptCount).isEqualTo(2)
            assertThat(record.lastHttpStatus).isEqualTo(429)
            assertThat(record.lastError).contains("previousState=FAILED_TRANSIENT", "previousDiagnostic=HTTP 429: rate limited")
        }

        @Test
        fun `delivery persistence failure preserves the attempt as explicitly uncertain`() {
            val failingDeliveredStore = DeliveredWriteFailingStore(store)
            val serviceWithPersistenceFailure = makeService(failingDeliveredStore)

            val result = serviceWithPersistenceFailure.publishIfNeeded(canonical("m-delivered-write-failure"))

            val record = store.loadRecords()["m-delivered-write-failure"]!!
            assertThat(result.outcome).isEqualTo(PublicationOutcome.DELIVERED_BUT_STATE_UNCERTAIN)
            assertThat(record.state).isEqualTo(PublicationState.DELIVERY_UNCERTAIN)
            assertThat(record.attemptCount).isEqualTo(1)
            assertThat(record.lastAttemptAt).isNotNull()
            assertThat(record.lastError).startsWith("DELIVERED_STATE_PERSISTENCE_FAILURE:")
        }

        @Test
        fun `DELIVERING becomes FAILED_TRANSIENT when webhook not configured`() {
            doThrow(IllegalStateException("no url")).whenever(webhookClient).send(any(), any())

            service.publishIfNeeded(canonical("m1"))

            assertThat(store.loadRecords()["m1"]?.state).isEqualTo(PublicationState.FAILED_TRANSIENT)
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
            store.saveRecord(PublicationRecord(
                matchId = "m1",
                state = PublicationState.DELIVERING,
                attemptCount = 3,
                lastAttemptAt = 1_723_000_000,
            ))

            // Simulate restart: new store instance reads the file
            val restartedStore = makeStore()
            val recovered = restartedStore.loadRecords()["m1"]!!
            assertThat(recovered.state).isEqualTo(PublicationState.DELIVERY_UNCERTAIN)
            assertThat(recovered.attemptCount).isEqualTo(3)
            assertThat(recovered.lastAttemptAt).isEqualTo(1_723_000_000)
            assertThat(recovered.lastError).startsWith("STARTUP_RECOVERY:")

            // New service on restarted store
            val restartedService = makeService(restartedStore)
            val result = restartedService.publishIfNeeded(canonical("m1"))

            assertThat(result.outcome).isEqualTo(PublicationOutcome.SKIPPED_DELIVERY_UNCERTAIN)
            verify(webhookClient, never()).send(any(), any())
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
            verify(webhookClient, never()).send(any(), any())
        }

        @Test
        fun `DELIVERY_UNCERTAIN blocks scheduler (zero HTTP calls)`() {
            store.saveRecord(PublicationRecord("m1", PublicationState.DELIVERY_UNCERTAIN))

            val result = service.publishIfNeeded(canonical("m1"))

            assertThat(result.outcome).isEqualTo(PublicationOutcome.SKIPPED_DELIVERY_UNCERTAIN)
            verify(webhookClient, never()).send(any(), any())
        }

        @Test
        fun `DELIVERY_UNCERTAIN blocks notify-latest (zero HTTP calls)`() {
            store.saveRecord(PublicationRecord("m1", PublicationState.DELIVERY_UNCERTAIN))

            val result = service.publishIfNeeded(canonical("m1"))

            assertThat(result.outcome).isEqualTo(PublicationOutcome.SKIPPED_DELIVERY_UNCERTAIN)
            verify(webhookClient, never()).send(any(), any())
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
            verify(webhookClient, never()).send(any(), any())
        }

        @Test
        fun `resolveAsUndelivered removes record allowing resend`() {
            store.saveRecord(PublicationRecord("m1", PublicationState.DELIVERY_UNCERTAIN))
            store.resolveAsUndelivered("m1")

            val result = service.publishIfNeeded(canonical("m1"))

            assertThat(result.outcome).isEqualTo(PublicationOutcome.PUBLISHED)
            verify(webhookClient).send(any(), any())
        }

        @Test
        fun `forcePublish sends even if DELIVERY_UNCERTAIN (explicit admin action)`() {
            store.saveRecord(PublicationRecord("m1", PublicationState.DELIVERY_UNCERTAIN))

            val result = service.forcePublish(canonical("m1"))

            assertThat(result.outcome).isEqualTo(PublicationOutcome.PUBLISHED)
            verify(webhookClient).send(any(), any())
            assertThat(store.loadRecords()["m1"]?.state).isEqualTo(PublicationState.DELIVERED)
        }

        @Test
        fun `force publish records prior uncertain diagnostic before replacing current state`() {
            val events = mock<OperationalEventRepository>()
            val recorder = OperationalEventRecorder(events)
            val diagnosticService = serviceWith(
                DiscordDestinationResolver { DiscordDestination("https://discord.test/default") },
                recorder,
            )
            store.saveRecord(PublicationRecord(
                matchId = "m-forensic",
                state = PublicationState.DELIVERY_UNCERTAIN,
                attemptCount = 2,
                lastAttemptAt = 1_723_000_000,
                lastError = "NETWORK_TIMEOUT: Read timed out",
            ))

            diagnosticService.forcePublish(canonical("m-forensic"))

            val captured = argumentCaptor<OperationalEvent>()
            verify(events, org.mockito.Mockito.atLeast(3)).save(captured.capture())
            assertThat(captured.allValues).anySatisfy { event ->
                assertThat(event).extracting(OperationalEvent::phase, OperationalEvent::status)
                    .containsExactly("MANUAL_RESEND_REQUESTED", EventStatus.INFO)
                assertThat(event.message).contains("estado anterior: DELIVERY_UNCERTAIN")
                assertThat(event.message).contains("NETWORK_TIMEOUT: Read timed out")
            }
            assertThat(captured.allValues).anySatisfy { event ->
                assertThat(event).extracting(OperationalEvent::phase, OperationalEvent::status)
                    .containsExactly("DELIVERED", EventStatus.SUCCESS)
                assertThat(event.message).contains("Origem: reenvio manual")
            }
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
            verify(webhookClient, never()).send(any(), any())
        }

        @Test
        fun `published match is skipped after store reload (restart simulation)`() {
            service.publishIfNeeded(canonical("m1"))
            verify(webhookClient, times(1)).send(any(), any())

            val restartedStore = makeStore()
            val restartedService = makeService(restartedStore)

            val result = restartedService.publishIfNeeded(canonical("m1"))

            assertThat(result.outcome).isEqualTo(PublicationOutcome.SKIPPED_ALREADY_DELIVERED)
            verify(webhookClient, times(1)).send(any(), any()) // still 1, no extra call
        }

        @Test
        fun `second call same session is skipped`() {
            service.publishIfNeeded(canonical("m1"))
            val second = service.publishIfNeeded(canonical("m1"))

            assertThat(second.outcome).isEqualTo(PublicationOutcome.SKIPPED_ALREADY_DELIVERED)
            verify(webhookClient, times(1)).send(any(), any())
        }
    }

    // =========================================================================
    // Store migration: v1 → v2
    // =========================================================================

    @Nested
    inner class StoreMigration {

        @Test
        fun `v1 string-array format is migrated to DELIVERED records`() {
            val storeFile = AppDataPaths.publicationStoreFile(CLUB_ID)
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
            val storeFile = AppDataPaths.publicationStoreFile(CLUB_ID)
            storeFile.parent.toFile().mkdirs()
            storeFile.toFile().writeText("""["old-id"]""")

            makeStore().loadRecords(CLUB_ID) // first scoped access triggers migration

            val backup = storeFile.resolveSibling("published-matches.json.v1.bak")
            assertThat(backup).exists()
            assertThat(backup.toFile().readText()).contains("old-id")
        }

        @Test
        fun `v1 IDs are considered DELIVERED - scheduler does not republish`() {
            val storeFile = AppDataPaths.publicationStoreFile(CLUB_ID)
            storeFile.parent.toFile().mkdirs()
            storeFile.toFile().writeText("""["m1","m2"]""")

            val migratedStore = makeStore()
            val migratedService = makeService(migratedStore)

            val r1 = migratedService.publishIfNeeded(canonical("m1"))
            val r2 = migratedService.publishIfNeeded(canonical("m2"))

            assertThat(r1.outcome).isEqualTo(PublicationOutcome.SKIPPED_ALREADY_DELIVERED)
            assertThat(r2.outcome).isEqualTo(PublicationOutcome.SKIPPED_ALREADY_DELIVERED)
            verify(webhookClient, never()).send(any(), any())
        }

        @Test
        fun `v2 format is read correctly`() {
            val storeFile = AppDataPaths.publicationStoreFile(CLUB_ID)
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
            val storeFile = AppDataPaths.publicationStoreFile(CLUB_ID)
            storeFile.parent.toFile().mkdirs()
            storeFile.toFile().writeText("{corrupted!}")

            val thrown = runCatching { service.publishIfNeeded(canonical("m1")) }
            assertThat(thrown.isFailure).isTrue()
            verify(webhookClient, never()).send(any(), any())
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

            whenever(webhookClient.send(any(), any())).thenAnswer { sendCount.incrementAndGet(); Unit }

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
            verify(webhookClient).send(any(), any())
        }

        @Test
        fun `forcePublish persists DELIVERED after success`() {
            service.forcePublish(canonical("m1"))

            assertThat(store.loadRecords()["m1"]?.state).isEqualTo(PublicationState.DELIVERED)
        }

        @Test
        fun `manual resend uses the same DNF contribution presentation as automatic delivery`() {
            val canonical = opponentDnfCanonical("dnf-resend")

            assertThat(service.publishIfNeeded(canonical).outcome).isEqualTo(PublicationOutcome.PUBLISHED)
            assertThat(service.forcePublish(canonical).outcome).isEqualTo(PublicationOutcome.PUBLISHED)

            val payloads = argumentCaptor<DiscordPayload>()
            verify(webhookClient, times(2)).send(any(), payloads.capture())
            assertThat(payloads.allValues).hasSize(2)
            assertThat(payloads.allValues[1]).isEqualTo(payloads.allValues[0])
            assertThat(payloads.firstValue.embeds.first().title).startsWith("🏆 Test FC 3 × 0 Rival FC")
            assertThat(payloads.firstValue.embeds.first().description)
                .contains("Adversário saiu antes do fim")
            assertThat(payloads.firstValue.embeds.flatMap { it.fields }
                .single { it.name == "⚽ GOLS" }.value).contains("Scorer ×2", "Support ×1")
            assertThat(payloads.firstValue.embeds.flatMap { it.fields }
                .single { it.name == "🎯 ASSISTÊNCIAS" }.value).contains("Scorer ×1")
        }

        @Test
        fun `forcePublish WAL - DELIVERING written before HTTP`() {
            var stateAtHttp: PublicationState? = null
            whenever(webhookClient.send(any(), any())).thenAnswer {
                stateAtHttp = store.loadRecords()["m1"]?.state
                Unit
            }

            service.forcePublish(canonical("m1"))

            assertThat(stateAtHttp).isEqualTo(PublicationState.DELIVERING)
        }

        @Test
        fun `forcePublish ambiguous network error upgrades DELIVERING to DELIVERY_UNCERTAIN`() {
            val networkError = DiscordDeliveryException("down", java.net.SocketException("Connection reset"))
            doThrow(networkError).whenever(webhookClient).send(any(), any())

            val result = service.forcePublish(canonical("m1"))

            assertThat(result.outcome).isEqualTo(PublicationOutcome.FAILED_AMBIGUOUS)
            assertThat(store.loadRecords()["m1"]?.state).isEqualTo(PublicationState.DELIVERY_UNCERTAIN)
        }

        @Test
        fun `forcePublish HTTP error persists as FAILED_TRANSIENT`() {
            val httpError = WebClientResponseException.create(503, "Service Unavailable", HttpHeaders.EMPTY, ByteArray(0), null)
            doThrow(DiscordDeliveryException("down", httpError)).whenever(webhookClient).send(any(), any())

            val result = service.forcePublish(canonical("m1"))

            assertThat(result.outcome).isEqualTo(PublicationOutcome.FAILED_HTTP)
            assertThat(store.loadRecords()["m1"]?.state).isEqualTo(PublicationState.FAILED_TRANSIENT)
        }

        @Test
        fun `manual resend failure preserves the slow recovery schedule of a parked publication`() {
            val now = java.time.Instant.now()
            val httpError = WebClientResponseException.create(503, "Service Unavailable", HttpHeaders.EMPTY, ByteArray(0), null)
            doThrow(DiscordDeliveryException("down", httpError)).whenever(webhookClient).send(any(), any())
            store.saveRecord(PublicationRecord(
                "parked-manual", PublicationState.RETRY_EXHAUSTED,
                attemptCount = PublicationRetryPolicy.MAX_AUTOMATIC_ATTEMPTS,
                recoveryAttemptCount = 2,
                nextAutomaticAttemptAt = now.plusSeconds(3_600).epochSecond,
            ))

            val result = service.forcePublish(canonical("parked-manual"))
            val record = requireNotNull(store.find(CLUB_ID, "parked-manual"))

            assertThat(result.outcome).isEqualTo(PublicationOutcome.FAILED_HTTP)
            assertThat(record.state).isEqualTo(PublicationState.RETRY_EXHAUSTED)
            assertThat(record.recoveryAttemptCount).isEqualTo(2)
            assertThat(record.nextAutomaticAttemptAt).isNotNull()
        }

        @Test
        fun `manual resend does not double-send a publication already claimed by reconciliation`() {
            val now = java.time.Instant.now()
            store.saveRecord(
                PublicationRecord(
                    matchId = "manual-versus-recovery",
                    state = PublicationState.RETRY_EXHAUSTED,
                    attemptCount = PublicationRetryPolicy.MAX_AUTOMATIC_ATTEMPTS,
                    nextAutomaticAttemptAt = now.minusSeconds(1).epochSecond,
                ),
            )
            val claim = requireNotNull(
                service.claimForReconciliation(
                    CLUB_ID,
                    requireNotNull(store.find(CLUB_ID, "manual-versus-recovery")),
                    now,
                ),
            )

            val manual = service.forcePublish(canonical("manual-versus-recovery"))
            val recovered = service.deliverReconciliationClaim(canonical("manual-versus-recovery"), claim)

            assertThat(manual.outcome).isEqualTo(PublicationOutcome.SKIPPED_DELIVERY_UNCERTAIN)
            assertThat(manual.errorMessage).contains("já está em andamento")
            assertThat(recovered.outcome).isEqualTo(PublicationOutcome.PUBLISHED)
            verify(webhookClient, times(1)).send(any(), any())
            assertThat(store.find(CLUB_ID, "manual-versus-recovery")?.state).isEqualTo(PublicationState.DELIVERED)
        }

        @Test
        fun `scheduler does not re-publish after forcePublish`() {
            service.forcePublish(canonical("m1"))

            val schedulerResult = service.publishIfNeeded(canonical("m1"))

            assertThat(schedulerResult.outcome).isEqualTo(PublicationOutcome.SKIPPED_ALREADY_DELIVERED)
            verify(webhookClient, times(1)).send(any(), any())
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
            whenever(failingStore.createRecordIfAbsent(clubIdEq(CLUB_ID), any())).thenReturn(true)
            whenever(failingStore.claimForAutomaticDelivery(clubIdEq(CLUB_ID), any(), any())).thenAnswer { invocation ->
                val expected = invocation.getArgument<PublicationRecord>(1)
                val attemptedAt = invocation.getArgument<java.time.Instant>(2)
                expected.copy(
                    state = PublicationState.DELIVERING,
                    attemptCount = expected.attemptCount + 1,
                    lastAttemptAt = attemptedAt.epochSecond,
                    updatedAt = attemptedAt.epochSecond,
                )
            }
            // Allow DELIVERING write, fail only DELIVERED write
            whenever(failingStore.saveRecord(clubIdEq(CLUB_ID), argThat { state == PublicationState.DELIVERING })).then { }
            doThrow(RuntimeException("disk full")).whenever(failingStore)
                .saveRecord(clubIdEq(CLUB_ID), argThat { state == PublicationState.DELIVERED })
            val svc = makeService(failingStore)

            val result = svc.publishIfNeeded(canonical("m1"))

            assertThat(result.outcome).isEqualTo(PublicationOutcome.DELIVERED_BUT_STATE_UNCERTAIN)
            assertThat(result.delivered).isTrue()
            verify(webhookClient).send(any(), any())
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
            verify(webhookClient).send(any(), any())
        }
    }

    // =========================================================================
    // Exception classification: WAL correctness under different failure modes
    // =========================================================================

    @Nested
    inner class ExceptionClassification {

        @Test
        fun `timeout (SocketTimeoutException) → FAILED_AMBIGUOUS, DELIVERY_UNCERTAIN`() {
            val timeout = DiscordDeliveryException("Read timed out", SocketTimeoutException("Read timed out"))
            doThrow(timeout).whenever(webhookClient).send(any(), any())

            val result = service.publishIfNeeded(canonical("m1"))

            assertThat(result.outcome).isEqualTo(PublicationOutcome.FAILED_AMBIGUOUS)
            assertThat(store.loadRecords()["m1"]?.state).isEqualTo(PublicationState.DELIVERY_UNCERTAIN)
            verify(webhookClient).send(any(), any()) // HTTP was attempted
        }

        @Test
        fun `connection reset (IOException) → FAILED_AMBIGUOUS, DELIVERY_UNCERTAIN`() {
            val reset = DiscordDeliveryException("Connection reset", java.io.IOException("Connection reset by peer"))
            doThrow(reset).whenever(webhookClient).send(any(), any())

            val result = service.publishIfNeeded(canonical("m1"))

            assertThat(result.outcome).isEqualTo(PublicationOutcome.FAILED_AMBIGUOUS)
            assertThat(store.loadRecords()["m1"]?.state).isEqualTo(PublicationState.DELIVERY_UNCERTAIN)
        }

        @Test
        fun `HTTP 400 from Discord → FAILED_PERMANENT`() {
            val http400 = WebClientResponseException.create(400, "Bad Request", HttpHeaders.EMPTY, ByteArray(0), null)
            doThrow(DiscordDeliveryException("Bad Request", http400)).whenever(webhookClient).send(any(), any())

            val result = service.publishIfNeeded(canonical("m1"))

            assertThat(result.outcome).isEqualTo(PublicationOutcome.FAILED_HTTP)
            assertThat(result.httpStatusCode).isEqualTo(400)
            assertThat(store.loadRecords()["m1"]?.state).isEqualTo(PublicationState.FAILED_PERMANENT)
        }

        @Test
        fun `explicit Discord 401 403 and 404 responses are permanent and never scheduled for recovery`() {
            listOf(401, 403, 404).forEach { status ->
                org.mockito.Mockito.reset(webhookClient)
                val response = WebClientResponseException.create(status, "Rejected", HttpHeaders.EMPTY, ByteArray(0), null)
                doThrow(DiscordDeliveryException("Discord rejected", response)).whenever(webhookClient).send(any(), any())

                service.publishIfNeeded(canonical("permanent-$status"))

                val record = requireNotNull(store.find(CLUB_ID, "permanent-$status"))
                assertThat(record.state).isEqualTo(PublicationState.FAILED_PERMANENT)
                assertThat(record.nextAutomaticAttemptAt).isNull()
            }
        }

        @Test
        fun `HTTP 500 from Discord → FAILED_TRANSIENT`() {
            val http500 = WebClientResponseException.create(500, "Internal Server Error", HttpHeaders.EMPTY, ByteArray(0), null)
            doThrow(DiscordDeliveryException("Server Error", http500)).whenever(webhookClient).send(any(), any())

            val result = service.publishIfNeeded(canonical("m1"))

            assertThat(result.outcome).isEqualTo(PublicationOutcome.FAILED_HTTP)
            assertThat(result.httpStatusCode).isEqualTo(500)
            assertThat(store.loadRecords()["m1"]?.state).isEqualTo(PublicationState.FAILED_TRANSIENT)
        }

        @Test
        fun `failure before connection (webhook not configured) → FAILED_TRANSIENT`() {
            doThrow(IllegalStateException("no url")).whenever(webhookClient).send(any(), any())

            val result = service.publishIfNeeded(canonical("m1"))

            assertThat(result.outcome).isEqualTo(PublicationOutcome.FAILED_BEFORE_SEND)
            assertThat(store.loadRecords()["m1"]?.state).isEqualTo(PublicationState.FAILED_TRANSIENT)
        }

        @Test
        fun `DELIVERY_UNCERTAIN is not auto-resent even after new match polling`() {
            store.saveRecord(PublicationRecord("m1", PublicationState.DELIVERY_UNCERTAIN))

            // Simulate scheduler calling publishIfNeeded multiple times
            val r1 = service.publishIfNeeded(canonical("m1"))
            val r2 = service.publishIfNeeded(canonical("m1"))
            val r3 = service.publishIfNeeded(canonical("m1"))

            assertThat(r1.outcome).isEqualTo(PublicationOutcome.SKIPPED_DELIVERY_UNCERTAIN)
            assertThat(r2.outcome).isEqualTo(PublicationOutcome.SKIPPED_DELIVERY_UNCERTAIN)
            assertThat(r3.outcome).isEqualTo(PublicationOutcome.SKIPPED_DELIVERY_UNCERTAIN)
            verify(webhookClient, never()).send(any(), any())
        }

        @Test
        fun `httpStatusCode is populated for FAILED_HTTP but null for other outcomes`() {
            // PUBLISHED → no httpStatusCode
            val published = service.publishIfNeeded(canonical("pub"))
            assertThat(published.outcome).isEqualTo(PublicationOutcome.PUBLISHED)
            assertThat(published.httpStatusCode).isNull()

            // FAILED_HTTP → httpStatusCode present
            val http422 = WebClientResponseException.create(422, "Unprocessable Entity", HttpHeaders.EMPTY, ByteArray(0), null)
            doThrow(DiscordDeliveryException("Unprocessable", http422)).whenever(webhookClient).send(any(), any())
            val failed = service.publishIfNeeded(canonical("fail"))
            assertThat(failed.outcome).isEqualTo(PublicationOutcome.FAILED_HTTP)
            assertThat(failed.httpStatusCode).isEqualTo(422)
            assertThat(store.loadRecords()["fail"]?.state).isEqualTo(PublicationState.FAILED_PERMANENT)
        }
    }

    // =========================================================================
    // Repeated restart: DELIVERY_UNCERTAIN survives multiple restarts
    // =========================================================================

    @Nested
    inner class RepeatedRestart {

        @Test
        fun `DELIVERY_UNCERTAIN remains DELIVERY_UNCERTAIN after repeated restarts`() {
            store.saveRecord(PublicationRecord("m1", PublicationState.DELIVERING))

            // Restart 1
            val restart1 = makeStore()
            assertThat(restart1.loadRecords()["m1"]?.state).isEqualTo(PublicationState.DELIVERY_UNCERTAIN)

            // Restart 2
            val restart2 = makeStore()
            assertThat(restart2.loadRecords()["m1"]?.state).isEqualTo(PublicationState.DELIVERY_UNCERTAIN)

            // Restart 3
            val restart3 = makeStore()
            assertThat(restart3.loadRecords()["m1"]?.state).isEqualTo(PublicationState.DELIVERY_UNCERTAIN)

            // Service on latest restart still blocks auto-resend
            val svc = makeService(restart3)
            val result = svc.publishIfNeeded(canonical("m1"))
            assertThat(result.outcome).isEqualTo(PublicationOutcome.SKIPPED_DELIVERY_UNCERTAIN)
            verify(webhookClient, never()).send(any(), any())
        }

        @Test
        fun `DELIVERY_UNCERTAIN can only be cleared by admin (resolveAsDelivered or resolveAsUndelivered)`() {
            store.saveRecord(PublicationRecord("m1", PublicationState.DELIVERING))
            val restarted = makeStore()

            // Normal scheduler cannot clear it
            val schedulerResult = makeService(restarted).publishIfNeeded(canonical("m1"))
            assertThat(schedulerResult.outcome).isEqualTo(PublicationOutcome.SKIPPED_DELIVERY_UNCERTAIN)

            // Admin resolves it
            restarted.resolveAsUndelivered("m1")
            val afterResolve = makeService(restarted).publishIfNeeded(canonical("m1"))
            assertThat(afterResolve.outcome).isEqualTo(PublicationOutcome.PUBLISHED)
            verify(webhookClient).send(any(), any())
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

    // =========================================================================
    // forcePublish per-match lock (race condition fix)
    // =========================================================================

    @Nested
    inner class ForcePublishLock {

        @Test
        fun `forcePublish and publishIfNeeded for same matchId serialize — no concurrent HTTP`() {
            val executor = Executors.newFixedThreadPool(2)
            val sendCount = AtomicInteger(0)
            val maxConcurrentSend = AtomicInteger(0)
            val currentConcurrentSend = AtomicInteger(0)
            val bothReady = CountDownLatch(2)
            val startGun = CountDownLatch(1)
            val bothDone = CountDownLatch(2)

            whenever(webhookClient.send(any(), any())).thenAnswer {
                val c = currentConcurrentSend.incrementAndGet()
                maxConcurrentSend.updateAndGet { max -> maxOf(max, c) }
                Thread.sleep(50)
                currentConcurrentSend.decrementAndGet()
                sendCount.incrementAndGet()
                Unit
            }

            executor.submit {
                bothReady.countDown()
                startGun.await(5, TimeUnit.SECONDS)
                service.forcePublish(canonical("race-fp"))
                bothDone.countDown()
            }
            executor.submit {
                bothReady.countDown()
                startGun.await(5, TimeUnit.SECONDS)
                service.publishIfNeeded(canonical("race-fp"))
                bothDone.countDown()
            }

            bothReady.await(2, TimeUnit.SECONDS)
            startGun.countDown()
            bothDone.await(10, TimeUnit.SECONDS)
            executor.shutdown()

            assertThat(maxConcurrentSend.get()).isEqualTo(1)
            assertThat(sendCount.get()).isLessThanOrEqualTo(2)
            assertThat(store.loadRecords()["race-fp"]?.state).isEqualTo(PublicationState.DELIVERED)
        }

        @Test
        fun `concurrent forcePublish for same matchId — only one HTTP call at a time`() {
            val executor = Executors.newFixedThreadPool(5)
            val sendCount = AtomicInteger(0)
            val maxConcurrent = AtomicInteger(0)
            val currentConcurrent = AtomicInteger(0)
            val allReady = CountDownLatch(5)
            val startGun = CountDownLatch(1)
            val allDone = CountDownLatch(5)

            whenever(webhookClient.send(any(), any())).thenAnswer {
                val c = currentConcurrent.incrementAndGet()
                maxConcurrent.updateAndGet { max -> maxOf(max, c) }
                Thread.sleep(30)
                currentConcurrent.decrementAndGet()
                sendCount.incrementAndGet()
                Unit
            }

            repeat(5) {
                executor.submit {
                    allReady.countDown()
                    startGun.await(5, TimeUnit.SECONDS)
                    service.forcePublish(canonical("same"))
                    allDone.countDown()
                }
            }

            allReady.await(2, TimeUnit.SECONDS)
            startGun.countDown()
            allDone.await(10, TimeUnit.SECONDS)
            executor.shutdown()

            assertThat(maxConcurrent.get()).isEqualTo(1)
            assertThat(store.loadRecords()["same"]?.state).isEqualTo(PublicationState.DELIVERED)
        }

        @Test
        fun `store is consistent after concurrent forcePublish and publishIfNeeded`() {
            val executor = Executors.newFixedThreadPool(4)
            val allDone = CountDownLatch(4)

            whenever(webhookClient.send(any(), any())).thenAnswer { Thread.sleep(10); Unit }

            repeat(2) {
                executor.submit {
                    service.forcePublish(canonical("consistent"))
                    allDone.countDown()
                }
            }
            repeat(2) {
                executor.submit {
                    service.publishIfNeeded(canonical("consistent"))
                    allDone.countDown()
                }
            }

            allDone.await(10, TimeUnit.SECONDS)
            executor.shutdown()

            val record = store.loadRecords()["consistent"]
            assertThat(record).isNotNull
            assertThat(record!!.state).isEqualTo(PublicationState.DELIVERED)
        }

        @Test
        fun `forcePublish for different matchIds can execute in parallel`() {
            val executor = Executors.newFixedThreadPool(3)
            val maxConcurrent = AtomicInteger(0)
            val currentConcurrent = AtomicInteger(0)
            val allReady = CountDownLatch(3)
            val startGun = CountDownLatch(1)
            val allDone = CountDownLatch(3)

            whenever(webhookClient.send(any(), any())).thenAnswer {
                val c = currentConcurrent.incrementAndGet()
                maxConcurrent.updateAndGet { max -> maxOf(max, c) }
                Thread.sleep(80)
                currentConcurrent.decrementAndGet()
                Unit
            }

            repeat(3) { i ->
                executor.submit {
                    allReady.countDown()
                    startGun.await(5, TimeUnit.SECONDS)
                    service.forcePublish(canonical("par-$i"))
                    allDone.countDown()
                }
            }

            allReady.await(2, TimeUnit.SECONDS)
            startGun.countDown()
            allDone.await(10, TimeUnit.SECONDS)
            executor.shutdown()

            assertThat(maxConcurrent.get()).isGreaterThan(1)
            assertThat(store.loadRecords()["par-0"]?.state).isEqualTo(PublicationState.DELIVERED)
            assertThat(store.loadRecords()["par-1"]?.state).isEqualTo(PublicationState.DELIVERED)
            assertThat(store.loadRecords()["par-2"]?.state).isEqualTo(PublicationState.DELIVERED)
        }

        @Test
        fun `lock is released when forcePublish throws exception during send`() {
            val networkError = DiscordDeliveryException("crash", java.io.IOException("boom"))
            doThrow(networkError).whenever(webhookClient).send(any(), any())

            val result = service.forcePublish(canonical("ex"))

            assertThat(result.outcome).isEqualTo(PublicationOutcome.FAILED_AMBIGUOUS)

            // Lock must be released — a second call must not deadlock
            org.mockito.Mockito.reset(webhookClient)
            val second = service.forcePublish(canonical("ex"))
            assertThat(second.outcome).isIn(
                PublicationOutcome.SKIPPED_DELIVERY_UNCERTAIN,
                PublicationOutcome.PUBLISHED,
                PublicationOutcome.FAILED_AMBIGUOUS,
            )
        }
    }

    // =========================================================================
    // Durable automatic publication intent and retry recovery
    // =========================================================================

    @Nested
    inner class DurablePublicationRecovery {

        @Test
        fun `durable PENDING is delivered by the immediate automatic fast path`() {
            store.saveRecord(CLUB_ID, PublicationRecord("pending-fast-path", PublicationState.PENDING))

            val result = service.publishIfNeeded(canonical("pending-fast-path"))

            assertThat(result.outcome).isEqualTo(PublicationOutcome.PUBLISHED)
            assertThat(store.find(CLUB_ID, "pending-fast-path")?.state).isEqualTo(PublicationState.DELIVERED)
            assertThat(store.find(CLUB_ID, "pending-fast-path")?.attemptCount).isEqualTo(1)
            verify(webhookClient).send(any(), any())
        }

        @Test
        fun `PENDING survives a restart and remains safely publishable`() {
            store.saveRecord(CLUB_ID, PublicationRecord("restart-pending", PublicationState.PENDING))
            val restartedStore = makeStore()
            val restartedService = makeService(restartedStore)

            val result = restartedService.publishIfNeeded(canonical("restart-pending"))

            assertThat(result.outcome).isEqualTo(PublicationOutcome.PUBLISHED)
            assertThat(restartedStore.find(CLUB_ID, "restart-pending")?.state).isEqualTo(PublicationState.DELIVERED)
        }

        @Test
        fun `four durable publication intents eventually deliver every safely retryable match`() {
            val transient = WebClientResponseException.create(
                503, "Service Unavailable", HttpHeaders.EMPTY, ByteArray(0), null,
            )
            listOf("match-1", "match-2", "match-3", "match-4").forEach { matchId ->
                store.saveRecord(CLUB_ID, PublicationRecord(matchId, PublicationState.PENDING))
            }
            val immediateAttempt = AtomicInteger(0)
            whenever(webhookClient.send(any(), any())).thenAnswer {
                when (immediateAttempt.incrementAndGet()) {
                    2, 4 -> throw DiscordDeliveryException("Discord 503", transient)
                    else -> Unit
                }
            }

            listOf("match-1", "match-2", "match-3", "match-4").forEach { matchId ->
                service.publishIfNeeded(canonical(matchId))
            }

            assertThat(store.find(CLUB_ID, "match-1")?.state).isEqualTo(PublicationState.DELIVERED)
            assertThat(store.find(CLUB_ID, "match-2")?.state).isEqualTo(PublicationState.FAILED_TRANSIENT)
            assertThat(store.find(CLUB_ID, "match-3")?.state).isEqualTo(PublicationState.DELIVERED)
            assertThat(store.find(CLUB_ID, "match-4")?.state).isEqualTo(PublicationState.FAILED_TRANSIENT)

            listOf("match-2", "match-4").forEach { matchId ->
                val failed = requireNotNull(store.find(CLUB_ID, matchId))
                store.saveRecord(
                    CLUB_ID,
                    failed.copy(
                        lastAttemptAt = java.time.Instant.now().minusSeconds(61).epochSecond,
                        nextAutomaticAttemptAt = null,
                    ),
                )
            }
            org.mockito.Mockito.reset(webhookClient)

            val retryTwo = service.publishIfNeeded(canonical("match-2"))
            val retryFour = service.publishIfNeeded(canonical("match-4"))

            assertThat(retryTwo.outcome).isEqualTo(PublicationOutcome.PUBLISHED)
            assertThat(retryFour.outcome).isEqualTo(PublicationOutcome.PUBLISHED)
            assertThat(store.loadRecords().values.map { it.state }).allMatch { it == PublicationState.DELIVERED }
            verify(webhookClient, times(2)).send(any(), any())
        }

        @Test
        fun `safe transient failures follow the approved one two five and fifteen minute backoff`() {
            assertThat(PublicationRetryPolicy.delayAfter(1)).isEqualTo(java.time.Duration.ofMinutes(1))
            assertThat(PublicationRetryPolicy.delayAfter(2)).isEqualTo(java.time.Duration.ofMinutes(2))
            assertThat(PublicationRetryPolicy.delayAfter(3)).isEqualTo(java.time.Duration.ofMinutes(5))
            assertThat(PublicationRetryPolicy.delayAfter(4)).isEqualTo(java.time.Duration.ofMinutes(15))

            val now = java.time.Instant.now().epochSecond
            val record = PublicationRecord(
                "backoff",
                PublicationState.FAILED_TRANSIENT,
                attemptCount = 1,
                lastAttemptAt = now,
            )
            store.saveRecord(CLUB_ID, record)

            val result = service.publishIfNeeded(canonical("backoff"))

            assertThat(result.outcome).isEqualTo(PublicationOutcome.SKIPPED_RETRY_BACKOFF)
            verify(webhookClient, never()).send(any(), any())
            assertThat(store.find(CLUB_ID, "backoff")).isEqualTo(record)
        }

        @Test
        fun `fifth safe automatic failure is parked for slow recovery and not resent by acquisition`() {
            val error = WebClientResponseException.create(
                503, "Service Unavailable", HttpHeaders.EMPTY, ByteArray(0), null,
            )
            doThrow(DiscordDeliveryException("Discord 503", error)).whenever(webhookClient).send(any(), any())
            store.saveRecord(CLUB_ID, PublicationRecord("retry-exhausted", PublicationState.PENDING))

            repeat(PublicationRetryPolicy.MAX_AUTOMATIC_ATTEMPTS) { index ->
                val result = service.publishIfNeeded(canonical("retry-exhausted"))
                assertThat(result.outcome).isEqualTo(PublicationOutcome.FAILED_HTTP)
                val record = requireNotNull(store.find(CLUB_ID, "retry-exhausted"))
                val attempt = index + 1
                if (attempt < PublicationRetryPolicy.MAX_AUTOMATIC_ATTEMPTS) {
                    assertThat(record.state).isEqualTo(PublicationState.FAILED_TRANSIENT)
                    store.saveRecord(
                        CLUB_ID,
                        record.copy(
                            lastAttemptAt = java.time.Instant.now()
                                .minus(requireNotNull(PublicationRetryPolicy.delayAfter(attempt)))
                                .minusSeconds(1)
                                .epochSecond,
                            nextAutomaticAttemptAt = null,
                        ),
                    )
                } else {
                    assertThat(record.state).isEqualTo(PublicationState.RETRY_EXHAUSTED)
                    assertThat(record.attemptCount).isEqualTo(PublicationRetryPolicy.MAX_AUTOMATIC_ATTEMPTS)
                    assertThat(record.nextAutomaticAttemptAt).isNotNull()
                    assertThat(record.lastError).contains("HTTP 503")
                    assertThat(record.lastHttpStatus).isEqualTo(503)
                }
            }

            val blocked = service.publishIfNeeded(canonical("retry-exhausted"))
            assertThat(blocked.outcome).isEqualTo(PublicationOutcome.SKIPPED_RETRY_EXHAUSTED)
            verify(webhookClient, times(PublicationRetryPolicy.MAX_AUTOMATIC_ATTEMPTS)).send(any(), any())
        }

        @Test
        fun `parked retryable publication is recovered by reconciliation and success clears recovery metadata`() {
            val now = java.time.Instant.now()
            store.saveRecord(
                PublicationRecord(
                    matchId = "parked-recovery",
                    state = PublicationState.RETRY_EXHAUSTED,
                    attemptCount = PublicationRetryPolicy.MAX_AUTOMATIC_ATTEMPTS,
                    lastAttemptAt = now.minusSeconds(1_800).epochSecond,
                    lastError = "HTTP 503: Service Unavailable",
                    lastHttpStatus = 503,
                    nextAutomaticAttemptAt = now.minusSeconds(1).epochSecond,
                ),
            )
            val expected = requireNotNull(store.find(CLUB_ID, "parked-recovery"))

            val claim = requireNotNull(service.claimForReconciliation(CLUB_ID, expected, now))
            val result = service.deliverReconciliationClaim(canonical("parked-recovery"), claim)

            assertThat(result.outcome).isEqualTo(PublicationOutcome.PUBLISHED)
            val delivered = requireNotNull(store.find(CLUB_ID, "parked-recovery"))
            assertThat(delivered.state).isEqualTo(PublicationState.DELIVERED)
            assertThat(delivered.attemptCount).isEqualTo(PublicationRetryPolicy.MAX_AUTOMATIC_ATTEMPTS + 1)
            assertThat(delivered.lastError).isNull()
            assertThat(delivered.nextAutomaticAttemptAt).isNull()
            assertThat(delivered.recoveryAttemptCount).isZero()
        }

        @Test
        fun `failed slow recovery remains parked with a longer durable delay`() {
            val now = java.time.Instant.now()
            val error = WebClientResponseException.create(503, "Service Unavailable", HttpHeaders.EMPTY, ByteArray(0), null)
            doThrow(DiscordDeliveryException("Discord 503", error)).whenever(webhookClient).send(any(), any())
            store.saveRecord(
                PublicationRecord(
                    matchId = "parked-again",
                    state = PublicationState.RETRY_EXHAUSTED,
                    attemptCount = PublicationRetryPolicy.MAX_AUTOMATIC_ATTEMPTS,
                    lastAttemptAt = now.minusSeconds(1_800).epochSecond,
                    nextAutomaticAttemptAt = now.minusSeconds(1).epochSecond,
                ),
            )

            val claim = requireNotNull(service.claimForReconciliation(CLUB_ID, requireNotNull(store.find(CLUB_ID, "parked-again")), now))
            val result = service.deliverReconciliationClaim(canonical("parked-again"), claim)
            val parked = requireNotNull(store.find(CLUB_ID, "parked-again"))

            assertThat(result.outcome).isEqualTo(PublicationOutcome.FAILED_HTTP)
            assertThat(parked.state).isEqualTo(PublicationState.RETRY_EXHAUSTED)
            assertThat(parked.recoveryAttemptCount).isEqualTo(1)
            assertThat(parked.nextAutomaticAttemptAt).isNotNull()
            assertThat(java.time.Instant.ofEpochSecond(requireNotNull(parked.nextAutomaticAttemptAt)))
                .isAfterOrEqualTo(now.truncatedTo(java.time.temporal.ChronoUnit.SECONDS).plus(java.time.Duration.ofHours(1)))
        }

        @Test
        fun `an exhausted match does not block a later match from automatic publication`() {
            store.saveRecord(
                PublicationRecord(
                    matchId = "older-exhausted",
                    state = PublicationState.RETRY_EXHAUSTED,
                    attemptCount = PublicationRetryPolicy.MAX_AUTOMATIC_ATTEMPTS,
                    nextAutomaticAttemptAt = java.time.Instant.now().plusSeconds(1_800).epochSecond,
                ),
            )

            val result = service.publishIfNeeded(canonical("later-match"))

            assertThat(result.outcome).isEqualTo(PublicationOutcome.PUBLISHED)
            assertThat(store.find(CLUB_ID, "older-exhausted")?.state).isEqualTo(PublicationState.RETRY_EXHAUSTED)
            assertThat(store.find(CLUB_ID, "later-match")?.state).isEqualTo(PublicationState.DELIVERED)
            verify(webhookClient).send(any(), any())
        }

        @Test
        fun `legacy transient record at the retry limit is made exhausted without a sixth automatic send`() {
            store.saveRecord(
                CLUB_ID,
                PublicationRecord(
                    "retry-limit",
                    PublicationState.FAILED_TRANSIENT,
                    attemptCount = PublicationRetryPolicy.MAX_AUTOMATIC_ATTEMPTS,
                    lastAttemptAt = java.time.Instant.now().epochSecond,
                ),
            )

            val result = service.publishIfNeeded(canonical("retry-limit"))

            assertThat(result.outcome).isEqualTo(PublicationOutcome.SKIPPED_RETRY_EXHAUSTED)
            assertThat(store.find(CLUB_ID, "retry-limit")?.state).isEqualTo(PublicationState.RETRY_EXHAUSTED)
            verify(webhookClient, never()).send(any(), any())
        }

        @Test
        fun `forensic DELIVERY_UNCERTAIN match is never automatically retried`() {
            store.saveRecord(
                CLUB_ID,
                PublicationRecord("990976744430293", PublicationState.DELIVERY_UNCERTAIN, attemptCount = 1),
            )

            val result = service.publishIfNeeded(canonical("990976744430293"))

            assertThat(result.outcome).isEqualTo(PublicationOutcome.SKIPPED_DELIVERY_UNCERTAIN)
            verify(webhookClient, never()).send(any(), any())
        }

        @Test
        fun `NO_DESTINATION baseline is not mutated when destination remains unavailable`() {
            store.saveRecord(
                CLUB_ID,
                PublicationRecord(
                    "no-destination",
                    PublicationState.BASELINED,
                    baselineReason = com.eafc26.discordstats.store.BaselineReason.NO_DESTINATION,
                ),
            )
            val events = mock<OperationalEventRepository>()
            val unavailable = serviceWith(DiscordDestinationResolver { null }, OperationalEventRecorder(events))
            val before = requireNotNull(store.find(CLUB_ID, "no-destination"))

            val claim = unavailable.claimForReconciliation(CLUB_ID, before)

            assertThat(claim).isNull()
            assertThat(store.find(CLUB_ID, "no-destination")).isEqualTo(before)
            verify(webhookClient, never()).send(any(), any())
            verify(events, never()).save(any())
        }

        @Test
        fun `NO_DESTINATION baseline is recovered only after a destination is resolvable`() {
            store.saveRecord(
                CLUB_ID,
                PublicationRecord(
                    "recover-destination",
                    PublicationState.BASELINED,
                    baselineReason = com.eafc26.discordstats.store.BaselineReason.NO_DESTINATION,
                ),
            )
            val record = requireNotNull(store.find(CLUB_ID, "recover-destination"))

            val claim = requireNotNull(service.claimForReconciliation(CLUB_ID, record))
            val result = service.deliverReconciliationClaim(canonical("recover-destination"), claim)

            assertThat(result.outcome).isEqualTo(PublicationOutcome.PUBLISHED)
            assertThat(store.find(CLUB_ID, "recover-destination")?.state).isEqualTo(PublicationState.DELIVERED)
        }
    }
}
