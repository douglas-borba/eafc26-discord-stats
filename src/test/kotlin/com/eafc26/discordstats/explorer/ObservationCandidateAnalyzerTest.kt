package com.eafc26.discordstats.explorer

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ObservationCandidateAnalyzerTest {
    private val analyzer = ObservationCandidateAnalyzer()

    @Test
    fun `ranks an unknown candidate with four exact AT LEAST observations as high priority`() {
        val analysis = analyzer.analyze(
            phrase = "Não seja fominha",
            observations = listOf(
                input("A", 1, mapOf(163 to 1)),
                input("B", 1, mapOf(163 to 1)),
                input("C", 2, mapOf(163 to 2)),
                input("D", 2, mapOf(163 to 2)),
            ),
            allPlayerObservations = emptyList(),
        )

        val candidate = analysis.candidates.single()
        assertThat(candidate.candidateKind).isEqualTo("UNKNOWN_CANDIDATE")
        assertThat(candidate.investigationStatus).isEqualTo("HIGH_PRIORITY")
        assertThat(candidate.aggregateEqualObserved).isEqualTo(4)
        assertThat(candidate.totalExcess).isZero()
        assertThat(candidate.metricName).isNull()
    }

    @Test
    fun `keeps known metrics as visible controls and reports a collision without semantic promotion`() {
        val analysis = analyzer.analyze(
            phrase = "Melhore seu tempo de bola",
            observations = listOf(
                input("B", 2, mapOf(112 to 3, 183 to 3)),
                input("D", 2, mapOf(112 to 2, 183 to 2)),
                input("E", 4, mapOf(112 to 4, 183 to 4)),
            ),
            allPlayerObservations = emptyList(),
        )

        val unknown = analysis.candidates.first { it.code == 183 }
        val control = analysis.candidates.first { it.code == 112 }
        assertThat(unknown.candidateKind).isEqualTo("UNKNOWN_CANDIDATE")
        assertThat(unknown.investigationStatus).isEqualTo("HIGH_PRIORITY")
        assertThat(unknown.aggregateEqualObserved).isEqualTo(2)
        assertThat(unknown.atLeastCompatibleCases).isEqualTo(1)
        assertThat(unknown.totalExcess).isEqualTo(1)
        assertThat(unknown.candidateCollisions).anySatisfy {
            assertThat(it.aggregateIndex to it.code).isEqualTo(0 to 112)
            assertThat(it.candidateKind).isEqualTo("KNOWN_CONTROL")
            assertThat(it.metricName).isEqualTo("Beats")
        }
        assertThat(control.candidateKind).isEqualTo("KNOWN_CONTROL")
        assertThat(control.metricName).isEqualTo("Beats")
        assertThat(control.investigationRank).isNull()
    }

    @Test
    fun `ranks the three exact offensive-effort regression observations without assigning meaning`() {
        val analysis = analyzer.analyze(
            phrase = "Ótimo empenho ofensivo",
            observations = listOf(
                input("A", 1, mapOf(100 to 1)),
                input("D", 2, mapOf(100 to 2)),
                input("E", 1, mapOf(100 to 1)),
            ),
            allPlayerObservations = emptyList(),
        )

        val candidate = analysis.candidates.single()
        assertThat(candidate.investigationStatus).isEqualTo("HIGH_PRIORITY")
        assertThat(candidate.aggregateEqualObserved).isEqualTo(3)
        assertThat(candidate.totalExcess).isZero()
        assertThat(candidate.metricName).isNull()
    }

    @Test
    fun `keeps aggregate namespaces separate and distinguishes contradicted and insufficient evidence`() {
        val analysis = analyzer.analyze(
            phrase = "Ótima interceptação",
            observations = listOf(input("C", 1, aggregate0 = mapOf(6 to 0), aggregate1 = mapOf(6 to 1))),
            allPlayerObservations = emptyList(),
        )

        val aggregate0 = analysis.candidates.first { it.aggregateIndex == 0 && it.code == 6 }
        val aggregate1 = analysis.candidates.first { it.aggregateIndex == 1 && it.code == 6 }
        assertThat(aggregate0.investigationStatus).isEqualTo("CONTRADICTED")
        assertThat(aggregate1.investigationStatus).isEqualTo("INSUFFICIENT_EVIDENCE")
        assertThat(aggregate1.evidence.single().comparison).isEqualTo("EXACT_COINCIDENCE")
    }

    @Test
    fun `preserves raw 174 as an unknown insufficient candidate with meaningful excess`() {
        val analysis = analyzer.analyze(
            phrase = "Ótima finta",
            observations = listOf(input("A", 2, mapOf(174 to 29))),
            allPlayerObservations = emptyList(),
        )

        val candidate = analysis.candidates.single()
        assertThat(candidate.candidateKind).isEqualTo("UNKNOWN_CANDIDATE")
        assertThat(candidate.registryConfidence).isEqualTo("UNKNOWN")
        assertThat(candidate.metricName).isNull()
        assertThat(candidate.investigationStatus).isEqualTo("INSUFFICIENT_EVIDENCE")
        assertThat(candidate.atLeastCompatibleCases).isEqualTo(1)
        assertThat(candidate.totalExcess).isEqualTo(27)
    }

    @Test
    fun `does not convert a missing phrase into a zero observation and reports phrase collisions deterministically`() {
        val analysis = analyzer.analyze(
            phrase = "Bela dividida",
            observations = listOf(input("A", 2, mapOf(7 to 2))),
            allPlayerObservations = listOf(
                recorded("A", "Bela dividida", 2),
                recorded("A", "Ótima finta", 2),
                recorded("B", "Outra frase", 0),
            ),
        )

        assertThat(analysis.annotatedMatches).isEqualTo(1)
        assertThat(analysis.candidates.single().comparableObservations).isEqualTo(1)
        assertThat(analysis.candidates.single().evidence.map { it.matchId }).containsExactly("A")
        assertThat(analysis.observationCollisions).containsExactly(
            ObservationCandidateAnalyzer.ObservationCollision("Ótima finta", 1),
        )
        assertThat(analysis.nextBestExperiments).anySatisfy {
            assertThat(it).contains("Observe separadamente esta frase e Ótima finta")
        }
    }

    @Test
    fun `EXACT observation rejects a greater aggregate while AT LEAST accepts it`() {
        val exact = analyzer.analyze(
            "Ação",
            listOf(input("A", 2, mapOf(9 to 3), completeness = ObservationCompleteness.EXACT)),
            emptyList(),
        ).candidates.single()
        val atLeast = analyzer.analyze(
            "Ação",
            listOf(input("A", 2, mapOf(9 to 3), completeness = ObservationCompleteness.AT_LEAST)),
            emptyList(),
        ).candidates.single()

        assertThat(exact.investigationStatus).isEqualTo("CONTRADICTED")
        assertThat(exact.contradictions).isEqualTo(1)
        assertThat(atLeast.evidence.single().comparison).isEqualTo("AT_LEAST_COMPATIBLE")
    }

    @Test
    fun `reports unknown candidate collisions independently of controls`() {
        val analysis = analyzer.analyze(
            "Ação",
            listOf(
                input("A", 1, mapOf(7 to 1, 8 to 1)),
                input("B", 2, mapOf(7 to 2, 8 to 2)),
            ),
            emptyList(),
        )

        val code7 = analysis.candidates.first { it.code == 7 }
        assertThat(code7.candidateCollisions).anySatisfy {
            assertThat(it.aggregateIndex to it.code).isEqualTo(0 to 8)
            assertThat(it.candidateKind).isEqualTo("UNKNOWN_CANDIDATE")
        }
        assertThat(analysis.nextBestExperiments.first()).contains("agg0[7] e agg0[8]")
    }

    private fun input(
        matchId: String,
        observed: Int,
        aggregate0: Map<Int, Int>,
        aggregate1: Map<Int, Int>? = null,
        completeness: ObservationCompleteness = ObservationCompleteness.AT_LEAST,
    ) = ObservationCandidateAnalyzer.ObservationInput(
        matchId = matchId,
        opponentName = "Opponent $matchId",
        observedCount = observed,
        completeness = completeness,
        aggregates = buildMap {
            put(0, aggregate0)
            if (aggregate1 != null) put(1, aggregate1)
        },
    )

    private fun recorded(matchId: String, phrase: String, observed: Int) =
        ObservationCandidateAnalyzer.RecordedObservation(matchId, phrase, observed, ObservationCompleteness.AT_LEAST)
}
