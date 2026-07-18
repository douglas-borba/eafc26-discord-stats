package com.eafc26.discordstats.presentation

import com.eafc26.discordstats.config.PhraseBank
import com.eafc26.discordstats.config.PhraseCategory
import com.eafc26.discordstats.discord.*
import com.eafc26.discordstats.ea.model.MatchResponse
import com.eafc26.discordstats.ea.model.PlayerEntry
import com.eafc26.discordstats.ea.model.PlayerStatisticsEligibility
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * Unified presentation model for match summaries.
 * 
 * This model is used by both:
 * - DiscordEmbedBuilder (for Discord webhook)
 * - Match Card renderer (for visual card)
 * 
 * Business logic remains in the existing selectors.
 * This class only aggregates their results into a presentation-ready format.
 */
data class MatchSummaryPresentation(
    // Match header
    val ourName: String,
    val oppName: String,
    val ourScore: Int,
    val oppScore: Int,
    val outcome: MatchOutcome,
    val date: String,
    val timestamp: String,
    val matchId: String,
    
    // Sections (null if not applicable)
    val goals: GoalsSection?,
    val assists: AssistsSection?,
    val highlights: HighlightsSection?,
    val craque: CraqueSection?,
    val perigoConstante: PerigoConstanteSection?,
    val bagre: BagreSection?,
    val xerife: XerifeSection?,
    val passePrecisao: PassePrecisaoSection?,
    val correioExtraviado: CorreioExtraviadoSection?,
    val muralha: MuralhaSection?,
)

data class MatchOutcome(
    val emoji: String,
    val label: String,
    val color: Int,
    val type: OutcomeType,
)

enum class OutcomeType { WIN, DRAW, LOSS }

data class GoalsSection(
    val scorers: List<PlayerGoal>,
)

data class PlayerGoal(
    val name: String,
    val count: Int,
)

data class AssistsSection(
    val assisters: List<PlayerAssist>,
)

data class PlayerAssist(
    val name: String,
    val count: Int,
)

data class HighlightsSection(
    val top3: List<TopPlayer>,
    val teamAverage: String?,
)

data class TopPlayer(
    val medal: String,
    val name: String,
    val rating: String,
)

data class CraqueSection(
    val name: String,
    val reason: String,
    val phrase: String,
)

data class PerigoConstanteSection(
    val name: String,
    val shots: Int,
    val goals: Int,
    val efficient: Boolean,
    val phrase: String,
)

data class BagreSection(
    val name: String,
    val sections: List<String>,
)

data class XerifeSection(
    val name: String,
    val tacklesMade: Int,
    val tackleAttempts: Int,
    val successRate: Int,
    val phrase: String,
)

data class PassePrecisaoSection(
    val name: String,
    val passesMade: Int,
    val passAttempts: Int,
    val accuracy: Int,
    val phrase: String,
)

data class CorreioExtraviadoSection(
    val name: String,
    val missedPasses: Int,
    val phrase: String,
)

data class MuralhaSection(
    val name: String,
    val saves: Int,
    val phrase: String,
)

/**
 * Builds the presentation model from match data.
 * Reuses all existing selectors and evaluators.
 */
object MatchSummaryBuilder {

    private val PT_BR = Locale.forLanguageTag("pt-BR")
    private val DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy '•' HH:mm", PT_BR)

    @Volatile
    var phraseBank: PhraseBank? = null

    fun build(
        match: MatchResponse,
        ourClubId: String,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): MatchSummaryPresentation {
        val ourEntry = match.clubs[ourClubId]
        val oppEntry = match.clubs.entries.firstOrNull { it.key != ourClubId }

        val ourName = ourEntry?.resolvedName() ?: "Nós"
        val oppName = oppEntry?.value?.resolvedName() ?: "Adversário"

        val resolved = MatchOutcomeResolver.resolve(ourEntry, oppEntry?.value)
        val outcome = resolved.outcome

        val date = Instant.ofEpochSecond(match.timestamp).atZone(zoneId).format(DATE_FMT)

        val allActive = PlayerStatisticsEligibility.eligiblePlayers(
            (match.players[ourClubId] ?: emptyMap()).values
        )

        val goalkeeper = allActive
            .filter { it.position == "goalkeeper" }
            .maxByOrNull { it.secondsPlayed?.toIntOrNull() ?: 0 }

        val outfield = allActive.filter { it.position != "goalkeeper" }
        val matchId = match.matchId

        return MatchSummaryPresentation(
            ourName = ourName,
            oppName = oppName,
            ourScore = resolved.ourScore,
            oppScore = resolved.oppScore,
            outcome = MatchOutcome(
                emoji = outcome.emoji,
                label = outcome.label,
                color = outcome.color,
                type = when {
                    resolved.ourScore > resolved.oppScore -> OutcomeType.WIN
                    resolved.ourScore < resolved.oppScore -> OutcomeType.LOSS
                    else -> OutcomeType.DRAW
                }
            ),
            date = date,
            timestamp = Instant.ofEpochSecond(match.timestamp).toString(),
            matchId = matchId,
            goals = buildGoalsSection(allActive),
            assists = buildAssistsSection(allActive),
            highlights = buildHighlightsSection(outfield, allActive),
            craque = buildCraqueSection(outfield, matchId),
            perigoConstante = buildPerigoConstanteSection(outfield, matchId),
            bagre = buildBagreSection(outfield, matchId),
            xerife = buildXerifeSection(outfield, matchId),
            passePrecisao = buildPassePrecisaoSection(outfield, matchId),
            correioExtraviado = buildCorreioSection(outfield, matchId),
            muralha = buildMuralhaSection(goalkeeper, matchId),
        )
    }

    private fun buildGoalsSection(players: Collection<PlayerEntry>): GoalsSection? {
        val scorers = players
            .filter { (it.goals?.toIntOrNull() ?: 0) > 0 }
            .sortedByDescending { it.goals?.toIntOrNull() ?: 0 }
            .map { PlayerGoal(it.playerName ?: "Desconhecido", it.goals?.toIntOrNull() ?: 0) }
        
        return if (scorers.isEmpty()) null else GoalsSection(scorers)
    }

    private fun buildAssistsSection(players: Collection<PlayerEntry>): AssistsSection? {
        val assisters = players
            .filter { (it.assists?.toIntOrNull() ?: 0) > 0 }
            .sortedByDescending { it.assists?.toIntOrNull() ?: 0 }
            .map { PlayerAssist(it.playerName ?: "Desconhecido", it.assists?.toIntOrNull() ?: 0) }
        
        return if (assisters.isEmpty()) null else AssistsSection(assisters)
    }

    private fun buildHighlightsSection(
        outfield: Collection<PlayerEntry>,
        allActive: Collection<PlayerEntry>,
    ): HighlightsSection? {
        val top = outfield
            .filter { it.rating != null }
            .sortedByDescending { it.rating?.toDoubleOrNull() ?: 0.0 }
            .take(3)
        val allRatings = allActive.mapNotNull { it.rating?.toDoubleOrNull() }
        
        if (top.isEmpty() && allRatings.isEmpty()) return null

        val medals = listOf("🥇", "🥈", "🥉")
        val top3 = top.mapIndexed { i, p ->
            TopPlayer(
                medal = medals[i],
                name = p.playerName ?: "Desconhecido",
                rating = fmtRating(p.rating),
            )
        }

        val teamAverage = if (allRatings.isNotEmpty()) {
            fmtRating("%.2f".format(allRatings.average()))
        } else null

        return HighlightsSection(top3, teamAverage)
    }

    private fun buildCraqueSection(outfield: Collection<PlayerEntry>, matchId: String): CraqueSection? {
        val selection = CraqueSelector.select(outfield) ?: return null
        val name = selection.player.playerName ?: "Desconhecido"
        val phrase = pickFromCategory(PhraseCategory.MVP, matchId, name)

        return CraqueSection(
            name = name,
            reason = selection.reason,
            phrase = phrase,
        )
    }

    private fun buildPerigoConstanteSection(outfield: Collection<PlayerEntry>, matchId: String): PerigoConstanteSection? {
        val selection = PerigoConstanteSelector.select(outfield) ?: return null
        val name = selection.player.playerName ?: "Desconhecido"
        val category = if (selection.efficient) PhraseCategory.PERIGO_EFICIENTE else PhraseCategory.PERIGO_VOLUME
        val phrase = pickFromCategory(category, matchId, name)

        return PerigoConstanteSection(
            name = name,
            shots = selection.shots,
            goals = selection.goals,
            efficient = selection.efficient,
            phrase = phrase,
        )
    }

    private fun buildBagreSection(outfield: Collection<PlayerEntry>, matchId: String): BagreSection? {
        val evaluation = BagrePerformanceEvaluator.evaluate(outfield, matchId, phraseBank) ?: return null
        val name = evaluation.player.playerName ?: "Desconhecido"

        return BagreSection(
            name = name,
            sections = evaluation.sections,
        )
    }

    private fun buildXerifeSection(outfield: Collection<PlayerEntry>, matchId: String): XerifeSection? {
        val selection = XerifeSelector.select(outfield) ?: return null
        val name = selection.player.playerName ?: "Desconhecido"
        val phrase = pickFromCategory(PhraseCategory.XERIFE, matchId, name)

        return XerifeSection(
            name = name,
            tacklesMade = selection.tacklesMade,
            tackleAttempts = selection.tackleAttempts,
            successRate = selection.successRate,
            phrase = phrase,
        )
    }

    private fun buildPassePrecisaoSection(outfield: Collection<PlayerEntry>, matchId: String): PassePrecisaoSection? {
        val selection = PassePrecisaoSelector.select(outfield) ?: return null
        val name = selection.player.playerName ?: "Desconhecido"
        val phrase = pickFromCategory(PhraseCategory.PASSE_PRECISAO, matchId, name)

        return PassePrecisaoSection(
            name = name,
            passesMade = selection.passesMade,
            passAttempts = selection.passAttempts,
            accuracy = selection.accuracy,
            phrase = phrase,
        )
    }

    private fun buildCorreioSection(outfield: Collection<PlayerEntry>, matchId: String): CorreioExtraviadoSection? {
        val candidates = outfield.mapNotNull { p ->
            val attempted = p.passAttempts?.toIntOrNull() ?: return@mapNotNull null
            val made = p.passesMade?.toIntOrNull() ?: return@mapNotNull null
            val failed = maxOf(attempted - made, 0)
            if (failed == 0) null else Pair(p, failed)
        }
        val best = candidates.maxWithOrNull(
            compareBy<Pair<PlayerEntry, Int>> { it.second }
                .thenByDescending { it.first.playerName ?: "" }
        ) ?: return null

        val name = best.first.playerName ?: "Desconhecido"
        val phrase = pickFromCategory(PhraseCategory.CORREIO, matchId, name)

        return CorreioExtraviadoSection(
            name = name,
            missedPasses = best.second,
            phrase = phrase,
        )
    }

    private fun buildMuralhaSection(gk: PlayerEntry?, matchId: String): MuralhaSection? {
        gk ?: return null
        val saves = gk.saves?.toIntOrNull() ?: 0
        val name = gk.playerName ?: "Goleiro"
        val phrase = pickFromCategory(PhraseCategory.GOALKEEPER, matchId, name)

        return MuralhaSection(
            name = name,
            saves = saves,
            phrase = phrase,
        )
    }

    private fun pickFromCategory(cat: PhraseCategory, matchId: String, seed: String): String {
        val list = phraseBank?.get(cat) ?: cat.defaults
        val hash = matchId.hashCode().toLong() + seed.hashCode().toLong()
        return list[(abs(hash) % list.size).toInt()]
    }

    private fun fmtRating(raw: String?): String {
        val d = raw?.toDoubleOrNull() ?: return "N/D"
        return "%.2f".format(d).replace('.', ',')
    }
}

