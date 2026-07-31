package com.eafc26.discordstats.discord

import com.eafc26.discordstats.config.PhraseBank
import com.eafc26.discordstats.ea.model.ClubDetails
import com.eafc26.discordstats.ea.model.ClubMatchEntry
import com.eafc26.discordstats.ea.model.MatchResponse
import com.eafc26.discordstats.ea.model.PlayerEntry
import com.eafc26.discordstats.presentation.MatchSummaryBuilder
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.ZoneOffset

class DiscordShadowModeTest {
    private val clubId = "club-us"
    private lateinit var shadow: DiscordShadowMode

    @BeforeEach
    fun setUp() {
        val phrases = PhraseBank(jacksonObjectMapper())
        DiscordEmbedBuilder.phraseBank = phrases
        shadow = DiscordShadowMode(DiscordRenderer(MatchSummaryBuilder(phrases)))
    }

    @Test
    fun `rich match and history payloads have exact structural parity`() {
        val result = shadow.compare(
            match(
                linkedMapOf(
                    "mvp" to player("MVP", "9.2", goals = "3", assists = "1", shots = "6", mom = "1"),
                    "def" to player("Defender", "8.0", tackles = "6", tackleAttempts = "7"),
                    "red" to player("SentOff", "6.0", redCards = "1"),
                    "bagre" to player("Bagre", "5.5", passes = "5", passAttempts = "20"),
                    "gk" to goalkeeper("Keeper"),
                )
            ),
            clubId,
            ZoneOffset.UTC,
        )

        assertThat(result.divergences).isEmpty()
        assertThat(result.hasParity).isTrue()
    }

    @Test
    fun `minimal payload preserves absent sections and exact ordering`() {
        val result = shadow.compare(match(emptyMap()), clubId, ZoneOffset.UTC)

        assertThat(result.divergences).isEmpty()
        assertThat(result.hasParity).isTrue()
    }

    @Test
    fun `virtual pro names and history goalkeeper EA MVP retain legacy output`() {
        val result = shadow.compare(
            match(
                linkedMapOf(
                    "line" to player("platform-line", "8.8"),
                    "bagre" to player("platform-bagre", "5.5"),
                    "gk" to goalkeeper("platform-gk").copy(manOfTheMatch = "1"),
                )
            ),
            clubId,
            ZoneOffset.UTC,
            mapOf("platform-line" to "Pro Line", "platform-gk" to "Pro Keeper"),
        )

        assertThat(result.divergences).isEmpty()
        assertThat(result.hasParity).isTrue()
        assertThat(result.canonicalHistory.embeds.single().fields.single { it.name == "⭐ MVP" }.value)
            .contains("Pro Keeper")
    }

    @Test
    fun `known Bagre highlight defect is preserved without renderer recalculation`() {
        val result = shadow.compare(
            match(
                linkedMapOf(
                    "best" to player("Best", "9.0"),
                    "bagre" to player("Bagre", "8.0"),
                )
            ),
            clubId,
            ZoneOffset.UTC,
        )

        assertThat(result.divergences).isEmpty()
        assertThat(result.canonicalMatch.embeds.single().fields.single {
            it.name == "🥇 DESTAQUES"
        }.value).contains("Bagre")
    }

    @Test
    fun `Discord presentation limit retains only the first two ordered offensive stories`() {
        val result = shadow.compare(
            match(
                linkedMapOf(
                    "decisive" to player("Decisive", "9.5", goals = "3", shots = "6"),
                    "fell-short" to player("FellShort", "9.0", goals = "1", shots = "6"),
                    "wasteful" to player("Wasteful", "8.5", goals = "0", shots = "6"),
                    "bagre" to player("Bagre", "5.0"),
                )
            ),
            clubId,
            ZoneOffset.UTC,
        )

        assertThat(result.divergences).isEmpty()
        assertThat(result.canonicalMatch.embeds.single().fields.map { it.name })
            .contains("⚡ DECISIVO", "😬 FICOU NO QUASE")
            .doesNotContain("🎯 PODERIA TER DECIDIDO", "😵 FALTOU CAPRICHO")
    }

    private fun match(players: Map<String, PlayerEntry>) = MatchResponse(
        matchId = "discord-shadow",
        timestamp = 1_718_500_000L,
        matchType = "leagueMatch",
        clubs = linkedMapOf(
            clubId to ClubMatchEntry(ClubDetails("Our FC", clubId), score = "4", result = "1"),
            "opponent" to ClubMatchEntry(ClubDetails("Opponent FC", "opponent"), score = "1", result = "0"),
        ),
        players = mapOf(clubId to players),
    )

    private fun player(
        name: String,
        rating: String,
        goals: String = "0",
        assists: String = "0",
        shots: String = "2",
        mom: String = "0",
        passes: String = "16",
        passAttempts: String = "20",
        tackles: String = "2",
        tackleAttempts: String = "4",
        redCards: String = "0",
    ) = PlayerEntry(
        playerName = name,
        position = "14",
        rating = rating,
        goals = goals,
        assists = assists,
        shots = shots,
        manOfTheMatch = mom,
        passesMade = passes,
        passAttempts = passAttempts,
        tacklesMade = tackles,
        tackleAttempts = tackleAttempts,
        redCards = redCards,
        secondsPlayed = "5400",
    )

    private fun goalkeeper(name: String) = PlayerEntry(
        playerName = name,
        position = PlayerEntry.POSITION_GOALKEEPER,
        rating = "8.8",
        saves = "7",
        goalsConceded = "1",
        reflexSaves = "4",
        secondsPlayed = "5400",
    )
}
