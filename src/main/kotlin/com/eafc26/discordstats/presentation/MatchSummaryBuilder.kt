package com.eafc26.discordstats.presentation

import com.eafc26.discordstats.config.PhraseBank
import com.eafc26.discordstats.config.PhraseCategory
import com.eafc26.discordstats.domain.interpretation.AwardDecisionReason
import com.eafc26.discordstats.domain.interpretation.AwardMetrics
import com.eafc26.discordstats.domain.interpretation.AwardType
import com.eafc26.discordstats.domain.interpretation.BagreCriticism
import com.eafc26.discordstats.domain.interpretation.GoalkeeperArchetype
import com.eafc26.discordstats.domain.interpretation.GoalkeeperNarrativeVariant
import com.eafc26.discordstats.domain.interpretation.MatchInterpretation
import com.eafc26.discordstats.domain.interpretation.MatchOutcome as DomainOutcome
import com.eafc26.discordstats.domain.match.FootballMatch
import com.eafc26.discordstats.domain.match.PlayerId
import com.eafc26.discordstats.domain.match.PlayerMatchPerformance
import com.eafc26.discordstats.domain.match.PlayerRole
import com.eafc26.discordstats.domain.story.MatchStories
import com.eafc26.discordstats.domain.story.StoryContent
import com.eafc26.discordstats.domain.story.StoryType
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.Random
import kotlin.math.abs

/**
 * Dashboard renderer for the canonical match pipeline.
 *
 * It formats already-made decisions and never reads EA DTOs or invokes a
 * football selector.
 */
@Component
class MatchSummaryBuilder(
    private val phraseBank: PhraseBank,
) {
    private val dateFmt = DateTimeFormatter.ofPattern("dd MMM yyyy '•' HH:mm", PT_BR)

    fun build(
        footballMatch: FootballMatch,
        interpretation: MatchInterpretation,
        stories: MatchStories,
        zoneId: ZoneId = MatchPresentationTimeZone.BRAZIL,
        forceRandomPhrases: Boolean = false,
    ): MatchSummaryPresentation {
        require(footballMatch.id == interpretation.matchId && stories.matchId == footballMatch.id) {
            "Match facts, interpretation and stories must belong to the same match"
        }
        val random = if (forceRandomPhrases) Random() else null
        val ourClub = footballMatch.participants.first { it.club.id == interpretation.perspectiveClubId }
        val opponent = footballMatch.participants.first { it.club.id == interpretation.result.opponentClub }
        val players = ourClub.players.associateBy { it.player.id }
        val storyByType = stories.stories.groupBy { it.type }
        val result = interpretation.result

        return MatchSummaryPresentation(
            ourName = ourClub.club.name?.value ?: "Nós",
            oppName = opponent.club.name?.value ?: "Adversário",
            ourScore = result.ourScore.goals,
            oppScore = result.opponentScore.goals,
            outcome = outcome(result.outcome),
            date = footballMatch.playedAt.atZone(zoneId).format(dateFmt),
            timestamp = footballMatch.playedAt.toString(),
            matchId = footballMatch.id.value,
            completionStatus = footballMatch.completion.status.name,
            dnfClubId = footballMatch.completion.dnfClubId?.value,
            goals = goals(storyByType[StoryType.GOALS], players),
            assists = assists(storyByType[StoryType.ASSISTS], players),
            highlights = highlights(storyByType[StoryType.HIGHLIGHTS], players),
            craque = craque(storyByType[StoryType.AWARD], players, footballMatch.id.value, random),
            offensiveNarratives = offensive(storyByType[StoryType.OFFENSIVE_NARRATIVE], players),
            behindThePlay = behindThePlay(
                storyByType[StoryType.BEHIND_THE_PLAY],
                players,
                footballMatch.id.value,
                random,
            ),
            oneOnOne = oneOnOne(
                storyByType[StoryType.ONE_ON_ONE],
                players,
                footballMatch.id.value,
                random,
            ),
            bagre = bagre(
                storyByType[StoryType.BAGRE_PERFORMANCE],
                players,
                footballMatch.id.value,
                random,
            ),
            redCard = redCard(storyByType[StoryType.RED_CARD], players, footballMatch.id.value, random),
            xerife = xerife(storyByType[StoryType.AWARD], players, footballMatch.id.value, random),
            passePrecisao = passPrecision(
                storyByType[StoryType.PASS_PRECISION],
                players,
                footballMatch.id.value,
                random,
            ),
            correioExtraviado = lostMail(
                storyByType[StoryType.LOST_MAIL],
                players,
                footballMatch.id.value,
                random,
            ),
            muralha = goalkeeper(
                storyByType[StoryType.GOALKEEPER],
                players,
                footballMatch.id.value,
                random,
            ),
            allPlayers = if (footballMatch.completion.hasCompleteSportingStatistics) buildAllPlayers(ourClub.players) else emptyList(),
        )
    }

    private fun outcome(value: DomainOutcome): MatchOutcome = when (value) {
        DomainOutcome.WIN -> MatchOutcome("🟢", "Vitória", 0x2ECC71, OutcomeType.WIN)
        DomainOutcome.DRAW -> MatchOutcome("🟡", "Empate", 0x95A5A6, OutcomeType.DRAW)
        DomainOutcome.LOSS -> MatchOutcome("🔴", "Derrota", 0xE74C3C, OutcomeType.LOSS)
    }

    private fun goals(
        stories: List<com.eafc26.discordstats.domain.story.Story>?,
        players: Map<PlayerId, PlayerMatchPerformance>,
    ): GoalsSection? {
        val content = stories?.singleOrNull()?.content as? StoryContent.Contributions ?: return null
        return GoalsSection(content.players.map { PlayerGoal(name(players.getValue(it.playerId)), it.goals) })
    }

    private fun assists(
        stories: List<com.eafc26.discordstats.domain.story.Story>?,
        players: Map<PlayerId, PlayerMatchPerformance>,
    ): AssistsSection? {
        val content = stories?.singleOrNull()?.content as? StoryContent.Contributions ?: return null
        return AssistsSection(content.players.map { PlayerAssist(name(players.getValue(it.playerId)), it.assists) })
    }

    private fun highlights(
        stories: List<com.eafc26.discordstats.domain.story.Story>?,
        players: Map<PlayerId, PlayerMatchPerformance>,
    ): HighlightsSection? {
        val content = stories?.singleOrNull()?.content as? StoryContent.Highlights ?: return null
        val medals = listOf("🥇", "🥈", "🥉")
        return HighlightsSection(
            top3 = content.players.mapIndexed { index, rated ->
                TopPlayer(medals[index], name(players.getValue(rated.playerId)), fmtRating(rated.rating))
            },
            teamAverage = content.teamAverageRating?.let(::fmtRating),
        )
    }

    private fun award(
        stories: List<com.eafc26.discordstats.domain.story.Story>?,
        type: AwardType,
    ): StoryContent.Award? = stories
        ?.mapNotNull { it.content as? StoryContent.Award }
        ?.singleOrNull { it.awardType == type }

    private fun craque(
        stories: List<com.eafc26.discordstats.domain.story.Story>?,
        players: Map<PlayerId, PlayerMatchPerformance>,
        matchId: String,
        random: Random?,
    ): CraqueSection? {
        val content = award(stories, AwardType.CRAQUE) ?: return null
        val metrics = content.metrics as AwardMetrics.Craque
        val playerName = name(players.getValue(content.winnerId))
        val parts = mutableListOf("Nota ${metrics.rating?.let(::fmtRating) ?: "N/D"}")
        val stats = mutableListOf<String>()
        if (metrics.goals > 0) stats += plural(metrics.goals, "gol", "gols")
        if (metrics.assists > 0) stats += plural(metrics.assists, "assistência", "assistências")
        if (stats.isNotEmpty()) parts += stats.joinToString(" e ")
        parts += if (content.reason == AwardDecisionReason.EA_MAN_OF_THE_MATCH) {
            "Craque da Partida (EA)"
        } else {
            "Melhor nota da partida"
        }
        return CraqueSection(
            playerName,
            parts.joinToString(" • "),
            phrase(PhraseCategory.MVP, matchId, playerName, random),
        )
    }

    private fun bagre(
        stories: List<com.eafc26.discordstats.domain.story.Story>?,
        players: Map<PlayerId, PlayerMatchPerformance>,
        matchId: String,
        random: Random?,
    ): BagreSection? {
        val content = stories?.singleOrNull()?.content as? StoryContent.BagrePerformance ?: return null
        val player = players.getValue(content.playerId)
        val playerName = name(player)
        val tackle = content.tackleSummary?.let {
            "${it.completed}/${it.attempted} certos (${it.accuracyPercent}%)"
        }
        val passing = content.passingSummary?.let {
            "${it.completed}/${it.attempted} certos (${it.accuracyPercent}%) · " +
                "${it.attempted - it.completed} errados"
        }
        val category = when (content.criticism) {
            BagreCriticism.TACKLING -> PhraseCategory.TACKLE
            BagreCriticism.PASSING -> PhraseCategory.PASS
            BagreCriticism.RATING -> PhraseCategory.RATING
        }
        return BagreSection(
            playerName,
            fmtRating(content.rating),
            bagreReason(content.criticism),
            tackle,
            passing,
            phrase(category, matchId, bagreSeed(seedName(player), content.criticism), random),
        )
    }

    private fun bagreReason(criticism: BagreCriticism): String = when (criticism) {
        BagreCriticism.TACKLING -> "Baixo aproveitamento em desarmes com volume relevante."
        BagreCriticism.PASSING -> "Baixo aproveitamento em passes com volume relevante."
        BagreCriticism.RATING -> "Nota abaixo do nível esperado na partida."
    }

    private fun redCard(
        stories: List<com.eafc26.discordstats.domain.story.Story>?,
        players: Map<PlayerId, PlayerMatchPerformance>,
        matchId: String,
        random: Random?,
    ): RedCardSection? {
        val content = stories?.singleOrNull()?.content as? StoryContent.RedCard ?: return null
        val playerName = name(players.getValue(content.playerId))
        return RedCardSection(
            playerName,
            content.redCards,
            phrase(PhraseCategory.PERDEU_A_CABECA, matchId, playerName, random),
        )
    }

    private fun xerife(
        stories: List<com.eafc26.discordstats.domain.story.Story>?,
        players: Map<PlayerId, PlayerMatchPerformance>,
        matchId: String,
        random: Random?,
    ): XerifeSection? {
        val content = award(stories, AwardType.XERIFE) ?: return null
        val metrics = content.metrics as AwardMetrics.Xerife
        val playerName = name(players.getValue(content.winnerId))
        return XerifeSection(
            playerName,
            metrics.tacklesCompleted,
            metrics.tacklesAttempted,
            metrics.accuracyPercent,
            phrase(PhraseCategory.XERIFE, matchId, playerName, random),
            metrics.interceptions,
        )
    }

    private fun behindThePlay(
        stories: List<com.eafc26.discordstats.domain.story.Story>?,
        players: Map<PlayerId, PlayerMatchPerformance>,
        matchId: String,
        random: Random?,
    ): BehindThePlaySection? {
        val content = stories?.singleOrNull()?.content as? StoryContent.BehindThePlay ?: return null
        val playerName = name(players.getValue(content.playerId))
        return BehindThePlaySection(
            playerName,
            content.secondAssists,
            content.throughPasses,
            phrase(PhraseCategory.BEHIND_THE_PLAY, matchId, playerName, random),
        )
    }

    private fun oneOnOne(
        stories: List<com.eafc26.discordstats.domain.story.Story>?,
        players: Map<PlayerId, PlayerMatchPerformance>,
        matchId: String,
        random: Random?,
    ): OneOnOneSection? {
        val content = stories?.singleOrNull()?.content as? StoryContent.OneOnOne ?: return null
        val playerName = name(players.getValue(content.playerId))
        return OneOnOneSection(
            playerName,
            content.beats,
            content.dribblesCompleted,
            phrase(PhraseCategory.ONE_ON_ONE, matchId, playerName, random),
        )
    }

    private fun offensive(
        stories: List<com.eafc26.discordstats.domain.story.Story>?,
        players: Map<PlayerId, PlayerMatchPerformance>,
    ): List<OffensiveNarrativeSection> = stories.orEmpty().map {
        val content = it.content as StoryContent.OffensiveNarrative
        val presentation = offensivePresentation(content.category)
        OffensiveNarrativeSection(
            name(players.getValue(content.playerId)),
            content.shots,
            content.goals,
            presentation.first,
            presentation.second,
            presentation.third,
        )
    }

    private fun passPrecision(
        stories: List<com.eafc26.discordstats.domain.story.Story>?,
        players: Map<PlayerId, PlayerMatchPerformance>,
        matchId: String,
        random: Random?,
    ): PassePrecisaoSection? {
        val content = stories?.singleOrNull()?.content as? StoryContent.PassPrecision ?: return null
        val playerName = name(players.getValue(content.playerId))
        return PassePrecisaoSection(
            playerName,
            content.completed,
            content.attempted,
            content.accuracyPercent,
            phrase(PhraseCategory.PASSE_PRECISAO, matchId, playerName, random),
        )
    }

    private fun lostMail(
        stories: List<com.eafc26.discordstats.domain.story.Story>?,
        players: Map<PlayerId, PlayerMatchPerformance>,
        matchId: String,
        random: Random?,
    ): CorreioExtraviadoSection? {
        val content = stories?.singleOrNull()?.content as? StoryContent.LostMail ?: return null
        val playerName = name(players.getValue(content.playerId))
        return CorreioExtraviadoSection(
            playerName,
            content.attempted - content.completed,
            content.playerAccuracyPercent,
            content.teamAccuracyPercent,
            content.deltaPercent,
            phrase(PhraseCategory.CORREIO, matchId, playerName, random),
        )
    }

    private fun goalkeeper(
        stories: List<com.eafc26.discordstats.domain.story.Story>?,
        players: Map<PlayerId, PlayerMatchPerformance>,
        matchId: String,
        random: Random?,
    ): MuralhaSection? {
        val content = stories?.singleOrNull()?.content as? StoryContent.Goalkeeper ?: return null
        val player = players.getValue(content.playerId)
        val playerName = name(player)
        return MuralhaSection(
            playerName,
            content.saves,
            content.goalsConceded,
            content.archetype,
            goalkeeperTitle(content.archetype),
            goalkeeperPhrase(content, matchId, seedName(player), random),
        )
    }

    private fun name(player: PlayerMatchPerformance): String =
        player.player.preferredDisplayName?.value
            ?: if (player.role == PlayerRole.Goalkeeper) "Goleiro BOT" else "Desconhecido"

    private fun seedName(player: PlayerMatchPerformance): String =
        player.player.platformName?.value
            ?: if (player.role == PlayerRole.Goalkeeper) "Goleiro BOT" else "Desconhecido"

    /**
     * Builds a list of all players who participated in the match with their ratings.
     * Excludes sentinel ratings (3.0) which indicate abandonment.
     */
    private fun buildAllPlayers(players: List<PlayerMatchPerformance>): List<PlayerRating> {
        val SENTINEL_RATING = BigDecimal("3.0")
        return players
            .filter { it.rating != null }
            .filter { it.rating?.value != SENTINEL_RATING }
            .map { player ->
                PlayerRating(
                    name = name(player),
                    rating = fmtRating(player.rating!!.value),
                    played = true,
                )
            }
    }

    private fun phrase(
        category: PhraseCategory,
        matchId: String,
        seed: String,
        random: Random?,
    ): String = pick(phraseBank.get(category), matchId, seed, random)

    private fun pick(values: List<String>, matchId: String, seed: String, random: Random?): String =
        if (random != null) {
            values[random.nextInt(values.size)]
        } else {
            values[(abs(matchId.hashCode().toLong() + seed.hashCode().toLong()) % values.size).toInt()]
        }

    private fun bagreSeed(name: String, criticism: BagreCriticism): String = when (criticism) {
        BagreCriticism.TACKLING -> "${name}desarmes"
        BagreCriticism.PASSING -> "${name}passes"
        BagreCriticism.RATING -> name
    }

    private fun goalkeeperPhrase(
        content: StoryContent.Goalkeeper,
        matchId: String,
        name: String,
        random: Random?,
    ): String {
        if (content.narrativeVariant == GoalkeeperNarrativeVariant.POOR_MILD ||
            content.narrativeVariant == GoalkeeperNarrativeVariant.POOR_MODERATE ||
            content.narrativeVariant == GoalkeeperNarrativeVariant.POOR_SEVERE
        ) {
            val category = goalkeeperCategory(content.archetype)
            val custom = phraseBank.get(category)
            if (custom.isNotEmpty() && custom != category.defaults) {
                return pick(custom, matchId, name, random)
            }
        }
        val contextual = GOALKEEPER_CONTEXT[content.narrativeVariant]
        if (contextual != null) return pick(contextual, matchId, name, random)
        return phrase(goalkeeperCategory(content.archetype), matchId, name, random)
    }

    private fun goalkeeperCategory(value: GoalkeeperArchetype): PhraseCategory = when (value) {
        GoalkeeperArchetype.WALL -> PhraseCategory.GOALKEEPER_WALL
        GoalkeeperArchetype.SOLID -> PhraseCategory.GOALKEEPER_SOLID
        GoalkeeperArchetype.UNDER_SIEGE -> PhraseCategory.GOALKEEPER_UNDER_SIEGE
        GoalkeeperArchetype.POOR -> PhraseCategory.GOALKEEPER_POOR
        GoalkeeperArchetype.QUIET -> PhraseCategory.GOALKEEPER_QUIET
    }

    private fun goalkeeperTitle(value: GoalkeeperArchetype): String = when (value) {
        GoalkeeperArchetype.WALL -> "🧱 Paredão"
        GoalkeeperArchetype.SOLID -> "🧤 Seguro"
        GoalkeeperArchetype.UNDER_SIEGE -> "💣 Bombardeado"
        GoalkeeperArchetype.POOR -> "🥬 Mão de Alface"
        GoalkeeperArchetype.QUIET -> "🤷 Discreto"
    }

    private fun offensivePresentation(
        category: com.eafc26.discordstats.domain.interpretation.OffensiveNarrativeCategory,
    ): Triple<String, String, String> = when (category) {
        com.eafc26.discordstats.domain.interpretation.OffensiveNarrativeCategory.COULD_HAVE_DECIDED ->
            Triple("PODERIA TER DECIDIDO", "🎯", "Teve chances para mudar o resultado, mas deixou a vitória escapar.")
        com.eafc26.discordstats.domain.interpretation.OffensiveNarrativeCategory.LACKED_COMPOSURE ->
            Triple("FALTOU CAPRICHO", "🎯", "As chances apareceram, mas a pontaria não ajudou.")
        com.eafc26.discordstats.domain.interpretation.OffensiveNarrativeCategory.DECISIVE ->
            Triple("DECISIVO", "⚡", "Quando a chance apareceu, ele resolveu.")
        com.eafc26.discordstats.domain.interpretation.OffensiveNarrativeCategory.FELL_SHORT ->
            Triple("FICOU NO QUASE", "😬", "Criou bastante, mas faltou transformar mais chances em gol.")
        com.eafc26.discordstats.domain.interpretation.OffensiveNarrativeCategory.CONSTANT_THREAT ->
            Triple("PERIGO CONSTANTE", "🔥", "A defesa adversária não teve sossego quando ele recebeu a bola.")
    }

    private fun fmtRating(value: BigDecimal): String =
        "%.2f".format(value.toDouble()).replace('.', ',')

    private fun plural(count: Int, singular: String, plural: String) =
        if (count == 1) "$count $singular" else "$count $plural"

    companion object {
        private val PT_BR = Locale.forLanguageTag("pt-BR")
        private val GOALKEEPER_CONTEXT = mapOf(
            GoalkeeperNarrativeVariant.REFLEX to listOf(
                "Fez defesas praticamente impossíveis.",
                "Os reflexos fizeram a diferença.",
                "Respondeu rápido em cada situação de perigo.",
                "O instinto salvou o time mais de uma vez.",
                "Tirou de onde não havia mais tempo para pensar.",
            ),
            GoalkeeperNarrativeVariant.PARRY to listOf(
                "Espalmou tudo que apareceu.",
                "Tirou o perigo com firmeza em cada oportunidade.",
                "As espalmadas mantiveram o time vivo.",
                "Cada intervenção foi precisa e segura.",
                "A luva trabalhou em horas extras.",
            ),
            GoalkeeperNarrativeVariant.CROSS to listOf(
                "Dominou completamente a área.",
                "Saiu com autoridade em cada bola aérea.",
                "O domínio da área foi total.",
                "Transmitiu segurança nas saídas.",
                "Limpou a área com eficiência.",
            ),
            GoalkeeperNarrativeVariant.GOOD_DIRECTION to listOf(
                "Leu muito bem as finalizações.",
                "Antecipou os chutes com inteligência.",
                "Posicionamento impecável nas defesas.",
                "Sabia para onde a bola ia antes do chute.",
                "A leitura de jogo foi o grande diferencial.",
            ),
            GoalkeeperNarrativeVariant.POOR_MILD to listOf(
                "Não conseguiu passar segurança.",
                "A atuação deixou a desejar.",
                "Houve momentos de hesitação que custaram caro.",
                "Não foi o jogo que o time precisava do goleiro.",
                "A segurança que o setor precisava não apareceu.",
                "Oscilou mais do que o ideal nas intervenções.",
            ),
            GoalkeeperNarrativeVariant.POOR_MODERATE to listOf(
                "As intervenções não evitaram os gols sofridos.",
                "Quando o time precisou, a resposta não veio.",
                "Os gols sofridos disseram mais que as defesas.",
                "A luva não ajudou hoje.",
                "Hoje o gol foi mais fácil de fazer.",
                "A defesa errou e o goleiro não compensou.",
            ),
            GoalkeeperNarrativeVariant.POOR_SEVERE to listOf(
                "Hoje qualquer chute parecia perigoso.",
                "A goleira virou uma meta aberta.",
                "Cada finalização era uma ameaça real.",
                "O adversário entendeu cedo que marcar era questão de tempo.",
                "A falta de segurança contaminou todo o setor.",
                "O time defendeu praticamente sem goleiro.",
            ),
        )
    }
}
