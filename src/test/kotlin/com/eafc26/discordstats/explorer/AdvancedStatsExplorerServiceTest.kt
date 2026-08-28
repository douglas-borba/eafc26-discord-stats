package com.eafc26.discordstats.explorer

import com.eafc26.discordstats.application.repository.CanonicalMatchOverview
import com.eafc26.discordstats.application.repository.CanonicalMatchRepository
import com.eafc26.discordstats.application.repository.CanonicalRepositoryMetadata
import com.eafc26.discordstats.canonical.CanonicalMatch
import com.eafc26.discordstats.domain.match.*
import com.eafc26.discordstats.domain.interpretation.MatchInterpretation
import com.eafc26.discordstats.domain.interpretation.ResultDecision
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Duration
import java.time.Instant

class AdvancedStatsExplorerServiceTest {

    private val clubId = ClubId("club-1")
    private val matchId = MatchId("match-1")

    private fun buildCanonical(
        rawAgg0: String? = "6:4,112:8,115:2,152:9,174:18,42:7",
        rawAgg1: String? = "6:3,47:1",
    ): CanonicalMatch {
        val player = PlayerMatchPerformance(
            player = PlayerIdentity(PlayerId("player-1"), DisplayName("Neymar"), null),
            role = PlayerRole.Outfield(null),
            participation = Participation(Duration.ofSeconds(5400), ParticipationStatus.COMPLETED),
            rating = MatchRating(java.math.BigDecimal("8.5")),
            attacking = AttackingStats(goals = 2, assists = 1, shots = 5),
            passing = PassingStats(attempted = 20, completed = 18),
            defending = DefendingStats(tacklesAttempted = 4, tacklesCompleted = 3, interceptions = 7),
            discipline = DisciplineStats(redCards = 0),
            goalkeeping = null,
            eaRecognition = EaRecognition(manOfTheMatch = true),
            advanced = AdvancedPlayerStats(secondAssists = 2, throughPasses = 9, dribblesCompleted = 18, beats = 8, interceptions = 7),
            advancedCoverage = AdvancedStatsCoverage.FULL,
            rawEventAggregates = if (rawAgg0 != null || rawAgg1 != null) RawEventAggregates(rawAgg0, rawAgg1) else null,
        )

        val footballMatch = FootballMatch(
            id = matchId,
            playedAt = Instant.ofEpochSecond(1718500000L),
            competition = CompetitionType.LEAGUE,
            participants = listOf(
                ClubMatchPerformance(
                    club = ClubIdentity(clubId, ClubName("Our FC")),
                    score = Score(3),
                    reportedResult = ReportedMatchResult.WIN,
                    players = listOf(player),
                ),
                ClubMatchPerformance(
                    club = ClubIdentity(ClubId("opp-1"), ClubName("Opponent FC")),
                    score = Score(1),
                    reportedResult = ReportedMatchResult.LOSS,
                    players = emptyList(),
                ),
            ),
            completion = MatchCompletion.COMPLETED,
        )

        val resultDecision = mock<ResultDecision>()
        whenever(resultDecision.ourScore).thenReturn(Score(3))
        whenever(resultDecision.opponentScore).thenReturn(Score(1))
        whenever(resultDecision.opponentClub).thenReturn(ClubId("opp-1"))

        val interpretation = mock<MatchInterpretation>()
        whenever(interpretation.perspectiveClubId).thenReturn(clubId)
        whenever(interpretation.result).thenReturn(resultDecision)

        val canonical = mock<CanonicalMatch>()
        whenever(canonical.matchId).thenReturn(matchId)
        whenever(canonical.footballMatch).thenReturn(footballMatch)
        whenever(canonical.interpretation).thenReturn(interpretation)

        return canonical
    }

    private fun fakeRepo(canonical: CanonicalMatch) = object : CanonicalMatchRepository {
        override fun save(match: CanonicalMatch) {}
        override fun findById(clubId: ClubId, matchId: MatchId): CanonicalMatch? =
            if (matchId == canonical.matchId) canonical else null
        override fun findMatchIds(clubId: ClubId): Set<MatchId> = setOf(canonical.matchId)
        override fun findLatestMatchId(clubId: ClubId): MatchId? = canonical.matchId
        override fun findExistingMatchIds(clubId: ClubId, candidateMatchIds: Collection<MatchId>): Set<MatchId> = emptySet()
        override fun findRecentMatchIds(clubId: ClubId, limit: Int): List<MatchId> = listOf(canonical.matchId)
        override fun findRecentOverview(clubId: ClubId, limit: Int): List<CanonicalMatchOverview> = emptyList()
        override fun findAll(clubId: ClubId): List<CanonicalMatch> = listOf(canonical)
        override fun findHistorySummaries(clubId: ClubId): List<CanonicalMatchOverview> = emptyList()
        override fun findRecent(clubId: ClubId, limit: Int): List<CanonicalMatch> = listOf(canonical)
        override fun metadata(clubId: ClubId): CanonicalRepositoryMetadata = CanonicalRepositoryMetadata(1, null, null, null, emptySet(), emptySet())
    }

    @Test
    fun `aggregate_0 and aggregate_1 entries are kept separate`() {
        val service = AdvancedStatsExplorerService(fakeRepo(buildCanonical()))
        val data = service.playerExplorerData(clubId, matchId, "player-1")!!

        val agg0code6 = data.aggregateEntries.first { it.aggregate == 0 && it.code == 6 }
        val agg1code6 = data.aggregateEntries.first { it.aggregate == 1 && it.code == 6 }
        assertThat(agg0code6.value).isEqualTo(4)
        assertThat(agg1code6.value).isEqualTo(3)
    }

    @Test
    fun `known codes get CONFIRMED status and metric name`() {
        val service = AdvancedStatsExplorerService(fakeRepo(buildCanonical()))
        val data = service.playerExplorerData(clubId, matchId, "player-1")!!

        val preAssists = data.aggregateEntries.first { it.aggregate == 0 && it.code == 115 }
        assertThat(preAssists.confidence).isEqualTo("CONFIRMED")
        assertThat(preAssists.metricName).isEqualTo("Pre-assists")
    }

    @Test
    fun `unknown codes remain UNKNOWN without metric name`() {
        val service = AdvancedStatsExplorerService(fakeRepo(buildCanonical()))
        val data = service.playerExplorerData(clubId, matchId, "player-1")!!

        val unknown42 = data.aggregateEntries.first { it.aggregate == 0 && it.code == 42 }
        assertThat(unknown42.confidence).isEqualTo("UNKNOWN")
        assertThat(unknown42.metricName).isNull()
    }

    @Test
    fun `code 6 is HYPOTHESIS not interception`() {
        val service = AdvancedStatsExplorerService(fakeRepo(buildCanonical()))
        val data = service.playerExplorerData(clubId, matchId, "player-1")!!

        val code6entries = data.aggregateEntries.filter { it.code == 6 }
        code6entries.forEach {
            assertThat(it.confidence).isEqualTo("HYPOTHESIS")
            assertThat(it.metricName).isNull()
        }
    }

    @Test
    fun `missing raw aggregates produce empty entries list`() {
        val service = AdvancedStatsExplorerService(fakeRepo(buildCanonical(rawAgg0 = null, rawAgg1 = null)))
        val data = service.playerExplorerData(clubId, matchId, "player-1")!!

        assertThat(data.aggregateEntries).isEmpty()
    }

    @Test
    fun `known stats are populated from player performance`() {
        val service = AdvancedStatsExplorerService(fakeRepo(buildCanonical()))
        val data = service.playerExplorerData(clubId, matchId, "player-1")!!

        assertThat(data.knownStats.goals).isEqualTo(2)
        assertThat(data.knownStats.assists).isEqualTo(1)
        assertThat(data.knownStats.shots).isEqualTo(5)
        assertThat(data.knownStats.passesAttempted).isEqualTo(20)
        assertThat(data.knownStats.beats).isEqualTo(8)
    }

    @Test
    fun `raw view preserves original strings`() {
        val service = AdvancedStatsExplorerService(fakeRepo(buildCanonical()))
        val data = service.playerExplorerData(clubId, matchId, "player-1")!!

        assertThat(data.rawAggregate0).isEqualTo("6:4,112:8,115:2,152:9,174:18,42:7")
        assertThat(data.rawAggregate1).isEqualTo("6:3,47:1")
    }

    @Test
    fun `export produces rows with aggregate and code`() {
        val service = AdvancedStatsExplorerService(fakeRepo(buildCanonical()))
        val rows = service.exportData(clubId, 5)

        assertThat(rows).isNotEmpty
        val firstRow = rows.first()
        assertThat(firstRow["clubId"]).isEqualTo("club-1")
        assertThat(firstRow["matchId"]).isEqualTo("match-1")
        assertThat(firstRow["playerName"]).isEqualTo("Neymar")
        assertThat(firstRow).containsKeys("aggregate", "code", "value")
    }

    @Test
    fun `multi-match comparison returns data for each match`() {
        val service = AdvancedStatsExplorerService(fakeRepo(buildCanonical()))
        val results = service.multiMatchComparison(clubId, "player-1", listOf(matchId))

        assertThat(results).hasSize(1)
        assertThat(results[0].matchId).isEqualTo("match-1")
    }
}
