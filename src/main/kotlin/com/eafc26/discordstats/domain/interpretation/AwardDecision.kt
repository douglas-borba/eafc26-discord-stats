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
        /** Legacy persisted field. It is no longer used to select or classify a recognition. */
        val severity: Int,
        val criticism: BagreCriticism,
        val tackleSummary: AccuracySummary?,
        val passingSummary: AccuracySummary?,
        val recognition: NegativeRecognition = NegativeRecognition.BAGRE,
        val peerAverageRating: BigDecimal? = null,
        val ratingDeficit: BigDecimal? = null,
        val peerAveragePassErrors: BigDecimal? = null,
    ) : AwardMetrics
}

/**
 * The single negative-recognition slot is still technically represented by
 * [AwardType.BAGRE] for compatibility with the canonical award model. This
 * value determines the product-facing recognition shown for a new match.
 */
enum class NegativeRecognition {
    BAGRE,
    LOW_PERFORMANCE,
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
