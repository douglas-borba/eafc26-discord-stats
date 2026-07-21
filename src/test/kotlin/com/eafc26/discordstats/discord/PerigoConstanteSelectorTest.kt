package com.eafc26.discordstats.discord

import com.eafc26.discordstats.ea.model.PlayerEntry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PerigoConstanteSelectorTest {

    @Test
    fun `selects player with most shots`() {
        val players = listOf(
            player("MostShots", shots = "8", goals = "2"),
            player("FewerShots", shots = "4", goals = "1"),
        )
        val result = PerigoConstanteSelector.select(players)
        assertThat(result).isNotNull
        assertThat(result!!.player.playerName).isEqualTo("MostShots")
    }

    @Test
    fun `requires minimum 3 shots`() {
        val players = listOf(
            player("TooFew", shots = "2", goals = "1"),
        )
        val result = PerigoConstanteSelector.select(players)
        assertThat(result).isNull()
    }

    @Test
    fun `exactly 3 shots qualifies`() {
        val players = listOf(
            player("Enough", shots = "3", goals = "0"),
        )
        val result = PerigoConstanteSelector.select(players)
        assertThat(result).isNotNull
        assertThat(result!!.player.playerName).isEqualTo("Enough")
    }

    @Test
    fun `returns null for empty list`() {
        val result = PerigoConstanteSelector.select(emptyList())
        assertThat(result).isNull()
    }

    @Test
    fun `returns correct stats`() {
        val players = listOf(
            player("Shooter", shots = "6", goals = "2"),
        )
        val result = PerigoConstanteSelector.select(players)
        assertThat(result).isNotNull
        assertThat(result!!.shots).isEqualTo(6)
        assertThat(result.goals).isEqualTo(2)
    }

    @Test
    fun `efficient when conversion rate is 75 percent or higher`() {
        val players = listOf(
            player("Efficient", shots = "4", goals = "3"), // 75%
        )
        val result = PerigoConstanteSelector.select(players)
        assertThat(result).isNotNull
        assertThat(result!!.efficient).isTrue()
    }

    @Test
    fun `not efficient when conversion below 75 percent`() {
        val players = listOf(
            player("Inefficient", shots = "5", goals = "2"), // 40%
        )
        val result = PerigoConstanteSelector.select(players)
        assertThat(result).isNotNull
        assertThat(result!!.efficient).isFalse()
    }

    @Test
    fun `not efficient when low conversion and few goals`() {
        val players = listOf(
            player("Inefficient", shots = "10", goals = "1"), // 10%
        )
        val result = PerigoConstanteSelector.select(players)
        assertThat(result).isNotNull
        assertThat(result!!.efficient).isFalse()
    }

    @Test
    fun `not efficient when zero goals`() {
        val players = listOf(
            player("NoGoals", shots = "5", goals = "0"),
        )
        val result = PerigoConstanteSelector.select(players)
        assertThat(result).isNotNull
        assertThat(result!!.efficient).isFalse()
    }

    @Test
    fun `uses goals as tiebreaker for same shots`() {
        val players = listOf(
            player("MoreGoals", shots = "5", goals = "3"),
            player("FewerGoals", shots = "5", goals = "1"),
        )
        val result = PerigoConstanteSelector.select(players)
        assertThat(result).isNotNull
        assertThat(result!!.player.playerName).isEqualTo("MoreGoals")
    }

    @Test
    fun `ignores players without shot data`() {
        val players = listOf(
            player("NoData", shots = null, goals = "0"),
            player("HasData", shots = "4", goals = "1"),
        )
        val result = PerigoConstanteSelector.select(players)
        assertThat(result).isNotNull
        assertThat(result!!.player.playerName).isEqualTo("HasData")
    }

    @Test
    fun `match 874612175930485 produces no Constant Danger winner - max shots is 2`() {
        // No player reached the 3-shot threshold; highest was 2 (Guilherme_cruzz, dbeng_bass, joaoborba07)
        val players = listOf(
            player("Guilherme_cruzz", shots = "2"),
            player("dbeng_bass",      shots = "2"),
            player("swegher",         shots = "0"),
            player("joaoborba07",     shots = "2"),
            player("paulorodrigues0", shots = "0"),
        )
        assertThat(PerigoConstanteSelector.select(players)).isNull()
    }

    private fun player(
        name: String,
        shots: String? = null,
        goals: String? = "0",
    ) = PlayerEntry(
        playerName = name,
        position = null,
        shots = shots,
        goals = goals,
        secondsPlayed = "900",
    )
}

