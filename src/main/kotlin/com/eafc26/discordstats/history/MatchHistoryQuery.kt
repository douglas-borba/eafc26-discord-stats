package com.eafc26.discordstats.history

import com.eafc26.discordstats.domain.match.CompetitionType
import com.eafc26.discordstats.domain.match.PlayerId
import java.time.Instant

/**
 * Generic filters over already-interpreted canonical matches.
 *
 * Time ranges use [fromInclusive, untilExclusive).
 */
data class MatchHistoryQuery(
    val fromInclusive: Instant? = null,
    val untilExclusive: Instant? = null,
    val competition: CompetitionType? = null,
    val playerId: PlayerId? = null,
    val order: MatchHistoryOrder = MatchHistoryOrder.NEWEST_FIRST,
    val limit: Int? = null,
) {
    init {
        require(fromInclusive == null || untilExclusive == null || fromInclusive < untilExclusive) {
            "History query start must be before its exclusive end"
        }
        require(limit == null || limit > 0) { "History query limit must be positive" }
    }
}

enum class MatchHistoryOrder {
    NEWEST_FIRST,
    OLDEST_FIRST,
}
