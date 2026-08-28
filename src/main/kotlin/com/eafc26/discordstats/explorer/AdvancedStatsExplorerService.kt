package com.eafc26.discordstats.explorer

import com.eafc26.discordstats.application.repository.CanonicalMatchRepository
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.MatchId
import com.eafc26.discordstats.domain.match.PlayerMatchPerformance
import com.eafc26.discordstats.domain.match.RawUnknownField
import com.eafc26.discordstats.canonical.CanonicalMatch
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.TextNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

class AdvancedStatsExplorerService(
    private val matchRepository: CanonicalMatchRepository,
    private val objectMapper: ObjectMapper = jacksonObjectMapper(),
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
        val scope: String,
        val name: String,
        val jsonType: String,
        val value: String,
        val truncated: Boolean,
        val originalSize: Int,
        val isAdditionalAggregateCandidate: Boolean,
    )

    /**
     * Export-only representation of one already-sanitized unknown EA field.
     *
     * Canonical storage deliberately keeps the JSON representation as text to
     * enforce capture limits. At this administrative boundary, complete values
     * are parsed back into JSON so their native structure and type are retained.
     * A truncated capture remains a JSON string preview and is explicitly marked.
     */
    data class UnknownFieldExport(
        val scope: String,
        val fieldName: String,
        val jsonType: String,
        val value: JsonNode,
        val truncated: Boolean,
        val originalSize: Int,
        val isAdditionalAggregateCandidate: Boolean,
    )

    data class UnknownFieldsExport(
        val status: String,
        val count: Int,
        val fields: List<UnknownFieldExport>,
    )

    data class AdditionalAggregateDataAlert(
        val fieldName: String,
        val matchCount: Int,
        val playerCount: Int,
    )

    data class DiscoveryData(
        val analysis: AdvancedStatsDiscoveryEngine.DiscoveryResult,
        val newAggregateDataDetected: List<AdditionalAggregateDataAlert>,
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

    /**
     * Flat CSV export for unknown-field analysis. Each captured field becomes a
     * row; players without captured fields keep one status row so UNAVAILABLE and
     * EMPTY remain distinguishable from PRESENT.
     */
    fun exportUnknownFieldsCsvData(clubId: ClubId, limit: Int = 20): List<Map<String, Any?>> {
        val matches = matchRepository.findRecent(clubId, limit)
        val rows = mutableListOf<Map<String, Any?>>()
        for (canonical in matches) {
            val perspectiveClubId = canonical.interpretation.perspectiveClubId.value
            val our = canonical.footballMatch.participants
                .firstOrNull { it.club.id.value == perspectiveClubId } ?: continue
            val opponent = canonical.footballMatch.participants
                .firstOrNull { it.club.id.value != perspectiveClubId }
            for (player in our.players) {
                val unknown = unknownFieldsForExport(player)
                if (unknown.fields.isEmpty()) {
                    rows.add(
                        baseUnknownFieldCsvRow(
                            clubId = clubId.value,
                            canonical = canonical,
                            player = player,
                            opponentName = opponent?.club?.name?.value,
                            status = unknown.status,
                            scope = player.rawUnknownFields?.scope ?: "player",
                        ),
                    )
                } else {
                    unknown.fields.forEach { field ->
                        rows.add(
                            baseUnknownFieldCsvRow(
                                clubId = clubId.value,
                                canonical = canonical,
                                player = player,
                                opponentName = opponent?.club?.name?.value,
                                status = unknown.status,
                                scope = field.scope,
                            ) + linkedMapOf(
                                "fieldName" to field.fieldName,
                                "jsonType" to field.jsonType,
                                "value" to csvValue(field),
                                "valueSize" to field.originalSize,
                                "truncated" to field.truncated,
                                "additionalAggregateCandidate" to field.isAdditionalAggregateCandidate,
                            ),
                        )
                    }
                }
            }
        }
        return rows
    }

    /**
     * Bounded Discovery Engine entry point. The repository supplies only the
     * requested recent window; all statistical work happens in memory.
     */
    fun discoveryData(
        clubId: ClubId,
        limit: Int = 10,
        aggregate: Int? = null,
        minimumMatches: Int = 0,
        minimumObservations: Int = 0,
        hideKnownRelationships: Boolean = true,
    ): DiscoveryData {
        // Scan at most twice the requested RAW window. Historical records with
        // no RAW transport data are excluded, but never treated as zeroes.
        val matches = matchRepository.findRecent(clubId, (limit * DISCOVERY_SCAN_MULTIPLIER).coerceAtMost(MAX_DISCOVERY_CANONICAL_MATCHES))
            .filter(::hasRawAggregateCoverage)
            .take(limit)
        val samples = matches.flatMap { canonical -> discoverySamples(clubId, canonical) }
        val result = AdvancedStatsDiscoveryEngine().analyze(samples, hideKnownRelationships)
        val filteredInventory = result.inventory.filter {
            (aggregate == null || it.aggregateIndex == aggregate) &&
                it.matchCount >= minimumMatches &&
                it.rawObservationCount >= minimumObservations
        }
        val visibleRefs = filteredInventory.map { AdvancedStatsDiscoveryEngine.CodeRef(it.aggregateIndex, it.code) }.toSet()
        fun visible(relation: AdvancedStatsDiscoveryEngine.DiscoveryRelation): Boolean =
            AdvancedStatsDiscoveryEngine.CodeRef(relation.aggregateIndex, relation.codeA) in visibleRefs &&
                AdvancedStatsDiscoveryEngine.CodeRef(relation.aggregateIndex, relation.codeB) in visibleRefs &&
                (relation.codeC == null || AdvancedStatsDiscoveryEngine.CodeRef(relation.aggregateIndex, relation.codeC) in visibleRefs)
        val filteredRelations = result.relations.filter(::visible)
        return DiscoveryData(
            analysis = result.copy(
                inventory = filteredInventory,
                relations = filteredRelations,
                topCandidates = filteredRelations.filter { it.evidenceTier != "COINCIDENCE" }.take(20),
                topDiscoverySignals = result.topDiscoverySignals.filter { signal ->
                    aggregate == null || signal.aggregateIndex == aggregate
                },
                correlations = result.correlations.filter {
                    AdvancedStatsDiscoveryEngine.CodeRef(it.aggregateIndex, it.codeA) in visibleRefs &&
                        AdvancedStatsDiscoveryEngine.CodeRef(it.aggregateIndex, it.codeB) in visibleRefs
                },
                calibration = result.calibration.filter {
                    AdvancedStatsDiscoveryEngine.CodeRef(it.aggregateIndex, it.code) in visibleRefs
                },
                relatedCodeFamilies = result.relatedCodeFamilies.filter { family ->
                    family.codes.all { code ->
                        AdvancedStatsDiscoveryEngine.CodeRef(family.aggregateIndex, code) in visibleRefs
                    }
                },
            ),
            newAggregateDataDetected = additionalAggregateAlerts(matches, clubId),
        )
    }

    fun anchorInvestigation(
        clubId: ClubId,
        limit: Int = 10,
        anchorType: String,
        aggregateIndex: Int?,
        code: Int?,
        metricName: String?,
    ): AdvancedStatsDiscoveryEngine.AnchorInvestigation {
        val matches = matchRepository.findRecent(clubId, (limit * DISCOVERY_SCAN_MULTIPLIER).coerceAtMost(MAX_DISCOVERY_CANONICAL_MATCHES))
            .filter(::hasRawAggregateCoverage)
            .take(limit)
        val samples = matches.flatMap { canonical -> discoverySamples(clubId, canonical) }
        val anchor = AdvancedStatsDiscoveryEngine.AnchorRef(anchorType, aggregateIndex, code, metricName)
        return AdvancedStatsDiscoveryEngine().investigateAnchor(samples, anchor)
    }

    fun familyInvestigation(
        clubId: ClubId,
        limit: Int = 10,
        aggregateIndex: Int,
        codes: List<Int>,
    ): AdvancedStatsDiscoveryEngine.FamilyInvestigation {
        val matches = matchRepository.findRecent(clubId, (limit * DISCOVERY_SCAN_MULTIPLIER).coerceAtMost(MAX_DISCOVERY_CANONICAL_MATCHES))
            .filter(::hasRawAggregateCoverage)
            .take(limit)
        val samples = matches.flatMap { canonical -> discoverySamples(clubId, canonical) }
        return AdvancedStatsDiscoveryEngine().investigateFamily(samples, aggregateIndex, codes)
    }

    fun residualExplainer(
        clubId: ClubId, limit: Int = 10,
        anchorType: String, aggregateIndex: Int?, code: Int?, metricName: String?,
        candidateAggregateIndex: Int, candidateCode: Int,
    ): AdvancedStatsDiscoveryEngine.ResidualExplainerResult {
        val matches = matchRepository.findRecent(clubId, (limit * DISCOVERY_SCAN_MULTIPLIER).coerceAtMost(MAX_DISCOVERY_CANONICAL_MATCHES))
            .filter(::hasRawAggregateCoverage).take(limit)
        val samples = matches.flatMap { canonical -> discoverySamples(clubId, canonical) }
        val anchor = AdvancedStatsDiscoveryEngine.AnchorRef(anchorType, aggregateIndex, code, metricName)
        val request = AdvancedStatsDiscoveryEngine.ResidualExplainerRequest(anchor, candidateAggregateIndex, candidateCode)
        return AdvancedStatsDiscoveryEngine().explainResiduals(samples, request)
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

    private fun discoverySamples(
        clubId: ClubId,
        canonical: CanonicalMatch,
    ): List<AdvancedStatsDiscoveryEngine.AggregateSample> {
        val perspectiveClubId = canonical.interpretation.perspectiveClubId.value
        val players = canonical.footballMatch.participants
            .firstOrNull { it.club.id.value == perspectiveClubId }
            ?.players
            .orEmpty()
        return players.flatMap { player ->
            val raw = player.rawEventAggregates ?: return@flatMap emptyList()
            listOfNotNull(
                raw.aggregate0?.let { aggregate -> discoverySample(clubId, canonical, player, 0, aggregate) },
                raw.aggregate1?.let { aggregate -> discoverySample(clubId, canonical, player, 1, aggregate) },
            )
        }
    }

    private fun hasRawAggregateCoverage(canonical: CanonicalMatch): Boolean {
        val perspectiveClubId = canonical.interpretation.perspectiveClubId.value
        return canonical.footballMatch.participants
            .firstOrNull { it.club.id.value == perspectiveClubId }
            ?.players
            ?.any { player ->
                player.rawEventAggregates?.let { it.aggregate0 != null || it.aggregate1 != null } == true
            }
            ?: false
    }

    private fun discoverySample(
        clubId: ClubId,
        canonical: CanonicalMatch,
        player: PlayerMatchPerformance,
        aggregateIndex: Int,
        rawAggregate: String,
    ): AdvancedStatsDiscoveryEngine.AggregateSample = AdvancedStatsDiscoveryEngine.AggregateSample(
        clubId = clubId.value,
        matchId = canonical.matchId.value,
        timestamp = canonical.footballMatch.playedAt.toString(),
        playerId = player.player.id.value,
        playerName = player.player.platformName?.value ?: player.player.proName?.value,
        aggregateIndex = aggregateIndex,
        sparseValues = parseHistogram(rawAggregate).orEmpty(),
        knownMetrics = linkedMapOf(
            "goals" to player.attacking.goals,
            "assists" to player.attacking.assists,
            "shots" to player.attacking.shots,
            "passesAttempted" to player.passing.attempted,
            "passesCompleted" to player.passing.completed,
            "tacklesAttempted" to player.defending.tacklesAttempted,
            "tacklesCompleted" to player.defending.tacklesCompleted,
        ),
        matchCompletion = canonical.footballMatch.completion.status.name,
    )

    private fun additionalAggregateAlerts(
        matches: List<CanonicalMatch>,
        clubId: ClubId,
    ): List<AdditionalAggregateDataAlert> {
        val sightings = mutableMapOf<String, MutableList<Pair<String, String>>>()
        matches.forEach { canonical ->
            if (canonical.interpretation.perspectiveClubId != clubId) return@forEach
            canonical.footballMatch.participants
                .firstOrNull { it.club.id == clubId }
                ?.players
                ?.forEach { player ->
                    player.rawUnknownFields?.fields
                        ?.filter { isAdditionalAggregateCandidate(it.name) && hasNonEmptyValue(it) }
                        ?.forEach { field ->
                            sightings.getOrPut(field.name) { mutableListOf() }
                                .add(canonical.matchId.value to player.player.id.value)
                        }
                }
        }
        return sightings.map { (fieldName, values) ->
            AdditionalAggregateDataAlert(
                fieldName = fieldName,
                matchCount = values.map { it.first }.toSet().size,
                playerCount = values.map { it.second }.toSet().size,
            )
        }.sortedBy { it.fieldName }
    }

    private fun hasNonEmptyValue(field: RawUnknownField): Boolean {
        if (field.truncated) return field.value.isNotBlank()
        val node = runCatching { objectMapper.readTree(field.value) }.getOrNull() ?: return field.value.isNotBlank()
        return when {
            node.isNull -> false
            node.isTextual -> node.asText().isNotBlank()
            node.isArray || node.isObject -> node.size() > 0
            else -> true
        }
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
                    scope = raw.scope,
                    name = f.name,
                    jsonType = f.jsonType,
                    value = f.value,
                    truncated = f.truncated,
                    originalSize = f.originalSize,
                    isAdditionalAggregateCandidate = isAdditionalAggregateCandidate(f.name),
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
    ): Map<String, Any?> {
        val unknownFields = unknownFieldsForExport(player)
        return mapOf(
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
            "unknownFieldsStatus" to unknownFields.status,
            "unknownFieldCount" to unknownFields.count,
            "unknownFields" to unknownFields.fields,
        )
    }

    private fun unknownFieldsForExport(player: PlayerMatchPerformance): UnknownFieldsExport {
        val raw = player.rawUnknownFields ?: return UnknownFieldsExport("UNAVAILABLE", 0, emptyList())
        val fields = raw.fields ?: return UnknownFieldsExport("UNAVAILABLE", 0, emptyList())
        if (fields.isEmpty()) return UnknownFieldsExport("EMPTY", 0, emptyList())
        return UnknownFieldsExport(
            status = "PRESENT",
            count = fields.size,
            fields = fields.map { field ->
                UnknownFieldExport(
                    scope = raw.scope,
                    fieldName = field.name,
                    jsonType = field.jsonType,
                    value = jsonValue(field),
                    truncated = field.truncated,
                    originalSize = field.originalSize,
                    isAdditionalAggregateCandidate = isAdditionalAggregateCandidate(field.name),
                )
            },
        )
    }

    private fun jsonValue(field: RawUnknownField): JsonNode {
        if (field.truncated) return TextNode.valueOf(field.value)
        return runCatching { objectMapper.readTree(field.value) }
            .getOrElse { TextNode.valueOf(field.value) }
    }

    private fun csvValue(field: UnknownFieldExport): String =
        if (field.truncated) {
            objectMapper.writeValueAsString(
                linkedMapOf(
                    "truncated" to true,
                    "preview" to field.value.asText(),
                    "originalSize" to field.originalSize,
                ),
            )
        } else {
            objectMapper.writeValueAsString(field.value)
        }

    private fun baseUnknownFieldCsvRow(
        clubId: String,
        canonical: CanonicalMatch,
        player: PlayerMatchPerformance,
        opponentName: String?,
        status: String,
        scope: String,
    ): LinkedHashMap<String, Any?> = linkedMapOf(
        "clubId" to clubId,
        "matchId" to canonical.matchId.value,
        "timestamp" to canonical.footballMatch.playedAt.toString(),
        "playerId" to player.player.id.value,
        "playerName" to (player.player.platformName?.value ?: player.player.proName?.value),
        "opponentName" to opponentName,
        "unknownFieldsStatus" to status,
        "scope" to scope,
        "fieldName" to null,
        "jsonType" to null,
        "value" to null,
        "valueSize" to null,
        "truncated" to null,
        "additionalAggregateCandidate" to false,
    )

    private fun isAdditionalAggregateCandidate(name: String): Boolean =
        Regex("^match_event_aggregate_(\\d+)$").matchEntire(name)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
            ?.let { it > 1 }
            ?: false

    private companion object {
        const val DISCOVERY_SCAN_MULTIPLIER = 2
        const val MAX_DISCOVERY_CANONICAL_MATCHES = 20
    }
}
