package com.eafc26.discordstats.discord

import com.eafc26.discordstats.discord.AttackingThreatPresenter.AttackingThreatContext
import com.eafc26.discordstats.discord.AttackingThreatPresenter.Category
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests for [AttackingThreatPresenter].
 *
 * Thresholds:
 * - MIN_SHOTS = 5  (PerigoConstanteSelector.MIN_SHOTS)
 * - DECISIVE_CONVERSION_THRESHOLD  = 0.50
 * - DECISIVE_MIN_GOALS             = 2
 * - FELL_SHORT_CONVERSION_THRESHOLD = 0.35
 *
 * Classification priority (first match wins):
 * 1. COULD_HAVE_DECIDED — draw/1-goal loss + shots>=5 + conversionRate < 0.50
 * 2. LACKED_COMPOSURE  — shots>=5 + goals==0
 * 3. DECISIVE          — victory + goals>=2 + conversionRate>=0.50
 * 4. FELL_SHORT        — shots>=5 + goals>=1 + conversionRate<0.35
 * 5. CONSTANT_THREAT   — positive fallback
 */
class AttackingThreatPresenterTest {

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun ctx(shots: Int, goals: Int, teamGoals: Int, opponentGoals: Int) =
        AttackingThreatContext(shots, goals, teamGoals, opponentGoals)

    private fun category(shots: Int, goals: Int, teamGoals: Int, opponentGoals: Int): Category =
        AttackingThreatPresenter.resolve(ctx(shots, goals, teamGoals, opponentGoals)).category

    private fun presentation(shots: Int, goals: Int, teamGoals: Int, opponentGoals: Int) =
        AttackingThreatPresenter.resolve(ctx(shots, goals, teamGoals, opponentGoals))

    // ── Eligibility gate (MIN_SHOTS = 5) ─────────────────────────────────────

    @Nested
    inner class Eligibility {

        @Test
        fun `4 shots is below threshold - selector returns null`() {
            val result = PerigoConstanteSelector.select(listOf(player("P", shots = "4")))
            assertThat(result).isNull()
        }

        @Test
        fun `5 shots meets threshold - selector returns a selection`() {
            val result = PerigoConstanteSelector.select(listOf(player("P", shots = "5")))
            assertThat(result).isNotNull
        }

        @Test
        fun `no eligible candidates - selector returns null`() {
            assertThat(PerigoConstanteSelector.select(emptyList())).isNull()
        }
    }

    // ── COULD_HAVE_DECIDED ────────────────────────────────────────────────────

    @Nested
    inner class CouldHaveDecided {

        @Test
        fun `5 shots 2 goals draw is COULD_HAVE_DECIDED - canonical example`() {
            // conversion = 40% < 50%
            assertThat(category(shots = 5, goals = 2, teamGoals = 1, opponentGoals = 1))
                .isEqualTo(Category.COULD_HAVE_DECIDED)
        }

        @Test
        fun `5 shots 2 goals draw title communicates missed opportunity`() {
            val p = presentation(shots = 5, goals = 2, teamGoals = 1, opponentGoals = 1)
            assertThat(p.title).isEqualTo("PODERIA TER DECIDIDO")
            assertThat(p.emoji).isEqualTo("🎯")
            assertThat(p.message).contains("vitória")
        }

        @Test
        fun `5 shots 2 goals one-goal defeat is COULD_HAVE_DECIDED`() {
            // goalDiff = -1, conversion = 40%
            assertThat(category(shots = 5, goals = 2, teamGoals = 1, opponentGoals = 2))
                .isEqualTo(Category.COULD_HAVE_DECIDED)
        }

        @Test
        fun `6 shots 2 goals one-goal defeat is COULD_HAVE_DECIDED`() {
            // conversion = 33% < 50%
            assertThat(category(shots = 6, goals = 2, teamGoals = 1, opponentGoals = 2))
                .isEqualTo(Category.COULD_HAVE_DECIDED)
        }

        @Test
        fun `5 shots 0 goals draw is COULD_HAVE_DECIDED - contextual rule wins over LACKED_COMPOSURE`() {
            // goalDiff=0, shots=5>=5, conversion=0%<50%
            assertThat(category(shots = 5, goals = 0, teamGoals = 0, opponentGoals = 0))
                .isEqualTo(Category.COULD_HAVE_DECIDED)
        }

        @Test
        fun `5 shots 3 goals draw is NOT COULD_HAVE_DECIDED - conversion equals 60 percent`() {
            // conversionRate = 0.6 >= 0.50 → rule 1 does not fire
            assertThat(category(shots = 5, goals = 3, teamGoals = 2, opponentGoals = 2))
                .isNotEqualTo(Category.COULD_HAVE_DECIDED)
        }

        @Test
        fun `6 shots 3 goals draw is NOT COULD_HAVE_DECIDED - conversion equals 50 percent`() {
            // conversionRate = 0.5 is NOT < 0.50 → rule 1 does not fire
            assertThat(category(shots = 6, goals = 3, teamGoals = 2, opponentGoals = 2))
                .isNotEqualTo(Category.COULD_HAVE_DECIDED)
        }

        @Test
        fun `exactly 50 percent conversion in a draw is NOT COULD_HAVE_DECIDED`() {
            val cat = AttackingThreatPresenter.classify(
                shots = 6, goals = 3, goalDiff = 0, conversionRate = 0.50
            )
            assertThat(cat).isNotEqualTo(Category.COULD_HAVE_DECIDED)
        }

        @Test
        fun `comfortable victory with 5 shots and 2 goals is NOT COULD_HAVE_DECIDED`() {
            // goalDiff = 2 → rule 1 requires goalDiff == 0 or -1
            assertThat(category(shots = 5, goals = 2, teamGoals = 3, opponentGoals = 1))
                .isNotEqualTo(Category.COULD_HAVE_DECIDED)
        }

        @Test
        fun `heavy defeat with 5 shots and 2 goals is NOT COULD_HAVE_DECIDED`() {
            // goalDiff = -3 → not narrow
            assertThat(category(shots = 5, goals = 2, teamGoals = 0, opponentGoals = 3))
                .isNotEqualTo(Category.COULD_HAVE_DECIDED)
        }

        @Test
        fun `draw with shots below MIN_SHOTS does NOT produce COULD_HAVE_DECIDED`() {
            // shots = 4 < 5 → rule 1 condition fails
            val cat = AttackingThreatPresenter.classify(
                shots = 4, goals = 1, goalDiff = 0, conversionRate = 0.25
            )
            assertThat(cat).isNotEqualTo(Category.COULD_HAVE_DECIDED)
        }
    }

    // ── LACKED_COMPOSURE ─────────────────────────────────────────────────────

    @Nested
    inner class LackedComposure {

        @Test
        fun `5 shots 0 goals victory is LACKED_COMPOSURE`() {
            // goalDiff=2 → not draw/narrow-loss; goals=0, shots=5>=5
            assertThat(category(shots = 5, goals = 0, teamGoals = 2, opponentGoals = 0))
                .isEqualTo(Category.LACKED_COMPOSURE)
        }

        @Test
        fun `5 shots 0 goals heavy defeat is LACKED_COMPOSURE`() {
            // goalDiff=-3 → not draw/narrow-loss; goals=0, shots=5>=5
            assertThat(category(shots = 5, goals = 0, teamGoals = 0, opponentGoals = 3))
                .isEqualTo(Category.LACKED_COMPOSURE)
        }

        @Test
        fun `8 shots 0 goals victory is LACKED_COMPOSURE`() {
            assertThat(category(shots = 8, goals = 0, teamGoals = 3, opponentGoals = 0))
                .isEqualTo(Category.LACKED_COMPOSURE)
        }

        @Test
        fun `5 shots 0 goals draw becomes COULD_HAVE_DECIDED not LACKED_COMPOSURE`() {
            // Rule 1 fires first because goalDiff=0 and conversion=0%<50%
            assertThat(category(shots = 5, goals = 0, teamGoals = 1, opponentGoals = 1))
                .isEqualTo(Category.COULD_HAVE_DECIDED)
        }

        @Test
        fun `LACKED_COMPOSURE title and message are appropriate`() {
            val p = presentation(shots = 6, goals = 0, teamGoals = 2, opponentGoals = 0)
            assertThat(p.title).isEqualTo("FALTOU CAPRICHO")
            assertThat(p.emoji).isEqualTo("🎯")
            assertThat(p.message).isNotBlank()
        }
    }

    // ── DECISIVE ─────────────────────────────────────────────────────────────

    @Nested
    inner class Decisive {

        @Test
        fun `5 shots 3 goals victory is DECISIVE`() {
            // conversionRate = 0.6 >= 0.50, goals=3>=2, goalDiff=2
            assertThat(category(shots = 5, goals = 3, teamGoals = 3, opponentGoals = 1))
                .isEqualTo(Category.DECISIVE)
        }

        @Test
        fun `6 shots 3 goals victory is DECISIVE`() {
            // conversionRate = 0.5 >= 0.50
            assertThat(category(shots = 6, goals = 3, teamGoals = 3, opponentGoals = 1))
                .isEqualTo(Category.DECISIVE)
        }

        @Test
        fun `exactly 50 percent conversion in a victory with 2 goals is DECISIVE`() {
            val cat = AttackingThreatPresenter.classify(
                shots = 4, goals = 2, goalDiff = 2, conversionRate = 0.50
            )
            assertThat(cat).isEqualTo(Category.DECISIVE)
        }

        @Test
        fun `5 shots 2 goals victory is NOT DECISIVE - conversion is 40 percent`() {
            // conversionRate = 0.4 < 0.50
            assertThat(category(shots = 5, goals = 2, teamGoals = 2, opponentGoals = 0))
                .isNotEqualTo(Category.DECISIVE)
        }

        @Test
        fun `4 shots 2 goals victory is omitted via selector - below MIN_SHOTS`() {
            // The selector would not pick this player (4 < MIN_SHOTS=5)
            val result = PerigoConstanteSelector.select(listOf(player("P", shots = "4", goals = "2")))
            assertThat(result).isNull()
        }

        @Test
        fun `DECISIVE title and emoji are positive`() {
            val p = presentation(shots = 5, goals = 3, teamGoals = 3, opponentGoals = 1)
            assertThat(p.title).isEqualTo("DECISIVO")
            assertThat(p.emoji).isEqualTo("⚡")
            assertThat(p.message).isNotBlank()
        }
    }

    // ── FELL_SHORT ───────────────────────────────────────────────────────────

    @Nested
    inner class FellShort {

        @Test
        fun `6 shots 1 goal is FELL_SHORT`() {
            // conversionRate = 0.167 < 0.35
            assertThat(category(shots = 6, goals = 1, teamGoals = 1, opponentGoals = 0))
                .isEqualTo(Category.FELL_SHORT)
        }

        @Test
        fun `8 shots 2 goals is FELL_SHORT`() {
            // conversionRate = 0.25 < 0.35
            assertThat(category(shots = 8, goals = 2, teamGoals = 2, opponentGoals = 0))
                .isEqualTo(Category.FELL_SHORT)
        }

        @Test
        fun `5 shots 2 goals is NOT FELL_SHORT - conversion is 40 percent`() {
            // conversionRate = 0.4 >= 0.35
            assertThat(category(shots = 5, goals = 2, teamGoals = 2, opponentGoals = 0))
                .isNotEqualTo(Category.FELL_SHORT)
        }

        @Test
        fun `exactly 35 percent conversion is NOT FELL_SHORT`() {
            // conversionRate = 0.35 is NOT < 0.35 → threshold is a strict lower bound
            val cat = AttackingThreatPresenter.classify(
                shots = 20, goals = 7, goalDiff = 3, conversionRate = 0.35
            )
            assertThat(cat).isNotEqualTo(Category.FELL_SHORT)
        }

        @Test
        fun `FELL_SHORT title and message communicate near-miss narrative`() {
            val p = presentation(shots = 6, goals = 1, teamGoals = 1, opponentGoals = 0)
            assertThat(p.title).isEqualTo("FICOU NO QUASE")
            assertThat(p.emoji).isEqualTo("😬")
            assertThat(p.message).isNotBlank()
        }
    }

    // ── CONSTANT_THREAT ───────────────────────────────────────────────────────

    @Nested
    inner class ConstantThreat {

        @Test
        fun `victory with moderate conversion is CONSTANT_THREAT`() {
            // 5 shots, 2 goals = 40%; goalDiff=2 → not rule 1; goals>0 → not rule 2;
            // 40%<50% → not DECISIVE; 40%>=35% → not FELL_SHORT → CONSTANT_THREAT
            assertThat(category(shots = 5, goals = 2, teamGoals = 3, opponentGoals = 1))
                .isEqualTo(Category.CONSTANT_THREAT)
        }

        @Test
        fun `5 shots 3 goals draw resolves to CONSTANT_THREAT`() {
            // conversionRate=0.6 => not COULD_HAVE_DECIDED (>=50%); goals>0; not victory→not DECISIVE;
            // 0.6>=0.35 → not FELL_SHORT → CONSTANT_THREAT
            assertThat(category(shots = 5, goals = 3, teamGoals = 2, opponentGoals = 2))
                .isEqualTo(Category.CONSTANT_THREAT)
        }

        @Test
        fun `CONSTANT_THREAT title and emoji are positive`() {
            val p = presentation(shots = 5, goals = 2, teamGoals = 3, opponentGoals = 1)
            assertThat(p.title).isEqualTo("PERIGO CONSTANTE")
            assertThat(p.emoji).isEqualTo("🔥")
            assertThat(p.message).isNotBlank()
        }

        @Test
        fun `CONSTANT_THREAT message does not imply wastefulness`() {
            val p = presentation(shots = 5, goals = 2, teamGoals = 3, opponentGoals = 1)
            // The positive message should not contain negative keywords
            assertThat(p.message.lowercase()).doesNotContain("faltou", "perdeu", "desperdiç")
        }
    }

    // ── Edge cases ────────────────────────────────────────────────────────────

    @Nested
    inner class EdgeCases {

        @Test
        fun `zero shots produces zero conversion without throwing`() {
            // conversionRate guard → 0.0; goalDiff=2, goals=0, shots=0<5 → else CONSTANT_THREAT
            val cat = AttackingThreatPresenter.classify(
                shots = 0, goals = 0, goalDiff = 1, conversionRate = 0.0
            )
            // shots=0 < MIN_SHOTS → won't be LACKED_COMPOSURE either
            assertThat(cat).isEqualTo(Category.CONSTANT_THREAT)
        }

        @Test
        fun `one shot one goal in a victory resolves without error`() {
            // shots=1 < MIN_SHOTS → not LACKED_COMPOSURE, not FELL_SHORT; goalDiff=1, goals=1<2 → not DECISIVE
            // falls to CONSTANT_THREAT
            val cat = category(shots = 1, goals = 1, teamGoals = 1, opponentGoals = 0)
            assertThat(cat).isEqualTo(Category.CONSTANT_THREAT)
        }

        @Test
        fun `same input always produces same output - deterministic`() {
            val ctx = ctx(shots = 5, goals = 2, teamGoals = 1, opponentGoals = 1)
            val r1 = AttackingThreatPresenter.resolve(ctx)
            val r2 = AttackingThreatPresenter.resolve(ctx)
            assertThat(r1).isEqualTo(r2)
        }

        @Test
        fun `deterministic tie-breaking by goals then name in selector`() {
            val players = listOf(
                player("Alpha", shots = "5", goals = "3"),
                player("Beta",  shots = "5", goals = "3"),
            )
            // Same shots and same goals → name tiebreaker is thenByDescending(name).
            // maxWithOrNull picks the maximum per comparator; descending name order means
            // the lexicographically smallest name ('A' < 'B') is ranked highest → Alpha wins.
            val result = PerigoConstanteSelector.select(players)
            assertThat(result).isNotNull
            assertThat(result!!.player.playerName).isEqualTo("Alpha")
        }
    }

    // ── Bagre exclusion (integration with selector) ───────────────────────────

    @Nested
    inner class BagreExclusion {

        @Test
        fun `bagre player excluded before selector is called`() {
            // Simulates the DiscordEmbedBuilder excluding the bagre player by name
            val bagreName = "LowRated"
            val candidates = listOf(
                player(bagreName, shots = "7", goals = "2"),
                player("OtherPlayer", shots = "5", goals = "1"),
            )
            val eligible = candidates.filter { it.playerName != bagreName }
            val result = PerigoConstanteSelector.select(eligible)
            assertThat(result).isNotNull
            assertThat(result!!.player.playerName).isEqualTo("OtherPlayer")
        }

        @Test
        fun `when only candidate is the bagre player no award is produced`() {
            val bagreName = "OnlyPlayer"
            val candidates = listOf(player(bagreName, shots = "7", goals = "2"))
            val eligible = candidates.filter { it.playerName != bagreName }
            assertThat(PerigoConstanteSelector.select(eligible)).isNull()
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun player(name: String, shots: String = "5", goals: String = "0") =
        com.eafc26.discordstats.ea.model.PlayerEntry(
            playerName = name,
            position = null,
            shots = shots,
            goals = goals,
            secondsPlayed = "900",
        )

    // ── Regression: 6 shots 1 goal (real-match observation) ──────────────────
    //
    // Root cause of the observed "🔥 PERIGO CONSTANTE" + empty message:
    //   The HTML templates (index.html, match-card.html) read pc.phrase, but
    //   PerigoConstanteSection now exposes message. The template received
    //   undefined, which rendered as "".  The title was hardcoded to
    //   '🔥 PERIGO CONSTANTE' in both templates, masking the real category.
    //
    // With the current classify() logic, 6 shots + 1 goal can never reach
    // CONSTANT_THREAT — the tests below assert the correct categories.

    @Nested
    inner class SixShotsOneGoalRegression {

        @Test
        fun `6 shots 1 goal draw is COULD_HAVE_DECIDED`() {
            // goalDiff=0, shots=6>=5, conv=0.167<0.50 → rule 1
            assertThat(category(shots = 6, goals = 1, teamGoals = 1, opponentGoals = 1))
                .isEqualTo(Category.COULD_HAVE_DECIDED)
        }

        @Test
        fun `6 shots 1 goal one-goal defeat is COULD_HAVE_DECIDED`() {
            // goalDiff=-1, shots=6>=5, conv=0.167<0.50 → rule 1
            assertThat(category(shots = 6, goals = 1, teamGoals = 1, opponentGoals = 2))
                .isEqualTo(Category.COULD_HAVE_DECIDED)
        }

        @Test
        fun `6 shots 1 goal victory is FELL_SHORT`() {
            // goalDiff=1>0 → rule 1 fails; goals=1 → rule 2 fails;
            // goals=1<2 → rule 3 fails; conv=0.167<0.35, shots=6>=5 → rule 4 FELL_SHORT
            assertThat(category(shots = 6, goals = 1, teamGoals = 2, opponentGoals = 1))
                .isEqualTo(Category.FELL_SHORT)
        }

        @Test
        fun `6 shots 1 goal comfortable victory is FELL_SHORT`() {
            // goalDiff=3; same reasoning as above
            assertThat(category(shots = 6, goals = 1, teamGoals = 3, opponentGoals = 0))
                .isEqualTo(Category.FELL_SHORT)
        }

        @Test
        fun `6 shots 1 goal heavy defeat is FELL_SHORT`() {
            // goalDiff=-3 → rule 1 fails (not narrow); goals=1 → rule 2 fails;
            // rule 3 fails; conv=0.167<0.35, shots=6>=5 → FELL_SHORT
            assertThat(category(shots = 6, goals = 1, teamGoals = 1, opponentGoals = 4))
                .isEqualTo(Category.FELL_SHORT)
        }

        @Test
        fun `6 shots 1 goal never resolves to CONSTANT_THREAT`() {
            // Verify against every plausible goalDiff — none should reach CONSTANT_THREAT
            val goalDiffs = listOf(-5, -4, -3, -2, 1, 2, 3, 4, 5)
            goalDiffs.forEach { diff ->
                val teamG = if (diff >= 0) diff else 0
                val oppG  = if (diff < 0) -diff else 0
                assertThat(category(shots = 6, goals = 1, teamGoals = teamG, opponentGoals = oppG))
                    .withFailMessage("Expected non-CONSTANT_THREAT for goalDiff=$diff but got CONSTANT_THREAT")
                    .isNotEqualTo(Category.CONSTANT_THREAT)
            }
        }
    }

    // ── Blank-string guarantee ────────────────────────────────────────────────
    //
    // Every presentation returned by the presenter must have a non-blank title,
    // emoji, and message.  This test exhaustively covers all five categories
    // using a representative input for each so that a stale or missing string
    // literal is caught immediately.

    @Nested
    inner class NoBlanksGuarantee {

        private fun assertNoBlanks(p: AttackingThreatPresenter.AttackingThreatPresentation) {
            assertThat(p.title).withFailMessage("title must not be blank for category ${p.category}").isNotBlank()
            assertThat(p.emoji).withFailMessage("emoji must not be blank for category ${p.category}").isNotBlank()
            assertThat(p.message).withFailMessage("message must not be blank for category ${p.category}").isNotBlank()
        }

        @Test
        fun `COULD_HAVE_DECIDED presentation has no blank strings`() {
            // 5 shots, 2 goals, draw
            assertNoBlanks(presentation(shots = 5, goals = 2, teamGoals = 1, opponentGoals = 1))
        }

        @Test
        fun `LACKED_COMPOSURE presentation has no blank strings`() {
            // 6 shots, 0 goals, big win
            assertNoBlanks(presentation(shots = 6, goals = 0, teamGoals = 3, opponentGoals = 0))
        }

        @Test
        fun `DECISIVE presentation has no blank strings`() {
            // 5 shots, 3 goals, victory
            assertNoBlanks(presentation(shots = 5, goals = 3, teamGoals = 3, opponentGoals = 1))
        }

        @Test
        fun `FELL_SHORT presentation has no blank strings`() {
            // 6 shots, 1 goal, victory
            assertNoBlanks(presentation(shots = 6, goals = 1, teamGoals = 2, opponentGoals = 1))
        }

        @Test
        fun `CONSTANT_THREAT presentation has no blank strings`() {
            // 5 shots, 2 goals, comfortable win (40% conversion → not decisive, not fell-short)
            assertNoBlanks(presentation(shots = 5, goals = 2, teamGoals = 3, opponentGoals = 1))
        }

        @Test
        fun `all five Category enum values are covered and return non-blank presentations`() {
            // Force every category via the internal classify() function and verify
            val cases = mapOf(
                Category.COULD_HAVE_DECIDED to Triple(5, 2, 0),   // shots=5, goals=2, goalDiff=0
                Category.LACKED_COMPOSURE   to Triple(6, 0, 2),   // shots=6, goals=0, goalDiff=2
                Category.DECISIVE           to Triple(5, 3, 2),   // shots=5, goals=3, goalDiff=2
                Category.FELL_SHORT         to Triple(6, 1, 1),   // shots=6, goals=1, goalDiff=1
                Category.CONSTANT_THREAT    to Triple(5, 2, 2),   // shots=5, goals=2, goalDiff=2
            )
            cases.forEach { (expectedCategory, params) ->
                val (shots, goals, goalDiff) = params
                val conv = if (shots > 0) goals.toDouble() / shots else 0.0
                val actualCategory = AttackingThreatPresenter.classify(shots, goals, goalDiff, conv)
                assertThat(actualCategory)
                    .withFailMessage("Expected classify() to produce $expectedCategory for inputs shots=$shots goals=$goals goalDiff=$goalDiff but got $actualCategory")
                    .isEqualTo(expectedCategory)
                // Resolve via the public API to check strings
                val p = AttackingThreatPresenter.resolve(
                    AttackingThreatContext(shots, goals,
                        if (goalDiff >= 0) goalDiff else 0,
                        if (goalDiff < 0) -goalDiff else 0)
                )
                assertNoBlanks(p)
            }
        }
    }
}
