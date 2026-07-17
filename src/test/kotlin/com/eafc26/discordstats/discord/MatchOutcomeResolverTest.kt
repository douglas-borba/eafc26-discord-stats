package com.eafc26.discordstats.discord

import com.eafc26.discordstats.ea.model.ClubMatchEntry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class MatchOutcomeResolverTest {

    @Nested
    inner class ScoreboardPrimary {

        @Test
        fun `victory when our score is higher`() {
            val result = MatchOutcomeResolver.resolve(
                ourEntry = club(score = "3", result = "1"),
                oppEntry = club(score = "1", result = "0"),
            )
            assertThat(result.outcome).isEqualTo(MatchOutcomeResolver.Outcome.VICTORY)
            assertThat(result.ourScore).isEqualTo(3)
            assertThat(result.oppScore).isEqualTo(1)
        }

        @Test
        fun `loss when our score is lower`() {
            val result = MatchOutcomeResolver.resolve(
                ourEntry = club(score = "0", result = "0"),
                oppEntry = club(score = "2", result = "1"),
            )
            assertThat(result.outcome).isEqualTo(MatchOutcomeResolver.Outcome.LOSS)
            assertThat(result.ourScore).isEqualTo(0)
            assertThat(result.oppScore).isEqualTo(2)
        }

        @Test
        fun `draw when scores are equal`() {
            val result = MatchOutcomeResolver.resolve(
                ourEntry = club(score = "1", result = "2"),
                oppEntry = club(score = "1", result = "2"),
            )
            assertThat(result.outcome).isEqualTo(MatchOutcomeResolver.Outcome.DRAW)
        }
    }

    @Nested
    inner class ScoreboardOverridesResult {

        @Test
        fun `scoreboard victory overrides loss result`() {
            // Scoreboard says 2-1 (victory), but result field says "0" (loss)
            val result = MatchOutcomeResolver.resolve(
                ourEntry = club(score = "2", result = "0"),
                oppEntry = club(score = "1", result = "1"),
            )
            assertThat(result.outcome).isEqualTo(MatchOutcomeResolver.Outcome.VICTORY)
        }

        @Test
        fun `scoreboard loss overrides victory result`() {
            // Scoreboard says 0-3 (loss), but result field says "1" (victory)
            val result = MatchOutcomeResolver.resolve(
                ourEntry = club(score = "0", result = "1"),
                oppEntry = club(score = "3", result = "0"),
            )
            assertThat(result.outcome).isEqualTo(MatchOutcomeResolver.Outcome.LOSS)
        }

        @Test
        fun `scoreboard draw overrides victory result`() {
            // Scoreboard says 2-2 (draw), but result field says "1" (victory)
            val result = MatchOutcomeResolver.resolve(
                ourEntry = club(score = "2", result = "1"),
                oppEntry = club(score = "2", result = "0"),
            )
            assertThat(result.outcome).isEqualTo(MatchOutcomeResolver.Outcome.DRAW)
        }
    }

    @Nested
    inner class FallbackToResult {

        @Test
        fun `uses result when our score is null`() {
            val result = MatchOutcomeResolver.resolve(
                ourEntry = club(score = null, result = "1"),
                oppEntry = club(score = "0", result = "0"),
            )
            assertThat(result.outcome).isEqualTo(MatchOutcomeResolver.Outcome.VICTORY)
        }

        @Test
        fun `uses result when opp score is null`() {
            val result = MatchOutcomeResolver.resolve(
                ourEntry = club(score = "2", result = "0"),
                oppEntry = club(score = null, result = "1"),
            )
            assertThat(result.outcome).isEqualTo(MatchOutcomeResolver.Outcome.LOSS)
        }

        @Test
        fun `uses result when scores are not parseable`() {
            val result = MatchOutcomeResolver.resolve(
                ourEntry = club(score = "invalid", result = "2"),
                oppEntry = club(score = "also-invalid", result = "2"),
            )
            assertThat(result.outcome).isEqualTo(MatchOutcomeResolver.Outcome.DRAW)
        }
    }

    @Nested
    inner class EdgeCases {

        @Test
        fun `handles null our entry`() {
            val result = MatchOutcomeResolver.resolve(
                ourEntry = null,
                oppEntry = club(score = "1", result = "1"),
            )
            // With null our entry, result defaults to LOSS
            assertThat(result.outcome).isEqualTo(MatchOutcomeResolver.Outcome.LOSS)
        }

        @Test
        fun `handles null opp entry`() {
            val result = MatchOutcomeResolver.resolve(
                ourEntry = club(score = "2", result = "1"),
                oppEntry = null,
            )
            // With null opp entry and valid our score, should determine from scores
            assertThat(result.outcome).isEqualTo(MatchOutcomeResolver.Outcome.VICTORY)
        }

        @Test
        fun `handles both entries null`() {
            val result = MatchOutcomeResolver.resolve(
                ourEntry = null,
                oppEntry = null,
            )
            assertThat(result.outcome).isEqualTo(MatchOutcomeResolver.Outcome.LOSS)
            assertThat(result.ourScore).isEqualTo(0)
            assertThat(result.oppScore).isEqualTo(0)
        }

        @Test
        fun `handles zero to zero draw`() {
            val result = MatchOutcomeResolver.resolve(
                ourEntry = club(score = "0", result = "2"),
                oppEntry = club(score = "0", result = "2"),
            )
            assertThat(result.outcome).isEqualTo(MatchOutcomeResolver.Outcome.DRAW)
            assertThat(result.ourScore).isEqualTo(0)
            assertThat(result.oppScore).isEqualTo(0)
        }
    }

    @Nested
    inner class OutcomeProperties {

        @Test
        fun `victory has correct color and emoji`() {
            val outcome = MatchOutcomeResolver.Outcome.VICTORY
            assertThat(outcome.emoji).isEqualTo("🟢")
            assertThat(outcome.label).isEqualTo("Vitória")
            assertThat(outcome.color).isEqualTo(0x2ECC71)
        }

        @Test
        fun `draw has correct color and emoji`() {
            val outcome = MatchOutcomeResolver.Outcome.DRAW
            assertThat(outcome.emoji).isEqualTo("🟡")
            assertThat(outcome.label).isEqualTo("Empate")
            assertThat(outcome.color).isEqualTo(0x95A5A6)
        }

        @Test
        fun `loss has correct color and emoji`() {
            val outcome = MatchOutcomeResolver.Outcome.LOSS
            assertThat(outcome.emoji).isEqualTo("🔴")
            assertThat(outcome.label).isEqualTo("Derrota")
            assertThat(outcome.color).isEqualTo(0xE74C3C)
        }
    }

    private fun club(score: String?, result: String?) = ClubMatchEntry(
        score = score,
        result = result,
    )
}

