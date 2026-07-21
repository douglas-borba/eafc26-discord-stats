package com.eafc26.discordstats.discord

import com.eafc26.discordstats.ea.model.PlayerEntry
import org.slf4j.LoggerFactory

/**
 * Selects the Xerife da Partida (best defensive player).
 *
 * ## Defensive Impact Score (DIS)
 *
 *   DIS = tacklesMade × (tacklesMade / tackleAttempts)
 *       = tacklesMade² / tackleAttempts
 *
 * ### Components
 *
 *   - **tacklesMade** — raw defensive volume.
 *
 *   - **successRateFraction** = tacklesMade / tackleAttempts  (0.0 – 1.0)
 *     Multiplying by this fraction continuously penalises sloppy tackling without
 *     an arbitrary binary gate: a 40 % tackler scores half what an 80 % tackler
 *     produces at the same volume.
 *
 * Playing time is **not** considered.  In a Pro Clubs environment almost every
 * player starts and finishes the match; substitutions are extremely rare.
 * Adding a time weight adds complexity without improving the result and has
 * caused production issues (players with success rates below any old binary gate
 * were silently dropped even though they had the best DIS in the match).
 *
 * ## Eligibility
 *   - tackleAttempts >= [MIN_TACKLE_ATTEMPTS]  — must have genuinely engaged
 *     defensively to compete for the award.  The minimum of 2 prevents a player
 *     who won a single lucky tackle from winning over someone who attempted many.
 *
 * ## Tiebreaker
 *   Equal DIS → higher successRate → more tacklesMade.
 *
 * Disconnected or AI-replaced players are excluded via the eligibility filter
 * applied before calling this selector.
 */
object XerifeSelector {

    private val log = LoggerFactory.getLogger(XerifeSelector::class.java)

    /**
     * Minimum tackle attempts required to be eligible.
     * Prevents a player with a single lucky tackle from winning the award.
     */
    const val MIN_TACKLE_ATTEMPTS = 2

    data class XerifeSelection(
        val player: PlayerEntry,
        val tacklesMade: Int,
        val tackleAttempts: Int,
        val successRate: Int,
        /** Defensive Impact Score used for ranking. Exposed for transparency / testing. */
        val defensiveScore: Double,
    )

    /**
     * Selects the Xerife from eligible outfield players.
     * Returns null if no player passes the eligibility criteria.
     */
    fun select(outfield: Collection<PlayerEntry>): XerifeSelection? {
        data class Candidate(
            val player: PlayerEntry,
            val made: Int,
            val attempts: Int,
            val successRate: Int,
            val score: Double,
        )

        log.debug("[XERIFE] Evaluating {} outfield player(s)", outfield.size)

        val candidates = outfield.mapNotNull { player ->
            val name = player.playerName ?: "(unknown)"
            val attempts = player.tackleAttempts?.toIntOrNull()
            if (attempts == null) {
                log.debug("[XERIFE] SKIP '{}': tackleAttempts is null/non-numeric (raw='{}')", name, player.tackleAttempts)
                return@mapNotNull null
            }
            if (attempts < MIN_TACKLE_ATTEMPTS) {
                log.debug("[XERIFE] SKIP '{}': tackleAttempts={} < MIN={}", name, attempts, MIN_TACKLE_ATTEMPTS)
                return@mapNotNull null
            }

            val made = player.tacklesMade?.toIntOrNull() ?: 0
            val successRateFraction = made.toDouble() / attempts
            val score = made * successRateFraction
            val successRateInt = (successRateFraction * 100).toInt()

            log.debug("[XERIFE] CANDIDATE '{}': made={} att={} rate={}% DIS={:.3f}",
                name, made, attempts, successRateInt, score)

            Candidate(player, made, attempts, successRateInt, score)
        }

        if (candidates.isEmpty()) {
            log.debug("[XERIFE] No candidates passed eligibility — returning null")
            return null
        }

        val best = candidates.maxWithOrNull(
            compareBy<Candidate> { it.score }
                .thenBy { it.successRate }
                .thenBy { it.made }
        ) ?: return null

        log.debug("[XERIFE] WINNER '{}': DIS={:.3f} rate={}% made={}/{}",
            best.player.playerName, best.score, best.successRate, best.made, best.attempts)

        return XerifeSelection(
            player = best.player,
            tacklesMade = best.made,
            tackleAttempts = best.attempts,
            successRate = best.successRate,
            defensiveScore = best.score,
        )
    }
}
