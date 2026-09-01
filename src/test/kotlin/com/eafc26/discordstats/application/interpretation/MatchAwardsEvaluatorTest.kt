package com.eafc26.discordstats.application.interpretation

import com.eafc26.discordstats.domain.interpretation.AwardType
import com.eafc26.discordstats.domain.interpretation.DecisionEvidence
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MatchAwardsEvaluatorTest {

    @Test
    fun `Bagre is selected first and excluded from positive awards`() {
        val bagreAndBestDefender = awardPlayer(
            "bagre",
            rating = "5.0",
            passesCompleted = 1,
            passesAttempted = 15,
            tacklesCompleted = 0,
            tacklesAttempted = 8,
        )
        val positiveWinner = awardPlayer(
            "positive",
            rating = "8.0",
            tacklesCompleted = 7,
            tacklesAttempted = 10,
        )
        val players = listOf(bagreAndBestDefender, positiveWinner)

        val awards = MatchAwardsEvaluator().evaluate(players, eligibility(players))

        assertThat(awards.bagre.winnerId).isEqualTo(bagreAndBestDefender.player.id)
        assertThat(awards.craque.winnerId).isEqualTo(positiveWinner.player.id)
        assertThat(awards.xerife.winnerId).isEqualTo(positiveWinner.player.id)
        assertThat(awards.craque.evidence.filterIsInstance<DecisionEvidence.AwardCandidate>())
            .anySatisfy {
                assertThat(it.playerId).isEqualTo(bagreAndBestDefender.player.id)
                assertThat(it.excludedByAward).isEqualTo(AwardType.BAGRE)
            }
    }
}
