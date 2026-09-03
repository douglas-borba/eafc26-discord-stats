package com.eafc26.discordstats.presentation.comparison

import com.eafc26.discordstats.comparison.ComparedMatch
import com.eafc26.discordstats.comparison.ComparisonMetric
import com.eafc26.discordstats.comparison.ComparisonUnit
import com.eafc26.discordstats.comparison.MatchComparison
import com.eafc26.discordstats.comparison.MatchComparisonOption
import com.eafc26.discordstats.comparison.NumericMatchDifference
import com.eafc26.discordstats.domain.interpretation.AwardType
import com.eafc26.discordstats.domain.interpretation.MatchOutcome
import com.eafc26.discordstats.domain.match.CompetitionType
import com.eafc26.discordstats.domain.match.PlayerId
import com.eafc26.discordstats.domain.story.StoryContent
import com.eafc26.discordstats.domain.story.StoryType
import com.eafc26.discordstats.presentation.MatchPresentationTimeZone
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class MatchComparisonOptionsResponse(
    val status: String,
    val matches: List<MatchComparisonOptionView>,
)

data class MatchComparisonResponse(
    val status: String,
    val comparison: MatchComparisonView? = null,
    val missingMatchIds: List<String> = emptyList(),
    val message: String? = null,
)

data class MatchComparisonOptionView(
    val matchId: String,
    val label: String,
    val playedAt: Instant,
)

data class MatchComparisonView(
    val first: ComparedMatchView,
    val second: ComparedMatchView,
    val numericDifferences: List<NumericDifferenceView>,
    val awardDifferences: List<AwardDifferenceView>,
    val storyDifferences: List<StoryDifferenceView>,
)

data class ComparedMatchView(
    val matchId: String,
    val date: String,
    val competition: String?,
    val outcomeCode: String,
    val outcomeLabel: String,
    val outcomeIcon: String,
    val ourClub: String,
    val opponentClub: String,
    val score: String,
    val statistics: List<ComparedMetricView>,
    val awards: List<ComparedAwardView>,
    val stories: List<ComparedStoryView>,
)

data class ComparedMetricView(
    val metric: String,
    val label: String,
    val value: String?,
    val available: Boolean,
)

data class ComparedAwardView(
    val type: String,
    val label: String,
    val winner: String?,
)

data class ComparedStoryView(
    val type: String,
    val title: String,
    val narrativeKey: String,
    val players: List<String>,
    val facts: List<ComparisonFactView>,
)

data class ComparisonFactView(val label: String, val value: String)

data class NumericDifferenceView(
    val metric: String,
    val label: String,
    val firstValue: String?,
    val secondValue: String?,
    val delta: String?,
    val available: Boolean,
)

data class AwardDifferenceView(
    val type: String,
    val label: String,
    val firstWinner: String?,
    val secondWinner: String?,
    val changed: Boolean,
)

data class StoryDifferenceView(
    val type: String,
    val label: String,
    val firstCount: Int,
    val secondCount: Int,
    val delta: Int,
)

object MatchComparisonPresenter {
    private val dateFormatter = DateTimeFormatter.ofPattern(
        "dd/MM/yyyy 'às' HH:mm",
        Locale.forLanguageTag("pt-BR"),
    )

    fun option(option: MatchComparisonOption, zoneId: ZoneId = MatchPresentationTimeZone.BRAZIL) =
        MatchComparisonOptionView(
            matchId = option.matchId.value,
            playedAt = option.playedAt,
            label = buildString {
                append(dateFormatter.withZone(zoneId).format(option.playedAt))
                append(" · ")
                append(option.outcome.presentation().first)
                append(" ")
                append(option.ourScore)
                append("×")
                append(option.opponentScore)
                append(" · ")
                append(option.opponentClubName ?: "Adversário")
            },
        )

    fun comparison(
        comparison: MatchComparison,
        zoneId: ZoneId = MatchPresentationTimeZone.BRAZIL,
    ) = MatchComparisonView(
        first = comparison.first.presentation(zoneId),
        second = comparison.second.presentation(zoneId),
        numericDifferences = comparison.differences.numeric.map { it.presentation() },
        awardDifferences = comparison.differences.awards.map {
            AwardDifferenceView(
                type = it.awardType.name,
                label = it.awardType.label(),
                firstWinner = it.firstWinnerName ?: it.firstWinnerId?.value,
                secondWinner = it.secondWinnerName ?: it.secondWinnerId?.value,
                changed = it.changed,
            )
        },
        storyDifferences = comparison.differences.stories.map {
            StoryDifferenceView(
                type = it.storyType.name,
                label = it.storyType.label(),
                firstCount = it.firstCount,
                secondCount = it.secondCount,
                delta = it.delta,
            )
        },
    )

    private fun ComparedMatch.presentation(zoneId: ZoneId): ComparedMatchView {
        val outcomePresentation = outcome.presentation()
        val statisticValues = mapOf(
            ComparisonMetric.TEAM_AVERAGE_RATING to statistics.averageRating,
            ComparisonMetric.TEAM_GOALS to statistics.goals?.toBigDecimal(),
            ComparisonMetric.TEAM_ASSISTS to statistics.assists?.toBigDecimal(),
            ComparisonMetric.SHOTS to statistics.shots?.toBigDecimal(),
            ComparisonMetric.PASSES_COMPLETED to statistics.passesCompleted?.toBigDecimal(),
            ComparisonMetric.PASSES_ATTEMPTED to statistics.passesAttempted?.toBigDecimal(),
            ComparisonMetric.PASS_ACCURACY_PERCENT to statistics.passAccuracyPercent,
            ComparisonMetric.TACKLES_COMPLETED to statistics.tacklesCompleted?.toBigDecimal(),
            ComparisonMetric.TACKLES_ATTEMPTED to statistics.tacklesAttempted?.toBigDecimal(),
            ComparisonMetric.RED_CARDS to statistics.redCards?.toBigDecimal(),
            ComparisonMetric.GOALKEEPER_SAVES to statistics.goalkeeperSaves?.toBigDecimal(),
            ComparisonMetric.POSSESSION_PERCENT to statistics.possessionPercent,
        )
        return ComparedMatchView(
            matchId = matchId.value,
            date = dateFormatter.withZone(zoneId).format(playedAt),
            competition = competition?.label(),
            outcomeCode = outcome.name,
            outcomeLabel = outcomePresentation.first,
            outcomeIcon = outcomePresentation.second,
            ourClub = ourClubName ?: "Nosso clube",
            opponentClub = opponentClubName ?: "Adversário",
            score = "$ourScore × $opponentScore",
            statistics = statisticValues.map { (metric, value) ->
                ComparedMetricView(
                    metric = metric.name,
                    label = metric.label(),
                    value = value?.format(metric.unit()),
                    available = value != null,
                )
            },
            awards = awards.map {
                ComparedAwardView(
                    type = it.type.name,
                    label = it.type.label(),
                    winner = it.winnerName ?: it.winnerId?.value,
                )
            },
            stories = stories.map { compared ->
                ComparedStoryView(
                    type = compared.story.type.name,
                    title = compared.story.type.label(),
                    narrativeKey = compared.story.narrativeKey.value,
                    players = compared.story.involvedPlayers
                        .map { compared.involvedPlayerNames[it] ?: it.value }
                        .sorted(),
                    facts = compared.story.content.facts(compared.involvedPlayerNames),
                )
            },
        )
    }

    private fun NumericMatchDifference.presentation() = NumericDifferenceView(
        metric = metric.name,
        label = metric.label(),
        firstValue = firstValue?.format(unit),
        secondValue = secondValue?.format(unit),
        delta = delta?.signed(unit),
        available = delta != null,
    )

    private fun StoryContent.facts(names: Map<PlayerId, String>): List<ComparisonFactView> = when (this) {
        is StoryContent.MatchResult -> listOf(ComparisonFactView("Placar", "${ourScore.goals} × ${opponentScore.goals}"))
        is StoryContent.Award -> listOf(
            ComparisonFactView("Premiação", awardType.label()),
            ComparisonFactView("Vencedor", names[winnerId] ?: winnerId.value),
        )
        is StoryContent.Contributions -> players.map {
            ComparisonFactView(names[it.playerId] ?: it.playerId.value, "${it.goals} G · ${it.assists} A")
        }
        is StoryContent.Highlights -> buildList {
            teamAverageRating?.let { add(ComparisonFactView("Média", it.format(ComparisonUnit.RATING))) }
            players.forEach { add(ComparisonFactView(names[it.playerId] ?: it.playerId.value, it.rating.toPlainString())) }
        }
        is StoryContent.EaRecognizedMvp -> listOf(ComparisonFactView("Jogador", names[playerId] ?: playerId.value))
        is StoryContent.BagrePerformance -> listOf(
            ComparisonFactView("Jogador", names[playerId] ?: playerId.value),
            ComparisonFactView("Nota", rating.toPlainString()),
        )
        is StoryContent.OffensiveNarrative -> listOf(
            ComparisonFactView("Jogador", names[playerId] ?: playerId.value),
            ComparisonFactView("Produção", "$goals gol(s) em $shots finalização(ões)"),
        )
        is StoryContent.BehindThePlay -> listOf(
            ComparisonFactView("Jogador", names[playerId] ?: playerId.value),
            ComparisonFactView("Pré-assistências", secondAssists.toString()),
            ComparisonFactView("Passes em profundidade", throughPasses.toString()),
        )
        is StoryContent.OneOnOne -> listOf(
            ComparisonFactView("Jogador", names[playerId] ?: playerId.value),
            ComparisonFactView("Adversários superados", beats.toString()),
        )
        is StoryContent.RedCard -> listOf(ComparisonFactView(names[playerId] ?: playerId.value, "$redCards cartão(ões)"))
        is StoryContent.PassPrecision -> listOf(ComparisonFactView(names[playerId] ?: playerId.value, "$completed/$attempted ($accuracyPercent%)"))
        is StoryContent.LostMail -> listOf(ComparisonFactView(names[playerId] ?: playerId.value, "$playerAccuracyPercent% · time $teamAccuracyPercent%"))
        is StoryContent.Goalkeeper -> listOf(
            ComparisonFactView("Jogador", names[playerId] ?: playerId.value),
            ComparisonFactView("Atuação", "$saves defesa(s) · $goalsConceded sofrido(s)"),
        )
    }

    private fun BigDecimal.format(unit: ComparisonUnit): String = when (unit) {
        ComparisonUnit.COUNT -> setScale(0, RoundingMode.HALF_UP).toPlainString()
        ComparisonUnit.RATING -> setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
        ComparisonUnit.PERCENTAGE_POINTS -> "${setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()}%"
    }

    private fun BigDecimal.signed(unit: ComparisonUnit): String {
        val formatted = abs().format(unit)
        return when {
            signum() > 0 -> "+$formatted"
            signum() < 0 -> "−$formatted"
            else -> formatted
        }
    }

    private fun ComparisonMetric.unit() = when (this) {
        ComparisonMetric.TEAM_AVERAGE_RATING -> ComparisonUnit.RATING
        ComparisonMetric.PASS_ACCURACY_PERCENT,
        ComparisonMetric.POSSESSION_PERCENT -> ComparisonUnit.PERCENTAGE_POINTS
        else -> ComparisonUnit.COUNT
    }

    private fun ComparisonMetric.label() = when (this) {
        ComparisonMetric.GOALS_SCORED -> "Gols no placar"
        ComparisonMetric.GOALS_CONCEDED -> "Gols sofridos"
        ComparisonMetric.TEAM_AVERAGE_RATING -> "Média da equipe"
        ComparisonMetric.TEAM_GOALS -> "Gols dos jogadores"
        ComparisonMetric.TEAM_ASSISTS -> "Assistências"
        ComparisonMetric.SHOTS -> "Finalizações"
        ComparisonMetric.PASSES_COMPLETED -> "Passes completos"
        ComparisonMetric.PASSES_ATTEMPTED -> "Passes tentados"
        ComparisonMetric.PASS_ACCURACY_PERCENT -> "Precisão de passes"
        ComparisonMetric.TACKLES_COMPLETED -> "Desarmes completos"
        ComparisonMetric.TACKLES_ATTEMPTED -> "Desarmes tentados"
        ComparisonMetric.RED_CARDS -> "Cartões vermelhos"
        ComparisonMetric.GOALKEEPER_SAVES -> "Defesas do goleiro"
        ComparisonMetric.POSSESSION_PERCENT -> "Posse de bola"
    }

    private fun CompetitionType.label() = when (this) {
        CompetitionType.FRIENDLY -> "Amistoso"
        CompetitionType.LEAGUE -> "Liga"
        CompetitionType.PLAYOFF -> "Playoff"
    }

    private fun MatchOutcome.presentation() = when (this) {
        MatchOutcome.WIN -> "Vitória" to "🏆"
        MatchOutcome.DRAW -> "Empate" to "🤝"
        MatchOutcome.LOSS -> "Derrota" to "📉"
    }

    private fun AwardType.label() = when (this) {
        AwardType.CRAQUE -> "⭐ Craque"
        AwardType.BAGRE -> "📉 Menor Desempenho"
        AwardType.XERIFE -> "🛡️ Xerife"
    }

    private fun StoryType.label() = when (this) {
        StoryType.MATCH_OUTCOME -> "Resultado"
        StoryType.AWARD -> "Premiação"
        StoryType.GOALS -> "Gols"
        StoryType.ASSISTS -> "Assistências"
        StoryType.HIGHLIGHTS -> "Destaques"
        StoryType.BAGRE_PERFORMANCE -> "Menor Desempenho"
        StoryType.OFFENSIVE_NARRATIVE -> "Narrativa ofensiva"
        StoryType.BEHIND_THE_PLAY -> "Por Trás da Jogada"
        StoryType.ONE_ON_ONE -> "No Um Contra Um"
        StoryType.RED_CARD -> "Cartão vermelho"
        StoryType.PASS_PRECISION -> "Passe de Precisão"
        StoryType.LOST_MAIL -> "Correio Extraviado"
        StoryType.GOALKEEPER -> "Muralha"
        StoryType.EA_RECOGNIZED_MVP -> "MVP da EA"
    }
}
