package com.eafc26.discordstats.explorer

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.math.abs

class AdvancedStatsDiscoveryEngineV3Test {

    private val engine = AdvancedStatsDiscoveryEngine()

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
        matchId: String = "match-$index",
    ) = AdvancedStatsDiscoveryEngine.AggregateSample(
        clubId = "club-1",
        matchId = matchId,
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

    private fun anchor(agg: Int? = 0, code: Int? = 10) =
        AdvancedStatsDiscoveryEngine.AnchorRef("AGGREGATE_CODE", agg, code, null)

    private fun request(anchorCode: Int = 10, candidateCode: Int = 11, candidateAgg: Int = 0) =
        AdvancedStatsDiscoveryEngine.ResidualExplainerRequest(anchor(code = anchorCode), candidateAgg, candidateCode)

    // 1. Residual direction is correct
    @Test
    fun `residual direction computed correctly`() {
        val samples = (0 until 6).map { i ->
            sample(i, mapOf(10 to 5, 11 to if (i < 2) 6 else if (i < 4) 5 else 4, 20 to i))
        }
        val result = engine.explainResiduals(samples, request())
        val groups = result.groups.associateBy { it.direction }
        assertThat(groups["NEGATIVE"]!!.count).isEqualTo(2)
        assertThat(groups["ZERO"]!!.count).isEqualTo(2)
        assertThat(groups["POSITIVE"]!!.count).isEqualTo(2)
    }

    // 2. Groups have correct match/player counts
    @Test
    fun `groups report correct match and player counts`() {
        val samples = (0 until 6).map { i ->
            sample(i, mapOf(10 to 5, 11 to if (i < 3) 5 else 4), playerId = "player-${i % 2}", matchId = "match-$i")
        }
        val result = engine.explainResiduals(samples, request())
        val pos = result.groups.first { it.direction == "POSITIVE" }
        assertThat(pos.count).isEqualTo(3)
        assertThat(pos.matches).isEqualTo(3)
    }

    // 3. All zeros produces only ZERO group
    @Test
    fun `perfect equality produces only zero group with observations`() {
        val samples = (0 until 8).map { i -> sample(i, mapOf(10 to i + 1, 11 to i + 1, 20 to i)) }
        val result = engine.explainResiduals(samples, request())
        val zero = result.groups.first { it.direction == "ZERO" }
        assertThat(zero.count).isEqualTo(8)
        assertThat(result.evidence).isEmpty()
        assertThat(result.signatures).isEmpty()
    }

    // 4. Evidence only contains non-zero residual
    @Test
    fun `evidence rows contain only non-zero residual observations`() {
        val samples = (0 until 8).map { i ->
            sample(i, mapOf(10 to 5, 11 to if (i == 3) 4 else 5, 20 to i))
        }
        val result = engine.explainResiduals(samples, request())
        assertThat(result.evidence).hasSize(1)
        assertThat(result.evidence[0].residual).isEqualTo(1)
        assertThat(result.evidence[0].residualDirection).isEqualTo("POSITIVE")
    }

    // 5. Discriminator activation rate differs across groups
    @Test
    fun `discriminator detects activation rate difference between groups`() {
        val samples = (0 until 10).map { i ->
            val anchor = if (i < 5) 10 else 10
            val candidate = if (i < 5) 10 else 9
            val discriminatorCode = if (i >= 5) 1 else 0
            sample(i, mapOf(10 to anchor, 11 to candidate, 20 to discriminatorCode), matchId = "match-$i", playerId = "player-${i % 3}")
        }
        val result = engine.explainResiduals(samples, request())
        val disc = result.discriminators.firstOrNull { it.code == 20 }
        assertThat(disc).isNotNull
        assertThat(disc!!.positive.activationRate).isGreaterThan(disc.zero.activationRate ?: 0.0)
    }

    // 6. Candidate code itself is excluded from discriminators
    @Test
    fun `candidate code is excluded from discriminators`() {
        val samples = (0 until 6).map { i ->
            sample(i, mapOf(10 to 5, 11 to if (i < 3) 5 else 4, 20 to i))
        }
        val result = engine.explainResiduals(samples, request())
        assertThat(result.discriminators.none { it.aggregateIndex == 0 && it.code == 11 }).isTrue()
    }

    // 7. Small sample warning
    @Test
    fun `small sample warning when fewer than 5 diff observations`() {
        val samples = (0 until 6).map { i ->
            sample(i, mapOf(10 to 5, 11 to if (i == 0) 4 else 5, 20 to 1))
        }
        val result = engine.explainResiduals(samples, request())
        val disc = result.discriminators.firstOrNull { it.code == 20 }
        assertThat(disc).isNotNull
        assertThat(disc!!.warnings).contains("SMALL_SAMPLE")
    }

    // 8. Single player dominated warning
    @Test
    fun `single player dominated warning`() {
        val samples = (0 until 6).map { i ->
            sample(i, mapOf(10 to 5, 11 to if (i < 3) 4 else 5, 20 to 1), playerId = "player-A")
        }
        val result = engine.explainResiduals(samples, request())
        val disc = result.discriminators.firstOrNull { it.code == 20 }
        assertThat(disc).isNotNull
        assertThat(disc!!.warnings).contains("SINGLE_PLAYER_DOMINATED")
    }

    // 9. Single match dominated warning
    @Test
    fun `single match dominated warning`() {
        val samples = (0 until 6).map { i ->
            sample(i, mapOf(10 to 5, 11 to if (i < 3) 4 else 5, 20 to 1), matchId = "match-A", playerId = "player-$i")
        }
        val result = engine.explainResiduals(samples, request())
        val disc = result.discriminators.firstOrNull { it.code == 20 }
        assertThat(disc).isNotNull
        assertThat(disc!!.warnings).contains("SINGLE_MATCH_DOMINATED")
    }

    // 10. Insufficient evidence classification
    @Test
    fun `insufficient evidence classification when fewer than 3 diff observations`() {
        val samples = (0 until 8).map { i ->
            sample(i, mapOf(10 to 5, 11 to if (i == 0) 4 else 5, 20 to 1))
        }
        val result = engine.explainResiduals(samples, request())
        val disc = result.discriminators.firstOrNull { it.code == 20 }
        assertThat(disc).isNotNull
        assertThat(disc!!.technicalClassification).isEqualTo("INSUFFICIENT_EVIDENCE")
    }

    // 11. NON_DISCRIMINATING when no activation delta
    @Test
    fun `non discriminating when activation rates are uniform`() {
        val samples = (0 until 10).map { i ->
            sample(i, mapOf(10 to 5, 11 to if (i < 5) 4 else 5, 20 to 1), matchId = "match-$i", playerId = "player-${i % 4}")
        }
        val result = engine.explainResiduals(samples, request())
        val disc = result.discriminators.firstOrNull { it.code == 20 }
        assertThat(disc).isNotNull
        assertThat(disc!!.technicalClassification).isEqualTo("NON_DISCRIMINATING")
    }

    // 12. Score components are all finite
    @Test
    fun `score components are all finite`() {
        val samples = (0 until 10).map { i ->
            sample(i, mapOf(10 to i + 1, 11 to i, 20 to if (i % 2 == 0) 1 else 0), matchId = "match-$i", playerId = "player-${i % 3}")
        }
        val result = engine.explainResiduals(samples, request())
        result.discriminators.forEach { d ->
            assertThat(d.score.total).isFinite()
            assertThat(d.score.activationDeltaComponent).isFinite()
            assertThat(d.score.singlePlayerPenalty).isFinite()
        }
    }

    // 13. Penalties reduce total score
    @Test
    fun `penalties reduce total score for single player`() {
        val singlePlayer = (0 until 6).map { i ->
            sample(i, mapOf(10 to 5, 11 to if (i < 3) 4 else 5, 20 to if (i < 3) 1 else 0), playerId = "player-A")
        }
        val multiPlayer = (0 until 6).map { i ->
            sample(i, mapOf(10 to 5, 11 to if (i < 3) 4 else 5, 20 to if (i < 3) 1 else 0), playerId = "player-$i")
        }
        val r1 = engine.explainResiduals(singlePlayer, request())
        val r2 = engine.explainResiduals(multiPlayer, request())
        val s1 = r1.discriminators.first { it.code == 20 }.score.total
        val s2 = r2.discriminators.first { it.code == 20 }.score.total
        assertThat(s2).isGreaterThan(s1)
    }

    // 14. Discriminators are sorted by score descending
    @Test
    fun `discriminators are sorted by score descending`() {
        val samples = (0 until 10).map { i ->
            sample(i, mapOf(10 to 5, 11 to if (i < 5) 4 else 5, 20 to if (i < 5) 10 else 0, 21 to 1),
                matchId = "match-$i", playerId = "player-${i % 4}")
        }
        val result = engine.explainResiduals(samples, request())
        val scores = result.discriminators.map { it.score.total }
        assertThat(scores).isSortedAccordingTo(Comparator.reverseOrder())
    }

    // 15. Signatures only for non-zero residual
    @Test
    fun `signatures only generated for non-zero residual observations`() {
        val samples = (0 until 8).map { i ->
            sample(i, mapOf(10 to 5, 11 to if (i < 2) 4 else 5, 20 to i))
        }
        val result = engine.explainResiduals(samples, request())
        assertThat(result.signatures).hasSize(2)
        assertThat(result.signatures.all { it.residual != 0 }).isTrue()
    }

    // 16. Signatures mark top discriminators
    @Test
    fun `signatures highlight top discriminators`() {
        val samples = (0 until 10).map { i ->
            sample(i, mapOf(10 to 5, 11 to if (i < 5) 4 else 5, 20 to if (i < 5) 10 else 0, 21 to 1),
                matchId = "match-$i", playerId = "player-${i % 4}")
        }
        val result = engine.explainResiduals(samples, request())
        if (result.signatures.isNotEmpty()) {
            val topCodes = result.discriminators.take(20).map { it.code }.toSet()
            result.signatures.forEach { sig ->
                sig.relevantCodes.filter { it.isTopDiscriminator }.forEach {
                    assertThat(topCodes).contains(it.code)
                }
            }
        }
    }

    // 17. Aggregate separation - codes from different aggregates treated independently
    @Test
    fun `aggregate separation preserved in discriminators`() {
        val agg0 = (0 until 6).map { i ->
            sample(i, mapOf(10 to 5, 11 to if (i < 3) 4 else 5, 20 to i), aggregate = 0)
        }
        val agg1 = (0 until 6).map { i ->
            sample(i, mapOf(20 to if (i < 3) 1 else 0), aggregate = 1)
        }
        val result = engine.explainResiduals(agg0 + agg1, request())
        val agg1Disc = result.discriminators.filter { it.aggregateIndex == 1 }
        val agg0Disc = result.discriminators.filter { it.aggregateIndex == 0 }
        assertThat(agg0Disc.none { it.code == 11 }).isTrue()
    }

    // 18. Known metric anchor works for residual explainer
    @Test
    fun `known metric anchor works with residual explainer`() {
        val samples = (0 until 8).map { i ->
            sample(i, mapOf(11 to if (i < 4) i + 1 else i, 20 to i), passesCompleted = i + 1)
        }
        val req = AdvancedStatsDiscoveryEngine.ResidualExplainerRequest(
            AdvancedStatsDiscoveryEngine.AnchorRef("KNOWN_METRIC", null, null, "passesCompleted"),
            0, 11,
        )
        val result = engine.explainResiduals(samples, req)
        assertThat(result.anchor.metricName).isEqualTo("passesCompleted")
        assertThat(result.groups).isNotEmpty
    }

    // 19. Empty samples produce empty result
    @Test
    fun `empty samples produce empty result`() {
        val result = engine.explainResiduals(emptyList(), request())
        assertThat(result.groups.sumOf { it.count }).isEqualTo(0)
        assertThat(result.discriminators).isEmpty()
        assertThat(result.evidence).isEmpty()
        assertThat(result.signatures).isEmpty()
    }

    // 20. Dataset metadata is populated
    @Test
    fun `dataset metadata is populated correctly`() {
        val samples = (0 until 6).map { i ->
            sample(i, mapOf(10 to i + 1, 11 to i, 20 to 1), matchId = "match-$i", playerId = "player-${i % 3}")
        }
        val result = engine.explainResiduals(samples, request())
        assertThat(result.dataset.rawMatchesAnalyzed).isEqualTo(6)
        assertThat(result.dataset.distinctPlayers).isEqualTo(3)
    }

    // 21. Conditional probabilities are NaN-safe
    @Test
    fun `conditional probabilities are NaN-safe`() {
        val samples = (0 until 6).map { i ->
            sample(i, mapOf(10 to 5, 11 to 5, 20 to 0))
        }
        val result = engine.explainResiduals(samples, request())
        result.discriminators.forEach { d ->
            if (d.pActiveGivenPositive != null) assertThat(d.pActiveGivenPositive).isFinite()
            if (d.pActiveGivenZero != null) assertThat(d.pActiveGivenZero).isFinite()
            if (d.pActiveGivenNegative != null) assertThat(d.pActiveGivenNegative).isFinite()
        }
    }
}
