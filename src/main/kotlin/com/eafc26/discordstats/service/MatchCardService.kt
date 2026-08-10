package com.eafc26.discordstats.service

import com.eafc26.discordstats.presentation.MatchSummaryPresentation
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.presentation.editorial.MatchEditorialPresentationRepository
import com.eafc26.discordstats.store.PublishedMatchStore
import com.eafc26.discordstats.web.PublicationStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class MatchCardService(
    private val latestMatchHolder: LatestMatchHolder,
    private val store: PublishedMatchStore,
    private val editorialRepository: MatchEditorialPresentationRepository?,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    sealed class MatchCardResult {
        data class Success(
            val presentation: MatchSummaryPresentation,
            val simulated: Boolean = false,
        ) : MatchCardResult()
        object NoMatches : MatchCardResult()
    }

    fun getLatestMatchCard(clubId: ClubId): MatchCardResult {
        val snapshot = latestMatchHolder.snapshot(clubId)

        if (snapshot.presentation != null) {
            log.debug("Returning cached presentation for match {} (version={}, simulated={})",
                snapshot.presentation.matchId, snapshot.version, snapshot.simulated)
            return MatchCardResult.Success(
                presentation = snapshot.presentation,
                simulated = snapshot.simulated,
            )
        }

        val persisted = try {
            editorialRepository?.findByClub(clubId, limit = 1)?.firstOrNull()
        } catch (ex: Exception) {
            log.debug("Editorial fallback unavailable for club {}: {}", clubId.value, ex.message)
            null
        }

        if (persisted != null) {
            log.debug("Hydrating latest match from editorial repository for club {}", clubId.value)
            latestMatchHolder.update(clubId, persisted.presentation)
            return MatchCardResult.Success(presentation = persisted.presentation)
        }

        log.debug("No cached presentation available (version={})", snapshot.version)
        return MatchCardResult.NoMatches
    }

    fun getPublicationStatus(clubId: ClubId, matchId: String): PublicationStatus? {
        val record = store.find(clubId, matchId) ?: return null
        return PublicationStatus(
            state = record.state.name,
            attemptCount = record.attemptCount,
            lastError = record.lastError,
            lastHttpStatus = record.lastHttpStatus,
        )
    }

    fun version(clubId: ClubId): Long = latestMatchHolder.version(clubId)

    fun isSimulated(clubId: ClubId): Boolean = latestMatchHolder.isSimulated(clubId)
}
