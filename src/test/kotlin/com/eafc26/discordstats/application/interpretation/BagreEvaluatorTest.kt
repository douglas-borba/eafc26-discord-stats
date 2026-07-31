package com.eafc26.discordstats.application.interpretation

import com.eafc26.discordstats.domain.interpretation.AwardDecisionReason
import com.eafc26.discordstats.domain.interpretation.DecisionEvidence
import com.eafc26.discordstats.domain.match.PlayerId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BagreEvaluatorTest {

    private val evaluator = BagreEvaluator()

    @Test
    fun `lowest rating at or above five wins`() {
        val players = listOf(
            awardPlayer("too-low", rating = "4.9"),
            awardPlayer("threshold", rating = "5.0"),
            awardPlayer("higher", rating = "7.0"),
        )

        val decision = evaluator.evaluate(players, eligibility(players))

        assertThat(decision.winnerId).isEqualTo(PlayerId("threshold"))
        assertThat(decision.reason).isEqualTo(AwardDecisionReason.LOWEST_ELIGIBLE_RATING)
        assertThat(decision.rule).isEqualTo(BagreEvaluator.RULE)
    }

    @Test
    fun `ties use passing tackles missed chances and missed passes in legacy order`() {
        val betterPassing = awardPlayer("better-pass", passesCompleted = 9, passesAttempted = 10)
        val worsePassing = awardPlayer("worse-pass", passesCompleted = 6, passesAttempted = 10)

        val decision = evaluator.evaluate(
            listOf(betterPassing, worsePassing),
            eligibility(listOf(betterPassing, worsePassing)),
        )

        assertThat(decision.winnerId).isEqualTo(worsePassing.player.id)
    }

    @Test
    fun `missing completed passes are treated as zero when attempts are known`() {
        val missingCompleted = awardPlayer(
            "missing",
            passesCompleted = null,
            passesAttempted = 10,
        )
        val complete = awardPlayer("complete", passesCompleted = 1, passesAttempted = 10)
        val players = listOf(complete, missingCompleted)

        val decision = evaluator.evaluate(players, eligibility(players))

        assertThat(decision.winnerId).isEqualTo(missingCompleted.player.id)
    }

    @Test
    fun `ratings below threshold and absent ratings produce an audited omission`() {
        val players = listOf(
            awardPlayer("low", rating = "4.9"),
            awardPlayer("absent", rating = null),
        )

        val decision = evaluator.evaluate(players, eligibility(players))

        assertThat(decision.awarded).isFalse()
        assertThat(decision.reason).isEqualTo(AwardDecisionReason.NO_ELIGIBLE_CANDIDATE)
        assertThat(decision.evidence.filterIsInstance<DecisionEvidence.Rating>())
            .hasSize(2)
    }
}
