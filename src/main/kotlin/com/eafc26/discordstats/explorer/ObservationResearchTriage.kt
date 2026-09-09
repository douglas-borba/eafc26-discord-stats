package com.eafc26.discordstats.explorer

/**
 * Deterministic research-only triage for already persisted human observations.
 *
 * It deliberately separates a literal direct-counter hypothesis from an
 * observational association with an exact EA feedback phrase. Neither result
 * assigns sporting semantics to an aggregate code.
 */
class ObservationResearchTriage {
    /** Compatibility-oriented aggregate state for existing Explorer consumers. */
    enum class ResearchState {
        COLLECT_MORE,
        PROMISING_DIRECT_COUNTER,
        READY_FOR_CONTROLLED_TEST,
        ASSOCIATED_BUT_NOT_DIRECT,
        DIRECT_COUNTER_REFUTED,
        VALIDATION_BLOCKED,
    }

    /** Strictly about the hypothesis `aggregate value == observed phrase count`. */
    enum class DirectCounterStatus {
        INSUFFICIENT,
        PROMISING,
        REFUTED,
        /** Reserved for future persisted controlled evidence; never inferred from passive rows. */
        CONTROLLED_CONFIRMED,
    }

    /** Repeated RAW/phrase behavior, never a claim about a football statistic. */
    enum class FeedbackAssociationStatus {
        INSUFFICIENT,
        EMERGING,
        STRONG,
    }

    /** No validation is emitted until controlled evidence has its own persisted provenance. */
    enum class ValidationStatus {
        NOT_VALIDATED,
        PROVISIONAL_VALIDATED,
        VALIDATED_DIRECT_COUNTER,
    }

    /** Legacy presentation field retained while clients migrate to [DirectCounterStatus]. */
    enum class DirectCounterValidity { NOT_REFUTED, REFUTED, INTEGRITY_LIMITED }

    /** Legacy presentation field retained while clients migrate to [FeedbackAssociationStatus]. */
    enum class AssociationInterest { NONE, LOW, MEDIUM, HIGH }

    enum class BackgroundDiscrimination {
        NOT_ENOUGH_BACKGROUND,
        DISCRIMINATIVE,
        BROADLY_PRESENT,
    }

    enum class ResearchPriority { P1, P2, P3, P4 }

    enum class NextActionType {
        CONTINUE_PASSIVE_COLLECTION,
        CONTROLLED_HIGH_COUNT_TARGET,
        CONTROLLED_DISCRIMINATION_TARGET,
        DISCRIMINATE_COLLISION,
        AUDIT_RAW_PROVENANCE,
        RETAIN_ASSOCIATION_ONLY,
        NO_DIRECT_COUNTER_FOLLOW_UP,
    }

    /** A deliberate work queue section, not a restatement of every internal state. */
    enum class QueueSection {
        READY_FOR_CONTROLLED_TEST,
        PROMISING_CONTINUE_COLLECTING,
        ASSOCIATED_NOT_DIRECT,
        NEEDS_AUDIT,
    }

    data class RawProvenanceSummary(
        val explicitValueEvidence: Int,
        val codeAbsentAssumedZeroEvidence: Int,
        val aggregateUnavailableEvidence: Int,
    ) {
        val hasIntegrityLimitation: Boolean
            get() = codeAbsentAssumedZeroEvidence > 0 || aggregateUnavailableEvidence > 0
    }

    /** Counts only rows where this exact RAW code was explicitly present. */
    data class ExplicitEvidenceSummary(
        val comparableObservations: Int,
        val exactCoincidences: Int,
        val compatibleObservations: Int,
        val explicitContradictions: Int = 0,
    ) {
        val supportiveObservations: Int
            get() = exactCoincidences + compatibleObservations

        val supportRatePercent: Int
            get() = if (comparableObservations == 0) 0 else supportiveObservations * 100 / comparableObservations
    }

    /**
     * Bounded comparison against other literal phrases for the same player.
     * It is a discrimination signal, not a statistical correlation test.
     */
    data class BackgroundSummary(
        val explicitOtherPhraseObservations: Int,
        val positiveOtherPhraseOccurrences: Int,
        val distinctOtherPhrases: Int,
    ) {
        val classification: BackgroundDiscrimination
            get() = when {
                explicitOtherPhraseObservations < MIN_BACKGROUND_OBSERVATIONS || distinctOtherPhrases < 2 ->
                    BackgroundDiscrimination.NOT_ENOUGH_BACKGROUND
                positiveOtherPhraseOccurrences * 100 >= explicitOtherPhraseObservations * BROAD_BACKGROUND_PERCENT ->
                    BackgroundDiscrimination.BROADLY_PRESENT
                else -> BackgroundDiscrimination.DISCRIMINATIVE
            }
    }

    data class CandidateInput(
        val phrase: String,
        val candidate: ObservationCandidateAnalyzer.CandidateAnalysis,
        val provenance: RawProvenanceSummary,
        val explicitEvidence: ExplicitEvidenceSummary,
        val background: BackgroundSummary = BackgroundSummary(0, 0, 0),
        val trustworthyContradictions: Int,
        val evidenceTruncated: Boolean,
        val hasMeaningfulVariation: Boolean = true,
    )

    data class Result(
        val state: ResearchState,
        val directCounterStatus: DirectCounterStatus,
        val feedbackAssociationStatus: FeedbackAssociationStatus,
        val validationStatus: ValidationStatus,
        val directCounterValidity: DirectCounterValidity,
        val associationInterest: AssociationInterest,
        val priority: ResearchPriority,
        /** Deterministic attention ordering only; never a confidence/probability. */
        val priorityScore: Int,
        val nextAction: NextActionType,
        val nextActionText: String,
        val queueSection: QueueSection?,
    )

    fun triage(input: CandidateInput): Result {
        require(input.candidate.candidateKind == ObservationCandidateAnalyzer.CandidateKind.UNKNOWN_CANDIDATE.name) {
            "Research triage accepts UNKNOWN_CANDIDATE only"
        }

        val directStatus = directCounterStatus(input)
        val association = feedbackAssociationStatus(input)
        val integrityLimited = input.evidenceTruncated || input.provenance.hasIntegrityLimitation
        val readyForControlledTest =
            (association == FeedbackAssociationStatus.STRONG || directStatus == DirectCounterStatus.PROMISING) &&
                input.explicitEvidence.comparableObservations >= READY_MIN_EXPLICIT_COMPARABLE &&
                input.hasMeaningfulVariation &&
                !integrityLimited &&
                input.background.classification != BackgroundDiscrimination.BROADLY_PRESENT

        val state = when {
            readyForControlledTest -> ResearchState.READY_FOR_CONTROLLED_TEST
            integrityLimited && hasUsefulSignal(directStatus, association) -> ResearchState.VALIDATION_BLOCKED
            directStatus == DirectCounterStatus.REFUTED && association == FeedbackAssociationStatus.STRONG ->
                ResearchState.ASSOCIATED_BUT_NOT_DIRECT
            directStatus == DirectCounterStatus.PROMISING -> ResearchState.PROMISING_DIRECT_COUNTER
            directStatus == DirectCounterStatus.REFUTED -> ResearchState.DIRECT_COUNTER_REFUTED
            else -> ResearchState.COLLECT_MORE
        }
        val queueSection = queueSection(state, directStatus, association, input)
        val priority = priorityFor(state, association)
        val score = priorityScore(state, input, association)
        val action = nextAction(state, directStatus, association, input)
        return Result(
            state = state,
            directCounterStatus = directStatus,
            feedbackAssociationStatus = association,
            validationStatus = ValidationStatus.NOT_VALIDATED,
            directCounterValidity = legacyValidity(directStatus, integrityLimited),
            associationInterest = legacyInterest(association),
            priority = priority,
            priorityScore = score,
            nextAction = action.first,
            nextActionText = action.second,
            queueSection = queueSection,
        )
    }

    private fun directCounterStatus(input: CandidateInput): DirectCounterStatus {
        if (input.trustworthyContradictions > 0) return DirectCounterStatus.REFUTED
        val explicit = input.explicitEvidence
        val strictSupport = explicit.supportiveObservations == explicit.comparableObservations
        return if (
            explicit.comparableObservations >= PROMISING_MIN_EXPLICIT_COMPARABLE &&
            explicit.exactCoincidences >= PROMISING_MIN_EXACT &&
            strictSupport &&
            explicit.explicitContradictions == 0
        ) DirectCounterStatus.PROMISING else DirectCounterStatus.INSUFFICIENT
    }

    private fun feedbackAssociationStatus(input: CandidateInput): FeedbackAssociationStatus {
        val evidence = input.explicitEvidence
        if (
            evidence.comparableObservations < EMERGING_MIN_EXPLICIT_COMPARABLE ||
            evidence.supportiveObservations < EMERGING_MIN_SUPPORTIVE ||
            evidence.supportRatePercent < EMERGING_MIN_SUPPORT_PERCENT ||
            evidence.explicitContradictions > maxOf(1, evidence.comparableObservations / 3)
        ) return FeedbackAssociationStatus.INSUFFICIENT

        val contradictionsAllowedForStrong = maxOf(2, evidence.comparableObservations / 3)
        val strong =
            evidence.comparableObservations >= STRONG_MIN_EXPLICIT_COMPARABLE &&
                evidence.exactCoincidences >= STRONG_MIN_EXACT &&
                evidence.supportRatePercent >= STRONG_MIN_SUPPORT_PERCENT &&
                evidence.explicitContradictions <= contradictionsAllowedForStrong &&
                input.background.classification != BackgroundDiscrimination.BROADLY_PRESENT
        return if (strong) FeedbackAssociationStatus.STRONG else FeedbackAssociationStatus.EMERGING
    }

    private fun hasUsefulSignal(
        directStatus: DirectCounterStatus,
        association: FeedbackAssociationStatus,
    ): Boolean = directStatus == DirectCounterStatus.PROMISING || association != FeedbackAssociationStatus.INSUFFICIENT

    private fun queueSection(
        state: ResearchState,
        directStatus: DirectCounterStatus,
        association: FeedbackAssociationStatus,
        input: CandidateInput,
    ): QueueSection? = when {
        state == ResearchState.READY_FOR_CONTROLLED_TEST -> QueueSection.READY_FOR_CONTROLLED_TEST
        state == ResearchState.VALIDATION_BLOCKED -> QueueSection.NEEDS_AUDIT
        association == FeedbackAssociationStatus.STRONG && directStatus == DirectCounterStatus.REFUTED ->
            QueueSection.ASSOCIATED_NOT_DIRECT
        association == FeedbackAssociationStatus.STRONG ||
            association == FeedbackAssociationStatus.EMERGING ||
            directStatus == DirectCounterStatus.PROMISING -> QueueSection.PROMISING_CONTINUE_COLLECTING
        state == ResearchState.DIRECT_COUNTER_REFUTED && hasAuditWorthyRefutation(input) -> QueueSection.NEEDS_AUDIT
        else -> null
    }

    private fun hasAuditWorthyRefutation(input: CandidateInput): Boolean =
        input.explicitEvidence.comparableObservations >= EMERGING_MIN_EXPLICIT_COMPARABLE &&
            input.trustworthyContradictions > 0 &&
            input.explicitEvidence.supportiveObservations >= 2

    private fun legacyValidity(
        directStatus: DirectCounterStatus,
        integrityLimited: Boolean,
    ): DirectCounterValidity = when {
        directStatus == DirectCounterStatus.REFUTED -> DirectCounterValidity.REFUTED
        integrityLimited -> DirectCounterValidity.INTEGRITY_LIMITED
        else -> DirectCounterValidity.NOT_REFUTED
    }

    private fun legacyInterest(status: FeedbackAssociationStatus): AssociationInterest = when (status) {
        FeedbackAssociationStatus.INSUFFICIENT -> AssociationInterest.NONE
        FeedbackAssociationStatus.EMERGING -> AssociationInterest.MEDIUM
        FeedbackAssociationStatus.STRONG -> AssociationInterest.HIGH
    }

    private fun priorityFor(
        state: ResearchState,
        association: FeedbackAssociationStatus,
    ): ResearchPriority = when {
        state == ResearchState.READY_FOR_CONTROLLED_TEST -> ResearchPriority.P1
        state == ResearchState.VALIDATION_BLOCKED -> ResearchPriority.P2
        association == FeedbackAssociationStatus.STRONG -> ResearchPriority.P2
        state == ResearchState.PROMISING_DIRECT_COUNTER -> ResearchPriority.P2
        association == FeedbackAssociationStatus.EMERGING -> ResearchPriority.P3
        else -> ResearchPriority.P4
    }

    private fun priorityScore(
        state: ResearchState,
        input: CandidateInput,
        association: FeedbackAssociationStatus,
    ): Int {
        val evidence = input.explicitEvidence
        val base = when (state) {
            ResearchState.READY_FOR_CONTROLLED_TEST -> 600
            ResearchState.VALIDATION_BLOCKED -> 500
            ResearchState.ASSOCIATED_BUT_NOT_DIRECT -> 460
            ResearchState.PROMISING_DIRECT_COUNTER -> 440
            ResearchState.COLLECT_MORE -> 300
            ResearchState.DIRECT_COUNTER_REFUTED -> 180
        }
        val associationBonus = when (association) {
            FeedbackAssociationStatus.STRONG -> 80
            FeedbackAssociationStatus.EMERGING -> 35
            FeedbackAssociationStatus.INSUFFICIENT -> 0
        }
        val backgroundPenalty = if (input.background.classification == BackgroundDiscrimination.BROADLY_PRESENT) 40 else 0
        return base +
            evidence.comparableObservations * 4 +
            evidence.exactCoincidences * 5 +
            evidence.compatibleObservations * 2 +
            associationBonus -
            evidence.explicitContradictions * 10 -
            input.candidate.totalExcess.coerceAtMost(50) -
            input.candidate.candidateCollisions.size * 10 -
            backgroundPenalty
    }

    private fun nextAction(
        state: ResearchState,
        directStatus: DirectCounterStatus,
        association: FeedbackAssociationStatus,
        input: CandidateInput,
    ): Pair<NextActionType, String> = when {
        state == ResearchState.READY_FOR_CONTROLLED_TEST && directStatus == DirectCounterStatus.REFUTED ->
            NextActionType.CONTROLLED_DISCRIMINATION_TARGET to
                "Experimento de discriminação: teste a associação com “${input.phrase}”; não teste igualdade 1:1."
        state == ResearchState.READY_FOR_CONTROLLED_TEST ->
            NextActionType.CONTROLLED_HIGH_COUNT_TARGET to
                "Experimento direcionado: registre uma partida com várias ocorrências observadas de “${input.phrase}”."
        state == ResearchState.VALIDATION_BLOCKED ->
            NextActionType.AUDIT_RAW_PROVENANCE to
                "Há padrão útil, mas a proveniência RAW ou a janela limitada precisa ser auditada antes do experimento."
        association == FeedbackAssociationStatus.STRONG && directStatus == DirectCounterStatus.REFUTED ->
            NextActionType.RETAIN_ASSOCIATION_ONLY to
                "A igualdade 1:1 foi refutada; retenha a associação e planeje um teste de discriminação."
        input.candidate.candidateCollisions.isNotEmpty() &&
            (association != FeedbackAssociationStatus.INSUFFICIENT || directStatus == DirectCounterStatus.PROMISING) ->
            NextActionType.DISCRIMINATE_COLLISION to
                "Colete uma situação que diferencie agg${input.candidate.aggregateIndex}[${input.candidate.code}] dos candidatos RAW colidentes."
        input.background.classification == BackgroundDiscrimination.BROADLY_PRESENT ->
            NextActionType.DISCRIMINATE_COLLISION to
                "Colete uma situação que diferencie esta frase de outras frases do mesmo jogador."
        association == FeedbackAssociationStatus.EMERGING || directStatus == DirectCounterStatus.PROMISING ->
            NextActionType.CONTINUE_PASSIVE_COLLECTION to
                "Continue a coleta literal desta frase para aumentar a evidência independente."
        else -> NextActionType.CONTINUE_PASSIVE_COLLECTION to
            "Continue a coleta literal desta frase; ainda não há evidência suficiente para priorizar este candidato."
    }

    companion object {
        const val EMERGING_MIN_EXPLICIT_COMPARABLE = 4
        const val EMERGING_MIN_SUPPORTIVE = 3
        const val EMERGING_MIN_SUPPORT_PERCENT = 60
        const val STRONG_MIN_EXPLICIT_COMPARABLE = 8
        const val STRONG_MIN_EXACT = 4
        const val STRONG_MIN_SUPPORT_PERCENT = 60
        const val PROMISING_MIN_EXPLICIT_COMPARABLE = 4
        const val PROMISING_MIN_EXACT = 3
        const val READY_MIN_EXPLICIT_COMPARABLE = 8
        const val MIN_BACKGROUND_OBSERVATIONS = 4
        const val BROAD_BACKGROUND_PERCENT = 75
    }
}
