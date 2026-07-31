package com.eafc26.discordstats.domain.match

data class PassingStats(
    val attempted: Int?,
    val completed: Int?,
) {
    init {
        requireNonNegative("passes attempted", attempted)
        requireNonNegative("passes completed", completed)
        require(attempted == null || completed == null || completed <= attempted) {
            "Completed passes must not exceed attempted passes"
        }
    }

    val missed: Int?
        get() = if (attempted != null && completed != null) attempted - completed else null

    val accuracy: Ratio?
        get() = if (attempted != null && completed != null && attempted > 0) {
            Ratio(completed, attempted)
        } else {
            null
        }
}
