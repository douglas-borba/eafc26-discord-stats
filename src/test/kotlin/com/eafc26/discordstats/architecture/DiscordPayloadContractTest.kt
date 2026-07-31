package com.eafc26.discordstats.architecture

import com.eafc26.discordstats.discord.DiscordEmbed
import com.eafc26.discordstats.discord.DiscordEmbedBuilder
import com.eafc26.discordstats.ea.model.ClubDetails
import com.eafc26.discordstats.ea.model.ClubMatchEntry
import com.eafc26.discordstats.ea.model.MatchResponse
import com.eafc26.discordstats.ea.model.PlayerEntry
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.ZoneOffset

/**
 * Structural snapshots for the current Discord contract.
 *
 * Phrase text is deliberately excluded: phrases have their own deterministic
 * characterization tests and can be customized by the user. These snapshots
 * freeze channel structure, section presence, order, title, result and color.
 */
class DiscordPayloadContractTest {

    private val mapper = jacksonObjectMapper()
    private val ourClubId = "club-us"

    @Test
    fun `rich Discord payload matches the Phase 0 structural snapshot`() {
        val match = match(
            players = linkedMapOf(
                "mvp-id" to player(
                    name = "MVP",
                    rating = "9.2",
                    goals = "3",
                    assists = "1",
                    shots = "6",
                    mom = "1",
                    passAttempts = "25",
                    passesMade = "24",
                ),
                "defender-id" to player(
                    name = "Defender",
                    rating = "8.0",
                    tackleAttempts = "7",
                    tacklesMade = "6",
                ),
                "sent-off-id" to player(
                    name = "SentOff",
                    rating = "6.0",
                    redCards = "1",
                ),
                "bagre-id" to player(
                    name = "Bagre",
                    rating = "5.5",
                    passAttempts = "20",
                    passesMade = "5",
                    tackleAttempts = "5",
                    tacklesMade = "1",
                ),
                "gk-id" to goalkeeper("Keeper"),
            ),
        )

        assertThat(snapshot(discordEmbed(match)))
            .isEqualTo(loadSnapshot("architecture/discord-rich-payload.snapshot.json"))
    }

    @Test
    fun `minimal Discord payload matches the Phase 0 structural snapshot`() {
        assertThat(snapshot(discordEmbed(match(players = emptyMap()))))
            .isEqualTo(loadSnapshot("architecture/discord-minimal-payload.snapshot.json"))
    }

    @Test
    fun `rich payload fields remain in the current exact order`() {
        val match = match(
            players = linkedMapOf(
                "mvp-id" to player(
                    name = "MVP",
                    rating = "9.2",
                    goals = "3",
                    assists = "1",
                    shots = "6",
                    mom = "1",
                    passAttempts = "25",
                    passesMade = "24",
                ),
                "defender-id" to player(
                    name = "Defender",
                    rating = "8.0",
                    tackleAttempts = "7",
                    tacklesMade = "6",
                ),
                "sent-off-id" to player(name = "SentOff", rating = "6.0", redCards = "1"),
                "bagre-id" to player(
                    name = "Bagre",
                    rating = "5.5",
                    passAttempts = "20",
                    passesMade = "5",
                    tackleAttempts = "5",
                    tacklesMade = "1",
                ),
                "gk-id" to goalkeeper("Keeper"),
            ),
        )

        assertThat(discordEmbed(match).fields.map { it.name }).containsExactly(
            "​", "⚽ GOLS",
            "​", "🎯 ASSISTÊNCIAS",
            "​", "🥇 DESTAQUES",
            "​", "⭐ CRAQUE DA PARTIDA",
            "​", "⚡ DECISIVO",
            "​", "🍍 BAGRE DA PARTIDA",
            "​", "🟥 PERDEU A CABEÇA",
            "​", "🚧 XERIFE DA PARTIDA",
            "​", "🎯 PASSE DE PRECISÃO",
            "​", "📮 CORREIO EXTRAVIADO",
            "​", "🧤 GOLEIRO",
        )
    }

    private fun snapshot(embed: DiscordEmbed): JsonNode = mapper.valueToTree(
        linkedMapOf(
            "title" to embed.title,
            "result" to embed.description?.substringBefore('\n'),
            "color" to embed.color,
            "hasTimestamp" to (embed.timestamp != null),
            "fields" to embed.fields.map {
                linkedMapOf(
                    "name" to it.name,
                    "inline" to it.inline,
                )
            },
        )
    )

    private fun loadSnapshot(path: String): JsonNode {
        val stream = checkNotNull(javaClass.classLoader.getResourceAsStream(path)) {
            "Missing snapshot resource: $path"
        }
        return stream.use(mapper::readTree)
    }

    private fun discordEmbed(match: MatchResponse): DiscordEmbed =
        DiscordEmbedBuilder.build(match, ourClubId, ZoneOffset.UTC).embeds.single()

    private fun match(
        players: Map<String, PlayerEntry>,
    ): MatchResponse = MatchResponse(
        matchId = "snapshot-match",
        timestamp = 1_718_500_000L,
        matchType = "leagueMatch",
        clubs = linkedMapOf(
            ourClubId to ClubMatchEntry(
                details = ClubDetails(name = "Our FC", clubId = ourClubId),
                score = "4",
                result = "1",
            ),
            "club-opponent" to ClubMatchEntry(
                details = ClubDetails(name = "Opponent FC", clubId = "club-opponent"),
                score = "1",
                result = "0",
            ),
        ),
        players = mapOf(ourClubId to players),
    )

    private fun player(
        name: String,
        rating: String,
        goals: String = "0",
        assists: String = "0",
        shots: String = "2",
        mom: String = "0",
        passAttempts: String = "20",
        passesMade: String = "16",
        tackleAttempts: String = "4",
        tacklesMade: String = "2",
        redCards: String = "0",
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
        secondsPlayed = "5400",
    )

    private fun goalkeeper(name: String): PlayerEntry = PlayerEntry(
        playerName = name,
        position = PlayerEntry.POSITION_GOALKEEPER,
        rating = "8.8",
        saves = "7",
        goalsConceded = "1",
        reflexSaves = "4",
        secondsPlayed = "5400",
    )
}
