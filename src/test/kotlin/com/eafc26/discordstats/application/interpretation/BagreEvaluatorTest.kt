package com.eafc26.discordstats.application.interpretation

import com.eafc26.discordstats.domain.interpretation.AwardDecisionReason
import com.eafc26.discordstats.domain.interpretation.AwardMetrics
import com.eafc26.discordstats.domain.interpretation.BagreCriticism
import com.eafc26.discordstats.domain.interpretation.DecisionEvidence
import com.eafc26.discordstats.domain.match.PlayerId
import com.eafc26.discordstats.domain.match.PlayerRole
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BagreEvaluatorTest {

    private val evaluator = BagreEvaluator()

    @Test
    fun `one excellent eligible player produces no Bagre`() {
        val player = awardPlayer("excellent", rating = "9.4")

        val decision = evaluate(player)

        assertNoBagre(decision)
    }

    @Test
    fun `one normal eligible player without negative evidence produces no Bagre`() {
        val player = awardPlayer("normal", rating = "7.0")

        assertNoBagre(evaluate(player))
    }

    @Test
    fun `two excellent players do not produce Bagre merely by relative order`() {
        val ten = awardPlayer("ten", rating = "10.0")
        val nineFour = awardPlayer("nine-four", rating = "9.4")

        assertNoBagre(evaluate(ten, nineFour))
    }

    @Test
    fun `production equivalent nine point four with one of nine tackles is not Bagre`() {
        val ten = awardPlayer("ten", rating = "10.0")
        val nineFour = awardPlayer(
            "nine-four",
            rating = "9.4",
            tacklesCompleted = 1,
            tacklesAttempted = 9,
        )

        assertNoBagre(evaluate(ten, nineFour))
    }

    @Test
    fun `two players with one genuinely poor performer select the qualified player`() {
        val good = awardPlayer("good", rating = "9.0")
        val poor = awardPlayer(
            "poor",
            rating = "5.5",
            passesCompleted = 6,
            passesAttempted = 15,
            tacklesCompleted = 1,
            tacklesAttempted = 8,
        )

        val decision = evaluate(good, poor)

        assertThat(decision.winnerId).isEqualTo(poor.player.id)
        assertThat(decision.reason).isEqualTo(AwardDecisionReason.QUALIFIED_NEGATIVE_PERFORMANCE)
        assertThat(decision.metrics).isInstanceOf(AwardMetrics.Bagre::class.java)
        assertThat((decision.metrics as AwardMetrics.Bagre).criticism).isEqualTo(BagreCriticism.RATING)
    }

    @Test
    fun `three or more good performers produce no Bagre`() {
        val players = listOf(
            awardPlayer("one", rating = "9.8"),
            awardPlayer("two", rating = "8.9"),
            awardPlayer("three", rating = "7.8"),
        )

        assertNoBagre(evaluator.evaluate(players, eligibility(players)))
    }

    @Test
    fun `five players with one genuinely poor performer select only the qualified player`() {
        val poor = awardPlayer(
            "poor",
            rating = "5.2",
            passesCompleted = 4,
            passesAttempted = 15,
            tacklesCompleted = 1,
            tacklesAttempted = 8,
        )
        val players = listOf(
            awardPlayer("one", rating = "9.0"),
            awardPlayer("two", rating = "8.5"),
            awardPlayer("three", rating = "8.2"),
            awardPlayer("four", rating = "7.8"),
            poor,
        )

        assertThat(evaluator.evaluate(players, eligibility(players)).winnerId).isEqualTo(poor.player.id)
    }

    @Test
    fun `multiple qualified candidates use severity then rating then deterministic player id`() {
        val alpha = awardPlayer("alpha", rating = "5.5", passesCompleted = 8, passesAttempted = 15)
        val bravo = awardPlayer("bravo", rating = "5.5", passesCompleted = 8, passesAttempted = 15)

        val decision = evaluate(bravo, alpha)

        assertThat(decision.winnerId).isEqualTo(alpha.player.id)
    }

    @Test
    fun `rating near threshold without independent sporting evidence produces no Bagre`() {
        val player = awardPlayer("near-threshold", rating = "6.0")

        assertNoBagre(evaluate(player))
    }

    @Test
    fun `tiny tackle sample is not negative qualification evidence`() {
        val player = awardPlayer(
            "tiny-tackle-sample",
            rating = "6.0",
            tacklesCompleted = 0,
            tacklesAttempted = 1,
        )

        assertNoBagre(evaluate(player))
    }

    @Test
    fun `meaningful poor tackle sample can qualify a low rated player`() {
        val player = awardPlayer(
            "poor-tackler",
            rating = "6.0",
            tacklesCompleted = 1,
            tacklesAttempted = 9,
        )

        val decision = evaluate(player)

        assertThat(decision.winnerId).isEqualTo(player.player.id)
        assertThat((decision.metrics as AwardMetrics.Bagre).tackleSummary)
            .extracting("completed", "attempted", "accuracyPercent")
            .containsExactly(1, 9, 11)
    }

    @Test
    fun `tiny passing sample is not negative qualification evidence`() {
        val player = awardPlayer(
            "tiny-pass-sample",
            rating = "6.0",
            passesCompleted = 1,
            passesAttempted = 2,
        )

        assertNoBagre(evaluate(player))
    }

    @Test
    fun `meaningful poor passing sample can qualify a low rated player`() {
        val player = awardPlayer(
            "poor-passer",
            rating = "6.0",
            passesCompleted = 6,
            passesAttempted = 15,
        )

        val decision = evaluate(player)

        assertThat(decision.winnerId).isEqualTo(player.player.id)
        assertThat((decision.metrics as AwardMetrics.Bagre).passingSummary)
            .extracting("completed", "attempted", "accuracyPercent")
            .containsExactly(6, 15, 40)
    }

    @Test
    fun `strong direct contribution guard prevents absurd Bagre selection`() {
        val player = awardPlayer(
            "contributor",
            rating = "5.0",
            goals = 1,
            assists = 1,
            passesCompleted = 1,
            passesAttempted = 15,
            tacklesCompleted = 0,
            tacklesAttempted = 8,
        )

        assertNoBagre(evaluate(player))
    }

    @Test
    fun `EA MVP and goalkeeper are never treated as Bagre candidates`() {
        val mvp = awardPlayer(
            "mvp",
            rating = "5.0",
            passesCompleted = 1,
            passesAttempted = 15,
            eaMvp = true,
        )
        val goalkeeper = awardPlayer(
            "goalkeeper",
            rating = "5.0",
            passesCompleted = 1,
            passesAttempted = 15,
            role = PlayerRole.Goalkeeper,
        )

        assertNoBagre(evaluate(mvp, goalkeeper))
    }

    @Test
    fun `advanced statistics do not participate in Bagre qualification`() {
        val player = awardPlayer(
            "advanced-only",
            rating = "7.0",
            secondAssists = 99,
            throughPasses = 99,
            dribblesCompleted = 99,
            beats = 99,
            interceptions = 99,
        )

        val decision = evaluate(player)

        assertNoBagre(decision)
        assertThat(decision.evidence).noneMatch { it is DecisionEvidence.AdvancedPerformance }
    }

    @Test
    fun `ratings below minimum and absent ratings produce audited no eligible candidate`() {
        val low = awardPlayer("low", rating = "4.9")
        val absent = awardPlayer("absent", rating = null)
        val players = listOf(low, absent)

        val decision = evaluator.evaluate(players, eligibility(players))

        assertThat(decision.awarded).isFalse()
        assertThat(decision.reason).isEqualTo(AwardDecisionReason.NO_ELIGIBLE_CANDIDATE)
        assertThat(decision.evidence.filterIsInstance<DecisionEvidence.Rating>()).hasSize(2)
    }

    private fun evaluate(vararg players: com.eafc26.discordstats.domain.match.PlayerMatchPerformance) =
        evaluator.evaluate(players.toList(), eligibility(players.toList()))

    private fun assertNoBagre(decision: com.eafc26.discordstats.domain.interpretation.AwardDecision) {
        assertThat(decision.awarded).isFalse()
        assertThat(decision.winnerId).isNull()
        assertThat(decision.reason).isEqualTo(AwardDecisionReason.NO_QUALIFIED_CANDIDATE)
    }
}
