package com.eafc26.discordstats.service

import com.eafc26.discordstats.canonical.CanonicalMatch
import com.eafc26.discordstats.domain.interpretation.AwardDecision
import com.eafc26.discordstats.domain.interpretation.AwardType
import com.eafc26.discordstats.domain.interpretation.MatchOutcome
import com.eafc26.discordstats.domain.match.PlayerId
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.PlayerMatchPerformance
import com.eafc26.discordstats.history.MatchHistoryQuery
import com.eafc26.discordstats.diagnostics.CanonicalReadOrigin
import com.eafc26.discordstats.diagnostics.CanonicalReadOriginContext
import com.eafc26.discordstats.profile.PlayerProfile
import com.eafc26.discordstats.profile.PlayerProfileIndexEntry
import com.eafc26.discordstats.profile.PlayerProfileMatch
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class PlayerProfileService(
    private val matchHistoryService: MatchHistoryService,
    private val readOriginContext: CanonicalReadOriginContext = CanonicalReadOriginContext(),
) {
    fun listPlayers(clubId: ClubId): List<PlayerProfileIndexEntry> = readOriginContext.withOrigin(CanonicalReadOrigin.PLAYERS) {
        val accumulated = linkedMapOf<PlayerId, MutablePlayerIndex>()

        matchHistoryService.list(clubId).forEach { canonical ->
            if (!canonical.footballMatch.completion.hasCompleteSportingStatistics) return@forEach
            canonical.perspectivePlayers().forEach { performance ->
                val player = performance.player
                val current = accumulated.getOrPut(player.id) {
                    MutablePlayerIndex(
                        displayName = player.preferredDisplayName?.value ?: player.id.value,
                        matchCount = 0,
                        latestMatchAt = canonical.footballMatch.playedAt,
                    )
                }
                current.matchCount += 1
            }
        }

        accumulated.map { (playerId, value) ->
            PlayerProfileIndexEntry(
                playerId = playerId,
                displayName = value.displayName,
                matchCount = value.matchCount,
                latestMatchAt = value.latestMatchAt,
            )
        }.sortedWith(
            compareByDescending<PlayerProfileIndexEntry> { it.latestMatchAt }
                .thenBy { it.displayName.lowercase() }
                .thenBy { it.playerId.value }
        )
    }

    fun findById(
        clubId: ClubId,
        playerId: PlayerId,
        recentMatchLimit: Int = DEFAULT_RECENT_MATCH_LIMIT,
    ): PlayerProfile? = readOriginContext.withOrigin(CanonicalReadOrigin.PLAYERS) {
        require(recentMatchLimit > 0) { "Recent match limit must be positive" }

        val appearances = matchHistoryService
            .list(clubId, MatchHistoryQuery(playerId = playerId))
            .filter { it.footballMatch.completion.hasCompleteSportingStatistics }
            .mapNotNull { canonical ->
                canonical.perspectivePlayers()
                    .firstOrNull { it.player.id == playerId }
                    ?.let { performance -> Appearance(canonical, performance) }
            }

        if (appearances.isEmpty()) return@withOrigin null

        val ratings = appearances.mapNotNull { it.performance.rating?.value }
        val awards = appearances.flatMap { it.canonical.interpretation.awards.all() }
        val latestIdentity = appearances.first().performance.player

        PlayerProfile(
            playerId = playerId,
            displayName = latestIdentity.preferredDisplayName?.value ?: playerId.value,
            matchCount = appearances.size,
            wins = appearances.count { it.canonical.interpretation.result.outcome == MatchOutcome.WIN },
            draws = appearances.count { it.canonical.interpretation.result.outcome == MatchOutcome.DRAW },
            losses = appearances.count { it.canonical.interpretation.result.outcome == MatchOutcome.LOSS },
            averageRating = ratings.averageOrNull(),
            ratedMatchCount = ratings.size,
            goals = appearances.sumOf { it.performance.attacking.goals ?: 0 },
            assists = appearances.sumOf { it.performance.attacking.assists ?: 0 },
            craques = awards.countWinner(playerId, AwardType.CRAQUE),
            bagres = awards.countWinner(playerId, AwardType.BAGRE),
            xerifes = awards.countWinner(playerId, AwardType.XERIFE),
            redCards = appearances.sumOf { it.performance.discipline.redCards ?: 0 },
            shots = appearances.sumOf { it.performance.attacking.shots ?: 0 },
            passesCompleted = appearances.sumOf { it.performance.passing.completed ?: 0 },
            passesAttempted = appearances.sumOf { it.performance.passing.attempted ?: 0 },
            tacklesCompleted = appearances.sumOf { it.performance.defending.tacklesCompleted ?: 0 },
            tacklesAttempted = appearances.sumOf { it.performance.defending.tacklesAttempted ?: 0 },
            recentMatches = appearances.take(recentMatchLimit).map { it.toProfileMatch(playerId) },
        )
    }

    private fun CanonicalMatch.perspectivePlayers(): List<PlayerMatchPerformance> =
        footballMatch.participants
            .firstOrNull { it.club.id == interpretation.perspectiveClubId }
            ?.players
            .orEmpty()

    private fun Appearance.toProfileMatch(playerId: PlayerId): PlayerProfileMatch {
        val result = canonical.interpretation.result
        val participants = canonical.footballMatch.participants.associateBy { it.club.id }
        val ourClub = participants[result.ourClub]
        val opponentClub = participants[result.opponentClub]
        val wonAwards = canonical.interpretation.awards.all()
            .filter { it.winnerId == playerId }
            .mapTo(linkedSetOf()) { it.type }

        return PlayerProfileMatch(
            matchId = canonical.matchId,
            playedAt = canonical.footballMatch.playedAt,
            competition = canonical.footballMatch.competition,
            ourClubName = ourClub?.club?.name?.value,
            opponentClubName = opponentClub?.club?.name?.value,
            ourScore = result.ourScore.goals,
            opponentScore = result.opponentScore.goals,
            outcome = result.outcome,
            rating = performance.rating?.value,
            goals = performance.attacking.goals,
            assists = performance.attacking.assists,
            awards = wonAwards,
        )
    }

    private fun List<BigDecimal>.averageOrNull(): BigDecimal? =
        takeIf { it.isNotEmpty() }
            ?.reduce(BigDecimal::add)
            ?.divide(size.toBigDecimal(), 2, RoundingMode.HALF_UP)

    private fun List<AwardDecision>.countWinner(playerId: PlayerId, type: AwardType): Int =
        count { it.type == type && it.winnerId == playerId }

    private fun com.eafc26.discordstats.domain.interpretation.MatchAwards.all() =
        listOf(craque, bagre, xerife)

    private data class Appearance(
        val canonical: CanonicalMatch,
        val performance: PlayerMatchPerformance,
    )

    private data class MutablePlayerIndex(
        val displayName: String,
        var matchCount: Int,
        val latestMatchAt: java.time.Instant,
    )

    companion object {
        const val DEFAULT_RECENT_MATCH_LIMIT = 5
    }
}
