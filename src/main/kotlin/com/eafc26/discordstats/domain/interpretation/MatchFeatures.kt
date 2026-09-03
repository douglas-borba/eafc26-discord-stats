package com.eafc26.discordstats.domain.interpretation

import com.eafc26.discordstats.domain.match.PlayerId
import java.math.BigDecimal

data class MatchFeatures(
    val contributions: ContributionDecision,
    val highlights: HighlightsDecision,
    val bagrePerformance: BagrePerformanceDecision?,
    val offensiveNarratives: List<OffensiveNarrativeDecision>,
    val redCard: RedCardDecision?,
    val passPrecision: PassPrecisionDecision?,
    val lostMail: LostMailDecision?,
    val goalkeeper: GoalkeeperDecision?,
    val eaRecognizedMvp: EaRecognizedMvpDecision?,
    val evaluations: List<FeatureEvaluation>,
    val behindThePlay: BehindThePlayDecision? = null,
    val oneOnOne: OneOnOneDecision? = null,
) {
    val rules: List<RuleReference>
        get() = evaluations.map { it.rule }.distinct()

    val evidence: List<DecisionEvidence>
        get() = evaluations.flatMap { it.evidence }
}

data class FeatureEvaluation(
    val feature: MatchFeatureType,
    val produced: Boolean,
    val rule: RuleReference,
    val evidence: List<DecisionEvidence>,
) {
    init {
        require(evidence.isNotEmpty()) { "A feature evaluation must contain evidence" }
    }
}

enum class MatchFeatureType {
    CONTRIBUTIONS,
    HIGHLIGHTS,
    BAGRE_PERFORMANCE,
    OFFENSIVE_NARRATIVES,
    BEHIND_THE_PLAY,
    ONE_ON_ONE,
    RED_CARD,
    PASS_PRECISION,
    LOST_MAIL,
    GOALKEEPER,
    EA_RECOGNIZED_MVP,
}

data class ContributionDecision(
    val goalScorers: List<PlayerContribution>,
    val assistProviders: List<PlayerContribution>,
    val rule: RuleReference,
    val evidence: List<DecisionEvidence>,
)

data class PlayerContribution(val playerId: PlayerId, val goals: Int, val assists: Int)

data class HighlightsDecision(
    val players: List<RatedHighlight>,
    val unfilteredPlayers: List<RatedHighlight>,
    val teamAverageRating: BigDecimal?,
    val rule: RuleReference,
    val evidence: List<DecisionEvidence>,
)

data class EaRecognizedMvpDecision(
    val playerId: PlayerId,
    val rating: BigDecimal?,
    val rule: RuleReference,
    val evidence: List<DecisionEvidence>,
)

data class RatedHighlight(val playerId: PlayerId, val rating: BigDecimal)

data class BagrePerformanceDecision(
    val playerId: PlayerId,
    val rating: BigDecimal,
    val criticism: BagreCriticism,
    val tackleSummary: AccuracySummary?,
    val passingSummary: AccuracySummary?,
    val rule: RuleReference,
    val evidence: List<DecisionEvidence>,
    val recognition: NegativeRecognition = NegativeRecognition.BAGRE,
    val peerAverageRating: BigDecimal? = null,
    val ratingDeficit: BigDecimal? = null,
    val peerAveragePassErrors: BigDecimal? = null,
)

enum class BagreCriticism {
    TACKLING,
    PASSING,
    RATING,
}

data class AccuracySummary(
    val completed: Int,
    val attempted: Int,
    val accuracyPercent: Int,
)

data class OffensiveNarrativeDecision(
    val playerId: PlayerId,
    val shots: Int,
    val goals: Int,
    val category: OffensiveNarrativeCategory,
    val rule: RuleReference,
    val evidence: List<DecisionEvidence>,
)

/** A creative contribution before the official assist. */
data class BehindThePlayDecision(
    val playerId: PlayerId,
    val secondAssists: Int,
    val throughPasses: Int,
    val rating: BigDecimal?,
    val rule: RuleReference,
    val evidence: List<DecisionEvidence>,
)

/** A player who repeatedly beat direct opponents in one-on-one situations. */
data class OneOnOneDecision(
    val playerId: PlayerId,
    val beats: Int,
    val dribblesCompleted: Int,
    val rating: BigDecimal?,
    val rule: RuleReference,
    val evidence: List<DecisionEvidence>,
)

enum class OffensiveNarrativeCategory {
    DECISIVE,
    COULD_HAVE_DECIDED,
    FELL_SHORT,
    LACKED_COMPOSURE,
    CONSTANT_THREAT,
}

data class RedCardDecision(
    val playerId: PlayerId,
    val redCards: Int,
    val rule: RuleReference,
    val evidence: List<DecisionEvidence>,
)

data class PassPrecisionDecision(
    val playerId: PlayerId,
    val completed: Int,
    val attempted: Int,
    val accuracyPercent: Int,
    val rule: RuleReference,
    val evidence: List<DecisionEvidence>,
)

data class LostMailDecision(
    val playerId: PlayerId,
    val completed: Int,
    val attempted: Int,
    val playerAccuracyPercent: Int,
    val teamAccuracyPercent: Int,
    val deltaPercent: Int,
    val rule: RuleReference,
    val evidence: List<DecisionEvidence>,
)

data class GoalkeeperDecision(
    val playerId: PlayerId,
    val saves: Int,
    val goalsConceded: Int,
    val archetype: GoalkeeperArchetype,
    val narrativeVariant: GoalkeeperNarrativeVariant,
    val rule: RuleReference,
    val evidence: List<DecisionEvidence>,
)

enum class GoalkeeperArchetype {
    WALL,
    SOLID,
    UNDER_SIEGE,
    POOR,
    QUIET,
}

enum class GoalkeeperNarrativeVariant {
    DEFAULT,
    REFLEX,
    PARRY,
    CROSS,
    GOOD_DIRECTION,
    POOR_MILD,
    POOR_MODERATE,
    POOR_SEVERE,
}
