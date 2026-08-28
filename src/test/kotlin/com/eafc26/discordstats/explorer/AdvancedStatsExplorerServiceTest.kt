package com.eafc26.discordstats.explorer

import com.eafc26.discordstats.application.repository.CanonicalMatchOverview
import com.eafc26.discordstats.application.repository.CanonicalMatchRepository
import com.eafc26.discordstats.application.repository.CanonicalRepositoryMetadata
import com.eafc26.discordstats.canonical.CanonicalMatch
import com.eafc26.discordstats.domain.match.*
import com.eafc26.discordstats.domain.interpretation.MatchInterpretation
import com.eafc26.discordstats.domain.interpretation.ResultDecision
import com.eafc26.discordstats.ea.mapping.UnknownFieldCapture
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
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
        rawUnknownFields: RawUnknownFields? = null,
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
            rawUnknownFields = rawUnknownFields,
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

    @Test
    fun `JSON export includes sanitized unknown fields with original JSON types and nested values`() {
        val raw = RawUnknownFields.of(
            "player",
            listOf(
                RawUnknownField("text", "string", "\"hello\""),
                RawUnknownField("number", "number", "42"),
                RawUnknownField("boolean", "boolean", "true"),
                RawUnknownField("nullable", "null", "null"),
                RawUnknownField("object", "object", "{\"nested\":{\"value\":1}}"),
                RawUnknownField("array", "array", "[1,{\"enabled\":true}]"),
            ),
        )
        val service = AdvancedStatsExplorerService(fakeRepo(buildCanonical(rawAgg0 = null, rawAgg1 = null, rawUnknownFields = raw)))

        val row = service.exportData(clubId, 5).single()
        val unknown = row["unknownFields"] as List<AdvancedStatsExplorerService.UnknownFieldExport>

        assertThat(row["unknownFieldsStatus"]).isEqualTo("PRESENT")
        assertThat(row["unknownFieldCount"]).isEqualTo(6)
        val byName = unknown.associateBy { it.fieldName }
        assertThat(byName.getValue("text").value.isTextual).isTrue()
        assertThat(byName.getValue("text").value.asText()).isEqualTo("hello")
        assertThat(byName.getValue("number").value.isInt).isTrue()
        assertThat(byName.getValue("number").value.intValue()).isEqualTo(42)
        assertThat(byName.getValue("boolean").value.isBoolean).isTrue()
        assertThat(byName.getValue("boolean").value.booleanValue()).isTrue()
        assertThat(byName.getValue("nullable").value.isNull).isTrue()
        assertThat(byName.getValue("object").value.path("nested").path("value").intValue()).isEqualTo(1)
        assertThat(byName.getValue("array").value[1].path("enabled").booleanValue()).isTrue()

        val exportedJson = ObjectMapper().readTree(ObjectMapper().writeValueAsString(listOf(row)))
        assertThat(exportedJson[0]["unknownFields"][0]["value"]).isNotNull
    }

    @Test
    fun `JSON export distinguishes unavailable and empty unknown field capture`() {
        val unavailable = AdvancedStatsExplorerService(fakeRepo(buildCanonical(rawAgg0 = null, rawAgg1 = null, rawUnknownFields = null)))
            .exportData(clubId, 5).single()
        val empty = AdvancedStatsExplorerService(fakeRepo(buildCanonical(rawAgg0 = null, rawAgg1 = null, rawUnknownFields = RawUnknownFields.empty("player"))))
            .exportData(clubId, 5).single()

        assertThat(unavailable["unknownFieldsStatus"]).isEqualTo("UNAVAILABLE")
        assertThat(unavailable["unknownFields"]).isEqualTo(emptyList<Any>())
        assertThat(empty["unknownFieldsStatus"]).isEqualTo("EMPTY")
        assertThat(empty["unknownFields"]).isEqualTo(emptyList<Any>())
    }

    @Test
    fun `CSV export emits one row per unknown field and keeps valid JSON in the value cell`() {
        val raw = RawUnknownFields.of(
            "player",
            listOf(
                RawUnknownField("object", "object", "{\"nested\":true}"),
                RawUnknownField("array", "array", "[1,2,3]"),
            ),
        )
        val rows = AdvancedStatsExplorerService(fakeRepo(buildCanonical(rawUnknownFields = raw)))
            .exportUnknownFieldsCsvData(clubId, 5)

        assertThat(rows).hasSize(2)
        assertThat(rows.map { it["fieldName"] }).containsExactly("object", "array")
        rows.forEach { row ->
            assertThat(ObjectMapper().readTree(row["value"] as String)).isNotNull
            assertThat(row["unknownFieldsStatus"]).isEqualTo("PRESENT")
        }
    }

    @Test
    fun `CSV export keeps a status row for historical capture unavailable`() {
        val rows = AdvancedStatsExplorerService(fakeRepo(buildCanonical(rawUnknownFields = null)))
            .exportUnknownFieldsCsvData(clubId, 5)

        val row = rows.single()
        assertThat(row["unknownFieldsStatus"]).isEqualTo("UNAVAILABLE")
        assertThat(row["fieldName"]).isNull()
    }

    @Test
    fun `additional aggregate fields are marked as candidates without a sporting mapping`() {
        val raw = RawUnknownFields.of(
            "player",
            listOf(RawUnknownField("match_event_aggregate_2", "string", "\"1:4\"")),
        )
        val service = AdvancedStatsExplorerService(fakeRepo(buildCanonical(rawAgg0 = null, rawAgg1 = null, rawUnknownFields = raw)))

        val detail = service.playerExplorerData(clubId, matchId, "player-1")!!
        val exported = (service.exportData(clubId, 5).single()["unknownFields"] as List<AdvancedStatsExplorerService.UnknownFieldExport>).single()

        assertThat(detail.unknownFields.fields).hasSize(1)
        assertThat(detail.unknownFields.fields.single().isAdditionalAggregateCandidate).isTrue()
        assertThat(exported.isAdditionalAggregateCandidate).isTrue()
        assertThat(detail.aggregateEntries).noneMatch { it.aggregate == 2 }
    }

    @Test
    fun `truncated unknown field remains explicit and does not pretend to be parsed JSON`() {
        val raw = RawUnknownFields.of(
            "player",
            listOf(RawUnknownField("large", "object", "{partial", truncated = true, originalSize = 9000)),
        )
        val row = AdvancedStatsExplorerService(fakeRepo(buildCanonical(rawAgg0 = null, rawAgg1 = null, rawUnknownFields = raw))).exportData(clubId, 5).single()
        val exported = (row["unknownFields"] as List<AdvancedStatsExplorerService.UnknownFieldExport>).single()

        assertThat(exported.truncated).isTrue()
        assertThat(exported.value.isTextual).isTrue()
        assertThat(exported.value.asText()).isEqualTo("{partial")
        assertThat(row["unknownFieldsStatus"]).isEqualTo("PRESENT")
    }

    @Test
    fun `sensitive input remains excluded before it can reach Explorer export`() {
        val captured = UnknownFieldCapture.capture(
            "player",
            linkedMapOf(
                "safeField" to JsonNodeFactory.instance.numberNode(7),
                "authToken" to JsonNodeFactory.instance.textNode("must-not-export"),
            ),
        )
        val rows = AdvancedStatsExplorerService(fakeRepo(buildCanonical(rawAgg0 = null, rawAgg1 = null, rawUnknownFields = captured))).exportData(clubId, 5)
        val fields = rows.single()["unknownFields"] as List<AdvancedStatsExplorerService.UnknownFieldExport>

        assertThat(fields.map { it.fieldName }).containsExactly("safeField")
    }

    @Test
    fun `discovery reads only the bounded recent window and never findAll`() {
        val canonical = buildCanonical()
        var requestedLimit = -1
        val repository = object : CanonicalMatchRepository by fakeRepo(canonical) {
            override fun findAll(clubId: ClubId): List<CanonicalMatch> = error("Discovery must not read full history")
            override fun findRecent(clubId: ClubId, limit: Int): List<CanonicalMatch> {
                requestedLimit = limit
                return listOf(canonical)
            }
        }

        val result = AdvancedStatsExplorerService(repository).discoveryData(clubId, limit = 10)

        assertThat(requestedLimit).isEqualTo(20)
        assertThat(result.analysis.rawMatchesAnalyzed).isEqualTo(1)
    }

    @Test
    fun `non empty aggregate two capture creates an alert without a mapping`() {
        val raw = RawUnknownFields.of(
            "player",
            listOf(RawUnknownField("match_event_aggregate_2", "string", "\"8:2\"")),
        )
        val result = AdvancedStatsExplorerService(fakeRepo(buildCanonical(rawUnknownFields = raw)))
            .discoveryData(clubId, limit = 10)

        val alert = result.newAggregateDataDetected.single()
        assertThat(alert.fieldName).isEqualTo("match_event_aggregate_2")
        assertThat(alert.matchCount).isEqualTo(1)
        assertThat(alert.playerCount).isEqualTo(1)
        assertThat(result.analysis.inventory).noneMatch { it.aggregateIndex == 2 }
    }

    @Test
    fun `historical match without RAW coverage is excluded rather than interpreted as zeros`() {
        val historical = buildCanonical(rawAgg0 = null, rawAgg1 = null)
        val withRaw = buildCanonical(rawAgg0 = "91:3", rawAgg1 = null)
        val repository = object : CanonicalMatchRepository by fakeRepo(withRaw) {
            override fun findRecent(clubId: ClubId, limit: Int): List<CanonicalMatch> = listOf(historical, withRaw)
            override fun findAll(clubId: ClubId): List<CanonicalMatch> = error("Discovery must not read full history")
        }

        val result = AdvancedStatsExplorerService(repository).discoveryData(clubId, limit = 10)

        val code = result.analysis.inventory.single { it.aggregateIndex == 0 && it.code == 91 }
        assertThat(result.analysis.rawMatchesAnalyzed).isEqualTo(1)
        assertThat(code.rawObservationCount).isEqualTo(1)
        assertThat(code.zeroCount).isZero()
    }
}
