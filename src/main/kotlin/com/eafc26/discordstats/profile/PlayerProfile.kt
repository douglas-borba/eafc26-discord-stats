package com.eafc26.discordstats.profile

import com.eafc26.discordstats.domain.interpretation.AwardType
import com.eafc26.discordstats.domain.interpretation.MatchOutcome
import com.eafc26.discordstats.domain.match.CompetitionType
import com.eafc26.discordstats.domain.match.MatchId
import com.eafc26.discordstats.domain.match.PlayerId
import java.math.BigDecimal
import java.time.Instant

/**
 * Historical aggregate over persisted canonical matches.
 *
 * This is a product query model, not a football decision model.
 */
data class PlayerProfile(
    val playerId: PlayerId,
    val displayName: String,
    val matchCount: Int,
    val wins: Int,
    val draws: Int,
    val losses: Int,
    val averageRating: BigDecimal?,
    val ratedMatchCount: Int,
    val goals: Int,
    val assists: Int,
    val craques: Int,
    val bagres: Int,
    val xerifes: Int,
    val redCards: Int,
    val recentMatches: List<PlayerProfileMatch>,
) {
    init {
        require(matchCount == wins + draws + losses) {
            "Profile result totals must equal its match count"
        }
        require(ratedMatchCount in 0..matchCount) {
            "Rated match count must fit within the profile match count"
        }
    }
}

data class PlayerProfileMatch(
    val matchId: MatchId,
    val playedAt: Instant,
    val competition: CompetitionType?,
    val ourClubName: String?,
    val opponentClubName: String?,
    val ourScore: Int,
    val opponentScore: Int,
    val outcome: MatchOutcome,
    val rating: BigDecimal?,
    val goals: Int?,
    val assists: Int?,
    val awards: Set<AwardType>,
)

data class PlayerProfileIndexEntry(
    val playerId: PlayerId,
    val displayName: String,
    val matchCount: Int,
    val latestMatchAt: Instant,
)
