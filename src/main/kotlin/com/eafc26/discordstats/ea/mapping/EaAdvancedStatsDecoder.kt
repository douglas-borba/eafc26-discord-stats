package com.eafc26.discordstats.ea.mapping

import com.eafc26.discordstats.domain.match.AdvancedPlayerStats
import com.eafc26.discordstats.ea.model.PlayerEntry

/**
 * Single translation point for the EA Clubs sparse event histograms.
 *
 * EA sends values as comma-separated `eventCode:value` pairs. Missing event
 * codes are factual zeroes, including either side of the interception sum.
 * No raw event code is persisted in the canonical domain.
 */
object EaAdvancedStatsDecoder {
    private const val SECOND_ASSISTS_CODE = 115
    private const val THROUGH_PASSES_CODE = 152
    private const val DRIBBLES_COMPLETED_CODE = 174
    private const val BEATS_CODE = 112
    private const val INTERCEPTIONS_CODE = 6

    fun decode(player: PlayerEntry): AdvancedPlayerStats {
        val aggregate0 = histogram(player.matchEventAggregate0)
        val aggregate1 = histogram(player.matchEventAggregate1)
        return AdvancedPlayerStats(
            secondAssists = aggregate0.valueAt(SECOND_ASSISTS_CODE),
            throughPasses = aggregate0.valueAt(THROUGH_PASSES_CODE),
            dribblesCompleted = aggregate0.valueAt(DRIBBLES_COMPLETED_CODE),
            beats = aggregate0.valueAt(BEATS_CODE),
            interceptions = aggregate0.valueAt(INTERCEPTIONS_CODE) + aggregate1.valueAt(INTERCEPTIONS_CODE),
        )
    }

    private fun histogram(raw: String?): Map<Int, Int> = raw
        ?.split(',')
        ?.mapNotNull { entry ->
            val (code, value) = entry.split(':', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
            val parsedCode = code.toIntOrNull() ?: return@mapNotNull null
            val parsedValue = value.toIntOrNull()?.takeIf { it >= 0 } ?: return@mapNotNull null
            parsedCode to parsedValue
        }
        ?.toMap()
        .orEmpty()

    private fun Map<Int, Int>.valueAt(code: Int): Int = this[code] ?: 0
}
