package com.eafc26.discordstats.application.interpretation

import com.eafc26.discordstats.domain.interpretation.AwardDecision
import com.eafc26.discordstats.domain.interpretation.AwardDecisionReason
import com.eafc26.discordstats.domain.interpretation.AwardType
import com.eafc26.discordstats.domain.interpretation.DecisionEvidence
import com.eafc26.discordstats.domain.interpretation.EligibilityInterpretation
import com.eafc26.discordstats.domain.interpretation.RuleId
import com.eafc26.discordstats.domain.interpretation.RuleReference
import com.eafc26.discordstats.domain.match.PlayerMatchPerformance
import java.math.BigDecimal

class BagreEvaluator {

    fun evaluate(
        players: Collection<PlayerMatchPerformance>,
        eligibility: EligibilityInterpretation,
    ): AwardDecision {
        val pool = AwardCandidatePool.outfield(players, eligibility)
        val evidence = pool.evidence + pool.candidates.flatMap {
            listOf(
                DecisionEvidence.Rating(it.player.id, it.rating?.value, MINIMUM_RATING),
                DecisionEvidence.PassingPerformance(
                    it.player.id,
                    it.passing.completed,
                    it.passing.attempted,
                ),
                DecisionEvidence.DefensivePerformance(
                    it.player.id,
                    it.defending.tacklesCompleted,
                    it.defending.tacklesAttempted,
                    defensiveImpactScore = null,
                ),
                DecisionEvidence.AttackingContribution(
                    it.player.id,
                    it.attacking.goals,
                    it.attacking.assists,
                    it.attacking.shots,
                ),
            )
        }
        val winner = pool.candidates
            .filter { it.rating != null && it.rating.value >= MINIMUM_RATING }
            .minWithOrNull(
                compareBy<PlayerMatchPerformance> { it.rating!!.value }
                    .thenBy { passingAccuracyOrDefault(it) }
                    .thenBy { tackleAccuracyOrDefault(it) }
                    .thenByDescending { shotsWithoutGoal(it) }
                    .thenByDescending { missedPasses(it) }
            )

        return AwardDecision(
            AwardType.BAGRE,
            winner?.player?.id,
            if (winner == null) {
                AwardDecisionReason.NO_ELIGIBLE_CANDIDATE
            } else {
                AwardDecisionReason.LOWEST_ELIGIBLE_RATING
            },
            RULE,
            evidence,
        )
    }

    private fun shotsWithoutGoal(player: PlayerMatchPerformance): Int =
        if ((player.attacking.goals ?: 0) > 0) 0 else player.attacking.shots ?: 0

    private fun passingAccuracyOrDefault(player: PlayerMatchPerformance): BigDecimal {
        val attempted = player.passing.attempted?.takeIf { it > 0 } ?: return BigDecimal.ONE
        return (player.passing.completed ?: 0).toBigDecimal()
            .divide(attempted.toBigDecimal(), 6, java.math.RoundingMode.HALF_UP)
    }

    private fun tackleAccuracyOrDefault(player: PlayerMatchPerformance): BigDecimal {
        val attempted = player.defending.tacklesAttempted?.takeIf { it > 0 } ?: return BigDecimal.ONE
        return (player.defending.tacklesCompleted ?: 0).toBigDecimal()
            .divide(attempted.toBigDecimal(), 6, java.math.RoundingMode.HALF_UP)
    }

    private fun missedPasses(player: PlayerMatchPerformance): Int {
        val attempted = player.passing.attempted ?: 0
        return maxOf(attempted - (player.passing.completed ?: 0), 0)
    }

    companion object {
        val MINIMUM_RATING: BigDecimal = BigDecimal("5.0")
        val RULE = RuleReference(RuleId("award.bagre"), version = 1)
    }
}
