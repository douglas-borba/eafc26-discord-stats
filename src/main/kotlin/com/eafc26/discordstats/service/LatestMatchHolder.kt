package com.eafc26.discordstats.service

import com.eafc26.discordstats.presentation.MatchSummaryPresentation
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

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
 * Thread-safety is achieved via atomic references.
 */
@Component
class LatestMatchHolder {

    private val presentationRef = AtomicReference<MatchSummaryPresentation?>(null)
    private val versionRef = AtomicLong(0)
    private val simulatedRef = AtomicReference<Boolean>(false)

    /**
     * Returns the cached presentation, or null if no acquisition has succeeded yet.
     */
    fun presentation(): MatchSummaryPresentation? = presentationRef.get()

    /**
     * Returns the current version number.
     *
     * The version is incremented every time a new presentation is cached.
     * Callers can compare versions to detect changes.
     */
    fun version(): Long = versionRef.get()

    /**
     * Returns true if the current cached presentation is from a simulated match.
     */
    fun isSimulated(): Boolean = simulatedRef.get()

    /**
     * Updates the cached presentation and increments the version.
     *
     * @param presentation The new presentation to cache.
     * @param simulated Whether this presentation is from a simulated match.
     * @return The new version number.
     */
    fun update(presentation: MatchSummaryPresentation, simulated: Boolean = false): Long {
        presentationRef.set(presentation)
        simulatedRef.set(simulated)
        return versionRef.incrementAndGet()
    }

    /**
     * Returns true if a presentation has been cached.
     */
    fun hasPresentation(): Boolean = presentationRef.get() != null

    /**
     * Clears the cached presentation.
     *
     * This is primarily used by the development simulator to reset state
     * between test runs. It should not be called in production.
     */
    fun clear() {
        presentationRef.set(null)
        simulatedRef.set(false)
        versionRef.incrementAndGet()
    }

    /**
     * Returns a snapshot of the current state.
     */
    fun snapshot(): LatestMatchSnapshot = LatestMatchSnapshot(
        presentation = presentationRef.get(),
        version = versionRef.get(),
        simulated = simulatedRef.get(),
    )
}

/**
 * Immutable snapshot of the latest match holder state.
 */
data class LatestMatchSnapshot(
    val presentation: MatchSummaryPresentation?,
    val version: Long,
    val simulated: Boolean = false,
)
