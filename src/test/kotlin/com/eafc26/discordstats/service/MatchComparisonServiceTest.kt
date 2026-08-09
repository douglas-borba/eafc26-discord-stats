package com.eafc26.discordstats.service

import com.eafc26.discordstats.application.interpretation.MatchInterpreter
import com.eafc26.discordstats.application.story.MatchStoryExtractor
import com.eafc26.discordstats.canonical.CanonicalMatch
import com.eafc26.discordstats.comparison.ComparisonMetric
import com.eafc26.discordstats.comparison.MatchComparisonResult
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.MatchId
import com.eafc26.discordstats.domain.story.StoryType
import com.eafc26.discordstats.ea.mapping.EaMatchMapper
import com.eafc26.discordstats.ea.mapping.MatchNormalizationResult
import com.eafc26.discordstats.ea.model.ClubDetails
import com.eafc26.discordstats.ea.model.ClubMatchEntry
import com.eafc26.discordstats.ea.model.MatchResponse
import com.eafc26.discordstats.ea.model.PlayerEntry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

class MatchComparisonServiceTest {
    private lateinit var history: MatchHistoryService
    private lateinit var service: MatchComparisonService

    @BeforeEach
    fun setUp() {
        history = mock()
        service = MatchComparisonService(history)
    }

    @Test
    fun `compares two canonical matches and produces structured differences`() {
        val first = canonical("first", 1_801_000_000L, 3, 1, "8.0", 2, 1, 4, 18, 20, 3, 4, 0)
        val second = canonical("second", 1_802_000_000L, 1, 2, "6.0", 1, 0, 2, 12, 20, 1, 3, 1)
        whenever(history.findById(OUR_CLUB, first.matchId)).thenReturn(first)
        whenever(history.findById(OUR_CLUB, second.matchId)).thenReturn(second)

        val comparison = (service.compare(OUR_CLUB, first.matchId, second.matchId) as MatchComparisonResult.Success).comparison

        assertThat(comparison.first.ourScore).isEqualTo(3)
        assertThat(comparison.second.ourScore).isEqualTo(1)
        assertThat(comparison.first.statistics.averageRating).isEqualByComparingTo("8.000000")
        assertThat(comparison.second.statistics.averageRating).isEqualByComparingTo("6.000000")
        assertThat(comparison.differences.numeric.single { it.metric == ComparisonMetric.GOALS_SCORED }.delta)
            .isEqualByComparingTo("-2")
        assertThat(comparison.differences.numeric.single { it.metric == ComparisonMetric.TEAM_ASSISTS }.delta)
            .isEqualByComparingTo("-1")
        assertThat(comparison.differences.numeric.single { it.metric == ComparisonMetric.PASS_ACCURACY_PERCENT }.delta)
            .isEqualByComparingTo("-30.000000")
        assertThat(comparison.differences.numeric.single { it.metric == ComparisonMetric.RED_CARDS }.delta)
            .isEqualByComparingTo("1")
        assertThat(comparison.differences.numeric.single { it.metric == ComparisonMetric.POSSESSION_PERCENT }.delta)
            .isNull()
        assertThat(comparison.differences.awards).hasSize(3)
        assertThat(comparison.differences.stories).hasSize(StoryType.entries.size)
    }

    @Test
    fun `same match compared with itself has zero available differences`() {
        val canonical = canonical("same", 1_801_000_000L, 3, 1, "8.0", 2, 1, 4, 18, 20, 3, 4, 0)
        whenever(history.findById(OUR_CLUB, canonical.matchId)).thenReturn(canonical)

        val comparison = (service.compare(OUR_CLUB, canonical.matchId, canonical.matchId) as MatchComparisonResult.Success).comparison

        assertThat(comparison.differences.numeric.filter { it.delta != null })
            .allMatch { it.delta!!.signum() == 0 }
        assertThat(comparison.differences.awards).allMatch { !it.changed }
        assertThat(comparison.differences.stories).allMatch { it.delta == 0 }
        verify(history).findById(OUR_CLUB, canonical.matchId)
    }

    @Test
    fun `reports every missing MatchId`() {
        val first = MatchId("missing-first")
        val second = MatchId("missing-second")
        whenever(history.findById(OUR_CLUB, first)).thenReturn(null)
        whenever(history.findById(OUR_CLUB, second)).thenReturn(null)

        val result = service.compare(OUR_CLUB, first, second) as MatchComparisonResult.NotFound

        assertThat(result.missingMatchIds).containsExactlyInAnyOrder(first, second)
    }

    @Test
    fun `comparison supports canonical matches without optional narrative stories`() {
        val first = canonicalWithoutPlayers("empty-one", 1_801_000_000L)
        val second = canonicalWithoutPlayers("empty-two", 1_802_000_000L)
        whenever(history.findById(OUR_CLUB, first.matchId)).thenReturn(first)
        whenever(history.findById(OUR_CLUB, second.matchId)).thenReturn(second)

        val comparison = (service.compare(OUR_CLUB, first.matchId, second.matchId) as MatchComparisonResult.Success).comparison

        assertThat(comparison.first.stories.map { it.story.type }).containsExactly(StoryType.MATCH_OUTCOME)
        assertThat(comparison.second.stories.map { it.story.type }).containsExactly(StoryType.MATCH_OUTCOME)
        assertThat(comparison.differences.stories.single { it.storyType == StoryType.OFFENSIVE_NARRATIVE }.delta)
            .isZero()
    }

    @Test
    fun `options preserve history order and canonical result summary`() {
        val recent = canonical("recent", 1_802_000_000L, 3, 1, "8.0", 2, 1, 4, 18, 20, 3, 4, 0)
        val old = canonical("old", 1_801_000_000L, 1, 2, "6.0", 1, 0, 2, 12, 20, 1, 3, 1)
        whenever(history.list(OUR_CLUB)).thenReturn(listOf(recent, old))

        val options = service.listOptions(OUR_CLUB)

        assertThat(options.map { it.matchId.value }).containsExactly("recent", "old")
        assertThat(options.first().ourScore).isEqualTo(3)
        assertThat(options.first().opponentClubName).isEqualTo("Opponent FC")
        verify(history, never()).findById(OUR_CLUB, recent.matchId)
    }

    @Test
    fun `comparison never resolves either match outside the requested club`() {
        val otherClub = ClubId("other-club")
        val first = MatchId("first")
        val second = MatchId("second")
        whenever(history.findById(OUR_CLUB, first)).thenReturn(null)
        whenever(history.findById(OUR_CLUB, second)).thenReturn(null)

        val result = service.compare(OUR_CLUB, first, second)

        assertThat(result).isInstanceOf(MatchComparisonResult.NotFound::class.java)
        verify(history).findById(OUR_CLUB, first)
        verify(history).findById(OUR_CLUB, second)
        verify(history, never()).findById(otherClub, first)
        verify(history, never()).findById(otherClub, second)
    }

    private fun canonical(
        id: String,
        timestamp: Long,
        ourScore: Int,
        opponentScore: Int,
        rating: String,
        goals: Int,
        assists: Int,
        shots: Int,
        passesCompleted: Int,
        passesAttempted: Int,
        tacklesCompleted: Int,
        tacklesAttempted: Int,
        redCards: Int,
    ): CanonicalMatch = canonical(
        MatchResponse(
            matchId = id,
            timestamp = timestamp,
            matchType = "leagueMatch",
            clubs = clubs(ourScore, opponentScore),
            players = mapOf(
                OUR_CLUB.value to linkedMapOf(
                    "player" to PlayerEntry(
                        playerName = "Player",
                        position = "14",
                        rating = rating,
                        goals = goals.toString(),
                        assists = assists.toString(),
                        shots = shots.toString(),
                        manOfTheMatch = "1",
                        passesMade = passesCompleted.toString(),
                        passAttempts = passesAttempted.toString(),
                        tacklesMade = tacklesCompleted.toString(),
                        tackleAttempts = tacklesAttempted.toString(),
                        redCards = redCards.toString(),
                        secondsPlayed = "5400",
                    )
                )
            ),
        )
    )

    private fun canonicalWithoutPlayers(id: String, timestamp: Long): CanonicalMatch =
        canonical(
            MatchResponse(
                matchId = id,
                timestamp = timestamp,
                matchType = "leagueMatch",
                clubs = clubs(0, 0),
                players = emptyMap(),
            )
        )

    private fun canonical(source: MatchResponse): CanonicalMatch {
        val footballMatch = (EaMatchMapper().map(source) as MatchNormalizationResult.Success).match
        val interpretation = MatchInterpreter().interpret(footballMatch, OUR_CLUB)
        return CanonicalMatch.current(
            footballMatch,
            interpretation,
            MatchStoryExtractor().extract(interpretation),
            Instant.parse("2026-07-30T10:00:00Z"),
        )
    }

    private fun clubs(ourScore: Int, opponentScore: Int) = linkedMapOf(
        OUR_CLUB.value to ClubMatchEntry(
            details = ClubDetails("Our FC", OUR_CLUB.value),
            score = ourScore.toString(),
            result = if (ourScore > opponentScore) "1" else if (ourScore == opponentScore) "2" else "0",
        ),
        "opponent" to ClubMatchEntry(
            details = ClubDetails("Opponent FC", "opponent"),
            score = opponentScore.toString(),
            result = if (opponentScore > ourScore) "1" else if (ourScore == opponentScore) "2" else "0",
        ),
    )

    private companion object {
        val OUR_CLUB = ClubId("our-club")
    }
}
