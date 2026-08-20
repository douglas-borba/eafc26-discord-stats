package com.eafc26.discordstats.service

import com.eafc26.discordstats.application.repository.CanonicalMatchRepository
import com.eafc26.discordstats.application.repository.CanonicalMatchOverview
import com.eafc26.discordstats.application.repository.CanonicalRepositoryMetadata
import com.eafc26.discordstats.canonical.CanonicalMatch
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.MatchId
import com.eafc26.discordstats.history.MatchHistoryOrder
import com.eafc26.discordstats.history.MatchHistoryQuery
import com.eafc26.discordstats.diagnostics.CanonicalReadOrigin
import com.eafc26.discordstats.diagnostics.CanonicalReadOriginContext
import org.springframework.stereotype.Service

/**
 * Read-only access to persisted canonical history.
 *
 * It organizes repository queries without reinterpreting football facts.
 */
@Service
class MatchHistoryService(
    private val repository: CanonicalMatchRepository,
    private val readOriginContext: CanonicalReadOriginContext = CanonicalReadOriginContext(),
) {
    fun findById(clubId: ClubId, matchId: MatchId): CanonicalMatch? = repository.findById(clubId, matchId)

    fun list(clubId: ClubId, query: MatchHistoryQuery = MatchHistoryQuery()): List<CanonicalMatch> {
        val comparator = if (query.order == MatchHistoryOrder.NEWEST_FIRST) {
            compareByDescending<CanonicalMatch> { it.footballMatch.playedAt }
                .thenBy { it.matchId.value }
        } else {
            compareBy<CanonicalMatch> { it.footballMatch.playedAt }
                .thenBy { it.matchId.value }
        }

        val matches = repository.findAll(clubId).asSequence()
            .filter {
                query.fromInclusive == null ||
                    !it.footballMatch.playedAt.isBefore(query.fromInclusive)
            }
            .filter {
                query.untilExclusive == null ||
                    it.footballMatch.playedAt.isBefore(query.untilExclusive)
            }
            .filter {
                query.competition == null ||
                    it.footballMatch.competition == query.competition
            }
            .filter { canonical ->
                query.playerId == null ||
                    canonical.footballMatch.participants.any { participant ->
                        participant.players.any { it.player.id == query.playerId }
                    }
            }
            .sortedWith(comparator)

        return query.limit?.let(matches::take)?.toList() ?: matches.toList()
    }

    fun latest(
        clubId: ClubId,
        limit: Int,
        origin: CanonicalReadOrigin? = null,
    ): List<CanonicalMatch> = readOriginContext.withOrigin(
        origin ?: readOriginContext.current().takeUnless { it == CanonicalReadOrigin.UNKNOWN }
            ?: CanonicalReadOrigin.HISTORY_LATEST,
    ) {
        repository.findRecent(clubId, limit)
    }

    /**
     * Ordered recent identifiers for consumers that only need to identify a
     * canonical context, without loading complete canonical records.
     */
    fun latestMatchIds(
        clubId: ClubId,
        limit: Int,
        origin: CanonicalReadOrigin? = null,
    ): List<MatchId> = readOriginContext.withOrigin(
        origin ?: readOriginContext.current().takeUnless { it == CanonicalReadOrigin.UNKNOWN }
            ?: CanonicalReadOrigin.HISTORY_LATEST,
    ) {
        repository.findRecentMatchIds(clubId, limit)
    }

    /**
     * Shallow newest-first feed for consumers that do not need to filter or inspect
     * the complete history. The repository applies the limit before loading payloads.
     */
    fun recent(
        clubId: ClubId,
        limit: Int,
        origin: CanonicalReadOrigin = CanonicalReadOrigin.DASHBOARD_OVERVIEW,
    ): List<CanonicalMatch> = readOriginContext.withOrigin(origin) {
        repository.findRecent(clubId, limit)
    }

    /** Bounded canonical sports facts for the public Overview fallback feed. */
    fun recentOverview(
        clubId: ClubId,
        limit: Int,
        origin: CanonicalReadOrigin = CanonicalReadOrigin.DASHBOARD_OVERVIEW,
    ): List<CanonicalMatchOverview> = readOriginContext.withOrigin(origin) {
        repository.findRecentOverview(clubId, limit)
    }

    fun metadata(clubId: ClubId): CanonicalRepositoryMetadata = repository.metadata(clubId)
}
