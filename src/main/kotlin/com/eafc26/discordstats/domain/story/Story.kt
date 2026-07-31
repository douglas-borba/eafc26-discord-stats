package com.eafc26.discordstats.domain.story

import com.eafc26.discordstats.domain.interpretation.AwardDecisionReason
import com.eafc26.discordstats.domain.interpretation.AwardType
import com.eafc26.discordstats.domain.interpretation.AwardMetrics
import com.eafc26.discordstats.domain.interpretation.DecisionEvidence
import com.eafc26.discordstats.domain.interpretation.MatchOutcome
import com.eafc26.discordstats.domain.interpretation.GoalkeeperArchetype
import com.eafc26.discordstats.domain.interpretation.GoalkeeperNarrativeVariant
import com.eafc26.discordstats.domain.interpretation.BagreCriticism
import com.eafc26.discordstats.domain.interpretation.AccuracySummary
import com.eafc26.discordstats.domain.interpretation.OffensiveNarrativeCategory
import com.eafc26.discordstats.domain.interpretation.PlayerContribution
import com.eafc26.discordstats.domain.interpretation.RatedHighlight
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
    GOALS,
    ASSISTS,
    HIGHLIGHTS,
    BAGRE_PERFORMANCE,
    OFFENSIVE_NARRATIVE,
    RED_CARD,
    PASS_PRECISION,
    LOST_MAIL,
    GOALKEEPER,
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
        val metrics: AwardMetrics?,
    ) : StoryContent

    data class Contributions(
        val players: List<PlayerContribution>,
    ) : StoryContent

    data class Highlights(
        val players: List<RatedHighlight>,
        val teamAverageRating: java.math.BigDecimal?,
    ) : StoryContent

    data class BagrePerformance(
        val playerId: PlayerId,
        val rating: java.math.BigDecimal,
        val criticism: BagreCriticism,
        val tackleSummary: AccuracySummary?,
        val passingSummary: AccuracySummary?,
    ) : StoryContent

    data class OffensiveNarrative(
        val playerId: PlayerId,
        val shots: Int,
        val goals: Int,
        val category: OffensiveNarrativeCategory,
    ) : StoryContent

    data class RedCard(
        val playerId: PlayerId,
        val redCards: Int,
    ) : StoryContent

    data class PassPrecision(
        val playerId: PlayerId,
        val completed: Int,
        val attempted: Int,
        val accuracyPercent: Int,
    ) : StoryContent

    data class LostMail(
        val playerId: PlayerId,
        val completed: Int,
        val attempted: Int,
        val playerAccuracyPercent: Int,
        val teamAccuracyPercent: Int,
        val deltaPercent: Int,
    ) : StoryContent

    data class Goalkeeper(
        val playerId: PlayerId,
        val saves: Int,
        val goalsConceded: Int,
        val archetype: GoalkeeperArchetype,
        val narrativeVariant: GoalkeeperNarrativeVariant,
    ) : StoryContent
}

data class StoryProvenance(
    val rules: List<RuleReference>,
    val evidence: List<DecisionEvidence>,
)
