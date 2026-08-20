package com.eafc26.discordstats.application.interpretation

import com.eafc26.discordstats.domain.interpretation.AwardDecisionReason
import com.eafc26.discordstats.domain.interpretation.DecisionEvidence
import com.eafc26.discordstats.domain.match.PlayerId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class XerifeEvaluatorTest {

    private val evaluator = XerifeEvaluator()

    @Test
    fun `requires four defensive actions applicable tackle accuracy and no red card`() {
        val belowVolume = awardPlayer("volume", tacklesCompleted = 3, tacklesAttempted = 3)
        val belowAccuracy = awardPlayer("accuracy", tacklesCompleted = 4, tacklesAttempted = 6)
        val redCard = awardPlayer("red", tacklesCompleted = 8, tacklesAttempted = 8, redCards = 1)
        val valid = awardPlayer("valid", tacklesCompleted = 7, tacklesAttempted = 10)
        val players = listOf(belowVolume, belowAccuracy, redCard, valid)

        val decision = evaluator.evaluate(players, eligibility(players))

        assertThat(decision.winnerId).isEqualTo(PlayerId("valid"))
        assertThat(decision.reason).isEqualTo(AwardDecisionReason.HIGHEST_DEFENSIVE_IMPACT)
        assertThat(decision.rule).isEqualTo(XerifeEvaluator.RULE)
    }

    @Test
    fun `interceptions count toward defensive impact and can elect a Xerife with few tackles`() {
        val tackler = awardPlayer("tackler", tacklesCompleted = 4, tacklesAttempted = 4, interceptions = 0)
        val reader = awardPlayer("reader", tacklesCompleted = 2, tacklesAttempted = 2, interceptions = 5)

        val decision = evaluator.evaluate(listOf(tackler, reader), eligibility(listOf(tackler, reader)))

        assertThat(decision.winnerId).isEqualTo(reader.player.id)
        assertThat(decision.metrics).isInstanceOf(com.eafc26.discordstats.domain.interpretation.AwardMetrics.Xerife::class.java)
        val metrics = decision.metrics as com.eafc26.discordstats.domain.interpretation.AwardMetrics.Xerife
        assertThat(metrics.interceptions).isEqualTo(5)
        assertThat(metrics.tacklesCompleted).isEqualTo(2)
        assertThat(metrics.defensiveImpactScore).isEqualByComparingTo("7")
    }

    @Test
    fun `highest defensive impact wins`() {
        val highImpact = awardPlayer("high", tacklesCompleted = 8, tacklesAttempted = 10)
        val lowImpact = awardPlayer("low", tacklesCompleted = 5, tacklesAttempted = 7)
        val players = listOf(highImpact, lowImpact)

        val decision = evaluator.evaluate(players, eligibility(players))

        assertThat(decision.winnerId).isEqualTo(highImpact.player.id)
        assertThat(decision.evidence.filterIsInstance<DecisionEvidence.DefensivePerformance>())
            .anySatisfy {
                assertThat(it.playerId).isEqualTo(highImpact.player.id)
                assertThat(it.defensiveImpactScore).isNotNull()
            }
    }

    @Test
    fun `no qualifying defender produces evidence for every considered player`() {
        val players = listOf(
            awardPlayer("low", tacklesCompleted = 3, tacklesAttempted = 3),
            awardPlayer("none", tacklesCompleted = 0, tacklesAttempted = 0),
        )

        val decision = evaluator.evaluate(players, eligibility(players))

        assertThat(decision.awarded).isFalse()
        assertThat(decision.evidence.filterIsInstance<DecisionEvidence.DefensivePerformance>())
            .hasSize(2)
        assertThat(decision.evidence.filterIsInstance<DecisionEvidence.Discipline>())
            .hasSize(2)
    }
}
