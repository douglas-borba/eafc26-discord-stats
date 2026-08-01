package com.eafc26.discordstats.application.interpretation

import com.eafc26.discordstats.domain.interpretation.DecisionEvidence
import com.eafc26.discordstats.domain.interpretation.MatchOutcome
import com.eafc26.discordstats.domain.interpretation.ResultDecisionSource
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.ClubIdentity
import com.eafc26.discordstats.domain.match.ClubMatchPerformance
import com.eafc26.discordstats.domain.match.ClubName
import com.eafc26.discordstats.domain.match.FootballMatch
import com.eafc26.discordstats.domain.match.MatchId
import com.eafc26.discordstats.domain.match.ReportedMatchResult
import com.eafc26.discordstats.domain.match.Score
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant

class MatchOutcomeEvaluatorTest {

    private val evaluator = MatchOutcomeEvaluator()
    private val ourClub = ClubId("our-club")
    private val opponent = ClubId("opponent")

    @Test
    fun `scoreboard decides win draw and loss`() {
        assertThat(evaluator.evaluate(match(3, 1), ourClub).outcome).isEqualTo(MatchOutcome.WIN)
        assertThat(evaluator.evaluate(match(2, 2), ourClub).outcome).isEqualTo(MatchOutcome.DRAW)
        assertThat(evaluator.evaluate(match(0, 1), ourClub).outcome).isEqualTo(MatchOutcome.LOSS)
    }

    @Test
    fun `scoreboard takes precedence over contradictory reported result`() {
        val decision = evaluator.evaluate(
            match(2, 0, ourReportedResult = ReportedMatchResult.LOSS),
            ourClub,
        )

        assertThat(decision.outcome).isEqualTo(MatchOutcome.WIN)
        assertThat(decision.decidedBy).isEqualTo(ResultDecisionSource.SCOREBOARD)
        assertThat(decision.rule).isEqualTo(MatchOutcomeEvaluator.RULE)
    }

    @Test
    fun `reported result is fallback when either score is absent`() {
        val decision = evaluator.resolve(
            ourClub = ourClub,
            opponentClub = opponent,
            ourScore = null,
            opponentScore = Score(4),
            reportedResult = ReportedMatchResult.WIN,
        )

        assertThat(decision.outcome).isEqualTo(MatchOutcome.WIN)
        assertThat(decision.decidedBy).isEqualTo(ResultDecisionSource.REPORTED_RESULT_FALLBACK)
        assertThat(decision.ourScore).isEqualTo(Score(0))
        assertThat(decision.opponentScore).isEqualTo(Score(4))
    }

    @Test
    fun `missing reported result follows current loss fallback`() {
        val decision = evaluator.resolve(
            ourClub = ourClub,
            opponentClub = opponent,
            ourScore = null,
            opponentScore = null,
            reportedResult = null,
        )

        assertThat(decision.outcome).isEqualTo(MatchOutcome.LOSS)
        assertThat(decision.ourScore).isEqualTo(Score(0))
        assertThat(decision.opponentScore).isEqualTo(Score(0))
    }

    @Test
    fun `decision records scoreboard and reported result evidence`() {
        val decision = evaluator.evaluate(
            match(3, 2, ourReportedResult = ReportedMatchResult.WIN),
            ourClub,
        )

        assertThat(decision.evidence).containsExactly(
            DecisionEvidence.Scoreboard(ourScore = 3, opponentScore = 2),
            DecisionEvidence.ReportedResult(ReportedMatchResult.WIN),
        )
    }

    @Test
    fun `same match can be evaluated from either participant perspective`() {
        val match = match(3, 1)

        assertThat(evaluator.evaluate(match, ourClub).outcome).isEqualTo(MatchOutcome.WIN)
        assertThat(evaluator.evaluate(match, opponent).outcome).isEqualTo(MatchOutcome.LOSS)
    }

    @Test
    fun `unknown perspective is rejected explicitly`() {
        assertThatThrownBy {
            evaluator.evaluate(match(1, 1), ClubId("not-a-participant"))
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun match(
        ourScore: Int,
        opponentScore: Int,
        ourReportedResult: ReportedMatchResult = ReportedMatchResult.WIN,
    ): FootballMatch = FootballMatch(
        id = MatchId("match-1"),
        playedAt = Instant.EPOCH,
        competition = null,
        participants = listOf(
            club(ourClub, "Our FC", ourScore, ourReportedResult),
            club(opponent, "Opponent FC", opponentScore, ReportedMatchResult.LOSS),
        ),
    )

    private fun club(
        id: ClubId,
        name: String,
        score: Int,
        result: ReportedMatchResult,
    ): ClubMatchPerformance = ClubMatchPerformance(
        club = ClubIdentity(id, ClubName(name)),
        score = Score(score),
        reportedResult = result,
        players = emptyList(),
    )
}
