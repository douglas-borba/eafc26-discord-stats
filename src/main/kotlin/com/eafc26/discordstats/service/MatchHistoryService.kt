package com.eafc26.discordstats.service

import com.eafc26.discordstats.application.club.DefaultClubProvider
import com.eafc26.discordstats.application.repository.CanonicalMatchRepository
import com.eafc26.discordstats.application.repository.CanonicalRepositoryMetadata
import com.eafc26.discordstats.canonical.CanonicalMatch
import com.eafc26.discordstats.domain.match.MatchId
import com.eafc26.discordstats.history.MatchHistoryOrder
import com.eafc26.discordstats.history.MatchHistoryQuery
import org.springframework.stereotype.Service

/**
 * Read-only access to persisted canonical history.
 *
 * It organizes repository queries without reinterpreting football facts.
 */
@Service
class MatchHistoryService(
    private val repository: CanonicalMatchRepository,
    private val defaultClubProvider: DefaultClubProvider,
) {
    private val clubId get() = defaultClubProvider.get().clubId

    fun findById(matchId: MatchId): CanonicalMatch? = repository.findById(clubId, matchId)

    fun list(query: MatchHistoryQuery = MatchHistoryQuery()): List<CanonicalMatch> {
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

    fun latest(limit: Int): List<CanonicalMatch> =
        list(MatchHistoryQuery(order = MatchHistoryOrder.NEWEST_FIRST, limit = limit))

    fun metadata(): CanonicalRepositoryMetadata = repository.metadata(clubId)
}
