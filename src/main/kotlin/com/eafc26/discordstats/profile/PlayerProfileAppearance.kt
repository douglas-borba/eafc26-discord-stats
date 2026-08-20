package com.eafc26.discordstats.profile

import com.eafc26.discordstats.domain.interpretation.AwardType
import com.eafc26.discordstats.domain.interpretation.MatchOutcome
import com.eafc26.discordstats.domain.match.CompetitionType
import com.eafc26.discordstats.domain.match.MatchCompletion
import com.eafc26.discordstats.domain.match.MatchId
import com.eafc26.discordstats.domain.match.PlayerId
import java.math.BigDecimal
import java.time.Instant

/**
 * Minimal per-player match projection used to assemble historical profiles.
 *
 * It is a read projection only: canonical matches remain the sporting source
 * of truth and local repositories can still derive this shape from canonical
 * records when PostgreSQL is unavailable.
 */
data class PlayerProfileAppearance(
    val playerId: PlayerId,
    val platformName: String?,
    val proName: String?,
    val matchId: MatchId,
    val playedAt: Instant,
    val competition: CompetitionType?,
    val ourClubName: String?,
    val opponentClubName: String?,
    val ourScore: Int,
    val opponentScore: Int,
    val outcome: MatchOutcome,
    val completion: MatchCompletion,
    val rating: BigDecimal?,
    val goals: Int?,
    val assists: Int?,
    val shots: Int?,
    val passesCompleted: Int?,
    val passesAttempted: Int?,
    val tacklesCompleted: Int?,
    val tacklesAttempted: Int?,
    val redCards: Int?,
    val awards: Set<AwardType>,
) {
    val preferredDisplayName: String
        get() = proName ?: platformName ?: playerId.value
}
