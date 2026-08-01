package com.eafc26.discordstats.ea.model

object PlayerStatisticsEligibility {

    /**
     * Returns the subset of [players] that are eligible for individual statistics.
     *
     * Eligibility is determined relative to the team:
     * a player must have played at least 90 % of the highest valid
     * `secondsPlayed` value on the same team. The comparison is exact integer
     * arithmetic — no floating point, no rounding:
     *
     *   playerSeconds * 100 >= maxSeconds * 90
     *
     * A `secondsPlayed` value is "valid" only when it is a positive integer.
     * null, blank, malformed, zero, and negative values are all invalid.
     *
     * If the entire team has no valid positive `secondsPlayed` (e.g. the EA
     * API response predates that field), every player is considered eligible
     * for backwards-compatibility.
     *
     * `status` and `rating` are never used for eligibility decisions.
     */
    fun eligiblePlayers(players: Collection<PlayerEntry>): List<PlayerEntry> {
        val validSeconds = players.mapNotNull { it.secondsPlayed?.toIntOrNull()?.takeIf { s -> s > 0 } }

        if (validSeconds.isEmpty()) return players.toList()

        val maxSeconds = validSeconds.max()
        return players.filter { p ->
            val played = p.secondsPlayed?.toIntOrNull()?.takeIf { s -> s > 0 } ?: return@filter false
            played.toLong() * 100 >= maxSeconds.toLong() * 90
        }
    }
}
