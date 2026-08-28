package com.eafc26.discordstats.explorer

import com.eafc26.discordstats.domain.match.ClubId
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
}
