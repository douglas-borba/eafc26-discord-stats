package com.eafc26.discordstats.explorer

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ObservationalEvidenceEngineTest {
    private val engine = ObservationalEvidenceEngine()

    @Test
    fun `AT LEAST evidence contradicts only aggregate values below definitely observed counts`() {
        val result = engine.analyze(listOf(
            sample("m1", 0, 182, 4, 4, ObservationCompleteness.AT_LEAST),
            sample("m2", 0, 182, 3, 1, ObservationCompleteness.AT_LEAST),
            sample("m3", 0, 182, 1, 5, ObservationCompleteness.AT_LEAST),
        )).single()

        assertThat(result.aggregateLessThanObserved).isEqualTo(1)
        assertThat(result.aggregateEqualObserved).isEqualTo(1)
        assertThat(result.aggregateGreaterThanObserved).isEqualTo(1)
        assertThat(result.classification).isEqualTo("DIRECT_COUNTER_INCOMPATIBLE")
    }

    @Test
    fun `EXACT evidence requires equality but never creates confirmed semantics`() {
        val incompatible = engine.analyze(listOf(
            sample("m1", 1, 24, 4, 4, ObservationCompleteness.EXACT),
            sample("m2", 1, 24, 5, 4, ObservationCompleteness.EXACT),
        )).single()
        val possible = engine.analyze(listOf(
            sample("m1", 1, 24, 4, 4, ObservationCompleteness.EXACT),
            sample("m2", 1, 24, 1, 1, ObservationCompleteness.EXACT),
        )).single()

        assertThat(incompatible.classification).isEqualTo("DIRECT_COUNTER_INCOMPATIBLE")
        assertThat(possible.classification).isEqualTo("DIRECT_COUNTER_POSSIBLE")
        assertThat(possible.exactSupportingEvidence).isEqualTo(2)
    }

    @Test
    fun `aggregate index remains part of candidate identity`() {
        val candidates = engine.analyze(listOf(
            sample("m1", 0, 24, 1, 1, ObservationCompleteness.AT_LEAST),
            sample("m1", 1, 24, 1, 1, ObservationCompleteness.AT_LEAST),
        ))
        assertThat(candidates.map { it.aggregateIndex to it.code }).containsExactly(0 to 24, 1 to 24)
    }

    private fun sample(match: String, aggregate: Int, code: Int, value: Int, observed: Int, mode: ObservationCompleteness) =
        ObservationalEvidenceEngine.Sample(match, aggregate, code, value, observed, mode)
}
