package com.eafc26.discordstats.presentation

import com.eafc26.discordstats.config.PhraseBank
import com.eafc26.discordstats.ea.model.ClubDetails
import com.eafc26.discordstats.ea.model.ClubMatchEntry
import com.eafc26.discordstats.ea.model.MatchResponse
import com.eafc26.discordstats.ea.model.PlayerEntry
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Regression tests for the Bagre exclusion rule:
 *
 * Once a player is selected as Bagre da Partida, that player must never
 * receive any positive individual award (Craque, Destaques/highlights,
 * Perigo Constante, Xerife, Passe Precisão).
 *
 * playerName is the internal identity key — proName is never used for comparisons.
 */
class BagreExclusionTest {

    private lateinit var builder: MatchSummaryBuilder
    private val clubId = "42"

    @BeforeEach
    fun setUp() {
        builder = MatchSummaryBuilder(PhraseBank(jacksonObjectMapper()))
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** A player with all required fields filled to be eligible for most awards. */
    private fun player(
        name: String,
        rating: String = "7.0",
        goals: String = "0",
        assists: String = "0",
        shots: String = "2",
        passAttempts: String = "20",
        passesMade: String = "16",
        tackleAttempts: String = "5",
        tacklesMade: String = "4",
        secondsPlayed: String = "5400",
        position: String = "midfielder",
    ) = PlayerEntry(
        playerName = name,
        position = position,
        rating = rating,
        goals = goals,
        assists = assists,
        shots = shots,
        passAttempts = passAttempts,
        passesMade = passesMade,
        tackleAttempts = tackleAttempts,
        tacklesMade = tacklesMade,
        secondsPlayed = secondsPlayed,
    )

    private fun buildSummary(players: Map<String, PlayerEntry>): MatchSummaryPresentation {
        val match = MatchResponse(
            matchId = "exclusion-test-01",
            timestamp = 1_700_000_000L,
            clubs = mapOf(
                clubId to ClubMatchEntry(
                    details = ClubDetails(name = "Test FC"),
                    score = "2",
                    result = "1",
                ),
                "opp" to ClubMatchEntry(
                    details = ClubDetails(name = "Opp FC"),
                    score = "1",
                    result = "0",
                ),
            ),
            players = mapOf(clubId to players),
        )
        return builder.build(match, clubId)
    }

    // ── Xerife exclusion ──────────────────────────────────────────────────────

    @Nested
    inner class XerifeExclusion {

        @Test
        fun `Bagre cannot also be Xerife`() {
            // "Bagre" has the lowest rating → selected as Bagre.
            // "Bagre" also has the most tackles → would be Xerife without the exclusion rule.
            val players = mapOf(
                "Bagre" to player("Bagre", rating = "5.5", tackleAttempts = "10", tacklesMade = "9"),
                "Good"  to player("Good",  rating = "8.0", tackleAttempts = "5",  tacklesMade = "3"),
            )

            val summary = buildSummary(players)

            assertThat(summary.bagre?.name).isEqualTo("Bagre")
            assertThat(summary.xerife?.name).isNotEqualTo("Bagre")
        }

        @Test
        fun `next eligible player becomes Xerife when Bagre would have won`() {
            val players = mapOf(
                "Bagre" to player("Bagre", rating = "5.5", tackleAttempts = "10", tacklesMade = "9"),
                "Runner" to player("Runner", rating = "7.5", tackleAttempts = "6",  tacklesMade = "5"),
            )

            val summary = buildSummary(players)

            assertThat(summary.bagre?.name).isEqualTo("Bagre")
            assertThat(summary.xerife?.name).isEqualTo("Runner")
        }

        @Test
        fun `Xerife is null when only eligible player is the Bagre`() {
            // Only the Bagre has enough tackle attempts; the other player has none.
            val players = mapOf(
                "Bagre" to player("Bagre", rating = "5.5", tackleAttempts = "10", tacklesMade = "8"),
                "Good"  to player("Good",  rating = "8.0", tackleAttempts = "1",  tacklesMade = "1"),
            )

            val summary = buildSummary(players)

            assertThat(summary.bagre?.name).isEqualTo("Bagre")
            assertThat(summary.xerife).isNull()
        }
    }

    // ── Craque exclusion ─────────────────────────────────────────────────────

    @Nested
    inner class CraqueExclusion {

        @Test
        fun `Bagre cannot also be Craque`() {
            // "Bagre" has lowest rating but also highest rating among a two-player list
            // — achieve this by making Bagre the only player; Craque should be null.
            val players = mapOf(
                "Bagre" to player("Bagre", rating = "5.5"),
            )

            val summary = buildSummary(players)

            assertThat(summary.bagre?.name).isEqualTo("Bagre")
            assertThat(summary.craque).isNull()
        }

        @Test
        fun `next eligible player becomes Craque when Bagre would have won`() {
            val players = mapOf(
                "Bagre"  to player("Bagre",  rating = "5.5"),
                "Craque" to player("Craque", rating = "8.5"),
            )

            val summary = buildSummary(players)

            assertThat(summary.bagre?.name).isEqualTo("Bagre")
            assertThat(summary.craque?.name).isEqualTo("Craque")
        }
    }

    // ── Perigo Constante exclusion ────────────────────────────────────────────

    @Nested
    inner class PerigoConstanteExclusion {

        @Test
        fun `Bagre cannot also be Perigo Constante`() {
            val players = mapOf(
                "Bagre" to player("Bagre", rating = "5.5", shots = "6"),
                "Good"  to player("Good",  rating = "8.0", shots = "2"),
            )

            val summary = buildSummary(players)

            assertThat(summary.bagre?.name).isEqualTo("Bagre")
            assertThat(summary.offensiveNarratives.none { it.name == "Bagre" }).isTrue()
        }

        @Test
        fun `Perigo Constante is null when only the Bagre has enough shots`() {
            val players = mapOf(
                "Bagre" to player("Bagre", rating = "5.5", shots = "5"),
                "Good"  to player("Good",  rating = "8.0", shots = "1"),
            )

            val summary = buildSummary(players)

            assertThat(summary.bagre?.name).isEqualTo("Bagre")
            assertThat(summary.offensiveNarratives).isEmpty()
        }
    }

    // ── Passe Precisão exclusion ──────────────────────────────────────────────

    @Nested
    inner class PassePrecisaoExclusion {

        @Test
        fun `Bagre cannot also be Passe Precisao`() {
            val players = mapOf(
                // Bagre has perfect passing but lowest rating
                "Bagre"  to player("Bagre",  rating = "5.5", passAttempts = "30", passesMade = "30"),
                "Runner" to player("Runner", rating = "8.0", passAttempts = "30", passesMade = "24"),
            )

            val summary = buildSummary(players)

            assertThat(summary.bagre?.name).isEqualTo("Bagre")
            assertThat(summary.passePrecisao?.name).isNotEqualTo("Bagre")
        }

        @Test
        fun `Passe Precisao is null when only Bagre meets the minimum attempt threshold`() {
            val players = mapOf(
                "Bagre" to player("Bagre", rating = "5.5", passAttempts = "20", passesMade = "18"),
                "Good"  to player("Good",  rating = "8.0", passAttempts = "5",  passesMade = "4"),
            )

            val summary = buildSummary(players)

            assertThat(summary.bagre?.name).isEqualTo("Bagre")
            assertThat(summary.passePrecisao).isNull()
        }
    }
}

