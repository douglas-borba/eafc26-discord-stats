package com.eafc26.discordstats.application.interpretation

import com.eafc26.discordstats.domain.interpretation.MatchInterpretation
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.FootballMatch

/**
 * Composes the deterministic decisions for a normalized match.
 *
 * This class owns orchestration only. Football rules remain in the focused
 * evaluators and calculators supplied to it.
 */
class MatchInterpreter(
    private val outcomeEvaluator: MatchOutcomeEvaluator = MatchOutcomeEvaluator(),
    private val eligibilityEvaluator: PlayerEligibilityEvaluator = PlayerEligibilityEvaluator(),
    private val metricsCalculator: TeamMetricsCalculator = TeamMetricsCalculator(),
    private val awardsEvaluator: MatchAwardsEvaluator = MatchAwardsEvaluator(),
) {

    fun interpret(
        match: FootballMatch,
        perspective: ClubId,
    ): MatchInterpretation {
        val clubPerformance = requireNotNull(
            match.participants.firstOrNull { it.club.id == perspective }
        ) { "Perspective club ${perspective.value} is not a match participant" }

        val eligibility = eligibilityEvaluator.evaluate(clubPerformance.players)
        val statisticallyEligiblePlayers = clubPerformance.players.filter {
            it.player.id in eligibility.eligiblePlayerIds
        }

        return MatchInterpretation(
            matchId = match.id,
            perspectiveClubId = perspective,
            result = outcomeEvaluator.evaluate(match, perspective),
            eligibility = eligibility,
            teamMetrics = metricsCalculator.calculate(statisticallyEligiblePlayers),
            awards = awardsEvaluator.evaluate(clubPerformance.players, eligibility),
        )
    }
}
