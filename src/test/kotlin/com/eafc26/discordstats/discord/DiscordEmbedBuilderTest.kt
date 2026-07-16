package com.eafc26.discordstats.discord

import com.eafc26.discordstats.ea.model.ClubDetails
import com.eafc26.discordstats.ea.model.ClubMatchEntry
import com.eafc26.discordstats.ea.model.MatchResponse
import com.eafc26.discordstats.ea.model.PlayerEntry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.ZoneOffset

class DiscordEmbedBuilderTest {

    private val ourClubId = "12345"
    private val zone = ZoneOffset.UTC

    // -- Result + color -------------------------------------------------------

    @Test
    fun `win result shows Win and green color`() {
        val embed = buildEmbed(ourResult = "1").embeds[0]
        assertThat(embed.fields.field("Result").value).isEqualTo("Win")
        assertThat(embed.color).isEqualTo(0x2ECC71)
    }

    @Test
    fun `draw result shows Draw and grey color`() {
        val embed = buildEmbed(ourResult = "2").embeds[0]
        assertThat(embed.fields.field("Result").value).isEqualTo("Draw")
        assertThat(embed.color).isEqualTo(0x95A5A6)
    }

    @Test
    fun `loss result shows Loss and red color`() {
        val embed = buildEmbed(ourResult = "0").embeds[0]
        assertThat(embed.fields.field("Result").value).isEqualTo("Loss")
        assertThat(embed.color).isEqualTo(0xE74C3C)
    }

    // -- Score ----------------------------------------------------------------

    @Test
    fun `score field shows our score vs opponent score`() {
        val embed = buildEmbed(ourScore = "3", oppScore = "1").embeds[0]
        assertThat(embed.fields.field("Score").value).isEqualTo("3 – 1")
    }

    // -- Title ----------------------------------------------------------------

    @Test
    fun `title contains our name vs opponent name`() {
        val embed = buildEmbed(ourName = "Associação BF", oppName = "Rival FC").embeds[0]
        assertThat(embed.title).isEqualTo("Associação BF vs Rival FC")
    }

    // -- Footer ---------------------------------------------------------------

    @Test
    fun `footer contains match ID`() {
        val embed = buildEmbed(matchId = "999888777").embeds[0]
        assertThat(embed.footer?.text).isEqualTo("Match ID: 999888777")
    }

    // -- Goal Scorers ---------------------------------------------------------

    @Test
    fun `goal scorers section lists players with goals`() {
        val embed = buildEmbedWithPlayers(
            player("Striker",    goals = "2"),
            player("Midfielder", goals = "1"),
            player("Defender",   goals = "0"),
        ).embeds[0]
        val field = embed.fields.fieldOrNull("Goal Scorers")
        assertThat(field).isNotNull
        assertThat(field!!.value).contains("Striker (2)")
        assertThat(field.value).contains("Midfielder (1)")
        assertThat(field.value).doesNotContain("Defender")
    }

    @Test
    fun `goal scorers section is omitted when nobody scored`() {
        val embed = buildEmbedWithPlayers(player("Defender", goals = "0")).embeds[0]
        assertThat(embed.fields.fieldOrNull("Goal Scorers")).isNull()
    }

    // -- Assists --------------------------------------------------------------

    @Test
    fun `assists section lists players with assists`() {
        val embed = buildEmbedWithPlayers(
            player("Midfielder", assists = "2"),
            player("Striker",    assists = "0"),
        ).embeds[0]
        val field = embed.fields.fieldOrNull("Assists")
        assertThat(field).isNotNull
        assertThat(field!!.value).contains("Midfielder (2)")
        assertThat(field.value).doesNotContain("Striker")
    }

    @Test
    fun `assists section is omitted when nobody assisted`() {
        val embed = buildEmbedWithPlayers(player("Striker", assists = "0")).embeds[0]
        assertThat(embed.fields.fieldOrNull("Assists")).isNull()
    }

    // -- Top 3 highlights -----------------------------------------------------

    @Test
    fun `top 3 shows players sorted by rating descending`() {
        val embed = buildEmbedWithPlayers(
            player("B", rating = "7.0"),
            player("A", rating = "8.5"),
            player("C", rating = "6.0"),
        ).embeds[0]
        val text = embed.fields.field("🏅 Destaques").value
        assertThat(text.indexOf("A")).isLessThan(text.indexOf("B"))
        assertThat(text.indexOf("B")).isLessThan(text.indexOf("C"))
    }

    @Test
    fun `top 3 contains no more than three players`() {
        val embed = buildEmbedWithPlayers(
            player("P1", rating = "9.0"),
            player("P2", rating = "8.0"),
            player("P3", rating = "7.0"),
            player("P4", rating = "6.0"),
            player("P5", rating = "5.0"),
        ).embeds[0]
        val text = embed.fields.field("🏅 Destaques").value
        assertThat(text).contains("🥇").contains("🥈").contains("🥉")
        assertThat(text).contains("P1").contains("P2").contains("P3")
        assertThat(text).doesNotContain("P4").doesNotContain("P5")
    }

    @Test
    fun `MOTM player is marked with star and MOTM tag in top 3`() {
        val embed = buildEmbedWithPlayers(
            player("Hero",  rating = "9.0", mom = "1"),
            player("Other", rating = "7.0"),
        ).embeds[0]
        val text = embed.fields.field("🏅 Destaques").value
        assertThat(text).contains("Hero")
        assertThat(text).contains("MOTM")
        assertThat(text).doesNotContain("Other.*MOTM".toRegex().pattern)
    }

    @Test
    fun `top 3 section is omitted when no ratings present`() {
        val embed = buildEmbedWithPlayers(player("A", rating = null)).embeds[0]
        assertThat(embed.fields.fieldOrNull("🏅 Destaques")).isNull()
    }

    // -- Seconds played -------------------------------------------------------

    @Test
    fun `players with zero seconds played are excluded from all sections`() {
        val embed = buildEmbedWithPlayers(
            player("Playing", goals = "1", rating = "8.0", secondsPlayed = "900"),
            player("Unused",  goals = "0", rating = "6.0", secondsPlayed = "0"),
        ).embeds[0]
        val ratings = embed.fields.field("🏅 Destaques").value
        assertThat(ratings).contains("Playing")
        assertThat(ratings).doesNotContain("Unused")
    }

    @Test
    fun `players with null seconds played are included`() {
        val embed = buildEmbedWithPlayers(
            player("NullSecs", rating = "7.5", secondsPlayed = null),
        ).embeds[0]
        assertThat(embed.fields.field("🏅 Destaques").value).contains("NullSecs")
    }

    // -- Goalkeeper section ---------------------------------------------------

    @Test
    fun `goalkeeper section shows saves, rating, and goals conceded when non-zero`() {
        val embed = buildEmbedWithPlayers(
            goalkeeper("Keeper", saves = "7", rating = "8.5", goalsConceded = "2"),
            player("Outfield", rating = "7.0"),
        ).embeds[0]
        val field = embed.fields.field("🥅 Goleiro")
        assertThat(field.value).contains("Keeper")
        assertThat(field.value).contains("7 defesas")
        assertThat(field.value).contains("Nota: 8.5")
        assertThat(field.value).contains("Gols sofridos: 2")
    }

    @Test
    fun `goalkeeper section omits goals conceded when zero`() {
        val embed = buildEmbedWithPlayers(
            goalkeeper("CleanSheet", saves = "5", rating = "9.0", goalsConceded = "0"),
            player("Outfield", rating = "7.0"),
        ).embeds[0]
        val field = embed.fields.field("🥅 Goleiro")
        assertThat(field.value).doesNotContain("Gols sofridos")
    }

    @Test
    fun `goalkeeper section is omitted when no goalkeeper played`() {
        val embed = buildEmbedWithPlayers(
            player("Midfielder", rating = "7.0"),
        ).embeds[0]
        assertThat(embed.fields.fieldOrNull("🥅 Goleiro")).isNull()
    }

    @Test
    fun `when two goalkeepers played shows the one with most seconds`() {
        val embed = buildEmbedWithPlayers(
            goalkeeper("GK_Short", saves = "1", rating = "7.0", secondsPlayed = "600"),
            goalkeeper("GK_Long",  saves = "5", rating = "8.0", secondsPlayed = "1200"),
            player("Outfield", rating = "6.5"),
        ).embeds[0]
        val field = embed.fields.field("🥅 Goleiro")
        assertThat(field.value).contains("GK_Long")
        assertThat(field.value).doesNotContain("GK_Short")
    }

    // -- Goalkeeper eligibility -----------------------------------------------

    @Test
    fun `goalkeeper is never selected as Bagre even with the lowest rating`() {
        val embed = buildEmbedWithPlayers(
            goalkeeper("BadGK",     rating = "4.0", secondsPlayed = "900"),
            player("BetterOutfield", rating = "7.0"),
        ).embeds[0]
        val bagre = embed.fields.field("🍍 Bagre da Partida")
        assertThat(bagre.value).doesNotContain("BadGK")
        assertThat(bagre.value).contains("BetterOutfield")
    }

    @Test
    fun `goalkeeper can appear in Top 3 highlights`() {
        val embed = buildEmbedWithPlayers(
            goalkeeper("StarGK",   rating = "9.5", secondsPlayed = "900"),
            player("Outfield1",    rating = "7.0"),
            player("Outfield2",    rating = "6.5"),
        ).embeds[0]
        val destaques = embed.fields.field("🏅 Destaques")
        assertThat(destaques.value).contains("StarGK")
    }

    // -- Bagre da Partida -----------------------------------------------------

    @Test
    fun `outfield player with lowest rating is selected as Bagre`() {
        val embed = buildEmbedWithPlayers(
            player("BadPlayer",  rating = "5.0"),
            player("GoodPlayer", rating = "8.0"),
            player("OkPlayer",   rating = "7.0"),
        ).embeds[0]
        val bagre = embed.fields.field("🍍 Bagre da Partida")
        assertThat(bagre.value).contains("BadPlayer")
        assertThat(bagre.value).doesNotContain("GoodPlayer")
        assertThat(bagre.value).doesNotContain("OkPlayer")
    }

    @Test
    fun `pass percentage and missed passes are calculated correctly in Bagre`() {
        val embed = buildEmbedWithPlayers(
            player("Bagre", rating = "5.0", passAttempts = "21", passesMade = "15"),
        ).embeds[0]
        val text = embed.fields.field("🍍 Bagre da Partida").value
        // 15 * 100 / 21 = 71 (integer division)
        assertThat(text).contains("15/21")
        assertThat(text).contains("71%")
        assertThat(text).contains("Passes errados: 6")
    }

    @Test
    fun `tackle percentage and missed tackles are calculated correctly in Bagre`() {
        val embed = buildEmbedWithPlayers(
            player("Bagre", rating = "5.0", tackleAttempts = "14", tacklesMade = "2"),
        ).embeds[0]
        val text = embed.fields.field("🍍 Bagre da Partida").value
        // 2 * 100 / 14 = 14 (integer division)
        assertThat(text).contains("2/14")
        assertThat(text).contains("14%")
        assertThat(text).contains("Desarmes perdidos: 12")
    }

    @Test
    fun `zero pass attempts means no pass lines in Bagre`() {
        val embed = buildEmbedWithPlayers(
            player("Bagre", rating = "5.0", passAttempts = "0", passesMade = "0"),
        ).embeds[0]
        val text = embed.fields.field("🍍 Bagre da Partida").value
        assertThat(text).doesNotContain("Passes:")
        assertThat(text).doesNotContain("Passes errados")
    }

    @Test
    fun `zero tackle attempts means no tackle lines in Bagre`() {
        val embed = buildEmbedWithPlayers(
            player("Bagre", rating = "5.0", tackleAttempts = "0", tacklesMade = "0"),
        ).embeds[0]
        val text = embed.fields.field("🍍 Bagre da Partida").value
        assertThat(text).doesNotContain("Desarmes:")
        assertThat(text).doesNotContain("Desarmes perdidos")
    }

    @Test
    fun `shots section is shown in Bagre when player had shots`() {
        val embed = buildEmbedWithPlayers(
            player("Bagre", rating = "5.0", shots = "3", goals = "0"),
        ).embeds[0]
        val text = embed.fields.field("🍍 Bagre da Partida").value
        assertThat(text).contains("Finalizações: 3")
        assertThat(text).contains("Gols: 0")
    }

    @Test
    fun `shots section is omitted from Bagre when player had no shots`() {
        val embed = buildEmbedWithPlayers(
            player("Bagre", rating = "5.0", shots = "0"),
        ).embeds[0]
        val text = embed.fields.field("🍍 Bagre da Partida").value
        assertThat(text).doesNotContain("Finalizações")
        assertThat(text).doesNotContain("Gols:")
    }

    // -- Team average ---------------------------------------------------------

    @Test
    fun `team average excludes players with zero seconds played`() {
        val embed = buildEmbedWithPlayers(
            player("Active",   rating = "6.0", secondsPlayed = "900"),
            player("Inactive", rating = "9.0", secondsPlayed = "0"),
        ).embeds[0]
        // Only Active (6.0) is counted; if Inactive were included average would be 7.50
        assertThat(embed.fields.field("⭐ Média do time").value).isEqualTo("6.00")
    }

    @Test
    fun `goalkeeper contributes to team average when secondsPlayed is greater than zero`() {
        val embed = buildEmbedWithPlayers(
            goalkeeper("GK",       rating = "8.0", secondsPlayed = "900"),
            player("Outfield",     rating = "6.0", secondsPlayed = "900"),
        ).embeds[0]
        // (8.0 + 6.0) / 2 = 7.0
        assertThat(embed.fields.field("⭐ Média do time").value).isEqualTo("7.00")
    }

    // -- Helpers --------------------------------------------------------------

    private fun buildEmbed(
        matchId: String = "123",
        ourName: String = "Our Club",
        oppName: String = "Opp Club",
        ourScore: String = "1",
        oppScore: String = "0",
        ourResult: String = "1",
    ): DiscordPayload {
        val match = MatchResponse(
            matchId   = matchId,
            timestamp = 1718500000L,
            matchType = "leagueMatch",
            clubs = mapOf(
                ourClubId to ClubMatchEntry(
                    details = ClubDetails(name = ourName),
                    score   = ourScore,
                    result  = ourResult,
                ),
                "99999" to ClubMatchEntry(
                    details = ClubDetails(name = oppName),
                    score   = oppScore,
                    result  = if (ourResult == "1") "0" else "1",
                ),
            ),
            players = emptyMap(),
        )
        return DiscordEmbedBuilder.build(match, ourClubId, zone)
    }

    private fun buildEmbedWithPlayers(vararg players: PlayerEntry): DiscordPayload {
        val match = MatchResponse(
            matchId   = "42",
            timestamp = 1718500000L,
            clubs = mapOf(
                ourClubId to ClubMatchEntry(details = ClubDetails(name = "Our Club"), score = "2", result = "1"),
                "99999"   to ClubMatchEntry(details = ClubDetails(name = "Opp"),      score = "0", result = "0"),
            ),
            players = mapOf(ourClubId to players.associateBy { it.playerName ?: "p" }),
        )
        return DiscordEmbedBuilder.build(match, ourClubId, zone)
    }

    /** Outfield player — pos is null (not "goalkeeper"), so always eligible for Bagre. */
    private fun player(
        name: String,
        goals: String?         = "0",
        assists: String?        = "0",
        rating: String?         = "7.0",
        mom: String?            = "0",
        secondsPlayed: String?  = "900",
        shots: String?          = null,
        passAttempts: String?   = null,
        passesMade: String?     = null,
        tackleAttempts: String? = null,
        tacklesMade: String?    = null,
        redCards: String?       = null,
    ) = PlayerEntry(
        playerName    = name,
        position      = null,
        goals         = goals,
        assists       = assists,
        rating        = rating,
        manOfTheMatch = mom,
        secondsPlayed = secondsPlayed,
        shots         = shots,
        passAttempts  = passAttempts,
        passesMade    = passesMade,
        tackleAttempts = tackleAttempts,
        tacklesMade   = tacklesMade,
        redCards      = redCards,
    )

    /** Goalkeeper — pos = "goalkeeper", ineligible for Bagre. */
    private fun goalkeeper(
        name: String,
        saves: String?         = "0",
        rating: String?        = "7.0",
        goalsConceded: String? = "0",
        secondsPlayed: String? = "900",
        mom: String?           = "0",
    ) = PlayerEntry(
        playerName    = name,
        position      = "goalkeeper",
        rating        = rating,
        manOfTheMatch = mom,
        secondsPlayed = secondsPlayed,
        saves         = saves,
        goalsConceded = goalsConceded,
    )

    private fun List<EmbedField>.field(name: String) =
        firstOrNull { it.name == name } ?: error("Field '$name' not found in ${map { it.name }}")

    private fun List<EmbedField>.fieldOrNull(name: String) = firstOrNull { it.name == name }
}
