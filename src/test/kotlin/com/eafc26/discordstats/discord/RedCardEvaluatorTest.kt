package com.eafc26.discordstats.discord

import com.eafc26.discordstats.config.PhraseCategory
import com.eafc26.discordstats.discord.RedCardEvaluator.RedCardSelection
import com.eafc26.discordstats.ea.model.PlayerEntry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for [RedCardEvaluator].
 *
 * Eligibility: redCards > 0
 *
 * Selection priority (with maxWithOrNull):
 *   1. rating ASC      — lower-rated player wins
 *   2. playerName DESC — latest alphabetically wins the final tie
 *
 * redCards is NOT used as a ranking criterion — only as an eligibility gate.
 * secondsPlayed is deliberately ignored.
 * The evaluator receives all outfield players — no Bagre exclusion applied.
 */
class RedCardEvaluatorTest {

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun player(
        name: String,
        redCards: String? = null,
        rating: String? = null,
        secondsPlayed: String? = "900",
    ) = PlayerEntry(
        playerName    = name,
        position      = null,
        redCards      = redCards,
        rating        = rating,
        secondsPlayed = secondsPlayed,
    )

    private fun evaluate(vararg players: PlayerEntry): RedCardSelection? =
        RedCardEvaluator.evaluate(players.toList())

    // ── Eligibility ───────────────────────────────────────────────────────────

    @Nested
    inner class Eligibility {

        @Test
        fun `empty list returns null`() {
            assertThat(RedCardEvaluator.evaluate(emptyList())).isNull()
        }

        @Test
        fun `player with zero red cards is not eligible`() {
            assertThat(evaluate(player("Clean", redCards = "0"))).isNull()
        }

        @Test
        fun `player with null red cards is not eligible`() {
            assertThat(evaluate(player("NoData", redCards = null))).isNull()
        }

        @Test
        fun `player with one red card is eligible`() {
            val result = evaluate(player("Sent Off", redCards = "1"))
            assertThat(result).isNotNull
            assertThat(result!!.player.playerName).isEqualTo("Sent Off")
        }

        @Test
        fun `all clean players returns null`() {
            assertThat(evaluate(
                player("A", redCards = "0"),
                player("B", redCards = null),
                player("C", redCards = "0"),
            )).isNull()
        }
    }

    // ── Selection result fields ───────────────────────────────────────────────

    @Nested
    inner class SelectionFields {

        @Test
        fun `redCards count is recorded correctly`() {
            val result = evaluate(player("Violent", redCards = "1"))
            assertThat(result!!.redCards).isEqualTo(1)
        }

        @Test
        fun `player reference is the correct entry`() {
            val p = player("Mineiro", redCards = "1", rating = "5.5")
            val result = RedCardEvaluator.evaluate(listOf(p))
            assertThat(result!!.player).isSameAs(p)
        }
    }

    // ── redCards NOT used as a ranking criterion ──────────────────────────────

    @Nested
    inner class RedCardsNotRanked {

        @Test
        fun `redCards count is not a ranking factor - lower rating wins regardless`() {
            // If redCards were ranked, the player with "2" would win.
            // Because only rating matters, the lower-rated player should win.
            val result = evaluate(
                player("HighCount", redCards = "2", rating = "8.0"),
                player("LowCount",  redCards = "1", rating = "5.0"),
            )
            assertThat(result!!.player.playerName).isEqualTo("LowCount")
        }

        @Test
        fun `single-card player with lower rating beats two-card player with higher rating`() {
            val result = evaluate(
                player("TwoCards", redCards = "2", rating = "7.0"),
                player("OneCard",  redCards = "1", rating = "6.0"),
            )
            assertThat(result!!.player.playerName).isEqualTo("OneCard")
        }
    }

    // ── Priority 1: rating ASC (lower rating wins) ────────────────────────────

    @Nested
    inner class RatingAscPriority {

        @Test
        fun `lower-rated player wins the tiebreaker`() {
            val result = evaluate(
                player("HighRating", redCards = "1", rating = "7.5"),
                player("LowRating",  redCards = "1", rating = "5.0"),
            )
            assertThat(result!!.player.playerName).isEqualTo("LowRating")
        }

        @Test
        fun `null rating is treated as lowest priority in tie`() {
            // Rated (5.5) should beat NullRating (null → MAX_VALUE in descending comparator)
            val result = evaluate(
                player("NullRating", redCards = "1", rating = null),
                player("Rated",      redCards = "1", rating = "5.5"),
            )
            assertThat(result!!.player.playerName).isEqualTo("Rated")
        }

        @Test
        fun `three-way tie on eligibility - lowest rating wins`() {
            val result = evaluate(
                player("A", redCards = "1", rating = "8.0"),
                player("B", redCards = "1", rating = "5.0"),
                player("C", redCards = "1", rating = "6.5"),
            )
            assertThat(result!!.player.playerName).isEqualTo("B")
        }
    }

    // ── Priority 2: playerName DESC (latest alphabetically wins) ─────────────

    @Nested
    inner class PlayerNameDescPriority {

        @Test
        fun `latest alphabetical name wins when rating is equal`() {
            val result = evaluate(
                player("Andre", redCards = "1", rating = "6.0"),
                player("Zeca",  redCards = "1", rating = "6.0"),
            )
            assertThat(result!!.player.playerName).isEqualTo("Zeca")
        }

        @Test
        fun `tiebreaker is deterministic - order of input does not matter`() {
            val playersAB = listOf(
                player("Andre", redCards = "1", rating = "6.0"),
                player("Zeca",  redCards = "1", rating = "6.0"),
            )
            val playersBA = listOf(
                player("Zeca",  redCards = "1", rating = "6.0"),
                player("Andre", redCards = "1", rating = "6.0"),
            )
            assertThat(RedCardEvaluator.evaluate(playersAB)!!.player.playerName)
                .isEqualTo(RedCardEvaluator.evaluate(playersBA)!!.player.playerName)
        }
    }

    // ── secondsPlayed ignored ─────────────────────────────────────────────────

    @Nested
    inner class SecondsPlayedIgnored {

        @Test
        fun `secondsPlayed does not affect selection - only rating and name matter`() {
            // p2 has lower rating → wins, despite playing far fewer minutes
            val p1 = player("FullGame",   redCards = "1", rating = "6.0", secondsPlayed = "5400")
            val p2 = player("SubSentOff", redCards = "1", rating = "5.5", secondsPlayed = "900")
            assertThat(RedCardEvaluator.evaluate(listOf(p1, p2))!!.player.playerName)
                .isEqualTo("SubSentOff")
        }

        @Test
        fun `null secondsPlayed does not disqualify a player`() {
            val result = evaluate(player("NoSeconds", redCards = "1", rating = "6.0", secondsPlayed = null))
            assertThat(result).isNotNull
        }
    }

    // ── Award independence ────────────────────────────────────────────────────

    @Nested
    inner class AwardIndependence {

        @Test
        fun `Bagre player is not excluded - evaluator receives all outfield players`() {
            val bagre  = player("Bagre",  redCards = "1", rating = "5.0")
            val scorer = player("Scorer", redCards = "0", rating = "8.0")
            val result = RedCardEvaluator.evaluate(listOf(bagre, scorer))
            assertThat(result).isNotNull
            assertThat(result!!.player.playerName).isEqualTo("Bagre")
        }
    }

    // ── Omission when no red card ─────────────────────────────────────────────

    @Nested
    inner class OmissionWhenNoRedCard {

        @Test
        fun `section is omitted when no player was sent off`() {
            assertThat(evaluate(
                player("A", rating = "7.0"),
                player("B", rating = "8.0"),
                player("C", rating = "6.0"),
            )).isNull()
        }
    }

    // ── Default phrase validity ───────────────────────────────────────────────

    @Nested
    inner class PhraseValidity {

        /**
         * These words or phrases signal that a message makes an unsupported
         * claim about the timing of the red card, its effect on the match
         * result, or any event not present in the EA payload.
         */
        private val forbiddenPatterns = listOf(
            "rumo da partida",
            "rumo",
            "resultado",
            "mudou",
            "estrago",
            "tranquilidade",
            "precisava",
            "na hora",
            "pior momento",
            "raiva",
        )

        @Test
        fun `no default phrase claims the red card changed the match direction or result`() {
            val phrases = PhraseCategory.PERDEU_A_CABECA.defaults
            phrases.forEach { phrase ->
                forbiddenPatterns.forEach { pattern ->
                    assertThat(phrase.lowercase())
                        .withFailMessage(
                            "Phrase \"$phrase\" contains unsupported claim (matched pattern: \"$pattern\")"
                        )
                        .doesNotContain(pattern)
                }
            }
        }

        @Test
        fun `all default phrases are non-blank`() {
            PhraseCategory.PERDEU_A_CABECA.defaults.forEach { phrase ->
                assertThat(phrase).isNotBlank()
            }
        }

        @Test
        fun `there are at least 8 default phrases`() {
            assertThat(PhraseCategory.PERDEU_A_CABECA.defaults.size).isGreaterThanOrEqualTo(8)
        }
    }

    // ── Discord display format ────────────────────────────────────────────────

    @Nested
    inner class DiscordDisplayFormat {

        private fun buildEmbed(vararg players: PlayerEntry) =
            DiscordEmbedBuilder.build(
                com.eafc26.discordstats.ea.model.MatchResponse(
                    matchId   = "test-rc-001",
                    timestamp = 1_700_000_000L,
                    clubs     = mapOf(
                        "our" to com.eafc26.discordstats.ea.model.ClubMatchEntry(
                            details = com.eafc26.discordstats.ea.model.ClubDetails(name = "Test FC"),
                            score   = "1",
                            result  = "1",
                        ),
                        "opp" to com.eafc26.discordstats.ea.model.ClubMatchEntry(
                            details = com.eafc26.discordstats.ea.model.ClubDetails(name = "Opp FC"),
                            score   = "0",
                            result  = "0",
                        ),
                    ),
                    players = mapOf("our" to players.associateBy { it.playerName ?: "p" }),
                ),
                ourClubId = "our",
            )

        @Test
        fun `Discord field shows fixed label Cartao vermelho`() {
            val embed = buildEmbed(player("Mineiro", redCards = "1", rating = "5.5")).embeds[0]
            val redCardField = embed.fields.firstOrNull { it.name.contains("PERDEU") }
            assertThat(redCardField).isNotNull
            assertThat(redCardField!!.value).contains("Cartão vermelho")
        }

        @Test
        fun `Discord field does not show a numeric card count`() {
            val embed = buildEmbed(player("Mineiro", redCards = "1", rating = "5.5")).embeds[0]
            val redCardField = embed.fields.firstOrNull { it.name.contains("PERDEU") }
            assertThat(redCardField).isNotNull
            // Must not show "1 cartão" or "2 cartões"
            assertThat(redCardField!!.value).doesNotContainPattern("\\d+ cart")
        }

        @Test
        fun `Discord field is omitted when no red card`() {
            val embed = buildEmbed(player("Clean", redCards = "0", rating = "7.0")).embeds[0]
            val redCardField = embed.fields.firstOrNull { it.name.contains("PERDEU") }
            assertThat(redCardField).isNull()
        }
    }
}
