package com.eafc26.discordstats.presentation.opponent

import com.eafc26.discordstats.domain.interpretation.MatchOutcome
import com.eafc26.discordstats.opponent.OpponentHistory
import com.eafc26.discordstats.opponent.OpponentLeaderType
import com.eafc26.discordstats.opponent.OpponentMatch
import com.eafc26.discordstats.opponent.OpponentRun
import com.eafc26.discordstats.opponent.OpponentRunRecord
import com.eafc26.discordstats.opponent.OpponentRunType
import com.eafc26.discordstats.opponent.OpponentSummary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class OpponentIndexResponse(val status: String, val opponents: List<OpponentSummaryView>)
data class OpponentDetailResponse(val status: String, val opponent: OpponentHistoryView? = null, val message: String? = null)
data class OpponentSummaryView(
    val clubId: String, val name: String, val meetings: Int, val wins: Int, val draws: Int, val losses: Int,
    val goalsFor: Int, val goalsAgainst: Int, val goalDifference: Int, val latestMatch: OpponentMatchView,
)
data class OpponentHistoryView(
    val clubId: String, val name: String, val meetings: Int, val periodLabel: String,
    val wins: Int, val draws: Int, val losses: Int, val goalsFor: Int, val goalsAgainst: Int, val goalDifference: Int,
    val matches: List<OpponentMatchView>, val biggestWins: List<OpponentMatchView>, val biggestLosses: List<OpponentMatchView>,
    val currentRun: OpponentRunView?, val runRecords: List<OpponentRunView>, val leaders: List<OpponentLeaderView>,
    val evidence: OpponentEvidenceView,
)
data class OpponentMatchView(
    val matchId: String, val playedAt: Instant, val dateLabel: String, val competition: String?, val ourClubName: String,
    val opponentName: String, val ourScore: Int, val opponentScore: Int, val outcomeCode: String, val outcomeLabel: String,
)
data class OpponentRunView(val type: String, val label: String, val count: Int, val matchIds: List<String>, val tiedRuns: Int = 1)
data class OpponentLeaderView(val type: String, val label: String, val value: Int, val players: List<OpponentPlayerView>)
data class OpponentPlayerView(val playerId: String, val name: String)
data class OpponentEvidenceView(val opponentClubId: String, val sourceMatchCount: Int, val sourceMatchIds: List<String>, val criteria: List<OpponentCriterionView>)
data class OpponentCriterionView(val criterion: String, val tiePolicy: String, val result: String, val matchIds: List<String>)

object OpponentHistoryPresenter {
    private val date = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("pt-BR"))

    fun index(opponents: List<OpponentSummary>, zone: ZoneId = ZoneId.systemDefault()) = OpponentIndexResponse(
        if (opponents.isEmpty()) "empty" else "success",
        opponents.map { it.view(zone) },
    )

    fun detail(history: OpponentHistory, zone: ZoneId = ZoneId.systemDefault()): OpponentDetailResponse {
        val record = history.record
        return OpponentDetailResponse("success", OpponentHistoryView(
            clubId = history.clubId.value, name = history.displayName, meetings = record.meetings,
            periodLabel = period(history.periodStart, history.periodEnd, zone),
            wins = record.wins, draws = record.draws, losses = record.losses, goalsFor = record.goalsFor,
            goalsAgainst = record.goalsAgainst, goalDifference = record.goalDifference,
            matches = history.matches.map { it.view(zone) }, biggestWins = history.biggestWins.map { it.view(zone) },
            biggestLosses = history.biggestLosses.map { it.view(zone) }, currentRun = history.currentRun?.view(),
            runRecords = history.runRecords.map { it.view() },
            leaders = history.playerLeaders.map { leader -> OpponentLeaderView(
                leader.type.name, leader.type.label(), leader.value,
                leader.players.map { OpponentPlayerView(it.playerId.value, it.displayName) }
            ) },
            evidence = OpponentEvidenceView(history.clubId.value, history.evidence.sourceMatchCount,
                history.evidence.sourceMatchIds.map { it.value }, history.evidence.criteria.map {
                    OpponentCriterionView(it.criterion, it.tiePolicy, it.result, it.involvedMatchIds.map { id -> id.value })
                }),
        ))
    }

    private fun OpponentSummary.view(zone: ZoneId) = OpponentSummaryView(
        clubId.value, displayName, meetings, record.wins, record.draws, record.losses, record.goalsFor,
        record.goalsAgainst, record.goalDifference, latestMatch.view(zone),
    )
    private fun OpponentMatch.view(zone: ZoneId) = OpponentMatchView(
        matchId.value, playedAt, date.withZone(zone).format(playedAt), competition?.label(), ourClubName ?: "Associação BF",
        opponentName ?: "Adversário sem nome", ourScore, opponentScore, outcome.name, outcome.label(),
    )
    private fun OpponentRun.view() = OpponentRunView(type.name, type.currentLabel(count), count, matchIds.map { it.value })
    private fun OpponentRunRecord.view() = OpponentRunView(type.name, type.recordLabel(count), count, runs.flatten().map { it.value }, runs.size)
    private fun OpponentRunType.currentLabel(count: Int) = when (this) {
        OpponentRunType.WINNING -> "$count vitórias consecutivas"
        OpponentRunType.UNBEATEN -> "$count jogos sem perder"
        OpponentRunType.WINLESS -> "$count jogos sem vencer"
    }
    private fun OpponentRunType.recordLabel(count: Int) = when (this) {
        OpponentRunType.WINNING -> "Maior sequência de vitórias: $count"
        OpponentRunType.UNBEATEN -> "Maior sequência invicta: $count"
        OpponentRunType.WINLESS -> "Maior sequência sem vencer: $count"
    }
    private fun OpponentLeaderType.label() = when (this) {
        OpponentLeaderType.GOALS -> "Artilharia contra o adversário"
        OpponentLeaderType.ASSISTS -> "Liderança em assistências"
        OpponentLeaderType.CRAQUES -> "Mais vezes Craque"
        OpponentLeaderType.XERIFES -> "Mais vezes Xerife"
    }
    private fun MatchOutcome.label() = when (this) {
        MatchOutcome.WIN -> "Vitória"
        MatchOutcome.DRAW -> "Empate"
        MatchOutcome.LOSS -> "Derrota"
    }
    private fun com.eafc26.discordstats.domain.match.CompetitionType.label() = name.lowercase().replaceFirstChar(Char::uppercase)
    private fun period(start: Instant, end: Instant, zone: ZoneId) = if (start == end) date.withZone(zone).format(start)
        else "${date.withZone(zone).format(start)} — ${date.withZone(zone).format(end)}"
}
