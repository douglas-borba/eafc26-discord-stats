package com.eafc26.discordstats.application.interpretation

import com.eafc26.discordstats.domain.interpretation.AwardDecision
import com.eafc26.discordstats.domain.interpretation.AwardDecisionReason
import com.eafc26.discordstats.domain.interpretation.AwardType
import com.eafc26.discordstats.domain.interpretation.DecisionEvidence
import com.eafc26.discordstats.domain.interpretation.EligibilityInterpretation
import com.eafc26.discordstats.domain.interpretation.RuleId
import com.eafc26.discordstats.domain.interpretation.RuleReference
import com.eafc26.discordstats.domain.match.PlayerId
import com.eafc26.discordstats.domain.match.PlayerMatchPerformance
import java.math.BigDecimal
import java.math.RoundingMode

class XerifeEvaluator {

    fun evaluate(
        players: Collection<PlayerMatchPerformance>,
        eligibility: EligibilityInterpretation,
        excludedByAward: Map<PlayerId, AwardType> = emptyMap(),
    ): AwardDecision {
        val pool = AwardCandidatePool.outfield(players, eligibility, excludedByAward)
        val candidates = pool.candidates.mapNotNull { player ->
            val attempted = player.defending.tacklesAttempted ?: return@mapNotNull null
            val completed = player.defending.tacklesCompleted ?: 0
            if (attempted <= 0 || completed < MINIMUM_TACKLES) return@mapNotNull null
            if (completed.toLong() * 100 < attempted.toLong() * MINIMUM_ACCURACY_PERCENT) {
                return@mapNotNull null
            }
            if ((player.discipline.redCards ?: 0) > 0) return@mapNotNull null

            Candidate(
                player = player,
                impact = completed.toBigDecimal()
                    .multiply(completed.toBigDecimal())
                    .divide(attempted.toBigDecimal(), SCORE_SCALE, RoundingMode.HALF_UP),
                accuracyPercent = completed * 100 / attempted,
                completed = completed,
            )
        }
        val winner = candidates.maxWithOrNull(
            compareBy<Candidate> { it.impact }
                .thenBy { it.accuracyPercent }
                .thenBy { it.completed }
        )
        val candidateById = candidates.associateBy { it.player.player.id }
        val evidence = pool.evidence + pool.candidates.flatMap {
            listOf(
                DecisionEvidence.DefensivePerformance(
                    it.player.id,
                    it.defending.tacklesCompleted,
                    it.defending.tacklesAttempted,
                    candidateById[it.player.id]?.impact,
                ),
                DecisionEvidence.Discipline(it.player.id, it.discipline.redCards),
            )
        }

        return AwardDecision(
            AwardType.XERIFE,
            winner?.player?.player?.id,
            if (winner == null) {
                AwardDecisionReason.NO_ELIGIBLE_CANDIDATE
            } else {
                AwardDecisionReason.HIGHEST_DEFENSIVE_IMPACT
            },
            RULE,
            evidence,
        )
    }

    private data class Candidate(
        val player: PlayerMatchPerformance,
        val impact: BigDecimal,
        val accuracyPercent: Int,
        val completed: Int,
    )

    companion object {
        const val MINIMUM_TACKLES = 4
        const val MINIMUM_ACCURACY_PERCENT = 70
        private const val SCORE_SCALE = 6
        val RULE = RuleReference(RuleId("award.xerife"), version = 1)
    }
}
