package com.eafc26.discordstats.service

import com.eafc26.discordstats.canonical.CanonicalMatch
import com.eafc26.discordstats.domain.interpretation.AwardType
import com.eafc26.discordstats.domain.interpretation.MatchOutcome
import com.eafc26.discordstats.domain.match.PlayerId
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.PlayerMatchPerformance
import com.eafc26.discordstats.application.repository.PlayerProfileReadRepository
import com.eafc26.discordstats.history.MatchHistoryQuery
import com.eafc26.discordstats.diagnostics.CanonicalReadOrigin
import com.eafc26.discordstats.diagnostics.CanonicalReadOriginContext
import com.eafc26.discordstats.profile.PlayerProfile
import com.eafc26.discordstats.profile.PlayerProfileAppearance
import com.eafc26.discordstats.profile.PlayerProfileIndexEntry
import com.eafc26.discordstats.profile.PlayerProfileMatch
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class PlayerProfileService(
    private val matchHistoryService: MatchHistoryService,
    private val readOriginContext: CanonicalReadOriginContext = CanonicalReadOriginContext(),
    private val playerProfileReadRepository: PlayerProfileReadRepository? = null,
) {
    fun listPlayers(clubId: ClubId): List<PlayerProfileIndexEntry> = readOriginContext.withOrigin(CanonicalReadOrigin.PLAYERS) {
        playerIndex(loadAppearances(clubId))
    }

    /**
     * Builds the complete player collection from one canonical history snapshot.
     *
     * The public player-list endpoint needs both the index and every profile, so
     * loading history once keeps every profile consistent and avoids per-player
     * canonical reads.
     */
    fun listProfiles(
        clubId: ClubId,
        recentMatchLimit: Int = DEFAULT_RECENT_MATCH_LIMIT,
    ): List<PlayerProfile> = readOriginContext.withOrigin(CanonicalReadOrigin.PLAYERS) {
        require(recentMatchLimit > 0) { "Recent match limit must be positive" }

        val appearances = loadAppearances(clubId)
        playerIndex(appearances).mapNotNull { entry ->
            profileFrom(appearances, entry.playerId, recentMatchLimit)
        }
    }

    fun findById(
        clubId: ClubId,
        playerId: PlayerId,
        recentMatchLimit: Int = DEFAULT_RECENT_MATCH_LIMIT,
    ): PlayerProfile? = readOriginContext.withOrigin(CanonicalReadOrigin.PLAYERS) {
        require(recentMatchLimit > 0) { "Recent match limit must be positive" }

        profileFrom(loadAppearances(clubId, playerId), playerId, recentMatchLimit)
    }

    private fun loadAppearances(clubId: ClubId): List<PlayerProfileAppearance> =
        playerProfileReadRepository?.findAppearances(clubId)
            ?: canonicalAppearances(matchHistoryService.list(clubId))

    private fun loadAppearances(clubId: ClubId, playerId: PlayerId): List<PlayerProfileAppearance> =
        playerProfileReadRepository?.findAppearances(clubId, playerId)
            ?: canonicalAppearances(matchHistoryService.list(clubId, MatchHistoryQuery(playerId = playerId)))

    private fun playerIndex(appearances: List<PlayerProfileAppearance>): List<PlayerProfileIndexEntry> {
        val accumulated = linkedMapOf<PlayerId, MutablePlayerIndex>()

        appearances.forEach { appearance ->
            if (!appearance.completion.hasCompleteSportingStatistics) return@forEach
            val current = accumulated.getOrPut(appearance.playerId) {
                MutablePlayerIndex(
                    displayName = appearance.preferredDisplayName,
                    matchCount = 0,
                    latestMatchAt = appearance.playedAt,
                )
            }
            current.matchCount += 1
        }

        return accumulated.map { (playerId, value) ->
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

    private fun profileFrom(
        allAppearances: List<PlayerProfileAppearance>,
        playerId: PlayerId,
        recentMatchLimit: Int,
    ): PlayerProfile? {
        val appearances = allAppearances
            .asSequence()
            .filter { it.playerId == playerId }
            .filter { it.completion.hasCompleteSportingStatistics }
            .toList()

        if (appearances.isEmpty()) return null

        val ratings = appearances.mapNotNull { it.rating }
        val latestAppearance = appearances.first()

        return PlayerProfile(
            playerId = playerId,
            displayName = latestAppearance.preferredDisplayName,
            matchCount = appearances.size,
            wins = appearances.count { it.outcome == MatchOutcome.WIN },
            draws = appearances.count { it.outcome == MatchOutcome.DRAW },
            losses = appearances.count { it.outcome == MatchOutcome.LOSS },
            averageRating = ratings.averageOrNull(),
            ratedMatchCount = ratings.size,
            goals = appearances.sumOf { it.goals ?: 0 },
            assists = appearances.sumOf { it.assists ?: 0 },
            craques = appearances.count { AwardType.CRAQUE in it.awards },
            bagres = appearances.count { AwardType.BAGRE in it.awards },
            xerifes = appearances.count { AwardType.XERIFE in it.awards },
            redCards = appearances.sumOf { it.redCards ?: 0 },
            shots = appearances.sumOf { it.shots ?: 0 },
            passesCompleted = appearances.sumOf { it.passesCompleted ?: 0 },
            passesAttempted = appearances.sumOf { it.passesAttempted ?: 0 },
            tacklesCompleted = appearances.sumOf { it.tacklesCompleted ?: 0 },
            tacklesAttempted = appearances.sumOf { it.tacklesAttempted ?: 0 },
            recentMatches = appearances.take(recentMatchLimit).map { it.toProfileMatch() },
        )
    }

    private fun canonicalAppearances(history: List<CanonicalMatch>): List<PlayerProfileAppearance> =
        history.flatMap { canonical ->
            val result = canonical.interpretation.result
            val participants = canonical.footballMatch.participants.associateBy { it.club.id }
            val ourClub = participants[result.ourClub]
            val opponentClub = participants[result.opponentClub]
            canonical.perspectivePlayers().map { performance ->
                PlayerProfileAppearance(
                    playerId = performance.player.id,
                    platformName = performance.player.platformName?.value,
                    proName = performance.player.proName?.value,
                    matchId = canonical.matchId,
                    playedAt = canonical.footballMatch.playedAt,
                    competition = canonical.footballMatch.competition,
                    ourClubName = ourClub?.club?.name?.value,
                    opponentClubName = opponentClub?.club?.name?.value,
                    ourScore = result.ourScore.goals,
                    opponentScore = result.opponentScore.goals,
                    outcome = result.outcome,
                    completion = canonical.footballMatch.completion,
                    rating = performance.rating?.value,
                    goals = performance.attacking.goals,
                    assists = performance.attacking.assists,
                    shots = performance.attacking.shots,
                    passesCompleted = performance.passing.completed,
                    passesAttempted = performance.passing.attempted,
                    tacklesCompleted = performance.defending.tacklesCompleted,
                    tacklesAttempted = performance.defending.tacklesAttempted,
                    redCards = performance.discipline.redCards,
                    awards = canonical.interpretation.awards.all()
                        .filter { it.winnerId == performance.player.id }
                        .mapTo(linkedSetOf()) { it.type },
                )
            }
        }

    private fun CanonicalMatch.perspectivePlayers(): List<PlayerMatchPerformance> =
        footballMatch.participants
            .firstOrNull { it.club.id == interpretation.perspectiveClubId }
            ?.players
            .orEmpty()

    private fun PlayerProfileAppearance.toProfileMatch(): PlayerProfileMatch {
        return PlayerProfileMatch(
            matchId = matchId,
            playedAt = playedAt,
            competition = competition,
            ourClubName = ourClubName,
            opponentClubName = opponentClubName,
            ourScore = ourScore,
            opponentScore = opponentScore,
            outcome = outcome,
            rating = rating,
            goals = goals,
            assists = assists,
            awards = awards,
        )
    }

    private fun List<BigDecimal>.averageOrNull(): BigDecimal? =
        takeIf { it.isNotEmpty() }
            ?.reduce(BigDecimal::add)
            ?.divide(size.toBigDecimal(), 2, RoundingMode.HALF_UP)

    private fun com.eafc26.discordstats.domain.interpretation.MatchAwards.all() =
        listOf(craque, bagre, xerife)

    private data class MutablePlayerIndex(
        val displayName: String,
        var matchCount: Int,
        val latestMatchAt: java.time.Instant,
    )

    companion object {
        const val DEFAULT_RECENT_MATCH_LIMIT = 5
    }
}
