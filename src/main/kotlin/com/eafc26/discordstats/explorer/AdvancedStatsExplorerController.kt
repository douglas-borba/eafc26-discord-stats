package com.eafc26.discordstats.explorer

import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.MatchId
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/admin/explorer")
class AdvancedStatsExplorerController(
    private val explorerService: AdvancedStatsExplorerService,
) {

    @GetMapping("/clubs/{clubId}/matches")
    fun matches(
        @PathVariable clubId: String,
        @RequestParam(defaultValue = "20") limit: Int,
    ): ResponseEntity<List<AdvancedStatsExplorerService.MatchSummary>> {
        if (limit < 1 || limit > 50) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Limit must be 1-50")
        return ResponseEntity.ok(explorerService.recentMatches(ClubId(clubId), limit))
    }

    @GetMapping("/clubs/{clubId}/matches/{matchId}/players")
    fun players(
        @PathVariable clubId: String,
        @PathVariable matchId: String,
    ): ResponseEntity<List<AdvancedStatsExplorerService.PlayerSummary>> {
        return ResponseEntity.ok(explorerService.matchPlayers(ClubId(clubId), MatchId(matchId)))
    }

    @GetMapping("/clubs/{clubId}/matches/{matchId}/players/{playerId}")
    fun playerDetail(
        @PathVariable clubId: String,
        @PathVariable matchId: String,
        @PathVariable playerId: String,
    ): ResponseEntity<AdvancedStatsExplorerService.PlayerExplorerData> {
        val data = explorerService.playerExplorerData(ClubId(clubId), MatchId(matchId), playerId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found in match")
        return ResponseEntity.ok(data)
    }

    @GetMapping("/clubs/{clubId}/players/{playerId}/compare")
    fun compareMatches(
        @PathVariable clubId: String,
        @PathVariable playerId: String,
        @RequestParam matchIds: List<String>,
    ): ResponseEntity<List<AdvancedStatsExplorerService.PlayerExplorerData>> {
        if (matchIds.size > 5) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Max 5 matches")
        val data = explorerService.multiMatchComparison(
            ClubId(clubId), playerId, matchIds.map(::MatchId),
        )
        return ResponseEntity.ok(data)
    }

    @GetMapping("/clubs/{clubId}/export")
    fun export(
        @PathVariable clubId: String,
        @RequestParam(defaultValue = "20") limit: Int,
        @RequestParam(defaultValue = "json") format: String,
    ): ResponseEntity<Any> {
        if (limit < 1 || limit > 50) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Limit must be 1-50")
        return when (format.lowercase()) {
            "csv" -> {
                val rows = explorerService.exportUnknownFieldsCsvData(ClubId(clubId), limit)
                if (rows.isEmpty()) return ResponseEntity.ok("")
                val headers = rows.first().keys.toList()
                val csv = buildString {
                    appendLine(headers.joinToString(","))
                    rows.forEach { row ->
                        appendLine(headers.joinToString(",") { key ->
                            val v = row[key]?.toString() ?: ""
                            if (v.contains(',') || v.contains('"') || v.contains('\n'))
                                "\"${v.replace("\"", "\"\"")}\""
                            else v
                        })
                    }
                }
                ResponseEntity.ok()
                    .header("Content-Type", "text/csv")
                    .header("Content-Disposition", "attachment; filename=\"explorer-$clubId.csv\"")
                    .body(csv)
            }
            else -> ResponseEntity.ok(explorerService.exportData(ClubId(clubId), limit))
        }
    }

    @GetMapping("/clubs/{clubId}/discovery")
    fun discovery(
        @PathVariable clubId: String,
        @RequestParam(defaultValue = "10") limit: Int,
        @RequestParam(defaultValue = "all") aggregate: String,
        @RequestParam(defaultValue = "0") minimumMatches: Int,
        @RequestParam(defaultValue = "0") minimumObservations: Int,
        @RequestParam(defaultValue = "true") hideKnownRelationships: Boolean,
    ): ResponseEntity<AdvancedStatsExplorerService.DiscoveryData> {
        if (limit < 1 || limit > 20) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Limit must be 1-20")
        if (minimumMatches < 0 || minimumObservations < 0) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Minimums must be non-negative")
        }
        val aggregateIndex = when (aggregate.lowercase()) {
            "all" -> null
            "0" -> 0
            "1" -> 1
            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Aggregate must be all, 0 or 1")
        }
        return ResponseEntity.ok(
            explorerService.discoveryData(
                clubId = ClubId(clubId),
                limit = limit,
                aggregate = aggregateIndex,
                minimumMatches = minimumMatches,
                minimumObservations = minimumObservations,
                hideKnownRelationships = hideKnownRelationships,
            ),
        )
    }

    @GetMapping("/clubs/{clubId}/anchor")
    fun anchorInvestigation(
        @PathVariable clubId: String,
        @RequestParam(defaultValue = "10") limit: Int,
        @RequestParam anchorType: String,
        @RequestParam(required = false) aggregateIndex: Int?,
        @RequestParam(required = false) code: Int?,
        @RequestParam(required = false) metricName: String?,
    ): ResponseEntity<AdvancedStatsDiscoveryEngine.AnchorInvestigation> {
        if (limit < 1 || limit > 20) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Limit must be 1-20")
        if (anchorType !in setOf("AGGREGATE_CODE", "KNOWN_METRIC", "CONFIRMED_ADVANCED")) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "anchorType must be AGGREGATE_CODE, KNOWN_METRIC, or CONFIRMED_ADVANCED")
        }
        if (anchorType == "AGGREGATE_CODE" && (aggregateIndex == null || code == null)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "aggregateIndex and code required for AGGREGATE_CODE")
        }
        if (anchorType == "KNOWN_METRIC" && metricName == null) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "metricName required for KNOWN_METRIC")
        }
        if (anchorType == "CONFIRMED_ADVANCED" && (aggregateIndex == null || code == null)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "aggregateIndex and code required for CONFIRMED_ADVANCED")
        }
        return ResponseEntity.ok(
            explorerService.anchorInvestigation(ClubId(clubId), limit, anchorType, aggregateIndex, code, metricName),
        )
    }

    @GetMapping("/clubs/{clubId}/family")
    fun familyInvestigation(
        @PathVariable clubId: String,
        @RequestParam(defaultValue = "10") limit: Int,
        @RequestParam aggregateIndex: Int,
        @RequestParam codes: List<Int>,
    ): ResponseEntity<AdvancedStatsDiscoveryEngine.FamilyInvestigation> {
        if (limit < 1 || limit > 20) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Limit must be 1-20")
        if (codes.size < 2 || codes.size > 20) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "2-20 codes required")
        return ResponseEntity.ok(
            explorerService.familyInvestigation(ClubId(clubId), limit, aggregateIndex, codes),
        )
    }

    @GetMapping("/clubs/{clubId}/residual-explainer")
    fun residualExplainer(
        @PathVariable clubId: String,
        @RequestParam(defaultValue = "10") limit: Int,
        @RequestParam anchorType: String,
        @RequestParam(required = false) aggregateIndex: Int?,
        @RequestParam(required = false) code: Int?,
        @RequestParam(required = false) metricName: String?,
        @RequestParam candidateAggregateIndex: Int,
        @RequestParam candidateCode: Int,
    ): ResponseEntity<AdvancedStatsDiscoveryEngine.ResidualExplainerResult> {
        if (limit < 1 || limit > 20) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Limit must be 1-20")
        if (anchorType !in setOf("AGGREGATE_CODE", "KNOWN_METRIC", "CONFIRMED_ADVANCED")) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid anchorType")
        }
        return ResponseEntity.ok(explorerService.residualExplainer(
            ClubId(clubId), limit, anchorType, aggregateIndex, code, metricName, candidateAggregateIndex, candidateCode))
    }

    @GetMapping("/clubs/{clubId}/novel-metrics")
    fun novelMetrics(
        @PathVariable clubId: String,
        @RequestParam(defaultValue = "10") limit: Int,
        @RequestParam(required = false) aggregateIndex: Int?,
        @RequestParam(required = false) code: Int?,
    ): ResponseEntity<Any> {
        if (limit < 1 || limit > 20) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Limit must be 1-20")
        if ((aggregateIndex == null) != (code == null)) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "aggregateIndex and code must be supplied together")
        val result = if (aggregateIndex == null) explorerService.novelMetricDiscovery(ClubId(clubId), limit)
        else explorerService.novelMetricDetail(ClubId(clubId), limit, aggregateIndex, code!!)
        return ResponseEntity.ok(result ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown aggregate code not found in bounded sample"))
    }

    @GetMapping("/clubs/{clubId}/players/{playerId}/position-observations")
    fun positionObservations(
        @PathVariable clubId: String,
        @PathVariable playerId: String,
        @RequestParam(defaultValue = "20") limit: Int,
    ): ResponseEntity<AdvancedStatsExplorerService.PositionObservationsData> {
        if (limit < 1 || limit > 50) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Limit must be 1-50")
        return ResponseEntity.ok(explorerService.positionObservations(ClubId(clubId), playerId, limit))
    }

    @GetMapping("/registry")
    fun registry(): ResponseEntity<List<CodeMapping>> {
        return ResponseEntity.ok(AdvancedStatsCodeRegistry.allMappings())
    }
}
