package com.eafc26.discordstats.discord

import com.eafc26.discordstats.ea.model.PlayerEntry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CraqueSelectorTest {

    @Test
    fun `selects EA-designated MVP when present`() {
        val players = listOf(
            player("MVP", rating = "8.0", mom = "1"),
            player("Higher", rating = "9.0", mom = "0"),
        )
        val result = CraqueSelector.select(players)
        assertThat(result).isNotNull
        assertThat(result!!.player.playerName).isEqualTo("MVP")
        assertThat(result.reason).contains("Craque da Partida (EA)")
    }

    @Test
    fun `selects highest rated when no EA MVP`() {
        val players = listOf(
            player("Highest", rating = "9.0"),
            player("Lower", rating = "7.0"),
        )
        val result = CraqueSelector.select(players)
        assertThat(result).isNotNull
        assertThat(result!!.player.playerName).isEqualTo("Highest")
        assertThat(result.reason).contains("Melhor nota da partida")
    }

    @Test
    fun `reason includes goals and assists`() {
        val players = listOf(
            player("Scorer", rating = "8.0", goals = "2", assists = "1"),
        )
        val result = CraqueSelector.select(players)
        assertThat(result).isNotNull
        assertThat(result!!.reason).contains("2 gols")
        assertThat(result.reason).contains("1 assistência")
    }

    @Test
    fun `uses goals as tiebreaker for same rating`() {
        val players = listOf(
            player("MoreGoals", rating = "8.0", goals = "2", assists = "0"),
            player("FewerGoals", rating = "8.0", goals = "1", assists = "0"),
        )
        val result = CraqueSelector.select(players)
        assertThat(result).isNotNull
        assertThat(result!!.player.playerName).isEqualTo("MoreGoals")
    }

    @Test
    fun `uses assists as tiebreaker after goals`() {
        val players = listOf(
            player("MoreAssists", rating = "8.0", goals = "1", assists = "2"),
            player("FewerAssists", rating = "8.0", goals = "1", assists = "1"),
        )
        val result = CraqueSelector.select(players)
        assertThat(result).isNotNull
        assertThat(result!!.player.playerName).isEqualTo("MoreAssists")
    }

    @Test
    fun `returns null when no players`() {
        val result = CraqueSelector.select(emptyList())
        assertThat(result).isNull()
    }

    @Test
    fun `returns null when no rated players and no MVP`() {
        val players = listOf(
            player("NoRating", rating = null),
        )
        val result = CraqueSelector.select(players)
        assertThat(result).isNull()
    }

    @Test
    fun `reason includes rating formatted with comma`() {
        val players = listOf(
            player("Player", rating = "8.5"),
        )
        val result = CraqueSelector.select(players)
        assertThat(result).isNotNull
        assertThat(result!!.reason).contains("Nota 8,50")
    }

    private fun player(
        name: String,
        rating: String? = "7.0",
        goals: String? = "0",
        assists: String? = "0",
        mom: String? = "0",
    ) = PlayerEntry(
        playerName = name,
        position = null,
        rating = rating,
        goals = goals,
        assists = assists,
        manOfTheMatch = mom,
        secondsPlayed = "900",
    )
}

