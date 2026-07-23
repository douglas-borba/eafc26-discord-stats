package com.eafc26.discordstats.discord

import com.eafc26.discordstats.ea.model.PlayerEntry
import org.slf4j.LoggerFactory

/**
 * Evaluates all eligible outfield players for offensive narratives and returns
 * one representative per category, ordered by priority.
 *
 * ## Responsibilities
 * 1. Filter eligible candidates: [shots >= MIN_SHOTS][MIN_SHOTS].
 * 2. Classify each candidate via [AttackingThreatPresenter.classify] (pure, deterministic).
 * 3. Within each category, retain the best representative
 *    (shots DESC → goals DESC → playerName ascending as final tiebreaker).
 * 4. Return all resulting narratives ordered by [CATEGORY_PRIORITY].
 *
 * ## What this evaluator does NOT do
 * - It does **not** cap the number of results. That is a presentation concern.
 *   Discord may take the first two; a season-recap page may take all of them.
 * - It does **not** apply Bagre exclusion. That is applied upstream by the caller
 *   before the eligible list is passed in.
 *
 * ## Determinism
 * Given the same inputs, the same list is always produced.
 * The tiebreaker within a category is fully deterministic:
 * shots → goals → lexicographically earliest player name.
 */
object OffensiveNarrativeEvaluator {

    private val log = LoggerFactory.getLogger(OffensiveNarrativeEvaluator::class.java)

    /** Minimum shots for a player to be considered for any offensive narrative. */
    const val MIN_SHOTS = 5

    /**
     * Defines the priority order in which narratives are surfaced.
     * Lower index = higher priority.
     */
    val CATEGORY_PRIORITY: List<AttackingThreatPresenter.Category> = listOf(
        AttackingThreatPresenter.Category.DECISIVE,
        AttackingThreatPresenter.Category.COULD_HAVE_DECIDED,
        AttackingThreatPresenter.Category.FELL_SHORT,
        AttackingThreatPresenter.Category.LACKED_COMPOSURE,
        AttackingThreatPresenter.Category.CONSTANT_THREAT,
    )

    /**
     * A single offensive narrative for one player.
     *
     * @param player The selected player entry.
     * @param shots Total shots attempted.
     * @param goals Total goals scored.
     * @param category The classification assigned by [AttackingThreatPresenter].
     * @param presentation The resolved title, emoji and message.
     */
    data class OffensiveNarrative(
        val player: PlayerEntry,
        val shots: Int,
        val goals: Int,
        val category: AttackingThreatPresenter.Category,
        val presentation: AttackingThreatPresenter.AttackingThreatPresentation,
    )

    /**
     * Evaluates [outfield] and returns all offensive narratives ordered by [CATEGORY_PRIORITY].
     *
     * The list may have 0 to [CATEGORY_PRIORITY].size entries.
     * Callers decide how many to display.
     *
     * @param outfield Eligible outfield players. Bagre exclusion must be applied by the caller.
     * @param teamGoals Our team's goals (for goal-difference computation).
     * @param opponentGoals The opponent's goals.
     */
    fun evaluate(
        outfield: Collection<PlayerEntry>,
        teamGoals: Int,
        opponentGoals: Int,
    ): List<OffensiveNarrative> {
        log.debug("[OFFENSIVE] Evaluating {} outfield player(s) | teamGoals={} oppGoals={}",
            outfield.size, teamGoals, opponentGoals)

        val goalDiff = teamGoals - opponentGoals

        // ── Step 1: Build candidates from eligible players ────────────────────

        data class Candidate(
            val player: PlayerEntry,
            val shots: Int,
            val goals: Int,
            val conversionRate: Double,
            val category: AttackingThreatPresenter.Category,
        )

        val candidates = outfield.mapNotNull { player ->
            val name  = player.playerName ?: "(unknown)"
            val shots = player.shots?.toIntOrNull() ?: run {
                log.debug("[OFFENSIVE] SKIP '{}': shots is null/non-numeric", name)
                return@mapNotNull null
            }
            if (shots < MIN_SHOTS) {
                log.debug("[OFFENSIVE] SKIP '{}': shots={} < MIN={}", name, shots, MIN_SHOTS)
                return@mapNotNull null
            }
            val goals          = player.goals?.toIntOrNull() ?: 0
            val conversionRate = if (shots > 0) goals.toDouble() / shots else 0.0
            val category       = AttackingThreatPresenter.classify(shots, goals, goalDiff, conversionRate)
            log.debug("[OFFENSIVE] CANDIDATE '{}': shots={} goals={} conv={:.3f} → {}",
                name, shots, goals, conversionRate, category)
            Candidate(player, shots, goals, conversionRate, category)
        }

        if (candidates.isEmpty()) {
            log.debug("[OFFENSIVE] No eligible candidates — returning empty list")
            return emptyList()
        }

        // ── Step 2: Best representative per category ──────────────────────────
        //
        // Tiebreaker: shots DESC → goals DESC → playerName ascending
        // (lexicographically smallest name, matching the behaviour of the
        //  previous PerigoConstanteSelector).

        val bestByCategory: Map<AttackingThreatPresenter.Category, Candidate> =
            candidates
                .groupBy { it.category }
                .mapValues { (_, group) ->
                    group.maxWithOrNull(
                        compareBy<Candidate> { it.shots }
                            .thenBy { it.goals }
                            .thenByDescending { it.player.playerName ?: "" }
                    )!! // group is always non-empty here
                }

        // ── Step 3: Resolve presentations and order by priority ───────────────

        val narratives = CATEGORY_PRIORITY.mapNotNull { category ->
            val best = bestByCategory[category] ?: return@mapNotNull null
            val ctx  = AttackingThreatPresenter.AttackingThreatContext(
                shots         = best.shots,
                goals         = best.goals,
                teamGoals     = teamGoals,
                opponentGoals = opponentGoals,
            )
            val presentation = AttackingThreatPresenter.resolve(ctx)
            log.debug("[OFFENSIVE] NARRATIVE '{}' → {} '{}': shots={} goals={}",
                best.player.playerName, category, presentation.title, best.shots, best.goals)
            OffensiveNarrative(
                player       = best.player,
                shots        = best.shots,
                goals        = best.goals,
                category     = category,
                presentation = presentation,
            )
        }

        log.debug("[OFFENSIVE] Returning {} narrative(s)", narratives.size)
        return narratives
    }
}

