package com.eafc26.discordstats.service

import com.eafc26.discordstats.canonical.CanonicalMatch
import com.eafc26.discordstats.domain.interpretation.MatchOutcome
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.MatchId
import com.eafc26.discordstats.domain.match.PlayerId
import com.eafc26.discordstats.opponent.HeadToHeadRecord
import com.eafc26.discordstats.opponent.OpponentCriterionEvidence
import com.eafc26.discordstats.opponent.OpponentHistory
import com.eafc26.discordstats.opponent.OpponentHistoryEvidence
import com.eafc26.discordstats.opponent.OpponentLeaderType
import com.eafc26.discordstats.opponent.OpponentMatch
import com.eafc26.discordstats.opponent.OpponentPlayer
import com.eafc26.discordstats.opponent.OpponentPlayerLeaders
import com.eafc26.discordstats.opponent.OpponentRun
import com.eafc26.discordstats.opponent.OpponentRunRecord
import com.eafc26.discordstats.opponent.OpponentRunType
import com.eafc26.discordstats.opponent.OpponentSummary
import org.springframework.stereotype.Service

@Service
class OpponentHistoryService(
    private val matchHistoryService: MatchHistoryService,
) {
    fun listOpponents(clubId: ClubId): List<OpponentSummary> = groupedMatches(clubId).map { (opponentClubId, matches) ->
        val ordered = matches.sortedCanonicalNewestFirst()
        OpponentSummary(
            clubId = opponentClubId,
            displayName = ordered.latestOpponentName() ?: FALLBACK_NAME,
            meetings = ordered.size,
            record = ordered.record(),
            latestMatch = ordered.first().opponentMatch(),
        )
    }.sortedWith(compareByDescending<OpponentSummary> { it.latestMatch.playedAt }.thenBy { it.clubId.value })

    fun findByClubId(clubId: ClubId, opponentClubId: ClubId): OpponentHistory? {
        val newest = groupedMatches(clubId)[opponentClubId]?.sortedCanonicalNewestFirst() ?: return null
        val oldest = newest.asReversed()
        val allNames = newest.mapNotNull { it.opponentName() }.toSet()
        val biggestWins = newest.extreme(MatchOutcome.WIN, maximum = true)
        val biggestLosses = newest.extreme(MatchOutcome.LOSS, maximum = false)
        val current = oldest.currentRun()
        val runRecords = OpponentRunType.entries.mapNotNull { type -> oldest.recordRun(type) }
        val leaders = oldest.filter { it.footballMatch.completion.hasCompleteSportingStatistics }.playerLeaders()
        val record = newest.record()
        val criteria = buildList {
            add(criterion("retrospecto", "Todas as partidas do ClubId são consideradas.", record.toString(), newest))
            if (biggestWins.isNotEmpty()) add(criterion("maior_vitoria", TIES, biggestWins.marginText(), biggestWins))
            if (biggestLosses.isNotEmpty()) add(criterion("maior_derrota", TIES, biggestLosses.marginText(), biggestLosses))
            current?.let { add(OpponentCriterionEvidence("situacao_atual_${it.type.name.lowercase()}", "Selecionar a sequência terminal aplicável.", it.count.toString(), it.matchIds)) }
            runRecords.forEach { run -> add(OpponentCriterionEvidence("maior_sequencia_${run.type.name.lowercase()}", TIES, run.count.toString(), run.runs.flatten())) }
            leaders.forEach { leader -> add(OpponentCriterionEvidence("lider_${leader.type.name.lowercase()}", PLAYER_TIES, leader.value.toString(), newest.map(CanonicalMatch::matchId))) }
        }
        return OpponentHistory(
            clubId = opponentClubId,
            displayName = newest.latestOpponentName() ?: FALLBACK_NAME,
            previousNames = allNames - setOfNotNull(newest.latestOpponentName()),
            periodStart = oldest.first().footballMatch.playedAt,
            periodEnd = newest.first().footballMatch.playedAt,
            record = record,
            matches = newest.map { it.opponentMatch() },
            biggestWins = biggestWins.map { it.opponentMatch() },
            biggestLosses = biggestLosses.map { it.opponentMatch() },
            currentRun = current,
            runRecords = runRecords,
            playerLeaders = leaders,
            evidence = OpponentHistoryEvidence(opponentClubId, oldest.map(CanonicalMatch::matchId), oldest.size, criteria),
        )
    }

    private fun groupedMatches(clubId: ClubId): Map<ClubId, List<CanonicalMatch>> =
        matchHistoryService.list(clubId).groupBy { it.interpretation.result.opponentClub }

    private fun List<CanonicalMatch>.record() = HeadToHeadRecord(
        meetings = size,
        wins = countOutcome(MatchOutcome.WIN),
        draws = countOutcome(MatchOutcome.DRAW),
        losses = countOutcome(MatchOutcome.LOSS),
        goalsFor = sumOf { it.interpretation.result.ourScore.goals },
        goalsAgainst = sumOf { it.interpretation.result.opponentScore.goals },
    )

    private fun List<CanonicalMatch>.countOutcome(outcome: MatchOutcome) =
        count { it.interpretation.result.outcome == outcome }

    private fun List<CanonicalMatch>.sortedCanonicalNewestFirst() = sortedWith(
        compareByDescending<CanonicalMatch> { it.footballMatch.playedAt }.thenBy { it.matchId.value }
    )

    private fun CanonicalMatch.opponentName() = footballMatch.participants
        .firstOrNull { it.club.id == interpretation.result.opponentClub }?.club?.name?.value

    private fun List<CanonicalMatch>.latestOpponentName() = firstNotNullOfOrNull { it.opponentName() }

    private fun CanonicalMatch.opponentMatch(): OpponentMatch {
        val result = interpretation.result
        val participants = footballMatch.participants.associateBy { it.club.id }
        return OpponentMatch(matchId, footballMatch.playedAt, footballMatch.competition,
            participants[result.ourClub]?.club?.name?.value, participants[result.opponentClub]?.club?.name?.value,
            result.ourScore.goals, result.opponentScore.goals, result.outcome)
    }

    private fun List<CanonicalMatch>.extreme(outcome: MatchOutcome, maximum: Boolean): List<CanonicalMatch> {
        val candidates = filter { it.interpretation.result.outcome == outcome }
        if (candidates.isEmpty()) return emptyList()
        val margins = candidates.associateWith { it.interpretation.result.ourScore.goals - it.interpretation.result.opponentScore.goals }
        val record = if (maximum) margins.values.max() else margins.values.min()
        return candidates.filter { margins.getValue(it) == record }
    }

    private fun List<CanonicalMatch>.currentRun(): OpponentRun? {
        if (size < 2) return null
        return OpponentRunType.entries.map { type -> type to takeLastWhile { it.matches(type) } }
            .filter { (_, run) -> run.size >= 2 }
            .maxWithOrNull(compareBy<Pair<OpponentRunType, List<CanonicalMatch>>> { it.second.size }.thenBy { -it.first.ordinal })
            ?.let { (type, run) -> OpponentRun(type, run.size, run.map(CanonicalMatch::matchId)) }
    }

    private fun List<CanonicalMatch>.recordRun(type: OpponentRunType): OpponentRunRecord? {
        if (size < 2) return null
        val runs = mutableListOf<List<CanonicalMatch>>()
        var current = mutableListOf<CanonicalMatch>()
        forEach { match ->
            if (match.matches(type)) current += match
            else if (current.isNotEmpty()) { runs += current.toList(); current = mutableListOf() }
        }
        if (current.isNotEmpty()) runs += current.toList()
        val maximum = runs.maxOfOrNull(List<CanonicalMatch>::size) ?: return null
        if (maximum < 2) return null
        return OpponentRunRecord(type, maximum, runs.filter { it.size == maximum }.map { run -> run.map(CanonicalMatch::matchId) })
    }

    private fun CanonicalMatch.matches(type: OpponentRunType) = when (type) {
        OpponentRunType.WINNING -> interpretation.result.outcome == MatchOutcome.WIN
        OpponentRunType.UNBEATEN -> interpretation.result.outcome != MatchOutcome.LOSS
        OpponentRunType.WINLESS -> interpretation.result.outcome != MatchOutcome.WIN
    }

    private fun List<CanonicalMatch>.playerLeaders(): List<OpponentPlayerLeaders> {
        val names = linkedMapOf<PlayerId, String>()
        val goals = linkedMapOf<PlayerId, Int>()
        val assists = linkedMapOf<PlayerId, Int>()
        val craques = linkedMapOf<PlayerId, Int>()
        val xerifes = linkedMapOf<PlayerId, Int>()
        forEach { canonical ->
            val ourPlayers = canonical.footballMatch.participants.firstOrNull { it.club.id == canonical.interpretation.perspectiveClubId }?.players.orEmpty()
            ourPlayers.forEach { p ->
                names[p.player.id] = p.player.preferredDisplayName?.value ?: p.player.id.value
                goals.merge(p.player.id, p.attacking.goals ?: 0, Int::plus)
                assists.merge(p.player.id, p.attacking.assists ?: 0, Int::plus)
            }
            canonical.interpretation.awards.all().filter { it.awarded && it.winnerId != null }.forEach { award ->
                when (award.type) {
                    com.eafc26.discordstats.domain.interpretation.AwardType.CRAQUE -> craques.merge(requireNotNull(award.winnerId), 1, Int::plus)
                    com.eafc26.discordstats.domain.interpretation.AwardType.XERIFE -> xerifes.merge(requireNotNull(award.winnerId), 1, Int::plus)
                    else -> Unit
                }
            }
        }
        return listOf(
            leaders(OpponentLeaderType.GOALS, goals, names), leaders(OpponentLeaderType.ASSISTS, assists, names),
            leaders(OpponentLeaderType.CRAQUES, craques, names), leaders(OpponentLeaderType.XERIFES, xerifes, names),
        ).filterNotNull()
    }

    private fun leaders(type: OpponentLeaderType, values: Map<PlayerId, Int>, names: Map<PlayerId, String>): OpponentPlayerLeaders? {
        val maximum = values.values.maxOrNull() ?: return null
        if (maximum <= 0) return null
        val players = values.filterValues { it == maximum }.keys.map { OpponentPlayer(it, names[it] ?: it.value) }
            .sortedWith(compareBy<OpponentPlayer> { it.displayName.lowercase() }.thenBy { it.playerId.value })
        return OpponentPlayerLeaders(type, maximum, players)
    }

    private fun com.eafc26.discordstats.domain.interpretation.MatchAwards.all() = listOf(craque, bagre, xerife)
    private fun criterion(name: String, tie: String, result: String, matches: List<CanonicalMatch>) =
        OpponentCriterionEvidence(name, tie, result, matches.map(CanonicalMatch::matchId))
    private fun List<CanonicalMatch>.marginText() = first().interpretation.result.let { "${it.ourScore.goals}x${it.opponentScore.goals}" }

    companion object {
        private const val FALLBACK_NAME = "Adversário sem nome"
        private const val TIES = "Preservar todas as partidas empatadas no critério."
        private const val PLAYER_TIES = "Preservar todos os jogadores empatados, ordenados por nome e PlayerId."
    }
}
