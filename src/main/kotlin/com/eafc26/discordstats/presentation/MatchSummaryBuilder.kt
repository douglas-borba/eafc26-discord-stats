package com.eafc26.discordstats.presentation

import com.eafc26.discordstats.config.PhraseBank
import com.eafc26.discordstats.config.PhraseCategory
import com.eafc26.discordstats.discord.BagrePerformanceEvaluator
import com.eafc26.discordstats.discord.CorreioExtraviadoSelector
import com.eafc26.discordstats.discord.CraqueSelector
import com.eafc26.discordstats.discord.GoalkeeperEvaluator
import com.eafc26.discordstats.discord.MatchOutcomeResolver
import com.eafc26.discordstats.discord.PassePrecisaoSelector
import com.eafc26.discordstats.discord.PerigoConstanteSelector
import com.eafc26.discordstats.discord.XerifeSelector
import com.eafc26.discordstats.ea.model.MatchResponse
import com.eafc26.discordstats.ea.model.PlayerEntry
import com.eafc26.discordstats.ea.model.PlayerStatisticsEligibility
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.Random
import kotlin.math.abs

/**
 * Builds the presentation model from match data.
 *
 * This is a Spring-managed component that receives [PhraseBank] via
 * constructor injection. No global mutable state is required.
 *
 * Reuses all existing selectors and evaluators.
 *
 * Phrase selection can be:
 * - **Deterministic** (default): Uses matchId hash for consistent results
 * - **Random**: When [forceRandomPhrases] is true, uses true random selection
 *
 * For DEV_SIMULATOR, random selection is enabled so repeated simulations
 * of the same fixture produce different phrase combinations.
 */
@Component
class MatchSummaryBuilder(
    private val phraseBank: PhraseBank,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val dateFmt = DateTimeFormatter.ofPattern("dd MMM yyyy '•' HH:mm", PT_BR)

    /**
     * Builds a presentation from match data.
     *
     * @param match The match data from EA API
     * @param ourClubId Our club's ID
     * @param zoneId Time zone for date formatting
     * @param forceRandomPhrases When true, uses random phrase selection instead of deterministic.
     *                           Used by DEV_SIMULATOR to ensure new phrases on each simulation.
     */
    fun build(
        match: MatchResponse,
        ourClubId: String,
        zoneId: ZoneId = ZoneId.systemDefault(),
        forceRandomPhrases: Boolean = false,
        proNames: Map<String, String> = emptyMap(),
    ): MatchSummaryPresentation {
        // Create a Random instance for this build if random phrases are requested
        val random: Random? = if (forceRandomPhrases) Random() else null
        
        val ourEntry = match.clubs[ourClubId]
        val oppEntry = match.clubs.entries.firstOrNull { it.key != ourClubId }

        val ourName = ourEntry?.resolvedName() ?: "Nós"
        val oppName = oppEntry?.value?.resolvedName() ?: "Adversário"

        val resolved = MatchOutcomeResolver.resolve(ourEntry, oppEntry?.value)
        val outcome = resolved.outcome

        val date = Instant.ofEpochSecond(match.timestamp).atZone(zoneId).format(dateFmt)

        // Get all players from the match (including BOT goalkeeper)
        val allPlayers = (match.players[ourClubId] ?: emptyMap()).values

        // Eligible players for stats (excludes substitutes with low playtime)
        val allActive = PlayerStatisticsEligibility.eligiblePlayers(allPlayers)

        // Find goalkeeper from ALL players (not just eligible) since BOT GK may not pass eligibility
        val goalkeeper = allPlayers
            .filter { it.isGoalkeeper() }
            .maxByOrNull { it.secondsPlayed?.toIntOrNull() ?: 0 }

        // Outfield players for awards (from eligible players, excluding goalkeeper)
        val outfield = allActive.filter { !it.isGoalkeeper() }
        val matchId = match.matchId

        // Determine Bagre first so positive awards can exclude that player.
        // playerName is the canonical identity key — proName is never used for comparisons.
        val bagrePlayerName: String? = BagrePerformanceEvaluator.selectBagrePlayer(outfield)?.playerName

        // Positive awards must never be given to the Bagre player.
        val positiveOutfield: List<PlayerEntry> = if (bagrePlayerName != null)
            outfield.filter { it.playerName != bagrePlayerName }
        else
            outfield

        // ── Diagnostic trace for award pipeline ──────────────────────────────
        // Log at INFO so this is always visible in production logs.
        // Remove once the root cause of missing awards is confirmed.
        log.info("[AWARDS-DIAG] match={} allPlayers={} allActive={} outfield={}",
            matchId, allPlayers.size, allActive.size, outfield.size)
        outfield.forEach { p ->
            log.info("[AWARDS-DIAG]   player='{}' pos={} rating={} shots={} " +
                "tackleAtt={} tackleMade={} secondsPlayed={}",
                p.playerName, p.position, p.rating,
                p.shots, p.tackleAttempts, p.tacklesMade, p.secondsPlayed)
        }
        // Log players excluded by PlayerStatisticsEligibility so we know if the filter is culprit
        val excluded = allPlayers.filter { !it.isGoalkeeper() && it !in allActive }
        if (excluded.isNotEmpty()) {
            log.info("[AWARDS-DIAG] Excluded by eligibility filter ({} player(s)):", excluded.size)
            val maxSec = allPlayers.mapNotNull { it.secondsPlayed?.toIntOrNull()?.takeIf { s -> s > 0 } }.maxOrNull() ?: 0
            excluded.forEach { p ->
                log.info("[AWARDS-DIAG]   EXCLUDED '{}': secondsPlayed={} (max={}, threshold={})",
                    p.playerName, p.secondsPlayed, maxSec, maxSec * 90 / 100)
            }
        }
        // ─────────────────────────────────────────────────────────────────────

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
            goals = buildGoalsSection(allActive, proNames),
            assists = buildAssistsSection(allActive, proNames),
            highlights = buildHighlightsSection(positiveOutfield, allActive, proNames),
            craque = buildCraqueSection(positiveOutfield, matchId, random, proNames),
            perigoConstante = buildPerigoConstanteSection(positiveOutfield, matchId, random, proNames),
            bagre = buildBagreSection(outfield, matchId, random, proNames),
            xerife = buildXerifeSection(positiveOutfield, matchId, random, proNames),
            passePrecisao = buildPassePrecisaoSection(positiveOutfield, matchId, random, proNames),
            correioExtraviado = buildCorreioSection(outfield, matchId, random, proNames),
            muralha = buildMuralhaSection(goalkeeper, matchId, random, proNames),
        )
    }

    private fun buildGoalsSection(players: Collection<PlayerEntry>, proNames: Map<String, String>): GoalsSection? {
        val scorers = players
            .filter { (it.goals?.toIntOrNull() ?: 0) > 0 }
            .sortedByDescending { it.goals?.toIntOrNull() ?: 0 }
            .map { PlayerGoal(it.displayName(proNames), it.goals?.toIntOrNull() ?: 0) }

        return if (scorers.isEmpty()) null else GoalsSection(scorers)
    }

    private fun buildAssistsSection(players: Collection<PlayerEntry>, proNames: Map<String, String>): AssistsSection? {
        val assisters = players
            .filter { (it.assists?.toIntOrNull() ?: 0) > 0 }
            .sortedByDescending { it.assists?.toIntOrNull() ?: 0 }
            .map { PlayerAssist(it.displayName(proNames), it.assists?.toIntOrNull() ?: 0) }

        return if (assisters.isEmpty()) null else AssistsSection(assisters)
    }

    private fun buildHighlightsSection(
        outfield: Collection<PlayerEntry>,
        allActive: Collection<PlayerEntry>,
        proNames: Map<String, String>,
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
                name = p.displayName(proNames),
                rating = fmtRating(p.rating),
            )
        }

        val teamAverage = if (allRatings.isNotEmpty()) {
            fmtRating("%.2f".format(allRatings.average()))
        } else null

        return HighlightsSection(top3, teamAverage)
    }

    private fun buildCraqueSection(outfield: Collection<PlayerEntry>, matchId: String, random: Random?, proNames: Map<String, String>): CraqueSection? {
        val selection = CraqueSelector.select(outfield) ?: return null
        val name = selection.player.displayName(proNames)
        val phrase = pickFromCategory(PhraseCategory.MVP, matchId, name, random)

        return CraqueSection(
            name = name,
            reason = selection.reason,
            phrase = phrase,
        )
    }

    private fun buildPerigoConstanteSection(outfield: Collection<PlayerEntry>, matchId: String, random: Random?, proNames: Map<String, String>): PerigoConstanteSection? {
        val selection = PerigoConstanteSelector.select(outfield) ?: return null
        val name = selection.player.displayName(proNames)
        val category = if (selection.efficient) PhraseCategory.PERIGO_EFICIENTE else PhraseCategory.PERIGO_VOLUME
        val phrase = pickFromCategory(category, matchId, name, random)

        return PerigoConstanteSection(
            name = name,
            shots = selection.shots,
            goals = selection.goals,
            efficient = selection.efficient,
            phrase = phrase,
        )
    }

    private fun buildBagreSection(outfield: Collection<PlayerEntry>, matchId: String, random: Random?, proNames: Map<String, String>): BagreSection? {
        val evaluation = BagrePerformanceEvaluator.evaluate(outfield, matchId, phraseBank, random) ?: return null

        return BagreSection(
            name = evaluation.player.displayName(proNames),
            rating = evaluation.rating,
            reason = evaluation.reason,
            tackleStats = evaluation.tackleStats,
            passStats = evaluation.passStats,
            phrase = evaluation.phrase,
        )
    }

    private fun buildXerifeSection(outfield: Collection<PlayerEntry>, matchId: String, random: Random?, proNames: Map<String, String>): XerifeSection? {
        val selection = XerifeSelector.select(outfield) ?: return null
        val name = selection.player.displayName(proNames)
        val phrase = pickFromCategory(PhraseCategory.XERIFE, matchId, name, random)

        return XerifeSection(
            name = name,
            tacklesMade = selection.tacklesMade,
            tackleAttempts = selection.tackleAttempts,
            successRate = selection.successRate,
            phrase = phrase,
        )
    }

    private fun buildPassePrecisaoSection(outfield: Collection<PlayerEntry>, matchId: String, random: Random?, proNames: Map<String, String>): PassePrecisaoSection? {
        val selection = PassePrecisaoSelector.select(outfield) ?: return null
        val name = selection.player.displayName(proNames)
        val phrase = pickFromCategory(PhraseCategory.PASSE_PRECISAO, matchId, name, random)

        return PassePrecisaoSection(
            name = name,
            passesMade = selection.passesMade,
            passAttempts = selection.passAttempts,
            accuracy = selection.accuracy,
            phrase = phrase,
        )
    }

    private fun buildCorreioSection(outfield: Collection<PlayerEntry>, matchId: String, random: Random?, proNames: Map<String, String>): CorreioExtraviadoSection? {
        val sel = CorreioExtraviadoSelector.select(outfield) ?: return null
        val name = sel.player.displayName(proNames)
        val phrase = pickFromCategory(PhraseCategory.CORREIO, matchId, name, random)
        return CorreioExtraviadoSection(
            name              = name,
            missedPasses      = sel.passAttempts - sel.passesMade,
            playerAccuracyPct = sel.playerAccuracyPct,
            teamAccuracyPct   = sel.teamAccuracyPct,
            deltaPct          = sel.deltaPct,
            phrase            = phrase,
        )
    }

    private fun buildMuralhaSection(gk: PlayerEntry?, matchId: String, random: Random?, proNames: Map<String, String>): MuralhaSection? {
        gk ?: return null
        val performance = GoalkeeperEvaluator.evaluate(gk, matchId, phraseBank, random)

        return MuralhaSection(
            name          = gk.displayName(proNames),
            saves         = gk.saves?.toIntOrNull() ?: 0,
            goalsConceded = gk.goalsConceded?.toIntOrNull() ?: 0,
            archetype     = performance.archetype,
            archetypeTitle = performance.title,
            phrase        = performance.message,
        )
    }

    /**
     * Picks a phrase from the given category.
     *
     * @param cat The phrase category
     * @param matchId The match ID (used for deterministic selection)
     * @param seed Additional seed (usually player name) for deterministic selection
     * @param random If provided, uses random selection instead of deterministic hash
     */
    private fun pickFromCategory(cat: PhraseCategory, matchId: String, seed: String, random: Random?): String {
        val list = phraseBank.get(cat)
        return if (random != null) {
            // Random selection for simulations
            list[random.nextInt(list.size)]
        } else {
            // Deterministic selection for production
            val hash = matchId.hashCode().toLong() + seed.hashCode().toLong()
            list[(abs(hash) % list.size).toInt()]
        }
    }

    private fun fmtRating(raw: String?): String {
        val d = raw?.toDoubleOrNull() ?: return "N/D"
        return "%.2f".format(d).replace('.', ',')
    }

    companion object {
        private val PT_BR = Locale.forLanguageTag("pt-BR")
    }
}

