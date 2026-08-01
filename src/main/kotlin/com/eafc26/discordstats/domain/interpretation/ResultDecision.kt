package com.eafc26.discordstats.domain.interpretation

import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.Score

data class ResultDecision(
    val ourClub: ClubId,
    val opponentClub: ClubId,
    val ourScore: Score,
    val opponentScore: Score,
    val outcome: MatchOutcome,
    val decidedBy: ResultDecisionSource,
    val rule: RuleReference,
    val evidence: List<DecisionEvidence>,
)

enum class ResultDecisionSource {
    SCOREBOARD,
    REPORTED_RESULT_FALLBACK,
}
