package com.eafc26.discordstats.domain.story

import com.eafc26.discordstats.domain.interpretation.AwardDecisionReason
import com.eafc26.discordstats.domain.interpretation.AwardType
import com.eafc26.discordstats.domain.interpretation.DecisionEvidence
import com.eafc26.discordstats.domain.interpretation.MatchOutcome
import com.eafc26.discordstats.domain.interpretation.ResultDecisionSource
import com.eafc26.discordstats.domain.interpretation.RuleReference
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.PlayerId
import com.eafc26.discordstats.domain.match.Score

data class Story(
    val type: StoryType,
    val priority: StoryPriority,
    val involvedPlayers: Set<PlayerId>,
    val narrativeKey: NarrativeKey,
    val content: StoryContent,
    val provenance: StoryProvenance,
) {
    init {
        require(provenance.rules.isNotEmpty()) { "A story must reference its source rule" }
        require(provenance.evidence.isNotEmpty()) { "A story must contain decision evidence" }
    }
}

enum class StoryType {
    MATCH_OUTCOME,
    AWARD,
}

enum class StoryPriority {
    PRIMARY,
    SECONDARY,
}

@JvmInline
value class NarrativeKey(val value: String) {
    init {
        require(value.matches(Regex("[a-z0-9]+(?:[._-][a-z0-9]+)*"))) {
            "NarrativeKey must contain lowercase semantic segments"
        }
    }
}

sealed interface StoryContent {
    data class MatchResult(
        val ourClubId: ClubId,
        val opponentClubId: ClubId,
        val ourScore: Score,
        val opponentScore: Score,
        val outcome: MatchOutcome,
        val decidedBy: ResultDecisionSource,
    ) : StoryContent

    data class Award(
        val awardType: AwardType,
        val winnerId: PlayerId,
        val reason: AwardDecisionReason,
    ) : StoryContent
}

data class StoryProvenance(
    val rules: List<RuleReference>,
    val evidence: List<DecisionEvidence>,
)
