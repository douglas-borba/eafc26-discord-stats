package com.eafc26.discordstats.application.repository

import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.PlayerId
import com.eafc26.discordstats.profile.PlayerProfileAppearance

/**
 * Optimized, derived projection for PlayerProfile queries.
 *
 * Implementations must return appearances ordered by playedAt descending and
 * matchId ascending. They must never reinterpret sporting facts.
 */
interface PlayerProfileReadRepository {
    fun findAppearances(clubId: ClubId): List<PlayerProfileAppearance>

    fun findAppearances(clubId: ClubId, playerId: PlayerId): List<PlayerProfileAppearance>
}
