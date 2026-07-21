package com.eafc26.discordstats.discord

import com.eafc26.discordstats.ea.model.PlayerEntry
import org.slf4j.LoggerFactory

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

    private val log = LoggerFactory.getLogger(PerigoConstanteSelector::class.java)

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
        log.debug("[PERIGO] Evaluating {} outfield player(s)", outfield.size)

        val candidates = outfield.mapNotNull { player ->
            val name = player.playerName ?: "(unknown)"
            val shots = player.shots?.toIntOrNull()
            if (shots == null) {
                log.debug("[PERIGO] SKIP '{}': shots is null/non-numeric (raw='{}')", name, player.shots)
                return@mapNotNull null
            }
            if (shots < MIN_SHOTS) {
                log.debug("[PERIGO] SKIP '{}': shots={} < MIN={}", name, shots, MIN_SHOTS)
                return@mapNotNull null
            }
            val goals = player.goals?.toIntOrNull() ?: 0
            log.debug("[PERIGO] CANDIDATE '{}': shots={} goals={}", name, shots, goals)
            Triple(player, shots, goals)
        }

        if (candidates.isEmpty()) {
            log.debug("[PERIGO] No candidates passed eligibility — returning null")
            return null
        }

        val best = candidates.maxWithOrNull(
            compareBy<Triple<PlayerEntry, Int, Int>> { it.second } // shots
                .thenBy { it.third } // goals as tiebreaker
                .thenByDescending { it.first.playerName ?: "" } // name as final tiebreaker
        ) ?: return null

        val shots = best.second
        val goals = best.third
        val efficient = shots > 0 && goals.toDouble() / shots >= 0.75

        log.debug("[PERIGO] WINNER '{}': shots={} goals={} efficient={}", best.first.playerName, shots, goals, efficient)

        return PerigoConstanteSelection(
            player = best.first,
            shots = shots,
            goals = goals,
            efficient = efficient,
        )
    }
}

