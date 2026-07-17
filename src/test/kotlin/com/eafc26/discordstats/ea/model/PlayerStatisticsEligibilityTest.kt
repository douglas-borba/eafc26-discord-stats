package com.eafc26.discordstats.ea.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PlayerStatisticsEligibilityTest {

    private fun player(secondsPlayed: String? = null, rating: String? = null, status: String? = null) =
        PlayerEntry(secondsPlayed = secondsPlayed, rating = rating, status = status)

    private fun eligible(vararg players: PlayerEntry) =
        PlayerStatisticsEligibility.eligiblePlayers(players.toList())

    // -- Exact integer boundary (spec examples) --------------------------------

    @Test
    fun `max 100 - player 90 is eligible`() {
        // 90 * 100 >= 100 * 90 -> 9000 >= 9000 -> eligible
        assertThat(eligible(player("100"), player("90")).map { it.secondsPlayed }).contains("90")
    }

    @Test
    fun `max 100 - player 89 is ineligible`() {
        // 89 * 100 >= 100 * 90 -> 8900 >= 9000 -> ineligible
        assertThat(eligible(player("100"), player("89")).map { it.secondsPlayed }).doesNotContain("89")
    }

    @Test
    fun `max 101 - player 91 is eligible`() {
        // 91 * 100 >= 101 * 90 -> 9100 >= 9090 -> eligible
        assertThat(eligible(player("101"), player("91")).map { it.secondsPlayed }).contains("91")
    }

    @Test
    fun `max 101 - player 90 is ineligible`() {
        // 90 * 100 >= 101 * 90 -> 9000 >= 9090 -> ineligible
        assertThat(eligible(player("101"), player("90")).map { it.secondsPlayed }).doesNotContain("90")
    }

    // -- 90 % threshold spec example ------------------------------------------

    @Test
    fun `spec example - max 5400`() {
        val result = PlayerStatisticsEligibility.eligiblePlayers(listOf(
            player("5400"),  // 100.0 % -> eligible
            player("5320"),  //  98.5 % -> eligible
            player("4860"),  //  90.0 % exactly -> eligible
            player("4800"),  //  88.9 % -> ineligible
            player("3200"),  //  59.3 % -> ineligible
        ))
        assertThat(result.map { it.secondsPlayed })
            .containsExactlyInAnyOrder("5400", "5320", "4860")
    }

    // -- Backward compatibility: no valid positive secondsPlayed ---------------

    @Test
    fun `all values absent - everyone eligible`() {
        val result = eligible(player(null), player(null), player(null))
        assertThat(result).hasSize(3)
    }

    @Test
    fun `all values malformed - everyone eligible`() {
        val result = eligible(player("abc"), player("N/A"), player("--"))
        assertThat(result).hasSize(3)
    }

    @Test
    fun `all values zero - everyone eligible`() {
        // zero is not valid positive, so no valid max -> backward-compat path
        val result = eligible(player("0"), player("0"))
        assertThat(result).hasSize(2)
    }

    @Test
    fun `all values negative - everyone eligible`() {
        val result = eligible(player("-1"), player("-100"))
        assertThat(result).hasSize(2)
    }

    @Test
    fun `mix of null and zero - everyone eligible`() {
        val result = eligible(player(null), player("0"), player(null))
        assertThat(result).hasSize(3)
    }

    // -- Invalid values when at least one valid positive exists ----------------

    @Test
    fun `null secondsPlayed is ineligible when team has valid values`() {
        // null is not a valid positive -> ineligible once a max exists
        val result = eligible(player("5400"), player(null))
        assertThat(result.map { it.secondsPlayed }).containsExactly("5400")
    }

    @Test
    fun `malformed secondsPlayed is ineligible when team has valid values`() {
        val result = eligible(player("5400"), player("abc"))
        assertThat(result.map { it.secondsPlayed }).containsExactly("5400")
    }

    @Test
    fun `zero secondsPlayed is ineligible when team has valid values`() {
        val result = eligible(player("5400"), player("0"))
        assertThat(result.map { it.secondsPlayed }).containsExactly("5400")
    }

    @Test
    fun `negative secondsPlayed is ineligible when team has valid values`() {
        val result = eligible(player("5400"), player("-1"))
        assertThat(result.map { it.secondsPlayed }).containsExactly("5400")
    }

    @Test
    fun `mixture of valid and missing - missing are ineligible`() {
        val result = eligible(player("5400"), player("5400"), player(null), player(null))
        assertThat(result.map { it.secondsPlayed }).containsExactlyInAnyOrder("5400", "5400")
    }

    @Test
    fun `mixture of valid and malformed - malformed are ineligible`() {
        val result = eligible(player("5400"), player("5400"), player("bad"), player(""))
        assertThat(result.map { it.secondsPlayed }).containsExactlyInAnyOrder("5400", "5400")
    }

    // -- Status and rating are never used for eligibility ---------------------

    @Test
    fun `status does not affect eligibility`() {
        val result = eligible(
            player("5400", status = "0"),
            player("5400", status = "2"),
            player("4800", status = "0"),
            player("4800", status = "2"),
        )
        assertThat(result.map { it.secondsPlayed }).containsExactlyInAnyOrder("5400", "5400")
    }

    @Test
    fun `low rating alone never excludes a player`() {
        val result = eligible(
            player("5400", rating = "1.0"),
            player("5400", rating = "0"),
            player("5400", rating = null),
        )
        assertThat(result).hasSize(3)
    }

    // -- Edge cases -----------------------------------------------------------

    @Test
    fun `single player with valid secondsPlayed is always eligible`() {
        assertThat(eligible(player("3600"))).hasSize(1)
    }

    @Test
    fun `single player with null secondsPlayed is eligible - no valid max exists`() {
        // No valid max -> backward-compat -> include all
        assertThat(eligible(player(null))).hasSize(1)
    }

    @Test
    fun `empty player list returns empty`() {
        assertThat(PlayerStatisticsEligibility.eligiblePlayers(emptyList())).isEmpty()
    }
}
