package com.eafc26.discordstats.explorer

/**
 * Deterministic research-only triage for already persisted human observations.
 *
 * This deliberately consumes [ObservationCandidateAnalyzer] output instead of
 * assigning semantics to an EA aggregate. A queue item is a work-order for a
 * human investigator, never a mapping, product metric, or confidence score.
 */
class ObservationResearchTriage {
    enum class ResearchState {
        COLLECT_MORE,
        PROMISING_DIRECT_COUNTER,
        READY_FOR_CONTROLLED_TEST,
        ASSOCIATED_BUT_NOT_DIRECT,
        DIRECT_COUNTER_REFUTED,
        VALIDATION_BLOCKED,
    }

    enum class DirectCounterValidity {
        NOT_REFUTED,
        REFUTED,
        INTEGRITY_LIMITED,
    }

    enum class AssociationInterest {
        NONE,
        LOW,
        MEDIUM,
        HIGH,
    }

    enum class ResearchPriority {
        P1,
        P2,
        P3,
        P4,
    }

    enum class NextActionType {
        CONTINUE_PASSIVE_COLLECTION,
        CONTROLLED_HIGH_COUNT_TARGET,
        DISCRIMINATE_COLLISION,
        AUDIT_RAW_PROVENANCE,
        RETAIN_ASSOCIATION_ONLY,
        NO_DIRECT_COUNTER_FOLLOW_UP,
    }

    data class RawProvenanceSummary(
        val explicitValueEvidence: Int,
        val codeAbsentAssumedZeroEvidence: Int,
        val aggregateUnavailableEvidence: Int,
    ) {
        val hasIntegrityLimitation: Boolean
            get() = codeAbsentAssumedZeroEvidence > 0 || aggregateUnavailableEvidence > 0
    }

    data class CandidateInput(
        val phrase: String,
        val candidate: ObservationCandidateAnalyzer.CandidateAnalysis,
        val provenance: RawProvenanceSummary,
        val trustworthyContradictions: Int,
        val evidenceTruncated: Boolean,
    )

    data class Result(
        val state: ResearchState,
        val directCounterValidity: DirectCounterValidity,
        val associationInterest: AssociationInterest,
        val priority: ResearchPriority,
        /**
         * Transparent deterministic ordering key. It ranks research attention
         * only; it is not a probability or semantic confidence.
         */
        val priorityScore: Int,
        val nextAction: NextActionType,
        val nextActionText: String,
    )

    fun triage(input: CandidateInput): Result {
        require(input.candidate.candidateKind == ObservationCandidateAnalyzer.CandidateKind.UNKNOWN_CANDIDATE.name) {
            "Research triage accepts UNKNOWN_CANDIDATE only"
        }

        val candidate = input.candidate
        val comparable = candidate.comparableObservations
        val supportive = candidate.exactSupportingEvidence + candidate.atLeastCompatibleCases
        val directRefuted = input.trustworthyContradictions > 0
        val integrityLimited = input.evidenceTruncated || input.provenance.hasIntegrityLimitation
        val associationInterest = associationInterest(candidate, supportive, directRefuted)
        val directValidity = when {
            directRefuted -> DirectCounterValidity.REFUTED
            integrityLimited -> DirectCounterValidity.INTEGRITY_LIMITED
            else -> DirectCounterValidity.NOT_REFUTED
        }
        val noObservedContradictions = candidate.contradictions == 0
        val noCandidateCollisions = candidate.candidateCollisions.isEmpty()
        val lowExcessForReady = candidate.totalExcess <= maxOf(2, comparable / 3)
        val matureDirectPattern =
            comparable >= READY_MIN_COMPARABLE &&
                candidate.exactSupportingEvidence >= READY_MIN_EXACT &&
                supportive == comparable &&
                noObservedContradictions &&
                lowExcessForReady &&
                noCandidateCollisions
        val promisingDirectPattern =
            comparable >= PROMISING_MIN_COMPARABLE &&
                candidate.exactSupportingEvidence >= PROMISING_MIN_EXACT &&
                supportive == comparable &&
                noObservedContradictions &&
                candidate.totalExcess <= comparable

        val state = when {
            directRefuted && associationInterest >= AssociationInterest.MEDIUM -> ResearchState.ASSOCIATED_BUT_NOT_DIRECT
            directRefuted -> ResearchState.DIRECT_COUNTER_REFUTED
            integrityLimited && (matureDirectPattern || promisingDirectPattern) -> ResearchState.VALIDATION_BLOCKED
            matureDirectPattern -> ResearchState.READY_FOR_CONTROLLED_TEST
            promisingDirectPattern -> ResearchState.PROMISING_DIRECT_COUNTER
            else -> ResearchState.COLLECT_MORE
        }
        val priority = priorityFor(state, associationInterest)
        val score = priorityScore(state, candidate, input.trustworthyContradictions, associationInterest)
        val action = nextAction(state, input.phrase, candidate)
        return Result(
            state = state,
            directCounterValidity = directValidity,
            associationInterest = associationInterest,
            priority = priority,
            priorityScore = score,
            nextAction = action.first,
            nextActionText = action.second,
        )
    }

    private fun associationInterest(
        candidate: ObservationCandidateAnalyzer.CandidateAnalysis,
        supportive: Int,
        directRefuted: Boolean,
    ): AssociationInterest {
        if (!directRefuted) return AssociationInterest.NONE
        val comparable = candidate.comparableObservations
        if (comparable == 0 || supportive == 0) return AssociationInterest.NONE
        return when {
            comparable >= 8 && candidate.exactSupportingEvidence >= 4 && supportive * 100 >= comparable * 60 -> AssociationInterest.HIGH
            comparable >= 5 && candidate.exactSupportingEvidence >= 3 && supportive * 100 >= comparable * 50 -> AssociationInterest.MEDIUM
            else -> AssociationInterest.LOW
        }
    }

    private fun priorityFor(state: ResearchState, associationInterest: AssociationInterest): ResearchPriority = when (state) {
        ResearchState.READY_FOR_CONTROLLED_TEST -> ResearchPriority.P1
        ResearchState.PROMISING_DIRECT_COUNTER, ResearchState.VALIDATION_BLOCKED -> ResearchPriority.P2
        ResearchState.COLLECT_MORE -> ResearchPriority.P3
        ResearchState.ASSOCIATED_BUT_NOT_DIRECT -> if (associationInterest == AssociationInterest.HIGH) ResearchPriority.P3 else ResearchPriority.P4
        ResearchState.DIRECT_COUNTER_REFUTED -> ResearchPriority.P4
    }

    private fun priorityScore(
        state: ResearchState,
        candidate: ObservationCandidateAnalyzer.CandidateAnalysis,
        trustworthyContradictions: Int,
        associationInterest: AssociationInterest,
    ): Int {
        val base = when (state) {
            ResearchState.READY_FOR_CONTROLLED_TEST -> 600
            ResearchState.PROMISING_DIRECT_COUNTER -> 500
            ResearchState.VALIDATION_BLOCKED -> 450
            ResearchState.ASSOCIATED_BUT_NOT_DIRECT -> 350
            ResearchState.COLLECT_MORE -> 250
            ResearchState.DIRECT_COUNTER_REFUTED -> 100
        }
        val associationBonus = when (associationInterest) {
            AssociationInterest.HIGH -> 40
            AssociationInterest.MEDIUM -> 20
            AssociationInterest.LOW -> 5
            AssociationInterest.NONE -> 0
        }
        return base +
            candidate.comparableObservations * 4 +
            candidate.exactSupportingEvidence * 4 +
            candidate.atLeastCompatibleCases * 2 +
            associationBonus -
            trustworthyContradictions * 12 -
            candidate.totalExcess.coerceAtMost(50) -
            candidate.candidateCollisions.size * 10
    }

    private fun nextAction(
        state: ResearchState,
        phrase: String,
        candidate: ObservationCandidateAnalyzer.CandidateAnalysis,
    ): Pair<NextActionType, String> = when (state) {
        ResearchState.READY_FOR_CONTROLLED_TEST ->
            NextActionType.CONTROLLED_HIGH_COUNT_TARGET to
                "Experimento direcionado: registre uma partida com várias ocorrências observadas de “$phrase”."
        ResearchState.PROMISING_DIRECT_COUNTER ->
            if (candidate.candidateCollisions.isNotEmpty()) {
                val collision = candidate.candidateCollisions.first()
                NextActionType.DISCRIMINATE_COLLISION to
                    "Continue a coleta e procure um cenário que diferencie agg${candidate.aggregateIndex}[${candidate.code}] de agg${collision.aggregateIndex}[${collision.code}]."
            } else {
                NextActionType.CONTINUE_PASSIVE_COLLECTION to
                    "Continue a coleta normal desta frase; ainda não há base para um experimento controlado."
            }
        ResearchState.VALIDATION_BLOCKED ->
            NextActionType.AUDIT_RAW_PROVENANCE to
                "Audite a proveniência RAW e a cobertura antes de qualquer experimento controlado."
        ResearchState.ASSOCIATED_BUT_NOT_DIRECT ->
            NextActionType.RETAIN_ASSOCIATION_ONLY to
                "Não teste este código como contador direto. Retenha-o apenas para pesquisa observacional futura."
        ResearchState.DIRECT_COUNTER_REFUTED ->
            NextActionType.NO_DIRECT_COUNTER_FOLLOW_UP to
                "Não priorize novo teste de contador direto para este candidato."
        ResearchState.COLLECT_MORE ->
            NextActionType.CONTINUE_PASSIVE_COLLECTION to
                "Continue a coleta normal desta frase para aumentar a evidência independente."
    }

    companion object {
        const val PROMISING_MIN_COMPARABLE = 4
        const val PROMISING_MIN_EXACT = 3
        const val READY_MIN_COMPARABLE = 8
        const val READY_MIN_EXACT = 6

    }
}
