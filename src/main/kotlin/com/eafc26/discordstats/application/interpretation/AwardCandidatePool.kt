package com.eafc26.discordstats.application.interpretation

import com.eafc26.discordstats.domain.interpretation.AwardType
import com.eafc26.discordstats.domain.interpretation.DecisionEvidence
import com.eafc26.discordstats.domain.interpretation.EligibilityInterpretation
import com.eafc26.discordstats.domain.match.PlayerId
import com.eafc26.discordstats.domain.match.PlayerMatchPerformance
import com.eafc26.discordstats.domain.match.PlayerRole

internal object AwardCandidatePool {

    data class Result(
        val candidates: List<PlayerMatchPerformance>,
        val evidence: List<DecisionEvidence.AwardCandidate>,
    )

    fun outfield(
        players: Collection<PlayerMatchPerformance>,
        eligibility: EligibilityInterpretation,
        exclusions: Map<PlayerId, AwardType> = emptyMap(),
    ): Result {
        val eligibleIds = eligibility.eligiblePlayerIds
        val evidence = players.map { player ->
            DecisionEvidence.AwardCandidate(
                playerId = player.player.id,
                statisticallyEligible = player.player.id in eligibleIds,
                outfield = player.role is PlayerRole.Outfield,
                excludedByAward = exclusions[player.player.id],
            )
        }
        val candidates = players.filter { player ->
            player.player.id in eligibleIds &&
                player.role is PlayerRole.Outfield &&
                player.player.id !in exclusions
        }
        return Result(candidates, evidence)
    }
}
