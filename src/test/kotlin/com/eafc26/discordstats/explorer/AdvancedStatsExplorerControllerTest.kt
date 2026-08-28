package com.eafc26.discordstats.explorer

import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.ea.mapping.EaPositionCodeDecoder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.test.web.reactive.server.WebTestClient

class AdvancedStatsExplorerControllerTest {

    private val explorerService = mock<AdvancedStatsExplorerService>()
    private val client = WebTestClient.bindToController(AdvancedStatsExplorerController(explorerService)).build()

    @Test
    fun `CSV export keeps one JSON value per unknown field cell`() {
        whenever(explorerService.exportUnknownFieldsCsvData(ClubId("club-1"), 20)).thenReturn(
            listOf(
                linkedMapOf(
                    "clubId" to "club-1",
                    "matchId" to "match-1",
                    "timestamp" to "2026-08-28T00:00:00Z",
                    "playerId" to "player-1",
                    "playerName" to "Player One",
                    "opponentName" to "Opponent",
                    "unknownFieldsStatus" to "PRESENT",
                    "scope" to "player",
                    "fieldName" to "extra",
                    "jsonType" to "object",
                    "value" to "{\"nested\":true}",
                    "valueSize" to 15,
                    "truncated" to false,
                    "additionalAggregateCandidate" to false,
                ),
            ),
        )

        val body = client.get().uri("/api/admin/explorer/clubs/club-1/export?format=csv")
            .exchange()
            .expectStatus().isOk
            .expectHeader().valueEquals("Content-Type", "text/csv")
            .expectBody(String::class.java)
            .returnResult()
            .responseBody

        assertThat(body).contains("fieldName,jsonType,value,valueSize")
        assertThat(body).contains("\"{\"\"nested\"\":true}\"")
    }

    @Test
    fun `discovery endpoint keeps the analysis bounded and passes filters`() {
        val result = AdvancedStatsExplorerService.DiscoveryData(
            analysis = AdvancedStatsDiscoveryEngine.DiscoveryResult(
                rawMatchesAnalyzed = 0, playerMatchObservations = 0, aggregate0CodeCount = 0, aggregate1CodeCount = 0,
                unknownCodeCount = 0, knownCodeCount = 0, hypothesisCodeCount = 0,
                inventory = emptyList(), topCandidates = emptyList(), relations = emptyList(), correlations = emptyList(), calibration = emptyList(),
            ),
            newAggregateDataDetected = emptyList(),
        )
        whenever(explorerService.discoveryData(ClubId("club-1"), 10, 0, 2, 5, true)).thenReturn(result)

        client.get().uri("/api/admin/explorer/clubs/club-1/discovery?limit=10&aggregate=0&minimumMatches=2&minimumObservations=5&hideKnownRelationships=true")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.analysis.rawMatchesAnalyzed").isEqualTo(0)
    }

    @Test
    fun `novel metrics and position observation endpoints remain bounded explorer APIs`() {
        val novel = NovelMetricDiscoveryEngine().analyze(emptyList())
        val positions = AdvancedStatsExplorerService.PositionObservationsData(
            coverage = "FULL",
            observations = emptyList(),
            distribution = listOf(
                AdvancedStatsExplorerService.PositionDistributionEntry(
                    "0", EaPositionCodeDecoder.decode("0"), 1,
                ),
            ),
            distinctCodes = 1,
        )
        whenever(explorerService.novelMetricDiscovery(ClubId("club-1"), 10)).thenReturn(novel)
        whenever(explorerService.positionObservations(ClubId("club-1"), "player-1", 20)).thenReturn(positions)

        client.get().uri("/api/admin/explorer/clubs/club-1/novel-metrics?limit=10")
            .exchange().expectStatus().isOk
            .expectBody().jsonPath("$.rawMatchesAnalyzed").isEqualTo(0)
        client.get().uri("/api/admin/explorer/clubs/club-1/players/player-1/position-observations?limit=20")
            .exchange().expectStatus().isOk
            .expectBody().jsonPath("$.coverage").isEqualTo("FULL")
    }
}
