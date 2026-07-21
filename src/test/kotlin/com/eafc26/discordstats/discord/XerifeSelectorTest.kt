package com.eafc26.discordstats.discord

import com.eafc26.discordstats.ea.model.PlayerEntry
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

/**
 * Tests for XerifeSelector.
 *
 * DIS = tacklesMade × (tacklesMade / tackleAttempts) × √(min(seconds, 5400) / 5400)
 */
class XerifeSelectorTest {

    // ── helpers ────────────────────────────────────────────────────────────────

    private fun player(
        name: String,
        tackleAttempts: String? = null,
        tacklesMade: String? = null,
        seconds: String = "5400",
    ) = PlayerEntry(
        playerName = name,
        position = null,
        tackleAttempts = tackleAttempts,
        tacklesMade = tacklesMade,
        secondsPlayed = seconds,
    )

    /** Expected DIS for known inputs — mirrors the formula exactly. */
    private fun dis(made: Int, attempts: Int, seconds: Int): Double {
        val fraction = made.toDouble() / attempts
        val timeWeight = sqrt(minOf(seconds, XerifeSelector.FULL_MATCH_SECONDS).toDouble() / XerifeSelector.FULL_MATCH_SECONDS)
        return made * fraction * timeWeight
    }

    // ── Eligibility ────────────────────────────────────────────────────────────

    @Nested
    inner class Eligibility {

        @Test
        fun `returns null for empty list`() {
            assertThat(XerifeSelector.select(emptyList())).isNull()
        }

        @Test
        fun `returns null when no player attempted a tackle`() {
            val players = listOf(player("None", tackleAttempts = "0", tacklesMade = "0"))
            assertThat(XerifeSelector.select(players)).isNull()
        }

        @Test
        fun `returns null when tackleAttempts is missing`() {
            val players = listOf(player("NoData", tackleAttempts = null, tacklesMade = null))
            assertThat(XerifeSelector.select(players)).isNull()
        }

        @Test
        fun `returns null when player played below MIN_SECONDS_PLAYED`() {
            // 539s — one second below the 9-minute threshold
            val players = listOf(player("TooShort", tackleAttempts = "10", tacklesMade = "9", seconds = "539"))
            assertThat(XerifeSelector.select(players)).isNull()
        }

        @Test
        fun `player at exactly MIN_SECONDS_PLAYED qualifies`() {
            val players = listOf(player("ExactMin", tackleAttempts = "5", tacklesMade = "4", seconds = "540"))
            assertThat(XerifeSelector.select(players)).isNotNull
        }

        @Test
        fun `zero tackle attempts disqualifies even with full playing time`() {
            val players = listOf(player("Zero", tackleAttempts = "0", tacklesMade = "0", seconds = "5400"))
            assertThat(XerifeSelector.select(players)).isNull()
        }

        @Test
        fun `no binary rate threshold — 60 percent now qualifies`() {
            // The old implementation disqualified exactly 60 %. Under DIS there is no hard gate.
            val players = listOf(player("Sixty", tackleAttempts = "10", tacklesMade = "6"))
            val result = XerifeSelector.select(players)
            assertThat(result).isNotNull
            assertThat(result!!.player.playerName).isEqualTo("Sixty")
        }

        @Test
        fun `no binary rate threshold — 50 percent qualifies`() {
            val players = listOf(player("Fifty", tackleAttempts = "10", tacklesMade = "5"))
            assertThat(XerifeSelector.select(players)).isNotNull
        }
    }

    // ── DIS formula ────────────────────────────────────────────────────────────

    @Nested
    inner class Formula {

        @Test
        fun `score equals tacklesMade x successFraction x sqrtTimeWeight`() {
            // 8 made / 10 att = 0.8 fraction, 5400s → timeWeight = 1.0
            // DIS = 8 × 0.8 × 1.0 = 6.4
            val players = listOf(player("A", tackleAttempts = "10", tacklesMade = "8", seconds = "5400"))
            val result = XerifeSelector.select(players)!!
            assertThat(result.defensiveScore).isCloseTo(dis(8, 10, 5400), within(0.001))
        }

        @Test
        fun `timeWeight is capped at 1 for overtime seconds`() {
            // 6000s > 5400 — capped at 5400 before sqrt
            // DIS = 5 × 1.0 × √1.0 = 5.0
            val players = listOf(player("Extra", tackleAttempts = "5", tacklesMade = "5", seconds = "6000"))
            val result = XerifeSelector.select(players)!!
            assertThat(result.defensiveScore).isCloseTo(dis(5, 5, 5400), within(0.001))
        }

        @Test
        fun `timeWeight for half a match is sqrt of 0_5 not 0_5`() {
            // This test documents that sqrt gives 0.707, not the linear 0.5
            val players = listOf(player("Half", tackleAttempts = "4", tacklesMade = "4", seconds = "2700"))
            val result = XerifeSelector.select(players)!!
            val expectedWeight = sqrt(0.5)                  // ≈ 0.707 (not 0.5)
            val expectedScore = 4 * 1.0 * expectedWeight    // ≈ 2.828
            assertThat(result.defensiveScore).isCloseTo(expectedScore, within(0.001))
        }

        @Test
        fun `zero tacklesMade yields score of zero regardless of attempts`() {
            val players = listOf(
                player("ZeroMade", tackleAttempts = "10", tacklesMade = "0"),
                player("OneMade",  tackleAttempts = "1",  tacklesMade = "1"),
            )
            // ZeroMade DIS = 0; OneMade DIS = 1.0
            assertThat(XerifeSelector.select(players)!!.player.playerName).isEqualTo("OneMade")
        }
    }

    // ── Efficiency vs volume ───────────────────────────────────────────────────

    @Nested
    inner class EfficiencyVsVolume {

        @Test
        fun `efficient player beats high-volume sloppy player`() {
            // SloppyBig:  12 made / 30 att (40%), full game → DIS = 12 × 0.4  × 1.0 = 4.8
            // CleanSmall:  9 made / 11 att (81%), full game → DIS =  9 × 0.818 × 1.0 ≈ 7.36
            val players = listOf(
                player("SloppyBig",  tackleAttempts = "30", tacklesMade = "12"),
                player("CleanSmall", tackleAttempts = "11", tacklesMade = "9"),
            )
            assertThat(XerifeSelector.select(players)!!.player.playerName).isEqualTo("CleanSmall")
        }

        @Test
        fun `same rate but higher volume wins`() {
            // Both 80%; More wins by volume
            // DIS(More)   = 8 × 0.8 × 1.0 = 6.4
            // DIS(Fewer)  = 4 × 0.8 × 1.0 = 3.2
            val players = listOf(
                player("More",  tackleAttempts = "10", tacklesMade = "8"),
                player("Fewer", tackleAttempts = "5",  tacklesMade = "4"),
            )
            assertThat(XerifeSelector.select(players)!!.player.playerName).isEqualTo("More")
        }
    }

    // ── Time weighting (sqrt) ──────────────────────────────────────────────────

    @Nested
    inner class TimeWeighting {

        /**
         * With LINEAR weighting the starter would need only ~2× the sub's per-minute output
         * to beat them; with sqrt the bar drops to ~2× — but crucially the sub can still win
         * if they were genuinely ~2× more impactful.
         *
         * This test documents the "fair sub" scenario: an outstanding 20-min sub
         * should beat a mediocre full-game player.
         */
        @Test
        fun `outstanding late sub beats mediocre full-game player under sqrt weighting`() {
            // Sub:     6 made / 7 att (85.7%), 1200s → DIS ≈ 6 × 0.857 × √(1200/5400) ≈ 2.28
            // Starter: 4 made / 8 att (50.0%), 5400s → DIS = 4 × 0.50  × 1.0           = 2.0
            val sub     = player("OutstandingSub", tackleAttempts = "7", tacklesMade = "6", seconds = "1200")
            val starter = player("MediocreStarter", tackleAttempts = "8", tacklesMade = "4", seconds = "5400")
            val result = XerifeSelector.select(listOf(sub, starter))!!
            assertThat(result.player.playerName).isEqualTo("OutstandingSub")
            // Verify actual score is close to expected
            assertThat(result.defensiveScore).isCloseTo(dis(6, 7, 1200), within(0.001))
        }

        @Test
        fun `dominant full-game player beats short sub despite sub having high rate`() {
            // Starter: 10 made / 13 att (77%), 5400s → DIS = 10 × 0.769 × 1.0  ≈ 7.69
            // Sub:      8 made /  9 att (89%), 1000s → DIS =  8 × 0.889 × √(1000/5400) ≈ 3.05
            val starter = player("DominantStarter", tackleAttempts = "13", tacklesMade = "10", seconds = "5400")
            val sub     = player("EfficientSub",    tackleAttempts = "9",  tacklesMade = "8",  seconds = "1000")
            assertThat(XerifeSelector.select(listOf(starter, sub))!!.player.playerName)
                .isEqualTo("DominantStarter")
        }

        @Test
        fun `sqrt is more generous to partial-game players than linear would be`() {
            // At 2700s (half game): sqrt(0.5) ≈ 0.707, linear = 0.5
            // A player who played half the game gets 71 % credit, not 50 %
            val sqrtWeight = sqrt(2700.0 / 5400.0)
            assertThat(sqrtWeight).isGreaterThan(0.70)
            assertThat(sqrtWeight).isLessThan(0.72)
        }

        @Test
        fun `micro-sub below MIN_SECONDS_PLAYED is excluded even with perfect stats`() {
            // 539s → disqualified; even 5/5 (100%) should return null if alone
            val players = listOf(player("MicroSub", tackleAttempts = "5", tacklesMade = "5", seconds = "539"))
            assertThat(XerifeSelector.select(players)).isNull()
        }
    }

    // ── Tiebreakers ───────────────────────────────────────────────────────────

    @Nested
    inner class Tiebreaker {

        @Test
        fun `equal DIS breaks tie by higher success rate`() {
            // A: 2 made / 4 att (50%), 5400s → DIS = 2 × 0.5 × 1.0 = 1.0
            // B: 1 made / 1 att (100%), 5400s → DIS = 1 × 1.0 × 1.0 = 1.0  (tied)
            // B wins via successRate tiebreaker
            val players = listOf(
                player("LowerRate",  tackleAttempts = "4", tacklesMade = "2"),
                player("HigherRate", tackleAttempts = "1", tacklesMade = "1"),
            )
            assertThat(XerifeSelector.select(players)!!.player.playerName).isEqualTo("HigherRate")
        }

        @Test
        fun `equal DIS and rate breaks tie by more tacklesMade`() {
            // Both 100% rate, full game:
            // A: 3 made / 3 att → DIS = 3.0
            // B: 2 made / 2 att → DIS = 2.0  (not a true tie, but shows direction)
            val players = listOf(
                player("More", tackleAttempts = "3", tacklesMade = "3"),
                player("Less", tackleAttempts = "2", tacklesMade = "2"),
            )
            assertThat(XerifeSelector.select(players)!!.player.playerName).isEqualTo("More")
        }
    }

    // ── Selection result fields ───────────────────────────────────────────────

    @Nested
    inner class SelectionFields {

        @Test
        fun `exposed fields are correct`() {
            val players = listOf(player("Xerife", tackleAttempts = "10", tacklesMade = "8", seconds = "5400"))
            val result = XerifeSelector.select(players)!!
            assertThat(result.tacklesMade).isEqualTo(8)
            assertThat(result.tackleAttempts).isEqualTo(10)
            assertThat(result.successRate).isEqualTo(80)
            assertThat(result.defensiveScore).isCloseTo(dis(8, 10, 5400), within(0.001))
        }

        @Test
        fun `successRate is integer percentage (floor)`() {
            // 7 / 9 = 77.7…% → stored as 77
            val players = listOf(player("Partial", tackleAttempts = "9", tacklesMade = "7"))
            val result = XerifeSelector.select(players)!!
            assertThat(result.successRate).isEqualTo(77)
        }
    }
}
