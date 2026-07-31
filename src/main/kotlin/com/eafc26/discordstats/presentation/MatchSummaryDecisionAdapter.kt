package com.eafc26.discordstats.presentation

import com.eafc26.discordstats.domain.interpretation.AwardType
import com.eafc26.discordstats.domain.interpretation.MatchInterpretation
import com.eafc26.discordstats.domain.interpretation.MatchOutcome as DomainMatchOutcome
import com.eafc26.discordstats.domain.story.MatchStories
import com.eafc26.discordstats.domain.story.StoryContent
import com.eafc26.discordstats.domain.story.StoryType

/**
 * Projects interpreted decisions into the types expected by the legacy
 * presentation boundary. It renders decisions; it does not make them.
 */
class MatchSummaryDecisionAdapter {

    fun adapt(
        interpretation: MatchInterpretation,
        stories: MatchStories,
    ): MatchSummaryDecisionProjection {
        require(stories.matchId == interpretation.matchId) {
            "Stories and interpretation must belong to the same match"
        }

        val outcomeStory = stories.stories.single { it.type == StoryType.MATCH_OUTCOME }
        val outcomeContent = outcomeStory.content as StoryContent.MatchResult
        require(outcomeContent.ourClubId == interpretation.perspectiveClubId) {
            "Outcome story must use the interpretation perspective"
        }

        val awardStories = stories.stories.mapNotNull { story ->
            val content = story.content as? StoryContent.Award ?: return@mapNotNull null
            AwardPresentationDecision(
                type = content.awardType,
                winnerId = content.winnerId,
                narrativeKey = story.narrativeKey,
            )
        }
        require(awardStories.map { it.type }.distinct().size == awardStories.size) {
            "Only one story per award type can be presented"
        }
        validateAwardParity(interpretation, awardStories)

        return MatchSummaryDecisionProjection(
            matchId = interpretation.matchId,
            ourScore = outcomeContent.ourScore.goals,
            opponentScore = outcomeContent.opponentScore.goals,
            outcome = outcomeContent.outcome.toPresentationOutcome(),
            awards = awardStories,
            trace = PresentationDecisionTrace(
                rules = stories.stories.flatMap { it.provenance.rules }.distinct(),
                evidence = stories.stories.flatMap { it.provenance.evidence },
            ),
        )
    }

    private fun validateAwardParity(
        interpretation: MatchInterpretation,
        projected: List<AwardPresentationDecision>,
    ) {
        val decisions = mapOf(
            AwardType.CRAQUE to interpretation.awards.craque,
            AwardType.BAGRE to interpretation.awards.bagre,
            AwardType.XERIFE to interpretation.awards.xerife,
        )
        val projectedByType = projected.associateBy { it.type }

        decisions.forEach { (type, decision) ->
            require(projectedByType[type]?.winnerId == decision.winnerId) {
                "Award story for $type must match its interpretation decision"
            }
        }
    }

    private fun DomainMatchOutcome.toPresentationOutcome(): MatchOutcome = when (this) {
        DomainMatchOutcome.WIN -> MatchOutcome(
            emoji = "🟢",
            label = "Vitória",
            color = 0x2ECC71,
            type = OutcomeType.WIN,
        )
        DomainMatchOutcome.DRAW -> MatchOutcome(
            emoji = "🟡",
            label = "Empate",
            color = 0x95A5A6,
            type = OutcomeType.DRAW,
        )
        DomainMatchOutcome.LOSS -> MatchOutcome(
            emoji = "🔴",
            label = "Derrota",
            color = 0xE74C3C,
            type = OutcomeType.LOSS,
        )
    }
}
