package com.eafc26.discordstats.service

import com.eafc26.discordstats.domain.match.ClubId
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Persistent audit state for a historical interval the bounded EA API could
 * not prove complete. It is deliberately independent from polling checkpoints.
 */
interface SynchronizationGapStore {
    fun findOpen(clubId: ClubId): SynchronizationGap?
    fun openGap(gap: SynchronizationGap)
}

data class SynchronizationGap(
    val clubId: ClubId,
    val anchorMatchId: String,
    val firstObservableMatchId: String?,
    val openedAt: Instant = Instant.now(),
)

/** Local fallback; production uses the durable Postgres implementation. */
class InMemorySynchronizationGapStore : SynchronizationGapStore {
    private val gaps = ConcurrentHashMap<ClubId, SynchronizationGap>()

    override fun findOpen(clubId: ClubId): SynchronizationGap? = gaps[clubId]

    override fun openGap(gap: SynchronizationGap) {
        gaps.putIfAbsent(gap.clubId, gap)
    }
}
