package com.eafc26.discordstats.discord

import com.eafc26.discordstats.ea.model.PlayerEntry
import org.slf4j.LoggerFactory

/**
 * Selects the player who received a red card, if any.
 *
 * ## Eligibility
 * Any player with [redCards] > 0 is eligible.
 *
 * ## Selection (when multiple players qualify)
 * Priority:
 * 1. rating ASC    — lower-rated player wins (weaker performer is more notable)
 * 2. playerName DESC — deterministic final tiebreaker (latest alphabetically wins)
 *
 * `redCards` is used only for **eligibility** (redCards > 0), not for ranking.
 * In practice every eligible player has exactly one red card, so using the count
 * as a ranking criterion adds no value.
 *
 * `secondsPlayed` is deliberately **not** used. Playing time is relevant only
 * for the Bagre award. A substitute sent off after two minutes is as notable
 * as a starter sent off in extra time.
 *
 * ## Award independence
 * Receiving a red card does NOT prevent a player from receiving any other award,
 * and no other award disqualifies a player here.
 * The evaluator must be called with all outfield players — not filtered by Bagre
 * exclusion or any other positive-award gate.
 */
object RedCardEvaluator {

    private val log = LoggerFactory.getLogger(RedCardEvaluator::class.java)

    /**
     * The result of the red card evaluation.
     *
     * @param player The selected player.
     * @param redCards The number of red cards (usually 1; rarely 2 from two yellows).
     */
    data class RedCardSelection(
        val player: PlayerEntry,
        val redCards: Int,
    )

    /**
     * Returns the most notable red-card player, or null if nobody was sent off.
     *
     * @param players All eligible outfield players. No upstream exclusion expected.
     */
    fun evaluate(players: Collection<PlayerEntry>): RedCardSelection? {
        log.debug("[RED_CARD] Evaluating {} player(s)", players.size)

        val candidates = players.mapNotNull { player ->
            val name     = player.playerName ?: "(unknown)"
            val redCards = player.redCards?.toIntOrNull() ?: 0
            if (redCards <= 0) return@mapNotNull null
            log.debug("[RED_CARD] CANDIDATE '{}': redCards={}", name, redCards)
            player to redCards
        }

        if (candidates.isEmpty()) {
            log.debug("[RED_CARD] No red cards in this match — returning null")
            return null
        }

        // maxWithOrNull semantics:
        //   compareByDescending { rating }   → lower rating ranks higher (rating ASC) ✓
        //   thenBy { playerName }            → later name alphabetically ranks higher (playerName DESC) ✓
        //   null rating → Double.MAX_VALUE   → ranks lowest in descending order (not preferred) ✓
        val best = candidates.maxWithOrNull(
            compareByDescending<Pair<PlayerEntry, Int>> { it.first.rating?.toDoubleOrNull() ?: Double.MAX_VALUE }
                .thenBy { it.first.playerName ?: "" }
        ) ?: return null

        log.debug("[RED_CARD] SELECTED '{}': redCards={} rating={}",
            best.first.playerName, best.second, best.first.rating)

        return RedCardSelection(player = best.first, redCards = best.second)
    }
}

