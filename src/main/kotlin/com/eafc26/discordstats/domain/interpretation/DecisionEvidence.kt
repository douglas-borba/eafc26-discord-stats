package com.eafc26.discordstats.domain.interpretation

import com.eafc26.discordstats.domain.match.PlayerId
import com.eafc26.discordstats.domain.match.ReportedMatchResult

sealed interface DecisionEvidence {
    data class Scoreboard(
        val ourScore: Int?,
        val opponentScore: Int?,
    ) : DecisionEvidence

    data class ReportedResult(
        val value: ReportedMatchResult?,
    ) : DecisionEvidence

    data class PlayingTime(
        val playerId: PlayerId,
        val playerSeconds: Long?,
        val maximumTeamSeconds: Long?,
        val requiredPercent: Int,
        val passed: Boolean,
    ) : DecisionEvidence
}
