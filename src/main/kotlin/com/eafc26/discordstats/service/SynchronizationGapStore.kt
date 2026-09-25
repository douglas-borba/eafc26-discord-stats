package com.eafc26.discordstats.service

import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.GameVersion
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Persistent audit state for a historical interval the bounded EA API could
 * not prove complete. It is deliberately independent from polling checkpoints.
 */
interface SynchronizationGapStore {
    fun findOpen(clubId: ClubId, gameVersion: GameVersion = GameVersion.FC26): SynchronizationGap?
    fun openGap(gap: SynchronizationGap)
}

data class SynchronizationGap(
    val clubId: ClubId,
    val anchorMatchId: String,
    val firstObservableMatchId: String?,
    val openedAt: Instant = Instant.now(),
    /** Contract era of the polling frontier; historical gaps must never bridge games. */
    val gameVersion: GameVersion = GameVersion.FC26,
)

/** Local fallback; production uses the durable Postgres implementation. */
class InMemorySynchronizationGapStore : SynchronizationGapStore {
    private val gaps = ConcurrentHashMap<Pair<GameVersion, ClubId>, SynchronizationGap>()

    override fun findOpen(clubId: ClubId, gameVersion: GameVersion): SynchronizationGap? = gaps[gameVersion to clubId]

    override fun openGap(gap: SynchronizationGap) {
        gaps.putIfAbsent(gap.gameVersion to gap.clubId, gap)
    }
}
