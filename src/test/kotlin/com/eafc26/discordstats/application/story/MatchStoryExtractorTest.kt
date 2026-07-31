package com.eafc26.discordstats.application.story

import com.eafc26.discordstats.application.interpretation.MatchInterpreter
import com.eafc26.discordstats.application.interpretation.awardPlayer
import com.eafc26.discordstats.domain.interpretation.AwardType
import com.eafc26.discordstats.domain.interpretation.MatchOutcome
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
import com.eafc26.discordstats.domain.story.StoryContent
import com.eafc26.discordstats.domain.story.StoryPriority
import com.eafc26.discordstats.domain.story.StoryType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class MatchStoryExtractorTest {

    private val extractor = MatchStoryExtractor()

    @Test
    fun `creates a typed outcome story from the existing result decision`() {
        val interpretation = interpretation(
            listOf(awardPlayer("player", rating = "7.0")),
            ourScore = 3,
            opponentScore = 1,
        )

        val story = extractor.extract(interpretation).stories.single {
            it.type == StoryType.MATCH_OUTCOME
        }

        assertThat(story.priority).isEqualTo(StoryPriority.PRIMARY)
        assertThat(story.narrativeKey.value).isEqualTo("match.outcome.win")
        assertThat(story.content).isEqualTo(
            StoryContent.MatchResult(
                ourClubId = OUR_CLUB,
                opponentClubId = OPPONENT,
                ourScore = Score(3),
                opponentScore = Score(1),
                outcome = MatchOutcome.WIN,
                decidedBy = interpretation.result.decidedBy,
            )
        )
        assertThat(story.provenance.rules).containsExactly(interpretation.result.rule)
        assertThat(story.provenance.evidence).isEqualTo(interpretation.result.evidence)
    }

    @Test
    fun `creates award stories without recalculating their winners`() {
        val bagre = awardPlayer("bagre", rating = "5.0")
        val positive = awardPlayer(
            "positive",
            rating = "9.0",
            tacklesCompleted = 7,
            tacklesAttempted = 10,
        )
        val interpretation = interpretation(listOf(bagre, positive))

        val awardStories = extractor.extract(interpretation).stories
            .filter { it.type == StoryType.AWARD }

        assertThat(awardStories).hasSize(3)
        assertThat(awardStories.map { (it.content as StoryContent.Award).winnerId })
            .containsExactly(
                interpretation.awards.craque.winnerId,
                interpretation.awards.bagre.winnerId,
                interpretation.awards.xerife.winnerId,
            )
        assertThat(awardStories.map { (it.content as StoryContent.Award).awardType })
            .containsExactly(AwardType.CRAQUE, AwardType.BAGRE, AwardType.XERIFE)
    }

    @Test
    fun `award story keeps the exact source rule and evidence`() {
        val bagre = awardPlayer("bagre", rating = "5.0")
        val positive = awardPlayer("positive", rating = "8.0")
        val interpretation = interpretation(listOf(bagre, positive))

        val craqueStory = extractor.extract(interpretation).stories.single {
            (it.content as? StoryContent.Award)?.awardType == AwardType.CRAQUE
        }

        assertThat(craqueStory.provenance.rules)
            .containsExactly(interpretation.awards.craque.rule)
        assertThat(craqueStory.provenance.evidence)
            .isEqualTo(interpretation.awards.craque.evidence)
        assertThat(craqueStory.involvedPlayers)
            .containsExactly(interpretation.awards.craque.winnerId)
    }

    @Test
    fun `a non-awarded decision does not become a fictional story`() {
        val goalkeeper = awardPlayer(
            "goalkeeper",
            role = PlayerRole.Goalkeeper,
        )
        val interpretation = interpretation(listOf(goalkeeper))

        val stories = extractor.extract(interpretation)

        assertThat(stories.stories).hasSize(1)
        assertThat(stories.stories.single().type).isEqualTo(StoryType.MATCH_OUTCOME)
        assertThat(interpretation.awards.craque.awarded).isFalse()
        assertThat(interpretation.awards.bagre.awarded).isFalse()
        assertThat(interpretation.awards.xerife.awarded).isFalse()
    }

    @Test
    fun `narrative keys are derived only from recorded decision types and reasons`() {
        val bagre = awardPlayer("bagre", rating = "5.0")
        val positive = awardPlayer("positive", rating = "8.0")

        val keys = extractor.extract(interpretation(listOf(bagre, positive)))
            .stories.map { it.narrativeKey.value }

        assertThat(keys).containsExactly(
            "match.outcome.win",
            "award.craque.highest_rating",
            "award.bagre.lowest_eligible_rating",
        )
    }

    private fun interpretation(
        players: List<PlayerMatchPerformance>,
        ourScore: Int = 1,
        opponentScore: Int = 0,
    ) = MatchInterpreter().interpret(
        FootballMatch(
            id = MatchId("phase-6-match"),
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

    companion object {
        val OUR_CLUB = ClubId("ours")
        val OPPONENT = ClubId("opponent")
    }
}
