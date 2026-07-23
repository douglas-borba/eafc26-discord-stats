package com.eafc26.discordstats.discord

/**
 * Deterministic presentation resolver for the "Perigo Constante" award.
 *
 * Receives attacking statistics and match context, and returns a presentation
 * model with a title, emoji, and contextual narrative.
 *
 * Decision priority (first matching rule wins):
 * 1. [Category.COULD_HAVE_DECIDED] — draw or narrow defeat (goal diff 0 or −1),
 *    shots ≥ [PerigoConstanteSelector.MIN_SHOTS], **and** conversionRate < 0.50.
 *    Players who converted ≥ 50 % of their shots are not blamed for the result.
 * 2. [Category.LACKED_COMPOSURE]   — shots ≥ MIN_SHOTS and goals == 0 (after rule 1).
 * 3. [Category.DECISIVE]           — victory + goals ≥ [DECISIVE_MIN_GOALS] + conversion ≥ 0.50.
 * 4. [Category.FELL_SHORT]         — shots ≥ MIN_SHOTS + goals ≥ 1 + conversion < 0.35.
 * 5. [Category.CONSTANT_THREAT]    — positive fallback for any remaining eligible performance.
 *
 * All inputs are integers or doubles — no rounding is applied before classification.
 * The same input always produces the same output.
 */
object AttackingThreatPresenter {

    // ── Thresholds ──────────────────────────────────────────────────────────

    /**
     * Minimum conversion rate (inclusive) required for the [Category.DECISIVE] category.
     * Example: 4 shots and 2 goals → 0.50 → DECISIVE (in a victory).
     */
    const val DECISIVE_CONVERSION_THRESHOLD = 0.50

    /**
     * Maximum conversion rate (exclusive) for the [Category.FELL_SHORT] category.
     * A player with conversion rate below this threshold but at least one goal
     * is classified as "Fell Short".
     * Example: 5 shots and 1 goal → 0.20 < 0.35 → FELL_SHORT.
     */
    const val FELL_SHORT_CONVERSION_THRESHOLD = 0.35

    /**
     * Minimum number of goals (inclusive) required for [Category.DECISIVE].
     * A single goal with high conversion is not enough to be decisive.
     */
    const val DECISIVE_MIN_GOALS = 2

    // ── Category ────────────────────────────────────────────────────────────

    enum class Category {
        /** Draw or 1-goal loss with meaningful volume — missed chance to win. */
        COULD_HAVE_DECIDED,
        /** Any result with zero goals — finishing was poor. */
        LACKED_COMPOSURE,
        /** Victory with strong conversion and multiple goals. */
        DECISIVE,
        /** Had some goals but efficiency was below [FELL_SHORT_CONVERSION_THRESHOLD]. */
        FELL_SHORT,
        /** Positive fallback for solid attacking performances. */
        CONSTANT_THREAT,
    }

    // ── Input / Output models ────────────────────────────────────────────────

    /**
     * Match context used to determine the presentation category.
     *
     * @param shots Total shots attempted by the player.
     * @param goals Total goals scored by the player.
     * @param teamGoals Total goals scored by our team (used to compute goal difference).
     * @param opponentGoals Total goals scored by the opponent.
     */
    data class AttackingThreatContext(
        val shots: Int,
        val goals: Int,
        val teamGoals: Int,
        val opponentGoals: Int,
    )

    /**
     * Presentation model returned by [resolve].
     *
     * @param category The resolved classification — useful for testing.
     * @param title Uppercase title without emoji (e.g. "PERIGO CONSTANTE").
     * @param emoji Leading emoji (e.g. "🔥").
     * @param message Contextual narrative to display as a quote.
     */
    data class AttackingThreatPresentation(
        val category: Category,
        val title: String,
        val emoji: String,
        val message: String,
    )

    // ── Public API ───────────────────────────────────────────────────────────

    /** Resolves the presentation for the given [ctx]. Always deterministic. */
    fun resolve(ctx: AttackingThreatContext): AttackingThreatPresentation {
        val goalDiff = ctx.teamGoals - ctx.opponentGoals
        val conversionRate = if (ctx.shots > 0) ctx.goals.toDouble() / ctx.shots else 0.0
        return presentation(classify(ctx.shots, ctx.goals, goalDiff, conversionRate))
    }

    // ── Classification ───────────────────────────────────────────────────────

    /**
     * Pure classification function — exposed internally so it can be tested in isolation.
     */
    internal fun classify(shots: Int, goals: Int, goalDiff: Int, conversionRate: Double): Category =
        when {
            // 1. COULD_HAVE_DECIDED: draw or narrow loss, meaningful volume, and conversion < 50%.
            //    The player had enough chances to change the result but did not convert enough of them.
            //    Players who converted >= 50% of their shots are not blamed for the result.
            (goalDiff == 0 || goalDiff == -1)
                && shots >= PerigoConstanteSelector.MIN_SHOTS
                && conversionRate < DECISIVE_CONVERSION_THRESHOLD ->
                Category.COULD_HAVE_DECIDED

            // 2. LACKED_COMPOSURE: high volume, zero goals in any other result context.
            goals == 0 && shots >= PerigoConstanteSelector.MIN_SHOTS -> Category.LACKED_COMPOSURE

            // 3. DECISIVE: victory with multiple goals and strong conversion (>= 50%).
            goalDiff > 0 && goals >= DECISIVE_MIN_GOALS && conversionRate >= DECISIVE_CONVERSION_THRESHOLD ->
                Category.DECISIVE

            // 4. FELL_SHORT: sufficient volume, at least one goal, but poor conversion (< 35%).
            shots >= PerigoConstanteSelector.MIN_SHOTS && goals >= 1 && conversionRate < FELL_SHORT_CONVERSION_THRESHOLD ->
                Category.FELL_SHORT

            // 5. CONSTANT_THREAT: positive fallback for any remaining eligible performance.
            else -> Category.CONSTANT_THREAT
        }

    // ── Presentation mapping ─────────────────────────────────────────────────

    private fun presentation(category: Category): AttackingThreatPresentation = when (category) {
        Category.COULD_HAVE_DECIDED -> AttackingThreatPresentation(
            category = category,
            title    = "PODERIA TER DECIDIDO",
            emoji    = "🎯",
            message  = "Teve chances para mudar o resultado, mas deixou a vitória escapar.",
        )
        Category.LACKED_COMPOSURE -> AttackingThreatPresentation(
            category = category,
            title    = "FALTOU CAPRICHO",
            emoji    = "🎯",
            message  = "As chances apareceram, mas a pontaria não ajudou.",
        )
        Category.DECISIVE -> AttackingThreatPresentation(
            category = category,
            title    = "DECISIVO",
            emoji    = "⚡",
            message  = "Quando a chance apareceu, ele resolveu.",
        )
        Category.FELL_SHORT -> AttackingThreatPresentation(
            category = category,
            title    = "FICOU NO QUASE",
            emoji    = "😬",
            message  = "Criou bastante, mas faltou transformar mais chances em gol.",
        )
        Category.CONSTANT_THREAT -> AttackingThreatPresentation(
            category = category,
            title    = "PERIGO CONSTANTE",
            emoji    = "🔥",
            message  = "A defesa adversária não teve sossego quando ele recebeu a bola.",
        )
    }
}

