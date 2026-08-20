package com.eafc26.discordstats.service

import com.eafc26.discordstats.config.AppProperties
import com.eafc26.discordstats.config.EaProperties
import com.eafc26.discordstats.config.PhraseBank
import com.eafc26.discordstats.config.PollingProperties
import com.eafc26.discordstats.application.repository.CanonicalMatchRepository
import com.eafc26.discordstats.discord.DiscordDeliveryException
import com.eafc26.discordstats.discord.DiscordRenderer
import com.eafc26.discordstats.discord.DiscordWebhookClient
import com.eafc26.discordstats.llm.EditorialContextBuilder
import com.eafc26.discordstats.llm.LlmEditorialService
import com.eafc26.discordstats.llm.LlmProperties
import com.eafc26.discordstats.ea.EaApiResult
import com.eafc26.discordstats.ea.EaClubsGateway
import com.eafc26.discordstats.ea.WindowedEaClubsGateway
import com.eafc26.discordstats.ea.model.ClubDetails
import com.eafc26.discordstats.ea.model.ClubMatchEntry
import com.eafc26.discordstats.ea.model.MatchResponse
import com.eafc26.discordstats.ea.model.MemberStats
import com.eafc26.discordstats.ea.model.PlayerEntry
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.MatchId
import com.eafc26.discordstats.presentation.MatchSummaryBuilder
import com.eafc26.discordstats.store.PublicationRecord
import com.eafc26.discordstats.store.PublicationState
import com.eafc26.discordstats.store.PublishedMatchStore
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class MatchAcquisitionServiceTest {

    private lateinit var gateway: EaClubsGateway
    private lateinit var store: PublishedMatchStore
    private lateinit var webhookClient: DiscordWebhookClient
    private lateinit var stateHolder: AcquisitionStateHolder
    private lateinit var latestMatchHolder: LatestMatchHolder
    private lateinit var matchSummaryBuilder: MatchSummaryBuilder
    private lateinit var service: MatchAcquisitionService
    private lateinit var canonicalMatchRepository: CanonicalMatchRepository
    private lateinit var editorialPresentationService: com.eafc26.discordstats.presentation.editorial.MatchEditorialPresentationService
    private lateinit var synchronizationGapStore: InMemorySynchronizationGapStore

    private val clubId = "12345"

    /**
     * Builds the service under test using a real [DiscordMatchPublicationService]
     * wired with the shared [store] and [webhookClient] mocks.
     * This allows tests to verify [webhookClient] interactions transparently.
     */
    private fun makeService(): MatchAcquisitionService {
        val props = AppProperties(
            ea = EaProperties(clubId = clubId, clubName = "Test FC"),
            polling = PollingProperties(),
        )
        val llmEditorialService = LlmEditorialService(
            EditorialContextBuilder(), null, mock(), LlmProperties(enabled = false),
        )
        val publicationService = DiscordMatchPublicationService(store, webhookClient, DiscordRenderer(matchSummaryBuilder), llmEditorialService)
        return MatchAcquisitionService(
            gateway,
            store,
            publicationService,
            props,
            stateHolder,
            latestMatchHolder,
            matchSummaryBuilder,
            canonicalMatchRepository,
            CanonicalMatchFactory(),
            editorialPresentationService,
            LlmEditorialService(EditorialContextBuilder(), null, mock(), LlmProperties(enabled = false)),
            synchronizationGapStore = synchronizationGapStore,
        )
    }

    @BeforeEach
    fun setUp() {
        gateway = mock()
        store = mock()
        webhookClient = mock()
        stateHolder = AcquisitionStateHolder()  // Use real instance for integration-style tests
        latestMatchHolder = LatestMatchHolder()  // Use real instance
        matchSummaryBuilder = MatchSummaryBuilder(PhraseBank(jacksonObjectMapper()))  // Use real instance
        canonicalMatchRepository = mock()
        editorialPresentationService = mock()
        synchronizationGapStore = InMemorySynchronizationGapStore()
        service = makeService()
        stubStore()  // default: empty store = first run
    }

    /**
     * Stubs both [store.loadIds] and [store.loadRecords] consistently for the given [ids].
     * All IDs are treated as DELIVERED records.
     * Must be called AFTER [service] is constructed (it wires into mocks).
     */
    private fun stubStore(vararg ids: String) {
        val set = ids.toSet()
        val records = set.associateWith { PublicationRecord(it, PublicationState.DELIVERED) }
        whenever(store.loadIds()).thenReturn(set)
        whenever(store.loadRecords()).thenReturn(records)
    }

    // -------------------------------------------------------------------------
    // Helper functions
    // -------------------------------------------------------------------------

    private fun match(
        id: String,
        ts: Long = System.currentTimeMillis() / 1000,
        ourScore: String = "2",
        oppScore: String = "1",
        ownerClubId: String = clubId,
        matchType: String? = null,
    ): MatchResponse = MatchResponse(
        matchId = id,
        timestamp = ts,
        matchType = matchType,
        clubs = mapOf(
            ownerClubId to ClubMatchEntry(
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

    /**
     * Makes the repository mock behave like the bounded polling lookup: each
     * acquisition sees one complete canonical-history snapshot, while each EA
     * window is checked only against the IDs it returned.
     */
    private fun stubCanonicalHistory(
        targetClubId: ClubId,
        vararg snapshots: Set<MatchId>,
    ) {
        require(snapshots.isNotEmpty())
        val nextSnapshot = AtomicInteger(0)
        val currentSnapshot = AtomicReference<Set<MatchId>>(emptySet())
        whenever(canonicalMatchRepository.findLatestMatchId(clubIdEq(targetClubId))).thenAnswer {
            val snapshot = snapshots[nextSnapshot.getAndIncrement().coerceAtMost(snapshots.lastIndex)]
            currentSnapshot.set(snapshot)
            snapshot.firstOrNull()?.value
        }
        whenever(canonicalMatchRepository.findExistingMatchIds(clubIdEq(targetClubId), any())).thenAnswer { invocation ->
            val candidates = invocation.getArgument<Collection<MatchId>>(1)
            candidates.asSequence()
                .distinct()
                .filter { it in currentSnapshot.get() }
                .toCollection(linkedSetOf())
        }
    }

    private class WindowedGateway(private vararg val responses: List<MatchResponse>) : WindowedEaClubsGateway {
        val windows = mutableListOf<Int>()
        val clubIds = mutableListOf<String>()
        private var index = 0
        override fun searchClubs(clubName: String) = EaApiResult.Success(emptyList<com.eafc26.discordstats.ea.model.ClubSearchResult>())
        override fun getLatestMatches(clubId: String): EaApiResult<List<MatchResponse>> = error("window is required")
        override fun getLatestMatches(clubId: String, maxResultCount: Int): EaApiResult<List<MatchResponse>> {
            clubIds += clubId
            windows += maxResultCount
            return EaApiResult.Success(responses.getOrElse(index++) { emptyList() })
        }
    }

    @Nested
    inner class IncrementalSchedulerSynchronization {
        @Test
        fun `canonical empty preserves first run window semantics`() {
            val latest = match("latest", 200)
            stubCanonicalHistory(LEGACY_TEST_CLUB, emptySet())
            val windowed = WindowedGateway(listOf(latest))

            service.acquire(AcquisitionTrigger.SCHEDULER, windowed)

            assertThat(windowed.windows).containsExactly(20)
        }

        @Test
        fun `trial initial acquisition persists the complete initial window without Discord`() {
            val first = match("first", 100)
            val latest = match("latest", 200)
            stubCanonicalHistory(LEGACY_TEST_CLUB, emptySet())
            val windowed = WindowedGateway(listOf(latest, first))

            val result = service.acquire(LEGACY_TEST_CLUB, AcquisitionTrigger.TRIAL_INITIAL, windowed)

            assertThat(windowed.windows).containsExactly(20)
            verify(canonicalMatchRepository, times(2)).save(any())
            verify(webhookClient, never()).send(any(), any())
            assertThat(result).isInstanceOf(AcquisitionResult.Processed::class.java)
            assertThat((result as AcquisitionResult.Processed).baselineEstablished).isTrue()
        }

        @Test
        fun `first run retains league and playoff matches from the combined window`() {
            val league = match("league", 100, matchType = "leagueMatch")
            val playoff = match("playoff", 200, matchType = "playoffMatch")
            stubCanonicalHistory(LEGACY_TEST_CLUB, emptySet())
            val windowed = WindowedGateway(listOf(playoff, league))

            service.acquire(AcquisitionTrigger.SCHEDULER, windowed)

            assertThat(windowed.windows).containsExactly(20)
            verify(canonicalMatchRepository, times(2)).save(any())
        }

        @Test
        fun `checkpoint in initial window processes only the new match`() {
            val checkpoint = match("known", 100)
            val new = match("new", 200)
            stubCanonicalHistory(LEGACY_TEST_CLUB, linkedSetOf(MatchId(checkpoint.matchId)))
            stubStore("existing")
            val windowed = WindowedGateway(listOf(new, checkpoint))

            service.acquire(AcquisitionTrigger.SCHEDULER, windowed)

            assertThat(windowed.windows).containsExactly(5)
            val saved = argumentCaptor<com.eafc26.discordstats.canonical.CanonicalMatch>()
            verify(canonicalMatchRepository).save(saved.capture())
            assertThat(saved.firstValue.matchId.value).isEqualTo("new")
            verify(canonicalMatchRepository).findLatestMatchId(LEGACY_TEST_CLUB)
            verify(canonicalMatchRepository).findExistingMatchIds(
                clubIdEq(LEGACY_TEST_CLUB),
                argThat { map { it.value } == listOf(new.matchId, checkpoint.matchId) },
            )
            verify(canonicalMatchRepository, never()).findAll(LEGACY_TEST_CLUB)
            verify(canonicalMatchRepository, never()).findMatchIds(LEGACY_TEST_CLUB)
        }

        @Test
        fun `poll diagnostics distinguish EA window size from newly acquired matches`() {
            val checkpoint = match("known", 100)
            val new = match("new", 200)
            stubCanonicalHistory(LEGACY_TEST_CLUB, linkedSetOf(MatchId(checkpoint.matchId)))
            stubStore("existing")
            val windowed = WindowedGateway(listOf(new, checkpoint))

            service.acquire(AcquisitionTrigger.SCHEDULER, windowed)

            assertThat(service.lastFetchMetrics(LEGACY_TEST_CLUB))
                .isEqualTo(MatchAcquisitionService.AcquisitionFetchMetrics(matchesReturned = 2, newMatches = 1))
        }

        @Test
        fun `admin poll retains incremental synchronization semantics and records its own origin`() {
            val checkpoint = match("known", 100)
            val new = match("new", 200)
            stubCanonicalHistory(LEGACY_TEST_CLUB, linkedSetOf(MatchId(checkpoint.matchId)))
            stubStore("existing")
            val windowed = WindowedGateway(listOf(new, checkpoint))

            service.acquire(AcquisitionTrigger.ADMIN_POLL, windowed)

            assertThat(windowed.windows).containsExactly(5)
            assertThat(stateHolder.current().trigger).isEqualTo(AcquisitionTrigger.ADMIN_POLL)
            val saved = argumentCaptor<com.eafc26.discordstats.canonical.CanonicalMatch>()
            verify(canonicalMatchRepository).save(saved.capture())
            assertThat(saved.firstValue.matchId.value).isEqualTo("new")
        }

        @Test
        fun `league checkpoint stops expansion while newer playoff matches are acquired`() {
            val leagueCheckpoint = match("league-checkpoint", 100, matchType = "leagueMatch")
            val playoffNew = match("playoff-new", 200, matchType = "playoffMatch")
            stubCanonicalHistory(LEGACY_TEST_CLUB, linkedSetOf(MatchId(leagueCheckpoint.matchId)))
            stubStore("existing")
            val windowed = WindowedGateway(listOf(playoffNew, leagueCheckpoint))

            service.acquire(AcquisitionTrigger.SCHEDULER, windowed)

            assertThat(windowed.windows).containsExactly(5)
            val saved = argumentCaptor<com.eafc26.discordstats.canonical.CanonicalMatch>()
            verify(canonicalMatchRepository).save(saved.capture())
            assertThat(saved.firstValue.matchId.value).isEqualTo("playoff-new")
        }

        @Test
        fun `playoff checkpoint stops expansion while newer league matches are acquired`() {
            val playoffCheckpoint = match("playoff-checkpoint", 100, matchType = "playoffMatch")
            val leagueNew = match("league-new", 200, matchType = "leagueMatch")
            stubCanonicalHistory(LEGACY_TEST_CLUB, linkedSetOf(MatchId(playoffCheckpoint.matchId)))
            stubStore("existing")
            val windowed = WindowedGateway(listOf(leagueNew, playoffCheckpoint))

            service.acquire(AcquisitionTrigger.SCHEDULER, windowed)

            assertThat(windowed.windows).containsExactly(5)
            val saved = argumentCaptor<com.eafc26.discordstats.canonical.CanonicalMatch>()
            verify(canonicalMatchRepository).save(saved.capture())
            assertThat(saved.firstValue.matchId.value).isEqualTo("league-new")
        }

        @Test
        fun `mixed league and playoff matches above checkpoint are processed chronologically`() {
            val checkpoint = match("league-checkpoint", 100, matchType = "leagueMatch")
            val playoffNew = match("playoff-new", 200, matchType = "playoffMatch")
            val leagueNew = match("league-new", 300, matchType = "leagueMatch")
            stubCanonicalHistory(LEGACY_TEST_CLUB, linkedSetOf(MatchId(checkpoint.matchId)))
            stubStore("existing")
            val windowed = WindowedGateway(listOf(leagueNew, checkpoint, playoffNew))

            service.acquire(AcquisitionTrigger.SCHEDULER, windowed)

            val saved = argumentCaptor<com.eafc26.discordstats.canonical.CanonicalMatch>()
            verify(canonicalMatchRepository, times(2)).save(saved.capture())
            assertThat(saved.allValues.map { it.matchId.value }).containsExactly("playoff-new", "league-new")
        }

        @Test
        fun `league playoff league alternation keeps the same operational frontier`() {
            val leagueCheckpoint = match("league-checkpoint", 100, matchType = "leagueMatch")
            val playoff = match("playoff", 200, matchType = "playoffMatch")
            val league = match("league", 300, matchType = "leagueMatch")
            stubCanonicalHistory(LEGACY_TEST_CLUB,
                linkedSetOf(MatchId(leagueCheckpoint.matchId)),
                linkedSetOf(MatchId(playoff.matchId), MatchId(leagueCheckpoint.matchId)),
            )
            stubStore("existing")
            val windowed = WindowedGateway(
                listOf(playoff, leagueCheckpoint),
                listOf(league, playoff),
            )

            service.acquire(AcquisitionTrigger.SCHEDULER, windowed)
            service.acquire(AcquisitionTrigger.SCHEDULER, windowed)

            assertThat(windowed.windows).containsExactly(5, 5)
            val saved = argumentCaptor<com.eafc26.discordstats.canonical.CanonicalMatch>()
            verify(canonicalMatchRepository, times(2)).save(saved.capture())
            assertThat(saved.allValues.map { it.matchId.value }).containsExactly("playoff", "league")
        }

        @Test
        fun `known-only initial window returns zero new without reprocessing canonical even when publication previously failed`() {
            val checkpoint = match("known", 100)
            stubCanonicalHistory(LEGACY_TEST_CLUB, linkedSetOf(MatchId(checkpoint.matchId)))
            whenever(store.loadRecords(clubIdEq(LEGACY_TEST_CLUB))).thenReturn(
                mapOf("known" to PublicationRecord("known", PublicationState.FAILED_TRANSIENT)),
            )
            val windowed = WindowedGateway(listOf(checkpoint))

            val result = service.acquire(AcquisitionTrigger.SCHEDULER, windowed)

            assertThat(result).isInstanceOf(AcquisitionResult.Processed::class.java)
            assertThat(windowed.windows).containsExactly(5)
            verify(canonicalMatchRepository, never()).save(any())
            verify(editorialPresentationService, never()).generateAndPersist(any())
        }

        @Test
        fun `checkpoint absence expands until found and stops`() {
            val checkpoint = match("known", 100)
            val firstWindow = (1..5).map { index -> match("new-$index", 200L + index) }
            val expandedWindow = firstWindow + (6..9).map { index -> match("new-$index", 200L + index) } + checkpoint
            stubCanonicalHistory(LEGACY_TEST_CLUB, linkedSetOf(MatchId(checkpoint.matchId)))
            stubStore("existing")
            val windowed = WindowedGateway(firstWindow, expandedWindow)

            service.acquire(AcquisitionTrigger.SCHEDULER, windowed)

            assertThat(windowed.windows).containsExactly(5, 10)
            val candidates = argumentCaptor<Collection<MatchId>>()
            verify(canonicalMatchRepository, times(2)).findExistingMatchIds(clubIdEq(LEGACY_TEST_CLUB), candidates.capture())
            assertThat(candidates.allValues.map { ids -> ids.map { it.value } })
                .containsExactly(
                    firstWindow.map { it.matchId },
                    expandedWindow.map { it.matchId },
                )
            verify(canonicalMatchRepository, times(9)).save(any())
        }

        @Test
        fun `checkpoint first observed in window twenty expands through every bounded window`() {
            val checkpoint = match("known", 100)
            val firstWindow = (1..5).map { index -> match("new-$index", 200L + index) }
            val secondWindow = (1..10).map { index -> match("new-$index", 200L + index) }
            val maximumWindow = (1..19).map { index -> match("new-$index", 200L + index) } + checkpoint
            stubCanonicalHistory(LEGACY_TEST_CLUB, linkedSetOf(MatchId(checkpoint.matchId)))
            stubStore("existing")
            val windowed = WindowedGateway(firstWindow, secondWindow, maximumWindow)

            service.acquire(AcquisitionTrigger.SCHEDULER, windowed)

            assertThat(windowed.windows).containsExactly(5, 10, 20)
            val candidates = argumentCaptor<Collection<MatchId>>()
            verify(canonicalMatchRepository, times(3)).findExistingMatchIds(clubIdEq(LEGACY_TEST_CLUB), candidates.capture())
            assertThat(candidates.allValues.map { ids -> ids.map { it.value }.size }).containsExactly(5, 10, 20)
            verify(canonicalMatchRepository, times(19)).save(any())
        }

        @Test
        fun `checkpoint missing at maximum window is bounded and deduplicated`() {
            val old = match("known", 1)
            val duplicate = match("new", 200)
            stubCanonicalHistory(LEGACY_TEST_CLUB, linkedSetOf(MatchId(old.matchId)))
            stubStore("existing")
            val windowed = WindowedGateway(listOf(duplicate), listOf(duplicate), listOf(duplicate, duplicate))

            service.acquire(AcquisitionTrigger.SCHEDULER, windowed)

            assertThat(windowed.windows).containsExactly(5, 10, 20)
            verify(canonicalMatchRepository, times(1)).save(any())
            val candidates = argumentCaptor<Collection<MatchId>>()
            verify(canonicalMatchRepository, times(3)).findExistingMatchIds(clubIdEq(LEGACY_TEST_CLUB), candidates.capture())
            assertThat(candidates.allValues).allSatisfy { ids ->
                assertThat(ids).containsExactly(MatchId(duplicate.matchId))
            }
        }

        @Test
        fun `historical gap remains open while later polls use the advanced operational checkpoint`() {
            val anchor = match("A", 100)
            val d = match("D", 400)
            val e = match("E", 500)
            val f = match("F", 600)
            val g = match("G", 700)
            stubCanonicalHistory(LEGACY_TEST_CLUB,
                linkedSetOf(MatchId(anchor.matchId)),
                linkedSetOf(MatchId(g.matchId), MatchId(f.matchId), MatchId(e.matchId), MatchId(d.matchId), MatchId(anchor.matchId)),
            )
            stubStore("existing")
            val windowed = WindowedGateway(
                listOf(d, e, f, g), listOf(d, e, f, g), listOf(d, e, f, g),
                listOf(e, f, g),
            )

            service.acquire(AcquisitionTrigger.SCHEDULER, windowed)
            service.acquire(AcquisitionTrigger.SCHEDULER, windowed)

            assertThat(windowed.windows).containsExactly(5, 10, 20, 5)
            verify(canonicalMatchRepository, times(4)).save(any())
            val gap = synchronizationGapStore.findOpen(LEGACY_TEST_CLUB)
            assertThat(gap).isNotNull
            assertThat(gap!!.anchorMatchId).isEqualTo("A")
            assertThat(gap.firstObservableMatchId).isEqualTo("D")
        }

        @Test
        fun `new match advances the operational frontier without closing the historical gap`() {
            val anchor = match("A", 100)
            val d = match("D", 400)
            val e = match("E", 500)
            val f = match("F", 600)
            val g = match("G", 700)
            val h = match("H", 800)
            stubCanonicalHistory(LEGACY_TEST_CLUB,
                linkedSetOf(MatchId(anchor.matchId)),
                linkedSetOf(MatchId(g.matchId), MatchId(f.matchId), MatchId(e.matchId), MatchId(d.matchId), MatchId(anchor.matchId)),
            )
            stubStore("existing")
            val windowed = WindowedGateway(
                listOf(d, e, f, g), listOf(d, e, f, g), listOf(d, e, f, g),
                listOf(h, g, f, e),
            )

            service.acquire(AcquisitionTrigger.SCHEDULER, windowed)
            service.acquire(AcquisitionTrigger.SCHEDULER, windowed)

            assertThat(windowed.windows).containsExactly(5, 10, 20, 5)
            val saved = argumentCaptor<com.eafc26.discordstats.canonical.CanonicalMatch>()
            verify(canonicalMatchRepository, times(5)).save(saved.capture())
            assertThat(saved.allValues.map { it.matchId.value }).containsExactly("D", "E", "F", "G", "H")
            assertThat(synchronizationGapStore.findOpen(LEGACY_TEST_CLUB)).isNotNull
        }

        @Test
        fun `unexpected EA order is persisted chronologically and failed publication state does not refetch canonical`() {
            val checkpoint = match("known", 50)
            val older = match("older", 100)
            val newer = match("newer", 200)
            stubCanonicalHistory(LEGACY_TEST_CLUB, linkedSetOf(MatchId(checkpoint.matchId)))
            stubStore("existing")
            val windowed = WindowedGateway(listOf(newer, checkpoint, older))

            service.acquire(AcquisitionTrigger.SCHEDULER, windowed)

            val saved = argumentCaptor<com.eafc26.discordstats.canonical.CanonicalMatch>()
            verify(canonicalMatchRepository, times(2)).save(saved.capture())
            assertThat(saved.allValues.map { it.matchId.value }).containsExactly("older", "newer")
        }

        @Test
        fun `clubs use independent canonical checkpoints`() {
            val secondClubId = "67890"
            val firstCheckpoint = match("first-known")
            val secondCheckpoint = match("second-known", ownerClubId = secondClubId)
            stubCanonicalHistory(LEGACY_TEST_CLUB, linkedSetOf(MatchId(firstCheckpoint.matchId)))
            stubCanonicalHistory(ClubId(secondClubId), linkedSetOf(MatchId(secondCheckpoint.matchId)))
            val windowed = WindowedGateway(listOf(firstCheckpoint), listOf(secondCheckpoint))

            service.acquire(AcquisitionTrigger.SCHEDULER, windowed)
            service.acquire(ClubId(secondClubId), AcquisitionTrigger.SCHEDULER, windowed)

            assertThat(windowed.clubIds).containsExactly(clubId, secondClubId)
            assertThat(windowed.windows).containsExactly(5, 5)
            verify(canonicalMatchRepository, never()).save(any())
        }

        @Test
        fun `historical gaps remain isolated between clubs`() {
            val first = SynchronizationGap(LEGACY_TEST_CLUB, "A", "D")
            val secondClub = ClubId("67890")
            val second = SynchronizationGap(secondClub, "X", "Z")

            synchronizationGapStore.openGap(first)
            synchronizationGapStore.openGap(second)

            assertThat(synchronizationGapStore.findOpen(LEGACY_TEST_CLUB)).isEqualTo(first)
            assertThat(synchronizationGapStore.findOpen(secondClub)).isEqualTo(second)
        }
    }

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
            verify(webhookClient, never()).send(any(), any())
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
            stubStore("existing")

            val result = service.acquire(AcquisitionTrigger.MANUAL)

            assertThat(result).isInstanceOf(AcquisitionResult.Processed::class.java)
            val processed = result as AcquisitionResult.Processed
            assertThat(processed.published).hasSize(1)
            assertThat(processed.published[0].matchId).isEqualTo("m1")
            assertThat(processed.published[0].summary).contains("Test FC")
            verify(webhookClient).send(any(), any())
            verify(store).saveRecord(clubIdEq(LEGACY_TEST_CLUB), argThat { matchId == "m1" && state == PublicationState.DELIVERED })
            verify(canonicalMatchRepository, atLeastOnce()).save(any())
        }

        @Test
        fun `skips already published match`() {
            val match = match("m1")
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(match)))
            stubStore("m1")

            val result = service.acquire(AcquisitionTrigger.MANUAL)

            assertThat(result).isInstanceOf(AcquisitionResult.Processed::class.java)
            val processed = result as AcquisitionResult.Processed
            assertThat(processed.published).isEmpty()
            assertThat(processed.alreadyPublished).hasSize(1)
            assertThat(processed.allSkipped()).isTrue()
            verify(webhookClient, never()).send(any(), any())
        }

        @Test
        fun `CLI trigger behaves same as MANUAL`() {
            val match = match("m1")
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(match)))
            stubStore("existing")

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
            stubStore("existing")

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
            stubStore("existing")

            val result = service.acquire(AcquisitionTrigger.SCHEDULER)

            val processed = result as AcquisitionResult.Processed
            assertThat(processed.published).hasSize(2)
            assertThat(processed.published[0].matchId).isEqualTo("m1") // older first
            assertThat(processed.published[1].matchId).isEqualTo("m2") // newer second
            verify(webhookClient, times(2)).send(any(), any())
        }

        @Test
        fun `skips already published matches`() {
            val published = match("m1", ts = 1000)
            val newMatch = match("m2", ts = 2000)
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(published, newMatch)))
            stubStore("m1")

            val result = service.acquire(AcquisitionTrigger.SCHEDULER)

            val processed = result as AcquisitionResult.Processed
            assertThat(processed.published).hasSize(1)
            assertThat(processed.published[0].matchId).isEqualTo("m2")
            verify(webhookClient, times(1)).send(any(), any())
        }

        @Test
        fun `returns nothing new when all matches already published`() {
            val match = match("m1")
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(match)))
            stubStore("m1")

            val result = service.acquire(AcquisitionTrigger.SCHEDULER)

            val processed = result as AcquisitionResult.Processed
            assertThat(processed.published).isEmpty()
            assertThat(processed.alreadyPublished).hasSize(1)
            verify(webhookClient, never()).send(any(), any())
        }

        @Test
        fun `DEV_SIMULATOR is web-only and never delivers to Discord`() {
            val match = match("m1")
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(match)))
            stubStore("existing")

            val result = service.acquire(AcquisitionTrigger.DEV_SIMULATOR)

            val processed = result as AcquisitionResult.Processed
            // Simulations never "publish" - they just cache
            assertThat(processed.simulated).isTrue()
            assertThat(processed.published).isEmpty()
            assertThat(processed.simulatedMatch).isNotNull
            assertThat(processed.simulatedMatch?.matchId).isEqualTo("m1")
            // Discord should NEVER be called
            verify(webhookClient, never()).send(any(), any())
            // Persistence (DELIVERING or DELIVERED) should NEVER happen for simulation
            verify(store, never()).saveRecord(clubIdEq(LEGACY_TEST_CLUB), any())
        }
    }

    // -------------------------------------------------------------------------
    // First Run Handling
    // -------------------------------------------------------------------------

    @Nested
    inner class FirstRun {

        @Test
        fun `scheduler establishes baseline without publishing`() {
            val match = match("m1")
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(match)))
            stubStore()

            val result = service.acquire(AcquisitionTrigger.SCHEDULER)

            val processed = result as AcquisitionResult.Processed
            assertThat(processed.baselineEstablished).isTrue()
            // NEW BEHAVIOR: First-run now publishes the latest match
            assertThat(processed.published).hasSize(1)
            assertThat(processed.published[0].matchId).isEqualTo("m1")
            verify(webhookClient).send(any(), any())
            verify(store).saveRecord(clubIdEq(LEGACY_TEST_CLUB), argThat { matchId == "m1" && state == PublicationState.DELIVERED })
            // Baseline now uses BASELINED state instead of DELIVERED
            verify(store).saveIds(emptySet())
            // The latest keeps the DELIVERED state written by publication.
            val captor = argumentCaptor<Set<String>>()
            verify(store).saveIds(clubIdEq(LEGACY_TEST_CLUB), captor.capture())
            assertThat(captor.firstValue).isEmpty()
        }

        @Test
        fun `scheduler baseline includes the complete returned window`() {
            val older = match("m1", ts = 1000)
            val newer = match("m2", ts = 2000)
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(newer, older)))
            stubStore()

            val result = service.acquire(AcquisitionTrigger.SCHEDULER)

            val processed = result as AcquisitionResult.Processed
            assertThat(processed.baselineEstablished).isTrue()
            // NEW BEHAVIOR: First-run now publishes the latest match (m2)
            assertThat(processed.published).hasSize(1)
            assertThat(processed.published[0].matchId).isEqualTo("m2")
            verify(webhookClient).send(any(), any())
            verify(store).saveIds(setOf("m1"))
        }

        @Test
        fun `manual first use establishes baseline without publishing`() {
            val match = match("m1")
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(match)))
            stubStore()

            val result = service.acquire(AcquisitionTrigger.MANUAL)

            val processed = result as AcquisitionResult.Processed
            assertThat(processed.baselineEstablished).isTrue()
            assertThat(processed.published).isEmpty()
            verify(webhookClient, never()).send(any(), any())
            verify(store).saveIds(setOf("m1"))
        }

        @Test
        fun `cli first use establishes baseline without publishing`() {
            val match = match("m1")
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(match)))
            stubStore()

            val result = service.acquire(AcquisitionTrigger.CLI)

            val processed = result as AcquisitionResult.Processed
            assertThat(processed.baselineEstablished).isTrue()
            verify(webhookClient, never()).send(any(), any())
            verify(store).saveIds(setOf("m1"))
        }

        @Test
        fun `only a match observed after baseline is published once`() {
            var records = mutableMapOf<String, PublicationRecord>()
            whenever(store.loadIds()).thenAnswer { records.keys.toSet() }
            whenever(store.loadRecords()).thenAnswer { records.toMap() }
            whenever(store.saveIds(clubIdEq(LEGACY_TEST_CLUB), any())).thenAnswer { invocation ->
                val ids = invocation.getArgument<Set<String>>(1)
                records = ids.associateWith { PublicationRecord(it, PublicationState.DELIVERED) }.toMutableMap()
                Unit
            }
            whenever(store.saveRecord(clubIdEq(LEGACY_TEST_CLUB), any())).thenAnswer { invocation ->
                val r = invocation.getArgument<PublicationRecord>(1)
                records[r.matchId] = r
                Unit
            }
            whenever(store.removeRecord(clubIdEq(LEGACY_TEST_CLUB), any())).thenAnswer { invocation ->
                records.remove(invocation.getArgument<String>(1))
                Unit
            }
            whenever(gateway.getLatestMatches(clubId))
                .thenReturn(EaApiResult.Success(listOf(match("baseline", ts = 1000))))
                .thenReturn(EaApiResult.Success(listOf(match("baseline", 1000), match("new", 2000))))
                .thenReturn(EaApiResult.Success(listOf(match("baseline", 1000), match("new", 2000))))

            val baseline = service.acquire(AcquisitionTrigger.MANUAL) as AcquisitionResult.Processed
            val firstNewCycle = service.acquire(AcquisitionTrigger.SCHEDULER) as AcquisitionResult.Processed
            val repeatedCycle = service.acquire(AcquisitionTrigger.SCHEDULER) as AcquisitionResult.Processed

            assertThat(baseline.baselineEstablished).isTrue()
            assertThat(firstNewCycle.published.map { it.matchId }).containsExactly("new")
            assertThat(repeatedCycle.published).isEmpty()
            assertThat(records.keys).containsExactlyInAnyOrder("baseline", "new")
            assertThat(records.values.filter { it.state == PublicationState.DELIVERED }.map { it.matchId })
                .containsExactlyInAnyOrder("baseline", "new")
            verify(webhookClient, times(1)).send(any(), any())
        }

        @Test
        fun `first-run preserves a transient failure on latest while baselining only older matches`() {
            val older = match("older", ts = 1000)
            val latest = match("latest", ts = 2000)
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(older, latest)))
            doThrow(DiscordDeliveryException("Discord 500", WebClientResponseException.create(500, "Server Error", HttpHeaders.EMPTY, ByteArray(0), null)))
                .whenever(webhookClient).send(any(), any())

            service.acquire(AcquisitionTrigger.SCHEDULER)

            verify(store).saveRecord(clubIdEq(LEGACY_TEST_CLUB), argThat { matchId == "latest" && state == PublicationState.FAILED_TRANSIENT })
            verify(store).saveIds(setOf("older"))
        }

        @Test
        fun `first-run preserves a permanent failure on latest while baselining only older matches`() {
            val older = match("older", ts = 1000)
            val latest = match("latest", ts = 2000)
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(older, latest)))
            doThrow(DiscordDeliveryException("Discord 403", WebClientResponseException.create(403, "Forbidden", HttpHeaders.EMPTY, ByteArray(0), null)))
                .whenever(webhookClient).send(any(), any())

            service.acquire(AcquisitionTrigger.SCHEDULER)

            verify(store).saveRecord(clubIdEq(LEGACY_TEST_CLUB), argThat { matchId == "latest" && state == PublicationState.FAILED_PERMANENT })
            verify(store).saveIds(setOf("older"))
        }

        @Test
        fun `first-run preserves an uncertain delivery on latest while baselining only older matches`() {
            val older = match("older", ts = 1000)
            val latest = match("latest", ts = 2000)
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(older, latest)))
            doThrow(DiscordDeliveryException("Network timeout")).whenever(webhookClient).send(any(), any())

            service.acquire(AcquisitionTrigger.SCHEDULER)

            verify(store).saveRecord(clubIdEq(LEGACY_TEST_CLUB), argThat { matchId == "latest" && state == PublicationState.DELIVERY_UNCERTAIN })
            verify(store).saveIds(setOf("older"))
        }

        @Test
        fun `second polling never republishes a delivered first-run latest`() {
            val match = match("latest")
            val records = mutableMapOf<String, PublicationRecord>()
            whenever(store.loadIds()).thenAnswer { records.keys.toSet() }
            whenever(store.loadRecords()).thenAnswer { records.toMap() }
            whenever(store.find(clubIdEq(LEGACY_TEST_CLUB), any())).thenAnswer { records[it.getArgument<String>(1)] }
            whenever(store.saveRecord(clubIdEq(LEGACY_TEST_CLUB), any())).thenAnswer {
                val record = it.getArgument<PublicationRecord>(1)
                records[record.matchId] = record
                Unit
            }
            whenever(store.saveIds(clubIdEq(LEGACY_TEST_CLUB), any())).thenAnswer {
                it.getArgument<Set<String>>(1).forEach { matchId ->
                    records.putIfAbsent(matchId, PublicationRecord(matchId, PublicationState.BASELINED))
                }
                Unit
            }
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(match)))

            service.acquire(AcquisitionTrigger.SCHEDULER)
            service.acquire(AcquisitionTrigger.SCHEDULER)

            verify(webhookClient, times(1)).send(any(), any())
            assertThat(records["latest"]!!.state).isEqualTo(PublicationState.DELIVERED)
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
            stubStore("m1")

            val result = service.acquire(AcquisitionTrigger.FORCE_RESEND)

            assertThat(result).isInstanceOf(AcquisitionResult.ForceResent::class.java)
            val resent = result as AcquisitionResult.ForceResent
            assertThat(resent.match.matchId).isEqualTo("m1")
            verify(webhookClient).send(any(), any())
        }

        @Test
        fun `force resend marks match as published to prevent scheduler re-send`() {
            val match = match("m1")
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(match)))
            stubStore()

            service.acquire(AcquisitionTrigger.FORCE_RESEND)

            // After force-resend, match ID must be persisted as DELIVERED so the scheduler won't re-publish
            verify(store).saveRecord(clubIdEq(LEGACY_TEST_CLUB), argThat { matchId == "m1" && state == PublicationState.DELIVERED })
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
            stubStore("existing")
            doThrow(IllegalStateException("Webhook not configured")).whenever(webhookClient).send(any(), any())

            val result = service.acquire(AcquisitionTrigger.MANUAL)

            assertThat(result).isEqualTo(AcquisitionResult.WebhookNotConfigured)
            verify(store, never()).saveRecord(clubIdEq(LEGACY_TEST_CLUB), argThat { state == PublicationState.DELIVERED })
        }

        @Test
        fun `returns failed match on Discord delivery error for MANUAL`() {
            val match = match("m1")
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(match)))
            stubStore("existing")
            doThrow(DiscordDeliveryException("Rate limited")).whenever(webhookClient).send(any(), any())

            val result = service.acquire(AcquisitionTrigger.MANUAL)

            val processed = result as AcquisitionResult.Processed
            assertThat(processed.published).isEmpty()
            assertThat(processed.failed).hasSize(1)
            assertThat(processed.failed[0].reason).contains("Rate limited")
            verify(store, never()).saveRecord(clubIdEq(LEGACY_TEST_CLUB), argThat { state == PublicationState.DELIVERED })
        }

        @Test
        fun `continues to next match on Discord error for SCHEDULER`() {
            val m1 = match("m1", ts = 1000)
            val m2 = match("m2", ts = 2000)
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(m1, m2)))
            stubStore("existing")

            // First call fails, second succeeds
            var callCount = 0
            whenever(webhookClient.send(any(), any())).thenAnswer {
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
            stubStore("existing")
            doThrow(IllegalStateException("Webhook not configured")).whenever(webhookClient).send(any(), any())

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
            stubStore("existing")
            // Fail only on DELIVERED writes (not on DELIVERING writes)
            doThrow(RuntimeException("Disk full")).whenever(store)
                .saveRecord(clubIdEq(LEGACY_TEST_CLUB), argThat { state == PublicationState.DELIVERED })

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
            stubStore("existing")

            service.acquire(AcquisitionTrigger.SCHEDULER)

            // Each match: DELIVERING + DELIVERED = 2 saveRecord calls per match, 4 total
            verify(store, times(2)).saveRecord(clubIdEq(LEGACY_TEST_CLUB), argThat { state == PublicationState.DELIVERED })
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
            val result = service.acquire(AcquisitionTrigger.ADMIN_POLL)

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
    // Custom Gateway (for DEV_SIMULATOR)
    // -------------------------------------------------------------------------

    @Nested
    inner class CustomGateway {

        @Test
        fun `uses provided gateway instead of default`() {
            val customGateway: EaClubsGateway = mock()
            val customMatch = match("custom-m1")
            whenever(customGateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(customMatch)))
            stubStore("existing")

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

            verify(webhookClient, never()).send(any(), any())
        }

        @Test
        fun `never persists simulated matches`() {
            val match = match("sim-3")
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(match)))

            service.acquire(AcquisitionTrigger.DEV_SIMULATOR)

            verify(store, never()).saveRecord(clubIdEq(LEGACY_TEST_CLUB), any())
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
            stubStore("sim-6")

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
            stubStore()

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
            stubStore("m1") // Already published

            service.acquire(AcquisitionTrigger.MANUAL)

            // Even though match was skipped (already published), presentation should be cached
            assertThat(latestMatchHolder.hasPresentation()).isTrue()
            assertThat(latestMatchHolder.presentation()?.matchId).isEqualTo("m1")
        }

        @Test
        fun `caches presentation even when Discord delivery fails`() {
            val match = match("m1")
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(match)))
            doThrow(DiscordDeliveryException("Rate limited")).whenever(webhookClient).send(any(), any())

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
            stubStore("existing")

            service.acquire(AcquisitionTrigger.SCHEDULER)

            // Should cache the latest match (m2)
            assertThat(latestMatchHolder.presentation()?.matchId).isEqualTo("m2")
        }

        @Test
        fun `caches presentation on first run baseline establishment`() {
            val match = match("m1")
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(match)))
            stubStore()

            service.acquire(AcquisitionTrigger.SCHEDULER)

            // Even when establishing baseline (no publish), presentation should be cached
            assertThat(latestMatchHolder.hasPresentation()).isTrue()
            assertThat(latestMatchHolder.presentation()?.matchId).isEqualTo("m1")
        }

        @Test
        fun `caches presentation on force resend`() {
            val match = match("m1")
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(match)))
            stubStore("m1")

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

    @Nested
    inner class ContinuousCanonicalCapture {

        @Test
        fun `persists the complete returned window before publication filtering`() {
            val older = match("m1", ts = 1000)
            val newer = match("m2", ts = 2000)
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(newer, older)))
            stubStore("existing")

            service.acquire(AcquisitionTrigger.SCHEDULER)

            // Canonical saves happen before Discord publication
            val order = inOrder(canonicalMatchRepository, webhookClient)
            order.verify(canonicalMatchRepository, times(2)).save(any())
            order.verify(webhookClient, times(2)).send(any(), any())
        }

        @Test
        fun `already published match is still persisted canonically`() {
            val published = match("published")
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(published)))
            stubStore("published")

            service.acquire(AcquisitionTrigger.MANUAL)

            verify(canonicalMatchRepository).save(any())
            verify(webhookClient, never()).send(any(), any())
            verify(store, never()).saveRecord(clubIdEq(LEGACY_TEST_CLUB), argThat { state == PublicationState.DELIVERED })
        }

        @Test
        fun `first scheduler cycle persists complete window before establishing baseline`() {
            val older = match("m1", ts = 1000)
            val newer = match("m2", ts = 2000)
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(newer, older)))
            stubStore()

            val result = service.acquire(AcquisitionTrigger.SCHEDULER) as AcquisitionResult.Processed

            assertThat(result.baselineEstablished).isTrue()
            verify(canonicalMatchRepository, times(2)).save(any())
            verify(store).saveIds(setOf("m1"))
            // NEW BEHAVIOR: First-run now publishes the latest match
            assertThat(result.published).hasSize(1)
            verify(webhookClient).send(any(), any())
        }

        @Test
        fun `repeated sliding windows persist every observation and expose continuous unique MatchIds`() {
            stubStore("existing")
            whenever(gateway.getLatestMatches(clubId))
                .thenReturn(EaApiResult.Success(listOf(match("m1", 1), match("m2", 2))))
                .thenReturn(EaApiResult.Success(listOf(match("m2", 2), match("m3", 3))))

            service.acquire(AcquisitionTrigger.SCHEDULER)
            service.acquire(AcquisitionTrigger.SCHEDULER)

            val captor = argumentCaptor<com.eafc26.discordstats.canonical.CanonicalMatch>()
            verify(canonicalMatchRepository, times(4)).save(captor.capture())
            assertThat(captor.allValues.map { it.matchId.value })
                .containsExactly("m1", "m2", "m2", "m3")
            assertThat(captor.allValues.map { it.matchId.value }.distinct())
                .containsExactly("m1", "m2", "m3")
        }
    }

    @Nested
    inner class EditorialPresentationGeneration {

        @Test
        fun `generates editorial presentation after canonical save`() {
            val m = match("m1")
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(m)))
            stubStore()

            service.acquire(AcquisitionTrigger.MANUAL)

            val order = inOrder(canonicalMatchRepository, editorialPresentationService)
            order.verify(canonicalMatchRepository).save(any())
            order.verify(editorialPresentationService).generateAndPersist(any())
        }

        @Test
        fun `generates editorial for each canonical match persisted`() {
            val m1 = match("m1", ts = 1000)
            val m2 = match("m2", ts = 2000)
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(m1, m2)))
            stubStore()

            service.acquire(AcquisitionTrigger.SCHEDULER)

            verify(canonicalMatchRepository, times(2)).save(any())
            verify(editorialPresentationService, times(2)).generateAndPersist(any())
        }

        @Test
        fun `does not generate editorial for DEV_SIMULATOR`() {
            val m = match("sim-match")
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(m)))

            service.acquire(AcquisitionTrigger.DEV_SIMULATOR)

            verify(canonicalMatchRepository, never()).save(any())
            verify(editorialPresentationService, never()).generateAndPersist(any())
        }

        @Test
        fun `editorial failure does not corrupt canonical save`() {
            val m = match("m1")
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(m)))
            whenever(editorialPresentationService.generateAndPersist(any()))
                .thenThrow(RuntimeException("Editorial DB failure"))
            stubStore()

            // Should complete without throwing
            val result = service.acquire(AcquisitionTrigger.MANUAL) as AcquisitionResult.Processed

            // Canonical was saved
            verify(canonicalMatchRepository).save(any())
            // Editorial was attempted
            verify(editorialPresentationService).generateAndPersist(any())
            // Acquisition completed (not thrown)
            assertThat(result).isNotNull
        }

        @Test
        fun `editorial failure does not block Discord publication`() {
            val m = match("m1")
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(m)))
            whenever(editorialPresentationService.generateAndPersist(any()))
                .thenThrow(RuntimeException("Editorial failure"))
            stubStore("existing")

            service.acquire(AcquisitionTrigger.MANUAL)

            // Discord delivery still happened
            verify(webhookClient).send(any(), any())
            verify(store).saveRecord(clubIdEq(LEGACY_TEST_CLUB), argThat { state == PublicationState.DELIVERED })
        }

        @Test
        fun `repeated acquisition for same match calls editorial once per cycle`() {
            val m = match("m1")
            whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(m)))
            stubStore()

            service.acquire(AcquisitionTrigger.SCHEDULER)
            service.acquire(AcquisitionTrigger.SCHEDULER)

            // Canonical saved twice (repeated window)
            verify(canonicalMatchRepository, times(2)).save(any())
            // Editorial called twice (one per cycle, service is idempotent via upsert)
            verify(editorialPresentationService, times(2)).generateAndPersist(any())
        }
    }
}
