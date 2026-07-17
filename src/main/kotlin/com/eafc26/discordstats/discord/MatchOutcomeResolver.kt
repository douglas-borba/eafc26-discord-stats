package com.eafc26.discordstats.discord

import com.eafc26.discordstats.ea.model.ClubMatchEntry

/**
 * Resolves match outcome (victory/draw/loss) from the perspective of our club.
 *
 * The scoreboard goals are the primary source of truth when they are valid.
 * The result field (0=loss, 1=win, 2=draw) is only used as fallback when
 * the scoreboard is invalid or unavailable.
 */
object MatchOutcomeResolver {

    enum class Outcome(val label: String, val emoji: String, val color: Int) {
        VICTORY("Vitória", "🟢", 0x2ECC71),
        DRAW("Empate", "🟡", 0x95A5A6),
        LOSS("Derrota", "🔴", 0xE74C3C),
    }

    data class ResolvedOutcome(
        val outcome: Outcome,
        val ourScore: Int,
        val oppScore: Int,
    )

    /**
     * Resolves the match outcome based on scores and result metadata.
     *
     * Priority:
     * 1. If both scores are valid integers, use scoreboard comparison
     * 2. Otherwise, fall back to result field (0=loss, 1=win, 2=draw)
     */
    fun resolve(ourEntry: ClubMatchEntry?, oppEntry: ClubMatchEntry?): ResolvedOutcome {
        val ourScore = ourEntry?.score?.toIntOrNull()
        val oppScore = oppEntry?.score?.toIntOrNull()

        // If both scores are valid, use scoreboard as primary source
        if (ourScore != null && oppScore != null && ourScore >= 0 && oppScore >= 0) {
            val outcome = when {
                ourScore > oppScore -> Outcome.VICTORY
                ourScore < oppScore -> Outcome.LOSS
                else -> Outcome.DRAW
            }
            return ResolvedOutcome(outcome, ourScore, oppScore)
        }

        // Fall back to result field
        val outcome = when (ourEntry?.result) {
            "1" -> Outcome.VICTORY
            "2" -> Outcome.DRAW
            else -> Outcome.LOSS
        }

        return ResolvedOutcome(
            outcome = outcome,
            ourScore = ourScore ?: 0,
            oppScore = oppScore ?: 0,
        )
    }
}

