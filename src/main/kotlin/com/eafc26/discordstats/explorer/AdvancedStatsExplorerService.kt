package com.eafc26.discordstats.explorer

import com.eafc26.discordstats.application.repository.CanonicalMatchRepository
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.MatchId
import com.eafc26.discordstats.domain.match.PlayerMatchPerformance
import com.eafc26.discordstats.domain.match.RawEventAggregates
import com.eafc26.discordstats.domain.match.RawUnknownField
import com.eafc26.discordstats.ea.mapping.EaPositionCodeDecoder
import com.eafc26.discordstats.canonical.CanonicalMatch
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.TextNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

class AdvancedStatsExplorerService(
    private val matchRepository: CanonicalMatchRepository,
    private val objectMapper: ObjectMapper = jacksonObjectMapper(),
    private val observationRepository: ExplorerObservationRepository = InMemoryExplorerObservationRepository(),
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
        val rawAggregate2: String?,
        val rawAggregate3: String?,
        val rawContextFields: List<RawContextEntry>,
        val unknownFields: UnknownFieldsData,
        val eaPositionCode: String?,
        val eaPositionCandidate: EaPositionCodeDecoder.DecodedPosition,
    )

    data class RawContextEntry(
        val name: String,
        val jsonType: String,
        val value: String,
        val truncated: Boolean,
    )

    data class ObservationComparisonData(
        val phrase: String,
        val annotatedMatches: Int,
        val annotatedObservations: Int,
        val excludedRawUnavailable: Int,
        val contradictedCandidates: Int,
        val candidates: List<ObservationCandidateAnalyzer.CandidateAnalysis>,
        val observationCollisions: List<ObservationCandidateAnalyzer.ObservationCollision>,
        val nextBestExperiments: List<String>,
    )

    /**
     * A bounded, read-only administrative work queue derived from persisted
     * observations. It never stores a research conclusion or changes a RAW
     * mapping.
     */
    data class ObservationResearchQueueData(
        val researchIdentityLimit: Int,
        val canonicalMatchLimit: Int,
        val observationsPerIdentityLimit: Int,
        val researchIdentitiesRead: Int,
        val observationsRead: Int,
        val canonicalMatchesRead: Int,
        val identityWindowTruncated: Boolean,
        val canonicalWindowTruncated: Boolean,
        val summary: ObservationResearchQueueSummary = ObservationResearchQueueSummary(
            strongFeedbackAssociations = 0,
            emergingFeedbackAssociations = 0,
            directCounterCandidates = 0,
            directCounterRefutedWithAssociation = 0,
            readyForControlledTest = 0,
            provisionalValidations = 0,
        ),
        /** Bounded per-literal-identity diagnosis for research audit, never product analytics. */
        val identityDiagnostics: List<ResearchIdentityDiagnostic> = emptyList(),
        val items: List<ObservationResearchQueueItem> = emptyList(),
    )

    data class ResearchIdentityDiagnostic(
        val playerId: String,
        val phrase: String,
        /** Exact only when [historyTruncated] is false; otherwise a lower bound. */
        val observationsKnownAtLeast: Int,
        val observationsLoaded: Int,
        val historyTruncated: Boolean,
        val canonicalEvidenceUnavailable: Int,
        val unknownCandidatesGenerated: Int,
        val noiseCandidates: Int,
        val directCounterRefuted: Int,
        val associationInsufficient: Int,
        val hiddenByVisibility: Int,
        val strongestCandidates: List<ResearchDiagnosticCandidate>,
    )

    data class ResearchDiagnosticCandidate(
        val aggregateIndex: Int,
        val code: Int,
        val directCounterStatus: String,
        val feedbackAssociationStatus: String,
        val queueSection: String?,
    )

    /** Compact derived research state; it never represents public product metrics. */
    data class ObservationResearchQueueSummary(
        val strongFeedbackAssociations: Int,
        val emergingFeedbackAssociations: Int,
        val directCounterCandidates: Int,
        val directCounterRefutedWithAssociation: Int,
        val readyForControlledTest: Int,
        /** Always zero until controlled evidence obtains persisted provenance. */
        val provisionalValidations: Int,
    )

    data class ObservationResearchQueueItem(
        val playerId: String,
        val playerName: String?,
        val phrase: String,
        val aggregateIndex: Int,
        val code: Int,
        val researchState: String,
        val queueSection: String,
        val directCounterStatus: String = "INSUFFICIENT",
        val feedbackAssociationStatus: String = "INSUFFICIENT",
        val validationStatus: String = "NOT_VALIDATED",
        val directCounterValidity: String,
        val associationInterest: String,
        val researchPriority: String,
        val researchPriorityScore: Int,
        val comparableObservations: Int,
        val exactCoincidences: Int,
        val compatibleObservations: Int,
        val contradictions: Int,
        val trustworthyContradictions: Int,
        val totalExcess: Int,
        val collisionCandidates: List<ObservationCandidateAnalyzer.CandidateCollision>,
        val background: ResearchBackground = ResearchBackground(
            classification = ObservationResearchTriage.BackgroundDiscrimination.NOT_ENOUGH_BACKGROUND.name,
            explicitOtherPhraseObservations = 0,
            positiveOtherPhraseOccurrences = 0,
            distinctOtherPhrases = 0,
        ),
        val rawProvenance: ResearchRawProvenance,
        val evidenceTruncated: Boolean,
        /** One existing evidence record suitable for the lazy evidence audit. */
        val auditMatchId: String?,
        val nextActionType: String,
        val nextAction: String,
    )

    data class ResearchRawProvenance(
        val explicitValueEvidence: Int,
        val codeAbsentAssumedZeroEvidence: Int,
        val aggregateUnavailableEvidence: Int,
    )

    data class ResearchBackground(
        val classification: String,
        val explicitOtherPhraseObservations: Int,
        val positiveOtherPhraseOccurrences: Int,
        val distinctOtherPhrases: Int,
    )

    private data class ResearchQueueEvaluation(
        val item: ObservationResearchQueueItem,
        val triage: ObservationResearchTriage.Result,
    )

    /** Read-only, on-demand provenance for one exact candidate/evidence pair. */
    data class ObservationEvidenceAudit(
        val identity: ObservationIdentity,
        val canonicalMatch: AuditCanonicalMatch?,
        val player: AuditPlayer,
        val observation: AuditObservation,
        val playerMatchObservations: List<AuditObservationVectorEntry>,
        val vectorTruncated: Boolean,
        val candidate: AuditCandidate,
    )

    data class ObservationIdentity(
        val clubId: String,
        val matchId: String,
        val playerId: String,
        val phrase: String,
    )

    data class AuditCanonicalMatch(
        val clubId: String,
        val matchId: String,
        val playedAt: String,
        val ourClubName: String?,
        val opponentName: String?,
        val ourScore: Int,
        val opponentScore: Int,
        val outcome: String,
    )

    data class AuditPlayer(
        val playerId: String,
        val platformName: String?,
        val proName: String?,
    )

    data class AuditObservation(
        val phrase: String,
        val observedCount: Int,
        val completeness: String,
        val note: String?,
        val observedPositionContext: String?,
        val createdAt: String?,
        val updatedAt: String?,
    )

    data class AuditObservationVectorEntry(
        val phrase: String,
        val observedCount: Int,
        val completeness: String,
    )

    enum class RawAggregateProvenance {
        EXPLICIT_VALUE,
        CODE_ABSENT_ASSUMED_ZERO,
        AGGREGATE_UNAVAILABLE,
    }

    data class AuditRawAggregateEntry(
        val code: Int,
        val value: Int,
    )

    data class AuditCandidate(
        val aggregateIndex: Int,
        val code: Int,
        val provenance: RawAggregateProvenance,
        val explicitRawValue: Int?,
        val valueUsedByAnalyzer: Int?,
        val comparison: String?,
        val difference: Int?,
        val rawAggregate: String?,
        val rawEntries: List<AuditRawAggregateEntry>,
        val rawEntriesTruncated: Boolean,
    )

    data class PositionObservation(
        val matchId: String,
        val playedAt: String,
        val opponentName: String?,
        val playerId: String,
        val playerName: String?,
        val eaPositionCode: String?,
        val candidate: EaPositionCodeDecoder.DecodedPosition,
        val completion: String,
        val rating: String?,
    )

    data class PositionDistributionEntry(
        val eaPositionCode: String?,
        val candidate: EaPositionCodeDecoder.DecodedPosition,
        val observations: Int,
    )

    data class PositionObservationsData(
        val coverage: String,
        val observations: List<PositionObservation>,
        val distribution: List<PositionDistributionEntry>,
        val distinctCodes: Int,
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

    /** Uses one bounded canonical window; it does not query per code or player. */
    fun novelMetricDiscovery(clubId: ClubId, limit: Int = 10): NovelMetricDiscoveryEngine.Result =
        NovelMetricDiscoveryEngine().analyze(boundedDiscoverySamples(clubId, limit))

    fun novelMetricDetail(
        clubId: ClubId,
        limit: Int = 10,
        aggregateIndex: Int,
        code: Int,
    ): NovelMetricDiscoveryEngine.CandidateDetail? =
        NovelMetricDiscoveryEngine().detail(boundedDiscoverySamples(clubId, limit), aggregateIndex, code)

    fun positionObservations(clubId: ClubId, playerId: String, limit: Int = 20): PositionObservationsData {
        val rows = matchRepository.findRecent(clubId, limit).mapNotNull { canonical ->
            val perspective = canonical.interpretation.perspectiveClubId
            val player = canonical.footballMatch.participants.firstOrNull { it.club.id == perspective }
                ?.players?.firstOrNull { it.player.id.value == playerId } ?: return@mapNotNull null
            val opponent = canonical.footballMatch.participants.firstOrNull { it.club.id != perspective }
            PositionObservation(
                canonical.matchId.value, canonical.footballMatch.playedAt.toString(), opponent?.club?.name?.value,
                playerId, player.player.platformName?.value ?: player.player.proName?.value, player.eaPositionCode,
                EaPositionCodeDecoder.decode(player.eaPositionCode), canonical.footballMatch.completion.status.name,
                player.rating?.value?.toPlainString(),
            )
        }
        val coverage = when {
            rows.isEmpty() || rows.all { it.eaPositionCode == null } -> "UNAVAILABLE"
            rows.all { it.eaPositionCode != null } -> "FULL"
            else -> "PARTIAL"
        }
        val distribution = rows.groupBy { it.eaPositionCode }.map { (raw, values) ->
            PositionDistributionEntry(raw, EaPositionCodeDecoder.decode(raw), values.size)
        }.sortedWith(compareByDescending<PositionDistributionEntry> { it.observations }.thenBy { it.eaPositionCode ?: "" })
        return PositionObservationsData(coverage, rows, distribution, rows.mapNotNull { it.eaPositionCode }.toSet().size)
    }

    fun observationsForPlayerMatch(clubId: ClubId, matchId: MatchId, playerId: String): List<ExplorerObservation> =
        observationRepository.findForPlayerMatch(clubId, matchId, playerId)

    fun saveObservation(observation: ExplorerObservation): ExplorerObservation = observationRepository.save(observation)

    /**
     * Reconciles only an administrator-confirmed literal phrase. The analyzer
     * remains exact: this changes historical evidence rather than its query
     * semantics.
     */
    fun reconcileObservationPhrase(
        clubId: ClubId,
        matchId: MatchId,
        playerId: String,
        sourcePhrase: String,
        targetPhrase: String,
    ): ObservationPhraseReconciliationResult {
        if (targetPhrase.isBlank()) {
            return ObservationPhraseReconciliationResult(ObservationPhraseReconciliationStatus.INVALID_TARGET)
        }
        return observationRepository.reconcilePhrase(clubId, matchId, playerId, sourcePhrase, targetPhrase)
    }

    fun distinctPhrasesForPlayer(clubId: ClubId, playerId: String, limit: Int = 50): List<String> =
        observationRepository.findForPlayer(clubId, playerId, limit)
            .map { it.phrase }
            .distinct()

    // ── Bulk import ─────────────────────────────────────────────────────

    enum class ObservationImportStatus { NEW, ALREADY_EXISTS, CONFLICT, INVALID }

    data class ObservationImportRecord(
        val index: Int,
        val matchId: String,
        val playerId: String,
        val phrase: String,
        val observedCount: Int,
        val completeness: ObservationCompleteness,
        val status: ObservationImportStatus,
        val reason: String? = null,
        val existingObservedCount: Int? = null,
        val existingCompleteness: ObservationCompleteness? = null,
        val existingNote: String? = null,
    )

    data class ObservationImportPreview(
        val total: Int,
        val newCount: Int,
        val alreadyExistsCount: Int,
        val conflictCount: Int,
        val invalidCount: Int,
        val records: List<ObservationImportRecord>,
    )

    data class ObservationImportResult(
        val inserted: Int,
        val alreadyExisted: Int,
        val total: Int,
    )

    data class ObservationImportInput(
        val matchId: String,
        val playerId: String,
        val phrase: String,
        val observedCount: Int,
        val completeness: ObservationCompleteness = ObservationCompleteness.AT_LEAST,
        val note: String? = null,
        val observedPositionContext: String? = null,
    )

    fun previewObservationImport(clubId: ClubId, inputs: List<ObservationImportInput>): ObservationImportPreview {
        if (inputs.isEmpty()) return ObservationImportPreview(0, 0, 0, 0, 1, listOf(
            ObservationImportRecord(0, "", "", "", 0, ObservationCompleteness.AT_LEAST, ObservationImportStatus.INVALID, "Empty payload"),
        ))
        if (inputs.size > 50) return ObservationImportPreview(inputs.size, 0, 0, 0, 1, listOf(
            ObservationImportRecord(0, "", "", "", 0, ObservationCompleteness.AT_LEAST, ObservationImportStatus.INVALID, "Batch exceeds 50 observation limit (received ${inputs.size})"),
        ))

        val records = mutableListOf<ObservationImportRecord>()

        // Phase 1: validate each record individually
        val validInputs = mutableListOf<Pair<Int, ObservationImportInput>>()
        for ((index, input) in inputs.withIndex()) {
            val validationError = validateInput(input)
            if (validationError != null) {
                records.add(ObservationImportRecord(index, input.matchId, input.playerId, input.phrase, input.observedCount, input.completeness, ObservationImportStatus.INVALID, validationError))
            } else {
                validInputs.add(index to input)
            }
        }

        // Phase 2: detect intra-batch duplicates
        val grouped = validInputs.groupBy { (_, input) -> Triple(input.matchId, input.playerId, input.phrase) }
        val duplicateConflicts = mutableSetOf<Triple<String, String, String>>()
        val deduplicatedInputs = mutableListOf<Pair<Int, ObservationImportInput>>()
        for ((key, group) in grouped) {
            if (group.size > 1) {
                val allIdentical = group.all { (_, inp) ->
                    inp.observedCount == group[0].second.observedCount &&
                        inp.completeness == group[0].second.completeness &&
                        inp.note == group[0].second.note &&
                        inp.observedPositionContext == group[0].second.observedPositionContext
                }
                if (allIdentical) {
                    deduplicatedInputs.add(group[0])
                    for (dup in group.drop(1)) {
                        records.add(ObservationImportRecord(dup.first, dup.second.matchId, dup.second.playerId, dup.second.phrase, dup.second.observedCount, dup.second.completeness, ObservationImportStatus.ALREADY_EXISTS, "Duplicate of record ${group[0].first} (identical, deduplicated)"))
                    }
                } else {
                    duplicateConflicts.add(key)
                    for ((idx, inp) in group) {
                        records.add(ObservationImportRecord(idx, inp.matchId, inp.playerId, inp.phrase, inp.observedCount, inp.completeness, ObservationImportStatus.CONFLICT, "Conflicting duplicate within batch for same identity"))
                    }
                }
            } else {
                deduplicatedInputs.add(group[0])
            }
        }

        // Phase 3: batch-validate matches and players
        val uniqueMatchIds = deduplicatedInputs.map { (_, inp) -> MatchId(inp.matchId) }.toSet()
        val canonicalMatches = matchRepository.findByIds(clubId, uniqueMatchIds).associateBy { it.matchId }
        val matchPlayerSets = mutableMapOf<MatchId, Set<String>>()
        for ((matchId, canonical) in canonicalMatches) {
            val perspective = canonical.interpretation.perspectiveClubId
            val ourPlayers = canonical.footballMatch.participants
                .firstOrNull { it.club.id == perspective }
                ?.players?.map { it.player.id.value }?.toSet() ?: emptySet()
            matchPlayerSets[matchId] = ourPlayers
        }

        // Phase 4: classify against canonical data
        val canonicallyValid = mutableListOf<Pair<Int, ObservationImportInput>>()
        for ((index, input) in deduplicatedInputs) {
            val mid = MatchId(input.matchId)
            if (mid !in canonicalMatches) {
                records.add(ObservationImportRecord(index, input.matchId, input.playerId, input.phrase, input.observedCount, input.completeness, ObservationImportStatus.INVALID, "Match not found in canonical data"))
                continue
            }
            val canonical = canonicalMatches[mid]!!
            if (canonical.interpretation.perspectiveClubId != clubId) {
                records.add(ObservationImportRecord(index, input.matchId, input.playerId, input.phrase, input.observedCount, input.completeness, ObservationImportStatus.INVALID, "Match does not belong to this club"))
                continue
            }
            val playerIds = matchPlayerSets[mid] ?: emptySet()
            if (input.playerId !in playerIds) {
                records.add(ObservationImportRecord(index, input.matchId, input.playerId, input.phrase, input.observedCount, input.completeness, ObservationImportStatus.INVALID, "Player not found in this match"))
                continue
            }
            canonicallyValid.add(index to input)
        }

        // Phase 5: batch lookup existing observations
        val identityKeys = canonicallyValid.map { (_, inp) -> ObservationIdentityKey(MatchId(inp.matchId), inp.playerId, inp.phrase) }
        val existingObs = observationRepository.findByIdentities(clubId, identityKeys)
            .associateBy { Triple(it.matchId.value, it.playerId, it.phrase) }

        for ((index, input) in canonicallyValid) {
            val key = Triple(input.matchId, input.playerId, input.phrase)
            val existing = existingObs[key]
            if (existing == null) {
                records.add(ObservationImportRecord(index, input.matchId, input.playerId, input.phrase, input.observedCount, input.completeness, ObservationImportStatus.NEW))
            } else {
                val conflicts = mutableListOf<String>()
                if (existing.observedCount != input.observedCount) conflicts.add("observedCount: existing=${existing.observedCount}, submitted=${input.observedCount}")
                if (existing.completeness != input.completeness) conflicts.add("completeness: existing=${existing.completeness}, submitted=${input.completeness}")
                if (existing.note != input.note) conflicts.add("note: existing=${existing.note}, submitted=${input.note}")
                if (existing.observedPositionContext != input.observedPositionContext) conflicts.add("observedPositionContext: existing=${existing.observedPositionContext}, submitted=${input.observedPositionContext}")
                if (conflicts.isEmpty()) {
                    records.add(ObservationImportRecord(index, input.matchId, input.playerId, input.phrase, input.observedCount, input.completeness, ObservationImportStatus.ALREADY_EXISTS, "Identical observation already exists"))
                } else {
                    records.add(ObservationImportRecord(index, input.matchId, input.playerId, input.phrase, input.observedCount, input.completeness, ObservationImportStatus.CONFLICT, conflicts.joinToString("; "), existing.observedCount, existing.completeness, existing.note))
                }
            }
        }

        records.sortBy { it.index }
        val newCount = records.count { it.status == ObservationImportStatus.NEW }
        val alreadyExistsCount = records.count { it.status == ObservationImportStatus.ALREADY_EXISTS }
        val conflictCount = records.count { it.status == ObservationImportStatus.CONFLICT }
        val invalidCount = records.count { it.status == ObservationImportStatus.INVALID }
        return ObservationImportPreview(inputs.size, newCount, alreadyExistsCount, conflictCount, invalidCount, records)
    }

    fun importObservations(clubId: ClubId, inputs: List<ObservationImportInput>): ObservationImportResult {
        val preview = previewObservationImport(clubId, inputs)
        if (preview.conflictCount > 0) throw IllegalStateException("Cannot import: ${preview.conflictCount} conflict(s) detected")
        if (preview.invalidCount > 0) throw IllegalStateException("Cannot import: ${preview.invalidCount} invalid record(s) detected")

        val newRecords = preview.records.filter { it.status == ObservationImportStatus.NEW }
        if (newRecords.isEmpty()) return ObservationImportResult(0, preview.alreadyExistsCount, preview.total)

        val inputsByKey = inputs.associateBy { Triple(it.matchId, it.playerId, it.phrase) }
        val toInsert = newRecords.mapNotNull { record ->
            val input = inputsByKey[Triple(record.matchId, record.playerId, record.phrase)] ?: return@mapNotNull null
            ExplorerObservation(
                clubId = clubId,
                matchId = MatchId(input.matchId),
                playerId = input.playerId,
                phrase = input.phrase,
                observedCount = input.observedCount,
                completeness = input.completeness,
                note = input.note,
                observedPositionContext = input.observedPositionContext,
            )
        }

        val inserted = observationRepository.insertIfAbsent(clubId, toInsert)
        return ObservationImportResult(inserted, preview.alreadyExistsCount, preview.total)
    }

    private fun validateInput(input: ObservationImportInput): String? {
        if (input.phrase.isBlank()) return "phrase must not be blank"
        if (input.observedCount < 0) return "observedCount must be non-negative"
        if (input.matchId.isBlank()) return "matchId must not be blank"
        if (input.playerId.isBlank()) return "playerId must not be blank"
        return null
    }

    /**
     * Bounded, evidence-only comparison. Every available RAW namespace joins
     * the comparison; an absent code in an available sparse namespace is zero,
     * while an absent namespace remains unavailable and is excluded.
     */
    fun compareObservations(clubId: ClubId, playerId: String, phrase: String, limit: Int = 20): ObservationComparisonData {
        val observations = observationRepository.findForPlayerPhrase(clubId, playerId, phrase, limit)
        val matchesById = matchRepository.findByIds(clubId, observations.map { it.matchId }).associateBy { it.matchId }
        val allPlayerObservations = (observationRepository.findForPlayer(clubId, playerId, limit) + observations)
            .distinctBy { listOf(it.matchId.value, it.phrase, it.observedCount.toString(), it.completeness.name) }
        val inputs = mutableListOf<ObservationCandidateAnalyzer.ObservationInput>()
        var rawUnavailable = 0

        observations.forEach { observation ->
            val canonical = matchesById[observation.matchId]
            val player = canonical?.let { match -> perspectivePlayer(match, playerId) }
            val raw = player?.rawEventAggregates
            if (raw == null) {
                rawUnavailable++
                return@forEach
            }
            val aggregates = linkedMapOf<Int, Map<Int, Int>>()
            aggregateSlots(raw).forEach { (aggregateIndex, rawValue) ->
                val histogram = parseHistogram(rawValue)
                if (histogram == null) {
                    rawUnavailable++
                } else {
                    aggregates[aggregateIndex] = histogram
                }
            }
            if (aggregates.isNotEmpty()) {
                inputs += ObservationCandidateAnalyzer.ObservationInput(
                    matchId = observation.matchId.value,
                    opponentName = canonical.let(::opponentName),
                    observedCount = observation.observedCount,
                    completeness = observation.completeness,
                    aggregates = aggregates,
                )
            }
        }
        val analysis = ObservationCandidateAnalyzer().analyze(
            phrase = phrase,
            observations = inputs,
            allPlayerObservations = allPlayerObservations.map {
                ObservationCandidateAnalyzer.RecordedObservation(
                    matchId = it.matchId.value,
                    phrase = it.phrase,
                    observedCount = it.observedCount,
                    completeness = it.completeness,
                )
            },
        )
        return ObservationComparisonData(
            phrase = analysis.phrase,
            annotatedMatches = observations.map { it.matchId }.toSet().size,
            annotatedObservations = observations.size,
            excludedRawUnavailable = rawUnavailable,
            contradictedCandidates = analysis.contradictedCandidates,
            candidates = analysis.candidates,
            observationCollisions = analysis.observationCollisions,
            nextBestExperiments = analysis.nextBestExperiments,
        )
    }

    /**
     * Derives a club-scoped research queue from bounded exact-identity evidence.
     * Identity discovery and evidence loading are deliberately separate: a
     * global newest-row slice is not a trustworthy history for a phrase. It
     * performs two bounded observation reads and one batch canonical lookup;
     * it never calls EA, persists a queue state, or eagerly loads audit detail.
     */
    fun observationResearchQueue(clubId: ClubId): ObservationResearchQueueData {
        val identitiesWithSentinel = observationRepository.findRecentResearchIdentities(
            clubId,
            RESEARCH_QUEUE_IDENTITY_LIMIT + 1,
        )
        val identityWindowTruncated = identitiesWithSentinel.size > RESEARCH_QUEUE_IDENTITY_LIMIT
        val selectedIdentities = identitiesWithSentinel.take(RESEARCH_QUEUE_IDENTITY_LIMIT)
        val evidenceWithSentinel = observationRepository.findRecentForResearchIdentities(
            clubId,
            selectedIdentities,
            RESEARCH_QUEUE_OBSERVATIONS_PER_IDENTITY_LIMIT + 1,
        )
        val evidenceByIdentity = evidenceWithSentinel.groupBy { ObservationResearchIdentity(it.playerId, it.phrase) }
        val selectedGroupObservations = selectedIdentities.associateWith { identity ->
            evidenceByIdentity[identity].orEmpty().take(RESEARCH_QUEUE_OBSERVATIONS_PER_IDENTITY_LIMIT)
        }
        val queueObservations = selectedGroupObservations.values.flatten()
            .sortedWith(compareByDescending<ExplorerObservation> { it.updatedAt }.thenByDescending { it.createdAt }.thenBy { it.matchId.value }.thenBy { it.playerId }.thenBy { it.phrase })
        val selectedMatchIds = linkedSetOf<MatchId>()
        queueObservations.forEach { observation ->
            if (selectedMatchIds.size < RESEARCH_QUEUE_CANONICAL_MATCH_LIMIT) selectedMatchIds += observation.matchId
        }
        val canonicalWindowTruncated = queueObservations.any { it.matchId !in selectedMatchIds }
        val matchesById = matchRepository.findByIds(clubId, selectedMatchIds).associateBy { it.matchId }
        val sourcesByIdentity = selectedGroupObservations.mapValues { (identity, observations) ->
            observations.map { observation -> researchSource(observation, matchesById[observation.matchId], identity.playerId) }
        }
        val triage = ObservationResearchTriage()
        val evaluatedCandidates = selectedIdentities.flatMap { identity ->
            val playerId = identity.playerId
            val phrase = identity.phrase
            val allPhraseObservations = evidenceByIdentity[identity].orEmpty()
            val phraseObservations = selectedGroupObservations.getValue(identity)
            val sources = sourcesByIdentity.getValue(identity)
            val inputs = sources.mapNotNull { source ->
                val available = source.aggregateHistograms.mapNotNull { (aggregateIndex, histogram) ->
                    histogram?.let { aggregateIndex to it }
                }.toMap()
                if (available.isEmpty()) null else ObservationCandidateAnalyzer.ObservationInput(
                    matchId = source.observation.matchId.value,
                    opponentName = source.canonical?.let(::opponentName),
                    observedCount = source.observation.observedCount,
                    completeness = source.observation.completeness,
                    aggregates = available,
                )
            }
            if (inputs.isEmpty()) return@flatMap emptyList()

            val allPlayerObservations = queueObservations.asSequence()
                .filter { it.playerId == playerId }
                .map {
                    ObservationCandidateAnalyzer.RecordedObservation(
                        matchId = it.matchId.value,
                        phrase = it.phrase,
                        observedCount = it.observedCount,
                        completeness = it.completeness,
                    )
                }
                .distinctBy { listOf(it.matchId, it.phrase, it.observedCount, it.completeness) }
                .toList()
            val analysis = ObservationCandidateAnalyzer().analyze(phrase, inputs, allPlayerObservations)
            val groupCanonicalTruncated = phraseObservations.any { it.matchId !in selectedMatchIds }
            val phraseWindowTruncated = allPhraseObservations.size > RESEARCH_QUEUE_OBSERVATIONS_PER_IDENTITY_LIMIT
            analysis.candidates.asSequence()
                .filter { it.candidateKind == ObservationCandidateAnalyzer.CandidateKind.UNKNOWN_CANDIDATE.name }
                .map { candidate ->
                    val provenance = researchProvenance(sources, candidate.aggregateIndex, candidate.code)
                    val explicitEvidence = explicitResearchEvidence(sources, candidate)
                    val background = researchBackground(
                        sourcesByIdentity = sourcesByIdentity,
                        currentIdentity = identity,
                        aggregateIndex = candidate.aggregateIndex,
                        code = candidate.code,
                    )
                    val trustworthyContradictions = candidate.evidence.count { evidence ->
                        val source = sources.firstOrNull { it.observation.matchId.value == evidence.matchId }
                        source?.provenance(candidate.aggregateIndex, candidate.code) == RawAggregateProvenance.EXPLICIT_VALUE &&
                            isTrustworthyDirectContradiction(evidence)
                    }
                    // Only truncation of this exact identity or of its selected
                    // canonical facts affects its maturity. Deferred identities
                    // do not silently turn a complete phrase history into a
                    // globally incomplete one.
                    val evidenceTruncated =
                        groupCanonicalTruncated ||
                            phraseWindowTruncated
                    val result = triage.triage(
                        ObservationResearchTriage.CandidateInput(
                            phrase = phrase,
                            candidate = candidate,
                            provenance = provenance,
                            explicitEvidence = explicitEvidence,
                            background = background,
                            trustworthyContradictions = trustworthyContradictions,
                            evidenceTruncated = evidenceTruncated,
                            hasMeaningfulVariation = hasMeaningfulVariation(sources, candidate),
                        ),
                    )
                    ResearchQueueEvaluation(
                        triage = result,
                        item = ObservationResearchQueueItem(
                        playerId = playerId,
                        playerName = sources.firstNotNullOfOrNull { source ->
                            source.player?.player?.platformName?.value ?: source.player?.player?.proName?.value
                        },
                        phrase = phrase,
                        aggregateIndex = candidate.aggregateIndex,
                        code = candidate.code,
                        researchState = result.state.name,
                        queueSection = result.queueSection?.name ?: HIDDEN_RESEARCH_QUEUE_SECTION,
                        directCounterStatus = result.directCounterStatus.name,
                        feedbackAssociationStatus = result.feedbackAssociationStatus.name,
                        validationStatus = result.validationStatus.name,
                        directCounterValidity = result.directCounterValidity.name,
                        associationInterest = result.associationInterest.name,
                        researchPriority = result.priority.name,
                        researchPriorityScore = result.priorityScore,
                        comparableObservations = explicitEvidence.comparableObservations,
                        exactCoincidences = explicitEvidence.exactCoincidences,
                        compatibleObservations = explicitEvidence.compatibleObservations,
                        contradictions = explicitEvidence.explicitContradictions,
                        trustworthyContradictions = trustworthyContradictions,
                        totalExcess = candidate.totalExcess,
                        collisionCandidates = candidate.candidateCollisions,
                        background = ResearchBackground(
                            classification = background.classification.name,
                            explicitOtherPhraseObservations = background.explicitOtherPhraseObservations,
                            positiveOtherPhraseOccurrences = background.positiveOtherPhraseOccurrences,
                            distinctOtherPhrases = background.distinctOtherPhrases,
                        ),
                        rawProvenance = ResearchRawProvenance(
                            explicitValueEvidence = provenance.explicitValueEvidence,
                            codeAbsentAssumedZeroEvidence = provenance.codeAbsentAssumedZeroEvidence,
                            aggregateUnavailableEvidence = provenance.aggregateUnavailableEvidence,
                        ),
                        evidenceTruncated = evidenceTruncated,
                        auditMatchId = candidate.evidence.firstOrNull { evidence ->
                            val source = sources.firstOrNull { it.observation.matchId.value == evidence.matchId }
                            source?.provenance(candidate.aggregateIndex, candidate.code) == RawAggregateProvenance.EXPLICIT_VALUE &&
                                isTrustworthyDirectContradiction(evidence)
                        }?.matchId ?: candidate.evidence.firstOrNull()?.matchId,
                        nextActionType = result.nextAction.name,
                        nextAction = result.nextActionText,
                    ),
                    )
                }
                .toList()
        }
        val items = evaluatedCandidates
            .filter { it.triage.queueSection != null }
            .map { it.item }
            .sortedWith(
            compareBy<ObservationResearchQueueItem> { queueSectionOrder(it.queueSection) }
                .thenBy { priorityOrder(it.researchPriority) }
                .thenByDescending { it.researchPriorityScore }
                .thenByDescending { it.comparableObservations }
                .thenBy { it.phrase }
                .thenBy { it.aggregateIndex }
                .thenBy { it.code },
            ).take(RESEARCH_QUEUE_MAX_ITEMS)
        val summary = ObservationResearchQueueSummary(
            strongFeedbackAssociations = evaluatedCandidates.count {
                it.triage.feedbackAssociationStatus == ObservationResearchTriage.FeedbackAssociationStatus.STRONG
            },
            emergingFeedbackAssociations = evaluatedCandidates.count {
                it.triage.feedbackAssociationStatus == ObservationResearchTriage.FeedbackAssociationStatus.EMERGING
            },
            directCounterCandidates = evaluatedCandidates.count {
                it.triage.directCounterStatus == ObservationResearchTriage.DirectCounterStatus.PROMISING
            },
            directCounterRefutedWithAssociation = evaluatedCandidates.count {
                it.triage.directCounterStatus == ObservationResearchTriage.DirectCounterStatus.REFUTED &&
                    it.triage.feedbackAssociationStatus != ObservationResearchTriage.FeedbackAssociationStatus.INSUFFICIENT
            },
            readyForControlledTest = evaluatedCandidates.count {
                it.triage.state == ObservationResearchTriage.ResearchState.READY_FOR_CONTROLLED_TEST
            },
            provisionalValidations = evaluatedCandidates.count {
                it.triage.validationStatus == ObservationResearchTriage.ValidationStatus.PROVISIONAL_VALIDATED
            },
        )
        val identityDiagnostics = selectedIdentities.map { identity ->
            val evaluations = evaluatedCandidates.filter {
                it.item.playerId == identity.playerId && it.item.phrase == identity.phrase
            }
            val allPhraseEvidence = evidenceByIdentity[identity].orEmpty()
            val loadedSources = sourcesByIdentity.getValue(identity)
            ResearchIdentityDiagnostic(
                playerId = identity.playerId,
                phrase = identity.phrase,
                observationsKnownAtLeast = allPhraseEvidence.size,
                observationsLoaded = selectedGroupObservations.getValue(identity).size,
                historyTruncated = allPhraseEvidence.size > RESEARCH_QUEUE_OBSERVATIONS_PER_IDENTITY_LIMIT,
                canonicalEvidenceUnavailable = loadedSources.count { it.canonical == null },
                unknownCandidatesGenerated = evaluations.size,
                noiseCandidates = evaluations.count {
                    it.triage.directCounterStatus == ObservationResearchTriage.DirectCounterStatus.INSUFFICIENT &&
                        it.triage.feedbackAssociationStatus == ObservationResearchTriage.FeedbackAssociationStatus.INSUFFICIENT
                },
                directCounterRefuted = evaluations.count {
                    it.triage.directCounterStatus == ObservationResearchTriage.DirectCounterStatus.REFUTED
                },
                associationInsufficient = evaluations.count {
                    it.triage.feedbackAssociationStatus == ObservationResearchTriage.FeedbackAssociationStatus.INSUFFICIENT
                },
                hiddenByVisibility = evaluations.count { it.triage.queueSection == null },
                strongestCandidates = evaluations.sortedWith(
                    compareByDescending<ResearchQueueEvaluation> { it.triage.feedbackAssociationStatus.ordinal }
                        .thenByDescending { it.triage.directCounterStatus.ordinal }
                        .thenByDescending { it.item.researchPriorityScore }
                        .thenBy { it.item.aggregateIndex }
                        .thenBy { it.item.code },
                ).take(RESEARCH_IDENTITY_DIAGNOSTIC_CANDIDATE_LIMIT).map { evaluation ->
                    ResearchDiagnosticCandidate(
                        aggregateIndex = evaluation.item.aggregateIndex,
                        code = evaluation.item.code,
                        directCounterStatus = evaluation.triage.directCounterStatus.name,
                        feedbackAssociationStatus = evaluation.triage.feedbackAssociationStatus.name,
                        queueSection = evaluation.triage.queueSection?.name,
                    )
                },
            )
        }

        return ObservationResearchQueueData(
            researchIdentityLimit = RESEARCH_QUEUE_IDENTITY_LIMIT,
            canonicalMatchLimit = RESEARCH_QUEUE_CANONICAL_MATCH_LIMIT,
            observationsPerIdentityLimit = RESEARCH_QUEUE_OBSERVATIONS_PER_IDENTITY_LIMIT,
            researchIdentitiesRead = selectedIdentities.size,
            observationsRead = queueObservations.size,
            canonicalMatchesRead = matchesById.size,
            identityWindowTruncated = identityWindowTruncated,
            canonicalWindowTruncated = canonicalWindowTruncated,
            summary = summary,
            identityDiagnostics = identityDiagnostics,
            items = items,
        )
    }

    /**
     * Loads only the facts needed to audit one exact persisted observation.
     * It intentionally runs outside the normal candidate comparison path.
     */
    fun observationEvidenceAudit(
        clubId: ClubId,
        matchId: MatchId,
        playerId: String,
        phrase: String,
        aggregateIndex: Int,
        code: Int,
    ): ObservationEvidenceAudit? {
        require(aggregateIndex in 0..3) { "aggregateIndex must be 0-3" }
        require(code >= 0) { "code must be non-negative" }

        val observation = observationRepository.findExact(clubId, matchId, playerId, phrase) ?: return null
        val vectorWithSentinel = observationRepository.findForPlayerMatchLimited(
            clubId,
            matchId,
            playerId,
            AUDIT_VECTOR_LIMIT + 1,
        )
        val vectorTruncated = vectorWithSentinel.size > AUDIT_VECTOR_LIMIT
        val vector = vectorWithSentinel.take(AUDIT_VECTOR_LIMIT)
        val canonical = matchRepository.findById(clubId, matchId)
        val player = canonical?.let { perspectivePlayer(it, playerId) }
        val rawAggregate = player?.rawEventAggregates?.let { aggregateSlot(it, aggregateIndex) }
        val histogram = parseHistogram(rawAggregate)
        val explicitRawValue = histogram?.get(code)
        val provenance = when {
            rawAggregate == null -> RawAggregateProvenance.AGGREGATE_UNAVAILABLE
            histogram?.containsKey(code) == true -> RawAggregateProvenance.EXPLICIT_VALUE
            else -> RawAggregateProvenance.CODE_ABSENT_ASSUMED_ZERO
        }
        val valueUsedByAnalyzer = when (provenance) {
            RawAggregateProvenance.EXPLICIT_VALUE -> explicitRawValue
            RawAggregateProvenance.CODE_ABSENT_ASSUMED_ZERO -> 0
            RawAggregateProvenance.AGGREGATE_UNAVAILABLE -> null
        }
        val rawEntries = histogram.orEmpty().entries
            .sortedBy { it.key }
            .take(AUDIT_RAW_ENTRY_LIMIT)
            .map { AuditRawAggregateEntry(it.key, it.value) }

        return ObservationEvidenceAudit(
            identity = ObservationIdentity(clubId.value, matchId.value, playerId, observation.phrase),
            canonicalMatch = canonical?.let { it.toAuditCanonicalMatch() },
            player = AuditPlayer(
                playerId = playerId,
                platformName = player?.player?.platformName?.value,
                proName = player?.player?.proName?.value,
            ),
            observation = observation.toAuditObservation(),
            playerMatchObservations = vector.map { it.toAuditVectorEntry() },
            vectorTruncated = vectorTruncated,
            candidate = AuditCandidate(
                aggregateIndex = aggregateIndex,
                code = code,
                provenance = provenance,
                explicitRawValue = explicitRawValue,
                valueUsedByAnalyzer = valueUsedByAnalyzer,
                comparison = valueUsedByAnalyzer?.let {
                    ObservationCandidateAnalyzer.comparisonFor(observation.completeness, it, observation.observedCount).name
                },
                difference = valueUsedByAnalyzer?.minus(observation.observedCount),
                rawAggregate = rawAggregate,
                rawEntries = rawEntries,
                rawEntriesTruncated = histogram.orEmpty().size > AUDIT_RAW_ENTRY_LIMIT,
            ),
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
        val samples = boundedDiscoverySamples(clubId, limit)
        val anchor = AdvancedStatsDiscoveryEngine.AnchorRef(anchorType, aggregateIndex, code, metricName)
        return AdvancedStatsDiscoveryEngine().investigateAnchor(samples, anchor)
    }

    fun familyInvestigation(
        clubId: ClubId,
        limit: Int = 10,
        aggregateIndex: Int,
        codes: List<Int>,
    ): AdvancedStatsDiscoveryEngine.FamilyInvestigation {
        val samples = boundedDiscoverySamples(clubId, limit)
        return AdvancedStatsDiscoveryEngine().investigateFamily(samples, aggregateIndex, codes)
    }

    fun residualExplainer(
        clubId: ClubId, limit: Int = 10,
        anchorType: String, aggregateIndex: Int?, code: Int?, metricName: String?,
        candidateAggregateIndex: Int, candidateCode: Int,
    ): AdvancedStatsDiscoveryEngine.ResidualExplainerResult {
        val samples = boundedDiscoverySamples(clubId, limit)
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
                beats = player.advanced.beats,
                interceptions = player.advanced.interceptions,
                advancedCoverage = player.advancedCoverage.name,
            ),
            rawAggregate0 = player.rawEventAggregates?.aggregate0,
            rawAggregate1 = player.rawEventAggregates?.aggregate1,
            rawAggregate2 = player.rawEventAggregates?.aggregate2,
            rawAggregate3 = player.rawEventAggregates?.aggregate3,
            rawContextFields = rawContextFields(player),
            unknownFields = buildUnknownFieldsData(player),
            eaPositionCode = player.eaPositionCode,
            eaPositionCandidate = EaPositionCodeDecoder.decode(player.eaPositionCode),
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
    ): AdvancedStatsDiscoveryEngine.AggregateSample {
        // The four advanced controls are factual zeroes only when the source
        // aggregate is present. For aggregate 1, only reuse the decoded
        // advanced values when their coverage is complete; historical or
        // partial transport data must remain unavailable rather than becoming
        // synthetic zeroes in Novel Metric Discovery.
        val advancedControls = if (player.advancedCoverage == com.eafc26.discordstats.domain.match.AdvancedStatsCoverage.FULL) {
            mapOf(
                "beats" to player.advanced.beats,
                "preAssists" to player.advanced.secondAssists,
                "throughPasses" to player.advanced.throughPasses,
            )
        } else {
            mapOf(
                "beats" to null,
                "preAssists" to null,
                "throughPasses" to null,
                "completedDribbles" to null,
            )
        }
        return AdvancedStatsDiscoveryEngine.AggregateSample(
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
            ) + advancedControls,
            matchCompletion = canonical.footballMatch.completion.status.name,
        )
    }

    private fun boundedDiscoverySamples(clubId: ClubId, limit: Int): List<AdvancedStatsDiscoveryEngine.AggregateSample> {
        val matches = matchRepository.findRecent(clubId, (limit * DISCOVERY_SCAN_MULTIPLIER).coerceAtMost(MAX_DISCOVERY_CANONICAL_MATCHES))
            .filter(::hasRawAggregateCoverage)
            .take(limit)
        return matches.flatMap { canonical -> discoverySamples(clubId, canonical) }
    }

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
        aggregateSlots(raw).forEach { (aggregateIndex, aggregate) ->
            parseHistogram(aggregate)?.forEach { (code, value) ->
                val mapping = AdvancedStatsCodeRegistry.lookup(aggregateIndex, code)
                entries.add(AggregateEntry(aggregateIndex, code, value, mapping.confidence.name, mapping.metricName, mapping.evidence))
            }
        }
        entries.sortWith(compareBy({ it.aggregate }, { it.code }))
        return entries
    }

    private fun parseHistogram(raw: String?): Map<Int, Int>? {
        if (raw == null) return null
        if (raw.isBlank()) return emptyMap()
        return raw.split(',').mapNotNull { entry ->
            val parts = entry.split(':', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
            val code = parts[0].toIntOrNull() ?: return@mapNotNull null
            val value = parts[1].toIntOrNull()?.takeIf { it >= 0 } ?: return@mapNotNull null
            code to value
        }.toMap()
    }

    private fun aggregateSlots(raw: RawEventAggregates): List<Pair<Int, String?>> = listOf(
        0 to raw.aggregate0,
        1 to raw.aggregate1,
        2 to raw.aggregate2,
        3 to raw.aggregate3,
    )

    private fun aggregateSlot(raw: RawEventAggregates, aggregateIndex: Int): String? = when (aggregateIndex) {
        0 -> raw.aggregate0
        1 -> raw.aggregate1
        2 -> raw.aggregate2
        3 -> raw.aggregate3
        else -> error("aggregateIndex must be 0-3")
    }

    private fun CanonicalMatch.toAuditCanonicalMatch(): AuditCanonicalMatch {
        val perspectiveClubId = interpretation.perspectiveClubId
        val perspective = footballMatch.participants.firstOrNull { it.club.id == perspectiveClubId }
        val opponent = footballMatch.participants.firstOrNull { it.club.id != perspectiveClubId }
        return AuditCanonicalMatch(
            clubId = perspectiveClubId.value,
            matchId = matchId.value,
            playedAt = footballMatch.playedAt.toString(),
            ourClubName = perspective?.club?.name?.value,
            opponentName = opponent?.club?.name?.value,
            ourScore = interpretation.result.ourScore.goals,
            opponentScore = interpretation.result.opponentScore.goals,
            outcome = interpretation.result.outcome.name,
        )
    }

    private fun ExplorerObservation.toAuditObservation() = AuditObservation(
        phrase = phrase,
        observedCount = observedCount,
        completeness = completeness.name,
        note = note,
        observedPositionContext = observedPositionContext,
        createdAt = createdAt?.toString(),
        updatedAt = updatedAt?.toString(),
    )

    private fun ExplorerObservation.toAuditVectorEntry() = AuditObservationVectorEntry(
        phrase = phrase,
        observedCount = observedCount,
        completeness = completeness.name,
    )

    private data class ResearchObservationSource(
        val observation: ExplorerObservation,
        val canonical: CanonicalMatch?,
        val player: PlayerMatchPerformance?,
        /** A null value means that this RAW aggregate slot was unavailable. */
        val aggregateHistograms: Map<Int, Map<Int, Int>?>,
    ) {
        fun provenance(aggregateIndex: Int, code: Int): RawAggregateProvenance {
            val histogram = aggregateHistograms[aggregateIndex] ?: return RawAggregateProvenance.AGGREGATE_UNAVAILABLE
            return if (histogram.containsKey(code)) RawAggregateProvenance.EXPLICIT_VALUE else RawAggregateProvenance.CODE_ABSENT_ASSUMED_ZERO
        }
    }

    private fun researchSource(
        observation: ExplorerObservation,
        canonical: CanonicalMatch?,
        playerId: String,
    ): ResearchObservationSource {
        val player = canonical?.let { perspectivePlayer(it, playerId) }
        val raw = player?.rawEventAggregates
        val histograms = (0..3).associateWith { aggregateIndex ->
            raw?.let { parseHistogram(aggregateSlot(it, aggregateIndex)) }
        }
        return ResearchObservationSource(observation, canonical, player, histograms)
    }

    private fun researchProvenance(
        sources: List<ResearchObservationSource>,
        aggregateIndex: Int,
        code: Int,
    ): ObservationResearchTriage.RawProvenanceSummary {
        var explicit = 0
        var assumed = 0
        var unavailable = 0
        sources.forEach { source ->
            when (source.provenance(aggregateIndex, code)) {
                RawAggregateProvenance.EXPLICIT_VALUE -> explicit++
                RawAggregateProvenance.CODE_ABSENT_ASSUMED_ZERO -> assumed++
                RawAggregateProvenance.AGGREGATE_UNAVAILABLE -> unavailable++
            }
        }
        return ObservationResearchTriage.RawProvenanceSummary(explicit, assumed, unavailable)
    }

    private fun explicitResearchEvidence(
        sources: List<ResearchObservationSource>,
        candidate: ObservationCandidateAnalyzer.CandidateAnalysis,
    ): ObservationResearchTriage.ExplicitEvidenceSummary {
        val sourceByMatchId = sources.associateBy { it.observation.matchId.value }
        val explicitEvidence = candidate.evidence.filter { evidence ->
            sourceByMatchId[evidence.matchId]
                ?.provenance(candidate.aggregateIndex, candidate.code) == RawAggregateProvenance.EXPLICIT_VALUE
        }
        return ObservationResearchTriage.ExplicitEvidenceSummary(
            comparableObservations = explicitEvidence.size,
            exactCoincidences = explicitEvidence.count {
                it.comparison == ObservationCandidateAnalyzer.EvidenceComparison.EXACT_COINCIDENCE.name
            },
            compatibleObservations = explicitEvidence.count {
                it.comparison == ObservationCandidateAnalyzer.EvidenceComparison.AT_LEAST_COMPATIBLE.name
            },
            explicitContradictions = explicitEvidence.count {
                isTrustworthyDirectContradiction(it)
            },
        )
    }

    /**
     * Uses only sources already loaded for the bounded queue. Other literal
     * phrases are never merged; they only provide a conservative background
     * signal for whether a RAW code appears broadly for this player.
     */
    private fun researchBackground(
        sourcesByIdentity: Map<ObservationResearchIdentity, List<ResearchObservationSource>>,
        currentIdentity: ObservationResearchIdentity,
        aggregateIndex: Int,
        code: Int,
    ): ObservationResearchTriage.BackgroundSummary {
        val otherSources = sourcesByIdentity.asSequence()
            .filter { (identity, _) -> identity.playerId == currentIdentity.playerId && identity.phrase != currentIdentity.phrase }
            .flatMap { (identity, sources) -> sources.asSequence().map { identity.phrase to it } }
            .filter { (_, source) -> source.provenance(aggregateIndex, code) == RawAggregateProvenance.EXPLICIT_VALUE }
            .toList()
        return ObservationResearchTriage.BackgroundSummary(
            explicitOtherPhraseObservations = otherSources.size,
            positiveOtherPhraseOccurrences = otherSources.count { (_, source) ->
                source.aggregateHistograms[aggregateIndex]?.get(code)?.let { it > 0 } == true
            },
            distinctOtherPhrases = otherSources.map { (phrase, _) -> phrase }.distinct().size,
        )
    }

    private fun hasMeaningfulVariation(
        sources: List<ResearchObservationSource>,
        candidate: ObservationCandidateAnalyzer.CandidateAnalysis,
    ): Boolean {
        val explicitEvidence = candidate.evidence.filter { evidence ->
            sources.firstOrNull { it.observation.matchId.value == evidence.matchId }
                ?.provenance(candidate.aggregateIndex, candidate.code) == RawAggregateProvenance.EXPLICIT_VALUE
        }
        return explicitEvidence.map { it.aggregateValue }.distinct().size >= 2 ||
            explicitEvidence.map { it.observedCount }.distinct().size >= 2
    }

    private fun isTrustworthyDirectContradiction(evidence: ObservationCandidateAnalyzer.Evidence): Boolean {
        if (evidence.comparison != ObservationCandidateAnalyzer.EvidenceComparison.CONTRADICTED.name) return false
        return when (ObservationCompleteness.valueOf(evidence.completeness)) {
            ObservationCompleteness.AT_LEAST -> evidence.aggregateValue < evidence.observedCount
            ObservationCompleteness.EXACT -> evidence.aggregateValue != evidence.observedCount
        }
    }

    private fun queueSectionOrder(section: String): Int = when (ObservationResearchTriage.QueueSection.valueOf(section)) {
        ObservationResearchTriage.QueueSection.READY_FOR_CONTROLLED_TEST -> 0
        ObservationResearchTriage.QueueSection.PROMISING_CONTINUE_COLLECTING -> 1
        ObservationResearchTriage.QueueSection.ASSOCIATED_NOT_DIRECT -> 2
        ObservationResearchTriage.QueueSection.NEEDS_AUDIT -> 3
    }

    private fun priorityOrder(priority: String): Int = when (ObservationResearchTriage.ResearchPriority.valueOf(priority)) {
        ObservationResearchTriage.ResearchPriority.P1 -> 0
        ObservationResearchTriage.ResearchPriority.P2 -> 1
        ObservationResearchTriage.ResearchPriority.P3 -> 2
        ObservationResearchTriage.ResearchPriority.P4 -> 3
    }

    private fun perspectivePlayer(canonical: CanonicalMatch, playerId: String): PlayerMatchPerformance? =
        canonical.footballMatch.participants
            .firstOrNull { it.club.id == canonical.interpretation.perspectiveClubId }
            ?.players
            ?.firstOrNull { it.player.id.value == playerId }

    private fun opponentName(canonical: CanonicalMatch): String? = canonical.footballMatch.participants
        .firstOrNull { it.club.id != canonical.interpretation.perspectiveClubId }
        ?.club
        ?.name
        ?.value

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
            ?.let { it > 3 }
            ?: false

    private fun rawContextFields(player: PlayerMatchPerformance): List<RawContextEntry> =
        player.rawUnknownFields?.fields
            ?.filter { it.name in RAW_CONTEXT_FIELD_NAMES }
            ?.map { RawContextEntry(it.name, it.jsonType, it.value, it.truncated) }
            ?.sortedBy { it.name }
            .orEmpty()

    private companion object {
        const val AUDIT_VECTOR_LIMIT = 100
        const val AUDIT_RAW_ENTRY_LIMIT = 100
        const val DISCOVERY_SCAN_MULTIPLIER = 2
        const val MAX_DISCOVERY_CANONICAL_MATCHES = 20
        const val RESEARCH_QUEUE_IDENTITY_LIMIT = 40
        const val RESEARCH_QUEUE_CANONICAL_MATCH_LIMIT = 50
        const val RESEARCH_QUEUE_OBSERVATIONS_PER_IDENTITY_LIMIT = 20
        const val RESEARCH_QUEUE_MAX_ITEMS = 24
        const val RESEARCH_IDENTITY_DIAGNOSTIC_CANDIDATE_LIMIT = 3
        const val HIDDEN_RESEARCH_QUEUE_SECTION = "HIDDEN"
        val RAW_CONTEXT_FIELD_NAMES = setOf("gameTime", "realtimegame", "realtimeidle", "archetypeid")
    }
}
