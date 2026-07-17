package com.eafc26.discordstats.discord

import com.eafc26.discordstats.ea.model.PlayerEntry

/**
 * Selects the Xerife da Partida (best defensive player).
 *
 * Requirements:
 * - Must have attempted at least one tackle
 * - Must have tackle success rate > 60% (exactly 60% does not qualify)
 * - Selection is by highest number of successful tackles, with success rate as tiebreaker
 *
 * Disconnected or AI-replaced players are excluded via the eligibility filter
 * applied before calling this selector.
 */
object XerifeSelector {

    /** Minimum tackle success rate (exclusive) to qualify as Xerife */
    const val MIN_SUCCESS_RATE = 60

    data class XerifeSelection(
        val player: PlayerEntry,
        val tacklesMade: Int,
        val tackleAttempts: Int,
        val successRate: Int,
    )

    /**
     * Selects the Xerife from eligible outfield players.
     * Returns null if no player qualifies (>60% success rate required).
     */
    fun select(outfield: Collection<PlayerEntry>): XerifeSelection? {
        val candidates = outfield.mapNotNull { player ->
            val attempts = player.tackleAttempts?.toIntOrNull() ?: return@mapNotNull null
            if (attempts <= 0) return@mapNotNull null
            val made = player.tacklesMade?.toIntOrNull() ?: 0
            val rate = made * 100 / attempts

            // Exactly 60% does not qualify - must be > 60%
            if (rate <= MIN_SUCCESS_RATE) return@mapNotNull null

            Triple(player, made, Pair(attempts, rate))
        }

        val best = candidates.maxWithOrNull(
            compareBy<Triple<PlayerEntry, Int, Pair<Int, Int>>> { it.second } // tacklesMade
                .thenBy { it.third.second } // success rate
        ) ?: return null

        return XerifeSelection(
            player = best.first,
            tacklesMade = best.second,
            tackleAttempts = best.third.first,
            successRate = best.third.second,
        )
    }
}

