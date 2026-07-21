package com.eafc26.discordstats.discord

import com.eafc26.discordstats.ea.model.PlayerEntry
import kotlin.math.sqrt

/**
 * Selects the Xerife da Partida (best defensive player).
 *
 * ## Defensive Impact Score (DIS)
 *
 *   DIS = tacklesMade × successRateFraction × timeWeight
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
 *   - **timeWeight** = √(min(secondsPlayed, FULL_MATCH_SECONDS) / FULL_MATCH_SECONDS)
 *     Square-root weighting applies diminishing returns to playing time, which
 *     produces a fairer balance than a simple linear multiplier:
 *
 *       | Minutes played | Linear weight | √ weight |
 *       |----------------|--------------|----------|
 *       | 90 (full game) |  1.00        |  1.00    |
 *       | 45 (half)      |  0.50        |  0.71    |
 *       | 20 (late sub)  |  0.22        |  0.47    |
 *       | 10             |  0.11        |  0.33    |
 *
 *     With a linear weight a substitute who played 20 minutes would need to be
 *     ~4.5× as efficient as a full-game starter to beat them — which is
 *     unrealistically demanding.  The √ curve requires only ~2× improvement per
 *     minute, which is both achievable and intuitively fair.
 *
 * ## Eligibility
 *   - tackleAttempts >= 1          — must have engaged defensively at all.
 *   - secondsPlayed >= [MIN_SECONDS_PLAYED] — at least ~10 % of the match
 *     to prevent a single-minute appearance from competing for the award.
 *
 * ## Tiebreaker
 *   Equal DIS → higher successRate → more tacklesMade.
 *
 * Disconnected or AI-replaced players are excluded via the eligibility filter
 * applied before calling this selector.
 */
object XerifeSelector {

    /** Duration of a full 90-minute match in seconds. */
    const val FULL_MATCH_SECONDS = 5400

    /**
     * Minimum seconds played to be eligible (~10 % of 90 min ≈ 9 minutes).
     * Prevents a micro-substitute from competing for the award.
     */
    const val MIN_SECONDS_PLAYED = 540

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

        val candidates = outfield.mapNotNull { player ->
            val attempts = player.tackleAttempts?.toIntOrNull() ?: return@mapNotNull null
            if (attempts <= 0) return@mapNotNull null

            val seconds = player.secondsPlayed?.toIntOrNull() ?: 0
            if (seconds < MIN_SECONDS_PLAYED) return@mapNotNull null

            val made = player.tacklesMade?.toIntOrNull() ?: 0
            val successRateFraction = made.toDouble() / attempts
            val timeRatio = minOf(seconds, FULL_MATCH_SECONDS).toDouble() / FULL_MATCH_SECONDS
            // √ weighting — diminishing returns on playing time (see KDoc for rationale)
            val timeWeight = sqrt(timeRatio)
            val score = made * successRateFraction * timeWeight

            val successRateInt = (successRateFraction * 100).toInt()

            Candidate(player, made, attempts, successRateInt, score)
        }

        val best = candidates.maxWithOrNull(
            compareBy<Candidate> { it.score }
                .thenBy { it.successRate }
                .thenBy { it.made }
        ) ?: return null

        return XerifeSelection(
            player = best.player,
            tacklesMade = best.made,
            tackleAttempts = best.attempts,
            successRate = best.successRate,
            defensiveScore = best.score,
        )
    }
}
