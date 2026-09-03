package com.eafc26.discordstats.domain.match

/**
 * Advanced per-player facts decoded from EA's sparse event aggregates.
 *
 * The opaque EA event codes intentionally do not escape the anti-corruption
 * layer. Values are zero when the corresponding event code is absent.
 */
data class AdvancedPlayerStats(
    val secondAssists: Int = 0,
    val throughPasses: Int = 0,
    /**
     * Legacy typed transport of aggregate_0[174]. The name is retained only for
     * canonical compatibility; it must not be treated as a football semantic.
     */
    val dribblesCompleted: Int = 0,
    val beats: Int = 0,
    val interceptions: Int = 0,
) {
    init {
        listOf(secondAssists, throughPasses, dribblesCompleted, beats, interceptions).forEach {
            require(it >= 0) { "Advanced player statistics must not be negative" }
        }
    }
}
