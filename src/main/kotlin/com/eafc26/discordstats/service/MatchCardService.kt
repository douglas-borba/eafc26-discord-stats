package com.eafc26.discordstats.service

import com.eafc26.discordstats.presentation.MatchSummaryPresentation
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Provides match card data from the cached presentation.
 *
 * This service reads from [LatestMatchHolder], which is populated by
 * [MatchAcquisitionService] during acquisition. It no longer queries
 * the EA API directly.
 *
 * If no acquisition has occurred yet, returns [MatchCardResult.NoMatches].
 */
@Service
class MatchCardService(
    private val latestMatchHolder: LatestMatchHolder,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    sealed class MatchCardResult {
        data class Success(val presentation: MatchSummaryPresentation) : MatchCardResult()
        object NoMatches : MatchCardResult()
        // EaUnavailable is no longer needed since we read from cache
    }

    /**
     * Returns the latest match card from the cache.
     *
     * @return [MatchCardResult.Success] with the cached presentation, or
     *         [MatchCardResult.NoMatches] if no acquisition has succeeded yet.
     */
    fun getLatestMatchCard(): MatchCardResult {
        val snapshot = latestMatchHolder.snapshot()

        if (snapshot.presentation == null) {
            log.debug("No cached presentation available (version={})", snapshot.version)
            return MatchCardResult.NoMatches
        }

        log.debug("Returning cached presentation for match {} (version={})",
            snapshot.presentation.matchId, snapshot.version)

        return MatchCardResult.Success(snapshot.presentation)
    }

    /**
     * Returns the current cache version.
     * Useful for clients to detect changes without fetching the full presentation.
     */
    fun version(): Long = latestMatchHolder.version()
}

