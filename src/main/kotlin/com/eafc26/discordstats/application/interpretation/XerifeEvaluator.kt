package com.eafc26.discordstats.application.interpretation

import com.eafc26.discordstats.domain.interpretation.AwardDecision
import com.eafc26.discordstats.domain.interpretation.AwardDecisionReason
import com.eafc26.discordstats.domain.interpretation.AwardType
import com.eafc26.discordstats.domain.interpretation.AwardMetrics
import com.eafc26.discordstats.domain.interpretation.DecisionEvidence
import com.eafc26.discordstats.domain.interpretation.EligibilityInterpretation
import com.eafc26.discordstats.domain.interpretation.RuleId
import com.eafc26.discordstats.domain.interpretation.RuleReference
import com.eafc26.discordstats.domain.match.PlayerId
import com.eafc26.discordstats.domain.match.PlayerMatchPerformance
import java.math.BigDecimal

class XerifeEvaluator {

    fun evaluate(
        players: Collection<PlayerMatchPerformance>,
        eligibility: EligibilityInterpretation,
        excludedByAward: Map<PlayerId, AwardType> = emptyMap(),
    ): AwardDecision {
        val pool = AwardCandidatePool.outfield(players, eligibility, excludedByAward)
        val candidates = pool.candidates.mapNotNull { player ->
            val attempted = player.defending.tacklesAttempted ?: 0
            val completed = player.defending.tacklesCompleted ?: 0
            val interceptions = player.defending.interceptions
            val defensiveActions = completed + interceptions
            if (defensiveActions < MINIMUM_DEFENSIVE_ACTIONS) return@mapNotNull null
            if (attempted > 0 && completed.toLong() * 100 < attempted.toLong() * MINIMUM_ACCURACY_PERCENT) {
                return@mapNotNull null
            }
            if ((player.discipline.redCards ?: 0) > 0) return@mapNotNull null

            Candidate(
                player = player,
                impact = defensiveActions.toBigDecimal(),
                accuracyPercent = if (attempted > 0) completed * 100 / attempted else null,
                completed = completed,
                attempted = attempted,
                interceptions = interceptions,
            )
        }
        val winner = candidates.maxWithOrNull(
            compareBy<Candidate> { it.impact }
                .thenBy { it.accuracyPercent ?: -1 }
                .thenBy { it.interceptions }
                .thenBy { it.completed }
                .thenBy { it.player.player.id.value }
        )
        val candidateById = candidates.associateBy { it.player.player.id }
        val evidence = pool.evidence + pool.candidates.flatMap {
            listOf(
                DecisionEvidence.DefensivePerformance(
                    it.player.id,
                    it.defending.tacklesCompleted,
                    it.defending.tacklesAttempted,
                    candidateById[it.player.id]?.impact,
                    it.defending.interceptions,
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
            winner?.let {
                AwardMetrics.Xerife(
                    tacklesCompleted = it.completed,
                    tacklesAttempted = it.attempted,
                    accuracyPercent = it.accuracyPercent ?: 0,
                    defensiveImpactScore = it.impact,
                    interceptions = it.interceptions,
                )
            },
        )
    }

    private data class Candidate(
        val player: PlayerMatchPerformance,
        val impact: BigDecimal,
        val accuracyPercent: Int?,
        val completed: Int,
        val attempted: Int,
        val interceptions: Int,
    )

    companion object {
        /** Combined successful defensive actions: tackles made + interceptions. */
        const val MINIMUM_DEFENSIVE_ACTIONS = 4
        const val MINIMUM_ACCURACY_PERCENT = 70
        val RULE = RuleReference(RuleId("award.xerife"), version = 2)
    }
}
