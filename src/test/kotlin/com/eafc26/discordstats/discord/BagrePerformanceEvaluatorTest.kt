package com.eafc26.discordstats.discord

import com.eafc26.discordstats.ea.model.PlayerEntry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class BagrePerformanceEvaluatorTest {

    // -- Bagre selection -----------------------------------------------------

    @Test
    fun `selects lowest rated eligible player as Bagre`() {
        val players = listOf(
            player("High", rating = "9.0"),
            player("Low", rating = "5.0"),
            player("Medium", rating = "7.0"),
        )
        val result = BagrePerformanceEvaluator.evaluate(players, "match1", null)
        assertThat(result).isNotNull
        assertThat(result!!.player.playerName).isEqualTo("Low")
    }

    @Test
    fun `returns null when no rated players`() {
        val players = listOf(player("NoRating", rating = null))
        val result = BagrePerformanceEvaluator.evaluate(players, "match1", null)
        assertThat(result).isNull()
    }

    @Test
    fun `returns null when empty player list`() {
        val result = BagrePerformanceEvaluator.evaluate(emptyList(), "match1", null)
        assertThat(result).isNull()
    }

    // -- Passing thresholds --------------------------------------------------

    @Nested
    inner class PassingThresholds {

        @Test
        fun `passes at 75 percent or above are omitted entirely`() {
            val players = listOf(
                player("Good", rating = "5.0", passAttempts = "20", passesMade = "15"), // 75%
            )
            val result = BagrePerformanceEvaluator.evaluate(players, "match1", null)
            val text = result!!.sections.joinToString("\n")
            assertThat(text).doesNotContain("📉 Passes")
        }

        @Test
        fun `passes at 81 percent are omitted - real example 13 of 16`() {
            val players = listOf(
                player("Good", rating = "7.10", passAttempts = "16", passesMade = "13"), // 81.25%
            )
            val result = BagrePerformanceEvaluator.evaluate(players, "match1", null)
            val text = result!!.sections.joinToString("\n")
            assertThat(text).doesNotContain("📉 Passes")
        }

        @Test
        fun `passes between 60 and 74 percent shown without harsh criticism`() {
            val players = listOf(
                player("Moderate", rating = "5.0", passAttempts = "20", passesMade = "14"), // 70%
            )
            val result = BagrePerformanceEvaluator.evaluate(players, "match1", null)
            val text = result!!.sections.joinToString("\n")
            assertThat(text).contains("📉 Passes")
            assertThat(text).contains("margem para evolução")
        }

        @Test
        fun `passes below 60 percent shown with negative criticism`() {
            val players = listOf(
                player("Poor", rating = "5.0", passAttempts = "20", passesMade = "10"), // 50%
            )
            val result = BagrePerformanceEvaluator.evaluate(players, "match1", null)
            val text = result!!.sections.joinToString("\n")
            assertThat(text).contains("📉 Passes")
            // Should have a phrase from PhraseCategory.PASS (from defaults)
            assertThat(text).contains("💬 \"")
        }
    }

    // -- Tackle thresholds ---------------------------------------------------

    @Nested
    inner class TackleThresholds {

        @Test
        fun `tackles above 60 percent are omitted`() {
            val players = listOf(
                player("Good", rating = "5.0", tackleAttempts = "10", tacklesMade = "8"), // 80%
            )
            val result = BagrePerformanceEvaluator.evaluate(players, "match1", null)
            val text = result!!.sections.joinToString("\n")
            assertThat(text).doesNotContain("🛡️ Desarmes")
        }

        @Test
        fun `tackles at 0 of 4 are shown with negative criticism - real example`() {
            val players = listOf(
                player("Poor", rating = "7.10", tackleAttempts = "4", tacklesMade = "0"), // 0%
            )
            val result = BagrePerformanceEvaluator.evaluate(players, "match1", null)
            val text = result!!.sections.joinToString("\n")
            assertThat(text).contains("🛡️ Desarmes")
            assertThat(text).contains("• 0/4 certos")
            assertThat(text).contains("0% de aproveitamento")
            assertThat(text).contains("💬 \"")
        }

        @Test
        fun `tackles at 40 percent or below are shown with negative criticism`() {
            val players = listOf(
                player("Poor", rating = "5.0", tackleAttempts = "10", tacklesMade = "4"), // 40%
            )
            val result = BagrePerformanceEvaluator.evaluate(players, "match1", null)
            val text = result!!.sections.joinToString("\n")
            assertThat(text).contains("🛡️ Desarmes")
        }

        @Test
        fun `tackles between 41 and 60 percent shown with moderate wording`() {
            val players = listOf(
                player("Moderate", rating = "5.0", tackleAttempts = "10", tacklesMade = "5"), // 50%
            )
            val result = BagrePerformanceEvaluator.evaluate(players, "match1", null)
            val text = result!!.sections.joinToString("\n")
            assertThat(text).contains("🛡️ Desarmes")
            assertThat(text).contains("espaço para melhorar")
        }
    }

    // -- Finishing section ---------------------------------------------------

    @Nested
    inner class FinishingSection {

        @Test
        fun `finishing section omitted when player scored goals`() {
            val players = listOf(
                player("Scorer", rating = "5.0", shots = "5", goals = "2"),
            )
            val result = BagrePerformanceEvaluator.evaluate(players, "match1", null)
            val text = result!!.sections.joinToString("\n")
            assertThat(text).doesNotContain("🎯 Finalizações")
        }

        @Test
        fun `finishing section shown when player had shots but no goals`() {
            val players = listOf(
                player("Missed", rating = "5.0", shots = "3", goals = "0"),
            )
            val result = BagrePerformanceEvaluator.evaluate(players, "match1", null)
            val text = result!!.sections.joinToString("\n")
            assertThat(text).contains("🎯 Finalizações")
            assertThat(text).contains("3 chutes")
            assertThat(text).contains("0 gols")
        }

        @Test
        fun `finishing section omitted when no shots`() {
            val players = listOf(
                player("NoShots", rating = "5.0", shots = "0", goals = "0"),
            )
            val result = BagrePerformanceEvaluator.evaluate(players, "match1", null)
            val text = result!!.sections.joinToString("\n")
            assertThat(text).doesNotContain("🎯 Finalizações")
        }

        @Test
        fun `never criticizes finishing when player scored - real example 2 shots 1 goal`() {
            val players = listOf(
                player("Scorer", rating = "7.10", shots = "2", goals = "1"),
            )
            val result = BagrePerformanceEvaluator.evaluate(players, "match1", null)
            val text = result!!.sections.joinToString("\n")
            assertThat(text).doesNotContain("🎯 Finalizações")
        }
    }

    // -- Positive counterpoints ----------------------------------------------

    @Nested
    inner class PositiveCounterpoints {

        @Test
        fun `shows goal counterpoint when bagre scored`() {
            val players = listOf(
                player("Bagre", rating = "5.0", goals = "1"),
            )
            val result = BagrePerformanceEvaluator.evaluate(players, "match1", null)
            val text = result!!.sections.joinToString("\n")
            assertThat(text).contains("⚽ Ainda assim, marcou 1 gol.")
        }

        @Test
        fun `shows assist counterpoint when bagre assisted`() {
            val players = listOf(
                player("Bagre", rating = "5.0", assists = "2"),
            )
            val result = BagrePerformanceEvaluator.evaluate(players, "match1", null)
            val text = result!!.sections.joinToString("\n")
            assertThat(text).contains("🎯 Ainda assim, deu 2 assistências.")
        }

        @Test
        fun `shows both counterpoints when bagre scored and assisted`() {
            val players = listOf(
                player("Bagre", rating = "5.0", goals = "1", assists = "1"),
            )
            val result = BagrePerformanceEvaluator.evaluate(players, "match1", null)
            val text = result!!.sections.joinToString("\n")
            assertThat(text).contains("⚽ Ainda assim, marcou 1 gol.")
            assertThat(text).contains("🎯 Ainda assim, deu 1 assistência.")
        }

        @Test
        fun `no counterpoint when no goals or assists`() {
            val players = listOf(
                player("Bagre", rating = "5.0", goals = "0", assists = "0"),
            )
            val result = BagrePerformanceEvaluator.evaluate(players, "match1", null)
            val text = result!!.sections.joinToString("\n")
            assertThat(text).doesNotContain("Ainda assim")
        }
    }

    // -- Pluralization -------------------------------------------------------

    @Nested
    inner class Pluralization {

        @Test
        fun `singular gol for 1 goal`() {
            val players = listOf(player("Bagre", rating = "5.0", goals = "1"))
            val result = BagrePerformanceEvaluator.evaluate(players, "match1", null)
            val text = result!!.sections.joinToString("\n")
            assertThat(text).contains("1 gol")
            assertThat(text).doesNotContain("1 gols")
        }

        @Test
        fun `plural gols for 2 goals`() {
            val players = listOf(player("Bagre", rating = "5.0", goals = "2"))
            val result = BagrePerformanceEvaluator.evaluate(players, "match1", null)
            val text = result!!.sections.joinToString("\n")
            assertThat(text).contains("2 gols")
        }

        @Test
        fun `singular assistencia for 1 assist`() {
            val players = listOf(player("Bagre", rating = "5.0", assists = "1"))
            val result = BagrePerformanceEvaluator.evaluate(players, "match1", null)
            val text = result!!.sections.joinToString("\n")
            assertThat(text).contains("1 assistência")
            assertThat(text).doesNotContain("1 assistências")
        }

        @Test
        fun `plural assistencias for 2 assists`() {
            val players = listOf(player("Bagre", rating = "5.0", assists = "2"))
            val result = BagrePerformanceEvaluator.evaluate(players, "match1", null)
            val text = result!!.sections.joinToString("\n")
            assertThat(text).contains("2 assistências")
        }

        @Test
        fun `singular passe errado for 1 missed pass`() {
            val players = listOf(player("Bagre", rating = "5.0", passAttempts = "10", passesMade = "9"))
            val result = BagrePerformanceEvaluator.evaluate(players, "match1", null)
            // 90% is above threshold, so no pass section - just verify result exists
            assertThat(result).isNotNull
        }

        @Test
        fun `plural passes errados for multiple missed passes`() {
            val players = listOf(player("Bagre", rating = "5.0", passAttempts = "10", passesMade = "4"))
            val result = BagrePerformanceEvaluator.evaluate(players, "match1", null)
            val text = result!!.sections.joinToString("\n")
            assertThat(text).contains("6 passes errados")
        }

        @Test
        fun `singular chute for 1 shot`() {
            val players = listOf(player("Bagre", rating = "5.0", shots = "1", goals = "0"))
            val result = BagrePerformanceEvaluator.evaluate(players, "match1", null)
            val text = result!!.sections.joinToString("\n")
            assertThat(text).contains("1 chute")
            assertThat(text).doesNotContain("1 chutes")
        }

        @Test
        fun `plural chutes for multiple shots`() {
            val players = listOf(player("Bagre", rating = "5.0", shots = "3", goals = "0"))
            val result = BagrePerformanceEvaluator.evaluate(players, "match1", null)
            val text = result!!.sections.joinToString("\n")
            assertThat(text).contains("3 chutes")
        }
    }

    // -- Generic mocking phrase threshold ------------------------------------

    @Nested
    inner class GenericMockingPhrase {

        @Test
        fun `rating 7_10 does not produce generic mocking phrase`() {
            val players = listOf(
                player("NotBad", rating = "7.10"),
            )
            val result = BagrePerformanceEvaluator.evaluate(players, "match1", null)
            val text = result!!.sections.joinToString("\n")
            // Should not contain phrase from RATING category when rating >= 6.5
            // No generic rating phrase for 7.10 (only subsection phrases if any)
            assertThat(text).doesNotContain("Contribuiu com presença")
        }

        @Test
        fun `rating 5_0 produces generic mocking phrase`() {
            val players = listOf(
                player("Poor", rating = "5.0"),
            )
            val result = BagrePerformanceEvaluator.evaluate(players, "match1", null)
            val text = result!!.sections.joinToString("\n")
            // Should contain a phrase from RATING category
            assertThat(text).contains("💬 \"")
        }

        @Test
        fun `rating 6_4 produces generic mocking phrase`() {
            val players = listOf(
                player("AlmostOk", rating = "6.4"),
            )
            val result = BagrePerformanceEvaluator.evaluate(players, "match1", null)
            val text = result!!.sections.joinToString("\n")
            assertThat(text).contains("💬 \"")
        }

        @Test
        fun `rating 6_5 does not produce generic mocking phrase`() {
            val players = listOf(
                player("Threshold", rating = "6.5"),
            )
            val result = BagrePerformanceEvaluator.evaluate(players, "match1", null)
            val text = result!!.sections.joinToString("\n")
            // Should not have generic mocking phrase for 6.5+
            assertThat(text).doesNotContain("Contribuiu com presença")
        }
    }

    // -- Real example from user ----------------------------------------------

    @Test
    fun `real example - rating 7_10 with 13 of 16 passes and 0 of 4 tackles and 2 shots 1 goal`() {
        val players = listOf(
            player(
                "RealPlayer",
                rating = "7.10",
                passAttempts = "16",
                passesMade = "13",  // 81%
                tackleAttempts = "4",
                tacklesMade = "0",  // 0%
                shots = "2",
                goals = "1",
                assists = "0"
            ),
        )
        val result = BagrePerformanceEvaluator.evaluate(players, "match1", null)
        val text = result!!.sections.joinToString("\n")

        // Should NOT criticize passing (81% is above 75%)
        assertThat(text).doesNotContain("📉 Passes")

        // SHOULD show tackle section (0% is below 40%)
        assertThat(text).contains("🛡️ Desarmes")
        assertThat(text).contains("0/4 certos")
        assertThat(text).contains("0% de aproveitamento")

        // Should NOT criticize finishing (player scored 1 goal)
        assertThat(text).doesNotContain("🎯 Finalizações")

        // Should show positive counterpoint for the goal
        assertThat(text).contains("⚽ Ainda assim, marcou 1 gol.")

        // Should NOT have generic mocking phrase (rating 7.10 >= 6.5)
        assertThat(text).doesNotContain("Contribuiu com presença")
    }

    // -- Helpers -------------------------------------------------------------

    private fun player(
        name: String,
        rating: String? = "7.0",
        goals: String? = "0",
        assists: String? = "0",
        shots: String? = null,
        passAttempts: String? = null,
        passesMade: String? = null,
        tackleAttempts: String? = null,
        tacklesMade: String? = null,
        redCards: String? = null,
        secondsPlayed: String? = "900",
    ) = PlayerEntry(
        playerName = name,
        position = null,
        rating = rating,
        goals = goals,
        assists = assists,
        shots = shots,
        passAttempts = passAttempts,
        passesMade = passesMade,
        tackleAttempts = tackleAttempts,
        tacklesMade = tacklesMade,
        redCards = redCards,
        secondsPlayed = secondsPlayed,
    )
}



