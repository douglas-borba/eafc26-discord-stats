package com.eafc26.discordstats.presentation.history

import com.eafc26.discordstats.application.repository.CanonicalRepositoryMetadata
import com.eafc26.discordstats.canonical.CanonicalMatch
import com.eafc26.discordstats.domain.interpretation.AwardDecisionReason
import com.eafc26.discordstats.domain.interpretation.AwardMetrics
import com.eafc26.discordstats.domain.interpretation.AwardType
import com.eafc26.discordstats.domain.interpretation.BagreCriticism
import com.eafc26.discordstats.domain.interpretation.EligibilityStatus
import com.eafc26.discordstats.domain.interpretation.GoalkeeperArchetype
import com.eafc26.discordstats.domain.interpretation.MatchOutcome
import com.eafc26.discordstats.domain.interpretation.OffensiveNarrativeCategory
import com.eafc26.discordstats.domain.match.CompetitionType
import com.eafc26.discordstats.domain.match.OutfieldPosition
import com.eafc26.discordstats.domain.match.PlayerId
import com.eafc26.discordstats.domain.match.PlayerRole
import com.eafc26.discordstats.domain.story.Story
import com.eafc26.discordstats.domain.story.StoryContent
import com.eafc26.discordstats.domain.story.StoryPriority
import com.eafc26.discordstats.domain.story.StoryType
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class HistoricalMatchListResponse(
    val status: String,
    val matches: List<HistoricalMatchSummary>,
    val metadata: HistoricalRepositoryMetadata,
)

data class HistoricalMatchDetailResponse(
    val status: String,
    val match: HistoricalMatchDetail? = null,
    val message: String? = null,
)

data class HistoricalMatchSummary(
    val matchId: String,
    val playedAt: Instant,
    val dateLabel: String,
    val competition: String?,
    val ourClub: HistoricalClub,
    val opponentClub: HistoricalClub,
    val outcome: HistoricalOutcome,
)

data class HistoricalMatchDetail(
    val summary: HistoricalMatchSummary,
    val players: List<HistoricalPlayer>,
    val awards: List<HistoricalAward>,
    val stories: List<HistoricalStory>,
    val provenance: HistoricalCanonicalProvenance,
)

data class HistoricalClub(
    val id: String,
    val name: String,
    val score: Int,
)

data class HistoricalOutcome(
    val code: String,
    val label: String,
    val icon: String,
)

data class HistoricalPlayer(
    val id: String,
    val name: String,
    val role: String,
    val eligibility: String?,
    val durationSeconds: Long?,
    val rating: BigDecimal?,
    val goals: Int?,
    val assists: Int?,
    val shots: Int?,
    val passesCompleted: Int?,
    val passesAttempted: Int?,
    val tacklesCompleted: Int?,
    val tacklesAttempted: Int?,
    val redCards: Int?,
    val saves: Int?,
    val goalsConceded: Int?,
)

data class HistoricalAward(
    val type: String,
    val label: String,
    val icon: String,
    val awarded: Boolean,
    val winnerId: String?,
    val winnerName: String?,
    val reason: String,
    val facts: List<HistoricalFact>,
    val ruleIds: List<String>,
)

data class HistoricalStory(
    val type: String,
    val title: String,
    val narrativeKey: String,
    val priority: String,
    val involvedPlayers: List<String>,
    val facts: List<HistoricalFact>,
    val ruleIds: List<String>,
    val evidenceCount: Int,
)

data class HistoricalFact(
    val label: String,
    val value: String,
)

data class HistoricalCanonicalProvenance(
    val schemaVersion: Int,
    val engineVersion: String,
    val generatedAt: Instant,
)

data class HistoricalRepositoryMetadata(
    val matchCount: Int,
    val oldestMatchAt: Instant?,
    val newestMatchAt: Instant?,
    val lastGeneratedAt: Instant?,
    val schemaVersions: List<Int>,
    val engineVersions: List<String>,
)

/**
 * Presentation-only projection of persisted canonical records. It translates
 * labels and formatting, but never derives a football decision.
 */
object HistoricalMatchPresenter {
    private val dateFormatter = DateTimeFormatter.ofPattern(
        "dd/MM/yyyy 'às' HH:mm",
        Locale.forLanguageTag("pt-BR"),
    )

    fun summary(
        canonical: CanonicalMatch,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): HistoricalMatchSummary {
        val interpretation = canonical.interpretation
        val result = interpretation.result
        val participants = canonical.footballMatch.participants.associateBy { it.club.id }
        val ourClub = requireNotNull(participants[result.ourClub]) {
            "Canonical match is missing the interpreted perspective club"
        }
        val opponent = requireNotNull(participants[result.opponentClub]) {
            "Canonical match is missing the interpreted opponent club"
        }

        return HistoricalMatchSummary(
            matchId = canonical.matchId.value,
            playedAt = canonical.footballMatch.playedAt,
            dateLabel = dateFormatter.withZone(zoneId).format(canonical.footballMatch.playedAt),
            competition = canonical.footballMatch.competition?.label(),
            ourClub = HistoricalClub(
                id = ourClub.club.id.value,
                name = ourClub.club.name?.value ?: "Nosso clube",
                score = result.ourScore.goals,
            ),
            opponentClub = HistoricalClub(
                id = opponent.club.id.value,
                name = opponent.club.name?.value ?: "Adversário",
                score = result.opponentScore.goals,
            ),
            outcome = result.outcome.presentation(),
        )
    }

    fun detail(
        canonical: CanonicalMatch,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): HistoricalMatchDetail {
        val interpretation = canonical.interpretation
        val ourPerformance = requireNotNull(
            canonical.footballMatch.participants.firstOrNull {
                it.club.id == interpretation.perspectiveClubId
            }
        )
        val playersById = ourPerformance.players.associateBy { it.player.id }
        val namesById = playersById.mapValues { (_, performance) ->
            performance.player.preferredDisplayName?.value ?: performance.player.id.value
        }
        val eligibilityById = interpretation.eligibility.decisions.associateBy { it.playerId }

        val players = ourPerformance.players.map { performance ->
            val eligibility = eligibilityById[performance.player.id]
            HistoricalPlayer(
                id = performance.player.id.value,
                name = namesById.getValue(performance.player.id),
                role = performance.role.label(),
                eligibility = eligibility?.status?.label(),
                durationSeconds = performance.participation.duration?.seconds,
                rating = performance.rating?.value,
                goals = performance.attacking.goals,
                assists = performance.attacking.assists,
                shots = performance.attacking.shots,
                passesCompleted = performance.passing.completed,
                passesAttempted = performance.passing.attempted,
                tacklesCompleted = performance.defending.tacklesCompleted,
                tacklesAttempted = performance.defending.tacklesAttempted,
                redCards = performance.discipline.redCards,
                saves = performance.goalkeeping?.saves,
                goalsConceded = performance.goalkeeping?.goalsConceded,
            )
        }

        val awards = listOf(
            interpretation.awards.craque,
            interpretation.awards.bagre,
            interpretation.awards.xerife,
        ).map { decision ->
            val bagreFacts = if (decision.type == AwardType.BAGRE) {
                interpretation.features.bagrePerformance
                    ?.takeIf { it.playerId == decision.winnerId }
                    ?.let {
                        buildList {
                            add(HistoricalFact("Nota", it.rating.plain()))
                            add(HistoricalFact("Motivo", it.criticism.label()))
                            it.tackleSummary?.let { summary ->
                                add(HistoricalFact(
                                    "Desarmes",
                                    "${summary.completed}/${summary.attempted} (${summary.accuracyPercent}%)",
                                ))
                            }
                            it.passingSummary?.let { summary ->
                                add(HistoricalFact(
                                    "Passes",
                                    "${summary.completed}/${summary.attempted} (${summary.accuracyPercent}%)",
                                ))
                            }
                        }
                    }.orEmpty()
            } else {
                decision.metrics.facts()
            }

            HistoricalAward(
                type = decision.type.name,
                label = decision.type.label(),
                icon = decision.type.icon(),
                awarded = decision.awarded,
                winnerId = decision.winnerId?.value,
                winnerName = decision.winnerId?.let(namesById::get),
                reason = decision.reason.label(),
                facts = bagreFacts,
                ruleIds = listOf(decision.rule.id.value),
            )
        }

        return HistoricalMatchDetail(
            summary = summary(canonical, zoneId),
            players = players,
            awards = awards,
            stories = canonical.stories.stories.map { it.presentation(namesById) },
            provenance = HistoricalCanonicalProvenance(
                schemaVersion = canonical.schemaVersion.value,
                engineVersion = canonical.engineVersion.value,
                generatedAt = canonical.generatedAt,
            ),
        )
    }

    fun metadata(metadata: CanonicalRepositoryMetadata) = HistoricalRepositoryMetadata(
        matchCount = metadata.matchCount,
        oldestMatchAt = metadata.oldestMatchAt,
        newestMatchAt = metadata.newestMatchAt,
        lastGeneratedAt = metadata.lastGeneratedAt,
        schemaVersions = metadata.schemaVersions.map { it.value }.sorted(),
        engineVersions = metadata.engineVersions.map { it.value }.sorted(),
    )

    private fun Story.presentation(namesById: Map<PlayerId, String>) = HistoricalStory(
        type = type.name,
        title = type.label(),
        narrativeKey = narrativeKey.value,
        priority = priority.label(),
        involvedPlayers = involvedPlayers.map { namesById[it] ?: it.value }.sorted(),
        facts = content.facts(namesById),
        ruleIds = provenance.rules.map { it.id.value },
        evidenceCount = provenance.evidence.size,
    )

    private fun StoryContent.facts(namesById: Map<PlayerId, String>): List<HistoricalFact> =
        when (this) {
            is StoryContent.MatchResult -> listOf(
                HistoricalFact("Resultado", outcome.presentation().label),
                HistoricalFact("Placar", "${ourScore.goals} × ${opponentScore.goals}"),
            )
            is StoryContent.Award -> buildList {
                add(HistoricalFact("Premiação", awardType.label()))
                add(HistoricalFact("Vencedor", namesById[winnerId] ?: winnerId.value))
                add(HistoricalFact("Critério", reason.label()))
                addAll(metrics.facts())
            }
            is StoryContent.Contributions -> players.map {
                HistoricalFact(
                    namesById[it.playerId] ?: it.playerId.value,
                    "${it.goals} gol(s), ${it.assists} assistência(s)",
                )
            }
            is StoryContent.Highlights -> buildList {
                teamAverageRating?.let { add(HistoricalFact("Média do time", it.plain())) }
                players.forEach {
                    add(HistoricalFact(namesById[it.playerId] ?: it.playerId.value, it.rating.plain()))
                }
            }
            is StoryContent.EaRecognizedMvp -> listOf(
                HistoricalFact("Jogador", namesById[playerId] ?: playerId.value),
                HistoricalFact("Nota", rating?.plain() ?: "—"),
            )
            is StoryContent.BagrePerformance -> buildList {
                add(HistoricalFact("Jogador", namesById[playerId] ?: playerId.value))
                add(HistoricalFact("Nota", rating.plain()))
                add(HistoricalFact("Motivo", criticism.label()))
            }
            is StoryContent.OffensiveNarrative -> listOf(
                HistoricalFact("Jogador", namesById[playerId] ?: playerId.value),
                HistoricalFact("Finalizações", shots.toString()),
                HistoricalFact("Gols", goals.toString()),
                HistoricalFact("Leitura", category.label()),
            )
            is StoryContent.RedCard -> listOf(
                HistoricalFact("Jogador", namesById[playerId] ?: playerId.value),
                HistoricalFact("Cartões vermelhos", redCards.toString()),
            )
            is StoryContent.PassPrecision -> listOf(
                HistoricalFact("Jogador", namesById[playerId] ?: playerId.value),
                HistoricalFact("Passes", "$completed/$attempted ($accuracyPercent%)"),
            )
            is StoryContent.LostMail -> listOf(
                HistoricalFact("Jogador", namesById[playerId] ?: playerId.value),
                HistoricalFact("Passes", "$completed/$attempted ($playerAccuracyPercent%)"),
                HistoricalFact("Média do time", "$teamAccuracyPercent%"),
                HistoricalFact("Diferença", "$deltaPercent p.p."),
            )
            is StoryContent.Goalkeeper -> listOf(
                HistoricalFact("Jogador", namesById[playerId] ?: playerId.value),
                HistoricalFact("Defesas", saves.toString()),
                HistoricalFact("Gols sofridos", goalsConceded.toString()),
                HistoricalFact("Leitura", archetype.label()),
            )
        }

    private fun AwardMetrics?.facts(): List<HistoricalFact> = when (this) {
        null -> emptyList()
        is AwardMetrics.Craque -> listOf(
            HistoricalFact("Nota", rating?.plain() ?: "—"),
            HistoricalFact("Gols", goals.toString()),
            HistoricalFact("Assistências", assists.toString()),
            HistoricalFact("MVP da EA", if (eaManOfTheMatch) "Sim" else "Não"),
        )
        is AwardMetrics.Xerife -> listOf(
            HistoricalFact("Desarmes", "$tacklesCompleted/$tacklesAttempted ($accuracyPercent%)"),
            HistoricalFact("Impacto defensivo", defensiveImpactScore.plain()),
        )
    }

    private fun BigDecimal.plain(): String = stripTrailingZeros().toPlainString()

    private fun MatchOutcome.presentation() = when (this) {
        MatchOutcome.WIN -> HistoricalOutcome("WIN", "Vitória", "🏆")
        MatchOutcome.DRAW -> HistoricalOutcome("DRAW", "Empate", "🤝")
        MatchOutcome.LOSS -> HistoricalOutcome("LOSS", "Derrota", "📉")
    }

    private fun CompetitionType.label() = when (this) {
        CompetitionType.FRIENDLY -> "Amistoso"
        CompetitionType.LEAGUE -> "Liga"
        CompetitionType.PLAYOFF -> "Playoff"
    }

    private fun PlayerRole.label() = when (this) {
        PlayerRole.Goalkeeper -> "Goleiro"
        is PlayerRole.Outfield -> position?.label() ?: "Linha"
        PlayerRole.Unknown -> "Não informado"
    }

    private fun OutfieldPosition.label() = when (this) {
        OutfieldPosition.DEFENDER -> "Defensor"
        OutfieldPosition.MIDFIELDER -> "Meio-campista"
        OutfieldPosition.FORWARD -> "Atacante"
    }

    private fun EligibilityStatus.label() = when (this) {
        EligibilityStatus.ELIGIBLE -> "Sim"
        EligibilityStatus.INELIGIBLE -> "Não"
    }

    private fun AwardType.label() = when (this) {
        AwardType.CRAQUE -> "Craque"
        AwardType.BAGRE -> "Bagre"
        AwardType.XERIFE -> "Xerife"
    }

    private fun AwardType.icon() = when (this) {
        AwardType.CRAQUE -> "⭐"
        AwardType.BAGRE -> "🐟"
        AwardType.XERIFE -> "🛡️"
    }

    private fun AwardDecisionReason.label() = when (this) {
        AwardDecisionReason.EA_MAN_OF_THE_MATCH -> "Eleito melhor em campo pela EA"
        AwardDecisionReason.HIGHEST_RATING -> "Maior nota elegível"
        AwardDecisionReason.LOWEST_ELIGIBLE_RATING -> "Menor nota elegível"
        AwardDecisionReason.HIGHEST_DEFENSIVE_IMPACT -> "Maior impacto defensivo"
        AwardDecisionReason.NO_ELIGIBLE_CANDIDATE -> "Sem candidato elegível"
    }

    private fun BagreCriticism.label() = when (this) {
        BagreCriticism.TACKLING -> "Desarmes"
        BagreCriticism.PASSING -> "Passes"
        BagreCriticism.RATING -> "Nota"
    }

    private fun StoryType.label() = when (this) {
        StoryType.MATCH_OUTCOME -> "Resultado"
        StoryType.AWARD -> "Premiação"
        StoryType.GOALS -> "Gols"
        StoryType.ASSISTS -> "Assistências"
        StoryType.HIGHLIGHTS -> "Destaques"
        StoryType.BAGRE_PERFORMANCE -> "Atuação do Bagre"
        StoryType.OFFENSIVE_NARRATIVE -> "Narrativa ofensiva"
        StoryType.RED_CARD -> "Cartão vermelho"
        StoryType.PASS_PRECISION -> "Passe de Precisão"
        StoryType.LOST_MAIL -> "Correio Extraviado"
        StoryType.GOALKEEPER -> "Muralha"
        StoryType.EA_RECOGNIZED_MVP -> "MVP da EA"
    }

    private fun StoryPriority.label() = when (this) {
        StoryPriority.PRIMARY -> "Principal"
        StoryPriority.SECONDARY -> "Secundária"
    }

    private fun OffensiveNarrativeCategory.label() = when (this) {
        OffensiveNarrativeCategory.DECISIVE -> "Decisivo"
        OffensiveNarrativeCategory.COULD_HAVE_DECIDED -> "Poderia ter decidido"
        OffensiveNarrativeCategory.FELL_SHORT -> "Ficou abaixo do esperado"
        OffensiveNarrativeCategory.LACKED_COMPOSURE -> "Faltou precisão"
        OffensiveNarrativeCategory.CONSTANT_THREAT -> "Ameaça constante"
    }

    private fun GoalkeeperArchetype.label() = when (this) {
        GoalkeeperArchetype.WALL -> "Muralha"
        GoalkeeperArchetype.SOLID -> "Seguro"
        GoalkeeperArchetype.UNDER_SIEGE -> "Sob pressão"
        GoalkeeperArchetype.POOR -> "Atuação abaixo"
        GoalkeeperArchetype.QUIET -> "Pouco exigido"
    }
}
