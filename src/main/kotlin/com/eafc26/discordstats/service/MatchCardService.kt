package com.eafc26.discordstats.service

import com.eafc26.discordstats.presentation.MatchSummaryPresentation
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.store.PublishedMatchStore
import com.eafc26.discordstats.web.PublicationStatus
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
    private val store: PublishedMatchStore,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    sealed class MatchCardResult {
        data class Success(
            val presentation: MatchSummaryPresentation,
            val simulated: Boolean = false,
        ) : MatchCardResult()
        object NoMatches : MatchCardResult()
    }

    /**
     * Returns the latest match card from the cache.
     *
     * @return [MatchCardResult.Success] with the cached presentation and simulation status, or
     *         [MatchCardResult.NoMatches] if no acquisition has succeeded yet.
     */
    fun getLatestMatchCard(clubId: ClubId): MatchCardResult {
        val snapshot = latestMatchHolder.snapshot(clubId)

        if (snapshot.presentation == null) {
            log.debug("No cached presentation available (version={})", snapshot.version)
            return MatchCardResult.NoMatches
        }

        log.debug("Returning cached presentation for match {} (version={}, simulated={})",
            snapshot.presentation.matchId, snapshot.version, snapshot.simulated)

        return MatchCardResult.Success(
            presentation = snapshot.presentation,
            simulated = snapshot.simulated,
        )
    }

    /**
     * Returns the publication status for a match.
     */
    fun getPublicationStatus(clubId: ClubId, matchId: String): PublicationStatus? {
        val record = store.find(clubId, matchId) ?: return null
        return PublicationStatus(
            state = record.state.name,
            attemptCount = record.attemptCount,
            lastError = record.lastError,
            lastHttpStatus = record.lastHttpStatus,
        )
    }

    /**
     * Returns the current cache version.
     * Useful for clients to detect changes without fetching the full presentation.
     */
    fun version(clubId: ClubId): Long = latestMatchHolder.version(clubId)

    /**
     * Returns whether the current cached presentation is from a simulated match.
     */
    fun isSimulated(clubId: ClubId): Boolean = latestMatchHolder.isSimulated(clubId)
}
