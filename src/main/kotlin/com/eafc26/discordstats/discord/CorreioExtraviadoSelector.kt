package com.eafc26.discordstats.discord

import com.eafc26.discordstats.ea.model.PlayerEntry

/**
 * Selects the "Correio Extraviado" — the player with the worst passing performance
 * in that match, using a hybrid eligibility model that accounts for sample confidence.
 *
 * ## Eligibility tiers
 *
 * **High-volume (≥ [HIGH_VOLUME_THRESHOLD] attempts) — comparative analysis**
 * - accuracy strictly below [MAX_ACCURACY_PCT]%
 * - at least [MIN_DELTA_PCT] percentage points below the team average
 *
 * **Low-volume ([MIN_PASS_ATTEMPTS]–[HIGH_VOLUME_THRESHOLD]-1 attempts) — extreme failure**
 * - accuracy at or below [LOW_VOLUME_MAX_ACCURACY_PCT]%
 * - no delta check (team average is noise at this sample size)
 *
 * **Ignored (< [MIN_PASS_ATTEMPTS] attempts)**
 * - sample too small to be meaningful
 *
 * ## Selection order (lower = "wins")
 * 1. Lowest playerAccuracyPct
 * 2. Tier (HIGH_VOLUME before LOW_VOLUME at the same accuracy — confidence weighting)
 * 3. Most missed passes (greater absolute impact)
 * 4. Name alphabetical (determinism)
 *
 * This prevents a player with 3 attempts and 0% from automatically beating a
 * high-volume player also at 0% — the larger sample is treated as more meaningful.
 * A low-volume player CAN still win when their accuracy is genuinely worse than
 * every high-volume candidate.
 */
object CorreioExtraviadoSelector {

    /** Attempts below this are always ignored — sample too small. */
    const val MIN_PASS_ATTEMPTS = 3

    /** Attempts at or above this threshold use the full comparative analysis. */
    const val HIGH_VOLUME_THRESHOLD = 10

    /** High-volume: accuracy must be strictly below this to be eligible. */
    const val MAX_ACCURACY_PCT = 75

    /** High-volume: must be at least this many pp below the team average. */
    const val MIN_DELTA_PCT = 5

    /** Low-volume: accuracy must be at or below this to qualify as extreme failure. */
    const val LOW_VOLUME_MAX_ACCURACY_PCT = 33

    /** Used internally to implement confidence-weighted tiebreaking. */
    private enum class Tier { HIGH_VOLUME, LOW_VOLUME }

    data class Selection(
        val player: PlayerEntry,
        val passesMade: Int,
        val passAttempts: Int,
        /** Player passing accuracy, 0–100 integer. */
        val playerAccuracyPct: Int,
        /** Team passing accuracy, 0–100 integer. */
        val teamAccuracyPct: Int,
        /** Percentage points below the team average (positive = worse). */
        val deltaPct: Int,
    )

    fun select(players: Collection<PlayerEntry>): Selection? {
        // 1. Team totals — all players with valid pass data, clamped against EA anomalies
        var totalAttempts = 0
        var totalMade = 0
        for (p in players) {
            val att  = p.passAttempts?.toIntOrNull() ?: continue
            val made = p.passesMade?.toIntOrNull()   ?: continue
            if (att <= 0) continue
            totalAttempts += att
            totalMade += minOf(made, att)
        }
        if (totalAttempts == 0) return null
        val teamPct = totalMade * 100 / totalAttempts

        // 2. Build candidate list applying tier-specific eligibility rules
        data class Candidate(
            val player: PlayerEntry,
            val made: Int,
            val attempts: Int,
            val playerPct: Int,
            val delta: Int,
            val missed: Int,
            val tier: Tier,
        )

        val candidates = players.mapNotNull { p ->
            val att  = p.passAttempts?.toIntOrNull() ?: return@mapNotNull null
            val made = p.passesMade?.toIntOrNull()   ?: return@mapNotNull null
            if (att < MIN_PASS_ATTEMPTS) return@mapNotNull null

            val clampedMade = minOf(made, att)
            val pct         = clampedMade * 100 / att
            val delta       = teamPct - pct
            val missed      = att - clampedMade

            val tier: Tier = if (att >= HIGH_VOLUME_THRESHOLD) {
                // High-volume: comparative analysis
                if (pct >= MAX_ACCURACY_PCT) return@mapNotNull null
                if (delta < MIN_DELTA_PCT)   return@mapNotNull null
                Tier.HIGH_VOLUME
            } else {
                // Low-volume (MIN_PASS_ATTEMPTS..HIGH_VOLUME_THRESHOLD-1): extreme failure only
                if (pct > LOW_VOLUME_MAX_ACCURACY_PCT) return@mapNotNull null
                Tier.LOW_VOLUME
            }

            Candidate(p, clampedMade, att, pct, delta, missed, tier)
        }

        if (candidates.isEmpty()) return null

        // 3. Select worst performer with confidence weighting
        //    Primary   : lowest accuracy (worse execution)
        //    Secondary : high-volume before low-volume (same accuracy → trust larger sample)
        //    Tertiary  : most missed passes (greater impact on construction)
        //    Quaternary: alphabetical name (determinism)
        val winner = candidates.minWithOrNull(
            compareBy<Candidate> { it.playerPct }
                .thenBy { it.tier.ordinal }         // HIGH_VOLUME=0 wins over LOW_VOLUME=1
                .thenByDescending { it.missed }
                .thenBy { it.player.playerName ?: "" }
        ) ?: return null

        return Selection(
            player            = winner.player,
            passesMade        = winner.made,
            passAttempts      = winner.attempts,
            playerAccuracyPct = winner.playerPct,
            teamAccuracyPct   = teamPct,
            deltaPct          = winner.delta,
        )
    }
}
