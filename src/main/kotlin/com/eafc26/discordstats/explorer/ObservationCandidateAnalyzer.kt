package com.eafc26.discordstats.explorer

/**
 * Mechanical evidence analysis for human-authored observations. It deliberately
 * answers only whether a RAW aggregate could be a direct counter for a phrase;
 * it never assigns a football meaning or mutates [AdvancedStatsCodeRegistry].
 */
class ObservationCandidateAnalyzer(
    private val evidenceEngine: ObservationalEvidenceEngine = ObservationalEvidenceEngine(),
) {
    enum class CandidateKind { UNKNOWN_CANDIDATE, KNOWN_CONTROL }
    enum class InvestigationStatus { CONTRADICTED, INSUFFICIENT_EVIDENCE, SURVIVES, HIGH_PRIORITY }
    enum class EvidenceComparison { CONTRADICTED, EXACT_COINCIDENCE, AT_LEAST_COMPATIBLE }

    data class ObservationInput(
        val matchId: String,
        val opponentName: String?,
        val observedCount: Int,
        val completeness: ObservationCompleteness,
        /** Only available slots are present. A missing code in a present slot is a factual zero. */
        val aggregates: Map<Int, Map<Int, Int>>,
    )

    data class RecordedObservation(
        val matchId: String,
        val phrase: String,
        val observedCount: Int,
        val completeness: ObservationCompleteness,
    )

    data class Evidence(
        val matchId: String,
        val opponentName: String?,
        val observedCount: Int,
        val completeness: String,
        val aggregateValue: Int,
        val comparison: String,
    )

    data class CandidateCollision(
        val aggregateIndex: Int,
        val code: Int,
        val candidateKind: String,
        val registryConfidence: String,
        val metricName: String?,
    )

    data class ObservationCollision(
        val phrase: String,
        val sharedObservedMatches: Int,
    )

    data class CandidateAnalysis(
        val aggregateIndex: Int,
        val code: Int,
        val candidateKind: String,
        val registryConfidence: String,
        val metricName: String?,
        val registryEvidence: String?,
        val annotatedMatches: Int,
        val comparableObservations: Int,
        val totalObservedOccurrences: Int,
        val aggregateLessThanObserved: Int,
        val aggregateEqualObserved: Int,
        val aggregateGreaterThanObserved: Int,
        val exactSupportingEvidence: Int,
        val contradictions: Int,
        val totalExcess: Int,
        val atLeastCompatibleCases: Int,
        /** Legacy-compatible narrow direct-counter classification. */
        val classification: String,
        val investigationStatus: String,
        val investigationRank: Int?,
        val evidence: List<Evidence>,
        val candidateCollisions: List<CandidateCollision>,
    )

    data class Analysis(
        val phrase: String,
        val annotatedMatches: Int,
        val annotatedObservations: Int,
        val contradictedCandidates: Int,
        val candidates: List<CandidateAnalysis>,
        val observationCollisions: List<ObservationCollision>,
        val nextBestExperiments: List<String>,
    )

    fun analyze(
        phrase: String,
        observations: List<ObservationInput>,
        allPlayerObservations: List<RecordedObservation>,
    ): Analysis {
        val codesByAggregate = observations
            .flatMap { input -> input.aggregates.entries }
            .groupBy({ it.key }, { it.value.keys })
            .mapValues { (_, codeSets) -> codeSets.flatten().toSortedSet() }

        val samples = observations.flatMap { input ->
            input.aggregates.flatMap { (aggregateIndex, values) ->
                codesByAggregate[aggregateIndex].orEmpty().map { code ->
                    ObservationalEvidenceEngine.Sample(
                        matchId = input.matchId,
                        aggregateIndex = aggregateIndex,
                        code = code,
                        aggregateValue = values[code] ?: 0,
                        observedCount = input.observedCount,
                        completeness = input.completeness,
                    )
                }
            }
        }
        val directEvidence = evidenceEngine.analyze(samples)
            .associateBy { it.aggregateIndex to it.code }

        val drafts = directEvidence.values.map { direct ->
            val mapping = AdvancedStatsCodeRegistry.lookup(direct.aggregateIndex, direct.code)
            val kind = if (AdvancedStatsCodeRegistry.isKnown(direct.aggregateIndex, direct.code)) {
                CandidateKind.KNOWN_CONTROL
            } else {
                CandidateKind.UNKNOWN_CANDIDATE
            }
            val evidence = observations.mapNotNull { input ->
                val aggregate = input.aggregates[direct.aggregateIndex] ?: return@mapNotNull null
                val value = aggregate[direct.code] ?: 0
                Evidence(
                    matchId = input.matchId,
                    opponentName = input.opponentName,
                    observedCount = input.observedCount,
                    completeness = input.completeness.name,
                    aggregateValue = value,
                    comparison = comparisonFor(input.completeness, value, input.observedCount).name,
                )
            }.sortedBy { it.matchId }
            val totalExcess = evidence.sumOf { row ->
                if (row.comparison == EvidenceComparison.AT_LEAST_COMPATIBLE.name) row.aggregateValue - row.observedCount else 0
            }
            val atLeastCompatible = evidence.count { it.comparison == EvidenceComparison.AT_LEAST_COMPATIBLE.name }
            CandidateDraft(
                direct = direct,
                kind = kind,
                mapping = mapping,
                evidence = evidence,
                totalExcess = totalExcess,
                atLeastCompatible = atLeastCompatible,
                status = statusFor(kind, direct, totalExcess, evidence),
            )
        }

        val collisionsByIdentity = drafts
            .filter { it.status != InvestigationStatus.CONTRADICTED && it.evidence.isNotEmpty() }
            .groupBy { draft ->
                draft.evidence.joinToString("|") { "${it.matchId}:${it.aggregateValue}" }
            }
            .filterValues { it.size > 1 }

        val rankedUnknowns = drafts
            .filter { it.kind == CandidateKind.UNKNOWN_CANDIDATE && it.status != InvestigationStatus.CONTRADICTED }
            .sortedWith(candidateOrdering())
            .mapIndexed { index, draft -> (draft.direct.aggregateIndex to draft.direct.code) to (index + 1) }
            .toMap()

        val candidates = drafts.map { draft ->
            val identity = draft.direct.aggregateIndex to draft.direct.code
            val collisions = collisionsByIdentity.values
                .firstOrNull { group -> group.any { it.direct.aggregateIndex to it.direct.code == identity } }
                .orEmpty()
                .filterNot { it.direct.aggregateIndex to it.direct.code == identity }
                .sortedWith(compareBy<CandidateDraft> { it.kind != CandidateKind.KNOWN_CONTROL }
                    .thenBy { it.direct.aggregateIndex }.thenBy { it.direct.code })
                .map { other ->
                    CandidateCollision(
                        aggregateIndex = other.direct.aggregateIndex,
                        code = other.direct.code,
                        candidateKind = other.kind.name,
                        registryConfidence = other.mapping.confidence.name,
                        metricName = other.mapping.metricName,
                    )
                }
            CandidateAnalysis(
                aggregateIndex = draft.direct.aggregateIndex,
                code = draft.direct.code,
                candidateKind = draft.kind.name,
                registryConfidence = draft.mapping.confidence.name,
                metricName = draft.mapping.metricName,
                registryEvidence = draft.mapping.evidence,
                annotatedMatches = draft.direct.annotatedMatches,
                comparableObservations = draft.direct.comparableObservations,
                totalObservedOccurrences = draft.direct.totalObservedOccurrences,
                aggregateLessThanObserved = draft.direct.aggregateLessThanObserved,
                aggregateEqualObserved = draft.direct.aggregateEqualObserved,
                aggregateGreaterThanObserved = draft.direct.aggregateGreaterThanObserved,
                exactSupportingEvidence = draft.direct.exactSupportingEvidence,
                contradictions = draft.evidence.count { it.comparison == EvidenceComparison.CONTRADICTED.name },
                totalExcess = draft.totalExcess,
                atLeastCompatibleCases = draft.atLeastCompatible,
                classification = draft.direct.classification,
                investigationStatus = draft.status.name,
                investigationRank = rankedUnknowns[identity],
                evidence = draft.evidence,
                candidateCollisions = collisions,
            )
        }.sortedWith(
            compareBy<CandidateAnalysis> { it.candidateKind != CandidateKind.UNKNOWN_CANDIDATE.name }
                .thenBy { statusOrder(InvestigationStatus.valueOf(it.investigationStatus)) }
                .thenBy { it.investigationRank ?: Int.MAX_VALUE }
                .thenBy { it.aggregateIndex }
                .thenBy { it.code },
        )

        val observationCollisions = phraseCollisions(phrase, allPlayerObservations)
        return Analysis(
            phrase = phrase,
            annotatedMatches = observations.map { it.matchId }.toSet().size,
            annotatedObservations = observations.size,
            contradictedCandidates = candidates.count { it.investigationStatus == InvestigationStatus.CONTRADICTED.name },
            candidates = candidates,
            observationCollisions = observationCollisions,
            nextBestExperiments = recommendations(observations, candidates, observationCollisions),
        )
    }

    private data class CandidateDraft(
        val direct: ObservationalEvidenceEngine.Candidate,
        val kind: CandidateKind,
        val mapping: CodeMapping,
        val evidence: List<Evidence>,
        val totalExcess: Int,
        val atLeastCompatible: Int,
        val status: InvestigationStatus,
    )

    private fun statusFor(
        kind: CandidateKind,
        direct: ObservationalEvidenceEngine.Candidate,
        totalExcess: Int,
        evidence: List<Evidence>,
    ): InvestigationStatus = when {
        direct.classification == "DIRECT_COUNTER_INCOMPATIBLE" -> InvestigationStatus.CONTRADICTED
        direct.comparableObservations < 2 -> InvestigationStatus.INSUFFICIENT_EVIDENCE
        kind == CandidateKind.UNKNOWN_CANDIDATE &&
            evidence.count { it.observedCount > 0 || it.completeness == ObservationCompleteness.EXACT.name } >= 3 &&
            evidence.count {
                it.comparison == EvidenceComparison.EXACT_COINCIDENCE.name &&
                    (it.observedCount > 0 || it.completeness == ObservationCompleteness.EXACT.name)
            } >= 2 &&
            totalExcess <= direct.comparableObservations -> InvestigationStatus.HIGH_PRIORITY
        else -> InvestigationStatus.SURVIVES
    }

    private fun candidateOrdering(): Comparator<CandidateDraft> =
        compareBy<CandidateDraft> { statusOrder(it.status) }
            .thenByDescending { it.direct.comparableObservations }
            .thenByDescending { it.direct.aggregateEqualObserved }
            .thenBy { it.totalExcess }
            .thenBy { it.direct.aggregateIndex }
            .thenBy { it.direct.code }

    private fun statusOrder(status: InvestigationStatus): Int = when (status) {
        InvestigationStatus.HIGH_PRIORITY -> 0
        InvestigationStatus.SURVIVES -> 1
        InvestigationStatus.INSUFFICIENT_EVIDENCE -> 2
        InvestigationStatus.CONTRADICTED -> 3
    }

    private fun phraseCollisions(
        phrase: String,
        observations: List<RecordedObservation>,
    ): List<ObservationCollision> {
        val fingerprints = observations.groupBy { it.phrase }.mapValues { (_, rows) ->
            rows.sortedBy { it.matchId }.joinToString("|") { "${it.matchId}:${it.observedCount}:${it.completeness.name}" }
        }
        val selected = fingerprints[phrase] ?: return emptyList()
        return fingerprints
            .filter { (otherPhrase, fingerprint) -> otherPhrase != phrase && fingerprint == selected }
            .map { (otherPhrase, _) ->
                ObservationCollision(otherPhrase, observations.count { it.phrase == phrase })
            }
            .sortedBy { it.phrase }
    }

    private fun recommendations(
        observations: List<ObservationInput>,
        candidates: List<CandidateAnalysis>,
        observationCollisions: List<ObservationCollision>,
    ): List<String> {
        val recommendations = mutableListOf<String>()
        val leadUnknown = candidates.firstOrNull {
            it.candidateKind == CandidateKind.UNKNOWN_CANDIDATE.name &&
                it.investigationStatus != InvestigationStatus.CONTRADICTED.name
        }
        leadUnknown?.candidateCollisions?.firstOrNull()?.let { collision ->
            recommendations += "Registre uma nova partida em que agg${leadUnknown.aggregateIndex}[${leadUnknown.code}] e agg${collision.aggregateIndex}[${collision.code}] tenham valores diferentes."
        }
        if (observations.size < 3) {
            recommendations += "Registre esta frase em mais partidas para aumentar a evidência independente."
        }
        if (observationCollisions.isNotEmpty()) {
            recommendations += "Observe separadamente esta frase e ${observationCollisions.first().phrase} em novas partidas."
        }
        if (observations.isNotEmpty() && observations.all { it.completeness == ObservationCompleteness.AT_LEAST }) {
            recommendations += "Uma captura completa marcada como EXACT aumentaria o poder de eliminação."
        }
        if (leadUnknown != null && leadUnknown.totalExcess > leadUnknown.comparableObservations) {
            recommendations += "Mais observações são necessárias para verificar o excesso de agg${leadUnknown.aggregateIndex}[${leadUnknown.code}]."
        }
        return recommendations.distinct().take(3)
    }

    companion object {
        /** Shared by the read-only audit view so it reports the Analyzer's existing comparison rule verbatim. */
        internal fun comparisonFor(
            completeness: ObservationCompleteness,
            aggregateValue: Int,
            observedCount: Int,
        ): EvidenceComparison = when {
            aggregateValue == observedCount -> EvidenceComparison.EXACT_COINCIDENCE
            completeness == ObservationCompleteness.AT_LEAST && aggregateValue > observedCount -> EvidenceComparison.AT_LEAST_COMPATIBLE
            else -> EvidenceComparison.CONTRADICTED
        }
    }
}
