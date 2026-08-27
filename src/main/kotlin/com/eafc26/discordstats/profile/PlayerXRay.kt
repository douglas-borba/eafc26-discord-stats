package com.eafc26.discordstats.profile

import com.eafc26.discordstats.domain.match.AdvancedStatsCoverage
import com.eafc26.discordstats.domain.match.MatchId
import java.math.BigDecimal
import java.time.Instant

/** Lightweight, deterministic read model for the selected player's X-Ray. */
data class PlayerXRay(
    val currentForm: PlayerCurrentForm,
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
    val averageRating: BigDecimal?,
    val goalsPerMatch: BigDecimal,
    val assistsPerMatch: BigDecimal,
    val directContributionsPerMatch: BigDecimal,
    val passAccuracy: BigDecimal?,
    val tackleEfficiency: BigDecimal?,
    val finishingConversion: BigDecimal?,
    val passAttempts: Int,
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
    val strengths: List<String>,
    val currentForm: String?,
    val opportunity: PlayerImprovementOpportunity?,
)

enum class PlayerImprovementArea { PASSING, TACKLING, FINISHING }

data class PlayerImprovementOpportunity(
    val area: PlayerImprovementArea,
    val differencePoints: BigDecimal,
    val message: String,
)
