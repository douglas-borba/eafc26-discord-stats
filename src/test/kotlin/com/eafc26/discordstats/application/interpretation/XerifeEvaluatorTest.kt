package com.eafc26.discordstats.application.interpretation

import com.eafc26.discordstats.domain.interpretation.AwardDecisionReason
import com.eafc26.discordstats.domain.interpretation.DecisionEvidence
import com.eafc26.discordstats.domain.match.PlayerId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class XerifeEvaluatorTest {

    private val evaluator = XerifeEvaluator()

    @Test
    fun `requires four tackles seventy percent accuracy and no red card`() {
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
