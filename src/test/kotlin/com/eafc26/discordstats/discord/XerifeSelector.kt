package com.eafc26.discordstats.discord

import com.eafc26.discordstats.ea.model.PlayerEntry
import org.slf4j.LoggerFactory

/**
 * Selects the Xerife da Partida (best defensive player).
 *
 * ## Eligibility gates (all must pass)
 *
   *   1. tacklesMade >= [MIN_TACKLES_MADE] (4) — meaningful defensive volume
 *   2. tacklePrecision >= [MIN_TACKLE_PRECISION] (0.70) — good tackling efficiency
 *   3. redCards == 0 — red card is a hard disqualifier
 *   4. Bagre exclusion — applied upstream by MatchSummaryBuilder
 *
 *   tacklePrecision = tacklesMade.toDouble() / tackleAttempts
 *   Zero tackle attempts → not eligible (no division by zero risk).
 *
 * ## Ranking (among eligible candidates)
 *
 *   Defensive Impact Score (DIS) = tacklesMade² / tackleAttempts
 *
 *   Equal DIS → higher successRate → more tacklesMade.
 *
 * Playing time is **not** considered.
 */
object XerifeSelector {

    private val log = LoggerFactory.getLogger(XerifeSelector::class.java)

    /** Minimum successful tackles required. */
    const val MIN_TACKLES_MADE = 4

    /** Minimum tackle precision required (inclusive). */
    const val MIN_TACKLE_PRECISION = 0.70

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
     *
     * Bagre exclusion is expected to be applied by the caller before passing [outfield].
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

            // Red card is a hard disqualification
            val redCards = player.redCards?.toIntOrNull() ?: 0
            if (redCards > 0) {
                log.debug("[XERIFE] SKIP '{}': has {} red card(s)", name, redCards)
                return@mapNotNull null
            }

            val attempts = player.tackleAttempts?.toIntOrNull()
            if (attempts == null || attempts == 0) {
                log.debug("[XERIFE] SKIP '{}': tackleAttempts is null/zero (raw='{}')", name, player.tackleAttempts)
                return@mapNotNull null
            }

            val made = player.tacklesMade?.toIntOrNull() ?: 0

            // Gate 1: minimum volume
            if (made < MIN_TACKLES_MADE) {
                log.debug("[XERIFE] SKIP '{}': tacklesMade={} < MIN={}", name, made, MIN_TACKLES_MADE)
                return@mapNotNull null
            }

            // Gate 2: minimum precision (no rounding before comparison)
            val precision = made.toDouble() / attempts
            if (precision < MIN_TACKLE_PRECISION) {
                log.debug("[XERIFE] SKIP '{}': precision={:.3f} < MIN={}", name, precision, MIN_TACKLE_PRECISION)
                return@mapNotNull null
            }

            val score = made.toDouble() * made.toDouble() / attempts.toDouble()
            val successRateInt = (precision * 100).toInt()

            log.debug("[XERIFE] CANDIDATE '{}': made={} att={} precision={:.3f} DIS={:.3f}",
                name, made, attempts, precision, score)

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
