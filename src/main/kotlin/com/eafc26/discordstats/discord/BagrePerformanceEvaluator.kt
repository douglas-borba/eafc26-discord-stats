package com.eafc26.discordstats.discord

import com.eafc26.discordstats.config.PhraseBank
import com.eafc26.discordstats.config.PhraseCategory
import com.eafc26.discordstats.ea.model.PlayerEntry
import kotlin.math.abs

/**
 * Evaluates Bagre da Partida performance and generates coherent criticism.
 *
 * Thresholds are centralized here:
 * - Passing: >= 75% is good (omit), 60-74% is moderate (no harsh criticism), < 60% is poor
 * - Tackles: <= 40% is poor, 41-60% is moderate, > 60% is good (omit)
 * - Finishing: never criticize if player scored goals
 * - Rating: generic mocking phrases only for rating < 6.5
 */
object BagrePerformanceEvaluator {

    // Thresholds
    private const val PASSING_GOOD_THRESHOLD = 75
    private const val PASSING_MODERATE_THRESHOLD = 60
    private const val TACKLE_POOR_THRESHOLD = 40
    private const val TACKLE_MODERATE_THRESHOLD = 60
    private const val RATING_MOCKING_THRESHOLD = 6.5
    
    /**
     * Minimum rating for Bagre eligibility.
     * Players with rating below this value are excluded from Bagre selection.
     * Rationale: extremely low ratings indicate the player likely disconnected
     * or had technical issues, not genuinely poor play.
     */
    const val MIN_BAGRE_RATING = 5.0

    data class BagreEvaluation(
        val player: PlayerEntry,
        val sections: List<String>,
    )

    /**
     * Selects the Bagre player (lowest-rated eligible outfield player) without building evaluation.
     * Used to determine which player should be excluded from positive awards.
     * 
     * Players with rating below [MIN_BAGRE_RATING] are excluded from selection.
     */
    fun selectBagrePlayer(outfield: Collection<PlayerEntry>): PlayerEntry? {
        val eligible = outfield.filter { p ->
            val rating = p.rating?.toDoubleOrNull()
            rating != null && rating >= MIN_BAGRE_RATING
        }
        if (eligible.isEmpty()) return null

        return eligible.minWithOrNull(
            compareBy<PlayerEntry> { it.rating?.toDoubleOrNull() ?: Double.MAX_VALUE }
                .thenBy { passCompletionPct(it) ?: 100.0 }
                .thenBy { tackleSuccessPct(it) ?: 100.0 }
                .thenByDescending { shotsNoGoal(it) }
                .thenByDescending { missedPasses(it) }
        )
    }

    /**
     * Selects the Bagre (lowest-rated eligible outfield player) and builds
     * coherent criticism sections based on their actual statistics.
     * 
     * Players with rating below [MIN_BAGRE_RATING] are excluded from selection.
     */
    fun evaluate(
        outfield: Collection<PlayerEntry>,
        matchId: String,
        phraseBank: PhraseBank?,
    ): BagreEvaluation? {
        // Filter to players with valid rating >= MIN_BAGRE_RATING
        val eligible = outfield.filter { p ->
            val rating = p.rating?.toDoubleOrNull()
            rating != null && rating >= MIN_BAGRE_RATING
        }
        if (eligible.isEmpty()) return null

        val bagre = eligible.minWithOrNull(
            compareBy<PlayerEntry> { it.rating?.toDoubleOrNull() ?: Double.MAX_VALUE }
                .thenBy { passCompletionPct(it) ?: 100.0 }
                .thenBy { tackleSuccessPct(it) ?: 100.0 }
                .thenByDescending { shotsNoGoal(it) }
                .thenByDescending { missedPasses(it) }
        ) ?: return null

        val name = bagre.displayName()
        val rating = bagre.rating?.toDoubleOrNull() ?: 0.0
        val sections = mutableListOf<String>()

        // Header with name and rating
        sections += "$name — Nota ${fmtRating(bagre.rating)}"

        // Reason line - always show this
        sections += "Motivo: menor nota entre os jogadores elegíveis."

        // Tackle section - only if poor performance
        buildTackleSection(bagre, matchId, phraseBank)?.let { sections += it }

        // Pass section - only if warranted by thresholds
        buildPassSection(bagre, matchId, phraseBank)?.let { sections += it }

        // Finishing section - only if poor and no goals
        buildFinishingSection(bagre, matchId, phraseBank)?.let { sections += it }

        // Positive counterpoints (goals/assists)
        buildCounterpoints(bagre)?.let { sections += it }

        // Generic phrase only for genuinely poor ratings
        if (rating < RATING_MOCKING_THRESHOLD) {
            val phrase = pickFromCategory(PhraseCategory.RATING, matchId, name, phraseBank)
            sections += "💬 \"$phrase\""
        }

        // Red card mention
        val redCards = bagre.redCards?.toIntOrNull() ?: 0
        if (redCards > 0) sections += "🟥 Cartão vermelho: $redCards"

        return BagreEvaluation(bagre, sections)
    }

    private fun buildTackleSection(
        player: PlayerEntry,
        matchId: String,
        phraseBank: PhraseBank?,
    ): String? {
        val attempts = player.tackleAttempts?.toIntOrNull() ?: return null
        if (attempts <= 0) return null

        val made = player.tacklesMade?.toIntOrNull() ?: 0
        val pct = made * 100 / attempts

        // Only show tackle section if performance is poor or moderate
        if (pct > TACKLE_MODERATE_THRESHOLD) return null

        val name = player.displayName()

        val phrase = when {
            pct <= TACKLE_POOR_THRESHOLD -> pickFromCategory(PhraseCategory.TACKLE, matchId, "${name}desarmes", phraseBank)
            else -> "Marcação com espaço para melhorar."
        }

        return buildString {
            append("🛡️ Desarmes\n")
            append("• $made/$attempts certos\n")
            append("• $pct% de aproveitamento\n\n")
            append("💬 \"$phrase\"")
        }
    }

    private fun buildPassSection(
        player: PlayerEntry,
        matchId: String,
        phraseBank: PhraseBank?,
    ): String? {
        val attempts = player.passAttempts?.toIntOrNull() ?: return null
        if (attempts <= 0) return null

        val made = player.passesMade?.toIntOrNull() ?: 0
        val pct = made * 100 / attempts

        // >= 75%: omit entirely
        if (pct >= PASSING_GOOD_THRESHOLD) return null

        val name = player.displayName()
        val missed = attempts - made

        val phrase = when {
            pct < PASSING_MODERATE_THRESHOLD -> pickFromCategory(PhraseCategory.PASS, matchId, "${name}passes", phraseBank)
            else -> "Distribuição de bola com margem para evolução."
        }

        return buildString {
            append("📉 Passes\n")
            append("• $made/$attempts certos ($pct%)\n")
            append("• ${pluralize(missed, "passe errado", "passes errados")}\n\n")
            append("💬 \"$phrase\"")
        }
    }

    private fun buildFinishingSection(
        player: PlayerEntry,
        matchId: String,
        phraseBank: PhraseBank?,
    ): String? {
        val goals = player.goals?.toIntOrNull() ?: 0
        val shots = player.shots?.toIntOrNull() ?: 0

        // Never criticize finishing if player scored
        if (goals > 0) return null
        // No shots means nothing to criticize
        if (shots <= 0) return null

        val name = player.displayName()
        val phrase = pickFromCategory(PhraseCategory.SHOOTING, matchId, "${name}chutes", phraseBank)

        return buildString {
            append("🎯 Finalizações\n")
            append("• ${pluralize(shots, "chute", "chutes")}\n")
            append("• 0 gols\n\n")
            append("💬 \"$phrase\"")
        }
    }

    private fun buildCounterpoints(player: PlayerEntry): String? {
        val goals = player.goals?.toIntOrNull() ?: 0
        val assists = player.assists?.toIntOrNull() ?: 0

        if (goals <= 0 && assists <= 0) return null

        val parts = mutableListOf<String>()
        if (goals > 0) {
            parts += "⚽ Ainda assim, marcou ${pluralize(goals, "gol", "gols")}."
        }
        if (assists > 0) {
            parts += "🎯 Ainda assim, deu ${pluralize(assists, "assistência", "assistências")}."
        }
        return parts.joinToString("\n")
    }

    // -- Helpers --

    private fun pluralize(count: Int, singular: String, plural: String): String =
        if (count == 1) "1 $singular" else "$count $plural"

    private fun pickFromCategory(
        cat: PhraseCategory,
        matchId: String,
        seed: String,
        phraseBank: PhraseBank?,
    ): String {
        val list = phraseBank?.get(cat) ?: cat.defaults
        val hash = matchId.hashCode().toLong() + seed.hashCode().toLong()
        return list[(abs(hash) % list.size).toInt()]
    }

    private fun fmtRating(raw: String?): String {
        val d = raw?.toDoubleOrNull() ?: return "N/D"
        return "%.2f".format(d).replace('.', ',')
    }

    private fun passCompletionPct(p: PlayerEntry): Double? {
        val attempts = p.passAttempts?.toIntOrNull()?.takeIf { it > 0 } ?: return null
        return (p.passesMade?.toIntOrNull() ?: 0).toDouble() * 100.0 / attempts
    }

    private fun tackleSuccessPct(p: PlayerEntry): Double? {
        val attempts = p.tackleAttempts?.toIntOrNull()?.takeIf { it > 0 } ?: return null
        return (p.tacklesMade?.toIntOrNull() ?: 0).toDouble() * 100.0 / attempts
    }

    private fun missedPasses(p: PlayerEntry): Int {
        val attempts = p.passAttempts?.toIntOrNull() ?: 0
        val made = p.passesMade?.toIntOrNull() ?: 0
        return maxOf(attempts - made, 0)
    }

    private fun shotsNoGoal(p: PlayerEntry): Int {
        if ((p.goals?.toIntOrNull() ?: 0) > 0) return 0
        return p.shots?.toIntOrNull() ?: 0
    }
}


