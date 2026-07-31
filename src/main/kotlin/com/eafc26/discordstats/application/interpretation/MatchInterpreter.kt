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
    private val featuresEvaluator: MatchFeaturesEvaluator = MatchFeaturesEvaluator(),
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

        val result = outcomeEvaluator.evaluate(match, perspective)
        val teamMetrics = metricsCalculator.calculate(statisticallyEligiblePlayers)
        val awards = awardsEvaluator.evaluate(clubPerformance.players, eligibility)

        return MatchInterpretation(
            footballMatch = match,
            perspectiveClubId = perspective,
            result = result,
            eligibility = eligibility,
            teamMetrics = teamMetrics,
            awards = awards,
            features = featuresEvaluator.evaluate(
                players = clubPerformance.players,
                eligibility = eligibility,
                result = result,
                teamMetrics = teamMetrics,
                awards = awards,
            ),
        )
    }
}
