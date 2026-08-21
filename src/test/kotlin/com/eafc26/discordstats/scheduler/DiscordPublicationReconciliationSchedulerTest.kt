package com.eafc26.discordstats.scheduler

import com.eafc26.discordstats.application.repository.CanonicalMatchRepository
import com.eafc26.discordstats.canonical.CanonicalMatch
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.MatchId
import com.eafc26.discordstats.discord.DiscordDestination
import com.eafc26.discordstats.ea.model.ClubDetails
import com.eafc26.discordstats.ea.model.ClubMatchEntry
import com.eafc26.discordstats.ea.model.MatchResponse
import com.eafc26.discordstats.service.CanonicalMatchFactory
import com.eafc26.discordstats.service.ClaimedDiscordPublication
import com.eafc26.discordstats.service.DiscordMatchPublicationService
import com.eafc26.discordstats.service.DiscordPublicationResult
import com.eafc26.discordstats.service.PublicationOutcome
import com.eafc26.discordstats.store.PostgresPublishedMatchStore
import com.eafc26.discordstats.store.PublicationRecord
import com.eafc26.discordstats.store.PublicationState
import com.eafc26.discordstats.store.PublicationWorkCandidate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class DiscordPublicationReconciliationSchedulerTest {
    private val clubId = ClubId("club-a")
    private val now = Instant.parse("2026-08-21T12:00:00Z")

    private lateinit var publicationStore: PostgresPublishedMatchStore
    private lateinit var canonicalMatches: CanonicalMatchRepository
    private lateinit var publicationService: DiscordMatchPublicationService
    private lateinit var status: PublicationReconciliationStatusHolder
    private lateinit var scheduler: DiscordPublicationReconciliationScheduler

    @BeforeEach
    fun setUp() {
        publicationStore = mock()
        canonicalMatches = mock()
        publicationService = mock()
        status = PublicationReconciliationStatusHolder()
        scheduler = DiscordPublicationReconciliationScheduler(
            publicationStore,
            canonicalMatches,
            publicationService,
            status,
            Clock.fixed(now, ZoneOffset.UTC),
        )
    }

    @Test
    fun `does not load canonical payload when the atomic claim is not acquired`() {
        val candidate = candidate("pending", PublicationState.PENDING)
        whenever(publicationStore.findAutomaticPublicationCandidates(now, 20)).thenReturn(listOf(candidate))
        whenever(publicationService.claimForReconciliation(clubId, candidate.record)).thenReturn(null)

        scheduler.reconcile()

        verify(canonicalMatches, never()).findById(clubId, MatchId("pending"))
        assertThat(status.current()).extracting(
            PublicationReconciliationStatus::claimed,
            PublicationReconciliationStatus::completed,
            PublicationReconciliationStatus::failed,
        ).containsExactly(0, 0, 0)
    }

    @Test
    fun `loads only the atomically claimed canonical match and not full history`() {
        val candidate = candidate("pending", PublicationState.PENDING)
        val claim = claimFor(candidate.record)
        val canonical = canonical("pending")
        whenever(publicationStore.findAutomaticPublicationCandidates(now, 20)).thenReturn(listOf(candidate))
        whenever(publicationService.claimForReconciliation(clubId, candidate.record)).thenReturn(claim)
        whenever(canonicalMatches.findById(clubId, MatchId("pending"))).thenReturn(canonical)
        whenever(publicationService.deliverReconciliationClaim(canonical, claim))
            .thenReturn(DiscordPublicationResult(PublicationOutcome.PUBLISHED, "pending"))

        scheduler.reconcile()

        verify(canonicalMatches).findById(clubId, MatchId("pending"))
        verify(canonicalMatches, never()).findAll(clubId)
        assertThat(status.current()).extracting(
            PublicationReconciliationStatus::claimed,
            PublicationReconciliationStatus::completed,
            PublicationReconciliationStatus::failed,
        ).containsExactly(1, 1, 0)
    }

    @Test
    fun `one recovery failure is isolated and does not abandon later safe work`() {
        val first = candidate("older", PublicationState.PENDING, playedAt = now.minusSeconds(60))
        val second = candidate("newer", PublicationState.PENDING, playedAt = now)
        val firstClaim = claimFor(first.record)
        val secondClaim = claimFor(second.record)
        val firstCanonical = canonical("older")
        val secondCanonical = canonical("newer")
        whenever(publicationStore.findAutomaticPublicationCandidates(now, 20)).thenReturn(listOf(first, second))
        whenever(publicationService.claimForReconciliation(clubId, first.record)).thenReturn(firstClaim)
        whenever(publicationService.claimForReconciliation(clubId, second.record)).thenReturn(secondClaim)
        whenever(canonicalMatches.findById(clubId, MatchId("older"))).thenReturn(firstCanonical)
        whenever(canonicalMatches.findById(clubId, MatchId("newer"))).thenReturn(secondCanonical)
        whenever(publicationService.deliverReconciliationClaim(firstCanonical, firstClaim))
            .thenThrow(IllegalStateException("renderer failure"))
        whenever(publicationService.deliverReconciliationClaim(secondCanonical, secondClaim))
            .thenReturn(DiscordPublicationResult(PublicationOutcome.PUBLISHED, "newer"))

        scheduler.reconcile()

        verify(publicationService).failClaimAmbiguously(
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any(),
        )
        verify(publicationService).deliverReconciliationClaim(secondCanonical, secondClaim)
        assertThat(status.current()).extracting(
            PublicationReconciliationStatus::claimed,
            PublicationReconciliationStatus::completed,
            PublicationReconciliationStatus::failed,
        ).containsExactly(2, 1, 1)
    }

    private fun candidate(
        matchId: String,
        state: PublicationState,
        playedAt: Instant = now,
    ): PublicationWorkCandidate = PublicationWorkCandidate(
        clubId,
        PublicationRecord(matchId, state),
        playedAt,
    )

    private fun claimFor(record: PublicationRecord): ClaimedDiscordPublication = ClaimedDiscordPublication(
        clubId = clubId,
        previous = record,
        claimed = record.copy(
            state = PublicationState.DELIVERING,
            attemptCount = record.attemptCount + 1,
            lastAttemptAt = now.epochSecond,
        ),
        destination = DiscordDestination("https://discord.test/webhook"),
    )

    private fun canonical(matchId: String): CanonicalMatch = CanonicalMatchFactory().create(
        source = MatchResponse(
            matchId = matchId,
            timestamp = now.epochSecond,
            clubs = mapOf(
                clubId.value to ClubMatchEntry(ClubDetails(name = "Club A"), score = "2", result = "1"),
                "opponent" to ClubMatchEntry(ClubDetails(name = "Opponent"), score = "1", result = "0"),
            ),
            players = emptyMap(),
        ),
        perspectiveClubId = clubId.value,
    )
}
