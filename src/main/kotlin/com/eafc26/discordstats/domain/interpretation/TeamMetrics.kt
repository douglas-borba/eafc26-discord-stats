package com.eafc26.discordstats.domain.interpretation

import com.eafc26.discordstats.domain.match.PassingStats
import java.math.BigDecimal

data class TeamMetrics(
    val averageRating: BigDecimal?,
    val passing: PassingStats,
    val totalGoals: Int?,
    val totalAssists: Int?,
)
