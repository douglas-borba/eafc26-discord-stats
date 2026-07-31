package com.eafc26.discordstats.domain.match

data class GoalkeepingStats(
    val saves: Int?,
    val goalsConceded: Int?,
    val cleanSheetAsGoalkeeper: Boolean?,
    val cleanSheetAsAny: Boolean?,
    val saveBreakdown: SaveBreakdown,
) {
    init {
        requireNonNegative("saves", saves)
        requireNonNegative("goals conceded", goalsConceded)
    }
}

data class SaveBreakdown(
    val goodDirection: Int?,
    val reflex: Int?,
    val parry: Int?,
    val punch: Int?,
    val diving: Int?,
    val crosses: Int?,
) {
    init {
        requireNonNegative("good direction saves", goodDirection)
        requireNonNegative("reflex saves", reflex)
        requireNonNegative("parry saves", parry)
        requireNonNegative("punch saves", punch)
        requireNonNegative("diving saves", diving)
        requireNonNegative("cross saves", crosses)
    }
}
