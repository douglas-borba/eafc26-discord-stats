package com.eafc26.discordstats.domain.interpretation

import com.eafc26.discordstats.domain.match.PlayerId
import java.math.BigDecimal

data class MatchAwards(
    val craque: AwardDecision,
    val bagre: AwardDecision,
    val xerife: AwardDecision,
)

data class AwardDecision(
    val type: AwardType,
    val winnerId: PlayerId?,
    val reason: AwardDecisionReason,
    val rule: RuleReference,
    val evidence: List<DecisionEvidence>,
    val metrics: AwardMetrics? = null,
) {
    init {
        require(evidence.isNotEmpty()) { "An award decision must contain evidence" }
        require((winnerId == null) == (reason in NO_AWARD_REASONS)) {
            "Award winner and decision reason are inconsistent"
        }
    }

    val awarded: Boolean
        get() = winnerId != null

    private companion object {
        val NO_AWARD_REASONS = setOf(
            AwardDecisionReason.NO_ELIGIBLE_CANDIDATE,
            AwardDecisionReason.NO_QUALIFIED_CANDIDATE,
        )
    }
}

sealed interface AwardMetrics {
    data class Craque(
        val rating: BigDecimal?,
        val goals: Int,
        val assists: Int,
        val eaManOfTheMatch: Boolean,
    ) : AwardMetrics

    data class Xerife(
        val tacklesCompleted: Int,
        val tacklesAttempted: Int,
        val accuracyPercent: Int,
        val defensiveImpactScore: BigDecimal,
        val interceptions: Int = 0,
    ) : AwardMetrics

    data class Bagre(
        val rating: BigDecimal,
        val severity: Int,
        val criticism: BagreCriticism,
        val tackleSummary: AccuracySummary?,
        val passingSummary: AccuracySummary?,
    ) : AwardMetrics
}

enum class AwardType {
    CRAQUE,
    BAGRE,
    XERIFE,
}

enum class AwardDecisionReason {
    EA_MAN_OF_THE_MATCH,
    HIGHEST_RATING,
    LOWEST_ELIGIBLE_RATING,
    QUALIFIED_NEGATIVE_PERFORMANCE,
    HIGHEST_DEFENSIVE_IMPACT,
    NO_ELIGIBLE_CANDIDATE,
    NO_QUALIFIED_CANDIDATE,
}
