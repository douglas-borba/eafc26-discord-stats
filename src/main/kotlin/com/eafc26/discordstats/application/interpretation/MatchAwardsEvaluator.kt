package com.eafc26.discordstats.application.interpretation

import com.eafc26.discordstats.domain.interpretation.AwardType
import com.eafc26.discordstats.domain.interpretation.EligibilityInterpretation
import com.eafc26.discordstats.domain.interpretation.MatchAwards
import com.eafc26.discordstats.domain.match.PlayerMatchPerformance

class MatchAwardsEvaluator(
    private val craqueEvaluator: CraqueEvaluator = CraqueEvaluator(),
    private val bagreEvaluator: BagreEvaluator = BagreEvaluator(),
    private val xerifeEvaluator: XerifeEvaluator = XerifeEvaluator(),
) {

    fun evaluate(
        players: Collection<PlayerMatchPerformance>,
        eligibility: EligibilityInterpretation,
    ): MatchAwards {
        val bagre = bagreEvaluator.evaluate(players, eligibility)
        val positiveAwardExclusions = bagre.winnerId
            ?.let { mapOf(it to AwardType.BAGRE) }
            .orEmpty()

        return MatchAwards(
            craque = craqueEvaluator.evaluate(players, eligibility, positiveAwardExclusions),
            bagre = bagre,
            xerife = xerifeEvaluator.evaluate(players, eligibility, positiveAwardExclusions),
        )
    }
}
