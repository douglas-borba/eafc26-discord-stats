package com.eafc26.discordstats.application.interpretation

import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.ClubIdentity
import com.eafc26.discordstats.domain.match.ClubMatchPerformance
import com.eafc26.discordstats.domain.match.ClubName
import com.eafc26.discordstats.domain.match.FootballMatch
import com.eafc26.discordstats.domain.match.MatchId
import com.eafc26.discordstats.domain.match.MatchCompletion
import com.eafc26.discordstats.domain.match.PlayerIdentity
import com.eafc26.discordstats.domain.match.PlayerId
import com.eafc26.discordstats.domain.match.PlayerMatchPerformance
import com.eafc26.discordstats.domain.match.Participation
import com.eafc26.discordstats.domain.match.AttackingStats
import com.eafc26.discordstats.domain.match.PassingStats
import com.eafc26.discordstats.domain.match.DefendingStats
import com.eafc26.discordstats.domain.match.DisciplineStats
import com.eafc26.discordstats.domain.match.EaRecognition
import com.eafc26.discordstats.domain.match.PlayerRole
import com.eafc26.discordstats.domain.match.MatchRating
import com.eafc26.discordstats.application.story.MatchStoryExtractor
import com.eafc26.discordstats.domain.match.Score
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class EmptyMatchInterpretationTest {

    @Test fun `DNF keeps official outcome but suppresses sporting decisions`() {
        val ours = ClubId("ours")
        val player = PlayerMatchPerformance(PlayerIdentity(PlayerId("p"), null, null), PlayerRole.Outfield(null), Participation(null, null), MatchRating(java.math.BigDecimal("9.0")), AttackingStats(2, 1, 4), PassingStats(10, 9), DefendingStats(2, 2), DisciplineStats(0), null, EaRecognition(false))
        val match = FootballMatch(MatchId("dnf"), Instant.EPOCH, null, listOf(
            ClubMatchPerformance(ClubIdentity(ours, ClubName("Ours")), Score(0), null, listOf(player)),
            ClubMatchPerformance(ClubIdentity(ClubId("opponent"), ClubName("Opponent")), Score(3), null, emptyList()),
        ), MatchCompletion.dnf(ours))
        val interpretation = MatchInterpreter().interpret(match, ours)
        assertThat(interpretation.result.outcome.name).isEqualTo("LOSS")
        assertThat(listOf(interpretation.awards.craque, interpretation.awards.bagre, interpretation.awards.xerife).none { it.awarded }).isTrue()
        assertThat(interpretation.features.contributions.goalScorers).isEmpty()
        assertThat(MatchStoryExtractor().extract(interpretation).stories).hasSize(1)
    }

    @Test
    fun `match without players remains fully interpretable and auditable`() {
        val ours = ClubId("ours")
        val match = FootballMatch(
            MatchId("empty-match"),
            Instant.EPOCH,
            null,
            listOf(
                ClubMatchPerformance(
                    ClubIdentity(ours, ClubName("Ours")),
                    Score(0),
                    null,
                    emptyList(),
                ),
                ClubMatchPerformance(
                    ClubIdentity(ClubId("opponent"), ClubName("Opponent")),
                    Score(0),
                    null,
                    emptyList(),
                ),
            ),
        )

        val interpretation = MatchInterpreter().interpret(match, ours)

        assertThat(interpretation.awards.craque.awarded).isFalse()
        assertThat(interpretation.awards.bagre.awarded).isFalse()
        assertThat(interpretation.awards.xerife.awarded).isFalse()
        assertThat(interpretation.awards.craque.evidence).isNotEmpty()
        assertThat(interpretation.features.contributions.evidence).isNotEmpty()
        assertThat(interpretation.features.highlights.evidence).isNotEmpty()
    }
}
