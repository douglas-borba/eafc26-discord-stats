package com.eafc26.discordstats.discord

import com.eafc26.discordstats.ea.model.ClubDetails
import com.eafc26.discordstats.ea.model.ClubMatchEntry
import com.eafc26.discordstats.ea.model.MatchResponse
import com.eafc26.discordstats.ea.model.PlayerEntry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.ZoneId

/**
 * Tests for goalkeeper display in Discord embed.
 *
 * Key requirements:
 * - BOT goalkeeper with saves should be displayed
 * - Human goalkeeper with saves should be displayed
 * - Blank BOT name falls back to "Goleiro BOT"
 * - Goalkeeper saves remain attached to the correct player
 * - BOT goalkeeper never appears in player awards (Craque, Bagre, etc.)
 * - The goalkeeper section shows the archetype title (e.g. "🧱 Paredão")
 */
class GoalkeeperDisplayTest {

    private val ourClubId = "1104972"
    private val oppClubId = "99999"
    private val zone = ZoneId.of("America/Sao_Paulo")

    @Test
    fun `BOT goalkeeper with saves is displayed`() {
        val match = createMatchWithGoalkeeper(
            gkName = null,
            gkPosition = "0",
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
            gkName = "   ",
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
        val gk = PlayerEntry(playerName = "TestGK", position = "0", saves = "5")
        assertThat(gk.isGoalkeeper()).isTrue()
    }

    @Test
    fun `goalkeeper with string goalkeeper is detected for backwards compatibility`() {
        val gk = PlayerEntry(playerName = "TestGK", position = "goalkeeper", saves = "5")
        assertThat(gk.isGoalkeeper()).isTrue()
    }

    @Test
    fun `outfield player is not detected as goalkeeper`() {
        val player = PlayerEntry(playerName = "Striker", position = "25", goals = "2")
        assertThat(player.isGoalkeeper()).isFalse()
    }

    @Test
    fun `BOT goalkeeper does not appear in player awards`() {
        val match = createMatchWithGoalkeeperAndOutfield(
            gkName = null,
            gkPosition = "0",
            gkSaves = "5",
            gkRating = "8.0",
            outfieldPlayers = listOf(
                player("Player1", rating = "7.0"),
                player("Player2", rating = "6.5"),
            )
        )

        val payload = DiscordEmbedBuilder.build(match, ourClubId, zone)
        val embed = payload.embeds[0]

        val gkField = embed.fields.firstOrNull { it.name == "🧤 GOLEIRO" }
        assertThat(gkField).isNotNull

        val destaques = embed.fields.firstOrNull { it.name == "🥇 DESTAQUES" }
        if (destaques != null) assertThat(destaques.value).doesNotContain("Goleiro BOT")

        val craque = embed.fields.firstOrNull { it.name == "⭐ CRAQUE DA PARTIDA" }
        if (craque != null) assertThat(craque.value).doesNotContain("Goleiro BOT")
    }

    @Test
    fun `goalkeeper saves are attached to the correct player`() {
        val match = MatchResponse(
            matchId = "test-123",
            timestamp = 1721267100L,
            clubs = mapOf(
                ourClubId to ClubMatchEntry(details = ClubDetails(name = "Our Club"), score = "2", result = "1"),
                oppClubId to ClubMatchEntry(details = ClubDetails(name = "Opp Club"), score = "1", result = "0"),
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
                        saves = "0",
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

    // ── Archetype rendering ───────────────────────────────────────────────────

    @Nested
    inner class ArchetypeRendering {

        @Test
        fun `goalkeeper section contains the archetype title`() {
            // 4 saves, 0 goals, rating 8.0 → WALL
            val embed = buildEmbedForGk(
                saves = "4", goalsConceded = "0", rating = "8.0"
            ).embeds[0]
            val gkField = embed.fields.first { it.name == "🧤 GOLEIRO" }
            assertThat(gkField.value).contains("🧱 Paredão")
        }

        @Test
        fun `UNDER_SIEGE title shown when goalkeeper is bombarded with acceptable rating`() {
            // 10 saves, 2 goals conceded, rating 6.2 → UNDER_SIEGE (rating ≥ 6.0)
            val embed = buildEmbedForGk(
                saves = "10", goalsConceded = "2", rating = "6.2"
            ).embeds[0]
            val gkField = embed.fields.first { it.name == "🧤 GOLEIRO" }
            assertThat(gkField.value).contains("💣 Bombardeado")
        }

        @Test
        fun `POOR title shown when goalkeeper has many saves but very low rating`() {
            // 10 saves, 2 goals conceded, rating 5.3 → POOR (low rating wins over save count)
            val embed = buildEmbedForGk(
                saves = "10", goalsConceded = "2", rating = "5.3"
            ).embeds[0]
            val gkField = embed.fields.first { it.name == "🧤 GOLEIRO" }
            assertThat(gkField.value).contains("🥬 Mão de Alface")
        }

        @Test
        fun `POOR title shown when performance is poor`() {
            // 2 saves, 3 goals, rating 5.0 → POOR
            val embed = buildEmbedForGk(
                saves = "2", goalsConceded = "3", rating = "5.0"
            ).embeds[0]
            val gkField = embed.fields.first { it.name == "🧤 GOLEIRO" }
            assertThat(gkField.value).contains("🥬 Mão de Alface")
        }

        @Test
        fun `QUIET title shown when goalkeeper had almost no involvement`() {
            // 0 saves, 0 goals → QUIET
            val embed = buildEmbedForGk(
                saves = "0", goalsConceded = "0", rating = "7.0"
            ).embeds[0]
            val gkField = embed.fields.first { it.name == "🧤 GOLEIRO" }
            assertThat(gkField.value).contains("🤷 Discreto")
        }

        @Test
        fun `SOLID title shown for decent performance`() {
            // 3 saves, 1 goal, rating 7.0 → SOLID
            val embed = buildEmbedForGk(
                saves = "3", goalsConceded = "1", rating = "7.0"
            ).embeds[0]
            val gkField = embed.fields.first { it.name == "🧤 GOLEIRO" }
            assertThat(gkField.value).contains("🧤 Seguro")
        }

        @Test
        fun `goalkeeper section always contains a phrase`() {
            val embed = buildEmbedForGk(saves = "3", goalsConceded = "1", rating = "7.0").embeds[0]
            val gkField = embed.fields.first { it.name == "🧤 GOLEIRO" }
            assertThat(gkField.value).contains("💬 \"")
        }

        private fun buildEmbedForGk(
            saves: String,
            goalsConceded: String,
            rating: String,
            goodDirectionSaves: String = "0",
        ): DiscordPayload {
            val match = MatchResponse(
                matchId = "arch-test",
                timestamp = 1721267100L,
                clubs = mapOf(
                    ourClubId to ClubMatchEntry(details = ClubDetails(name = "Our Club"), score = "2", result = "1"),
                    oppClubId to ClubMatchEntry(details = ClubDetails(name = "Opp"), score = "1", result = "0"),
                ),
                players = mapOf(
                    ourClubId to mapOf(
                        "gk" to PlayerEntry(
                            playerName         = "Goleiro",
                            position           = "0",
                            saves              = saves,
                            goalsConceded      = goalsConceded,
                            rating             = rating,
                            goodDirectionSaves = goodDirectionSaves,
                            secondsPlayed      = "5400",
                        ),
                        "p1" to player("Linha1"),
                    ),
                    oppClubId to emptyMap(),
                ),
            )
            return DiscordEmbedBuilder.build(match, ourClubId, zone)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun createMatchWithGoalkeeper(
        gkName: String?,
        gkPosition: String,
        gkSaves: String,
        gkGoalsConceded: String,
    ): MatchResponse = MatchResponse(
        matchId = "test-123",
        timestamp = 1721267100L,
        clubs = mapOf(
            ourClubId to ClubMatchEntry(details = ClubDetails(name = "Our Club"), score = "2", result = "1"),
            oppClubId to ClubMatchEntry(details = ClubDetails(name = "Opp Club"), score = "1", result = "0"),
        ),
        players = mapOf(
            ourClubId to mapOf(
                "gk_id" to PlayerEntry(
                    playerName    = gkName,
                    position      = gkPosition,
                    saves         = gkSaves,
                    goalsConceded = gkGoalsConceded,
                    secondsPlayed = "5400",
                ),
                "player1" to player("Player1", rating = "7.0"),
            ),
            oppClubId to emptyMap(),
        ),
    )

    private fun createMatchWithGoalkeeperAndOutfield(
        gkName: String?,
        gkPosition: String,
        gkSaves: String,
        gkRating: String,
        outfieldPlayers: List<PlayerEntry>,
    ): MatchResponse {
        val playersMap = mutableMapOf<String, PlayerEntry>()
        playersMap["gk_id"] = PlayerEntry(
            playerName    = gkName,
            position      = gkPosition,
            saves         = gkSaves,
            rating        = gkRating,
            secondsPlayed = "5400",
        )
        outfieldPlayers.forEachIndexed { i, p -> playersMap["player_$i"] = p }

        return MatchResponse(
            matchId = "test-123",
            timestamp = 1721267100L,
            clubs = mapOf(
                ourClubId to ClubMatchEntry(details = ClubDetails(name = "Our Club"), score = "2", result = "1"),
                oppClubId to ClubMatchEntry(details = ClubDetails(name = "Opp Club"), score = "1", result = "0"),
            ),
            players = mapOf(ourClubId to playersMap, oppClubId to emptyMap()),
        )
    }

    private fun player(
        name: String,
        rating: String      = "7.0",
        goals: String       = "0",
        assists: String     = "0",
        shots: String       = "0",
        passAttempts: String  = "20",
        passesMade: String    = "15",
        tackleAttempts: String = "5",
        tacklesMade: String    = "3",
        secondsPlayed: String  = "5400",
    ) = PlayerEntry(
        playerName     = name,
        position       = "14",
        goals          = goals,
        assists        = assists,
        rating         = rating,
        shots          = shots,
        passAttempts   = passAttempts,
        passesMade     = passesMade,
        tackleAttempts = tackleAttempts,
        tacklesMade    = tacklesMade,
        secondsPlayed  = secondsPlayed,
    )
}

