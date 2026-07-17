package com.eafc26.discordstats.discord

import com.eafc26.discordstats.ea.model.PlayerEntry

/**
 * Selects the Craque da Partida among eligible players.
 *
 * Selection priority:
 * 1. Player with manOfTheMatch = "1" (EA-designated MVP)
 * 2. Highest rating among outfield players
 * 3. Goals and assists as tiebreakers
 *
 * Disconnected or AI-replaced players are never eligible.
 */
object CraqueSelector {

    data class CraqueSelection(
        val player: PlayerEntry,
        val reason: String,
    )

    /**
     * Selects the Craque from the given eligible outfield players.
     * Returns null if no eligible player is found.
     */
    fun select(outfield: Collection<PlayerEntry>): CraqueSelection? {
        if (outfield.isEmpty()) return null

        // First priority: EA-designated MVP
        val eaMvp = outfield.firstOrNull { it.manOfTheMatch == "1" }
        if (eaMvp != null) {
            return CraqueSelection(
                player = eaMvp,
                reason = buildReason(eaMvp, isMvp = true),
            )
        }

        // Second priority: highest rated player with goals/assists as tiebreakers
        val best = outfield
            .filter { it.rating != null }
            .maxWithOrNull(
                compareBy<PlayerEntry> { it.rating?.toDoubleOrNull() ?: 0.0 }
                    .thenBy { it.goals?.toIntOrNull() ?: 0 }
                    .thenBy { it.assists?.toIntOrNull() ?: 0 }
            )

        if (best != null) {
            return CraqueSelection(
                player = best,
                reason = buildReason(best, isMvp = false),
            )
        }

        return null
    }

    private fun buildReason(player: PlayerEntry, isMvp: Boolean): String {
        val rating = player.rating?.toDoubleOrNull()?.let { "%.2f".format(it).replace('.', ',') } ?: "N/D"
        val goals = player.goals?.toIntOrNull() ?: 0
        val assists = player.assists?.toIntOrNull() ?: 0

        val parts = mutableListOf<String>()
        parts += "Nota $rating"

        if (goals > 0 || assists > 0) {
            val stats = mutableListOf<String>()
            if (goals > 0) stats += "${pluralize(goals, "gol", "gols")}"
            if (assists > 0) stats += "${pluralize(assists, "assistência", "assistências")}"
            parts += stats.joinToString(" e ")
        }

        if (isMvp) {
            parts += "Craque da Partida (EA)"
        } else {
            parts += "Melhor nota da partida"
        }

        return parts.joinToString(" • ")
    }

    private fun pluralize(count: Int, singular: String, plural: String): String =
        if (count == 1) "$count $singular" else "$count $plural"
}

