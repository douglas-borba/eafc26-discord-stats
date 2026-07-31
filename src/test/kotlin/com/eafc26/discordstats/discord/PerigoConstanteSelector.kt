package com.eafc26.discordstats.discord

import com.eafc26.discordstats.ea.model.PlayerEntry
import org.slf4j.LoggerFactory

/**
 * @deprecated Replaced by [OffensiveNarrativeEvaluator].
 *
 * This class is retained only so that existing test references compile
 * while the migration is completed. New code must use
 * [OffensiveNarrativeEvaluator.evaluate] instead.
 */
@Deprecated(
    message = "Use OffensiveNarrativeEvaluator instead.",
    replaceWith = ReplaceWith("OffensiveNarrativeEvaluator"),
)
object PerigoConstanteSelector {

    private val log = LoggerFactory.getLogger(PerigoConstanteSelector::class.java)

    /** Delegates to [OffensiveNarrativeEvaluator.MIN_SHOTS]. */
    @Deprecated("Use OffensiveNarrativeEvaluator.MIN_SHOTS")
    const val MIN_SHOTS = OffensiveNarrativeEvaluator.MIN_SHOTS

    data class PerigoConstanteSelection(
        val player: PlayerEntry,
        val shots: Int,
        val goals: Int,
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

        log.debug("[PERIGO] WINNER '{}': shots={} goals={}", best.first.playerName, shots, goals)

        return PerigoConstanteSelection(
            player = best.first,
            shots = shots,
            goals = goals,
        )
    }
}
