package com.eafc26.discordstats.explorer

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.math.abs

class AdvancedStatsDiscoveryEngineV2Test {

    private val engine = AdvancedStatsDiscoveryEngine(
        AdvancedStatsDiscoveryEngine.Thresholds(
            minimumObservations = 3,
            minimumMatches = 2,
            candidateObservations = 4,
            candidateMatches = 2,
            strongObservations = 6,
            strongMatches = 3,
            minimumInformativeObservations = 2,
            candidateInformativeObservations = 3,
            strongInformativeObservations = 4,
            maximumTernaryCodes = 9,
            maximumRelations = 1_000,
        ),
    )

    private fun sample(
        index: Int,
        values: Map<Int, Int>,
        aggregate: Int = 0,
        goals: Int = 0,
        assists: Int = 0,
        shots: Int = 0,
        passesCompleted: Int = 0,
        tacklesCompleted: Int = 0,
        matchCompletion: String = "COMPLETED",
        playerId: String = "player-$index",
    ) = AdvancedStatsDiscoveryEngine.AggregateSample(
        clubId = "club-1",
        matchId = "match-$index",
        timestamp = "2026-08-28T00:00:00Z",
        playerId = playerId,
        playerName = "Player $index",
        aggregateIndex = aggregate,
        sparseValues = values,
        knownMetrics = mapOf(
            "goals" to goals, "assists" to assists, "shots" to shots,
            "passesAttempted" to 0, "passesCompleted" to passesCompleted,
            "tacklesAttempted" to 0, "tacklesCompleted" to tacklesCompleted,
        ),
        matchCompletion = matchCompletion,
    )

    @Test
    fun `exact duplicate is classified as NEAR_DUPLICATE`() {
        val samples = (0 until 8).map { i ->
            val v = i + 1
            sample(i, mapOf(10 to v, 11 to v, 12 to i * 2))
        }
        val result = engine.investigateAnchor(samples, AdvancedStatsDiscoveryEngine.AnchorRef("AGGREGATE_CODE", 0, 10, null))
        val rel = result.relationships.first { it.candidateCode == 11 }
        assertThat(rel.technicalClassification).isEqualTo("NEAR_DUPLICATE")
        assertThat(rel.informativeEqualityRate).isEqualTo(1.0)
        assertThat(rel.differenceCases).isEmpty()
    }

    @Test
    fun `near duplicate has non-empty difference cases`() {
        val samples = (0 until 8).map { i ->
            val v = i + 1
            sample(i, mapOf(10 to v, 11 to if (i == 3) v + 1 else v, 12 to i))
        }
        val result = engine.investigateAnchor(samples, AdvancedStatsDiscoveryEngine.AnchorRef("AGGREGATE_CODE", 0, 10, null))
        val rel = result.relationships.first { it.candidateCode == 11 }
        assertThat(rel.technicalClassification).isIn("NEAR_DUPLICATE", "POSSIBLE_SUBTYPE", "POSSIBLE_SUPERSET")
        assertThat(rel.differenceCases).hasSize(1)
        assertThat(rel.differenceCases[0].difference).isNotEqualTo(0)
    }

    @Test
    fun `possible subtype detected when B lte A consistently`() {
        val samples = (0 until 8).map { i ->
            val a = i + 3
            val b = if (a > 0) (a - 1).coerceAtLeast(0) else 0
            sample(i, mapOf(10 to a, 11 to b, 12 to i))
        }
        val result = engine.investigateAnchor(samples, AdvancedStatsDiscoveryEngine.AnchorRef("AGGREGATE_CODE", 0, 10, null))
        val rel = result.relationships.first { it.candidateCode == 11 }
        assertThat(rel.technicalClassification).isEqualTo("POSSIBLE_SUBTYPE")
        assertThat(rel.anchorGteCandidateRate).isGreaterThanOrEqualTo(0.85)
    }

    @Test
    fun `possible superset detected when B gte A consistently`() {
        val samples = (0 until 8).map { i ->
            val a = i + 1
            val b = a + 2
            sample(i, mapOf(10 to a, 11 to b, 12 to i))
        }
        val result = engine.investigateAnchor(samples, AdvancedStatsDiscoveryEngine.AnchorRef("AGGREGATE_CODE", 0, 10, null))
        val rel = result.relationships.first { it.candidateCode == 11 }
        assertThat(rel.technicalClassification).isEqualTo("POSSIBLE_SUPERSET")
    }

    @Test
    fun `related but not subset when correlated but not ordered`() {
        val samples = (0 until 8).map { i ->
            val a = i + 1
            val b = if (i % 2 == 0) a + 1 else a - 1
            sample(i, mapOf(10 to a, 11 to b, 12 to i))
        }
        val result = engine.investigateAnchor(samples, AdvancedStatsDiscoveryEngine.AnchorRef("AGGREGATE_CODE", 0, 10, null))
        val rel = result.relationships.first { it.candidateCode == 11 }
        assertThat(rel.technicalClassification).isIn("RELATED", "NEAR_DUPLICATE")
    }

    @Test
    fun `independent codes produce INDEPENDENT classification`() {
        val samples = (0 until 8).map { i ->
            sample(i, mapOf(10 to if (i < 4) i + 1 else 0, 11 to if (i >= 4) i else 0, 12 to i + 1))
        }
        val result = engine.investigateAnchor(samples, AdvancedStatsDiscoveryEngine.AnchorRef("AGGREGATE_CODE", 0, 10, null))
        val rel = result.relationships.first { it.candidateCode == 11 }
        assertThat(rel.technicalClassification).isIn("INDEPENDENT", "RELATED")
        assertThat(rel.nonZeroOverlap).isLessThan(0.5)
    }

    @Test
    fun `zero-zero observations do not inflate equality metrics`() {
        val samples = (0 until 10).map { i ->
            if (i < 7) sample(i, mapOf(10 to 0, 11 to 0, 12 to i + 1))
            else sample(i, mapOf(10 to i, 11 to i + 1, 12 to i))
        }
        val result = engine.investigateAnchor(samples, AdvancedStatsDiscoveryEngine.AnchorRef("AGGREGATE_CODE", 0, 10, null))
        val rel = result.relationships.first { it.candidateCode == 11 }
        assertThat(rel.pEqualGivenEitherActive).isLessThan(1.0)
        assertThat(rel.informativeEqualityRate).isLessThan(1.0)
    }

    @Test
    fun `residual distribution is computed correctly`() {
        val samples = (0 until 6).map { i ->
            sample(i, mapOf(10 to i + 2, 11 to i + 1, 12 to i))
        }
        val result = engine.investigateAnchor(samples, AdvancedStatsDiscoveryEngine.AnchorRef("AGGREGATE_CODE", 0, 10, null))
        val rel = result.relationships.first { it.candidateCode == 11 }
        assertThat(rel.residualAMinusB.residualCounts).containsKey(1)
        assertThat(rel.residualAMinusB.mean).isEqualTo(1.0)
        assertThat(rel.residualAMinusB.zeroPercent).isEqualTo(0.0)
    }

    @Test
    fun `conditional probabilities computed correctly`() {
        val samples = (0 until 8).map { i ->
            val a = if (i < 6) i + 1 else 0
            val b = if (i < 4) i + 1 else 0
            sample(i, mapOf(10 to a, 11 to b, 12 to i + 1))
        }
        val result = engine.investigateAnchor(samples, AdvancedStatsDiscoveryEngine.AnchorRef("AGGREGATE_CODE", 0, 10, null))
        val rel = result.relationships.first { it.candidateCode == 11 }
        assertThat(rel.pCandidateActiveGivenAnchorActive).isGreaterThan(0.0)
        assertThat(rel.pAnchorActiveGivenCandidateActive).isEqualTo(1.0)
    }

    @Test
    fun `known metric anchor works for assists`() {
        val samples = (0 until 8).map { i ->
            sample(i, mapOf(11 to i % 3, 12 to i + 1), assists = i % 3)
        }
        val result = engine.investigateAnchor(samples, AdvancedStatsDiscoveryEngine.AnchorRef("KNOWN_METRIC", null, null, "assists"))
        assertThat(result.anchor.knownLabel).isEqualTo("assists")
        assertThat(result.anchor.registryStatus).isEqualTo("CONFIRMED")
        val rel = result.relationships.firstOrNull { it.candidateCode == 11 }
        assertThat(rel).isNotNull
        assertThat(rel!!.informativeEqualityRate).isEqualTo(1.0)
    }

    @Test
    fun `confirmed advanced anchor works for code 174`() {
        val samples = (0 until 6).map { i ->
            sample(i, mapOf(174 to i + 1, 175 to i * 2, 176 to i))
        }
        val result = engine.investigateAnchor(samples, AdvancedStatsDiscoveryEngine.AnchorRef("CONFIRMED_ADVANCED", 0, 174, null))
        assertThat(result.anchor.knownLabel).isEqualTo("Completed dribbles")
        assertThat(result.relationships).isNotEmpty
        assertThat(result.relationships.none { it.candidateCode == 174 }).isTrue()
    }

    @Test
    fun `distinct matches and distinct players are reported`() {
        val samples = (0 until 6).flatMap { i ->
            listOf(
                sample(i, mapOf(10 to i + 1, 11 to i + 1), playerId = "player-A"),
                sample(i, mapOf(10 to i + 2, 11 to i + 2), playerId = "player-B"),
            )
        }
        val result = engine.investigateAnchor(samples, AdvancedStatsDiscoveryEngine.AnchorRef("AGGREGATE_CODE", 0, 10, null))
        assertThat(result.anchor.distinctPlayers).isEqualTo(2)
        val rel = result.relationships.first { it.candidateCode == 11 }
        assertThat(rel.distinctPlayers).isEqualTo(2)
        assertThat(rel.matches).isEqualTo(6)
    }

    @Test
    fun `family matrix computes pairwise relationships`() {
        val samples = (0 until 6).map { i ->
            sample(i, mapOf(10 to i + 1, 11 to (i + 1) * 2, 12 to i + 1))
        }
        val result = engine.investigateFamily(samples, 0, listOf(10, 11, 12))
        assertThat(result.codes).containsExactly(10, 11, 12)
        assertThat(result.matrix).hasSize(3)
        val cell10_12 = result.matrix.first { it.codeA == 10 && it.codeB == 12 }
        assertThat(cell10_12.informativeEquality).isEqualTo(1.0)
        assertThat(result.observations).hasSize(6)
    }

    @Test
    fun `family observations include known metrics and match completion`() {
        val samples = (0 until 4).map { i ->
            sample(i, mapOf(10 to i, 11 to i + 1), goals = i, matchCompletion = if (i == 2) "DNF" else "COMPLETED")
        }
        val result = engine.investigateFamily(samples, 0, listOf(10, 11))
        val dnfObs = result.observations.filter { it.matchCompletion == "DNF" }
        assertThat(dnfObs).hasSize(1)
        assertThat(result.observations.first().goals).isNotNull
    }

    @Test
    fun `DNF metadata is present in evidence rows`() {
        val samples = listOf(
            sample(0, mapOf(10 to 5, 11 to 5), matchCompletion = "COMPLETED"),
            sample(1, mapOf(10 to 3, 11 to 4), matchCompletion = "DNF"),
            sample(2, mapOf(10 to 2, 11 to 2), matchCompletion = "COMPLETED"),
            sample(3, mapOf(10 to 1, 11 to 1), matchCompletion = "COMPLETED"),
        )
        val result = engine.investigateAnchor(samples, AdvancedStatsDiscoveryEngine.AnchorRef("AGGREGATE_CODE", 0, 10, null))
        val rel = result.relationships.first { it.candidateCode == 11 }
        val dnfRows = rel.evidenceObservations.filter { it.matchCompletion == "DNF" }
        assertThat(dnfRows).hasSize(1)
        assertThat(dnfRows[0].matchId).isEqualTo("match-1")
    }

    @Test
    fun `spearman correlation is computed`() {
        val samples = (0 until 6).map { i ->
            sample(i, mapOf(10 to i + 1, 11 to (i + 1) * 3, 12 to i))
        }
        val result = engine.investigateAnchor(samples, AdvancedStatsDiscoveryEngine.AnchorRef("AGGREGATE_CODE", 0, 10, null))
        val rel = result.relationships.first { it.candidateCode == 11 }
        assertThat(rel.spearman).isNotNull
        assertThat(abs(rel.spearman!!)).isGreaterThan(0.9)
    }

    @Test
    fun `anchor score rewards match and player diversity`() {
        val singlePlayer = (0 until 6).map { i ->
            sample(i, mapOf(10 to i + 1, 11 to i + 1), playerId = "player-A")
        }
        val multiPlayer = (0 until 6).map { i ->
            sample(i, mapOf(10 to i + 1, 11 to i + 1), playerId = "player-${i % 3}")
        }
        val r1 = engine.investigateAnchor(singlePlayer, AdvancedStatsDiscoveryEngine.AnchorRef("AGGREGATE_CODE", 0, 10, null))
        val r2 = engine.investigateAnchor(multiPlayer, AdvancedStatsDiscoveryEngine.AnchorRef("AGGREGATE_CODE", 0, 10, null))
        val s1 = r1.relationships.first { it.candidateCode == 11 }.score.total
        val s2 = r2.relationships.first { it.candidateCode == 11 }.score.total
        assertThat(s2).isGreaterThan(s1)
    }

    @Test
    fun `bounded repository - anchor uses only provided samples`() {
        val samples = (0 until 4).map { i -> sample(i, mapOf(10 to i + 1, 11 to i + 1)) }
        val result = engine.investigateAnchor(samples, AdvancedStatsDiscoveryEngine.AnchorRef("AGGREGATE_CODE", 0, 10, null))
        assertThat(result.dataset.rawMatchesAnalyzed).isEqualTo(4)
        assertThat(result.anchor.observations).isEqualTo(4)
    }

    @Test
    fun `engine distinguishes exact duplicate from imperfect near duplicate`() {
        val samples = (0 until 8).map { i ->
            val v = i + 1
            sample(i, mapOf(10 to v, 11 to v, 12 to if (i == 5) v + 1 else v, 13 to i * 3))
        }
        val result = engine.investigateAnchor(samples, AdvancedStatsDiscoveryEngine.AnchorRef("AGGREGATE_CODE", 0, 10, null))
        val exact = result.relationships.first { it.candidateCode == 11 }
        val imperfect = result.relationships.first { it.candidateCode == 12 }
        assertThat(exact.informativeEqualityRate).isEqualTo(1.0)
        assertThat(imperfect.informativeEqualityRate).isLessThan(1.0)
        assertThat(exact.differenceCases).isEmpty()
        assertThat(imperfect.differenceCases).isNotEmpty
    }
}
