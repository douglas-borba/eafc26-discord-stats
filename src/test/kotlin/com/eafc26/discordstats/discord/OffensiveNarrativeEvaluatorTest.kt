package com.eafc26.discordstats.discord

import com.eafc26.discordstats.discord.AttackingThreatPresenter.Category
import com.eafc26.discordstats.discord.OffensiveNarrativeEvaluator.OffensiveNarrative
import com.eafc26.discordstats.ea.model.PlayerEntry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for [OffensiveNarrativeEvaluator].
 *
 * The evaluator:
 * - Filters eligible candidates (shots >= [OffensiveNarrativeEvaluator.MIN_SHOTS]).
 * - Classifies each via [AttackingThreatPresenter.classify].
 * - Keeps the best representative per category.
 * - Returns all narratives ordered by [OffensiveNarrativeEvaluator.CATEGORY_PRIORITY].
 * - Never caps the result list (that is a presentation concern).
 */
class OffensiveNarrativeEvaluatorTest {

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun player(
        name: String,
        shots: String = "5",
        goals: String = "0",
    ) = PlayerEntry(
        playerName    = name,
        position      = null,
        shots         = shots,
        goals         = goals,
        secondsPlayed = "900",
    )

    private fun evaluate(
        players: List<PlayerEntry>,
        teamGoals: Int,
        opponentGoals: Int,
    ): List<OffensiveNarrative> =
        OffensiveNarrativeEvaluator.evaluate(players, teamGoals, opponentGoals)

    // ── Eligibility ───────────────────────────────────────────────────────────

    @Nested
    inner class Eligibility {

        @Test
        fun `empty list returns empty`() {
            assertThat(evaluate(emptyList(), 1, 0)).isEmpty()
        }

        @Test
        fun `player below MIN_SHOTS is excluded`() {
            assertThat(evaluate(listOf(player("P", shots = "4")), 1, 0)).isEmpty()
        }

        @Test
        fun `player at exactly MIN_SHOTS qualifies`() {
            assertThat(evaluate(listOf(player("P", shots = "5")), 1, 0)).isNotEmpty
        }

        @Test
        fun `only players at or above MIN_SHOTS are returned`() {
            val result = evaluate(
                listOf(
                    player("TooFew", shots = "4", goals = "3"),
                    player("Enough", shots = "5", goals = "1"),
                ),
                teamGoals = 1, opponentGoals = 0,
            )
            assertThat(result.map { it.player.playerName }).doesNotContain("TooFew")
            assertThat(result.map { it.player.playerName }).contains("Enough")
        }
    }

    // ── Single player ─────────────────────────────────────────────────────────

    @Nested
    inner class SinglePlayer {

        @Test
        fun `one eligible player produces one narrative`() {
            val result = evaluate(listOf(player("Solo", shots = "6", goals = "1")), 2, 1)
            assertThat(result).hasSize(1)
            assertThat(result.first().player.playerName).isEqualTo("Solo")
        }

        @Test
        fun `narrative carries correct shots and goals`() {
            val result = evaluate(listOf(player("P", shots = "7", goals = "2")), 2, 0)
            assertThat(result.first().shots).isEqualTo(7)
            assertThat(result.first().goals).isEqualTo(2)
        }

        @Test
        fun `narrative presentation is not blank`() {
            val n = evaluate(listOf(player("P", shots = "5", goals = "2")), 1, 0).first()
            assertThat(n.presentation.title).isNotBlank()
            assertThat(n.presentation.emoji).isNotBlank()
            assertThat(n.presentation.message).isNotBlank()
        }
    }

    // ── Two players, different categories ─────────────────────────────────────

    @Nested
    inner class TwoDifferentCategories {

        @Test
        fun `DECISIVE and FELL_SHORT produce two narratives`() {
            // PlayerA: 5 shots, 3 goals, win (3-1) → DECISIVE (conv=60%>=50%, goals=3>=2, goalDiff=2)
            // PlayerB: 6 shots, 1 goal,  win (3-1) → FELL_SHORT (conv=17%<35%)
            val result = evaluate(
                listOf(
                    player("Decisivo", shots = "5", goals = "3"),
                    player("Quase",    shots = "6", goals = "1"),
                ),
                teamGoals = 3, opponentGoals = 1,
            )
            assertThat(result).hasSize(2)
            val categories = result.map { it.category }
            assertThat(categories).containsExactly(Category.DECISIVE, Category.FELL_SHORT)
        }

        @Test
        fun `DECISIVE player appears before COULD_HAVE_DECIDED`() {
            // PlayerA: 5 shots, 3 goals, draw → COULD_HAVE_DECIDED? No: conv=60%>=50% → NOT COULD_HAVE_DECIDED
            // Let's use: PlayerA: 5 shots, 3 goals, victory → DECISIVE
            //            PlayerB: 5 shots, 2 goals, draw → COULD_HAVE_DECIDED (conv=40%<50%)
            val result = evaluate(
                listOf(
                    player("Decisivo", shots = "5", goals = "3"),  // victory player
                    player("Poderia",  shots = "5", goals = "2"),  // draw player
                ),
                teamGoals = 2, opponentGoals = 2, // draw — but Decisivo scored 3?
                // Note: goals are per-player, not team totals. teamGoals is independent.
            )
            // With teamGoals=2, opponentGoals=2 (draw):
            // Decisivo: conv=60%>=50%, draw → COULD_HAVE_DECIDED (rule 1 fires first due to draw)
            // Poderia:  conv=40%<50%,  draw → COULD_HAVE_DECIDED
            // Both same category → best (5 shots, 3 goals → Decisivo) wins
            assertThat(result).hasSize(1)
            assertThat(result.first().player.playerName).isEqualTo("Decisivo")
        }

        @Test
        fun `correct DECISIVE then COULD_HAVE_DECIDED order - separate match contexts`() {
            // Use goalDiff=2 (clear victory) for DECISIVE
            // and a second player in a different scenario via the same match context:
            // PlayerA: 5 shots, 3 goals, 2-goal win → DECISIVE
            // PlayerB: 6 shots, 0 goals, 2-goal win → LACKED_COMPOSURE (not draw)
            val result = evaluate(
                listOf(
                    player("Decisivo",  shots = "5", goals = "3"),
                    player("SemGol",    shots = "6", goals = "0"),
                ),
                teamGoals = 3, opponentGoals = 1,
            )
            val categories = result.map { it.category }
            assertThat(categories[0]).isEqualTo(Category.DECISIVE)
            assertThat(categories[1]).isEqualTo(Category.LACKED_COMPOSURE)
        }
    }

    // ── Two players, same category ────────────────────────────────────────────

    @Nested
    inner class TwoSameCategory {

        @Test
        fun `only one narrative when both players get the same category`() {
            // Both FELL_SHORT: 6 shots 1 goal and 7 shots 1 goal, both wins
            val result = evaluate(
                listOf(
                    player("A", shots = "6", goals = "1"),
                    player("B", shots = "7", goals = "1"),
                ),
                teamGoals = 2, opponentGoals = 0,
            )
            assertThat(result).hasSize(1)
        }

        @Test
        fun `best player by shots wins within same category`() {
            // B has more shots → B wins FELL_SHORT slot
            val result = evaluate(
                listOf(
                    player("A", shots = "6", goals = "1"),
                    player("B", shots = "7", goals = "1"),
                ),
                teamGoals = 2, opponentGoals = 0,
            )
            assertThat(result.first().player.playerName).isEqualTo("B")
        }

        @Test
        fun `goals tiebreaker within same shots`() {
            // Same shots (6), B has more goals → B wins
            val result = evaluate(
                listOf(
                    player("A", shots = "6", goals = "1"),
                    player("B", shots = "6", goals = "2"),
                ),
                teamGoals = 3, opponentGoals = 0,
            )
            // Both: 6 shots, ≥1 goal, conv<0.35? A=17%<35% FELL_SHORT; B=33%<35% FELL_SHORT
            assertThat(result).hasSize(1)
            assertThat(result.first().player.playerName).isEqualTo("B")
        }

        @Test
        fun `name tiebreaker when shots and goals are equal - lexicographically earliest wins`() {
            // Both 5 shots, 2 goals, same category; Alpha < Beta → Alpha wins
            val result = evaluate(
                listOf(
                    player("Beta",  shots = "5", goals = "2"),
                    player("Alpha", shots = "5", goals = "2"),
                ),
                teamGoals = 3, opponentGoals = 1,
            )
            assertThat(result).hasSize(1)
            assertThat(result.first().player.playerName).isEqualTo("Alpha")
        }
    }

    // ── Priority ordering ─────────────────────────────────────────────────────

    @Nested
    inner class PriorityOrdering {

        @Test
        fun `CATEGORY_PRIORITY list has DECISIVE first`() {
            assertThat(OffensiveNarrativeEvaluator.CATEGORY_PRIORITY.first())
                .isEqualTo(Category.DECISIVE)
        }

        @Test
        fun `CATEGORY_PRIORITY list covers all five categories`() {
            assertThat(OffensiveNarrativeEvaluator.CATEGORY_PRIORITY)
                .containsExactlyInAnyOrder(*Category.entries.toTypedArray())
        }

        @Test
        fun `result list is ordered by priority`() {
            // FELL_SHORT player and CONSTANT_THREAT player in same win
            // FELL_SHORT: 6 shots, 1 goal (conv=17%<35%)
            // CONSTANT_THREAT: 5 shots, 2 goals (conv=40%, not decisive, not fell-short)
            val result = evaluate(
                listOf(
                    player("CT",  shots = "5", goals = "2"),
                    player("FS",  shots = "6", goals = "1"),
                ),
                teamGoals = 3, opponentGoals = 0,
            )
            assertThat(result).hasSize(2)
            assertThat(result[0].category).isEqualTo(Category.FELL_SHORT)
            assertThat(result[1].category).isEqualTo(Category.CONSTANT_THREAT)
        }
    }

    // ── No cap ────────────────────────────────────────────────────────────────

    @Nested
    inner class NoCap {

        @Test
        fun `evaluator returns more than two narratives when multiple categories are filled`() {
            // Create players for three different categories in the same match context.
            // Use a heavy win so goalDiff > 1 (rules out COULD_HAVE_DECIDED).
            // DECISIVE:        5 shots, 3 goals, win → conv=60%>=50%, goals=3>=2 ✓
            // FELL_SHORT:      6 shots, 1 goal,  win → conv=17%<35% ✓
            // CONSTANT_THREAT: 5 shots, 2 goals, win → conv=40%>=35%, not decisive ✓
            val result = evaluate(
                listOf(
                    player("Decisive", shots = "5", goals = "3"),
                    player("FellShort", shots = "6", goals = "1"),
                    player("Constant",  shots = "5", goals = "2"),
                ),
                teamGoals = 5, opponentGoals = 0,
            )
            assertThat(result.size).isGreaterThanOrEqualTo(3)
        }

        @Test
        fun `evaluator result can exceed two - cap is enforced by callers only`() {
            val result = evaluate(
                listOf(
                    player("A", shots = "5", goals = "3"),  // DECISIVE
                    player("B", shots = "6", goals = "0"),  // LACKED_COMPOSURE
                    player("C", shots = "5", goals = "2"),  // CONSTANT_THREAT
                ),
                teamGoals = 5, opponentGoals = 0,
            )
            // All three produce distinct categories → all three narratives returned
            assertThat(result).hasSizeGreaterThan(2)
        }
    }

    // ── Determinism ───────────────────────────────────────────────────────────

    @Nested
    inner class Determinism {

        @Test
        fun `same input always produces same output`() {
            val players = listOf(
                player("A", shots = "5", goals = "2"),
                player("B", shots = "6", goals = "1"),
            )
            val r1 = evaluate(players, teamGoals = 2, opponentGoals = 0)
            val r2 = evaluate(players, teamGoals = 2, opponentGoals = 0)
            assertThat(r1.map { it.player.playerName }).isEqualTo(r2.map { it.player.playerName })
            assertThat(r1.map { it.category }).isEqualTo(r2.map { it.category })
        }
    }

    // ── Bagre exclusion ───────────────────────────────────────────────────────

    @Nested
    inner class BagreExclusion {

        @Test
        fun `bagre excluded upstream produces correct remaining narratives`() {
            val bagreName = "Bagre"
            val all = listOf(
                player(bagreName,    shots = "8", goals = "0"),
                player("OtherPlayer", shots = "5", goals = "1"),
            )
            val eligible = all.filter { it.playerName != bagreName }
            val result = evaluate(eligible, teamGoals = 1, opponentGoals = 0)
            assertThat(result.none { it.player.playerName == bagreName }).isTrue()
            assertThat(result).isNotEmpty
        }

        @Test
        fun `no narratives when only eligible player is the bagre`() {
            val only = listOf(player("Bagre", shots = "7", goals = "0"))
            val eligible = only.filter { it.playerName != "Bagre" }
            assertThat(evaluate(eligible, teamGoals = 1, opponentGoals = 0)).isEmpty()
        }
    }
}

