package com.eafc26.discordstats.explorer

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ObservationResearchTriageTest {
    private val triage = ObservationResearchTriage()

    @Test
    fun `near direct explicit pattern is strong promising and ready for a controlled test`() {
        val result = triage.triage(input(candidate(comparable = 8, exact = 6, compatible = 2), explicit = 8))

        assertThat(result.directCounterStatus).isEqualTo(ObservationResearchTriage.DirectCounterStatus.PROMISING)
        assertThat(result.feedbackAssociationStatus).isEqualTo(ObservationResearchTriage.FeedbackAssociationStatus.STRONG)
        assertThat(result.state).isEqualTo(ObservationResearchTriage.ResearchState.READY_FOR_CONTROLLED_TEST)
        assertThat(result.queueSection).isEqualTo(ObservationResearchTriage.QueueSection.READY_FOR_CONTROLLED_TEST)
        assertThat(result.validationStatus).isEqualTo(ObservationResearchTriage.ValidationStatus.NOT_VALIDATED)
    }

    @Test
    fun `historical 183 shaped evidence refutes direct equality without losing strong association`() {
        val result = triage.triage(
            input(candidate(comparable = 11, exact = 6, compatible = 3, contradictions = 2, excess = 5), explicit = 11, trustworthyContradictions = 2),
        )

        assertThat(result.directCounterStatus).isEqualTo(ObservationResearchTriage.DirectCounterStatus.REFUTED)
        assertThat(result.feedbackAssociationStatus).isEqualTo(ObservationResearchTriage.FeedbackAssociationStatus.STRONG)
        assertThat(result.state).isEqualTo(ObservationResearchTriage.ResearchState.READY_FOR_CONTROLLED_TEST)
        assertThat(result.nextAction).isEqualTo(ObservationResearchTriage.NextActionType.CONTROLLED_DISCRIMINATION_TARGET)
    }

    @Test
    fun `five repeated explicit rows form an emerging visible association`() {
        val result = triage.triage(input(candidate(comparable = 5, exact = 3, compatible = 2), explicit = 5))

        assertThat(result.feedbackAssociationStatus).isEqualTo(ObservationResearchTriage.FeedbackAssociationStatus.EMERGING)
        assertThat(result.queueSection).isEqualTo(ObservationResearchTriage.QueueSection.PROMISING_CONTINUE_COLLECTING)
        assertThat(result.state).isEqualTo(ObservationResearchTriage.ResearchState.PROMISING_DIRECT_COUNTER)
    }

    @Test
    fun `assumed zero dominated noise is hidden and cannot refute or associate strongly`() {
        val result = triage.triage(
            input(candidate(comparable = 15, contradictions = 14), explicit = 1, assumed = 14),
        )

        assertThat(result.directCounterStatus).isEqualTo(ObservationResearchTriage.DirectCounterStatus.INSUFFICIENT)
        assertThat(result.feedbackAssociationStatus).isEqualTo(ObservationResearchTriage.FeedbackAssociationStatus.INSUFFICIENT)
        assertThat(result.queueSection).isNull()
    }

    @Test
    fun `weak coincidence remains hidden`() {
        val result = triage.triage(input(candidate(comparable = 15, exact = 1), explicit = 15))

        assertThat(result.feedbackAssociationStatus).isEqualTo(ObservationResearchTriage.FeedbackAssociationStatus.INSUFFICIENT)
        assertThat(result.queueSection).isNull()
    }

    @Test
    fun `an explicit contradiction refutes direct equality but association remains independently evaluated`() {
        val result = triage.triage(
            input(
                candidate(comparable = 8, exact = 5, compatible = 2, contradictions = 1),
                explicit = 8,
                trustworthyContradictions = 1,
            ),
        )

        assertThat(result.directCounterStatus).isEqualTo(ObservationResearchTriage.DirectCounterStatus.REFUTED)
        assertThat(result.feedbackAssociationStatus).isEqualTo(ObservationResearchTriage.FeedbackAssociationStatus.STRONG)
    }

    @Test
    fun `broad background behavior prevents strong maturity and asks for discrimination`() {
        val result = triage.triage(
            input(
                candidate(comparable = 8, exact = 6, compatible = 2),
                explicit = 8,
                background = ObservationResearchTriage.BackgroundSummary(8, 7, 2),
            ),
        )

        assertThat(result.feedbackAssociationStatus).isEqualTo(ObservationResearchTriage.FeedbackAssociationStatus.EMERGING)
        assertThat(result.state).isEqualTo(ObservationResearchTriage.ResearchState.PROMISING_DIRECT_COUNTER)
        assertThat(result.nextAction).isEqualTo(ObservationResearchTriage.NextActionType.DISCRIMINATE_COLLISION)
    }

    @Test
    fun `truncated strong passive evidence remains visible for audit but is not ready`() {
        val result = triage.triage(
            input(candidate(comparable = 8, exact = 6, compatible = 2), explicit = 8, truncated = true),
        )

        assertThat(result.feedbackAssociationStatus).isEqualTo(ObservationResearchTriage.FeedbackAssociationStatus.STRONG)
        assertThat(result.state).isEqualTo(ObservationResearchTriage.ResearchState.VALIDATION_BLOCKED)
        assertThat(result.queueSection).isEqualTo(ObservationResearchTriage.QueueSection.NEEDS_AUDIT)
    }

    @Test
    fun `aggregate slots remain separate identities`() {
        val aggregateZero = triage.triage(input(candidate(comparable = 5, exact = 3, compatible = 2, aggregateIndex = 0), explicit = 5))
        val aggregateOne = triage.triage(input(candidate(comparable = 5, exact = 3, compatible = 2, aggregateIndex = 1), explicit = 5))

        assertThat(aggregateZero.feedbackAssociationStatus).isEqualTo(ObservationResearchTriage.FeedbackAssociationStatus.EMERGING)
        assertThat(aggregateOne.feedbackAssociationStatus).isEqualTo(ObservationResearchTriage.FeedbackAssociationStatus.EMERGING)
    }

    private fun input(
        candidate: ObservationCandidateAnalyzer.CandidateAnalysis,
        explicit: Int,
        assumed: Int = 0,
        unavailable: Int = 0,
        trustworthyContradictions: Int = 0,
        truncated: Boolean = false,
        background: ObservationResearchTriage.BackgroundSummary = ObservationResearchTriage.BackgroundSummary(0, 0, 0),
    ) = ObservationResearchTriage.CandidateInput(
        phrase = "Frase literal",
        candidate = candidate,
        provenance = ObservationResearchTriage.RawProvenanceSummary(explicit, assumed, unavailable),
        explicitEvidence = ObservationResearchTriage.ExplicitEvidenceSummary(
            comparableObservations = explicit,
            exactCoincidences = candidate.exactSupportingEvidence,
            compatibleObservations = candidate.atLeastCompatibleCases,
            explicitContradictions = trustworthyContradictions,
        ),
        background = background,
        trustworthyContradictions = trustworthyContradictions,
        evidenceTruncated = truncated,
        hasMeaningfulVariation = true,
    )

    private fun candidate(
        comparable: Int,
        exact: Int = 0,
        compatible: Int = 0,
        contradictions: Int = 0,
        excess: Int = 0,
        aggregateIndex: Int = 0,
    ) = ObservationCandidateAnalyzer.CandidateAnalysis(
        aggregateIndex = aggregateIndex,
        code = 183,
        candidateKind = ObservationCandidateAnalyzer.CandidateKind.UNKNOWN_CANDIDATE.name,
        registryConfidence = "UNKNOWN",
        metricName = null,
        registryEvidence = null,
        annotatedMatches = comparable,
        comparableObservations = comparable,
        totalObservedOccurrences = comparable,
        aggregateLessThanObserved = contradictions,
        aggregateEqualObserved = exact,
        aggregateGreaterThanObserved = compatible,
        exactSupportingEvidence = exact,
        contradictions = contradictions,
        totalExcess = excess,
        atLeastCompatibleCases = compatible,
        classification = if (contradictions > 0) "DIRECT_COUNTER_INCOMPATIBLE" else "DIRECT_COUNTER_CANDIDATE",
        investigationStatus = if (contradictions > 0) "CONTRADICTED" else "HIGH_PRIORITY",
        investigationRank = null,
        evidence = emptyList(),
        candidateCollisions = emptyList(),
    )
}
