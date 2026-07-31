package com.eafc26.discordstats.architecture

import com.eafc26.discordstats.config.PhraseBank
import com.eafc26.discordstats.discord.DiscordEmbed
import com.eafc26.discordstats.discord.DiscordEmbedBuilder
import com.eafc26.discordstats.ea.model.ClubDetails
import com.eafc26.discordstats.ea.model.ClubMatchEntry
import com.eafc26.discordstats.ea.model.MatchResponse
import com.eafc26.discordstats.ea.model.PlayerEntry
import com.eafc26.discordstats.presentation.MatchSummaryBuilder
import com.eafc26.discordstats.presentation.OutcomeType
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.ZoneOffset

/**
 * Characterizes the behavior shared by the two current interpretation paths:
 * [MatchSummaryBuilder] for the web card and [DiscordEmbedBuilder] for Discord.
 *
 * These tests intentionally exercise both paths from the same EA DTO. They are
 * migration safety tests, not a recommendation to keep the duplicated paths.
 */
class CurrentPresentationCharacterizationTest {

    private val ourClubId = "club-us"
    private val zone = ZoneOffset.UTC

    private lateinit var summaryBuilder: MatchSummaryBuilder

    @BeforeEach
    fun setUp() {
        val phraseBank = PhraseBank(jacksonObjectMapper())
        summaryBuilder = MatchSummaryBuilder(phraseBank)
        DiscordEmbedBuilder.phraseBank = phraseBank
    }

    @Nested
    inner class SharedDecisions {

        @Test
        fun `web card and Discord agree on the principal award winners`() {
            val match = match(
                ourScore = "4",
                opponentScore = "2",
                players = linkedMapOf(
                    "mvp-id" to player(
                        name = "MVP",
                        rating = "9.2",
                        goals = "1",
                        assists = "2",
                        mom = "1",
                    ),
                    "shooter-id" to player(
                        name = "Shooter",
                        rating = "8.5",
                        goals = "2",
                        shots = "6",
                    ),
                    "defender-id" to player(
                        name = "Defender",
                        rating = "7.8",
                        tackleAttempts = "7",
                        tacklesMade = "6",
                    ),
                    "passer-id" to player(
                        name = "Passer",
                        rating = "8.2",
                        passAttempts = "30",
                        passesMade = "29",
                    ),
                    "bagre-id" to player(
                        name = "Bagre",
                        rating = "5.5",
                        passAttempts = "20",
                        passesMade = "6",
                        tackleAttempts = "5",
                        tacklesMade = "1",
                    ),
                ),
            )

            val card = summaryBuilder.build(match, ourClubId, zone)
            val discord = discordEmbed(match)

            assertThat(discord.fieldValue("⭐ CRAQUE DA PARTIDA")).contains(card.craque!!.name)
            assertThat(discord.fieldValue("🍍 BAGRE DA PARTIDA")).contains(card.bagre!!.name)
            assertThat(discord.fieldValue("🚧 XERIFE DA PARTIDA")).contains(card.xerife!!.name)
            assertThat(discord.fieldValue("🎯 PASSE DE PRECISÃO")).contains(card.passePrecisao!!.name)
            assertThat(discord.fieldValue("📮 CORREIO EXTRAVIADO")).contains(card.correioExtraviado!!.name)
        }

        @Test
        fun `web card and Discord agree on a red card selection independent of positive awards`() {
            val match = match(
                players = linkedMapOf(
                    "star-id" to player(name = "Star", rating = "9.0", goals = "2"),
                    "sent-off-id" to player(
                        name = "SentOff",
                        rating = "6.0",
                        redCards = "1",
                        tackleAttempts = "8",
                        tacklesMade = "7",
                    ),
                    "bagre-id" to player(name = "Bagre", rating = "5.5"),
                ),
            )

            val card = summaryBuilder.build(match, ourClubId, zone)
            val discord = discordEmbed(match)

            assertThat(card.redCard!!.name).isEqualTo("SentOff")
            assertThat(discord.fieldValue("🟥 PERDEU A CABEÇA")).contains(card.redCard!!.name)
            assertThat(card.xerife?.name).isNotEqualTo("SentOff")
        }

        @Test
        fun `web card and Discord agree on goalkeeper identity and archetype title`() {
            val match = match(
                players = linkedMapOf(
                    "line-id" to player(name = "Line", rating = "8.0"),
                    "bagre-id" to player(name = "Bagre", rating = "5.5"),
                    "gk-id" to goalkeeper(
                        name = "Keeper",
                        rating = "8.7",
                        saves = "7",
                        goalsConceded = "1",
                        reflexSaves = "4",
                    ),
                ),
            )

            val card = summaryBuilder.build(match, ourClubId, zone)
            val discordGoalkeeper = discordEmbed(match).fieldValue("🧤 GOLEIRO")

            assertThat(card.muralha!!.name).isEqualTo("Keeper")
            assertThat(discordGoalkeeper).contains(card.muralha!!.name)
            assertThat(discordGoalkeeper).contains(card.muralha!!.archetypeTitle)
        }

        @Test
        fun `web card and Discord exclude a short appearance from individual statistics`() {
            val match = match(
                players = linkedMapOf(
                    "active-id" to player(
                        name = "Active",
                        rating = "8.0",
                        goals = "1",
                        secondsPlayed = "5400",
                    ),
                    "short-id" to player(
                        name = "ShortAppearance",
                        rating = "9.9",
                        goals = "3",
                        secondsPlayed = "300",
                    ),
                ),
            )

            val card = summaryBuilder.build(match, ourClubId, zone)
            val allDiscordFields = discordEmbed(match).fields.joinToString("\n") { it.value }

            assertThat(card.goals!!.scorers.map { it.name }).containsExactly("Active")
            assertThat(card.highlights!!.top3.map { it.name }).doesNotContain("ShortAppearance")
            assertThat(allDiscordFields).contains("Active")
            assertThat(allDiscordFields).doesNotContain("ShortAppearance")
        }

        @Test
        fun `missing playing time keeps all players eligible in both paths`() {
            val match = match(
                players = linkedMapOf(
                    "one-id" to player(name = "One", rating = "8.0", goals = "1", secondsPlayed = null),
                    "two-id" to player(name = "Two", rating = "7.0", assists = "1", secondsPlayed = null),
                ),
            )

            val card = summaryBuilder.build(match, ourClubId, zone)
            val allDiscordFields = discordEmbed(match).fields.joinToString("\n") { it.value }

            assertThat(card.goals!!.scorers.map { it.name }).contains("One")
            assertThat(card.assists!!.assisters.map { it.name }).contains("Two")
            assertThat(allDiscordFields).contains("One").contains("Two")
        }

        @Test
        fun `EA MVP wins Craque over the higher rated player in both paths`() {
            val match = match(
                players = linkedMapOf(
                    "ea-mvp-id" to player(name = "EaMvp", rating = "7.5", mom = "1"),
                    "highest-id" to player(name = "HighestRating", rating = "9.5"),
                    "bagre-id" to player(name = "Bagre", rating = "5.5"),
                ),
            )

            val card = summaryBuilder.build(match, ourClubId, zone)
            val discord = discordEmbed(match)

            assertThat(card.craque!!.name).isEqualTo("EaMvp")
            assertThat(discord.fieldValue("⭐ CRAQUE DA PARTIDA")).contains("EaMvp")
        }

        @Test
        fun `pro names are used consistently by web card and Discord`() {
            val match = match(
                players = linkedMapOf(
                    "player-id" to player(name = "platform_tag", rating = "9.0", goals = "1"),
                    "bagre-id" to player(name = "other_tag", rating = "5.5"),
                ),
            )
            val proNames = mapOf(
                "platform_tag" to "Camisa 10",
                "other_tag" to "Zagueiro Pro",
            )

            val card = summaryBuilder.build(match, ourClubId, zone, proNames = proNames)
            val discord = DiscordEmbedBuilder.build(match, ourClubId, zone, proNames).embeds.single()

            assertThat(card.goals!!.scorers.map { it.name }).contains("Camisa 10")
            assertThat(discord.fieldValue("⚽ GOLS")).contains("Camisa 10")
            assertThat(discord.fields.joinToString("\n") { it.value }).doesNotContain("platform_tag")
        }

        @Test
        fun `same match produces the same selected phrases in both current paths`() {
            val match = match(
                id = "deterministic-match",
                players = linkedMapOf(
                    "star-id" to player(name = "Star", rating = "9.0"),
                    "bagre-id" to player(name = "Bagre", rating = "5.5"),
                ),
            )

            val firstCard = summaryBuilder.build(match, ourClubId, zone)
            val secondCard = summaryBuilder.build(match, ourClubId, zone)
            val firstDiscord = discordEmbed(match)
            val secondDiscord = discordEmbed(match)

            assertThat(firstCard.craque!!.phrase).isEqualTo(secondCard.craque!!.phrase)
            assertThat(firstCard.bagre!!.phrase).isEqualTo(secondCard.bagre!!.phrase)
            assertThat(firstDiscord.fieldValue("⭐ CRAQUE DA PARTIDA"))
                .isEqualTo(secondDiscord.fieldValue("⭐ CRAQUE DA PARTIDA"))
            assertThat(firstDiscord.fieldValue("🍍 BAGRE DA PARTIDA"))
                .isEqualTo(secondDiscord.fieldValue("🍍 BAGRE DA PARTIDA"))
            assertThat(firstDiscord.fieldValue("⭐ CRAQUE DA PARTIDA")).contains(firstCard.craque!!.phrase)
            assertThat(firstDiscord.fieldValue("🍍 BAGRE DA PARTIDA")).contains(firstCard.bagre!!.phrase)
        }
    }

    @Nested
    inner class MatchResultCharacterization {

        @Test
        fun `scoreboard outcome is consistent across both paths`() {
            val scenarios = listOf(
                Triple("3" to "1", OutcomeType.WIN, "Vitória"),
                Triple("2" to "2", OutcomeType.DRAW, "Empate"),
                Triple("0" to "1", OutcomeType.LOSS, "Derrota"),
            )

            scenarios.forEach { (scores, expectedType, expectedDiscordLabel) ->
                val match = match(ourScore = scores.first, opponentScore = scores.second)
                val card = summaryBuilder.build(match, ourClubId, zone)
                val discord = discordEmbed(match)

                assertThat(card.outcome.type).isEqualTo(expectedType)
                assertThat(discord.description).contains(expectedDiscordLabel)
                assertThat(discord.title).contains("${scores.first.toInt()} × ${scores.second.toInt()}")
            }
        }

        @Test
        fun `valid scoreboard takes precedence over contradictory EA result flags`() {
            val match = match(
                ourScore = "2",
                opponentScore = "0",
                ourResult = "0",
                opponentResult = "1",
            )

            val card = summaryBuilder.build(match, ourClubId, zone)
            val discord = discordEmbed(match)

            assertThat(card.outcome.type).isEqualTo(OutcomeType.WIN)
            assertThat(discord.description).contains("Vitória")
        }
    }

    @Nested
    inner class KnownDivergences {

        @Test
        fun `Bagre is excluded from web highlights but currently remains in Discord highlights`() {
            val match = match(
                players = linkedMapOf(
                    "star-id" to player(name = "Star", rating = "9.0"),
                    "bagre-id" to player(name = "Bagre", rating = "5.5"),
                ),
            )

            val card = summaryBuilder.build(match, ourClubId, zone)
            val discordHighlights = discordEmbed(match).fieldValue("🥇 DESTAQUES")

            assertThat(card.bagre!!.name).isEqualTo("Bagre")
            assertThat(card.highlights!!.top3.map { it.name }).doesNotContain("Bagre")
            assertThat(discordHighlights).contains("Bagre")
        }
    }

    private fun discordEmbed(match: MatchResponse): DiscordEmbed =
        DiscordEmbedBuilder.build(match, ourClubId, zone).embeds.single()

    private fun DiscordEmbed.fieldValue(name: String): String =
        fields.single { it.name == name }.value

    private fun match(
        id: String = "characterization-match",
        ourScore: String = "2",
        opponentScore: String = "1",
        ourResult: String = "1",
        opponentResult: String = "0",
        players: Map<String, PlayerEntry> = emptyMap(),
    ): MatchResponse = MatchResponse(
        matchId = id,
        timestamp = 1_718_500_000L,
        matchType = "leagueMatch",
        clubs = linkedMapOf(
            ourClubId to ClubMatchEntry(
                details = ClubDetails(name = "Our FC", clubId = ourClubId),
                score = ourScore,
                result = ourResult,
            ),
            "club-opponent" to ClubMatchEntry(
                details = ClubDetails(name = "Opponent FC", clubId = "club-opponent"),
                score = opponentScore,
                result = opponentResult,
            ),
        ),
        players = mapOf(ourClubId to players),
    )

    private fun player(
        name: String,
        rating: String? = "7.0",
        goals: String? = "0",
        assists: String? = "0",
        shots: String? = "2",
        mom: String? = "0",
        passAttempts: String? = "20",
        passesMade: String? = "16",
        tackleAttempts: String? = "4",
        tacklesMade: String? = "2",
        redCards: String? = "0",
        secondsPlayed: String? = "5400",
    ): PlayerEntry = PlayerEntry(
        playerName = name,
        position = "14",
        rating = rating,
        goals = goals,
        assists = assists,
        shots = shots,
        manOfTheMatch = mom,
        passAttempts = passAttempts,
        passesMade = passesMade,
        tackleAttempts = tackleAttempts,
        tacklesMade = tacklesMade,
        redCards = redCards,
        secondsPlayed = secondsPlayed,
    )

    private fun goalkeeper(
        name: String,
        rating: String,
        saves: String,
        goalsConceded: String,
        reflexSaves: String,
    ): PlayerEntry = PlayerEntry(
        playerName = name,
        position = PlayerEntry.POSITION_GOALKEEPER,
        rating = rating,
        saves = saves,
        goalsConceded = goalsConceded,
        reflexSaves = reflexSaves,
        secondsPlayed = "5400",
    )
}
