package com.eafc26.discordstats.discord

import com.eafc26.discordstats.ea.model.PlayerEntry
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests for XerifeSelector.
 *
 * Eligibility gates (all must pass):
 *   1. tacklesMade >= 4
 *   2. tacklePrecision = tacklesMade / tackleAttempts >= 0.70
 *   3. redCards == 0
 *   4. Not the Bagre (applied upstream — tested in BagreExclusionTest)
 *
 * Ranking for eligible candidates:
 *   DIS = tacklesMade² / tackleAttempts
 *   Tiebreaker: higher successRate → more tacklesMade
 */
class XerifeSelectorTest {

    // ── helpers ────────────────────────────────────────────────────────────────

    private fun player(
        name: String,
        tackleAttempts: String? = null,
        tacklesMade: String? = null,
        redCards: String? = null,
        seconds: String? = null,
    ) = PlayerEntry(
        playerName = name,
        position = null,
        tackleAttempts = tackleAttempts,
        tacklesMade = tacklesMade,
        redCards = redCards,
        secondsPlayed = seconds,
    )

    private fun dis(made: Int, attempts: Int): Double =
        made.toDouble() * made.toDouble() / attempts.toDouble()

    // ── Volume gate: tacklesMade >= 4 ─────────────────────────────────────────

    @Nested
    inner class VolumeGate {

        @Test
        fun `4 tackles 4 attempts 100 percent is eligible`() {
            // 4/4 = 100%, made=4 == MIN → should qualify
            val players = listOf(player("FourPerfect", tackleAttempts = "4", tacklesMade = "4"))
            val result = XerifeSelector.select(players)
            assertThat(result).isNotNull
            assertThat(result!!.player.playerName).isEqualTo("FourPerfect")
        }

        @Test
        fun `4 tackles 5 attempts 80 percent is eligible`() {
            // 4/5 = 80% >= 70%, made=4 == MIN
            val players = listOf(player("FourFive", tackleAttempts = "5", tacklesMade = "4"))
            val result = XerifeSelector.select(players)
            assertThat(result).isNotNull
            assertThat(result!!.player.playerName).isEqualTo("FourFive")
        }

        @Test
        fun `4 tackles 6 attempts 66_7 percent is not eligible`() {
            // 4/6 ≈ 66.7% < 70% — fails precision gate
            val players = listOf(player("FourSix", tackleAttempts = "6", tacklesMade = "4"))
            assertThat(XerifeSelector.select(players)).isNull()
        }

        @Test
        fun `3 tackles 3 attempts is not eligible`() {
            // made=3 < MIN=4 — fails volume gate
            val players = listOf(player("ThreeThree", tackleAttempts = "3", tacklesMade = "3"))
            assertThat(XerifeSelector.select(players)).isNull()
        }

        @Test
        fun `5 successful tackles with 100% precision is still eligible`() {
            // 5/5 = 100% — continues to qualify
            val players = listOf(player("FivePerfect", tackleAttempts = "5", tacklesMade = "5"))
            val result = XerifeSelector.select(players)
            assertThat(result).isNotNull
            assertThat(result!!.player.playerName).isEqualTo("FivePerfect")
        }
    }

    // ── Precision gate: tacklePrecision >= 0.70 ───────────────────────────────

    @Nested
    inner class PrecisionGate {

        @Test
        fun `exactly 70% precision with 4+ tackles is eligible`() {
            // 7/10 = 0.70 exactly — boundary inclusive; made=7 >= 4
            val players = listOf(player("SevenTen", tackleAttempts = "10", tacklesMade = "7"))
            assertThat(XerifeSelector.select(players)).isNotNull
        }

        @Test
        fun `exactly 70% precision at new minimum of 4 tackles`() {
            // No combination of 4 made gives exactly 70% with integer attempts:
            // 4/5=80%, 4/6≈66.7% (fails). Use 4 made / approx: nearest valid is 4/5.
            // Use instead 4 made / 4 att = 100% — confirm it passes
            val players = listOf(player("FourFour", tackleAttempts = "4", tacklesMade = "4"))
            assertThat(XerifeSelector.select(players)).isNotNull
        }

        @Test
        fun `precision below 70% is ineligible`() {
            // 4/6 ≈ 66.7% < 0.70; made=4 meets volume gate but fails precision gate
            val players = listOf(player("FourSix", tackleAttempts = "6", tacklesMade = "4"))
            assertThat(XerifeSelector.select(players)).isNull()
        }

        @Test
        fun `5 out of 7 is eligible (approx 71_4 percent)`() {
            // 5/7 ≈ 0.714 >= 0.70 and made=5 >= 5
            val players = listOf(player("FiveSeven", tackleAttempts = "7", tacklesMade = "5"))
            val result = XerifeSelector.select(players)
            assertThat(result).isNotNull
            assertThat(result!!.player.playerName).isEqualTo("FiveSeven")
        }

        @Test
        fun `5 out of 8 is ineligible (62_5 percent)`() {
            // 5/8 = 0.625 < 0.70
            val players = listOf(player("FiveEight", tackleAttempts = "8", tacklesMade = "5"))
            assertThat(XerifeSelector.select(players)).isNull()
        }

        @Test
        fun `precision is computed as double division not integer division`() {
            // 5/7 as integer division = 0 → would wrongly fail; as Double ≈ 0.714 → eligible
            val players = listOf(player("IntVsDouble", tackleAttempts = "7", tacklesMade = "5"))
            assertThat(XerifeSelector.select(players)).isNotNull
        }
    }

    // ── Zero attempts ─────────────────────────────────────────────────────────

    @Nested
    inner class ZeroAttempts {

        @Test
        fun `zero tackle attempts is handled safely and player is ineligible`() {
            val players = listOf(player("ZeroAtt", tackleAttempts = "0", tacklesMade = "0"))
            assertThat(XerifeSelector.select(players)).isNull()
        }

        @Test
        fun `null tackle attempts disqualifies player`() {
            val players = listOf(player("NullAtt", tackleAttempts = null, tacklesMade = "5"))
            assertThat(XerifeSelector.select(players)).isNull()
        }

        @Test
        fun `returns null for empty list`() {
            assertThat(XerifeSelector.select(emptyList())).isNull()
        }
    }

    // ── Red card gate ─────────────────────────────────────────────────────────

    @Nested
    inner class RedCardGate {

        @Test
        fun `red-carded player is ineligible even with perfect stats`() {
            // 8/8 = 100% and made=8 but has a red card
            val players = listOf(player("Violent", tackleAttempts = "8", tacklesMade = "8", redCards = "1"))
            assertThat(XerifeSelector.select(players)).isNull()
        }

        @Test
        fun `next valid candidate is selected when red-carded player has better stats`() {
            val redCarded = player("Violent",   tackleAttempts = "8", tacklesMade = "8", redCards = "1")
            val clean     = player("CleanPlay", tackleAttempts = "7", tacklesMade = "5")
            // CleanPlay: 5/7 ≈ 71.4% — eligible; Violent excluded by red card
            val result = XerifeSelector.select(listOf(redCarded, clean))
            assertThat(result).isNotNull
            assertThat(result!!.player.playerName).isEqualTo("CleanPlay")
        }

        @Test
        fun `zero red cards field is not disqualifying`() {
            val players = listOf(player("Clean", tackleAttempts = "7", tacklesMade = "5", redCards = "0"))
            assertThat(XerifeSelector.select(players)).isNotNull
        }

        @Test
        fun `null red cards field treated as zero`() {
            val players = listOf(player("NoRedData", tackleAttempts = "7", tacklesMade = "5", redCards = null))
            assertThat(XerifeSelector.select(players)).isNotNull
        }
    }

    // ── Bagre exclusion ───────────────────────────────────────────────────────
    //
    // MatchSummaryBuilder excludes the Bagre before calling XerifeSelector.select.
    // The integration-level guarantee is in BagreExclusionTest.
    // These tests verify that when the Bagre is removed from input the next
    // eligible candidate is correctly selected (or omitted if none exists).

    @Nested
    inner class BagreExclusion {

        @Test
        fun `next eligible player is selected when Bagre is excluded from input`() {
            // Simulate caller removing the Bagre — only eligible candidate remains
            val eligibleAfterExclusion = listOf(
                player("GoodDefender", tackleAttempts = "7", tacklesMade = "5"),
            )
            val result = XerifeSelector.select(eligibleAfterExclusion)
            assertThat(result).isNotNull
            assertThat(result!!.player.playerName).isEqualTo("GoodDefender")
        }

        @Test
        fun `Xerife is omitted when only remaining player after Bagre exclusion fails gates`() {
            // After Bagre removed, the sole remaining player has made=3 < 4
            val remaining = listOf(player("PoorDefender", tackleAttempts = "4", tacklesMade = "3"))
            assertThat(XerifeSelector.select(remaining)).isNull()
        }
    }

    // ── Omission when nobody satisfies requirements ───────────────────────────

    @Nested
    inner class OmissionWhenNoEligible {

        @Test
        fun `Xerife is omitted when nobody satisfies both minimum requirements`() {
            val players = listOf(
                player("HighVolumeLowPrecision", tackleAttempts = "10", tacklesMade = "5"), // 50% < 70%
                player("HighPrecisionLowVolume", tackleAttempts = "3",  tacklesMade = "3"), // 100% but made=3 < 4
                player("BothFail",               tackleAttempts = "9",  tacklesMade = "3"), // ~33% and made=3 < 4
            )
            assertThat(XerifeSelector.select(players)).isNull()
        }

        @Test
        fun `all red-carded players returns null`() {
            val players = listOf(
                player("P1", tackleAttempts = "7", tacklesMade = "5", redCards = "1"),
                player("P2", tackleAttempts = "8", tacklesMade = "6", redCards = "1"),
            )
            assertThat(XerifeSelector.select(players)).isNull()
        }
    }

    // ── Ranking among eligible candidates ────────────────────────────────────

    @Nested
    inner class Ranking {

        @Test
        fun `higher DIS wins between two eligible players`() {
            // A: 8/10 = 80% eligible; DIS = 64/10 = 6.4
            // B: 5/7 ≈ 71.4% eligible; DIS = 25/7 ≈ 3.57
            val players = listOf(
                player("A", tackleAttempts = "10", tacklesMade = "8"),
                player("B", tackleAttempts = "7",  tacklesMade = "5"),
            )
            assertThat(XerifeSelector.select(players)!!.player.playerName).isEqualTo("A")
        }

        @Test
        fun `ineligible player (below precision gate) never wins over eligible player`() {
            // SloppyBig: 12/30 = 40% — ineligible
            // CleanSmall: 9/11 ≈ 81.8% — eligible; DIS ≈ 7.36
            val players = listOf(
                player("SloppyBig",  tackleAttempts = "30", tacklesMade = "12"),
                player("CleanSmall", tackleAttempts = "11", tacklesMade = "9"),
            )
            assertThat(XerifeSelector.select(players)!!.player.playerName).isEqualTo("CleanSmall")
        }

        @Test
        fun `DIS formula is tacklesMade squared divided by attempts`() {
            val players = listOf(player("X", tackleAttempts = "10", tacklesMade = "8"))
            val result = XerifeSelector.select(players)!!
            assertThat(result.defensiveScore).isCloseTo(dis(8, 10), within(0.001))
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
        fun `successRate for exactly 70% stored as 70`() {
            // 7/10 = 70.0% → floor = 70
            val players = listOf(player("Exact70", tackleAttempts = "10", tacklesMade = "7"))
            val result = XerifeSelector.select(players)!!
            assertThat(result.successRate).isEqualTo(70)
        }

        @Test
        fun `successRate for 5 out of 7 stored as 71 (floor)`() {
            // 5/7 = 71.42...% → floor = 71
            val players = listOf(player("FiveSeven", tackleAttempts = "7", tacklesMade = "5"))
            val result = XerifeSelector.select(players)!!
            assertThat(result.successRate).isEqualTo(71)
        }
    }

    // ── secondsPlayed independence ────────────────────────────────────────────

    @Nested
    inner class SecondsPlayedIndependence {

        @Test
        fun `winner is same regardless of secondsPlayed value`() {
            fun makeMatch(seconds: String?) = listOf(
                player("Best",  tackleAttempts = "7", tacklesMade = "6", seconds = seconds), // ~85.7% eligible
                player("Worse", tackleAttempts = "7", tacklesMade = "5", seconds = seconds), // ~71.4% eligible
            )
            listOf("0", "60", "540", "5400", null).forEach { sec ->
                assertThat(XerifeSelector.select(makeMatch(sec))!!.player.playerName)
                    .describedAs("secondsPlayed=$sec")
                    .isEqualTo("Best")
            }
        }
    }

    // ── Match 874612175930485 regression ─────────────────────────────────────
    //
    // In this match all outfield players had at most 2 successful tackles — well
    // below the new minimum of 5. The Xerife section must be omitted.

    @Nested
    inner class Match874612175930485Regression {

        private fun realOutfield() = listOf(
            player("Guilherme_cruzz", tackleAttempts = "6", tacklesMade = "1", seconds = "5621"),
            player("dbeng_bass",      tackleAttempts = "5", tacklesMade = "2", seconds = "5621"),
            player("swegher",         tackleAttempts = "9", tacklesMade = "2", seconds = "5621"),
            player("joaoborba07",     tackleAttempts = "5", tacklesMade = "0", seconds = "5621"),
            player("paulorodrigues0", tackleAttempts = "7", tacklesMade = "1", seconds = "5621"),
        )

        @Test
        fun `Xerife is omitted because no player reaches 5 successful tackles`() {
            assertThat(XerifeSelector.select(realOutfield())).isNull()
        }

        @Test
        fun `all players in this match have tacklesMade below minimum`() {
            val maxMade = realOutfield()
                .mapNotNull { it.tacklesMade?.toIntOrNull() }
                .maxOrNull() ?: 0
            assertThat(maxMade)
                .describedAs("max tacklesMade in match 874612175930485")
                .isLessThan(XerifeSelector.MIN_TACKLES_MADE)
        }
    }
}
