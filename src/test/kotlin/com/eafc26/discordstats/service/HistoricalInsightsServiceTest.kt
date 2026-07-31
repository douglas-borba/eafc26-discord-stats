package com.eafc26.discordstats.service

import com.eafc26.discordstats.application.interpretation.MatchInterpreter
import com.eafc26.discordstats.application.story.MatchStoryExtractor
import com.eafc26.discordstats.canonical.CanonicalMatch
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.MatchId
import com.eafc26.discordstats.domain.match.PlayerId
import com.eafc26.discordstats.ea.mapping.EaMatchMapper
import com.eafc26.discordstats.ea.mapping.MatchNormalizationResult
import com.eafc26.discordstats.ea.model.ClubDetails
import com.eafc26.discordstats.ea.model.ClubMatchEntry
import com.eafc26.discordstats.ea.model.MatchResponse
import com.eafc26.discordstats.ea.model.PlayerEntry
import com.eafc26.discordstats.history.MatchHistoryOrder
import com.eafc26.discordstats.history.MatchHistoryQuery
import com.eafc26.discordstats.insight.HistoricalInsightType
import com.eafc26.discordstats.insight.HistoricalInsightValue
import com.eafc26.discordstats.profile.PlayerProfile
import com.eafc26.discordstats.profile.PlayerProfileIndexEntry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant

class HistoricalInsightsServiceTest {
    private lateinit var history: MatchHistoryService
    private lateinit var profiles: PlayerProfileService
    private lateinit var service: HistoricalInsightsService

    @BeforeEach
    fun setUp() {
        history = mock()
        profiles = mock()
        service = HistoricalInsightsService(history, profiles)
    }

    @Test
    fun `empty history produces no insights and avoids player aggregation`() {
        whenever(history.list(OLDEST_FIRST)).thenReturn(emptyList())

        val report = service.generate()

        assertThat(report.sourceMatchCount).isZero()
        assertThat(report.insights).isEmpty()
        verify(profiles, never()).listPlayers()
    }

    @Test
    fun `single match produces deterministic club and temporal facts`() {
        val match = canonical("only", "2026-07-01T10:00:00Z", 3, 0, "8.0")
        whenever(history.list(OLDEST_FIRST)).thenReturn(listOf(match))
        whenever(profiles.listPlayers()).thenReturn(emptyList())

        val report = service.generate()

        assertCount(report, HistoricalInsightType.LONGEST_WINNING_STREAK, 1)
        assertCount(report, HistoricalInsightType.LONGEST_UNBEATEN_STREAK, 1)
        assertCount(report, HistoricalInsightType.LONGEST_CONCEDING_STREAK, null)
        assertThat(report.insights.single { it.type == HistoricalInsightType.FIRST_WIN }.involvedMatchIds)
            .containsExactly(MatchId("only"))
        assertThat(report.insights.single { it.type == HistoricalInsightType.LATEST_WIN }.involvedMatchIds)
            .containsExactly(MatchId("only"))
        assertThat(
            (report.insights.single { it.type == HistoricalInsightType.LONGEST_UNBEATEN_INTERVAL }.value
                as HistoricalInsightValue.DurationSeconds).value
        ).isZero()
    }

    @Test
    fun `streak rating temporal and biggest-win criteria use canonical chronology`() {
        val matches = listOf(
            canonical("m1", "2026-07-01T10:00:00Z", 3, 0, "8.0"),
            canonical("m2", "2026-07-02T10:00:00Z", 0, 0, "7.0"),
            canonical("m3", "2026-07-03T10:00:00Z", 0, 2, "6.0"),
            canonical("m4", "2026-07-04T10:00:00Z", 4, 1, "8.0"),
            canonical("m5", "2026-07-05T10:00:00Z", 2, 1, "9.0"),
        )
        whenever(history.list(OLDEST_FIRST)).thenReturn(matches)
        whenever(profiles.listPlayers()).thenReturn(emptyList())

        val report = service.generate()

        assertCount(report, HistoricalInsightType.LONGEST_WINNING_STREAK, 2)
        assertCount(report, HistoricalInsightType.LONGEST_UNBEATEN_STREAK, 2)
        assertCount(report, HistoricalInsightType.LONGEST_SCORELESS_STREAK, 2)
        assertCount(report, HistoricalInsightType.LONGEST_CONCEDING_STREAK, 3)
        assertThat(rating(report, HistoricalInsightType.BEST_TEAM_AVERAGE)).isEqualByComparingTo("9.000000")
        assertThat(rating(report, HistoricalInsightType.WORST_TEAM_AVERAGE)).isEqualByComparingTo("6.000000")
        assertThat(report.insights.single { it.type == HistoricalInsightType.FIRST_WIN }.involvedMatchIds)
            .containsExactly(MatchId("m1"))
        assertThat(report.insights.single { it.type == HistoricalInsightType.LATEST_WIN }.involvedMatchIds)
            .containsExactly(MatchId("m5"))
        assertThat(report.insights.single { it.type == HistoricalInsightType.BIGGEST_WIN }.involvedMatchIds)
            .containsExactly(MatchId("m1"), MatchId("m4"))
    }

    @Test
    fun `ties preserve all player leaders in stable order`() {
        val match = canonical("m1", "2026-07-01T10:00:00Z", 1, 0, "8.0")
        val ana = profile("ana", "Ana", matches = 3, goals = 4, assists = 2, craques = 2, average = "8.0", rated = 3)
        val bia = profile("bia", "Bia", matches = 3, goals = 4, assists = 2, craques = 2, average = "8.0", rated = 3)
        whenever(history.list(OLDEST_FIRST)).thenReturn(listOf(match))
        whenever(profiles.listPlayers()).thenReturn(listOf(index(bia), index(ana)))
        whenever(profiles.findById(ana.playerId)).thenReturn(ana)
        whenever(profiles.findById(bia.playerId)).thenReturn(bia)

        val report = service.generate()

        val scorers = report.insights.single { it.type == HistoricalInsightType.TOP_SCORER }
        assertThat(scorers.involvedPlayers.map { it.displayName }).containsExactly("Ana", "Bia")
        assertThat(scorers.evidence.eligibleCandidateCount).isEqualTo(2)
        val averages = report.insights.single { it.type == HistoricalInsightType.HIGHEST_PLAYER_AVERAGE }
        assertThat(averages.involvedPlayers.map { it.playerId.value }).containsExactly("ana", "bia")
    }

    @Test
    fun `highest player average enforces minimum rated matches`() {
        val match = canonical("m1", "2026-07-01T10:00:00Z", 1, 0, "8.0")
        val ineligible = profile("few", "Few", matches = 2, average = "9.5", rated = 2)
        val eligible = profile("enough", "Enough", matches = 3, average = "8.2", rated = 3)
        whenever(history.list(OLDEST_FIRST)).thenReturn(listOf(match))
        whenever(profiles.listPlayers()).thenReturn(listOf(index(ineligible), index(eligible)))
        whenever(profiles.findById(ineligible.playerId)).thenReturn(ineligible)
        whenever(profiles.findById(eligible.playerId)).thenReturn(eligible)

        val insight = service.generate().insights.single {
            it.type == HistoricalInsightType.HIGHEST_PLAYER_AVERAGE
        }

        assertThat((insight.value as HistoricalInsightValue.Rating).value).isEqualByComparingTo("8.2")
        assertThat(insight.involvedPlayers.single().playerId.value).isEqualTo("enough")
        assertThat(insight.rule.criterion).contains("3 partidas")
    }

    @Test
    fun `same persisted input produces exactly repeatable report`() {
        val matches = listOf(
            canonical("m1", "2026-07-01T10:00:00Z", 1, 0, "8.0"),
            canonical("m2", "2026-07-02T10:00:00Z", 1, 1, "7.0"),
        )
        whenever(history.list(OLDEST_FIRST)).thenReturn(matches)
        whenever(profiles.listPlayers()).thenReturn(emptyList())

        assertThat(service.generate()).isEqualTo(service.generate())
    }

    private fun assertCount(
        report: com.eafc26.discordstats.insight.HistoricalInsightsReport,
        type: HistoricalInsightType,
        expected: Int?,
    ) {
        val insight = report.insights.firstOrNull { it.type == type }
        if (expected == null) {
            assertThat(insight).isNull()
        } else {
            assertThat((insight!!.value as HistoricalInsightValue.Count).value).isEqualTo(expected)
        }
    }

    private fun rating(
        report: com.eafc26.discordstats.insight.HistoricalInsightsReport,
        type: HistoricalInsightType,
    ) = (report.insights.single { it.type == type }.value as HistoricalInsightValue.Rating).value

    private fun profile(
        id: String,
        name: String,
        matches: Int,
        goals: Int = 0,
        assists: Int = 0,
        craques: Int = 0,
        bagres: Int = 0,
        xerifes: Int = 0,
        average: String? = null,
        rated: Int = 0,
    ) = PlayerProfile(
        PlayerId(id),
        name,
        matches,
        wins = matches,
        draws = 0,
        losses = 0,
        averageRating = average?.let(::BigDecimal),
        ratedMatchCount = rated,
        goals = goals,
        assists = assists,
        craques = craques,
        bagres = bagres,
        xerifes = xerifes,
        redCards = 0,
        recentMatches = emptyList(),
    )

    private fun index(profile: PlayerProfile) = PlayerProfileIndexEntry(
        profile.playerId,
        profile.displayName,
        profile.matchCount,
        Instant.parse("2026-07-01T10:00:00Z"),
    )

    private fun canonical(
        id: String,
        playedAt: String,
        ourScore: Int,
        opponentScore: Int,
        rating: String,
    ): CanonicalMatch {
        val source = MatchResponse(
            matchId = id,
            timestamp = Instant.parse(playedAt).epochSecond,
            matchType = "leagueMatch",
            clubs = linkedMapOf(
                OUR_CLUB.value to ClubMatchEntry(
                    details = ClubDetails("Our FC", OUR_CLUB.value),
                    score = ourScore.toString(),
                    result = result(ourScore, opponentScore),
                ),
                "opponent" to ClubMatchEntry(
                    details = ClubDetails("Opponent FC", "opponent"),
                    score = opponentScore.toString(),
                    result = result(opponentScore, ourScore),
                ),
            ),
            players = mapOf(
                OUR_CLUB.value to mapOf(
                    "player" to PlayerEntry(
                        playerName = "Player",
                        position = "14",
                        rating = rating,
                        goals = ourScore.toString(),
                        assists = "1",
                        shots = "3",
                        passesMade = "18",
                        passAttempts = "20",
                        tacklesMade = "2",
                        tackleAttempts = "4",
                        redCards = "0",
                        secondsPlayed = "5400",
                    )
                )
            ),
        )
        val footballMatch = (EaMatchMapper().map(source) as MatchNormalizationResult.Success).match
        val interpretation = MatchInterpreter().interpret(footballMatch, OUR_CLUB)
        return CanonicalMatch.current(
            footballMatch,
            interpretation,
            MatchStoryExtractor().extract(interpretation),
            Instant.parse("2026-07-30T10:00:00Z"),
        )
    }

    private fun result(score: Int, opponent: Int) =
        if (score > opponent) "1" else if (score == opponent) "2" else "0"

    private companion object {
        val OUR_CLUB = ClubId("our-club")
        val OLDEST_FIRST = MatchHistoryQuery(order = MatchHistoryOrder.OLDEST_FIRST)
    }
}
