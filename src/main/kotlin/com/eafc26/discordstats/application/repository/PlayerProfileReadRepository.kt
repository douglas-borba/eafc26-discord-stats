package com.eafc26.discordstats.application.repository

import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.GameVersion
import com.eafc26.discordstats.domain.match.PlayerId
import com.eafc26.discordstats.profile.PlayerProfileAppearance
import com.eafc26.discordstats.profile.PlayerProfileIndexEntry

/**
 * Optimized, derived projection for PlayerProfile queries.
 *
 * Implementations must return appearances ordered by playedAt descending and
 * matchId ascending. They must never reinterpret sporting facts.
 *
 * A club's monitored identity is scoped to one contract era at a time, but its
 * canonical history can legitimately span two (a clubId that keeps its numeric
 * identity across an EA game transition). [gameVersion] must always be applied
 * so a player's X-Ray never silently blends FC26 and FC27 appearances under the
 * same clubId.
 */
interface PlayerProfileReadRepository {
    /** Selector-only summary. Must not materialize one complete X-Ray per player. */
    fun findPlayerIndex(clubId: ClubId, gameVersion: GameVersion = GameVersion.FC26): List<PlayerProfileIndexEntry>

    fun findAppearances(clubId: ClubId, gameVersion: GameVersion = GameVersion.FC26): List<PlayerProfileAppearance>

    fun findAppearances(clubId: ClubId, playerId: PlayerId, gameVersion: GameVersion = GameVersion.FC26): List<PlayerProfileAppearance>
}
