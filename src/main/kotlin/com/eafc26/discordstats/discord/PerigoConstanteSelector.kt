package com.eafc26.discordstats.discord

import com.eafc26.discordstats.ea.model.PlayerEntry

/**
 * Selects the "Perigo Constante" highlight - the player who posed the most threat to the opponent's goal.
 *
 * Requirements:
 * - Must have attempted at least 3 shots
 * - Selection is by highest number of shots
 * - Context-aware phrases based on efficiency (goals vs shots)
 *
 * Disconnected or AI-replaced players are excluded via the eligibility filter
 * applied before calling this selector.
 */
object PerigoConstanteSelector {

    /** Minimum shots required to be considered */
    const val MIN_SHOTS = 3

    data class PerigoConstanteSelection(
        val player: PlayerEntry,
        val shots: Int,
        val goals: Int,
        /** true if player was efficient (scored goals), false if mostly missed */
        val efficient: Boolean,
    )

    /**
     * Selects the player who attempted the most shots.
     * Returns null if no player meets the minimum shot threshold.
     */
    fun select(outfield: Collection<PlayerEntry>): PerigoConstanteSelection? {
        val candidates = outfield.mapNotNull { player ->
            val shots = player.shots?.toIntOrNull() ?: return@mapNotNull null
            if (shots < MIN_SHOTS) return@mapNotNull null
            val goals = player.goals?.toIntOrNull() ?: 0
            Triple(player, shots, goals)
        }

        val best = candidates.maxWithOrNull(
            compareBy<Triple<PlayerEntry, Int, Int>> { it.second } // shots
                .thenBy { it.third } // goals as tiebreaker
                .thenByDescending { it.first.playerName ?: "" } // name as final tiebreaker
        ) ?: return null

        val shots = best.second
        val goals = best.third
        // Consider efficient if scored at least 1 goal per 3 shots, or at least 2 goals
        val efficient = goals >= 2 || (shots > 0 && goals.toDouble() / shots >= 0.33)

        return PerigoConstanteSelection(
            player = best.first,
            shots = shots,
            goals = goals,
            efficient = efficient,
        )
    }
}

