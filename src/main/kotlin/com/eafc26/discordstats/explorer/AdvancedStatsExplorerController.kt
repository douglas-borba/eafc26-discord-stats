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
        val rows = explorerService.exportData(ClubId(clubId), limit)
        return when (format.lowercase()) {
            "csv" -> {
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
            else -> ResponseEntity.ok(rows)
        }
    }

    @GetMapping("/registry")
    fun registry(): ResponseEntity<List<CodeMapping>> {
        return ResponseEntity.ok(AdvancedStatsCodeRegistry.allMappings())
    }
}
