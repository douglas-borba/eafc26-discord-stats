package com.eafc26.discordstats.domain.match

data class DefendingStats(
    val tacklesAttempted: Int?,
    val tacklesCompleted: Int?,
) {
    init {
        requireNonNegative("tackles attempted", tacklesAttempted)
        requireNonNegative("tackles completed", tacklesCompleted)
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
