package com.eafc26.discordstats.domain.match

data class AttackingStats(
    val goals: Int?,
    val assists: Int?,
    val shots: Int?,
) {
    init {
        requireNonNegative("goals", goals)
        requireNonNegative("assists", assists)
        requireNonNegative("shots", shots)
    }
}

internal fun requireNonNegative(name: String, value: Int?) {
    require(value == null || value >= 0) { "$name must not be negative" }
}
