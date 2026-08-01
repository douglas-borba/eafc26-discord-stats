package com.eafc26.discordstats.comparison

import com.eafc26.discordstats.domain.interpretation.AwardDecisionReason
import com.eafc26.discordstats.domain.interpretation.AwardType
import com.eafc26.discordstats.domain.interpretation.MatchOutcome
import com.eafc26.discordstats.domain.match.CompetitionType
import com.eafc26.discordstats.domain.match.MatchId
import com.eafc26.discordstats.domain.match.PlayerId
import com.eafc26.discordstats.domain.story.Story
import java.math.BigDecimal
import java.time.Instant

data class MatchComparison(
    val first: ComparedMatch,
    val second: ComparedMatch,
    val differences: MatchDifferences,
)

data class ComparedMatch(
    val matchId: MatchId,
    val playedAt: Instant,
    val competition: CompetitionType?,
    val outcome: MatchOutcome,
    val ourClubName: String?,
    val opponentClubName: String?,
    val ourScore: Int,
    val opponentScore: Int,
    val statistics: ComparedStatistics,
    val awards: List<ComparedAward>,
    val stories: List<ComparedStory>,
)

data class ComparedStatistics(
    val averageRating: BigDecimal?,
    val goals: Int?,
    val assists: Int?,
    val shots: Int?,
    val passesCompleted: Int?,
    val passesAttempted: Int?,
    val passAccuracyPercent: BigDecimal?,
    val tacklesCompleted: Int?,
    val tacklesAttempted: Int?,
    val redCards: Int?,
    val goalkeeperSaves: Int?,
    /**
     * EA possession is not present in CanonicalMatch schema v1.
     */
    val possessionPercent: BigDecimal? = null,
)

data class ComparedAward(
    val type: AwardType,
    val winnerId: PlayerId?,
    val winnerName: String?,
    val reason: AwardDecisionReason,
)

data class ComparedStory(
    val story: Story,
    val involvedPlayerNames: Map<PlayerId, String>,
)

data class MatchDifferences(
    val numeric: List<NumericMatchDifference>,
    val awards: List<AwardMatchDifference>,
    val stories: List<StoryMatchDifference>,
)

data class NumericMatchDifference(
    val metric: ComparisonMetric,
    val firstValue: BigDecimal?,
    val secondValue: BigDecimal?,
    /**
     * secondValue - firstValue; null when either side is unavailable.
     */
    val delta: BigDecimal?,
    val unit: ComparisonUnit,
)

data class AwardMatchDifference(
    val awardType: AwardType,
    val firstWinnerId: PlayerId?,
    val firstWinnerName: String?,
    val secondWinnerId: PlayerId?,
    val secondWinnerName: String?,
    val changed: Boolean,
)

data class StoryMatchDifference(
    val storyType: com.eafc26.discordstats.domain.story.StoryType,
    val firstCount: Int,
    val secondCount: Int,
    val delta: Int,
)

enum class ComparisonMetric {
    GOALS_SCORED,
    GOALS_CONCEDED,
    TEAM_AVERAGE_RATING,
    TEAM_GOALS,
    TEAM_ASSISTS,
    SHOTS,
    PASSES_COMPLETED,
    PASSES_ATTEMPTED,
    PASS_ACCURACY_PERCENT,
    TACKLES_COMPLETED,
    TACKLES_ATTEMPTED,
    RED_CARDS,
    GOALKEEPER_SAVES,
    POSSESSION_PERCENT,
}

enum class ComparisonUnit {
    COUNT,
    RATING,
    PERCENTAGE_POINTS,
}

data class MatchComparisonOption(
    val matchId: MatchId,
    val playedAt: Instant,
    val outcome: MatchOutcome,
    val ourClubName: String?,
    val opponentClubName: String?,
    val ourScore: Int,
    val opponentScore: Int,
)

sealed interface MatchComparisonResult {
    data class Success(val comparison: MatchComparison) : MatchComparisonResult
    data class NotFound(val missingMatchIds: Set<MatchId>) : MatchComparisonResult
}
