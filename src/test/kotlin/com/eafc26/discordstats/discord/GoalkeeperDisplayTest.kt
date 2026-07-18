package com.eafc26.discordstats.discord

import com.eafc26.discordstats.ea.model.ClubDetails
import com.eafc26.discordstats.ea.model.ClubMatchEntry
import com.eafc26.discordstats.ea.model.MatchResponse
import com.eafc26.discordstats.ea.model.PlayerEntry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.ZoneId

/**
 * Tests for goalkeeper display in Discord embed and Match Card.
 * 
 * Key requirements:
 * - BOT goalkeeper with saves should be displayed
 * - Human goalkeeper with saves should be displayed
 * - Blank BOT name falls back to "Goleiro BOT"
 * - Goalkeeper saves remain attached to the correct player
 * - BOT goalkeeper never appears in player awards (Craque, Bagre, etc.)
 */
class GoalkeeperDisplayTest {

    private val ourClubId = "1104972"
    private val oppClubId = "99999"
    private val zone = ZoneId.of("America/Sao_Paulo")

    @Test
    fun `BOT goalkeeper with saves is displayed`() {
        val match = createMatchWithGoalkeeper(
            gkName = null,  // BOT has no name
            gkPosition = "0",  // EA API position code for goalkeeper
            gkSaves = "5",
            gkGoalsConceded = "2",
        )

        val payload = DiscordEmbedBuilder.build(match, ourClubId, zone)
        val embed = payload.embeds[0]
        
        val gkField = embed.fields.firstOrNull { it.name == "🧤 GOLEIRO" }
        assertThat(gkField).isNotNull
        assertThat(gkField!!.value).contains("Goleiro BOT")
        assertThat(gkField.value).contains("5 defesas")
        assertThat(gkField.value).contains("2 gols sofridos")
    }

    @Test
    fun `human goalkeeper with saves is displayed`() {
        val match = createMatchWithGoalkeeper(
            gkName = "João Goleiro",
            gkPosition = "0",
            gkSaves = "7",
            gkGoalsConceded = "1",
        )

        val payload = DiscordEmbedBuilder.build(match, ourClubId, zone)
        val embed = payload.embeds[0]
        
        val gkField = embed.fields.firstOrNull { it.name == "🧤 GOLEIRO" }
        assertThat(gkField).isNotNull
        assertThat(gkField!!.value).contains("João Goleiro")
        assertThat(gkField.value).contains("7 defesas")
        assertThat(gkField.value).contains("1 gol sofrido")  // singular
    }

    @Test
    fun `blank BOT name falls back to Goleiro BOT`() {
        val match = createMatchWithGoalkeeper(
            gkName = "   ",  // blank name
            gkPosition = "0",
            gkSaves = "3",
            gkGoalsConceded = "0",
        )

        val payload = DiscordEmbedBuilder.build(match, ourClubId, zone)
        val embed = payload.embeds[0]
        
        val gkField = embed.fields.firstOrNull { it.name == "🧤 GOLEIRO" }
        assertThat(gkField).isNotNull
        assertThat(gkField!!.value).contains("Goleiro BOT")
    }

    @Test
    fun `goalkeeper with position code 0 is detected`() {
        val gk = PlayerEntry(
            playerName = "TestGK",
            position = "0",  // EA API position code
            saves = "5",
        )
        
        assertThat(gk.isGoalkeeper()).isTrue()
    }

    @Test
    fun `goalkeeper with string goalkeeper is detected for backwards compatibility`() {
        val gk = PlayerEntry(
            playerName = "TestGK",
            position = "goalkeeper",  // test helper string
            saves = "5",
        )
        
        assertThat(gk.isGoalkeeper()).isTrue()
    }

    @Test
    fun `outfield player is not detected as goalkeeper`() {
        val player = PlayerEntry(
            playerName = "Striker",
            position = "25",  // striker position code
            goals = "2",
        )
        
        assertThat(player.isGoalkeeper()).isFalse()
    }

    @Test
    fun `BOT goalkeeper does not appear in player awards`() {
        val match = createMatchWithGoalkeeperAndOutfield(
            gkName = null,  // BOT
            gkPosition = "0",
            gkSaves = "5",
            gkRating = "8.0",  // High rating but should not be in awards
            outfieldPlayers = listOf(
                player("Player1", rating = "7.0"),
                player("Player2", rating = "6.5"),
            )
        )

        val payload = DiscordEmbedBuilder.build(match, ourClubId, zone)
        val embed = payload.embeds[0]
        
        // BOT goalkeeper should be in GOLEIRO section
        val gkField = embed.fields.firstOrNull { it.name == "🧤 GOLEIRO" }
        assertThat(gkField).isNotNull
        
        // BOT goalkeeper should NOT appear in DESTAQUES (top 3)
        val destaques = embed.fields.firstOrNull { it.name == "🥇 DESTAQUES" }
        if (destaques != null) {
            assertThat(destaques.value).doesNotContain("Goleiro BOT")
        }
        
        // BOT goalkeeper should NOT appear in CRAQUE
        val craque = embed.fields.firstOrNull { it.name == "⭐ CRAQUE DA PARTIDA" }
        if (craque != null) {
            assertThat(craque.value).doesNotContain("Goleiro BOT")
        }
    }

    @Test
    fun `goalkeeper saves are attached to the correct player`() {
        val match = MatchResponse(
            matchId = "test-123",
            timestamp = 1721267100L,
            clubs = mapOf(
                ourClubId to ClubMatchEntry(
                    details = ClubDetails(name = "Our Club"),
                    score = "2",
                    result = "1"
                ),
                oppClubId to ClubMatchEntry(
                    details = ClubDetails(name = "Opp Club"),
                    score = "1",
                    result = "0"
                ),
            ),
            players = mapOf(
                ourClubId to mapOf(
                    "gk_id" to PlayerEntry(
                        playerName = "Nosso Goleiro",
                        position = "0",
                        saves = "8",
                        goalsConceded = "1",
                        secondsPlayed = "5400",
                    ),
                    "player_id" to PlayerEntry(
                        playerName = "Nosso Atacante",
                        position = "25",
                        goals = "2",
                        saves = "0",  // Outfield player has no saves
                        secondsPlayed = "5400",
                    ),
                ),
                oppClubId to emptyMap(),
            ),
        )

        val payload = DiscordEmbedBuilder.build(match, ourClubId, zone)
        val embed = payload.embeds[0]
        
        val gkField = embed.fields.firstOrNull { it.name == "🧤 GOLEIRO" }
        assertThat(gkField).isNotNull
        assertThat(gkField!!.value).contains("Nosso Goleiro")
        assertThat(gkField.value).contains("8 defesas")
        
        // Outfield player should not appear in goalkeeper section
        assertThat(gkField.value).doesNotContain("Nosso Atacante")
    }

    @Test
    fun `singular form used for 1 save`() {
        val match = createMatchWithGoalkeeper(
            gkName = "GK",
            gkPosition = "0",
            gkSaves = "1",
            gkGoalsConceded = "0",
        )

        val payload = DiscordEmbedBuilder.build(match, ourClubId, zone)
        val embed = payload.embeds[0]
        
        val gkField = embed.fields.firstOrNull { it.name == "🧤 GOLEIRO" }
        assertThat(gkField!!.value).contains("1 defesa")
        assertThat(gkField.value).doesNotContain("1 defesas")
    }

    private fun createMatchWithGoalkeeper(
        gkName: String?,
        gkPosition: String,
        gkSaves: String,
        gkGoalsConceded: String,
    ): MatchResponse {
        return MatchResponse(
            matchId = "test-123",
            timestamp = 1721267100L,
            clubs = mapOf(
                ourClubId to ClubMatchEntry(
                    details = ClubDetails(name = "Our Club"),
                    score = "2",
                    result = "1"
                ),
                oppClubId to ClubMatchEntry(
                    details = ClubDetails(name = "Opp Club"),
                    score = "1",
                    result = "0"
                ),
            ),
            players = mapOf(
                ourClubId to mapOf(
                    "gk_id" to PlayerEntry(
                        playerName = gkName,
                        position = gkPosition,
                        saves = gkSaves,
                        goalsConceded = gkGoalsConceded,
                        secondsPlayed = "5400",
                    ),
                    "player1" to player("Player1", rating = "7.0"),
                ),
                oppClubId to emptyMap(),
            ),
        )
    }

    private fun createMatchWithGoalkeeperAndOutfield(
        gkName: String?,
        gkPosition: String,
        gkSaves: String,
        gkRating: String,
        outfieldPlayers: List<PlayerEntry>,
    ): MatchResponse {
        val playersMap = mutableMapOf<String, PlayerEntry>()
        playersMap["gk_id"] = PlayerEntry(
            playerName = gkName,
            position = gkPosition,
            saves = gkSaves,
            rating = gkRating,
            secondsPlayed = "5400",
        )
        outfieldPlayers.forEachIndexed { i, p ->
            playersMap["player_$i"] = p
        }

        return MatchResponse(
            matchId = "test-123",
            timestamp = 1721267100L,
            clubs = mapOf(
                ourClubId to ClubMatchEntry(
                    details = ClubDetails(name = "Our Club"),
                    score = "2",
                    result = "1"
                ),
                oppClubId to ClubMatchEntry(
                    details = ClubDetails(name = "Opp Club"),
                    score = "1",
                    result = "0"
                ),
            ),
            players = mapOf(
                ourClubId to playersMap,
                oppClubId to emptyMap(),
            ),
        )
    }

    private fun player(
        name: String,
        rating: String = "7.0",
        goals: String = "0",
        assists: String = "0",
        shots: String = "0",
        passAttempts: String = "20",
        passesMade: String = "15",
        tackleAttempts: String = "5",
        tacklesMade: String = "3",
        secondsPlayed: String = "5400",
    ) = PlayerEntry(
        playerName = name,
        position = "14",  // Midfielder
        goals = goals,
        assists = assists,
        rating = rating,
        shots = shots,
        passAttempts = passAttempts,
        passesMade = passesMade,
        tackleAttempts = tackleAttempts,
        tacklesMade = tacklesMade,
        secondsPlayed = secondsPlayed,
    )
}

