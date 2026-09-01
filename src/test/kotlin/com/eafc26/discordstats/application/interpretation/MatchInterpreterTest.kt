package com.eafc26.discordstats.application.interpretation

import com.eafc26.discordstats.domain.interpretation.AwardType
import com.eafc26.discordstats.domain.interpretation.MatchOutcome
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.ClubIdentity
import com.eafc26.discordstats.domain.match.ClubMatchPerformance
import com.eafc26.discordstats.domain.match.ClubName
import com.eafc26.discordstats.domain.match.CompetitionType
import com.eafc26.discordstats.domain.match.FootballMatch
import com.eafc26.discordstats.domain.match.MatchId
import com.eafc26.discordstats.domain.match.ReportedMatchResult
import com.eafc26.discordstats.domain.match.Score
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class MatchInterpreterTest {

    private val interpreter = MatchInterpreter()

    @Test
    fun `composes outcome eligibility metrics and awards without changing their decisions`() {
        val bagre = awardPlayer("bagre", rating = "5.0").withDuration(900)
        val craque = awardPlayer(
            "craque",
            rating = "9.0",
            goals = 2,
            tacklesCompleted = 7,
            tacklesAttempted = 10,
        ).withDuration(1_000)
        val match = match(listOf(bagre, craque), ourScore = 2, opponentScore = 1)

        val interpretation = interpreter.interpret(match, OUR_CLUB)

        assertThat(interpretation.result.outcome).isEqualTo(MatchOutcome.WIN)
        assertThat(interpretation.eligibility.eligiblePlayerIds)
            .containsExactlyInAnyOrder(bagre.player.id, craque.player.id)
        assertThat(interpretation.teamMetrics.totalGoals).isEqualTo(2)
        assertThat(interpretation.awards.bagre.winnerId).isEqualTo(bagre.player.id)
        assertThat(interpretation.awards.craque.winnerId).isEqualTo(craque.player.id)
        assertThat(interpretation.awards.xerife.winnerId).isEqualTo(craque.player.id)
    }

    @Test
    fun `team metrics reuse eligibility and exclude short participation`() {
        val fullMatch = awardPlayer("full", rating = "8.0", goals = 1).withDuration(1_000)
        val short = awardPlayer("short", rating = "10.0", goals = 5).withDuration(899)
        val match = match(listOf(fullMatch, short))

        val interpretation = interpreter.interpret(match, OUR_CLUB)

        assertThat(interpretation.eligibility.eligiblePlayerIds).containsExactly(fullMatch.player.id)
        assertThat(interpretation.teamMetrics.averageRating).isEqualByComparingTo("8.000000")
        assertThat(interpretation.teamMetrics.totalGoals).isEqualTo(1)
        assertThat(interpretation.awards.bagre.awarded).isFalse()
        assertThat(interpretation.awards.craque.winnerId).isEqualTo(fullMatch.player.id)
    }

    @Test
    fun `aggregates every applied rule and decision evidence for audit`() {
        val players = listOf(
            awardPlayer(
                "one",
                rating = "7.0",
                tacklesCompleted = 4,
                tacklesAttempted = 5,
            ).withDuration(1_000)
        )

        val interpretation = interpreter.interpret(match(players), OUR_CLUB)

        assertThat(interpretation.appliedRules.map { it.id.value })
            .containsExactly(
                "match.outcome",
                "player.statistical-eligibility",
                "award.craque",
                "award.bagre",
                "award.xerife",
                "match.player-contributions",
                "match.rated-highlights",
                "narrative.bagre-performance",
                "narrative.offensive-performance",
                "narrative.behind-the-play",
                "narrative.one-on-one",
                "narrative.red-card",
                "award.pass-precision",
                "award.lost-mail",
                "narrative.goalkeeper",
                "recognition.ea-mvp",
            )
        assertThat(interpretation.evidence).isNotEmpty()
        assertThat(interpretation.awards.craque.evidence).isNotEmpty()
        assertThat(interpretation.awards.bagre.evidence).isNotEmpty()
        assertThat(interpretation.awards.xerife.evidence).isNotEmpty()
    }

    @Test
    fun `Bagre exclusion remains visible in the composed audit trail`() {
        val bagre = awardPlayer(
            "bagre",
            rating = "5.0",
            passesCompleted = 1,
            passesAttempted = 15,
            tacklesCompleted = 0,
            tacklesAttempted = 8,
        ).withDuration(1_000)
        val other = awardPlayer(
            "other",
            rating = "7.0",
            tacklesCompleted = 4,
            tacklesAttempted = 5,
        ).withDuration(1_000)

        val interpretation = interpreter.interpret(match(listOf(bagre, other)), OUR_CLUB)

        assertThat(interpretation.awards.bagre.winnerId).isEqualTo(bagre.player.id)
        assertThat(interpretation.awards.craque.winnerId).isEqualTo(other.player.id)
        assertThat(
            interpretation.awards.craque.evidence
                .filterIsInstance<com.eafc26.discordstats.domain.interpretation.DecisionEvidence.AwardCandidate>()
        ).anySatisfy {
            assertThat(it.playerId).isEqualTo(bagre.player.id)
            assertThat(it.excludedByAward).isEqualTo(AwardType.BAGRE)
        }
    }

    @Test
    fun `rejects a perspective that is not a participant`() {
        val match = match(listOf(awardPlayer("one").withDuration(1_000)))

        assertThatThrownBy {
            interpreter.interpret(match, ClubId("unknown"))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("not a match participant")
    }

    private fun match(
        players: List<com.eafc26.discordstats.domain.match.PlayerMatchPerformance>,
        ourScore: Int = 1,
        opponentScore: Int = 0,
    ) = FootballMatch(
        id = MatchId("phase-5-match"),
        playedAt = Instant.parse("2026-01-01T00:00:00Z"),
        competition = CompetitionType.LEAGUE,
        participants = listOf(
            ClubMatchPerformance(
                club = ClubIdentity(OUR_CLUB, ClubName("Our club")),
                score = Score(ourScore),
                reportedResult = ReportedMatchResult.WIN,
                players = players,
            ),
            ClubMatchPerformance(
                club = ClubIdentity(OPPONENT, ClubName("Opponent")),
                score = Score(opponentScore),
                reportedResult = ReportedMatchResult.LOSS,
                players = emptyList(),
            ),
        ),
    )

    private fun com.eafc26.discordstats.domain.match.PlayerMatchPerformance.withDuration(
        seconds: Long,
    ) = copy(participation = participation.copy(duration = Duration.ofSeconds(seconds)))

    companion object {
        val OUR_CLUB = ClubId("ours")
        val OPPONENT = ClubId("opponent")
    }
}
