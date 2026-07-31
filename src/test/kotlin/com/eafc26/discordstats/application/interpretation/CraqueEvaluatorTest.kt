package com.eafc26.discordstats.application.interpretation

import com.eafc26.discordstats.domain.interpretation.AwardDecisionReason
import com.eafc26.discordstats.domain.interpretation.AwardType
import com.eafc26.discordstats.domain.interpretation.DecisionEvidence
import com.eafc26.discordstats.domain.match.PlayerId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CraqueEvaluatorTest {

    private val evaluator = CraqueEvaluator()

    @Test
    fun `EA MVP has priority over a higher rating`() {
        val players = listOf(
            awardPlayer("ea-mvp", rating = "8.0", eaMvp = true),
            awardPlayer("higher", rating = "9.0"),
        )

        val decision = evaluator.evaluate(players, eligibility(players))

        assertThat(decision.winnerId).isEqualTo(PlayerId("ea-mvp"))
        assertThat(decision.reason).isEqualTo(AwardDecisionReason.EA_MAN_OF_THE_MATCH)
        assertThat(decision.rule).isEqualTo(CraqueEvaluator.RULE)
    }

    @Test
    fun `rating goals and assists are applied in that order`() {
        val players = listOf(
            awardPlayer("assists", rating = "8.0", goals = 1, assists = 3),
            awardPlayer("goals", rating = "8.0", goals = 2, assists = 0),
            awardPlayer("lower", rating = "7.9", goals = 5, assists = 5),
        )

        val decision = evaluator.evaluate(players, eligibility(players))

        assertThat(decision.winnerId).isEqualTo(PlayerId("goals"))
        assertThat(decision.reason).isEqualTo(AwardDecisionReason.HIGHEST_RATING)
    }

    @Test
    fun `statistically ineligible and externally excluded players are audited`() {
        val ineligible = awardPlayer("ineligible", rating = "10")
        val excluded = awardPlayer("bagre", rating = "9")
        val winner = awardPlayer("winner", rating = "8")
        val players = listOf(ineligible, excluded, winner)

        val decision = evaluator.evaluate(
            players,
            eligibility(players, setOf(ineligible.player.id)),
            mapOf(excluded.player.id to AwardType.BAGRE),
        )

        assertThat(decision.winnerId).isEqualTo(winner.player.id)
        assertThat(decision.evidence.filterIsInstance<DecisionEvidence.AwardCandidate>())
            .anySatisfy {
                assertThat(it.playerId).isEqualTo(ineligible.player.id)
                assertThat(it.statisticallyEligible).isFalse()
            }
            .anySatisfy {
                assertThat(it.playerId).isEqualTo(excluded.player.id)
                assertThat(it.excludedByAward).isEqualTo(AwardType.BAGRE)
            }
    }

    @Test
    fun `no rated eligible candidate produces an audited omission`() {
        val players = listOf(awardPlayer("unrated", rating = null))

        val decision = evaluator.evaluate(players, eligibility(players))

        assertThat(decision.awarded).isFalse()
        assertThat(decision.reason).isEqualTo(AwardDecisionReason.NO_ELIGIBLE_CANDIDATE)
        assertThat(decision.evidence).isNotEmpty()
    }
}
