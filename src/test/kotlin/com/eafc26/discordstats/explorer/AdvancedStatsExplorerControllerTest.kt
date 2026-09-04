package com.eafc26.discordstats.explorer

import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.MatchId
import com.eafc26.discordstats.ea.mapping.EaPositionCodeDecoder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
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

    @Test
    fun `reconciliation endpoint keeps source and target explicit`() {
        val result = ObservationPhraseReconciliationResult(
            ObservationPhraseReconciliationStatus.SUCCESS,
            observation = ExplorerObservation(ClubId("club-1"), com.eafc26.discordstats.domain.match.MatchId("match-1"), "player-1", "Ótimo empenho ofensivo", 2),
        )
        whenever(explorerService.reconcileObservationPhrase(
            ClubId("club-1"), com.eafc26.discordstats.domain.match.MatchId("match-1"), "player-1",
            "otimo emepenho ofensivo", "Ótimo empenho ofensivo",
        )).thenReturn(result)

        client.post().uri("/api/admin/explorer/clubs/club-1/matches/match-1/players/player-1/observations/reconcile")
            .bodyValue(mapOf("sourcePhrase" to "otimo emepenho ofensivo", "targetPhrase" to "Ótimo empenho ofensivo"))
            .exchange().expectStatus().isOk
            .expectBody().jsonPath("$.status").isEqualTo("SUCCESS")
            .jsonPath("$.observation.phrase").isEqualTo("Ótimo empenho ofensivo")

        verify(explorerService).reconcileObservationPhrase(
            ClubId("club-1"), com.eafc26.discordstats.domain.match.MatchId("match-1"), "player-1",
            "otimo emepenho ofensivo", "Ótimo empenho ofensivo",
        )
    }

    @Test
    fun `evidence audit endpoint forwards one exact persisted identity`() {
        val audit = AdvancedStatsExplorerService.ObservationEvidenceAudit(
            identity = AdvancedStatsExplorerService.ObservationIdentity("club-1", "match-1", "player-1", "Melhore seu tempo de bola"),
            canonicalMatch = null,
            player = AdvancedStatsExplorerService.AuditPlayer("player-1", "Player", null),
            observation = AdvancedStatsExplorerService.AuditObservation("Melhore seu tempo de bola", 6, "AT_LEAST", null, null, null, null),
            playerMatchObservations = emptyList(),
            vectorTruncated = false,
            candidate = AdvancedStatsExplorerService.AuditCandidate(
                aggregateIndex = 0,
                code = 183,
                provenance = AdvancedStatsExplorerService.RawAggregateProvenance.EXPLICIT_VALUE,
                explicitRawValue = 3,
                valueUsedByAnalyzer = 3,
                comparison = "CONTRADICTED",
                difference = -3,
                rawAggregate = "183:3",
                rawEntries = listOf(AdvancedStatsExplorerService.AuditRawAggregateEntry(183, 3)),
                rawEntriesTruncated = false,
            ),
        )
        whenever(explorerService.observationEvidenceAudit(ClubId("club-1"), MatchId("match-1"), "player-1", "Melhore seu tempo de bola", 0, 183))
            .thenReturn(audit)

        val response = AdvancedStatsExplorerController(explorerService).observationEvidenceAudit(
            "club-1", "player-1", "match-1", "Melhore seu tempo de bola", 0, 183,
        )

        assertThat(response.statusCode.value()).isEqualTo(200)
        assertThat(response.body?.identity?.phrase).isEqualTo("Melhore seu tempo de bola")
        assertThat(response.body?.candidate?.difference).isEqualTo(-3)

        verify(explorerService).observationEvidenceAudit(
            ClubId("club-1"), MatchId("match-1"), "player-1", "Melhore seu tempo de bola", 0, 183,
        )
    }
}
