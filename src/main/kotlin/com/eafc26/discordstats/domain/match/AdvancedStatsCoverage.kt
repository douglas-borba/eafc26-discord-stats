package com.eafc26.discordstats.domain.match

/**
 * Availability of the EA sparse event aggregates for one player appearance.
 *
 * This is deliberately separate from [AdvancedPlayerStats]: absent aggregates
 * must remain unavailable rather than being interpreted as factual zeroes.
 */
enum class AdvancedStatsCoverage {
    UNAVAILABLE,
    PARTIAL,
    FULL,
}
