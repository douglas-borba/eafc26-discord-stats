package com.eafc26.discordstats.discord

import com.eafc26.discordstats.config.PhraseBank
import com.eafc26.discordstats.config.PhraseCategory
import com.eafc26.discordstats.ea.model.PlayerEntry
import java.util.Random
import kotlin.math.abs

/**
 * Evaluates a goalkeeper's match and produces a [GoalkeeperPerformance].
 *
 * ## Classification logic
 *
 * The **EA rating** is the primary verdict. Raw save counts enrich the
 * interpretation but never override a clearly poor rating.
 *
 * Priority order (first matching rule wins):
 *
 *  Priority  Archetype       Condition
 * ------------------------------------------------------------------------------------------
 *  1         QUIET           saves ≤ 1 **and** goalsConceded ≤ 1 — barely worked
 *  2         WALL            rating ≥ [WALL_RATING_THRESHOLD] **and** (cleanSheet **or** impactSaves ≥ [WALL_MIN_IMPACT_SAVES])
 *  3         POOR            rating < [POOR_RATING_THRESHOLD] — EA verdict takes priority over save count
 *  4         UNDER_SIEGE     saves ≥ [MIN_SAVES_UNDER_SIEGE] — bombarded with acceptable rating
 *  5         SOLID           everything else
 *
 * **impactSaves** = goodDirectionSaves + reflexSaves + parrySaves
 */
object GoalkeeperEvaluator {

    /** Total saves threshold to classify a goalkeeper as UNDER_SIEGE. */
    const val MIN_SAVES_UNDER_SIEGE = 6

    /** Rating below which a goalkeeper is classified as POOR (checked before UNDER_SIEGE). */
    const val POOR_RATING_THRESHOLD = 6.0

    /** Rating at or above which WALL is considered (combined with other criteria). */
    const val WALL_RATING_THRESHOLD = 8.0

    /** Minimum impact saves to qualify for WALL even when goals were conceded. */
    const val WALL_MIN_IMPACT_SAVES = 3

    /** Minimum notable count for a save type to be considered dominant. */
    private const val DOMINANT_SAVE_THRESHOLD = 3

    data class GoalkeeperPerformance(
        val archetype: GoalkeeperArchetype,
        /** Localised title, e.g. "🧱 Paredão". */
        val title: String,
        /** Selected contextual phrase for Discord / web card. */
        val message: String,
    )

    // ── Contextual phrase pools ───────────────────────────────────────────────

    private val CONTEXTUAL_REFLEX = listOf(
        "Fez defesas praticamente impossíveis.",
        "Os reflexos fizeram a diferença.",
        "Respondeu rápido em cada situação de perigo.",
        "O instinto salvou o time mais de uma vez.",
        "Tirou de onde não havia mais tempo para pensar.",
    )

    private val CONTEXTUAL_PARRY = listOf(
        "Espalmou tudo que apareceu.",
        "Tirou o perigo com firmeza em cada oportunidade.",
        "As espalmadas mantiveram o time vivo.",
        "Cada intervenção foi precisa e segura.",
        "A luva trabalhou em horas extras.",
    )

    private val CONTEXTUAL_CROSS = listOf(
        "Dominou completamente a área.",
        "Saiu com autoridade em cada bola aérea.",
        "O domínio da área foi total.",
        "Transmitiu segurança nas saídas.",
        "Limpou a área com eficiência.",
    )

    private val CONTEXTUAL_GOOD_DIRECTION = listOf(
        "Leu muito bem as finalizações.",
        "Antecipou os chutes com inteligência.",
        "Posicionamento impecável nas defesas.",
        "Sabia para onde a bola ia antes do chute.",
        "A leitura de jogo foi o grande diferencial.",
    )

    // Tiered POOR phrases — selected based on how poor the rating was
    private val POOR_MILD = listOf(
        "Não conseguiu passar segurança.",
        "A atuação deixou a desejar.",
        "Houve momentos de hesitação que custaram caro.",
        "Não foi o jogo que o time precisava do goleiro.",
        "A segurança que o setor precisava não apareceu.",
        "Oscilou mais do que o ideal nas intervenções.",
    )

    private val POOR_MODERATE = listOf(
        "As intervenções não evitaram os gols sofridos.",
        "Quando o time precisou, a resposta não veio.",
        "Os gols sofridos disseram mais que as defesas.",
        "A luva não ajudou hoje.",
        "Hoje o gol foi mais fácil de fazer.",
        "A defesa errou e o goleiro não compensou.",
    )

    private val POOR_SEVERE = listOf(
        "Hoje qualquer chute parecia perigoso.",
        "A goleira virou uma meta aberta.",
        "Cada finalização era uma ameaça real.",
        "O adversário entendeu cedo que marcar era questão de tempo.",
        "A falta de segurança contaminou todo o setor.",
        "O time defendeu praticamente sem goleiro.",
    )

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Evaluates [gk] and returns the appropriate [GoalkeeperPerformance].
     */
    fun evaluate(
        gk: PlayerEntry,
        matchId: String,
        phraseBank: PhraseBank?,
        random: Random? = null,
    ): GoalkeeperPerformance {
        val saves         = gk.saves?.toIntOrNull()         ?: 0
        val goalsConceded = gk.goalsConceded?.toIntOrNull() ?: 0
        val rating        = gk.rating?.toDoubleOrNull()     ?: 0.0

        val goodDirection = gk.goodDirectionSaves?.toIntOrNull() ?: 0
        val reflex        = gk.reflexSaves?.toIntOrNull()        ?: 0
        val parry         = gk.parrySaves?.toIntOrNull()         ?: 0
        val cross         = gk.crossSaves?.toIntOrNull()         ?: 0
        val impactSaves   = goodDirection + reflex + parry

        val cleanSheet = goalsConceded == 0

        val archetype = classify(saves, goalsConceded, rating, impactSaves, cleanSheet)
        val category  = phraseCategory(archetype)
        val message   = selectMessage(
            archetype     = archetype,
            category      = category,
            rating        = rating,
            reflex        = reflex,
            parry         = parry,
            cross         = cross,
            goodDirection = goodDirection,
            matchId       = matchId,
            seed          = gk.displayName(),
            phraseBank    = phraseBank,
            random        = random,
        )

        return GoalkeeperPerformance(
            archetype = archetype,
            title     = archetype.title,
            message   = message,
        )
    }

    // ── Classification ────────────────────────────────────────────────────────

    internal fun classify(
        saves: Int,
        goalsConceded: Int,
        rating: Double,
        impactSaves: Int,
        cleanSheet: Boolean,
    ): GoalkeeperArchetype = when {
        // 1. Barely worked
        saves <= 1 && goalsConceded <= 1 ->
            GoalkeeperArchetype.QUIET

        // 2. Outstanding: high rating with clean sheet or decisive saves
        rating >= WALL_RATING_THRESHOLD && (cleanSheet || impactSaves >= WALL_MIN_IMPACT_SAVES) ->
            GoalkeeperArchetype.WALL

        // 3. Poor: EA rating is the primary verdict — wins over save count
        rating < POOR_RATING_THRESHOLD ->
            GoalkeeperArchetype.POOR

        // 4. Bombarded: many saves with acceptable rating (implicitly rating ≥ 6.0)
        saves >= MIN_SAVES_UNDER_SIEGE ->
            GoalkeeperArchetype.UNDER_SIEGE

        // 5. Default: decent performance
        else ->
            GoalkeeperArchetype.SOLID
    }

    // ── Phrase category mapping ───────────────────────────────────────────────

    internal fun phraseCategory(archetype: GoalkeeperArchetype): PhraseCategory = when (archetype) {
        GoalkeeperArchetype.WALL        -> PhraseCategory.GOALKEEPER_WALL
        GoalkeeperArchetype.SOLID       -> PhraseCategory.GOALKEEPER_SOLID
        GoalkeeperArchetype.UNDER_SIEGE -> PhraseCategory.GOALKEEPER_UNDER_SIEGE
        GoalkeeperArchetype.POOR        -> PhraseCategory.GOALKEEPER_POOR
        GoalkeeperArchetype.QUIET       -> PhraseCategory.GOALKEEPER_QUIET
    }

    // ── Phrase selection ──────────────────────────────────────────────────────

    private enum class DominantSave { REFLEX, PARRY, CROSS, GOOD_DIRECTION, NONE }

    private fun dominantSaveType(reflex: Int, parry: Int, cross: Int, goodDirection: Int): DominantSave {
        val best = maxOf(reflex, parry, goodDirection)
        return when {
            best < DOMINANT_SAVE_THRESHOLD && cross < DOMINANT_SAVE_THRESHOLD -> DominantSave.NONE
            reflex == best && reflex >= DOMINANT_SAVE_THRESHOLD -> DominantSave.REFLEX
            parry == best && parry >= DOMINANT_SAVE_THRESHOLD   -> DominantSave.PARRY
            goodDirection >= DOMINANT_SAVE_THRESHOLD             -> DominantSave.GOOD_DIRECTION
            cross >= DOMINANT_SAVE_THRESHOLD                     -> DominantSave.CROSS
            else                                                  -> DominantSave.NONE
        }
    }

    /**
     * Selects a contextual message:
     * - POOR: tiered by rating severity (ignores phraseBank for richer variety unless customised)
     * - WALL / UNDER_SIEGE with dominant save type: returns a stat-contextual phrase
     * - Everything else: falls back to phraseBank → category defaults
     */
    private fun selectMessage(
        archetype: GoalkeeperArchetype,
        category: PhraseCategory,
        rating: Double,
        reflex: Int,
        parry: Int,
        cross: Int,
        goodDirection: Int,
        matchId: String,
        seed: String,
        phraseBank: PhraseBank?,
        random: Random?,
    ): String {
        // POOR: if no custom phrases, use tiered defaults for better variety
        if (archetype == GoalkeeperArchetype.POOR) {
            val custom = phraseBank?.get(category)
            if (custom != null && custom.isNotEmpty() && custom != category.defaults) {
                return pickFrom(custom, matchId, seed, random)
            }
            val tiered = when {
                rating < 4.5 -> POOR_SEVERE
                rating < 5.5 -> POOR_MODERATE
                else         -> POOR_MILD
            }
            return pickFrom(tiered, matchId, seed, random)
        }

        // WALL / UNDER_SIEGE: enrich with dominant save type contextual phrases
        if (archetype == GoalkeeperArchetype.WALL || archetype == GoalkeeperArchetype.UNDER_SIEGE) {
            val dominant = dominantSaveType(reflex, parry, cross, goodDirection)
            val contextual: List<String>? = when (dominant) {
                DominantSave.REFLEX         -> CONTEXTUAL_REFLEX
                DominantSave.PARRY          -> CONTEXTUAL_PARRY
                DominantSave.CROSS          -> CONTEXTUAL_CROSS
                DominantSave.GOOD_DIRECTION -> CONTEXTUAL_GOOD_DIRECTION
                DominantSave.NONE           -> null
            }
            if (contextual != null) return pickFrom(contextual, matchId, seed, random)
        }

        return pick(category, matchId, seed, phraseBank, random)
    }

    private fun pick(
        cat: PhraseCategory,
        matchId: String,
        seed: String,
        phraseBank: PhraseBank?,
        random: Random?,
    ): String {
        val list = phraseBank?.get(cat) ?: cat.defaults
        return pickFrom(list, matchId, seed, random)
    }

    private fun pickFrom(list: List<String>, matchId: String, seed: String, random: Random?): String {
        return if (random != null) {
            list[random.nextInt(list.size)]
        } else {
            val hash = matchId.hashCode().toLong() + seed.hashCode().toLong()
            list[(abs(hash) % list.size).toInt()]
        }
    }
}
