package com.eafc26.discordstats.domain.match

data class PlayerMatchPerformance(
    val player: PlayerIdentity,
    val role: PlayerRole,
    val participation: Participation,
    val rating: MatchRating?,
    val attacking: AttackingStats,
    val passing: PassingStats,
    val defending: DefendingStats,
    val discipline: DisciplineStats,
    val goalkeeping: GoalkeepingStats?,
    val eaRecognition: EaRecognition,
    val advanced: AdvancedPlayerStats = AdvancedPlayerStats(),
    val advancedCoverage: AdvancedStatsCoverage = AdvancedStatsCoverage.UNAVAILABLE,
    val rawEventAggregates: RawEventAggregates? = null,
    val rawUnknownFields: RawUnknownFields? = null,
)
