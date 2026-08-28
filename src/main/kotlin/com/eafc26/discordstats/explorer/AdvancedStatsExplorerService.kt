package com.eafc26.discordstats.explorer

import com.eafc26.discordstats.application.repository.CanonicalMatchRepository
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.MatchId
import com.eafc26.discordstats.domain.match.PlayerMatchPerformance
import com.eafc26.discordstats.domain.match.RawUnknownField
import com.eafc26.discordstats.canonical.CanonicalMatch

class AdvancedStatsExplorerService(
    private val matchRepository: CanonicalMatchRepository,
) {

    data class MatchSummary(
        val matchId: String,
        val playedAt: String,
        val opponentName: String?,
        val ourScore: Int,
        val opponentScore: Int,
        val hasRawAggregates: Boolean,
    )

    data class PlayerSummary(
        val playerId: String,
        val platformName: String?,
        val proName: String?,
        val hasRawAggregates: Boolean,
    )

    data class AggregateEntry(
        val aggregate: Int,
        val code: Int,
        val value: Int,
        val confidence: String,
        val metricName: String?,
        val evidence: String?,
    )

    data class KnownStats(
        val rating: String?,
        val goals: Int?,
        val assists: Int?,
        val shots: Int?,
        val passesAttempted: Int?,
        val passesCompleted: Int?,
        val tacklesAttempted: Int?,
        val tacklesCompleted: Int?,
        val secondAssists: Int,
        val throughPasses: Int,
        val dribblesCompleted: Int,
        val beats: Int,
        val interceptions: Int,
        val advancedCoverage: String,
    )

    data class UnknownFieldEntry(
        val name: String,
        val jsonType: String,
        val value: String,
        val truncated: Boolean,
        val originalSize: Int,
        val isAdditionalAggregate: Boolean,
    )

    /**
     * Unknown fields capture status:
     * - null: capture was not active (historical data)
     * - empty list: capture active, no unknown fields received
     * - non-empty: unknown fields found
     */
    data class UnknownFieldsData(
        val status: String,
        val fields: List<UnknownFieldEntry>,
    )

    data class PlayerExplorerData(
        val playerId: String,
        val platformName: String?,
        val proName: String?,
        val matchId: String,
        val playedAt: String,
        val opponentName: String?,
        val aggregateEntries: List<AggregateEntry>,
        val knownStats: KnownStats,
        val rawAggregate0: String?,
        val rawAggregate1: String?,
        val unknownFields: UnknownFieldsData,
    )

    fun recentMatches(clubId: ClubId, limit: Int = 20): List<MatchSummary> {
        val matches = matchRepository.findRecent(clubId, limit)
        return matches.map { canonical ->
            val perspectiveClubId = canonical.interpretation.perspectiveClubId.value
            val opponent = canonical.footballMatch.participants
                .firstOrNull { it.club.id.value != perspectiveClubId }
            val our = canonical.footballMatch.participants
                .firstOrNull { it.club.id.value == perspectiveClubId }
            val hasRaw = our?.players?.any { it.rawEventAggregates != null } ?: false
            MatchSummary(
                matchId = canonical.matchId.value,
                playedAt = canonical.footballMatch.playedAt.toString(),
                opponentName = opponent?.club?.name?.value,
                ourScore = canonical.interpretation.result.ourScore.goals,
                opponentScore = canonical.interpretation.result.opponentScore.goals,
                hasRawAggregates = hasRaw,
            )
        }
    }

    fun matchPlayers(clubId: ClubId, matchId: MatchId): List<PlayerSummary> {
        val canonical = matchRepository.findById(clubId, matchId) ?: return emptyList()
        val perspectiveClubId = canonical.interpretation.perspectiveClubId.value
        val our = canonical.footballMatch.participants
            .firstOrNull { it.club.id.value == perspectiveClubId } ?: return emptyList()
        return our.players.map { p ->
            PlayerSummary(
                playerId = p.player.id.value,
                platformName = p.player.platformName?.value,
                proName = p.player.proName?.value,
                hasRawAggregates = p.rawEventAggregates != null,
            )
        }
    }

    fun playerExplorerData(clubId: ClubId, matchId: MatchId, playerId: String): PlayerExplorerData? {
        val canonical = matchRepository.findById(clubId, matchId) ?: return null
        val perspectiveClubId = canonical.interpretation.perspectiveClubId.value
        val our = canonical.footballMatch.participants
            .firstOrNull { it.club.id.value == perspectiveClubId } ?: return null
        val player = our.players.firstOrNull { it.player.id.value == playerId } ?: return null
        val opponent = canonical.footballMatch.participants
            .firstOrNull { it.club.id.value != perspectiveClubId }

        return buildPlayerData(player, canonical, opponent?.club?.name?.value)
    }

    fun multiMatchComparison(clubId: ClubId, playerId: String, matchIds: List<MatchId>): List<PlayerExplorerData> {
        return matchIds.take(5).mapNotNull { matchId ->
            playerExplorerData(clubId, matchId, playerId)
        }
    }

    fun exportData(clubId: ClubId, limit: Int = 20): List<Map<String, Any?>> {
        val matches = matchRepository.findRecent(clubId, limit)
        val rows = mutableListOf<Map<String, Any?>>()
        for (canonical in matches) {
            val perspectiveClubId = canonical.interpretation.perspectiveClubId.value
            val our = canonical.footballMatch.participants
                .firstOrNull { it.club.id.value == perspectiveClubId } ?: continue
            for (player in our.players) {
                val entries = parseAggregateEntries(player)
                if (entries.isEmpty()) {
                    rows.add(buildExportRow(clubId.value, canonical, player, null))
                } else {
                    for (entry in entries) {
                        rows.add(buildExportRow(clubId.value, canonical, player, entry))
                    }
                }
            }
        }
        return rows
    }

    private fun buildPlayerData(
        player: PlayerMatchPerformance,
        canonical: CanonicalMatch,
        opponentName: String?,
    ): PlayerExplorerData {
        val entries = parseAggregateEntries(player)
        return PlayerExplorerData(
            playerId = player.player.id.value,
            platformName = player.player.platformName?.value,
            proName = player.player.proName?.value,
            matchId = canonical.matchId.value,
            playedAt = canonical.footballMatch.playedAt.toString(),
            opponentName = opponentName,
            aggregateEntries = entries,
            knownStats = KnownStats(
                rating = player.rating?.value?.toPlainString(),
                goals = player.attacking.goals,
                assists = player.attacking.assists,
                shots = player.attacking.shots,
                passesAttempted = player.passing.attempted,
                passesCompleted = player.passing.completed,
                tacklesAttempted = player.defending.tacklesAttempted,
                tacklesCompleted = player.defending.tacklesCompleted,
                secondAssists = player.advanced.secondAssists,
                throughPasses = player.advanced.throughPasses,
                dribblesCompleted = player.advanced.dribblesCompleted,
                beats = player.advanced.beats,
                interceptions = player.advanced.interceptions,
                advancedCoverage = player.advancedCoverage.name,
            ),
            rawAggregate0 = player.rawEventAggregates?.aggregate0,
            rawAggregate1 = player.rawEventAggregates?.aggregate1,
            unknownFields = buildUnknownFieldsData(player),
        )
    }

    private fun buildUnknownFieldsData(player: PlayerMatchPerformance): UnknownFieldsData {
        val raw = player.rawUnknownFields
            ?: return UnknownFieldsData("UNAVAILABLE", emptyList())
        val fields = raw.fields
            ?: return UnknownFieldsData("UNAVAILABLE", emptyList())
        if (fields.isEmpty()) return UnknownFieldsData("EMPTY", emptyList())
        return UnknownFieldsData(
            status = "PRESENT",
            fields = fields.map { f ->
                UnknownFieldEntry(
                    name = f.name,
                    jsonType = f.jsonType,
                    value = f.value,
                    truncated = f.truncated,
                    originalSize = f.originalSize,
                    isAdditionalAggregate = f.name.startsWith("match_event_aggregate_") &&
                        f.name != "match_event_aggregate_0" && f.name != "match_event_aggregate_1",
                )
            },
        )
    }

    private fun parseAggregateEntries(player: PlayerMatchPerformance): List<AggregateEntry> {
        val raw = player.rawEventAggregates ?: return emptyList()
        val entries = mutableListOf<AggregateEntry>()
        parseHistogram(raw.aggregate0)?.forEach { (code, value) ->
            val mapping = AdvancedStatsCodeRegistry.lookup(0, code)
            entries.add(AggregateEntry(0, code, value, mapping.confidence.name, mapping.metricName, mapping.evidence))
        }
        parseHistogram(raw.aggregate1)?.forEach { (code, value) ->
            val mapping = AdvancedStatsCodeRegistry.lookup(1, code)
            entries.add(AggregateEntry(1, code, value, mapping.confidence.name, mapping.metricName, mapping.evidence))
        }
        entries.sortWith(compareBy({ it.aggregate }, { it.code }))
        return entries
    }

    private fun parseHistogram(raw: String?): Map<Int, Int>? {
        if (raw.isNullOrBlank()) return null
        return raw.split(',').mapNotNull { entry ->
            val parts = entry.split(':', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
            val code = parts[0].toIntOrNull() ?: return@mapNotNull null
            val value = parts[1].toIntOrNull()?.takeIf { it >= 0 } ?: return@mapNotNull null
            code to value
        }.toMap()
    }

    private fun buildExportRow(
        clubId: String,
        canonical: CanonicalMatch,
        player: PlayerMatchPerformance,
        entry: AggregateEntry?,
    ): Map<String, Any?> = mapOf(
        "clubId" to clubId,
        "matchId" to canonical.matchId.value,
        "timestamp" to canonical.footballMatch.playedAt.toString(),
        "playerId" to player.player.id.value,
        "playerName" to (player.player.platformName?.value ?: player.player.proName?.value),
        "aggregate" to entry?.aggregate,
        "code" to entry?.code,
        "value" to entry?.value,
        "confidence" to entry?.confidence,
        "metricName" to entry?.metricName,
        "rating" to player.rating?.value?.toPlainString(),
        "goals" to player.attacking.goals,
        "assists" to player.attacking.assists,
        "shots" to player.attacking.shots,
        "passesAttempted" to player.passing.attempted,
        "passesCompleted" to player.passing.completed,
        "tacklesAttempted" to player.defending.tacklesAttempted,
        "tacklesCompleted" to player.defending.tacklesCompleted,
        "unknownFieldsStatus" to (player.rawUnknownFields?.let { if (it.fields == null) "UNAVAILABLE" else if (it.fields.isEmpty()) "EMPTY" else "PRESENT" } ?: "UNAVAILABLE"),
        "unknownFieldCount" to (player.rawUnknownFields?.fields?.size ?: 0),
    )
}
