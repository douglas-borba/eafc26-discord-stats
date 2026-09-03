package com.eafc26.discordstats.application.interpretation

import com.eafc26.discordstats.domain.interpretation.AwardDecision
import com.eafc26.discordstats.domain.interpretation.AwardDecisionReason
import com.eafc26.discordstats.domain.interpretation.AwardMetrics
import com.eafc26.discordstats.domain.interpretation.BagreCriticism
import com.eafc26.discordstats.domain.interpretation.DecisionEvidence
import com.eafc26.discordstats.domain.interpretation.NegativeRecognition
import com.eafc26.discordstats.domain.match.PlayerId
import com.eafc26.discordstats.domain.match.PlayerMatchPerformance
import com.eafc26.discordstats.domain.match.PlayerRole
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class BagreEvaluatorTest {

    private val evaluator = BagreEvaluator()

    @Test
    fun `excellent ratings produce no negative recognition`() {
        val ten = awardPlayer("ten", rating = "10.0")
        val nineFour = awardPlayer("nine-four", rating = "9.4")

        assertNoRecognition(evaluate(ten, nineFour))
    }

    @Test
    fun `lowest eligible rating is selected before any supporting statistic`() {
        val automaticBagre = awardPlayer(
            "lowest",
            rating = "6.8",
            passesCompleted = 20,
            passesAttempted = 20,
            tacklesCompleted = 9,
            tacklesAttempted = 9,
        )
        val worseStatistics = awardPlayer(
            "higher",
            rating = "7.4",
            passesCompleted = 0,
            passesAttempted = 20,
            tacklesCompleted = 0,
            tacklesAttempted = 9,
        )

        assertRecognition(evaluate(automaticBagre, worseStatistics), automaticBagre.player.id, NegativeRecognition.BAGRE)
    }

    @Test
    fun `ratings below seven always produce Bagre including exact lower boundary`() {
        listOf("6.99", "6.8").forEach { rating ->
            val player = awardPlayer("rating-$rating", rating = rating)
            assertRecognition(evaluate(player), player.player.id, NegativeRecognition.BAGRE)
        }
    }

    @Test
    fun `rating seven through seven point four nine automatically produces low performance`() {
        listOf("7.00", "7.3", "7.49").forEach { rating ->
            val player = awardPlayer("rating-$rating", rating = rating)
            assertRecognition(evaluate(player), player.player.id, NegativeRecognition.LOW_PERFORMANCE)
        }
    }

    @Test
    fun `borderline boundaries with insufficient evidence produce no recognition`() {
        val sevenFive = awardPlayer("seven-five", rating = "7.50")
        val sevenNineNine = awardPlayer("seven-nine-nine", rating = "7.99")
        val eight = awardPlayer("eight", rating = "8.00")

        assertNoRecognition(evaluate(sevenFive))
        assertNoRecognition(evaluate(sevenNineNine))
        assertNoRecognition(evaluate(eight))
    }

    @Test
    fun `automatic low performance does not need a negative supporting statistic`() {
        val high = awardPlayer("high", rating = "9.0")
        val mid = awardPlayer("mid", rating = "8.5")
        val low = awardPlayer("low", rating = "7.3", passesCompleted = 20, passesAttempted = 20)

        assertRecognition(evaluate(high, mid, low), low.player.id, NegativeRecognition.LOW_PERFORMANCE)
    }

    @Test
    fun `strong peer rating deficit qualifies a borderline player`() {
        val candidate = awardPlayer("candidate", rating = "7.7")
        val decision = evaluate(
            candidate,
            awardPlayer("nine-two", rating = "9.2"),
            awardPlayer("eight-nine", rating = "8.9"),
            awardPlayer("eight-six", rating = "8.6"),
        )

        assertRecognition(decision, candidate.player.id, NegativeRecognition.LOW_PERFORMANCE)
        val metrics = decision.metrics as AwardMetrics.Bagre
        assertThat(metrics.criticism).isEqualTo(BagreCriticism.RATING)
        assertThat(metrics.peerAverageRating).isEqualByComparingTo("8.90")
        assertThat(metrics.ratingDeficit).isEqualByComparingTo("1.20")
    }

    @Test
    fun `small peer rating deficit does not qualify a borderline player`() {
        val candidate = awardPlayer("candidate", rating = "7.8")

        assertNoRecognition(evaluate(candidate, awardPlayer("one", rating = "8.2"), awardPlayer("two", rating = "8.1")))
    }

    @Test
    fun `moderate deficit plus material pass errors qualifies the selected borderline candidate`() {
        val candidate = awardPlayer("candidate", rating = "7.7", passesCompleted = 9, passesAttempted = 20)
        val decision = evaluate(
            candidate,
            awardPlayer("one", rating = "8.5", passesCompleted = 18, passesAttempted = 20),
            awardPlayer("two", rating = "8.3", passesCompleted = 17, passesAttempted = 20),
        )

        assertRecognition(decision, candidate.player.id, NegativeRecognition.LOW_PERFORMANCE)
        val metrics = decision.metrics as AwardMetrics.Bagre
        assertThat(metrics.criticism).isEqualTo(BagreCriticism.PASSING)
        assertThat(metrics.passingSummary).extracting("completed", "attempted").containsExactly(9, 20)
        assertThat(metrics.peerAveragePassErrors).isEqualByComparingTo("2.50")
    }

    @Test
    fun `trivial pass error difference never qualifies a borderline player`() {
        val candidate = awardPlayer("candidate", rating = "7.7", passesCompleted = 16, passesAttempted = 20)

        assertNoRecognition(evaluate(
            candidate,
            awardPlayer("one", rating = "8.5", passesCompleted = 17, passesAttempted = 20),
            awardPlayer("two", rating = "8.3", passesCompleted = 17, passesAttempted = 20),
        ))
    }

    @Test
    fun `low volume peer passing data cannot create a relative pass-error signal`() {
        val candidate = awardPlayer("candidate", rating = "7.7", passesCompleted = 9, passesAttempted = 20)

        assertNoRecognition(evaluate(
            candidate,
            awardPlayer("one", rating = "8.5", passesCompleted = 0, passesAttempted = 1),
            awardPlayer("two", rating = "8.3", passesCompleted = 0, passesAttempted = 1),
        ))
    }

    @Test
    fun `moderate deficit plus meaningful tackle volume qualifies a borderline player`() {
        val candidate = awardPlayer("candidate", rating = "7.7", tacklesCompleted = 1, tacklesAttempted = 9)
        val decision = evaluate(candidate, awardPlayer("one", rating = "8.5"), awardPlayer("two", rating = "8.3"))

        assertRecognition(decision, candidate.player.id, NegativeRecognition.LOW_PERFORMANCE)
        assertThat((decision.metrics as AwardMetrics.Bagre).criticism).isEqualTo(BagreCriticism.TACKLING)
    }

    @Test
    fun `low volume tackle samples do not qualify a borderline player`() {
        listOf(
            awardPlayer("zero-one", rating = "7.7", tacklesCompleted = 0, tacklesAttempted = 1),
            awardPlayer("one-two", rating = "7.7", tacklesCompleted = 1, tacklesAttempted = 2),
        ).forEach { candidate ->
            assertNoRecognition(evaluate(candidate, awardPlayer("one", rating = "8.5"), awardPlayer("two", rating = "8.3")))
        }
    }

    @Test
    fun `candidate cannot fall through to a higher rated player when borderline qualification fails`() {
        val candidate = awardPlayer("candidate", rating = "7.7")
        val higherTerriblePlayer = awardPlayer("higher", rating = "7.9", passesCompleted = 0, passesAttempted = 20)

        assertNoRecognition(evaluate(candidate, higherTerriblePlayer, awardPlayer("good", rating = "8.0")))
    }

    @Test
    fun `two player borderline match uses the same thresholds`() {
        val candidate = awardPlayer("candidate", rating = "7.7")
        assertNoRecognition(evaluate(candidate, awardPlayer("peer", rating = "8.5")))

        val withEvidence = candidate.copy(passing = candidate.passing.copy(attempted = 20, completed = 8))
        assertRecognition(evaluate(withEvidence, awardPlayer("peer", rating = "8.5", passesCompleted = 18, passesAttempted = 20)), withEvidence.player.id, NegativeRecognition.LOW_PERFORMANCE)

        val strong = awardPlayer("strong", rating = "7.6")
        assertRecognition(evaluate(strong, awardPlayer("elite", rating = "9.4")), strong.player.id, NegativeRecognition.LOW_PERFORMANCE)
    }

    @Test
    fun `single eligible player has only the absolute rating rules`() {
        val bagre = awardPlayer("bagre", rating = "6.8")
        val low = awardPlayer("low", rating = "7.3")
        val borderline = awardPlayer("borderline", rating = "7.7", passesCompleted = 0, passesAttempted = 20)

        assertRecognition(evaluate(bagre), bagre.player.id, NegativeRecognition.BAGRE)
        assertRecognition(evaluate(low), low.player.id, NegativeRecognition.LOW_PERFORMANCE)
        assertNoRecognition(evaluate(borderline))
    }

    @Test
    fun `exact rating ties use player identity and not sporting statistics`() {
        val alpha = awardPlayer("alpha", rating = "6.8", passesCompleted = 20, passesAttempted = 20)
        val bravo = awardPlayer("bravo", rating = "6.8", passesCompleted = 0, passesAttempted = 20)

        assertRecognition(evaluate(bravo, alpha), alpha.player.id, NegativeRecognition.BAGRE)
    }

    @Test
    fun `positive contribution and EA MVP do not suppress an absolute recognition`() {
        val player = awardPlayer("contributor", rating = "6.9", goals = 1, assists = 1, eaMvp = true)

        assertRecognition(evaluate(player), player.player.id, NegativeRecognition.BAGRE)
    }

    @Test
    fun `goalkeepers and ineligible players remain excluded from negative candidate selection`() {
        val goalkeeper = awardPlayer("goalkeeper", rating = "5.0", role = PlayerRole.Goalkeeper)
        val ineligible = awardPlayer("ineligible", rating = "5.5")
        val outfield = awardPlayer("outfield", rating = "7.3")
        val players = listOf(goalkeeper, ineligible, outfield)

        val decision = evaluator.evaluate(players, eligibility(players, ineligible = setOf(ineligible.player.id)))

        assertRecognition(decision, outfield.player.id, NegativeRecognition.LOW_PERFORMANCE)
    }

    @Test
    fun `missing passing values are unavailable rather than coerced into errors`() {
        val candidate = awardPlayer("candidate", rating = "7.7", passesCompleted = null, passesAttempted = null)

        assertNoRecognition(evaluate(candidate, awardPlayer("one", rating = "8.5"), awardPlayer("peer", rating = "8.3")))
    }

    @Test
    fun `advanced aggregates and factual red cards do not affect the negative model`() {
        val candidate = awardPlayer(
            "candidate",
            rating = "7.7",
            redCards = 1,
            secondAssists = 99,
            throughPasses = 99,
            dribblesCompleted = 99,
            beats = 99,
            interceptions = 99,
        )
        val decision = evaluate(candidate, awardPlayer("one", rating = "8.5"), awardPlayer("two", rating = "8.3"))

        assertNoRecognition(decision)
        assertThat(decision.evidence).noneMatch {
            it is DecisionEvidence.AdvancedPerformance || it is DecisionEvidence.Discipline
        }
    }

    @Test
    fun `no rated eligible outfield candidate remains auditable`() {
        val goalkeeper = awardPlayer("goalkeeper", rating = "5.0", role = PlayerRole.Goalkeeper)
        val missing = awardPlayer("missing", rating = null)
        val players = listOf(goalkeeper, missing)

        val decision = evaluator.evaluate(players, eligibility(players))

        assertThat(decision.awarded).isFalse()
        assertThat(decision.reason).isEqualTo(AwardDecisionReason.NO_ELIGIBLE_CANDIDATE)
    }

    private fun evaluate(vararg players: PlayerMatchPerformance): AwardDecision =
        evaluator.evaluate(players.toList(), eligibility(players.toList()))

    private fun assertRecognition(
        decision: AwardDecision,
        winnerId: PlayerId,
        recognition: NegativeRecognition,
    ) {
        assertThat(decision.winnerId).isEqualTo(winnerId)
        assertThat(decision.reason).isEqualTo(AwardDecisionReason.QUALIFIED_NEGATIVE_PERFORMANCE)
        assertThat(decision.metrics).isInstanceOf(AwardMetrics.Bagre::class.java)
        assertThat((decision.metrics as AwardMetrics.Bagre).recognition).isEqualTo(recognition)
    }

    private fun assertNoRecognition(decision: AwardDecision) {
        assertThat(decision.awarded).isFalse()
        assertThat(decision.winnerId).isNull()
        assertThat(decision.reason).isEqualTo(AwardDecisionReason.NO_QUALIFIED_CANDIDATE)
    }
}
