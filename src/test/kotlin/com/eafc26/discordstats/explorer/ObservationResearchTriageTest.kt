package com.eafc26.discordstats.explorer

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ObservationResearchTriageTest {
    private val triage = ObservationResearchTriage()

    @Test
    fun `tiny exact sample remains collect more`() {
        val result = triage.triage(input(candidate(comparable = 2, exact = 2), explicit = 2))

        assertThat(result.state).isEqualTo(ObservationResearchTriage.ResearchState.COLLECT_MORE)
        assertThat(result.nextAction).isEqualTo(ObservationResearchTriage.NextActionType.CONTINUE_PASSIVE_COLLECTION)
    }

    @Test
    fun `mature explicit zero-contradiction pattern is ready for controlled test`() {
        val result = triage.triage(input(candidate(comparable = 12, exact = 9, compatible = 3, excess = 3), explicit = 12))

        assertThat(result.state).isEqualTo(ObservationResearchTriage.ResearchState.READY_FOR_CONTROLLED_TEST)
        assertThat(result.directCounterValidity).isEqualTo(ObservationResearchTriage.DirectCounterValidity.NOT_REFUTED)
        assertThat(result.priority).isEqualTo(ObservationResearchTriage.ResearchPriority.P1)
        assertThat(result.nextAction).isEqualTo(ObservationResearchTriage.NextActionType.CONTROLLED_HIGH_COUNT_TARGET)
        assertThat(result.nextActionText).contains("Frase literal")
    }

    @Test
    fun `one trustworthy contradiction permanently refutes direct counter despite many exact rows`() {
        val result = triage.triage(input(candidate(comparable = 11, exact = 10, contradictions = 1), explicit = 11, trustworthyContradictions = 1))

        assertThat(result.directCounterValidity).isEqualTo(ObservationResearchTriage.DirectCounterValidity.REFUTED)
        assertThat(result.state).isEqualTo(ObservationResearchTriage.ResearchState.ASSOCIATED_BUT_NOT_DIRECT)
        assertThat(result.nextAction).isNotEqualTo(ObservationResearchTriage.NextActionType.CONTROLLED_HIGH_COUNT_TARGET)
    }

    @Test
    fun `183-style evidence retains association interest but cannot be a direct candidate`() {
        val result = triage.triage(input(candidate(comparable = 11, exact = 6, compatible = 3, contradictions = 2, excess = 5), explicit = 11, trustworthyContradictions = 2))

        assertThat(result.state).isEqualTo(ObservationResearchTriage.ResearchState.ASSOCIATED_BUT_NOT_DIRECT)
        assertThat(result.directCounterValidity).isEqualTo(ObservationResearchTriage.DirectCounterValidity.REFUTED)
        assertThat(result.associationInterest).isEqualTo(ObservationResearchTriage.AssociationInterest.HIGH)
        assertThat(result.queueSection).isEqualTo(ObservationResearchTriage.QueueSection.ASSOCIATED_NOT_DIRECT)
        assertThat(result.nextAction).isEqualTo(ObservationResearchTriage.NextActionType.RETAIN_ASSOCIATION_ONLY)
    }

    @Test
    fun `all contradictory candidate has refuted low research priority`() {
        val result = triage.triage(input(candidate(comparable = 11, contradictions = 11), explicit = 11, trustworthyContradictions = 11))

        assertThat(result.state).isEqualTo(ObservationResearchTriage.ResearchState.DIRECT_COUNTER_REFUTED)
        assertThat(result.associationInterest).isEqualTo(ObservationResearchTriage.AssociationInterest.NONE)
        assertThat(result.priority).isEqualTo(ObservationResearchTriage.ResearchPriority.P4)
    }

    @Test
    fun `assumed zero unavailable and truncated evidence cannot receive strongest maturity`() {
        val mature = candidate(comparable = 12, exact = 9, compatible = 3, excess = 3)

        val assumed = triage.triage(input(mature, explicit = 9, assumed = 3))
        val unavailable = triage.triage(input(mature, explicit = 9, unavailable = 3))
        val truncated = triage.triage(input(mature, explicit = 12, truncated = true))

        assertThat(assumed.state).isEqualTo(ObservationResearchTriage.ResearchState.VALIDATION_BLOCKED)
        assertThat(assumed.directCounterValidity).isEqualTo(ObservationResearchTriage.DirectCounterValidity.INTEGRITY_LIMITED)
        assertThat(unavailable.state).isEqualTo(ObservationResearchTriage.ResearchState.VALIDATION_BLOCKED)
        assertThat(truncated.state).isEqualTo(ObservationResearchTriage.ResearchState.VALIDATION_BLOCKED)
    }

    @Test
    fun `promising collision recommends discrimination instead of choosing a candidate`() {
        val collision = ObservationCandidateAnalyzer.CandidateCollision(
            aggregateIndex = 1,
            code = 183,
            candidateKind = ObservationCandidateAnalyzer.CandidateKind.UNKNOWN_CANDIDATE.name,
            registryConfidence = "UNKNOWN",
            metricName = null,
        )
        val result = triage.triage(
            input(
                candidate(comparable = 4, exact = 3, compatible = 1, collisions = listOf(collision)),
                explicit = 4,
            ),
        )

        assertThat(result.state).isEqualTo(ObservationResearchTriage.ResearchState.PROMISING_DIRECT_COUNTER)
        assertThat(result.nextAction).isEqualTo(ObservationResearchTriage.NextActionType.DISCRIMINATE_COLLISION)
        assertThat(result.nextActionText).contains("agg0[183]").contains("agg1[183]")
    }

    @Test
    fun `assumed zero noise is not promoted into the research queue`() {
        val result = triage.triage(
            input(
                candidate(comparable = 15, contradictions = 14),
                explicit = 1,
                assumed = 14,
            ),
        )

        assertThat(result.state).isEqualTo(ObservationResearchTriage.ResearchState.COLLECT_MORE)
        assertThat(result.directCounterValidity).isEqualTo(ObservationResearchTriage.DirectCounterValidity.INTEGRITY_LIMITED)
        assertThat(result.associationInterest).isEqualTo(ObservationResearchTriage.AssociationInterest.NONE)
        assertThat(result.queueSection).isNull()
    }

    @Test
    fun `one compatible assumed zero background candidate is not a queue item`() {
        val result = triage.triage(
            input(
                candidate(comparable = 15, compatible = 1, contradictions = 14),
                explicit = 1,
                assumed = 14,
            ),
        )

        assertThat(result.state).isEqualTo(ObservationResearchTriage.ResearchState.COLLECT_MORE)
        assertThat(result.queueSection).isNull()
    }

    @Test
    fun `repeated explicit emerging pattern is visible before direct maturity`() {
        val result = triage.triage(
            input(candidate(comparable = 5, exact = 2, compatible = 1), explicit = 5),
        )

        assertThat(result.state).isEqualTo(ObservationResearchTriage.ResearchState.COLLECT_MORE)
        assertThat(result.queueSection).isEqualTo(ObservationResearchTriage.QueueSection.PROMISING_CONTINUE_COLLECTING)
    }

    @Test
    fun `truncated but otherwise meaningful pattern is an audit action not ready`() {
        val result = triage.triage(
            input(candidate(comparable = 8, exact = 6, compatible = 2, excess = 2), explicit = 8, truncated = true),
        )

        assertThat(result.state).isEqualTo(ObservationResearchTriage.ResearchState.VALIDATION_BLOCKED)
        assertThat(result.queueSection).isEqualTo(ObservationResearchTriage.QueueSection.NEEDS_AUDIT)
    }

    private fun input(
        candidate: ObservationCandidateAnalyzer.CandidateAnalysis,
        explicit: Int,
        assumed: Int = 0,
        unavailable: Int = 0,
        trustworthyContradictions: Int = 0,
        truncated: Boolean = false,
        explicitExact: Int = candidate.exactSupportingEvidence,
        explicitCompatible: Int = candidate.atLeastCompatibleCases,
    ) = ObservationResearchTriage.CandidateInput(
        phrase = "Frase literal",
        candidate = candidate,
        provenance = ObservationResearchTriage.RawProvenanceSummary(explicit, assumed, unavailable),
        explicitEvidence = ObservationResearchTriage.ExplicitEvidenceSummary(
            comparableObservations = explicit,
            exactCoincidences = explicitExact,
            compatibleObservations = explicitCompatible,
        ),
        trustworthyContradictions = trustworthyContradictions,
        evidenceTruncated = truncated,
    )

    private fun candidate(
        comparable: Int,
        exact: Int = 0,
        compatible: Int = 0,
        contradictions: Int = 0,
        excess: Int = 0,
        collisions: List<ObservationCandidateAnalyzer.CandidateCollision> = emptyList(),
    ) = ObservationCandidateAnalyzer.CandidateAnalysis(
        aggregateIndex = 0,
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
        candidateCollisions = collisions,
    )
}
