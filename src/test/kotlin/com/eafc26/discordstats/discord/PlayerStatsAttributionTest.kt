package com.eafc26.discordstats.discord

import com.eafc26.discordstats.ea.model.ClubDetails
import com.eafc26.discordstats.ea.model.ClubMatchEntry
import com.eafc26.discordstats.ea.model.MatchResponse
import com.eafc26.discordstats.ea.model.PlayerEntry
import com.eafc26.discordstats.presentation.MatchSummaryBuilder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.ZoneId

/**
 * Regression tests for player statistics attribution.
 * 
 * Tests verify that:
 * - Each player's goals and assists are correctly attributed to their name
 * - Statistics are not mixed between players
 * - displayName() is used consistently
 */
class PlayerStatsAttributionTest {

    private val ourClubId = "1104972"
    private val oppClubId = "99999"
    private val zone = ZoneId.of("America/Sao_Paulo")

    /**
     * Regression test for the dbeng_bass/Guilherme_cruzz scenario.
     * 
     * Expected:
     * - dbeng_bass: 1 goal, 1 assist
     * - Guilherme_cruzz: 0 goals, 0 assists
     * 
     * Previously observed bug: stats were incorrectly attributed.
     */
    @Test
    fun `Discord embed correctly credits dbeng_bass with goal and assist`() {
        val match = createMatchWithPlayers(
            "dbeng_bass_id" to player("dbeng_bass", goals = "1", assists = "1", rating = "7.5"),
            "guilherme_cruzz_id" to player("Guilherme_cruzz", goals = "0", assists = "0", rating = "6.8"),
            "other_player_id" to player("OtherPlayer", goals = "2", assists = "1", rating = "8.0"),
        )

        val payload = DiscordEmbedBuilder.build(match, ourClubId, zone)
        val embed = payload.embeds[0]
        
        val goalsField = embed.fields.firstOrNull { it.name == "⚽ GOLS" }
        val assistsField = embed.fields.firstOrNull { it.name == "🎯 ASSISTÊNCIAS" }

        // dbeng_bass should appear in goals
        assertThat(goalsField?.value).contains("dbeng_bass")
        assertThat(goalsField?.value).contains("×1")
        
        // dbeng_bass should appear in assists
        assertThat(assistsField?.value).contains("dbeng_bass")
        
        // Guilherme_cruzz should NOT appear in goals (0 goals)
        assertThat(goalsField?.value).doesNotContain("Guilherme_cruzz")
        
        // Guilherme_cruzz should NOT appear in assists (0 assists)
        assertThat(assistsField?.value).doesNotContain("Guilherme_cruzz")
    }

    @Test
    fun `Match Card correctly credits dbeng_bass with goal and assist`() {
        val match = createMatchWithPlayers(
            "dbeng_bass_id" to player("dbeng_bass", goals = "1", assists = "1", rating = "7.5"),
            "guilherme_cruzz_id" to player("Guilherme_cruzz", goals = "0", assists = "0", rating = "6.8"),
        )

        val presentation = MatchSummaryBuilder.build(match, ourClubId, zone)

        // Goals section
        assertThat(presentation.goals).isNotNull
        val scorers = presentation.goals!!.scorers
        assertThat(scorers.any { it.name == "dbeng_bass" && it.count == 1 }).isTrue()
        assertThat(scorers.none { it.name == "Guilherme_cruzz" }).isTrue()

        // Assists section
        assertThat(presentation.assists).isNotNull
        val assisters = presentation.assists!!.assisters
        assertThat(assisters.any { it.name == "dbeng_bass" && it.count == 1 }).isTrue()
        assertThat(assisters.none { it.name == "Guilherme_cruzz" }).isTrue()
    }

    @Test
    fun `player identity is preserved through all transformations`() {
        val match = createMatchWithPlayers(
            "player_A" to player("PlayerA", goals = "3", assists = "0", rating = "9.0"),
            "player_B" to player("PlayerB", goals = "0", assists = "3", rating = "8.0"),
            "player_C" to player("PlayerC", goals = "1", assists = "1", rating = "7.0"),
        )

        val payload = DiscordEmbedBuilder.build(match, ourClubId, zone)
        val embed = payload.embeds[0]
        
        val goalsField = embed.fields.firstOrNull { it.name == "⚽ GOLS" }
        val assistsField = embed.fields.firstOrNull { it.name == "🎯 ASSISTÊNCIAS" }

        // PlayerA: 3 goals, 0 assists
        assertThat(goalsField?.value).contains("PlayerA")
        assertThat(goalsField?.value).contains("×3")
        assertThat(assistsField?.value).doesNotContain("PlayerA")

        // PlayerB: 0 goals, 3 assists
        assertThat(goalsField?.value).doesNotContain("PlayerB")
        assertThat(assistsField?.value).contains("PlayerB")
        assertThat(assistsField?.value).contains("×3")

        // PlayerC: 1 goal, 1 assist
        assertThat(goalsField?.value).contains("PlayerC")
        assertThat(assistsField?.value).contains("PlayerC")
    }

    @Test
    fun `player with highest goals appears first in goals section`() {
        val match = createMatchWithPlayers(
            "low_scorer" to player("LowScorer", goals = "1", rating = "6.0"),
            "high_scorer" to player("HighScorer", goals = "3", rating = "8.0"),
            "mid_scorer" to player("MidScorer", goals = "2", rating = "7.0"),
        )

        val payload = DiscordEmbedBuilder.build(match, ourClubId, zone)
        val goalsField = payload.embeds[0].fields.firstOrNull { it.name == "⚽ GOLS" }

        // HighScorer (3 goals) should appear before MidScorer (2) and LowScorer (1)
        val text = goalsField?.value ?: ""
        val highPos = text.indexOf("HighScorer")
        val midPos = text.indexOf("MidScorer")
        val lowPos = text.indexOf("LowScorer")
        
        assertThat(highPos).isLessThan(midPos)
        assertThat(midPos).isLessThan(lowPos)
    }

    @Test
    fun `displayName is used for presentation in Discord`() {
        // Player with Pro player name that needs normalization
        val match = createMatchWithPlayers(
            "player_id" to PlayerEntry(
                playerName = "  João Silva  ",  // Has extra whitespace
                goals = "1",
                assists = "0",
                rating = "7.0",
                secondsPlayed = "5400",
            )
        )

        val payload = DiscordEmbedBuilder.build(match, ourClubId, zone)
        val goalsField = payload.embeds[0].fields.firstOrNull { it.name == "⚽ GOLS" }

        // Should use trimmed name
        assertThat(goalsField?.value).contains("João Silva")
        assertThat(goalsField?.value).doesNotContain("  João Silva  ")
    }

    private fun createMatchWithPlayers(vararg players: Pair<String, PlayerEntry>): MatchResponse {
        return MatchResponse(
            matchId = "test-match-123",
            timestamp = 1721267100L,  // July 18, 2026
            matchType = "leagueMatch",
            clubs = mapOf(
                ourClubId to ClubMatchEntry(
                    details = ClubDetails(name = "Associação BF"),
                    score = "3",
                    result = "0"
                ),
                oppClubId to ClubMatchEntry(
                    details = ClubDetails(name = "Agrobola FC"),
                    score = "4",
                    result = "1"
                ),
            ),
            players = mapOf(
                ourClubId to players.toMap(),
                oppClubId to emptyMap(),
            ),
        )
    }

    private fun player(
        name: String,
        goals: String = "0",
        assists: String = "0",
        rating: String = "7.0",
        shots: String = "0",
        passAttempts: String = "20",
        passesMade: String = "15",
        tackleAttempts: String = "5",
        tacklesMade: String = "3",
        secondsPlayed: String = "5400",
    ) = PlayerEntry(
        playerName = name,
        position = null,
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

