package com.eafc26.discordstats.presentation

import com.eafc26.discordstats.application.interpretation.MatchInterpreter
import com.eafc26.discordstats.application.interpretation.awardPlayer
import com.eafc26.discordstats.application.story.MatchStoryExtractor
import com.eafc26.discordstats.domain.interpretation.AwardType
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.ClubIdentity
import com.eafc26.discordstats.domain.match.ClubMatchPerformance
import com.eafc26.discordstats.domain.match.ClubName
import com.eafc26.discordstats.domain.match.FootballMatch
import com.eafc26.discordstats.domain.match.MatchId
import com.eafc26.discordstats.domain.match.PlayerMatchPerformance
import com.eafc26.discordstats.domain.match.PlayerRole
import com.eafc26.discordstats.domain.match.ReportedMatchResult
import com.eafc26.discordstats.domain.match.Score
import com.eafc26.discordstats.domain.story.MatchStories
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant

class MatchSummaryDecisionAdapterTest {

    private val adapter = MatchSummaryDecisionAdapter()

    @Test
    fun `maps interpreted outcomes to the exact legacy visual contract`() {
        listOf(
            OutcomeCase(3, 1, OutcomeType.WIN, "Vitória", "🟢", 0x2ECC71),
            OutcomeCase(1, 1, OutcomeType.DRAW, "Empate", "🟡", 0x95A5A6),
            OutcomeCase(0, 2, OutcomeType.LOSS, "Derrota", "🔴", 0xE74C3C),
        ).forEach { case ->
            val interpretation = interpretation(
                listOf(goalkeeper()),
                case.ourScore,
                case.opponentScore,
            )
            val projection = adapter.adapt(
                interpretation,
                MatchStoryExtractor().extract(interpretation),
            )

            assertThat(projection.ourScore).isEqualTo(case.ourScore)
            assertThat(projection.opponentScore).isEqualTo(case.opponentScore)
            assertThat(projection.outcome.type).isEqualTo(case.type)
            assertThat(projection.outcome.label).isEqualTo(case.label)
            assertThat(projection.outcome.emoji).isEqualTo(case.emoji)
            assertThat(projection.outcome.color).isEqualTo(case.color)
        }
    }

    @Test
    fun `projects only winners already present in award stories`() {
        val bagre = awardPlayer("bagre", rating = "5.0")
        val positive = awardPlayer(
            "positive",
            rating = "9.0",
            tacklesCompleted = 7,
            tacklesAttempted = 10,
        )
        val interpretation = interpretation(listOf(bagre, positive))

        val projection = adapter.adapt(
            interpretation,
            MatchStoryExtractor().extract(interpretation),
        )

        assertThat(projection.awards.map { it.type })
            .containsExactly(AwardType.CRAQUE, AwardType.BAGRE, AwardType.XERIFE)
        assertThat(projection.awards.associate { it.type to it.winnerId })
            .containsEntry(AwardType.CRAQUE, interpretation.awards.craque.winnerId)
            .containsEntry(AwardType.BAGRE, interpretation.awards.bagre.winnerId)
            .containsEntry(AwardType.XERIFE, interpretation.awards.xerife.winnerId)
    }

    @Test
    fun `preserves story rule references and evidence in the presentation trace`() {
        val interpretation = interpretation(
            listOf(
                awardPlayer("bagre", rating = "5.0"),
                awardPlayer("positive", rating = "8.0"),
            )
        )
        val stories = MatchStoryExtractor().extract(interpretation)

        val projection = adapter.adapt(interpretation, stories)

        assertThat(projection.trace.rules)
            .containsExactlyElementsOf(stories.stories.flatMap { it.provenance.rules }.distinct())
        assertThat(projection.trace.evidence)
            .containsExactlyElementsOf(stories.stories.flatMap { it.provenance.evidence })
    }

    @Test
    fun `rejects stories belonging to another match`() {
        val interpretation = interpretation(listOf(goalkeeper()))
        val stories = MatchStoryExtractor().extract(interpretation)
        val otherMatchStories = MatchStories(
            matchId = MatchId("another-match"),
            stories = stories.stories,
        )

        assertThatThrownBy {
            adapter.adapt(interpretation, otherMatchStories)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("same match")
    }

    @Test
    fun `omitted awards remain omitted instead of being recomputed`() {
        val interpretation = interpretation(listOf(goalkeeper()))

        val projection = adapter.adapt(
            interpretation,
            MatchStoryExtractor().extract(interpretation),
        )

        assertThat(projection.awards).isEmpty()
    }

    private fun interpretation(
        players: List<PlayerMatchPerformance>,
        ourScore: Int = 1,
        opponentScore: Int = 0,
    ) = MatchInterpreter().interpret(
        FootballMatch(
            id = MatchId("phase-7-match"),
            playedAt = Instant.parse("2026-01-01T00:00:00Z"),
            competition = null,
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
        ),
        OUR_CLUB,
    )

    private fun goalkeeper() = awardPlayer(
        id = "goalkeeper",
        role = PlayerRole.Goalkeeper,
    )

    private data class OutcomeCase(
        val ourScore: Int,
        val opponentScore: Int,
        val type: OutcomeType,
        val label: String,
        val emoji: String,
        val color: Int,
    )

    companion object {
        val OUR_CLUB = ClubId("ours")
        val OPPONENT = ClubId("opponent")
    }
}
