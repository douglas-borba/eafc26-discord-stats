package com.eafc26.discordstats.explorer

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class NovelMetricDiscoveryEngineTest {
    private val engine = NovelMetricDiscoveryEngine()

    private fun sample(index: Int, values: Map<Int, Int>, goals: Int = 0, passes: Int = 0, aggregate: Int = 0) =
        AdvancedStatsDiscoveryEngine.AggregateSample(
            clubId = "club", matchId = "m-$index", timestamp = "2026-08-28T00:00:00Z", playerId = "p-$index",
            playerName = "P$index", aggregateIndex = aggregate, sparseValues = values,
            knownMetrics = mapOf("goals" to goals, "assists" to 0, "shots" to 0, "passesAttempted" to passes,
                "passesCompleted" to passes, "tacklesAttempted" to 0, "tacklesCompleted" to 0,
                "beats" to 0, "preAssists" to 0, "throughPasses" to 0, "completedDribbles" to 0),
        )

    @Test fun `exact known duplicate is demoted while independent varied code is novel`() {
        val result = engine.analyze((0 until 8).map { i ->
            val goals = i % 3
            sample(i, mapOf(900 to goals, 901 to goals * 2, 999 to if (i % 2 == 0) i + 1 else 0), goals)
        })
        assertThat(result.candidates.single { it.code == 900 }.classification).isEqualTo("REDUNDANT_WITH_KNOWN")
        assertThat(result.candidates.single { it.code == 901 }.classification).isEqualTo("LIKELY_REDUNDANT")
        assertThat(result.candidates.single { it.code == 999 }.classification).isEqualTo("NOVEL_CANDIDATE")
    }

    @Test fun `unknown duplicate family is aggregate-local and representative is deterministic`() {
        val result = engine.analyze((0 until 8).flatMap { i ->
            listOf(sample(i, mapOf(700 to i + 1, 701 to i + 1)), sample(i + 20, mapOf(700 to i + 1, 701 to i + 1), aggregate = 1))
        })
        assertThat(result.families).hasSize(2)
        assertThat(result.families).allSatisfy { family ->
            assertThat(family.relatedCodes).containsExactly(700, 701)
            assertThat(family.representativeCode).isEqualTo(700)
        }
        assertThat(result.candidates.filter { it.code == 701 }).allSatisfy { assertThat(it.warnings).contains("UNKNOWN_FAMILY_REDUNDANCY") }
    }

    @Test fun `small sparse and constant candidates never become high novel priority`() {
        val result = engine.analyze(listOf(sample(1, mapOf(500 to 1)), sample(2, mapOf(500 to 1))))
        val candidate = result.candidates.single { it.code == 500 }
        assertThat(candidate.classification).isEqualTo("INSUFFICIENT_EVIDENCE")
        assertThat(candidate.priority).isEqualTo("LOW")
        assertThat(candidate.warnings).contains("SMALL_SAMPLE", "LOW_VARIANCE", "VERY_SPARSE")
    }

    @Test fun `aggregate indexes stay separate and detail evidence remains bounded`() {
        val samples = (0 until 30).flatMap { i -> listOf(sample(i, mapOf(400 to i), aggregate = 0), sample(i, mapOf(400 to i), aggregate = 1)) }
        val result = engine.analyze(samples)
        assertThat(result.candidates.filter { it.code == 400 }).extracting<Int> { it.aggregateIndex }.containsExactly(0, 1)
        val detail = engine.detail(samples, 0, 400)!!
        assertThat(detail.highValues).hasSizeLessThanOrEqualTo(20)
        assertThat(detail.highValues.map { it.value }).isSortedAccordingTo(Comparator.reverseOrder())
        assertThat(detail.lowNonZeroValues.map { it.value }).isSorted()
        assertThat(detail.zeroValues).allSatisfy { assertThat(it.value).isZero() }
    }

    @Test fun `confirmed advanced aggregate control demotes an otherwise unknown duplicate without registry mutation`() {
        val result = engine.analyze((0 until 8).map { i ->
            val value = (i % 3) + 1
            sample(i, mapOf(112 to value, 888 to value))
        })

        val duplicate = result.candidates.single { it.code == 888 }
        assertThat(duplicate.classification).isEqualTo("REDUNDANT_WITH_KNOWN")
        assertThat(duplicate.closestKnownRelation?.name).isEqualTo("agg0[112] Beats")
        assertThat(AdvancedStatsCodeRegistry.lookup(0, 888).confidence).isEqualTo(CodeConfidence.UNKNOWN)
    }

    @Test fun `priority reflects bounded evidence rather than a sporting interpretation`() {
        val high = engine.analyze((0 until 20).map { i -> sample(i, mapOf(650 to i + 1)) })
            .candidates.single { it.code == 650 }
        val medium = engine.analyze((0 until 8).map { i -> sample(i, mapOf(651 to i + 1)) })
            .candidates.single { it.code == 651 }

        assertThat(high.classification).isEqualTo("NOVEL_CANDIDATE")
        assertThat(high.priority).isEqualTo("HIGH")
        assertThat(medium.classification).isEqualTo("NOVEL_CANDIDATE")
        assertThat(medium.priority).isEqualTo("MEDIUM")
    }
}
