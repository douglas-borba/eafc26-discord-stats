package com.eafc26.discordstats.discord

import com.eafc26.discordstats.ea.model.PlayerEntry

/**
 * Selects the "Passe de Precisão" highlight - the player with the best passing accuracy.
 *
 * Requirements:
 * - Must have attempted at least a minimum number of passes to avoid misleading samples
 * - Selection is by highest pass completion percentage
 *
 * Disconnected or AI-replaced players are excluded via the eligibility filter
 * applied before calling this selector.
 */
object PassePrecisaoSelector {

    /** Minimum pass attempts required to be considered */
    const val MIN_PASS_ATTEMPTS = 10

    data class PassePrecisaoSelection(
        val player: PlayerEntry,
        val passesMade: Int,
        val passAttempts: Int,
        val accuracy: Int,
    )

    /**
     * Selects the player with best passing accuracy.
     * Returns null if no player meets the minimum attempt threshold.
     */
    fun select(outfield: Collection<PlayerEntry>): PassePrecisaoSelection? {
        val candidates = outfield.mapNotNull { player ->
            val attempts = player.passAttempts?.toIntOrNull() ?: return@mapNotNull null
            if (attempts < MIN_PASS_ATTEMPTS) return@mapNotNull null
            val made = player.passesMade?.toIntOrNull() ?: 0
            val accuracy = made * 100 / attempts

            Triple(player, Pair(made, attempts), accuracy)
        }

        val best = candidates.maxWithOrNull(
            compareBy<Triple<PlayerEntry, Pair<Int, Int>, Int>> { it.third } // accuracy
                .thenBy { it.second.second } // more attempts as tiebreaker
        ) ?: return null

        return PassePrecisaoSelection(
            player = best.first,
            passesMade = best.second.first,
            passAttempts = best.second.second,
            accuracy = best.third,
        )
    }
}

