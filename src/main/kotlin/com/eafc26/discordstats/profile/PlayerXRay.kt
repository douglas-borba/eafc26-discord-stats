package com.eafc26.discordstats.profile

import com.eafc26.discordstats.domain.match.AdvancedStatsCoverage
import com.eafc26.discordstats.domain.match.MatchId
import java.math.BigDecimal
import java.time.Instant

/**
 * Lightweight, deterministic read model for the selected player's X-Ray.
 *
 * It deliberately does not expose player traits: the available facts do not
 * include a reliable historical role context that would make labels such as
 * "defensive" or "creator" semantically safe across players.
 */
data class PlayerXRay(
    val currentForm: PlayerCurrentForm,
    val trend: PlayerTrend,
    val consistency: PlayerConsistency,
    val attack: PlayerAttackMetrics,
    val creation: PlayerCreationMetrics,
    val defense: PlayerDefenseMetrics,
    val advancedCoverage: PlayerAdvancedCoverage,
    val oneOnOne: PlayerOneOnOneMetrics?,
    val recognitions: PlayerRecognitions,
    val records: PlayerPersonalRecords,
    val analysis: PlayerAnalysis,
)

enum class PlayerCurrentFormState { FORMING, RECENT_ONLY, COMPARED }

data class PlayerCurrentForm(
    val state: PlayerCurrentFormState,
    val recent: PlayerFormPeriod?,
    val previous: PlayerFormPeriod?,
    val differences: PlayerFormDifferences?,
)

data class PlayerFormPeriod(
    val appearances: Int,
    val ratedAppearances: Int,
    val averageRating: BigDecimal?,
    val goals: Int,
    val assists: Int,
    val directContributions: Int,
    val goalsPerMatch: BigDecimal,
    val assistsPerMatch: BigDecimal,
    val directContributionsPerMatch: BigDecimal,
    val passAccuracy: BigDecimal?,
    val tackleEfficiency: BigDecimal?,
    val finishingConversion: BigDecimal?,
    val passesCompleted: Int,
    val passAttempts: Int,
    val tacklesCompleted: Int,
    val tackleAttempts: Int,
    val shots: Int,
)

data class PlayerFormDifferences(
    val averageRating: BigDecimal?,
    val goalsPerMatch: BigDecimal,
    val assistsPerMatch: BigDecimal,
    val directContributionsPerMatch: BigDecimal,
    /** Percentage-point difference, never relative percentage growth. */
    val passAccuracyPoints: BigDecimal?,
    val tackleEfficiencyPoints: BigDecimal?,
    val finishingConversionPoints: BigDecimal?,
)

data class PlayerAttackMetrics(
    val goals: Int,
    val goalsPerMatch: BigDecimal,
    val shots: Int,
    val shotsPerMatch: BigDecimal,
    val finishingConversion: BigDecimal?,
)

data class PlayerCreationMetrics(
    val assists: Int,
    val assistsPerMatch: BigDecimal,
    val passesAttempted: Int,
    val passesCompleted: Int,
    val passAccuracy: BigDecimal?,
    val directContributions: Int,
    val directContributionsPerMatch: BigDecimal,
)

data class PlayerDefenseMetrics(
    val tacklesAttempted: Int,
    val tacklesCompleted: Int,
    val tackleEfficiency: BigDecimal?,
    val tacklesCompletedPerMatch: BigDecimal,
)

data class PlayerAdvancedCoverage(
    val eligibleAppearances: Int,
    val fullAppearances: Int,
    val partialAppearances: Int,
    val unavailableAppearances: Int,
    val coverage: AdvancedStatsCoverage,
)

data class PlayerOneOnOneMetrics(
    val coveredAppearances: Int,
    val dribblesCompleted: Int,
    val opponentsBeaten: Int,
)

data class PlayerRecognitions(
    val craques: Int,
    val bagres: Int,
    val xerifes: Int,
    /** Complete appearances eligible for the same recognition denominator. */
    val eligibleAppearances: Int,
    val craqueRate: BigDecimal?,
    val bagreRate: BigDecimal?,
    val xerifeRate: BigDecimal?,
)

data class PlayerPersonalRecords(
    val mostGoalsInMatch: PlayerSingleMatchRecord?,
    val mostAssistsInMatch: PlayerSingleMatchRecord?,
    val mostDirectContributionsInMatch: PlayerSingleMatchRecord?,
    val scoringStreak: Int,
    val assistStreak: Int,
    val directContributionStreak: Int,
    val ratingTenMatches: Int,
)

data class PlayerSingleMatchRecord(
    val value: Int,
    val matchId: MatchId,
    val playedAt: Instant,
    val opponentClubName: String?,
)

data class PlayerAnalysis(
    val summary: String,
    /** At most one principal and one secondary strength, ordered deterministically. */
    val strengths: List<PlayerStrength>,
    val improvement: PlayerImprovementAssessment,
)

enum class PlayerStrengthCategory { OFFENSIVE_PRODUCTION, FINISHING, CREATION, PASSING, TACKLING }

enum class PlayerEvidenceUnit { RATING, PER_MATCH, PERCENTAGE, COUNT }

/**
 * The raw values supporting a conclusion. A null denominator means that the
 * measure is a per-appearance rate rather than an efficiency percentage.
 */
data class PlayerAnalysisEvidence(
    val value: BigDecimal,
    val unit: PlayerEvidenceUnit,
    val numerator: Int?,
    val denominator: Int?,
    val appearances: Int,
    val baselineValue: BigDecimal? = null,
    val delta: BigDecimal? = null,
    val baselineNumerator: Int? = null,
    val baselineDenominator: Int? = null,
    val baselineAppearances: Int? = null,
)

data class PlayerStrength(
    val category: PlayerStrengthCategory,
    val label: String,
    val evidence: PlayerAnalysisEvidence,
    val rule: String,
    val message: String,
)

enum class PlayerImprovementAssessmentState { FOUND, INSUFFICIENT_EVIDENCE }

data class PlayerImprovementAssessment(
    val state: PlayerImprovementAssessmentState,
    val opportunity: PlayerImprovementOpportunity?,
    val message: String,
)

enum class PlayerImprovementSource { RECENT_REGRESSION, STRUCTURAL_LOW_EFFICIENCY }

enum class PlayerImprovementArea {
    RATING,
    GOALS,
    ASSISTS,
    DIRECT_CONTRIBUTIONS,
    PASSING,
    TACKLING,
    FINISHING,
}

data class PlayerImprovementOpportunity(
    val source: PlayerImprovementSource,
    val area: PlayerImprovementArea,
    val label: String,
    val evidence: PlayerAnalysisEvidence,
    val rule: String,
    val message: String,
)

enum class PlayerTrendStatus { RISING, STABLE, FALLING, FORMING }

enum class PlayerTrendMetricType { RATING, GOALS_PER_MATCH, ASSISTS_PER_MATCH, DIRECT_CONTRIBUTIONS_PER_MATCH }

data class PlayerTrendMetric(
    val type: PlayerTrendMetricType,
    val recentValue: BigDecimal,
    val baselineValue: BigDecimal,
    val delta: BigDecimal,
)

data class PlayerTrend(
    val status: PlayerTrendStatus,
    val recentRating: BigDecimal?,
    val baselineRating: BigDecimal?,
    val ratingDelta: BigDecimal?,
    val metrics: List<PlayerTrendMetric>,
)

enum class PlayerConsistencyState { INSUFFICIENT_SAMPLE, AVAILABLE }

/** Factual rating distribution; it intentionally does not label a player as consistent or inconsistent. */
data class PlayerConsistency(
    val state: PlayerConsistencyState,
    val ratedAppearances: Int,
    val averageRating: BigDecimal?,
    val ratingStandardDeviation: BigDecimal?,
    val ratingsAtLeastEight: Int,
    val ratingsAtLeastEightRate: BigDecimal?,
    val ratingsAtLeastNine: Int,
    val ratingsAtLeastNineRate: BigDecimal?,
    val ratingTenMatches: Int,
)
