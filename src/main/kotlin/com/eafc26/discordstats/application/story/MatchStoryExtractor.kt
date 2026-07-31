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

        val featureStories = featureStories(interpretation)

        return MatchStories(
            matchId = interpretation.matchId,
            stories = listOf(outcomeStory) + awardStories + featureStories,
        )
    }

    private fun featureStories(interpretation: MatchInterpretation): List<Story> {
        val features = interpretation.features
        return buildList {
            if (features.contributions.goalScorers.isNotEmpty()) {
                add(
                    featureStory(
                        StoryType.GOALS,
                        "match.goals",
                        features.contributions.goalScorers.mapTo(linkedSetOf()) { it.playerId },
                        StoryContent.Contributions(features.contributions.goalScorers),
                        features.contributions.rule,
                        features.contributions.evidence,
                    )
                )
            }
            if (features.contributions.assistProviders.isNotEmpty()) {
                add(
                    featureStory(
                        StoryType.ASSISTS,
                        "match.assists",
                        features.contributions.assistProviders.mapTo(linkedSetOf()) { it.playerId },
                        StoryContent.Contributions(features.contributions.assistProviders),
                        features.contributions.rule,
                        features.contributions.evidence,
                    )
                )
            }
            if (features.highlights.players.isNotEmpty() || features.highlights.teamAverageRating != null) {
                add(
                    featureStory(
                        StoryType.HIGHLIGHTS,
                        "match.highlights",
                        features.highlights.players.mapTo(linkedSetOf()) { it.playerId },
                        StoryContent.Highlights(
                            features.highlights.players,
                            features.highlights.teamAverageRating,
                        ),
                        features.highlights.rule,
                        features.highlights.evidence,
                    )
                )
            }
            features.bagrePerformance?.let {
                add(
                    featureStory(
                        StoryType.BAGRE_PERFORMANCE,
                        "bagre.${it.criticism.name.lowercase()}",
                        setOf(it.playerId),
                        StoryContent.BagrePerformance(
                            it.playerId,
                            it.rating,
                            it.criticism,
                            it.tackleSummary,
                            it.passingSummary,
                        ),
                        it.rule,
                        it.evidence,
                    )
                )
            }
            features.offensiveNarratives.forEach {
                add(
                    featureStory(
                        StoryType.OFFENSIVE_NARRATIVE,
                        "offensive.${it.category.name.lowercase()}",
                        setOf(it.playerId),
                        StoryContent.OffensiveNarrative(it.playerId, it.shots, it.goals, it.category),
                        it.rule,
                        it.evidence,
                    )
                )
            }
            features.redCard?.let {
                add(
                    featureStory(
                        StoryType.RED_CARD,
                        "discipline.red_card",
                        setOf(it.playerId),
                        StoryContent.RedCard(it.playerId, it.redCards),
                        it.rule,
                        it.evidence,
                    )
                )
            }
            features.passPrecision?.let {
                add(
                    featureStory(
                        StoryType.PASS_PRECISION,
                        "award.pass_precision",
                        setOf(it.playerId),
                        StoryContent.PassPrecision(
                            it.playerId,
                            it.completed,
                            it.attempted,
                            it.accuracyPercent,
                        ),
                        it.rule,
                        it.evidence,
                    )
                )
            }
            features.lostMail?.let {
                add(
                    featureStory(
                        StoryType.LOST_MAIL,
                        "award.lost_mail",
                        setOf(it.playerId),
                        StoryContent.LostMail(
                            it.playerId,
                            it.completed,
                            it.attempted,
                            it.playerAccuracyPercent,
                            it.teamAccuracyPercent,
                            it.deltaPercent,
                        ),
                        it.rule,
                        it.evidence,
                    )
                )
            }
            features.goalkeeper?.let {
                add(
                    featureStory(
                        StoryType.GOALKEEPER,
                        "goalkeeper.${it.archetype.name.lowercase()}.${it.narrativeVariant.name.lowercase()}",
                        setOf(it.playerId),
                        StoryContent.Goalkeeper(
                            it.playerId,
                            it.saves,
                            it.goalsConceded,
                            it.archetype,
                            it.narrativeVariant,
                        ),
                        it.rule,
                        it.evidence,
                    )
                )
            }
        }
    }

    private fun featureStory(
        type: StoryType,
        key: String,
        players: Set<com.eafc26.discordstats.domain.match.PlayerId>,
        content: StoryContent,
        rule: com.eafc26.discordstats.domain.interpretation.RuleReference,
        evidence: List<com.eafc26.discordstats.domain.interpretation.DecisionEvidence>,
    ) = Story(
        type = type,
        priority = StoryPriority.SECONDARY,
        involvedPlayers = players,
        narrativeKey = NarrativeKey(key),
        content = content,
        provenance = StoryProvenance(listOf(rule), evidence),
    )

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
