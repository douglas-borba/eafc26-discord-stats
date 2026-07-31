package com.eafc26.discordstats.application.interpretation

import com.eafc26.discordstats.domain.interpretation.AwardDecision
import com.eafc26.discordstats.domain.interpretation.AwardDecisionReason
import com.eafc26.discordstats.domain.interpretation.AwardType
import com.eafc26.discordstats.domain.interpretation.GoalkeeperArchetype
import com.eafc26.discordstats.domain.interpretation.GoalkeeperNarrativeVariant
import com.eafc26.discordstats.domain.interpretation.BagreCriticism
import com.eafc26.discordstats.domain.interpretation.MatchAwards
import com.eafc26.discordstats.domain.interpretation.MatchOutcome
import com.eafc26.discordstats.domain.interpretation.OffensiveNarrativeCategory
import com.eafc26.discordstats.domain.interpretation.ResultDecision
import com.eafc26.discordstats.domain.interpretation.ResultDecisionSource
import com.eafc26.discordstats.domain.interpretation.RuleId
import com.eafc26.discordstats.domain.interpretation.RuleReference
import com.eafc26.discordstats.domain.interpretation.TeamMetrics
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.PassingStats
import com.eafc26.discordstats.domain.match.PlayerId
import com.eafc26.discordstats.domain.match.PlayerMatchPerformance
import com.eafc26.discordstats.domain.match.PlayerRole
import com.eafc26.discordstats.domain.match.Score
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class MatchFeaturesEvaluatorTest {

    private val evaluator = MatchFeaturesEvaluator()

    @Test
    fun `contributions are ordered independently and highlights exclude Bagre`() {
        val bagre = awardPlayer("bagre", rating = "5.0", goals = 3)
        val scorer = awardPlayer("scorer", rating = "8.0", goals = 2, assists = 1)
        val creator = awardPlayer("creator", rating = "7.0", goals = 1, assists = 3)
        val players = listOf(bagre, scorer, creator)

        val features = evaluate(players, bagre.player.id)

        assertThat(features.contributions.goalScorers.map { it.playerId })
            .containsExactly(bagre.player.id, scorer.player.id, creator.player.id)
        assertThat(features.contributions.assistProviders.map { it.playerId })
            .containsExactly(creator.player.id, scorer.player.id)
        assertThat(features.highlights.players.map { it.playerId })
            .containsExactly(scorer.player.id, creator.player.id)
        assertThat(features.highlights.unfilteredPlayers.map { it.playerId })
            .containsExactly(scorer.player.id, creator.player.id, bagre.player.id)
        assertThat(features.highlights.teamAverageRating).isEqualByComparingTo("6.666667")
    }

    @Test
    fun `EA recognized MVP is selected from eligible players with auditable evidence`() {
        val goalkeeper = awardPlayer(
            "keeper",
            rating = "9.4",
            eaMvp = true,
            role = PlayerRole.Goalkeeper,
        )
        val player = awardPlayer("line", rating = "8.0")

        val decision = evaluate(listOf(player, goalkeeper)).eaRecognizedMvp

        assertThat(decision!!.playerId).isEqualTo(goalkeeper.player.id)
        assertThat(decision.rating).isEqualByComparingTo("9.4")
        assertThat(decision.rule).isEqualTo(MatchFeaturesEvaluator.EA_RECOGNIZED_MVP_RULE)
        assertThat(decision.evidence).isNotEmpty()
    }

    @Test
    fun `offensive narratives reuse result and Bagre exclusion and preserve priority`() {
        val bagre = awardPlayer("bagre", rating = "5.0", shots = 8, goals = 4)
        val decisive = awardPlayer("decisive", shots = 6, goals = 3)
        val wasteful = awardPlayer("wasteful", shots = 6, goals = 0)
        val players = listOf(bagre, decisive, wasteful)

        val features = evaluate(players, bagre.player.id, ourScore = 4, opponentScore = 1)

        assertThat(features.offensiveNarratives.map { it.playerId })
            .containsExactly(decisive.player.id, wasteful.player.id)
        assertThat(features.offensiveNarratives.map { it.category })
            .containsExactly(
                OffensiveNarrativeCategory.DECISIVE,
                OffensiveNarrativeCategory.LACKED_COMPOSURE,
            )
        assertThat(features.offensiveNarratives).allMatch {
            it.rule == MatchFeaturesEvaluator.OFFENSIVE_RULE && it.evidence.isNotEmpty()
        }
    }

    @Test
    fun `Bagre assessment records visible summaries and criticism category`() {
        val bagre = awardPlayer(
            "bagre",
            rating = "5.0",
            tacklesCompleted = 1,
            tacklesAttempted = 5,
            passesCompleted = 3,
            passesAttempted = 10,
        )

        val decision = evaluate(listOf(bagre), bagre.player.id).bagrePerformance

        assertThat(decision!!.criticism).isEqualTo(BagreCriticism.TACKLING)
        assertThat(decision.tackleSummary!!.accuracyPercent).isEqualTo(20)
        assertThat(decision.passingSummary!!.accuracyPercent).isEqualTo(30)
        assertThat(decision.rule).isEqualTo(MatchFeaturesEvaluator.BAGRE_PERFORMANCE_RULE)
    }

    @Test
    fun `red card selection uses lowest rating then latest source name`() {
        val earlier = awardPlayer("Alpha", rating = "6.0", redCards = 1)
        val later = awardPlayer("Zulu", rating = "6.0", redCards = 1)

        val decision = evaluate(listOf(earlier, later)).redCard

        assertThat(decision!!.playerId).isEqualTo(later.player.id)
        assertThat(decision.rule).isEqualTo(MatchFeaturesEvaluator.RED_CARD_RULE)
        assertThat(decision.evidence).isNotEmpty()
    }

    @Test
    fun `pass precision uses accuracy then attempt volume and excludes Bagre`() {
        val bagre = awardPlayer("bagre", rating = "5.0", passesCompleted = 30, passesAttempted = 30)
        val smaller = awardPlayer("small", passesCompleted = 10, passesAttempted = 10)
        val larger = awardPlayer("large", passesCompleted = 20, passesAttempted = 20)

        val decision = evaluate(listOf(bagre, smaller, larger), bagre.player.id).passPrecision

        assertThat(decision!!.playerId).isEqualTo(larger.player.id)
        assertThat(decision.accuracyPercent).isEqualTo(100)
    }

    @Test
    fun `lost mail applies comparative and low-volume eligibility`() {
        val good = awardPlayer("good", passesCompleted = 18, passesAttempted = 20)
        val poor = awardPlayer("poor", passesCompleted = 8, passesAttempted = 20)
        val tiny = awardPlayer("tiny", passesCompleted = 0, passesAttempted = 2)

        val decision = evaluate(listOf(good, poor, tiny)).lostMail

        assertThat(decision!!.playerId).isEqualTo(poor.player.id)
        assertThat(decision.playerAccuracyPercent).isEqualTo(40)
        assertThat(decision.teamAccuracyPercent).isEqualTo(61)
        assertThat(decision.deltaPercent).isEqualTo(21)
    }

    @Test
    fun `longest playing goalkeeper is classified from normalized save facts`() {
        val keeper = awardPlayer(
            "keeper",
            rating = "8.5",
            role = PlayerRole.Goalkeeper,
            saves = 5,
            goalsConceded = 1,
            reflexSaves = 3,
        )

        val decision = evaluate(listOf(keeper)).goalkeeper

        assertThat(decision!!.playerId).isEqualTo(keeper.player.id)
        assertThat(decision.archetype).isEqualTo(GoalkeeperArchetype.WALL)
        assertThat(decision.narrativeVariant).isEqualTo(GoalkeeperNarrativeVariant.REFLEX)
        assertThat(decision.rule).isEqualTo(MatchFeaturesEvaluator.GOALKEEPER_RULE)
        assertThat(decision.evidence).isNotEmpty()
    }

    @Test
    fun `every always-produced feature decision is traceable`() {
        val features = evaluate(listOf(awardPlayer("one")))

        assertThat(features.contributions.rule).isEqualTo(MatchFeaturesEvaluator.CONTRIBUTIONS_RULE)
        assertThat(features.contributions.evidence).isNotEmpty()
        assertThat(features.highlights.rule).isEqualTo(MatchFeaturesEvaluator.HIGHLIGHTS_RULE)
        assertThat(features.highlights.evidence).isNotEmpty()
        assertThat(features.evaluations).hasSize(9)
        assertThat(features.evaluations).allMatch { it.evidence.isNotEmpty() }
        assertThat(features.evaluations.single {
            it.feature == com.eafc26.discordstats.domain.interpretation.MatchFeatureType.RED_CARD
        }.produced).isFalse()
    }

    private fun evaluate(
        players: List<PlayerMatchPerformance>,
        bagreId: PlayerId? = null,
        ourScore: Int = 1,
        opponentScore: Int = 0,
    ) = evaluator.evaluate(
        players = players,
        eligibility = eligibility(players),
        result = ResultDecision(
            ClubId("ours"),
            ClubId("opponent"),
            Score(ourScore),
            Score(opponentScore),
            when {
                ourScore > opponentScore -> MatchOutcome.WIN
                ourScore < opponentScore -> MatchOutcome.LOSS
                else -> MatchOutcome.DRAW
            },
            ResultDecisionSource.SCOREBOARD,
            RuleReference(RuleId("match.outcome"), 1),
            listOf(com.eafc26.discordstats.domain.interpretation.DecisionEvidence.Scoreboard(ourScore, opponentScore)),
        ),
        teamMetrics = TeamMetricsCalculator().calculate(players),
        awards = awards(bagreId),
    )

    private fun awards(bagreId: PlayerId?): MatchAwards {
        fun decision(type: AwardType, winner: PlayerId?) = AwardDecision(
            type,
            winner,
            if (winner == null) {
                AwardDecisionReason.NO_ELIGIBLE_CANDIDATE
            } else {
                AwardDecisionReason.LOWEST_ELIGIBLE_RATING
            },
            RuleReference(RuleId("test.${type.name.lowercase()}"), 1),
            listOf(com.eafc26.discordstats.domain.interpretation.DecisionEvidence.Scoreboard(1, 0)),
        )
        return MatchAwards(
            decision(AwardType.CRAQUE, null),
            decision(AwardType.BAGRE, bagreId),
            decision(AwardType.XERIFE, null),
        )
    }
}
