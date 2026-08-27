package com.eafc26.discordstats.application.repository

import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.PlayerId
import com.eafc26.discordstats.profile.PlayerProfileAppearance
import com.eafc26.discordstats.profile.PlayerProfileIndexEntry

/**
 * Optimized, derived projection for PlayerProfile queries.
 *
 * Implementations must return appearances ordered by playedAt descending and
 * matchId ascending. They must never reinterpret sporting facts.
 */
interface PlayerProfileReadRepository {
    /** Selector-only summary. Must not materialize one complete X-Ray per player. */
    fun findPlayerIndex(clubId: ClubId): List<PlayerProfileIndexEntry>

    fun findAppearances(clubId: ClubId): List<PlayerProfileAppearance>

    fun findAppearances(clubId: ClubId, playerId: PlayerId): List<PlayerProfileAppearance>
}
