package com.eafc26.discordstats.domain.story

import com.eafc26.discordstats.domain.interpretation.DecisionEvidence
import com.eafc26.discordstats.domain.interpretation.MatchOutcome
import com.eafc26.discordstats.domain.interpretation.ResultDecisionSource
import com.eafc26.discordstats.domain.interpretation.RuleId
import com.eafc26.discordstats.domain.interpretation.RuleReference
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.Score
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class StoryTest {

    @Test
    fun `narrative key rejects presentation text and invalid formatting`() {
        assertThatThrownBy { NarrativeKey("Craque da Partida!") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `story requires source rules and evidence`() {
        val content = StoryContent.MatchResult(
            ourClubId = ClubId("ours"),
            opponentClubId = ClubId("opponent"),
            ourScore = Score(1),
            opponentScore = Score(0),
            outcome = MatchOutcome.WIN,
            decidedBy = ResultDecisionSource.SCOREBOARD,
        )
        val evidence = DecisionEvidence.Scoreboard(1, 0)

        assertThatThrownBy {
            Story(
                StoryType.MATCH_OUTCOME,
                StoryPriority.PRIMARY,
                emptySet(),
                NarrativeKey("match.outcome.win"),
                content,
                StoryProvenance(emptyList(), listOf(evidence)),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)

        assertThatThrownBy {
            Story(
                StoryType.MATCH_OUTCOME,
                StoryPriority.PRIMARY,
                emptySet(),
                NarrativeKey("match.outcome.win"),
                content,
                StoryProvenance(
                    listOf(RuleReference(RuleId("match.outcome"), 1)),
                    emptyList(),
                ),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
