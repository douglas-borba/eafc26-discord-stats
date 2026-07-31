package com.eafc26.discordstats.domain.match

data class DisciplineStats(
    val redCards: Int?,
) {
    init {
        requireNonNegative("red cards", redCards)
    }
}
