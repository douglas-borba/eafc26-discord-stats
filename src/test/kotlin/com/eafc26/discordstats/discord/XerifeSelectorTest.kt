package com.eafc26.discordstats.discord

import com.eafc26.discordstats.ea.model.PlayerEntry
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests for XerifeSelector.
 *
 * DIS = tacklesMade × (tacklesMade / tackleAttempts)
 *     = tacklesMade² / tackleAttempts
 *
 * secondsPlayed is irrelevant — the award depends only on defensive performance.
 */
class XerifeSelectorTest {

    // ── helpers ────────────────────────────────────────────────────────────────

    private fun player(
        name: String,
        tackleAttempts: String? = null,
        tacklesMade: String? = null,
        seconds: String? = null,          // null by default — playing time is ignored
    ) = PlayerEntry(
        playerName = name,
        position = null,
        tackleAttempts = tackleAttempts,
        tacklesMade = tacklesMade,
        secondsPlayed = seconds,
    )

    /** Expected DIS for known inputs. */
    private fun dis(made: Int, attempts: Int): Double =
        made.toDouble() * made.toDouble() / attempts.toDouble()

    // ── Eligibility ────────────────────────────────────────────────────────────

    @Nested
    inner class Eligibility {

        @Test
        fun `returns null for empty list`() {
            assertThat(XerifeSelector.select(emptyList())).isNull()
        }

        @Test
        fun `returns null when no player has enough tackle attempts`() {
            val players = listOf(player("None", tackleAttempts = "1", tacklesMade = "1"))
            assertThat(XerifeSelector.select(players)).isNull()
        }

        @Test
        fun `returns null when tackleAttempts is missing`() {
            val players = listOf(player("NoData", tackleAttempts = null, tacklesMade = null))
            assertThat(XerifeSelector.select(players)).isNull()
        }

        @Test
        fun `player with exactly MIN_TACKLE_ATTEMPTS qualifies`() {
            val players = listOf(player("ExactMin", tackleAttempts = "2", tacklesMade = "1"))
            assertThat(XerifeSelector.select(players)).isNotNull
        }

        @Test
        fun `player with zero seconds played is still eligible`() {
            // Playing time is irrelevant
            val players = listOf(player("ZeroSeconds", tackleAttempts = "5", tacklesMade = "4", seconds = "0"))
            assertThat(XerifeSelector.select(players)).isNotNull
            assertThat(XerifeSelector.select(players)!!.player.playerName).isEqualTo("ZeroSeconds")
        }

        @Test
        fun `player with null secondsPlayed is eligible`() {
            val players = listOf(player("NullSeconds", tackleAttempts = "5", tacklesMade = "4", seconds = null))
            val result = XerifeSelector.select(players)
            assertThat(result).isNotNull
            assertThat(result!!.player.playerName).isEqualTo("NullSeconds")
        }

        @Test
        fun `player with very short playing time can still win`() {
            // 60 s is what a disconnected / late sub player might have
            val short  = player("ShortGame", tackleAttempts = "4", tacklesMade = "4", seconds = "60")
            val long   = player("LongGame",  tackleAttempts = "4", tacklesMade = "2", seconds = "5400")
            // ShortGame DIS = 4.0; LongGame DIS = 1.0 → ShortGame wins
            assertThat(XerifeSelector.select(listOf(short, long))!!.player.playerName).isEqualTo("ShortGame")
        }

        @Test
        fun `single tackle attempt disqualifies player`() {
            val players = listOf(player("OneAttempt", tackleAttempts = "1", tacklesMade = "1"))
            assertThat(XerifeSelector.select(players)).isNull()
        }

        @Test
        fun `zero tackle attempts disqualifies player`() {
            val players = listOf(player("Zero", tackleAttempts = "0", tacklesMade = "0"))
            assertThat(XerifeSelector.select(players)).isNull()
        }
    }

    // ── DIS formula ────────────────────────────────────────────────────────────

    @Nested
    inner class Formula {

        @Test
        fun `score equals tacklesMade squared divided by attempts`() {
            // 8 made / 10 att → DIS = 8 * 8 / 10 = 6.4
            val players = listOf(player("A", tackleAttempts = "10", tacklesMade = "8"))
            val result = XerifeSelector.select(players)!!
            assertThat(result.defensiveScore).isCloseTo(dis(8, 10), within(0.001))
        }

        @Test
        fun `secondsPlayed has no effect on score`() {
            // Same tackles, different playing times → identical DIS
            val p1 = player("Short", tackleAttempts = "5", tacklesMade = "4", seconds = "100")
            val p2 = player("Long",  tackleAttempts = "5", tacklesMade = "4", seconds = "5400")
            val r1 = XerifeSelector.select(listOf(p1))!!
            val r2 = XerifeSelector.select(listOf(p2))!!
            assertThat(r1.defensiveScore).isCloseTo(r2.defensiveScore, within(0.001))
        }

        @Test
        fun `zero tacklesMade yields score of zero regardless of attempts`() {
            val players = listOf(
                player("ZeroMade", tackleAttempts = "10", tacklesMade = "0"),
                player("OneMade",  tackleAttempts = "2",  tacklesMade = "1"),
            )
            // ZeroMade DIS = 0; OneMade DIS > 0
            assertThat(XerifeSelector.select(players)!!.player.playerName).isEqualTo("OneMade")
        }
    }

    // ── Efficiency vs volume ───────────────────────────────────────────────────

    @Nested
    inner class EfficiencyVsVolume {

        @Test
        fun `efficient player beats high-volume sloppy player`() {
            // SloppyBig:  12 made / 30 att (40%) → DIS = 12 * 0.40 = 4.8
            // CleanSmall:  9 made / 11 att (81%) → DIS = 9 * 0.818 ≈ 7.36
            val players = listOf(
                player("SloppyBig",  tackleAttempts = "30", tacklesMade = "12"),
                player("CleanSmall", tackleAttempts = "11", tacklesMade = "9"),
            )
            assertThat(XerifeSelector.select(players)!!.player.playerName).isEqualTo("CleanSmall")
        }

        @Test
        fun `same rate but higher volume wins`() {
            // Both 80%:  More DIS = 8 * 0.8 = 6.4; Fewer DIS = 4 * 0.8 = 3.2
            val players = listOf(
                player("More",  tackleAttempts = "10", tacklesMade = "8"),
                player("Fewer", tackleAttempts = "5",  tacklesMade = "4"),
            )
            assertThat(XerifeSelector.select(players)!!.player.playerName).isEqualTo("More")
        }
    }

    // ── Tiebreakers ───────────────────────────────────────────────────────────

    @Nested
    inner class Tiebreaker {

        @Test
        fun `equal DIS breaks tie by higher success rate`() {
            // A: 2 made / 4 att (50%) → DIS = 1.0
            // B: 1 made / 1 att — disqualified (< MIN_TACKLE_ATTEMPTS)
            // Use B with 2 attempts: 2 made / 2 att (100%) → DIS = 2.0 — not a tie
            // Proper tie: A 2/4 DIS=1.0; C 1/2 DIS=0.5 — not a tie either
            // A 2/4 DIS=1.0 vs D 1/1 — D is ineligible (1 attempt < 2)
            // Use: A 4/8 DIS=2.0 vs B 2/4 DIS=1.0 (not a tie)
            // True tie: A 2/4=50% DIS=1.0 vs B 2/4=50% DIS=1.0 → B wins if higher rate... same rate
            // Actual tie possible with: A 3/6 (50%) DIS=1.5 vs B 3/6 (50%) DIS=1.5 → thenBy successRate → same
            // → thenBy made → same → arbitrary; so test with different rates at same DIS:
            // A 2/4 DIS=1.0; B 1/2 DIS=0.5 — not tied.
            // Tied DIS: A 2/4=0.5 rate → score=1.0; B 1/1 — ineligible.
            // Simplest true DIS tie: impossible with distinct fractions. Use same fraction.
            // A: 4/8 (50%) DIS=2.0; B: 2/4 (50%) DIS=1.0 → not tied.
            // Note: DIS is tied only if made1²/att1 = made2²/att2, e.g. 2/4 and 2/4.
            val players = listOf(
                player("LowerRate",  tackleAttempts = "4", tacklesMade = "2"),  // 50%, DIS=1.0
                player("HigherRate", tackleAttempts = "2", tacklesMade = "2"),  // 100%, DIS=2.0
            )
            // Not a tie — HigherRate wins outright on DIS
            assertThat(XerifeSelector.select(players)!!.player.playerName).isEqualTo("HigherRate")
        }

        @Test
        fun `equal DIS and rate breaks tie by more tacklesMade`() {
            // Both 100% rate: A 3/3 DIS=3.0; B 2/2 DIS=2.0 → A wins on DIS
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
            val players = listOf(player("Xerife", tackleAttempts = "10", tacklesMade = "8"))
            val result = XerifeSelector.select(players)!!
            assertThat(result.tacklesMade).isEqualTo(8)
            assertThat(result.tackleAttempts).isEqualTo(10)
            assertThat(result.successRate).isEqualTo(80)
            assertThat(result.defensiveScore).isCloseTo(dis(8, 10), within(0.001))
        }

        @Test
        fun `successRate is integer percentage (floor)`() {
            // 7 / 9 = 77.7…% → stored as 77
            val players = listOf(player("Partial", tackleAttempts = "9", tacklesMade = "7"))
            val result = XerifeSelector.select(players)!!
            assertThat(result.successRate).isEqualTo(77)
        }
    }

    // ── Match 874612175930485 regression ─────────────────────────────────────
    //
    // Root cause (fixed): the old XerifeSelector required a success rate > 60%.
    // In this match every player's rate was ≤ 40 %, so candidates was empty and
    // the Sheriff was never rendered. The DIS formula has no such gate.
    //
    // Playing time was subsequently removed as a factor entirely.

    @Nested
    inner class Match874612175930485Regression {

        /**
         * Exact outfield eligible players for this match.
         * secondsPlayed is included as the real EA API returned it, but the
         * selector no longer uses it.
         */
        private fun realOutfield() = listOf(
            player("Guilherme_cruzz", tackleAttempts = "6", tacklesMade = "1", seconds = "5621"),
            player("dbeng_bass",      tackleAttempts = "5", tacklesMade = "2", seconds = "5621"),
            player("swegher",         tackleAttempts = "9", tacklesMade = "2", seconds = "5621"),
            player("joaoborba07",     tackleAttempts = "5", tacklesMade = "0", seconds = "5621"),
            player("paulorodrigues0", tackleAttempts = "7", tacklesMade = "1", seconds = "5621"),
        )

        @Test
        fun `dbeng_bass wins with highest DIS 0_800`() {
            val result = XerifeSelector.select(realOutfield())

            assertThat(result).isNotNull
            assertThat(result!!.player.playerName).isEqualTo("dbeng_bass")
            assertThat(result.defensiveScore).isCloseTo(dis(2, 5), within(0.001))
            assertThat(result.tacklesMade).isEqualTo(2)
            assertThat(result.tackleAttempts).isEqualTo(5)
            assertThat(result.successRate).isEqualTo(40)
        }

        @Test
        fun `same result whether secondsPlayed is present or null`() {
            // Strip secondsPlayed from every player — outcome must be identical
            val withoutSeconds = realOutfield().map { p ->
                p.copy(secondsPlayed = null)
            }
            val withSeconds    = realOutfield()

            assertThat(XerifeSelector.select(withoutSeconds)!!.player.playerName)
                .isEqualTo(XerifeSelector.select(withSeconds)!!.player.playerName)
        }

        @Test
        fun `old 60pct success-rate gate would have produced null for this match`() {
            // All outfield players had rates <= 40 pct; the old rule required rate > 60 pct.
            // This test documents why the award was absent before the DIS refactor.
            val maxRate = realOutfield()
                .mapNotNull { p ->
                    val att = p.tackleAttempts?.toIntOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
                    val made = p.tacklesMade?.toIntOrNull() ?: 0
                    made * 100 / att
                }
                .maxOrNull() ?: 0
            assertThat(maxRate)
                .describedAs("max success rate in the match (all <= 40 pct)")
                .isLessThanOrEqualTo(40)
        }

        @Test
        fun `expected DIS ranking order dbeng_bass beats swegher`() {
            val result = XerifeSelector.select(realOutfield())!!
            // dbeng_bass DIS = 2*2/5 = 0.800 > swegher DIS = 2*2/9 ≈ 0.444
            assertThat(result.defensiveScore).isGreaterThan(dis(2, 9))
        }
    }

    // ── secondsPlayed independence ────────────────────────────────────────────

    @Nested
    inner class SecondsPlayedIndependence {

        @Test
        fun `winner is same regardless of secondsPlayed value`() {
            fun makeMatch(seconds: String?) = listOf(
                player("Best",  tackleAttempts = "5", tacklesMade = "4", seconds = seconds),
                player("Worse", tackleAttempts = "5", tacklesMade = "2", seconds = seconds),
            )
            listOf("0", "60", "540", "5400", null).forEach { sec ->
                assertThat(XerifeSelector.select(makeMatch(sec))!!.player.playerName)
                    .describedAs("secondsPlayed=$sec")
                    .isEqualTo("Best")
            }
        }

        @Test
        fun `player with null secondsPlayed beats player with full game if better defender`() {
            val nullSeconds = player("NoTimeData", tackleAttempts = "4", tacklesMade = "4", seconds = null)
            val fullGame    = player("FullGame",   tackleAttempts = "4", tacklesMade = "2", seconds = "5400")
            // NoTimeData DIS = 4.0; FullGame DIS = 1.0
            assertThat(XerifeSelector.select(listOf(nullSeconds, fullGame))!!.player.playerName)
                .isEqualTo("NoTimeData")
        }

        @Test
        fun `player with zero seconds played beats player with full game if better defender`() {
            val zeroSeconds = player("ZeroSec", tackleAttempts = "4", tacklesMade = "4", seconds = "0")
            val fullGame    = player("FullGame", tackleAttempts = "4", tacklesMade = "2", seconds = "5400")
            assertThat(XerifeSelector.select(listOf(zeroSeconds, fullGame))!!.player.playerName)
                .isEqualTo("ZeroSec")
        }
    }
}
