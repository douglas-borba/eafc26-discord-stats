package com.eafc26.discordstats.opponent

import com.eafc26.discordstats.domain.interpretation.AwardType
import com.eafc26.discordstats.domain.interpretation.MatchOutcome
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.CompetitionType
import com.eafc26.discordstats.domain.match.MatchId
import com.eafc26.discordstats.domain.match.PlayerId
import java.time.Instant

data class OpponentSummary(
    val clubId: ClubId,
    val displayName: String,
    val meetings: Int,
    val record: HeadToHeadRecord,
    val latestMatch: OpponentMatch,
)

data class OpponentHistory(
    val clubId: ClubId,
    val displayName: String,
    val previousNames: Set<String>,
    val periodStart: Instant,
    val periodEnd: Instant,
    val record: HeadToHeadRecord,
    val matches: List<OpponentMatch>,
    val biggestWins: List<OpponentMatch>,
    val biggestLosses: List<OpponentMatch>,
    val currentRun: OpponentRun?,
    val runRecords: List<OpponentRunRecord>,
    val playerLeaders: List<OpponentPlayerLeaders>,
    val evidence: OpponentHistoryEvidence,
)

data class HeadToHeadRecord(
    val meetings: Int,
    val wins: Int,
    val draws: Int,
    val losses: Int,
    val goalsFor: Int,
    val goalsAgainst: Int,
) {
    val goalDifference: Int get() = goalsFor - goalsAgainst
}

data class OpponentMatch(
    val matchId: MatchId,
    val playedAt: Instant,
    val competition: CompetitionType?,
    val ourClubName: String?,
    val opponentName: String?,
    val ourScore: Int,
    val opponentScore: Int,
    val outcome: MatchOutcome,
)

enum class OpponentRunType { WINNING, UNBEATEN, WINLESS }

data class OpponentRun(
    val type: OpponentRunType,
    val count: Int,
    val matchIds: List<MatchId>,
)

data class OpponentRunRecord(
    val type: OpponentRunType,
    val count: Int,
    val runs: List<List<MatchId>>,
)

enum class OpponentLeaderType(val awardType: AwardType? = null) {
    GOALS,
    ASSISTS,
    CRAQUES(AwardType.CRAQUE),
    XERIFES(AwardType.XERIFE),
}

data class OpponentPlayerLeaders(
    val type: OpponentLeaderType,
    val value: Int,
    val players: List<OpponentPlayer>,
)

data class OpponentPlayer(
    val playerId: PlayerId,
    val displayName: String,
)

data class OpponentHistoryEvidence(
    val opponentClubId: ClubId,
    val sourceMatchIds: List<MatchId>,
    val sourceMatchCount: Int,
    val criteria: List<OpponentCriterionEvidence>,
)

data class OpponentCriterionEvidence(
    val criterion: String,
    val tiePolicy: String,
    val result: String,
    val involvedMatchIds: List<MatchId>,
)
