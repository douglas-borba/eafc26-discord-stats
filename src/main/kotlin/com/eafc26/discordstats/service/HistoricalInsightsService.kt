package com.eafc26.discordstats.service

import com.eafc26.discordstats.canonical.CanonicalMatch
import com.eafc26.discordstats.domain.interpretation.MatchOutcome
import com.eafc26.discordstats.domain.match.MatchId
import com.eafc26.discordstats.history.MatchHistoryOrder
import com.eafc26.discordstats.history.MatchHistoryQuery
import com.eafc26.discordstats.insight.HistoricalInsight
import com.eafc26.discordstats.insight.HistoricalInsightCategory
import com.eafc26.discordstats.insight.HistoricalInsightEvidence
import com.eafc26.discordstats.insight.HistoricalInsightObservation
import com.eafc26.discordstats.insight.HistoricalInsightPlayer
import com.eafc26.discordstats.insight.HistoricalInsightRule
import com.eafc26.discordstats.insight.HistoricalInsightRuleId
import com.eafc26.discordstats.insight.HistoricalInsightType
import com.eafc26.discordstats.insight.HistoricalInsightValue
import com.eafc26.discordstats.insight.HistoricalInsightsReport
import com.eafc26.discordstats.insight.HistoricalScoreline
import com.eafc26.discordstats.profile.PlayerProfile
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Duration

@Service
class HistoricalInsightsService(
    private val matchHistoryService: MatchHistoryService,
    private val playerProfileService: PlayerProfileService,
) {
    fun generate(): HistoricalInsightsReport {
        val matches = matchHistoryService.list(
            MatchHistoryQuery(order = MatchHistoryOrder.OLDEST_FIRST)
        )
        if (matches.isEmpty()) return HistoricalInsightsReport(0, emptyList())

        val profiles = playerProfileService.listPlayers()
            .mapNotNull { playerProfileService.findById(it.playerId) }
        val insights = buildList {
            addRunInsight(matches, HistoricalInsightType.LONGEST_WINNING_STREAK) {
                it.interpretation.result.outcome == MatchOutcome.WIN
            }
            addRunInsight(matches, HistoricalInsightType.LONGEST_UNBEATEN_STREAK) {
                it.interpretation.result.outcome != MatchOutcome.LOSS
            }
            addRunInsight(matches, HistoricalInsightType.LONGEST_SCORELESS_STREAK) {
                it.interpretation.result.ourScore.goals == 0
            }
            addRunInsight(matches, HistoricalInsightType.LONGEST_CONCEDING_STREAK) {
                it.interpretation.result.opponentScore.goals > 0
            }
            addRatingInsight(matches, HistoricalInsightType.BEST_TEAM_AVERAGE, maximum = true)
            addRatingInsight(matches, HistoricalInsightType.WORST_TEAM_AVERAGE, maximum = false)
            addPlayerLeader(profiles, matches.size, HistoricalInsightType.MOST_CRAQUES, positiveOnly = true) { it.craques.toBigDecimal() }
            addPlayerLeader(profiles, matches.size, HistoricalInsightType.MOST_BAGRES, positiveOnly = true) { it.bagres.toBigDecimal() }
            addPlayerLeader(profiles, matches.size, HistoricalInsightType.MOST_XERIFES, positiveOnly = true) { it.xerifes.toBigDecimal() }
            addPlayerLeader(profiles, matches.size, HistoricalInsightType.TOP_SCORER, positiveOnly = true) { it.goals.toBigDecimal() }
            addPlayerLeader(profiles, matches.size, HistoricalInsightType.ASSIST_LEADER, positiveOnly = true) { it.assists.toBigDecimal() }
            addPlayerAverageLeader(profiles, matches.size)
            addVictoryMoment(matches, HistoricalInsightType.FIRST_WIN, first = true)
            addVictoryMoment(matches, HistoricalInsightType.LATEST_WIN, first = false)
            addLongestUnbeatenInterval(matches)
            addBiggestWin(matches)
        }
        return HistoricalInsightsReport(matches.size, insights)
    }

    private fun MutableList<HistoricalInsight>.addRunInsight(
        matches: List<CanonicalMatch>,
        type: HistoricalInsightType,
        predicate: (CanonicalMatch) -> Boolean,
    ) {
        val runs = runs(matches, predicate)
        if (runs.isEmpty()) return
        val record = runs.maxOf { it.matches.size }
        val leaders = runs.filter { it.matches.size == record }
        add(
            HistoricalInsight(
                type = type,
                category = HistoricalInsightCategory.CLUB,
                value = HistoricalInsightValue.Count(record),
                involvedMatchIds = leaders.flatMap { run -> run.matches.map { it.matchId } },
                involvedPlayers = emptyList(),
                rule = type.rule(),
                evidence = HistoricalInsightEvidence(
                    sourceMatchCount = matches.size,
                    candidateCount = runs.size,
                    eligibleCandidateCount = leaders.size,
                    observations = runs.mapIndexed { index, run ->
                        HistoricalInsightObservation(
                            "run-${index + 1}:${run.matches.first().matchId.value}",
                            run.matches.size.toString(),
                        )
                    },
                ),
            )
        )
    }

    private fun MutableList<HistoricalInsight>.addRatingInsight(
        matches: List<CanonicalMatch>,
        type: HistoricalInsightType,
        maximum: Boolean,
    ) {
        val candidates = matches.mapNotNull { canonical ->
            canonical.interpretation.teamMetrics.averageRating?.let { canonical to it }
        }
        if (candidates.isEmpty()) return
        val record = if (maximum) candidates.maxOf { it.second } else candidates.minOf { it.second }
        val leaders = candidates.filter { it.second.compareTo(record) == 0 }
        add(
            HistoricalInsight(
                type = type,
                category = HistoricalInsightCategory.CLUB,
                value = HistoricalInsightValue.Rating(record),
                involvedMatchIds = leaders.map { it.first.matchId },
                involvedPlayers = emptyList(),
                rule = type.rule(),
                evidence = HistoricalInsightEvidence(
                    matches.size,
                    candidates.size,
                    leaders.size,
                    candidates.map {
                        HistoricalInsightObservation(it.first.matchId.value, it.second.toPlainString())
                    },
                ),
            )
        )
    }

    private fun MutableList<HistoricalInsight>.addPlayerLeader(
        profiles: List<PlayerProfile>,
        sourceMatchCount: Int,
        type: HistoricalInsightType,
        positiveOnly: Boolean,
        metric: (PlayerProfile) -> BigDecimal,
    ) {
        if (profiles.isEmpty()) return
        val record = profiles.maxOf(metric)
        if (positiveOnly && record.signum() == 0) return
        val leaders = profiles.filter { metric(it).compareTo(record) == 0 }.sortedPlayers()
        add(
            HistoricalInsight(
                type = type,
                category = HistoricalInsightCategory.PLAYER,
                value = HistoricalInsightValue.Count(record.intValueExact()),
                involvedMatchIds = emptyList(),
                involvedPlayers = leaders.map { HistoricalInsightPlayer(it.playerId, it.displayName) },
                rule = type.rule(),
                evidence = HistoricalInsightEvidence(
                    sourceMatchCount,
                    profiles.size,
                    leaders.size,
                    profiles.sortedPlayers().map {
                        HistoricalInsightObservation(it.playerId.value, metric(it).toPlainString())
                    },
                ),
            )
        )
    }

    private fun MutableList<HistoricalInsight>.addPlayerAverageLeader(
        profiles: List<PlayerProfile>,
        sourceMatchCount: Int,
    ) {
        val eligible = profiles.filter {
            it.ratedMatchCount >= MINIMUM_RATED_MATCHES && it.averageRating != null
        }
        if (eligible.isEmpty()) return
        val record = eligible.maxOf { requireNotNull(it.averageRating) }
        val leaders = eligible.filter {
            requireNotNull(it.averageRating).compareTo(record) == 0
        }.sortedPlayers()
        val type = HistoricalInsightType.HIGHEST_PLAYER_AVERAGE
        add(
            HistoricalInsight(
                type,
                HistoricalInsightCategory.PLAYER,
                HistoricalInsightValue.Rating(record),
                emptyList(),
                leaders.map { HistoricalInsightPlayer(it.playerId, it.displayName) },
                type.rule(),
                HistoricalInsightEvidence(
                    sourceMatchCount,
                    profiles.size,
                    eligible.size,
                    profiles.sortedPlayers().map {
                        HistoricalInsightObservation(
                            it.playerId.value,
                            "${it.averageRating?.toPlainString() ?: "unrated"}/${it.ratedMatchCount}",
                        )
                    },
                ),
            )
        )
    }

    private fun MutableList<HistoricalInsight>.addVictoryMoment(
        matches: List<CanonicalMatch>,
        type: HistoricalInsightType,
        first: Boolean,
    ) {
        val victories = matches.filter { it.interpretation.result.outcome == MatchOutcome.WIN }
        if (victories.isEmpty()) return
        val selected = if (first) victories.first() else victories.last()
        add(
            HistoricalInsight(
                type,
                HistoricalInsightCategory.TEMPORAL,
                HistoricalInsightValue.Moment(selected.footballMatch.playedAt),
                listOf(selected.matchId),
                emptyList(),
                type.rule(),
                HistoricalInsightEvidence(
                    matches.size,
                    victories.size,
                    1,
                    victories.map {
                        HistoricalInsightObservation(it.matchId.value, it.footballMatch.playedAt.toString())
                    },
                ),
            )
        )
    }

    private fun MutableList<HistoricalInsight>.addLongestUnbeatenInterval(matches: List<CanonicalMatch>) {
        val runs = runs(matches) { it.interpretation.result.outcome != MatchOutcome.LOSS }
        if (runs.isEmpty()) return
        val durations = runs.map { run ->
            run to Duration.between(
                run.matches.first().footballMatch.playedAt,
                run.matches.last().footballMatch.playedAt,
            ).seconds
        }
        val record = durations.maxOf { it.second }
        val leaders = durations.filter { it.second == record }
        val type = HistoricalInsightType.LONGEST_UNBEATEN_INTERVAL
        add(
            HistoricalInsight(
                type,
                HistoricalInsightCategory.TEMPORAL,
                HistoricalInsightValue.DurationSeconds(record),
                leaders.flatMap { it.first.matches.map(CanonicalMatch::matchId) },
                emptyList(),
                type.rule(),
                HistoricalInsightEvidence(
                    matches.size,
                    runs.size,
                    leaders.size,
                    durations.mapIndexed { index, value ->
                        HistoricalInsightObservation(
                            "run-${index + 1}:${value.first.matches.first().matchId.value}",
                            value.second.toString(),
                        )
                    },
                ),
            )
        )
    }

    private fun MutableList<HistoricalInsight>.addBiggestWin(matches: List<CanonicalMatch>) {
        val victories = matches.filter { it.interpretation.result.outcome == MatchOutcome.WIN }
        if (victories.isEmpty()) return
        val withMargins = victories.map {
            it to (it.interpretation.result.ourScore.goals - it.interpretation.result.opponentScore.goals)
        }
        val record = withMargins.maxOf { it.second }
        val leaders = withMargins.filter { it.second == record }
        val type = HistoricalInsightType.BIGGEST_WIN
        add(
            HistoricalInsight(
                type,
                HistoricalInsightCategory.TEMPORAL,
                HistoricalInsightValue.BiggestWin(
                    record,
                    leaders.map {
                        HistoricalScoreline(
                            it.first.matchId,
                            it.first.interpretation.result.ourScore.goals,
                            it.first.interpretation.result.opponentScore.goals,
                        )
                    },
                ),
                leaders.map { it.first.matchId },
                emptyList(),
                type.rule(),
                HistoricalInsightEvidence(
                    matches.size,
                    victories.size,
                    leaders.size,
                    withMargins.map {
                        HistoricalInsightObservation(it.first.matchId.value, it.second.toString())
                    },
                ),
            )
        )
    }

    private fun runs(
        matches: List<CanonicalMatch>,
        predicate: (CanonicalMatch) -> Boolean,
    ): List<HistoricalRun> {
        val result = mutableListOf<HistoricalRun>()
        var current = mutableListOf<CanonicalMatch>()
        matches.forEach { match ->
            if (predicate(match)) {
                current += match
            } else if (current.isNotEmpty()) {
                result += HistoricalRun(current.toList())
                current = mutableListOf()
            }
        }
        if (current.isNotEmpty()) result += HistoricalRun(current.toList())
        return result
    }

    private fun List<PlayerProfile>.sortedPlayers() =
        sortedWith(compareBy<PlayerProfile> { it.displayName.lowercase() }.thenBy { it.playerId.value })

    private fun HistoricalInsightType.rule(): HistoricalInsightRule {
        val (criterion, tiePolicy) = when (this) {
            HistoricalInsightType.LONGEST_WINNING_STREAK ->
                "Maior número de vitórias consecutivas em ordem cronológica." to CO_LEADER_POLICY
            HistoricalInsightType.LONGEST_UNBEATEN_STREAK ->
                "Maior número de partidas consecutivas cujo resultado não é derrota." to CO_LEADER_POLICY
            HistoricalInsightType.LONGEST_SCORELESS_STREAK ->
                "Maior número de partidas consecutivas com zero gol no placar do clube." to CO_LEADER_POLICY
            HistoricalInsightType.LONGEST_CONCEDING_STREAK ->
                "Maior número de partidas consecutivas com ao menos um gol sofrido." to CO_LEADER_POLICY
            HistoricalInsightType.BEST_TEAM_AVERAGE ->
                "Maior média de equipe persistida; partidas sem média são inelegíveis." to CO_LEADER_POLICY
            HistoricalInsightType.WORST_TEAM_AVERAGE ->
                "Menor média de equipe persistida; partidas sem média são inelegíveis." to CO_LEADER_POLICY
            HistoricalInsightType.MOST_CRAQUES -> "Maior contagem histórica de Craques; zero não produz insight." to PLAYER_TIE_POLICY
            HistoricalInsightType.MOST_BAGRES -> "Maior contagem histórica de Bagres; zero não produz insight." to PLAYER_TIE_POLICY
            HistoricalInsightType.MOST_XERIFES -> "Maior contagem histórica de Xerifes; zero não produz insight." to PLAYER_TIE_POLICY
            HistoricalInsightType.TOP_SCORER -> "Maior total histórico de gols; zero não produz insight." to PLAYER_TIE_POLICY
            HistoricalInsightType.ASSIST_LEADER -> "Maior total histórico de assistências; zero não produz insight." to PLAYER_TIE_POLICY
            HistoricalInsightType.HIGHEST_PLAYER_AVERAGE ->
                "Maior média histórica entre jogadores com pelo menos $MINIMUM_RATED_MATCHES partidas avaliadas." to PLAYER_TIE_POLICY
            HistoricalInsightType.FIRST_WIN -> "Primeira vitória pela data da partida; MatchId desempata na consulta histórica." to "Selecionar a primeira."
            HistoricalInsightType.LATEST_WIN -> "Última vitória pela data da partida; MatchId desempata na consulta histórica." to "Selecionar a última."
            HistoricalInsightType.LONGEST_UNBEATEN_INTERVAL ->
                "Maior duração entre a primeira e a última partida de uma sequência invicta." to CO_LEADER_POLICY
            HistoricalInsightType.BIGGEST_WIN ->
                "Maior saldo positivo entre gols marcados e sofridos em uma vitória." to CO_LEADER_POLICY
        }
        return HistoricalInsightRule(
            HistoricalInsightRuleId("historical.${name.lowercase()}"),
            1,
            criterion,
            tiePolicy,
        )
    }

    private data class HistoricalRun(val matches: List<CanonicalMatch>)

    companion object {
        const val MINIMUM_RATED_MATCHES = 3
        private const val CO_LEADER_POLICY = "Preservar todas as sequências ou partidas empatadas."
        private const val PLAYER_TIE_POLICY = "Preservar todos os jogadores empatados, ordenados por nome e PlayerId."
    }
}
