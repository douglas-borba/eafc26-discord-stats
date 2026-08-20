package com.eafc26.discordstats.domain.match

data class DefendingStats(
    val tacklesAttempted: Int?,
    val tacklesCompleted: Int?,
    val interceptions: Int = 0,
) {
    init {
        requireNonNegative("tackles attempted", tacklesAttempted)
        requireNonNegative("tackles completed", tacklesCompleted)
        require(interceptions >= 0) { "Interceptions must not be negative" }
        require(tacklesAttempted == null || tacklesCompleted == null || tacklesCompleted <= tacklesAttempted) {
            "Completed tackles must not exceed attempted tackles"
        }
    }

    val tackleAccuracy: Ratio?
        get() = if (tacklesAttempted != null && tacklesCompleted != null && tacklesAttempted > 0) {
            Ratio(tacklesCompleted, tacklesAttempted)
        } else {
            null
        }
}
