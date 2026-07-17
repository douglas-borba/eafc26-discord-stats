package com.eafc26.discordstats.discord

import com.eafc26.discordstats.ea.model.PlayerEntry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PassePrecisaoSelectorTest {

    @Test
    fun `selects player with highest accuracy`() {
        val players = listOf(
            player("Best", passAttempts = "20", passesMade = "18"), // 90%
            player("Worse", passAttempts = "20", passesMade = "16"), // 80%
        )
        val result = PassePrecisaoSelector.select(players)
        assertThat(result).isNotNull
        assertThat(result!!.player.playerName).isEqualTo("Best")
    }

    @Test
    fun `requires minimum 10 pass attempts`() {
        val players = listOf(
            player("TooFew", passAttempts = "9", passesMade = "9"), // 100% but only 9 attempts
            player("Enough", passAttempts = "10", passesMade = "8"), // 80% with 10 attempts
        )
        val result = PassePrecisaoSelector.select(players)
        assertThat(result).isNotNull
        assertThat(result!!.player.playerName).isEqualTo("Enough")
    }

    @Test
    fun `returns null when no player meets minimum attempts`() {
        val players = listOf(
            player("TooFew", passAttempts = "5", passesMade = "5"),
        )
        val result = PassePrecisaoSelector.select(players)
        assertThat(result).isNull()
    }

    @Test
    fun `returns null for empty list`() {
        val result = PassePrecisaoSelector.select(emptyList())
        assertThat(result).isNull()
    }

    @Test
    fun `uses more attempts as tiebreaker`() {
        val players = listOf(
            player("MoreAttempts", passAttempts = "30", passesMade = "27"), // 90%
            player("FewerAttempts", passAttempts = "20", passesMade = "18"), // 90%
        )
        val result = PassePrecisaoSelector.select(players)
        assertThat(result).isNotNull
        assertThat(result!!.player.playerName).isEqualTo("MoreAttempts")
    }

    @Test
    fun `returns correct stats`() {
        val players = listOf(
            player("Precise", passAttempts = "25", passesMade = "23"),
        )
        val result = PassePrecisaoSelector.select(players)
        assertThat(result).isNotNull
        assertThat(result!!.passesMade).isEqualTo(23)
        assertThat(result.passAttempts).isEqualTo(25)
        assertThat(result.accuracy).isEqualTo(92)
    }

    @Test
    fun `ignores players without pass data`() {
        val players = listOf(
            player("NoData", passAttempts = null, passesMade = null),
            player("HasData", passAttempts = "15", passesMade = "12"),
        )
        val result = PassePrecisaoSelector.select(players)
        assertThat(result).isNotNull
        assertThat(result!!.player.playerName).isEqualTo("HasData")
    }

    private fun player(
        name: String,
        passAttempts: String? = null,
        passesMade: String? = null,
    ) = PlayerEntry(
        playerName = name,
        position = null,
        passAttempts = passAttempts,
        passesMade = passesMade,
        secondsPlayed = "900",
    )
}

