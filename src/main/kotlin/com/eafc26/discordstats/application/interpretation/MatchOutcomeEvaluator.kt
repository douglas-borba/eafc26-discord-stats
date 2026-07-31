package com.eafc26.discordstats.application.interpretation

import com.eafc26.discordstats.domain.interpretation.DecisionEvidence
import com.eafc26.discordstats.domain.interpretation.MatchOutcome
import com.eafc26.discordstats.domain.interpretation.ResultDecision
import com.eafc26.discordstats.domain.interpretation.ResultDecisionSource
import com.eafc26.discordstats.domain.interpretation.RuleId
import com.eafc26.discordstats.domain.interpretation.RuleReference
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.FootballMatch
import com.eafc26.discordstats.domain.match.ReportedMatchResult
import com.eafc26.discordstats.domain.match.Score

/**
 * Resolves the result from one club's perspective.
 *
 * Valid scores are authoritative. The source-reported result is used only when
 * either score is absent, matching the current MatchOutcomeResolver behavior.
 */
class MatchOutcomeEvaluator {

    fun evaluate(match: FootballMatch, perspective: ClubId): ResultDecision {
        val ourPerformance = requireNotNull(
            match.participants.firstOrNull { it.club.id == perspective }
        ) { "Perspective club ${perspective.value} is not a match participant" }
        val opponentPerformance = requireNotNull(
            match.participants.firstOrNull { it.club.id != perspective }
        ) { "Match has no opponent for perspective ${perspective.value}" }

        return resolve(
            ourClub = perspective,
            opponentClub = opponentPerformance.club.id,
            ourScore = ourPerformance.score,
            opponentScore = opponentPerformance.score,
            reportedResult = ourPerformance.reportedResult,
        )
    }

    /**
     * Score-optional rule entry point retained for exact characterization of
     * the current reported-result fallback. Normalized FootballMatch currently
     * supplies non-null scores.
     */
    fun resolve(
        ourClub: ClubId,
        opponentClub: ClubId,
        ourScore: Score?,
        opponentScore: Score?,
        reportedResult: ReportedMatchResult?,
    ): ResultDecision {
        val evidence = listOf(
            DecisionEvidence.Scoreboard(ourScore?.goals, opponentScore?.goals),
            DecisionEvidence.ReportedResult(reportedResult),
        )

        if (ourScore != null && opponentScore != null) {
            val outcome = when {
                ourScore.goals > opponentScore.goals -> MatchOutcome.WIN
                ourScore.goals < opponentScore.goals -> MatchOutcome.LOSS
                else -> MatchOutcome.DRAW
            }
            return ResultDecision(
                ourClub = ourClub,
                opponentClub = opponentClub,
                ourScore = ourScore,
                opponentScore = opponentScore,
                outcome = outcome,
                decidedBy = ResultDecisionSource.SCOREBOARD,
                rule = RULE,
                evidence = evidence,
            )
        }

        val outcome = when (reportedResult) {
            ReportedMatchResult.WIN -> MatchOutcome.WIN
            ReportedMatchResult.DRAW -> MatchOutcome.DRAW
            ReportedMatchResult.LOSS, null -> MatchOutcome.LOSS
        }
        return ResultDecision(
            ourClub = ourClub,
            opponentClub = opponentClub,
            ourScore = ourScore ?: Score(0),
            opponentScore = opponentScore ?: Score(0),
            outcome = outcome,
            decidedBy = ResultDecisionSource.REPORTED_RESULT_FALLBACK,
            rule = RULE,
            evidence = evidence,
        )
    }

    companion object {
        val RULE = RuleReference(RuleId("match.outcome"), version = 1)
    }
}
