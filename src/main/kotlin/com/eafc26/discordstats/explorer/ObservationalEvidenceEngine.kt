package com.eafc26.discordstats.explorer

/**
 * Tests only the narrow direct 1:1 counter hypothesis. It intentionally does
 * not infer a sporting meaning or update the aggregate registry.
 */
class ObservationalEvidenceEngine {
    data class Sample(
        val matchId: String,
        val aggregateIndex: Int,
        val code: Int,
        val aggregateValue: Int,
        val observedCount: Int,
        val completeness: ObservationCompleteness,
    )

    data class Candidate(
        val aggregateIndex: Int,
        val code: Int,
        val annotatedMatches: Int,
        val comparableObservations: Int,
        val totalObservedOccurrences: Int,
        val aggregateLessThanObserved: Int,
        val aggregateEqualObserved: Int,
        val aggregateGreaterThanObserved: Int,
        val exactSupportingEvidence: Int,
        val classification: String,
    )

    fun analyze(samples: List<Sample>): List<Candidate> = samples
        .groupBy { it.aggregateIndex to it.code }
        .map { (identity, rows) ->
            val less = rows.count { row ->
                when (row.completeness) {
                    ObservationCompleteness.AT_LEAST -> row.aggregateValue < row.observedCount
                    ObservationCompleteness.EXACT -> row.aggregateValue < row.observedCount
                }
            }
            val equal = rows.count { it.aggregateValue == it.observedCount }
            val greater = rows.count { row ->
                when (row.completeness) {
                    ObservationCompleteness.AT_LEAST -> row.aggregateValue > row.observedCount
                    ObservationCompleteness.EXACT -> row.aggregateValue > row.observedCount
                }
            }
            val exactSupporting = rows.count { it.completeness == ObservationCompleteness.EXACT && it.aggregateValue == it.observedCount }
            val hasContradiction = rows.any { row ->
                when (row.completeness) {
                    ObservationCompleteness.AT_LEAST -> row.aggregateValue < row.observedCount
                    ObservationCompleteness.EXACT -> row.aggregateValue != row.observedCount
                }
            }
            Candidate(
                aggregateIndex = identity.first,
                code = identity.second,
                annotatedMatches = rows.map { it.matchId }.toSet().size,
                comparableObservations = rows.size,
                totalObservedOccurrences = rows.sumOf { it.observedCount },
                aggregateLessThanObserved = less,
                aggregateEqualObserved = equal,
                aggregateGreaterThanObserved = greater,
                exactSupportingEvidence = exactSupporting,
                classification = when {
                    hasContradiction -> "DIRECT_COUNTER_INCOMPATIBLE"
                    rows.size < 2 -> "INSUFFICIENT_OBSERVATIONS"
                    else -> "DIRECT_COUNTER_POSSIBLE"
                },
            )
        }
        .sortedWith(compareBy<Candidate> { it.classification != "DIRECT_COUNTER_INCOMPATIBLE" }.thenBy { it.aggregateIndex }.thenBy { it.code })
}
