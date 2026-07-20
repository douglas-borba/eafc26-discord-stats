package com.eafc26.discordstats.discord

import com.eafc26.discordstats.config.PhraseBank
import com.eafc26.discordstats.config.PhraseCategory
import com.eafc26.discordstats.ea.model.PlayerEntry
import java.util.Random
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
        val rating: String,
        val reason: String,
        val tackleStats: String?,
        val passStats: String?,
        val phrase: String,
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
     * a structured evaluation with typed fields.
     * 
     * Players with rating below [MIN_BAGRE_RATING] are excluded from selection.
     * 
     * Returns exactly ONE phrase - picked based on the most relevant criticism:
     * 1. If tackles are poor (<=40%), use tackle phrase
     * 2. Else if passes are poor (<60%), use pass phrase
     * 3. Otherwise use generic rating phrase
     *
     * @param random If provided, uses random phrase selection instead of deterministic hash
     */
    fun evaluate(
        outfield: Collection<PlayerEntry>,
        matchId: String,
        phraseBank: PhraseBank?,
        random: Random? = null,
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
        val ratingValue = bagre.rating?.toDoubleOrNull() ?: 0.0
        
        // Build tackle stats if applicable
        val tackleStats = buildTackleStats(bagre)
        
        // Build pass stats if applicable
        val passStats = buildPassStats(bagre)
        
        // Pick exactly ONE phrase based on most relevant criticism
        val phrase = pickBagrePhrase(bagre, matchId, phraseBank, random)

        return BagreEvaluation(
            player = bagre,
            rating = fmtRating(bagre.rating),
            reason = "Menor nota entre os jogadores elegíveis.",
            tackleStats = tackleStats,
            passStats = passStats,
            phrase = phrase,
        )
    }
    
    /**
     * Builds tackle statistics string if tackles are poor or moderate.
     */
    private fun buildTackleStats(player: PlayerEntry): String? {
        val attempts = player.tackleAttempts?.toIntOrNull() ?: return null
        if (attempts <= 0) return null
        
        val made = player.tacklesMade?.toIntOrNull() ?: 0
        val pct = made * 100 / attempts
        
        // Only show if performance is poor or moderate
        if (pct > TACKLE_MODERATE_THRESHOLD) return null
        
        return "$made/$attempts certos ($pct%)"
    }
    
    /**
     * Builds pass statistics string if passes are below good threshold.
     */
    private fun buildPassStats(player: PlayerEntry): String? {
        val attempts = player.passAttempts?.toIntOrNull() ?: return null
        if (attempts <= 0) return null
        
        val made = player.passesMade?.toIntOrNull() ?: 0
        val pct = made * 100 / attempts
        
        // >= 75%: omit entirely
        if (pct >= PASSING_GOOD_THRESHOLD) return null
        
        val missed = attempts - made
        return "$made/$attempts certos ($pct%) · $missed errados"
    }
    
    /**
     * Picks exactly ONE phrase based on the most relevant criticism:
     * 1. Poor tackles (<=40%) -> tackle phrase
     * 2. Poor passes (<60%) -> pass phrase
     * 3. Otherwise -> generic rating phrase
     */
    private fun pickBagrePhrase(
        player: PlayerEntry,
        matchId: String,
        phraseBank: PhraseBank?,
        random: Random?,
    ): String {
        val name = player.displayName()
        
        // Check tackles first (most defensive-critical)
        val tackleAttempts = player.tackleAttempts?.toIntOrNull() ?: 0
        if (tackleAttempts > 0) {
            val tackleMade = player.tacklesMade?.toIntOrNull() ?: 0
            val tacklePct = tackleMade * 100 / tackleAttempts
            if (tacklePct <= TACKLE_POOR_THRESHOLD) {
                return pickFromCategory(PhraseCategory.TACKLE, matchId, "${name}desarmes", phraseBank, random)
            }
        }
        
        // Check passes second
        val passAttempts = player.passAttempts?.toIntOrNull() ?: 0
        if (passAttempts > 0) {
            val passMade = player.passesMade?.toIntOrNull() ?: 0
            val passPct = passMade * 100 / passAttempts
            if (passPct < PASSING_MODERATE_THRESHOLD) {
                return pickFromCategory(PhraseCategory.PASS, matchId, "${name}passes", phraseBank, random)
            }
        }
        
        // Default to generic rating phrase
        return pickFromCategory(PhraseCategory.RATING, matchId, name, phraseBank, random)
    }

    // Legacy methods kept for backwards compatibility but no longer used by evaluate()
    private fun buildTackleSection(
        player: PlayerEntry,
        matchId: String,
        phraseBank: PhraseBank?,
        random: Random?,
    ): String? {
        val attempts = player.tackleAttempts?.toIntOrNull() ?: return null
        if (attempts <= 0) return null

        val made = player.tacklesMade?.toIntOrNull() ?: 0
        val pct = made * 100 / attempts

        // Only show tackle section if performance is poor or moderate
        if (pct > TACKLE_MODERATE_THRESHOLD) return null

        val name = player.displayName()

        val phrase = when {
            pct <= TACKLE_POOR_THRESHOLD -> pickFromCategory(PhraseCategory.TACKLE, matchId, "${name}desarmes", phraseBank, random)
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
        random: Random?,
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
            pct < PASSING_MODERATE_THRESHOLD -> pickFromCategory(PhraseCategory.PASS, matchId, "${name}passes", phraseBank, random)
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
        random: Random?,
    ): String? {
        val goals = player.goals?.toIntOrNull() ?: 0
        val shots = player.shots?.toIntOrNull() ?: 0

        // Never criticize finishing if player scored
        if (goals > 0) return null
        // No shots means nothing to criticize
        if (shots <= 0) return null

        val name = player.displayName()
        val phrase = pickFromCategory(PhraseCategory.SHOOTING, matchId, "${name}chutes", phraseBank, random)

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
        random: Random?,
    ): String {
        val list = phraseBank?.get(cat) ?: cat.defaults
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


