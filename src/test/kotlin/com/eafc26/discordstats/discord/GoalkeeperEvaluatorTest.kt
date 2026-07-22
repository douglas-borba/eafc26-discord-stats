package com.eafc26.discordstats.discord

import com.eafc26.discordstats.ea.model.PlayerEntry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests for [GoalkeeperEvaluator].
 *
 * Classification priority (first match wins):
 *   1. QUIET          — saves ≤ 1 and goalsConceded ≤ 1
 *   2. WALL           — rating ≥ 8.0 and (cleanSheet or impactSaves ≥ 3)
 *   3. POOR           — rating < 6.0  (EA verdict takes priority over save count)
 *   4. UNDER_SIEGE    — saves ≥ 6     (implicitly rating ≥ 6.0)
 *   5. SOLID          — everything else
 */
class GoalkeeperEvaluatorTest {

    // ── helpers ────────────────────────────────────────────────────────────────

    private fun gk(
        saves: Int              = 0,
        goalsConceded: Int      = 0,
        rating: Double          = 7.0,
        goodDirectionSaves: Int = 0,
        reflexSaves: Int        = 0,
        parrySaves: Int         = 0,
    ) = PlayerEntry(
        playerName         = "GK",
        position           = "0",
        saves              = saves.toString(),
        goalsConceded      = goalsConceded.toString(),
        rating             = rating.toString(),
        goodDirectionSaves = goodDirectionSaves.toString(),
        reflexSaves        = reflexSaves.toString(),
        parrySaves         = parrySaves.toString(),
        secondsPlayed      = "5400",
    )

    private fun classify(
        saves: Int              = 0,
        goalsConceded: Int      = 0,
        rating: Double          = 7.0,
        goodDirectionSaves: Int = 0,
        reflexSaves: Int        = 0,
        parrySaves: Int         = 0,
    ) = GoalkeeperEvaluator.classify(
        saves         = saves,
        goalsConceded = goalsConceded,
        rating        = rating,
        impactSaves   = goodDirectionSaves + reflexSaves + parrySaves,
        cleanSheet    = goalsConceded == 0,
    )

    // ── QUIET ──────────────────────────────────────────────────────────────────

    @Nested
    inner class Quiet {

        @Test
        fun `zero saves zero goals is QUIET`() {
            assertThat(classify(saves = 0, goalsConceded = 0, rating = 7.0))
                .isEqualTo(GoalkeeperArchetype.QUIET)
        }

        @Test
        fun `one save zero goals is QUIET`() {
            assertThat(classify(saves = 1, goalsConceded = 0, rating = 7.5))
                .isEqualTo(GoalkeeperArchetype.QUIET)
        }

        @Test
        fun `zero saves one goal is QUIET`() {
            assertThat(classify(saves = 0, goalsConceded = 1, rating = 6.0))
                .isEqualTo(GoalkeeperArchetype.QUIET)
        }

        @Test
        fun `one save one goal is QUIET`() {
            assertThat(classify(saves = 1, goalsConceded = 1, rating = 5.5))
                .isEqualTo(GoalkeeperArchetype.QUIET)
        }

        @Test
        fun `two saves takes it out of QUIET`() {
            // 2 saves, 0 goals, good rating → WALL (not QUIET)
            assertThat(classify(saves = 2, goalsConceded = 0, rating = 8.5))
                .isNotEqualTo(GoalkeeperArchetype.QUIET)
        }
    }

    // ── WALL ───────────────────────────────────────────────────────────────────

    @Nested
    inner class Wall {

        @Test
        fun `high rating with clean sheet is WALL`() {
            assertThat(classify(saves = 4, goalsConceded = 0, rating = 8.0))
                .isEqualTo(GoalkeeperArchetype.WALL)
        }

        @Test
        fun `high rating with impact saves is WALL even with goals conceded`() {
            assertThat(classify(saves = 5, goalsConceded = 2, rating = 8.0, goodDirectionSaves = 3))
                .isEqualTo(GoalkeeperArchetype.WALL)
        }

        @Test
        fun `exactly at WALL rating threshold with clean sheet is WALL`() {
            assertThat(classify(saves = 3, goalsConceded = 0, rating = 8.0))
                .isEqualTo(GoalkeeperArchetype.WALL)
        }

        @Test
        fun `rating just below WALL threshold is not WALL`() {
            assertThat(classify(saves = 3, goalsConceded = 0, rating = 7.99))
                .isNotEqualTo(GoalkeeperArchetype.WALL)
        }

        @Test
        fun `rating 7_5 is no longer WALL`() {
            // WALL_RATING_THRESHOLD raised from 7.5 to 8.0 — Paredão should feel special
            assertThat(classify(saves = 4, goalsConceded = 0, rating = 7.5))
                .isNotEqualTo(GoalkeeperArchetype.WALL)
        }

        @Test
        fun `high rating with goals conceded and fewer than 3 impact saves is not WALL`() {
            assertThat(classify(saves = 4, goalsConceded = 2, rating = 8.0, goodDirectionSaves = 2))
                .isNotEqualTo(GoalkeeperArchetype.WALL)
        }

        @Test
        fun `excellent rating with several impact saves is WALL`() {
            assertThat(classify(saves = 6, goalsConceded = 1, rating = 8.5, reflexSaves = 4))
                .isEqualTo(GoalkeeperArchetype.WALL)
        }
    }

    // ── POOR ───────────────────────────────────────────────────────────────────

    @Nested
    inner class Poor {

        @Test
        fun `low rating with goals conceded is POOR`() {
            assertThat(classify(saves = 2, goalsConceded = 3, rating = 4.5))
                .isEqualTo(GoalkeeperArchetype.POOR)
        }

        @Test
        fun `rating exactly at POOR threshold is POOR`() {
            assertThat(classify(saves = 2, goalsConceded = 2, rating = 5.9))
                .isEqualTo(GoalkeeperArchetype.POOR)
        }

        @Test
        fun `rating at 6_0 is not POOR`() {
            assertThat(classify(saves = 2, goalsConceded = 2, rating = 6.0))
                .isNotEqualTo(GoalkeeperArchetype.POOR)
        }

        @Test
        fun `poor rating with many saves is POOR not UNDER_SIEGE`() {
            // POOR is checked before UNDER_SIEGE — EA rating wins over save count
            assertThat(classify(saves = 6, goalsConceded = 2, rating = 4.5))
                .isEqualTo(GoalkeeperArchetype.POOR)
        }

        @Test
        fun `many saves and very low rating is POOR`() {
            // Regression: 8 saves, 5 goals conceded, rating 4.8 → POOR (not UNDER_SIEGE)
            assertThat(classify(saves = 8, goalsConceded = 5, rating = 4.8))
                .isEqualTo(GoalkeeperArchetype.POOR)
        }

        @Test
        fun `ten saves with poor rating is POOR not UNDER_SIEGE`() {
            // The EA rating is the verdict — a goalkeeper who made many routine saves
            // but still performed badly should not be described as UNDER_SIEGE
            assertThat(classify(saves = 10, goalsConceded = 4, rating = 5.0))
                .isEqualTo(GoalkeeperArchetype.POOR)
        }
    }

    // ── UNDER_SIEGE ────────────────────────────────────────────────────────────

    @Nested
    inner class UnderSiege {

        @Test
        fun `six saves with acceptable rating triggers UNDER_SIEGE`() {
            assertThat(classify(saves = 6, goalsConceded = 2, rating = 6.5))
                .isEqualTo(GoalkeeperArchetype.UNDER_SIEGE)
        }

        @Test
        fun `many saves with acceptable rating is UNDER_SIEGE`() {
            // saves=10, goalsConceded=2, rating=6.2 → acceptable rating → UNDER_SIEGE
            assertThat(classify(saves = 10, goalsConceded = 2, rating = 6.2))
                .isEqualTo(GoalkeeperArchetype.UNDER_SIEGE)
        }

        @Test
        fun `exactly MIN_SAVES_UNDER_SIEGE with acceptable rating is UNDER_SIEGE`() {
            assertThat(classify(saves = GoalkeeperEvaluator.MIN_SAVES_UNDER_SIEGE, goalsConceded = 1, rating = 6.5))
                .isEqualTo(GoalkeeperArchetype.UNDER_SIEGE)
        }

        @Test
        fun `five saves does not trigger UNDER_SIEGE`() {
            assertThat(classify(saves = 5, goalsConceded = 1, rating = 6.5))
                .isNotEqualTo(GoalkeeperArchetype.UNDER_SIEGE)
        }

        @Test
        fun `high saves with high rating and clean sheet is WALL not UNDER_SIEGE`() {
            // WALL has higher priority than UNDER_SIEGE
            assertThat(classify(saves = 8, goalsConceded = 0, rating = 9.0))
                .isEqualTo(GoalkeeperArchetype.WALL)
        }

        @Test
        fun `many saves with poor rating is POOR not UNDER_SIEGE`() {
            assertThat(classify(saves = 8, goalsConceded = 3, rating = 5.5))
                .isEqualTo(GoalkeeperArchetype.POOR)
        }
    }

    // ── SOLID ──────────────────────────────────────────────────────────────────

    @Nested
    inner class Solid {

        @Test
        fun `average saves and moderate rating is SOLID`() {
            assertThat(classify(saves = 3, goalsConceded = 1, rating = 7.0))
                .isEqualTo(GoalkeeperArchetype.SOLID)
        }

        @Test
        fun `decent rating without clean sheet and not bombarded is SOLID`() {
            assertThat(classify(saves = 4, goalsConceded = 2, rating = 6.5))
                .isEqualTo(GoalkeeperArchetype.SOLID)
        }

        @Test
        fun `moderate rating with few saves and few goals is SOLID`() {
            assertThat(classify(saves = 2, goalsConceded = 1, rating = 6.5))
                .isEqualTo(GoalkeeperArchetype.SOLID)
        }

        @Test
        fun `ordinary performance is SOLID`() {
            assertThat(classify(saves = 3, goalsConceded = 1, rating = 7.0))
                .isEqualTo(GoalkeeperArchetype.SOLID)
        }
    }

    // ── Full evaluate() path ──────────────────────────────────────────────────

    @Nested
    inner class Evaluate {

        @Test
        fun `returns GoalkeeperPerformance with correct archetype and title`() {
            val result = GoalkeeperEvaluator.evaluate(
                gk(saves = 5, goalsConceded = 0, rating = 8.0),
                matchId = "match-001",
                phraseBank = null,
            )
            assertThat(result.archetype).isEqualTo(GoalkeeperArchetype.WALL)
            assertThat(result.title).isEqualTo(GoalkeeperArchetype.WALL.title)
            assertThat(result.message).isNotBlank()
        }

        @Test
        fun `message is not blank for every archetype`() {
            val cases = listOf(
                gk(saves = 0, goalsConceded = 0, rating = 7.0),          // QUIET
                gk(saves = 4, goalsConceded = 0, rating = 8.0),          // WALL
                gk(saves = 8, goalsConceded = 2, rating = 6.5),          // UNDER_SIEGE
                gk(saves = 2, goalsConceded = 3, rating = 5.0),          // POOR
                gk(saves = 3, goalsConceded = 1, rating = 7.0),          // SOLID
            )
            cases.forEach { goalkeeper ->
                val result = GoalkeeperEvaluator.evaluate(goalkeeper, "m1", null)
                assertThat(result.message).isNotBlank()
            }
        }

        @Test
        fun `deterministic phrase selection produces same result for same matchId and name`() {
            val goalkeeper = gk(saves = 3, goalsConceded = 1, rating = 7.0)
            val r1 = GoalkeeperEvaluator.evaluate(goalkeeper, "match-abc", null)
            val r2 = GoalkeeperEvaluator.evaluate(goalkeeper, "match-abc", null)
            assertThat(r1.message).isEqualTo(r2.message)
        }

        @Test
        fun `archetype title matches enum title`() {
            GoalkeeperArchetype.entries.forEach { arch ->
                val result = GoalkeeperEvaluator.evaluate(
                    gkForArchetype(arch),
                    matchId = "t",
                    phraseBank = null,
                )
                assertThat(result.title).isEqualTo(arch.title)
            }
        }

        private fun gkForArchetype(arch: GoalkeeperArchetype) = when (arch) {
            GoalkeeperArchetype.QUIET       -> gk(saves = 0, goalsConceded = 0, rating = 7.0)
            GoalkeeperArchetype.WALL        -> gk(saves = 4, goalsConceded = 0, rating = 8.0)
            GoalkeeperArchetype.UNDER_SIEGE -> gk(saves = 8, goalsConceded = 2, rating = 6.5)
            GoalkeeperArchetype.POOR        -> gk(saves = 2, goalsConceded = 3, rating = 5.0)
            GoalkeeperArchetype.SOLID       -> gk(saves = 3, goalsConceded = 1, rating = 7.0)
        }
    }

    // ── Phrase category mapping ───────────────────────────────────────────────

    @Nested
    inner class PhraseCategoryMapping {

        @Test
        fun `every archetype maps to a distinct phrase category`() {
            val categories = GoalkeeperArchetype.entries
                .map { GoalkeeperEvaluator.phraseCategory(it) }
                .toSet()
            assertThat(categories).hasSize(GoalkeeperArchetype.entries.size)
        }
    }

    // ── Regression — production examples ──────────────────────────────────────

    @Nested
    inner class ProductionRegression {

        /**
         * Many saves but very low rating → POOR.
         * The EA rating is the primary verdict; a goalkeeper who let in many goals
         * despite making saves should not be described as "bombarded" ("Bombardeado").
         */
        @Test
        fun `many saves and very low rating is POOR not UNDER_SIEGE`() {
            val result = GoalkeeperEvaluator.evaluate(
                gk(
                    saves              = 10,
                    goalsConceded      = 2,
                    rating             = 5.3,
                    goodDirectionSaves = 3,
                ),
                matchId    = "prod-match-001",
                phraseBank = null,
            )
            assertThat(result.archetype).isEqualTo(GoalkeeperArchetype.POOR)
            assertThat(result.message).isNotBlank()
        }

        /**
         * Many saves with an acceptable rating → UNDER_SIEGE.
         * The goalkeeper was genuinely bombarded and generally did his job.
         */
        @Test
        fun `many saves and acceptable rating is UNDER_SIEGE`() {
            val result = GoalkeeperEvaluator.evaluate(
                gk(
                    saves         = 10,
                    goalsConceded = 2,
                    rating        = 6.2,
                ),
                matchId    = "prod-match-004",
                phraseBank = null,
            )
            assertThat(result.archetype).isEqualTo(GoalkeeperArchetype.UNDER_SIEGE)
            assertThat(result.title).isEqualTo(GoalkeeperArchetype.UNDER_SIEGE.title)
            assertThat(result.message).isNotBlank()
        }

        /** Excellent rating + clean sheet → WALL. */
        @Test
        fun `clean sheet with high rating is WALL`() {
            val result = GoalkeeperEvaluator.evaluate(
                gk(saves = 5, goalsConceded = 0, rating = 8.5),
                matchId    = "prod-match-002",
                phraseBank = null,
            )
            assertThat(result.archetype).isEqualTo(GoalkeeperArchetype.WALL)
            assertThat(result.title).isEqualTo(GoalkeeperArchetype.WALL.title)
        }

        /** Excellent rating + several impact saves → WALL. */
        @Test
        fun `high rating with several impact saves is WALL`() {
            val result = GoalkeeperEvaluator.evaluate(
                gk(saves = 5, goalsConceded = 0, rating = 8.5, goodDirectionSaves = 3),
                matchId    = "prod-match-005",
                phraseBank = null,
            )
            assertThat(result.archetype).isEqualTo(GoalkeeperArchetype.WALL)
        }

        /** Quiet match → QUIET. */
        @Test
        fun `goalkeeper with no saves and no goals conceded is QUIET`() {
            val result = GoalkeeperEvaluator.evaluate(
                gk(saves = 0, goalsConceded = 0, rating = 7.0),
                matchId    = "prod-match-003",
                phraseBank = null,
            )
            assertThat(result.archetype).isEqualTo(GoalkeeperArchetype.QUIET)
            assertThat(result.title).isEqualTo(GoalkeeperArchetype.QUIET.title)
        }

        /** Ordinary performance → SOLID. */
        @Test
        fun `ordinary match is SOLID`() {
            val result = GoalkeeperEvaluator.evaluate(
                gk(saves = 3, goalsConceded = 1, rating = 7.0),
                matchId    = "prod-match-006",
                phraseBank = null,
            )
            assertThat(result.archetype).isEqualTo(GoalkeeperArchetype.SOLID)
        }
    }
}

