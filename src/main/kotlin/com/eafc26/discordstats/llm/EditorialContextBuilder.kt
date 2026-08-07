package com.eafc26.discordstats.llm

import com.eafc26.discordstats.canonical.CanonicalMatch
import com.eafc26.discordstats.domain.interpretation.AwardType
import com.eafc26.discordstats.domain.interpretation.GoalkeeperArchetype
import com.eafc26.discordstats.domain.interpretation.MatchOutcome
import com.eafc26.discordstats.domain.interpretation.OffensiveNarrativeCategory
import com.eafc26.discordstats.domain.match.PlayerId
import com.eafc26.discordstats.domain.match.PlayerMatchPerformance
import com.eafc26.discordstats.domain.match.PlayerRole
import com.eafc26.discordstats.domain.story.StoryContent
import com.eafc26.discordstats.domain.story.StoryType
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Component
class EditorialContextBuilder {

    private val dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("pt-BR"))

    fun buildMatchContext(canonical: CanonicalMatch, zoneId: ZoneId = ZoneId.systemDefault()): EditorialContext =
        EditorialContext(
            match = matchContext(canonical, zoneId),
            recentForm = null,
        )

    fun buildFullContext(
        current: CanonicalMatch,
        recentMatches: List<CanonicalMatch>,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): EditorialContext = EditorialContext(
        match = matchContext(current, zoneId),
        recentForm = recentForm(recentMatches, current),
    )

    /**
     * Builds a panorama context using ALL recent matches (including current).
     * For dashboard panorama, we want statistics to match exactly what the UI displays,
     * so we include the current match in the aggregated statistics.
     */
    fun buildPanoramaContext(
        current: CanonicalMatch,
        recentMatches: List<CanonicalMatch>,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): EditorialContext = EditorialContext(
        match = matchContext(current, zoneId),
        recentForm = recentFormIncludingCurrent(recentMatches),
    )

    private fun matchContext(canonical: CanonicalMatch, zoneId: ZoneId): MatchContext {
        val interp = canonical.interpretation
        val match = canonical.footballMatch
        val ourClub = match.participants.first { it.club.id == interp.perspectiveClubId }
        val opponent = match.participants.first { it.club.id == interp.result.opponentClub }
        val players = ourClub.players.associateBy { it.player.id }
        val stories = canonical.stories.stories

        val mvpStory = stories
            .filter { it.type == StoryType.AWARD }
            .mapNotNull { it.content as? StoryContent.Award }
            .firstOrNull { it.awardType == AwardType.CRAQUE }

        val bagreStory = stories
            .firstOrNull { it.type == StoryType.BAGRE_PERFORMANCE }
            ?.content as? StoryContent.BagrePerformance

        val xerifeStory = stories
            .filter { it.type == StoryType.AWARD }
            .mapNotNull { it.content as? StoryContent.Award }
            .firstOrNull { it.awardType == AwardType.XERIFE }

        val gkStory = stories
            .firstOrNull { it.type == StoryType.GOALKEEPER }
            ?.content as? StoryContent.Goalkeeper

        val goalsStory = stories
            .firstOrNull { it.type == StoryType.GOALS }
            ?.content as? StoryContent.Contributions

        val assistsStory = stories
            .firstOrNull { it.type == StoryType.ASSISTS }
            ?.content as? StoryContent.Contributions

        val highlightsStory = stories
            .firstOrNull { it.type == StoryType.HIGHLIGHTS }
            ?.content as? StoryContent.Highlights

        val notableEvents = buildNotableEvents(stories, players)

        return MatchContext(
            ourClub = ourClub.club.name?.value ?: "Nós",
            opponent = opponent.club.name?.value ?: "Adversário",
            ourScore = interp.result.ourScore.goals,
            opponentScore = interp.result.opponentScore.goals,
            outcome = interp.result.outcome,
            date = match.playedAt.atZone(zoneId).format(dateFmt),
            mvp = mvpStory?.let { award ->
                PlayerHighlight(
                    name = displayName(players[award.winnerId]),
                    rating = (award.metrics as? com.eafc26.discordstats.domain.interpretation.AwardMetrics.Craque)
                        ?.rating?.let(::fmtRating),
                    detail = mvpDetail(award),
                )
            },
            worstPerformer = bagreStory?.let {
                PlayerHighlight(
                    name = displayName(players[it.playerId]),
                    rating = fmtRating(it.rating),
                    detail = bagreDetail(it),
                )
            },
            xerife = xerifeStory?.let { award ->
                val metrics = award.metrics as? com.eafc26.discordstats.domain.interpretation.AwardMetrics.Xerife
                PlayerHighlight(
                    name = displayName(players[award.winnerId]),
                    rating = null,
                    detail = metrics?.let { "${it.tacklesCompleted}/${it.tacklesAttempted} desarmes (${it.accuracyPercent}%)" },
                )
            },
            goalkeeper = gkStory?.let {
                GoalkeeperHighlight(
                    name = displayName(players[it.playerId]),
                    saves = it.saves,
                    goalsConceded = it.goalsConceded,
                    archetype = archetypeLabel(it.archetype),
                )
            },
            goals = goalsStory?.players?.map { ScorerContext(displayName(players[it.playerId]), it.goals) } ?: emptyList(),
            assists = assistsStory?.players?.map { AssistContext(displayName(players[it.playerId]), it.assists) } ?: emptyList(),
            teamAverageRating = highlightsStory?.teamAverageRating?.let(::fmtRating),
            notableEvents = notableEvents,
        )
    }

    private fun recentForm(recentMatches: List<CanonicalMatch>, excluding: CanonicalMatch): RecentFormContext? {
        val others = recentMatches.filter { it.matchId != excluding.matchId }
        if (others.isEmpty()) return null

        val results = others.map { canonical ->
            val interp = canonical.interpretation
            val opponent = canonical.footballMatch.participants
                .first { it.club.id == interp.result.opponentClub }
            RecentMatchResult(
                opponent = opponent.club.name?.value ?: "Adversário",
                ourScore = interp.result.ourScore.goals,
                opponentScore = interp.result.opponentScore.goals,
                outcome = interp.result.outcome,
            )
        }

        val allResults = listOf(excluding).plus(others).map { it.interpretation.result.outcome }
        val streak = computeStreak(allResults)

        return RecentFormContext(
            results = results,
            wins = results.count { it.outcome == MatchOutcome.WIN },
            draws = results.count { it.outcome == MatchOutcome.DRAW },
            losses = results.count { it.outcome == MatchOutcome.LOSS },
            goalsScored = results.sumOf { it.ourScore },
            goalsConceded = results.sumOf { it.opponentScore },
            streak = streak,
        )
    }

    /**
     * Builds recent form context INCLUDING all matches (no exclusion).
     * Used for panorama where we want aggregated statistics to match
     * exactly what the dashboard displays (all 10 matches).
     */
    private fun recentFormIncludingCurrent(recentMatches: List<CanonicalMatch>): RecentFormContext? {
        if (recentMatches.isEmpty()) return null

        val results = recentMatches.map { canonical ->
            val interp = canonical.interpretation
            val opponent = canonical.footballMatch.participants
                .first { it.club.id == interp.result.opponentClub }
            RecentMatchResult(
                opponent = opponent.club.name?.value ?: "Adversário",
                ourScore = interp.result.ourScore.goals,
                opponentScore = interp.result.opponentScore.goals,
                outcome = interp.result.outcome,
            )
        }

        val allOutcomes = recentMatches.map { it.interpretation.result.outcome }
        val streak = computeStreak(allOutcomes)

        return RecentFormContext(
            results = results,
            wins = results.count { it.outcome == MatchOutcome.WIN },
            draws = results.count { it.outcome == MatchOutcome.DRAW },
            losses = results.count { it.outcome == MatchOutcome.LOSS },
            goalsScored = results.sumOf { it.ourScore },
            goalsConceded = results.sumOf { it.opponentScore },
            streak = streak,
        )
    }

    private fun computeStreak(outcomes: List<MatchOutcome>): String {
        if (outcomes.isEmpty()) return "nenhuma"
        val first = outcomes.first()
        val count = outcomes.takeWhile { it == first }.size
        val label = when (first) {
            MatchOutcome.WIN -> if (count == 1) "1 vitória" else "$count vitórias seguidas"
            MatchOutcome.DRAW -> if (count == 1) "1 empate" else "$count empates seguidos"
            MatchOutcome.LOSS -> if (count == 1) "1 derrota" else "$count derrotas seguidas"
        }
        return label
    }

    private fun buildNotableEvents(
        stories: List<com.eafc26.discordstats.domain.story.Story>,
        players: Map<PlayerId, PlayerMatchPerformance>,
    ): List<String> = buildList {
        stories.firstOrNull { it.type == StoryType.RED_CARD }
            ?.let { it.content as? StoryContent.RedCard }
            ?.let { add("Cartão vermelho: ${displayName(players[it.playerId])}") }

        stories.filter { it.type == StoryType.OFFENSIVE_NARRATIVE }
            .mapNotNull { it.content as? StoryContent.OffensiveNarrative }
            .forEach { narrative ->
                val label = when (narrative.category) {
                    OffensiveNarrativeCategory.DECISIVE -> "Decisivo"
                    OffensiveNarrativeCategory.CONSTANT_THREAT -> "Perigo constante"
                    OffensiveNarrativeCategory.COULD_HAVE_DECIDED -> "Poderia ter decidido"
                    OffensiveNarrativeCategory.FELL_SHORT -> "Ficou no quase"
                    OffensiveNarrativeCategory.LACKED_COMPOSURE -> "Faltou capricho"
                }
                add("$label: ${displayName(players[narrative.playerId])} (${narrative.shots} chutes, ${narrative.goals} gols)")
            }

        stories.firstOrNull { it.type == StoryType.PASS_PRECISION }
            ?.let { it.content as? StoryContent.PassPrecision }
            ?.let { add("Passe de precisão: ${displayName(players[it.playerId])} (${it.accuracyPercent}%)") }

        stories.firstOrNull { it.type == StoryType.LOST_MAIL }
            ?.let { it.content as? StoryContent.LostMail }
            ?.let { add("Correio extraviado: ${displayName(players[it.playerId])} (${it.playerAccuracyPercent}% de acerto)") }
    }

    private fun mvpDetail(award: StoryContent.Award): String {
        val metrics = award.metrics as? com.eafc26.discordstats.domain.interpretation.AwardMetrics.Craque ?: return ""
        val parts = mutableListOf<String>()
        if (metrics.goals > 0) parts += "${metrics.goals} gol(s)"
        if (metrics.assists > 0) parts += "${metrics.assists} assistência(s)"
        if (metrics.eaManOfTheMatch) parts += "Craque da Partida (EA)"
        return parts.joinToString(", ")
    }

    private fun bagreDetail(bagre: StoryContent.BagrePerformance): String = when (bagre.criticism) {
        com.eafc26.discordstats.domain.interpretation.BagreCriticism.TACKLING ->
            bagre.tackleSummary?.let { "Desarmes: ${it.completed}/${it.attempted} (${it.accuracyPercent}%)" } ?: "Menor nota"
        com.eafc26.discordstats.domain.interpretation.BagreCriticism.PASSING ->
            bagre.passingSummary?.let { "Passes: ${it.completed}/${it.attempted} (${it.accuracyPercent}%)" } ?: "Menor nota"
        com.eafc26.discordstats.domain.interpretation.BagreCriticism.RATING -> "Menor nota da partida"
    }

    private fun archetypeLabel(archetype: GoalkeeperArchetype): String = when (archetype) {
        GoalkeeperArchetype.WALL -> "Paredão"
        GoalkeeperArchetype.SOLID -> "Seguro"
        GoalkeeperArchetype.UNDER_SIEGE -> "Bombardeado"
        GoalkeeperArchetype.POOR -> "Mão de Alface"
        GoalkeeperArchetype.QUIET -> "Discreto"
    }

    private fun displayName(player: PlayerMatchPerformance?): String =
        player?.player?.preferredDisplayName?.value
            ?: if (player?.role is PlayerRole.Goalkeeper) "Goleiro BOT" else "Desconhecido"

    private fun fmtRating(value: BigDecimal): String =
        "%.2f".format(value.toDouble()).replace('.', ',')
}
