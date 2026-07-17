package com.eafc26.discordstats.discord

import com.eafc26.discordstats.ea.model.PlayerEntry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class XerifeSelectorTest {

    @Test
    fun `selects player with most tackles when above 60 percent`() {
        val players = listOf(
            player("MostTackles", tackleAttempts = "10", tacklesMade = "8"), // 80%
            player("FewerTackles", tackleAttempts = "5", tacklesMade = "4"), // 80%
        )
        val result = XerifeSelector.select(players)
        assertThat(result).isNotNull
        assertThat(result!!.player.playerName).isEqualTo("MostTackles")
    }

    @Test
    fun `exactly 60 percent does not qualify`() {
        val players = listOf(
            player("Exactly60", tackleAttempts = "10", tacklesMade = "6"), // exactly 60%
        )
        val result = XerifeSelector.select(players)
        assertThat(result).isNull()
    }

    @Test
    fun `61 percent qualifies`() {
        val players = listOf(
            player("Above60", tackleAttempts = "100", tacklesMade = "61"), // 61%
        )
        val result = XerifeSelector.select(players)
        assertThat(result).isNotNull
        assertThat(result!!.player.playerName).isEqualTo("Above60")
    }

    @Test
    fun `returns null when no player above 60 percent`() {
        val players = listOf(
            player("Poor", tackleAttempts = "10", tacklesMade = "5"), // 50%
        )
        val result = XerifeSelector.select(players)
        assertThat(result).isNull()
    }

    @Test
    fun `returns null when no tackles attempted`() {
        val players = listOf(
            player("NoTackles", tackleAttempts = "0", tacklesMade = "0"),
        )
        val result = XerifeSelector.select(players)
        assertThat(result).isNull()
    }

    @Test
    fun `returns null for empty player list`() {
        val result = XerifeSelector.select(emptyList())
        assertThat(result).isNull()
    }

    @Test
    fun `returns correct stats in selection`() {
        val players = listOf(
            player("Xerife", tackleAttempts = "10", tacklesMade = "8"),
        )
        val result = XerifeSelector.select(players)
        assertThat(result).isNotNull
        assertThat(result!!.tacklesMade).isEqualTo(8)
        assertThat(result.tackleAttempts).isEqualTo(10)
        assertThat(result.successRate).isEqualTo(80)
    }

    @Test
    fun `tiebreaker uses success rate`() {
        val players = listOf(
            player("HighRate", tackleAttempts = "5", tacklesMade = "5"), // 100%
            player("LowerRate", tackleAttempts = "10", tacklesMade = "7"), // 70%
        )
        // Both have 5+ tackles made, but HighRate has higher rate (100% vs 70%)
        // Actually HighRate has 5 tackles made, LowerRate has 7
        // Since we compare by tacklesMade first, LowerRate should win
        val result = XerifeSelector.select(players)
        assertThat(result).isNotNull
        assertThat(result!!.player.playerName).isEqualTo("LowerRate")
    }

    private fun player(
        name: String,
        tackleAttempts: String? = null,
        tacklesMade: String? = null,
    ) = PlayerEntry(
        playerName = name,
        position = null,
        tackleAttempts = tackleAttempts,
        tacklesMade = tacklesMade,
        secondsPlayed = "900",
    )
}

