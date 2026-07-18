package com.eafc26.discordstats.discord

import com.eafc26.discordstats.ea.model.PlayerEntry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Tests for BagrePerformanceEvaluator eligibility rules.
 * 
 * Key rule: Players with rating below MIN_BAGRE_RATING (5.0) are excluded.
 */
class BagreEligibilityTest {

    @Test
    fun `player with rating 4_9 is not eligible for Bagre`() {
        val players = listOf(
            player("LowRating", rating = "4.9"),
            player("HighRating", rating = "7.0"),
        )
        
        val result = BagrePerformanceEvaluator.evaluate(players, "match-1", null)
        
        // Should skip the 4.9 player and select the 7.0 player (lowest eligible)
        assertThat(result).isNotNull
        assertThat(result!!.player.playerName).isEqualTo("HighRating")
    }

    @Test
    fun `player with rating 5_0 is eligible for Bagre`() {
        val players = listOf(
            player("ExactThreshold", rating = "5.0"),
            player("HighRating", rating = "7.0"),
        )
        
        val result = BagrePerformanceEvaluator.evaluate(players, "match-1", null)
        
        // Should select the 5.0 player as it meets the threshold
        assertThat(result).isNotNull
        assertThat(result!!.player.playerName).isEqualTo("ExactThreshold")
    }

    @Test
    fun `player with rating above 5_0 is eligible for Bagre`() {
        val players = listOf(
            player("AboveThreshold", rating = "5.5"),
            player("HighRating", rating = "7.0"),
        )
        
        val result = BagrePerformanceEvaluator.evaluate(players, "match-1", null)
        
        assertThat(result).isNotNull
        assertThat(result!!.player.playerName).isEqualTo("AboveThreshold")
    }

    @Test
    fun `lowest eligible player is selected for Bagre`() {
        val players = listOf(
            player("Rating5", rating = "5.0"),
            player("Rating6", rating = "6.0"),
            player("Rating7", rating = "7.0"),
        )
        
        val result = BagrePerformanceEvaluator.evaluate(players, "match-1", null)
        
        assertThat(result).isNotNull
        assertThat(result!!.player.playerName).isEqualTo("Rating5")
    }

    @Test
    fun `no eligible players returns null`() {
        val players = listOf(
            player("VeryLow1", rating = "3.0"),
            player("VeryLow2", rating = "4.5"),
            player("VeryLow3", rating = "4.9"),
        )
        
        val result = BagrePerformanceEvaluator.evaluate(players, "match-1", null)
        
        assertThat(result).isNull()
    }

    @Test
    fun `player below 5_0 cannot be selected even if lowest rating`() {
        val players = listOf(
            player("Disconnected", rating = "2.0"),  // Would be lowest, but ineligible
            player("Normal", rating = "6.5"),
        )
        
        val result = BagrePerformanceEvaluator.evaluate(players, "match-1", null)
        
        assertThat(result).isNotNull
        assertThat(result!!.player.playerName).isEqualTo("Normal")
        assertThat(result.player.rating).isEqualTo("6.5")
    }

    @Test
    fun `MIN_BAGRE_RATING constant is 5_0`() {
        assertThat(BagrePerformanceEvaluator.MIN_BAGRE_RATING).isEqualTo(5.0)
    }

    @Test
    fun `empty player list returns null`() {
        val result = BagrePerformanceEvaluator.evaluate(emptyList(), "match-1", null)
        assertThat(result).isNull()
    }

    @Test
    fun `players with null rating are excluded`() {
        val players = listOf(
            player("NoRating", rating = null),
            player("HasRating", rating = "6.0"),
        )
        
        val result = BagrePerformanceEvaluator.evaluate(players, "match-1", null)
        
        assertThat(result).isNotNull
        assertThat(result!!.player.playerName).isEqualTo("HasRating")
    }

    private fun player(
        name: String,
        rating: String? = "7.0",
        goals: String? = "0",
        assists: String? = "0",
        shots: String? = "0",
        passAttempts: String? = "10",
        passesMade: String? = "8",
        tackleAttempts: String? = "5",
        tacklesMade: String? = "3",
        secondsPlayed: String? = "5400",
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

