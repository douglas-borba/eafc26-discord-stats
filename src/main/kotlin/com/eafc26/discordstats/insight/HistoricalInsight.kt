package com.eafc26.discordstats.insight

import com.eafc26.discordstats.domain.match.MatchId
import com.eafc26.discordstats.domain.match.PlayerId
import java.math.BigDecimal
import java.time.Instant

data class HistoricalInsightsReport(
    val sourceMatchCount: Int,
    val insights: List<HistoricalInsight>,
)

data class HistoricalInsight(
    val type: HistoricalInsightType,
    val category: HistoricalInsightCategory,
    val value: HistoricalInsightValue,
    val involvedMatchIds: List<MatchId>,
    val involvedPlayers: List<HistoricalInsightPlayer>,
    val rule: HistoricalInsightRule,
    val evidence: HistoricalInsightEvidence,
)

enum class HistoricalInsightCategory {
    CLUB,
    PLAYER,
    TEMPORAL,
}

enum class HistoricalInsightType {
    LONGEST_WINNING_STREAK,
    LONGEST_UNBEATEN_STREAK,
    LONGEST_SCORELESS_STREAK,
    LONGEST_CONCEDING_STREAK,
    BEST_TEAM_AVERAGE,
    WORST_TEAM_AVERAGE,
    MOST_CRAQUES,
    MOST_BAGRES,
    MOST_XERIFES,
    TOP_SCORER,
    ASSIST_LEADER,
    HIGHEST_PLAYER_AVERAGE,
    FIRST_WIN,
    LATEST_WIN,
    LONGEST_UNBEATEN_INTERVAL,
    BIGGEST_WIN,
}

sealed interface HistoricalInsightValue {
    data class Count(val value: Int) : HistoricalInsightValue
    data class Rating(val value: BigDecimal) : HistoricalInsightValue
    data class DurationSeconds(val value: Long) : HistoricalInsightValue
    data class Moment(val value: Instant) : HistoricalInsightValue
    data class BiggestWin(
        val margin: Int,
        val scorelines: List<HistoricalScoreline>,
    ) : HistoricalInsightValue
}

data class HistoricalScoreline(
    val matchId: MatchId,
    val ourScore: Int,
    val opponentScore: Int,
)

data class HistoricalInsightPlayer(
    val playerId: PlayerId,
    val displayName: String,
)

data class HistoricalInsightRule(
    val id: HistoricalInsightRuleId,
    val version: Int,
    val criterion: String,
    val tiePolicy: String,
) {
    init {
        require(version > 0) { "Historical insight rule version must be positive" }
    }
}

@JvmInline
value class HistoricalInsightRuleId(val value: String) {
    init {
        require(value.matches(Regex("historical\\.[a-z0-9._-]+"))) {
            "Historical insight rule ID must use the historical namespace"
        }
    }
}

data class HistoricalInsightEvidence(
    val sourceMatchCount: Int,
    val candidateCount: Int,
    val eligibleCandidateCount: Int,
    val observations: List<HistoricalInsightObservation>,
)

data class HistoricalInsightObservation(
    val subjectId: String,
    val metricValue: String,
)
