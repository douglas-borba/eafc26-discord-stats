package com.eafc26.discordstats.presentation.insight

import com.eafc26.discordstats.insight.HistoricalInsight
import com.eafc26.discordstats.insight.HistoricalInsightCategory
import com.eafc26.discordstats.insight.HistoricalInsightType
import com.eafc26.discordstats.insight.HistoricalInsightValue
import com.eafc26.discordstats.insight.HistoricalInsightsReport
import java.math.RoundingMode
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class HistoricalInsightsResponse(
    val status: String,
    val sourceMatchCount: Int,
    val insights: List<HistoricalInsightView>,
)

data class HistoricalInsightView(
    val type: String,
    val category: String,
    val categoryLabel: String,
    val title: String,
    val description: String,
    val value: String,
    val involvedMatches: List<String>,
    val involvedPlayers: List<HistoricalInsightPlayerView>,
    val audit: HistoricalInsightAuditView,
)

data class HistoricalInsightPlayerView(
    val playerId: String,
    val name: String,
)

data class HistoricalInsightAuditView(
    val ruleId: String,
    val ruleVersion: Int,
    val criterion: String,
    val tiePolicy: String,
    val sourceMatchCount: Int,
    val candidateCount: Int,
    val eligibleCandidateCount: Int,
    val observations: List<HistoricalInsightObservationView>,
)

data class HistoricalInsightObservationView(
    val subjectId: String,
    val metricValue: String,
)

object HistoricalInsightPresenter {
    private val dateFormatter = DateTimeFormatter.ofPattern(
        "dd/MM/yyyy 'às' HH:mm",
        Locale.forLanguageTag("pt-BR"),
    )

    fun response(
        report: HistoricalInsightsReport,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ) = HistoricalInsightsResponse(
        status = if (report.insights.isEmpty()) "empty" else "success",
        sourceMatchCount = report.sourceMatchCount,
        insights = report.insights.map { it.presentation(zoneId) },
    )

    private fun HistoricalInsight.presentation(zoneId: ZoneId) = HistoricalInsightView(
        type = type.name,
        category = category.name,
        categoryLabel = category.label(),
        title = type.title(),
        description = type.description(involvedPlayers.size),
        value = value.format(zoneId),
        involvedMatches = involvedMatchIds.map { it.value },
        involvedPlayers = involvedPlayers.map {
            HistoricalInsightPlayerView(it.playerId.value, it.displayName)
        },
        audit = HistoricalInsightAuditView(
            ruleId = rule.id.value,
            ruleVersion = rule.version,
            criterion = rule.criterion,
            tiePolicy = rule.tiePolicy,
            sourceMatchCount = evidence.sourceMatchCount,
            candidateCount = evidence.candidateCount,
            eligibleCandidateCount = evidence.eligibleCandidateCount,
            observations = evidence.observations.map {
                HistoricalInsightObservationView(it.subjectId, it.metricValue)
            },
        ),
    )

    private fun HistoricalInsightValue.format(zoneId: ZoneId): String = when (this) {
        is HistoricalInsightValue.Count -> value.toString()
        is HistoricalInsightValue.Rating ->
            value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
        is HistoricalInsightValue.DurationSeconds -> {
            val duration = Duration.ofSeconds(value)
            val days = duration.toDays()
            when {
                days > 0 -> "$days dia${if (days == 1L) "" else "s"}"
                duration.toHours() > 0 -> "${duration.toHours()} hora${if (duration.toHours() == 1L) "" else "s"}"
                else -> "0 dias"
            }
        }
        is HistoricalInsightValue.Moment -> dateFormatter.withZone(zoneId).format(value)
        is HistoricalInsightValue.BiggestWin -> buildString {
            append(scorelines.joinToString(" / ") { "${it.ourScore} × ${it.opponentScore}" })
            append(" (+")
            append(margin)
            append(")")
        }
    }

    private fun HistoricalInsightCategory.label() = when (this) {
        HistoricalInsightCategory.CLUB -> "Clube"
        HistoricalInsightCategory.PLAYER -> "Jogadores"
        HistoricalInsightCategory.TEMPORAL -> "Temporais"
    }

    private fun HistoricalInsightType.title() = when (this) {
        HistoricalInsightType.LONGEST_WINNING_STREAK -> "Maior sequência de vitórias"
        HistoricalInsightType.LONGEST_UNBEATEN_STREAK -> "Maior sequência invicta"
        HistoricalInsightType.LONGEST_SCORELESS_STREAK -> "Maior sequência sem marcar"
        HistoricalInsightType.LONGEST_CONCEDING_STREAK -> "Maior sequência sofrendo gols"
        HistoricalInsightType.BEST_TEAM_AVERAGE -> "Melhor média da equipe"
        HistoricalInsightType.WORST_TEAM_AVERAGE -> "Pior média da equipe"
        HistoricalInsightType.MOST_CRAQUES -> "Mais Craques"
        HistoricalInsightType.MOST_BAGRES -> "Mais Bagres"
        HistoricalInsightType.MOST_XERIFES -> "Mais Xerifes"
        HistoricalInsightType.TOP_SCORER -> "Artilheiro histórico"
        HistoricalInsightType.ASSIST_LEADER -> "Líder em assistências"
        HistoricalInsightType.HIGHEST_PLAYER_AVERAGE -> "Maior média de jogador"
        HistoricalInsightType.FIRST_WIN -> "Primeira vitória"
        HistoricalInsightType.LATEST_WIN -> "Última vitória"
        HistoricalInsightType.LONGEST_UNBEATEN_INTERVAL -> "Maior intervalo sem derrotas"
        HistoricalInsightType.BIGGEST_WIN -> "Maior goleada"
    }

    private fun HistoricalInsightType.description(playerCount: Int) = when (this) {
        HistoricalInsightType.LONGEST_WINNING_STREAK -> "Vitórias consecutivas no histórico canônico."
        HistoricalInsightType.LONGEST_UNBEATEN_STREAK -> "Partidas consecutivas sem derrota."
        HistoricalInsightType.LONGEST_SCORELESS_STREAK -> "Partidas consecutivas com zero gol marcado."
        HistoricalInsightType.LONGEST_CONCEDING_STREAK -> "Partidas consecutivas sofrendo ao menos um gol."
        HistoricalInsightType.BEST_TEAM_AVERAGE -> "Maior nota média registrada para a equipe."
        HistoricalInsightType.WORST_TEAM_AVERAGE -> "Menor nota média registrada para a equipe."
        HistoricalInsightType.MOST_CRAQUES -> leaderDescription(playerCount, "premiações de Craque")
        HistoricalInsightType.MOST_BAGRES -> leaderDescription(playerCount, "premiações de Bagre")
        HistoricalInsightType.MOST_XERIFES -> leaderDescription(playerCount, "premiações de Xerife")
        HistoricalInsightType.TOP_SCORER -> leaderDescription(playerCount, "gols")
        HistoricalInsightType.ASSIST_LEADER -> leaderDescription(playerCount, "assistências")
        HistoricalInsightType.HIGHEST_PLAYER_AVERAGE ->
            "Maior média entre jogadores com ao menos três partidas avaliadas."
        HistoricalInsightType.FIRST_WIN -> "Primeira vitória registrada cronologicamente."
        HistoricalInsightType.LATEST_WIN -> "Vitória mais recente registrada."
        HistoricalInsightType.LONGEST_UNBEATEN_INTERVAL ->
            "Tempo entre a primeira e a última partida da sequência invicta mais longa em duração."
        HistoricalInsightType.BIGGEST_WIN -> "Vitória com o maior saldo de gols."
    }

    private fun leaderDescription(playerCount: Int, metric: String) =
        if (playerCount > 1) "Co-líderes históricos em $metric." else "Liderança histórica em $metric."
}
