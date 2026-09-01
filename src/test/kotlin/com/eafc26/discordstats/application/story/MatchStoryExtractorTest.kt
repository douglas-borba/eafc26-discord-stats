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

        assertThat(stories.stories.count { it.type == StoryType.MATCH_OUTCOME }).isEqualTo(1)
        assertThat(stories.stories.filter { it.type == StoryType.AWARD }).isEmpty()
        assertThat(interpretation.awards.craque.awarded).isFalse()
        assertThat(interpretation.awards.bagre.awarded).isFalse()
        assertThat(interpretation.awards.xerife.awarded).isFalse()
    }

    @Test
    fun `narrative keys are derived only from recorded decision types and reasons`() {
        val bagre = awardPlayer("bagre", rating = "5.0")
        val positive = awardPlayer("positive", rating = "8.0")

        val keys = extractor.extract(interpretation(listOf(bagre, positive)))
            .stories
            .filter { it.type == StoryType.MATCH_OUTCOME || it.type == StoryType.AWARD }
            .map { it.narrativeKey.value }

        assertThat(keys).containsExactly(
            "match.outcome.win",
            "award.craque.highest_rating",
            "award.bagre.qualified_negative_performance",
        )
    }

    @Test
    fun `rich interpretation exposes every story family required by current consumers`() {
        val bagre = awardPlayer(
            "bagre",
            rating = "5.0",
            passesCompleted = 2,
            passesAttempted = 10,
            tacklesCompleted = 1,
            tacklesAttempted = 5,
        )
        val star = awardPlayer(
            "star",
            rating = "9.0",
            goals = 2,
            assists = 1,
            shots = 6,
            passesCompleted = 10,
            passesAttempted = 10,
            tacklesCompleted = 4,
            tacklesAttempted = 5,
            secondAssists = 2,
            throughPasses = 8,
            dribblesCompleted = 7,
            beats = 3,
            redCards = 1,
        )
        val goalkeeper = awardPlayer(
            "keeper",
            rating = "8.5",
            role = PlayerRole.Goalkeeper,
            saves = 5,
            goalsConceded = 1,
            reflexSaves = 3,
        )

        val stories = extractor.extract(
            interpretation(listOf(bagre, star, goalkeeper), ourScore = 3, opponentScore = 1)
        )

        assertThat(stories.stories.map { it.type }.toSet()).contains(
            StoryType.MATCH_OUTCOME,
            StoryType.AWARD,
            StoryType.GOALS,
            StoryType.ASSISTS,
            StoryType.HIGHLIGHTS,
            StoryType.BAGRE_PERFORMANCE,
            StoryType.OFFENSIVE_NARRATIVE,
            StoryType.BEHIND_THE_PLAY,
            StoryType.ONE_ON_ONE,
            StoryType.RED_CARD,
            StoryType.PASS_PRECISION,
            StoryType.LOST_MAIL,
            StoryType.GOALKEEPER,
        )
        assertThat(stories.stories).allMatch {
            it.provenance.rules.isNotEmpty() && it.provenance.evidence.isNotEmpty()
        }
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
