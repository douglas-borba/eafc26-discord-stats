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

class CraqueEvaluator {

    fun evaluate(
        players: Collection<PlayerMatchPerformance>,
        eligibility: EligibilityInterpretation,
        excludedByAward: Map<PlayerId, AwardType> = emptyMap(),
    ): AwardDecision {
        val pool = AwardCandidatePool.outfield(players, eligibility, excludedByAward)
        val recognitionEvidence = pool.candidates.map {
            DecisionEvidence.EaRecognition(it.player.id, it.eaRecognition.manOfTheMatch)
        }
        val ratingEvidence = pool.candidates.map {
            DecisionEvidence.Rating(it.player.id, it.rating?.value, minimumRequired = null)
        }
        val attackEvidence = pool.candidates.map {
            DecisionEvidence.AttackingContribution(
                it.player.id,
                it.attacking.goals,
                it.attacking.assists,
                it.attacking.shots,
            )
        }
        val evidence = pool.evidence + recognitionEvidence + ratingEvidence + attackEvidence

        val eaMvp = pool.candidates.firstOrNull { it.eaRecognition.manOfTheMatch == true }
        if (eaMvp != null) {
            return AwardDecision(
                AwardType.CRAQUE,
                eaMvp.player.id,
                AwardDecisionReason.EA_MAN_OF_THE_MATCH,
                RULE,
                evidence,
            )
        }

        val winner = pool.candidates
            .filter { it.rating != null }
            .maxWithOrNull(
                compareBy<PlayerMatchPerformance> { it.rating!!.value }
                    .thenBy { it.attacking.goals ?: 0 }
                    .thenBy { it.attacking.assists ?: 0 }
            )

        return AwardDecision(
            AwardType.CRAQUE,
            winner?.player?.id,
            if (winner == null) {
                AwardDecisionReason.NO_ELIGIBLE_CANDIDATE
            } else {
                AwardDecisionReason.HIGHEST_RATING
            },
            RULE,
            evidence,
        )
    }

    companion object {
        val RULE = RuleReference(RuleId("award.craque"), version = 1)
    }
}
