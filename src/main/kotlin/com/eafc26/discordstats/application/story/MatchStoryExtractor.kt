package com.eafc26.discordstats.application.story

import com.eafc26.discordstats.domain.interpretation.AwardDecision
import com.eafc26.discordstats.domain.interpretation.AwardDecisionReason
import com.eafc26.discordstats.domain.interpretation.MatchInterpretation
import com.eafc26.discordstats.domain.story.MatchStories
import com.eafc26.discordstats.domain.story.NarrativeKey
import com.eafc26.discordstats.domain.story.Story
import com.eafc26.discordstats.domain.story.StoryContent
import com.eafc26.discordstats.domain.story.StoryPriority
import com.eafc26.discordstats.domain.story.StoryProvenance
import com.eafc26.discordstats.domain.story.StoryType

/**
 * Projects already-made decisions into presentation-neutral story records.
 * It does not select winners or re-evaluate football statistics.
 */
class MatchStoryExtractor {

    fun extract(interpretation: MatchInterpretation): MatchStories {
        val result = interpretation.result
        val outcomeStory = Story(
            type = StoryType.MATCH_OUTCOME,
            priority = StoryPriority.PRIMARY,
            involvedPlayers = emptySet(),
            narrativeKey = NarrativeKey("match.outcome.${result.outcome.name.lowercase()}"),
            content = StoryContent.MatchResult(
                ourClubId = result.ourClub,
                opponentClubId = result.opponentClub,
                ourScore = result.ourScore,
                opponentScore = result.opponentScore,
                outcome = result.outcome,
                decidedBy = result.decidedBy,
            ),
            provenance = StoryProvenance(
                rules = listOf(result.rule),
                evidence = result.evidence,
            ),
        )

        val awardStories = listOf(
            interpretation.awards.craque,
            interpretation.awards.bagre,
            interpretation.awards.xerife,
        ).mapNotNull(::awardStory)

        return MatchStories(
            matchId = interpretation.matchId,
            stories = listOf(outcomeStory) + awardStories,
        )
    }

    private fun awardStory(decision: AwardDecision): Story? {
        val winnerId = decision.winnerId ?: return null
        require(decision.reason != AwardDecisionReason.NO_ELIGIBLE_CANDIDATE)

        return Story(
            type = StoryType.AWARD,
            priority = StoryPriority.SECONDARY,
            involvedPlayers = setOf(winnerId),
            narrativeKey = NarrativeKey(
                "award.${decision.type.name.lowercase()}.${decision.reason.name.lowercase()}"
            ),
            content = StoryContent.Award(
                awardType = decision.type,
                winnerId = winnerId,
                reason = decision.reason,
            ),
            provenance = StoryProvenance(
                rules = listOf(decision.rule),
                evidence = decision.evidence,
            ),
        )
    }
}
