package com.eafc26.discordstats.application.interpretation

import com.eafc26.discordstats.domain.interpretation.TeamMetrics
import com.eafc26.discordstats.domain.match.PassingStats
import com.eafc26.discordstats.domain.match.PlayerMatchPerformance
import java.math.BigDecimal
import java.math.RoundingMode

class TeamMetricsCalculator {

    fun calculate(players: Collection<PlayerMatchPerformance>): TeamMetrics {
        val ratings = players.mapNotNull { it.rating?.value }
        val passingPairs = players.mapNotNull { player ->
            val attempted = player.passing.attempted
            val completed = player.passing.completed
            if (attempted != null && completed != null && attempted > 0) {
                attempted to completed
            } else {
                null
            }
        }

        return TeamMetrics(
            averageRating = ratings.averageOrNull(),
            passing = if (passingPairs.isEmpty()) {
                PassingStats(attempted = null, completed = null)
            } else {
                PassingStats(
                    attempted = passingPairs.sumOf { it.first },
                    completed = passingPairs.sumOf { it.second },
                )
            },
            totalGoals = players.mapNotNull { it.attacking.goals }.sumIfKnown(),
            totalAssists = players.mapNotNull { it.attacking.assists }.sumIfKnown(),
        )
    }

    private fun List<BigDecimal>.averageOrNull(): BigDecimal? {
        if (isEmpty()) return null
        return reduce(BigDecimal::add).divide(
            size.toBigDecimal(),
            DECIMAL_SCALE,
            RoundingMode.HALF_UP,
        )
    }

    private fun List<Int>.sumIfKnown(): Int? = if (isEmpty()) null else sum()

    companion object {
        const val DECIMAL_SCALE = 6
    }
}
