package com.eafc26.discordstats.service

import com.eafc26.discordstats.presentation.MatchSummaryPresentation
import com.eafc26.discordstats.domain.match.ClubId
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * Holds the latest successfully acquired match presentation.
 *
 * This component caches the [MatchSummaryPresentation] generated during
 * acquisition, allowing the Match Card to display data without querying
 * the EA API on every request.
 *
 * The cache is updated:
 * - After successful presentation generation (PROCESSING phase)
 * - Even when Discord delivery is skipped (already published)
 * - Even when Discord delivery fails
 *
 * A Discord failure does NOT invalidate the cached presentation.
 * The presentation represents "what EA told us", independent of Discord.
 *
 * Every cached state is isolated by the official EA [ClubId].
 */
@Component
class LatestMatchHolder {
    private val states = ConcurrentHashMap<ClubId, LatestMatchSnapshot>()

    /**
     * Returns the cached presentation, or null if no acquisition has succeeded yet.
     */
    fun presentation(clubId: ClubId): MatchSummaryPresentation? = snapshot(clubId).presentation

    /**
     * Returns the current version number.
     *
     * The version is incremented every time a new presentation is cached.
     * Callers can compare versions to detect changes.
     */
    fun version(clubId: ClubId): Long = snapshot(clubId).version

    /**
     * Returns true if the current cached presentation is from a simulated match.
     */
    fun isSimulated(clubId: ClubId): Boolean = snapshot(clubId).simulated

    /**
     * Updates the cached presentation and increments the version.
     *
     * @param presentation The new presentation to cache.
     * @param simulated Whether this presentation is from a simulated match.
     * @return The new version number.
     */
    fun update(clubId: ClubId, presentation: MatchSummaryPresentation, simulated: Boolean = false): Long =
        states.compute(clubId) { _, current ->
            LatestMatchSnapshot(presentation, (current?.version ?: 0) + 1, simulated)
        }!!.version

    /**
     * Returns true if a presentation has been cached.
     */
    fun hasPresentation(clubId: ClubId): Boolean = presentation(clubId) != null

    /**
     * Clears the cached presentation.
     *
     * This is primarily used by the development simulator to reset state
     * between test runs. It should not be called in production.
     */
    fun clear(clubId: ClubId) {
        states.compute(clubId) { _, current ->
            LatestMatchSnapshot(null, (current?.version ?: 0) + 1, false)
        }
    }

    /**
     * Returns a snapshot of the current state.
     */
    fun snapshot(clubId: ClubId): LatestMatchSnapshot = states[clubId] ?: LatestMatchSnapshot.empty()
}

/**
 * Immutable snapshot of the latest match holder state.
 */
data class LatestMatchSnapshot(
    val presentation: MatchSummaryPresentation?,
    val version: Long,
    val simulated: Boolean = false,
) {
    companion object {
        fun empty() = LatestMatchSnapshot(null, 0, false)
    }
}
