package com.eafc26.discordstats.web

import com.eafc26.discordstats.domain.match.PlayerId
import com.eafc26.discordstats.profile.PlayerProfile
import com.eafc26.discordstats.profile.PlayerProfileIndexEntry
import com.eafc26.discordstats.service.PlayerProfileService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant

class PlayerProfileControllerTest {
    private lateinit var service: PlayerProfileService
    private lateinit var controller: PlayerProfileController

    @BeforeEach
    fun setUp() {
        service = mock()
        controller = PlayerProfileController(service)
    }

    @Test
    fun `player page serves dedicated resource`() {
        assertThat(controller.playersPage().body!!.path).isEqualTo("players.html")
        verifyNoMoreInteractions(service)
    }

    @Test
    fun `empty player index returns explicit empty state`() {
        whenever(service.listPlayers()).thenReturn(emptyList())

        val response = controller.listPlayers().block()!!

        assertThat(response.body!!.status).isEqualTo("empty")
        assertThat(response.body!!.players).isEmpty()
    }

    @Test
    fun `player index exposes presentation contract`() {
        whenever(service.listPlayers()).thenReturn(
            listOf(PlayerProfileIndexEntry(PlayerId("p1"), "Player", 3, Instant.parse("2026-07-03T10:00:00Z")))
        )

        val response = controller.listPlayers().block()!!

        assertThat(response.body!!.status).isEqualTo("success")
        assertThat(response.body!!.players.single().playerId).isEqualTo("p1")
        assertThat(response.body!!.players.single().matchCount).isEqualTo(3)
    }

    @Test
    fun `existing player profile is presented without additional data access`() {
        val profile = PlayerProfile(
            playerId = PlayerId("p1"),
            displayName = "Player",
            matchCount = 2,
            wins = 1,
            draws = 1,
            losses = 0,
            averageRating = BigDecimal("7.50"),
            ratedMatchCount = 2,
            goals = 2,
            assists = 1,
            craques = 1,
            bagres = 0,
            xerifes = 1,
            redCards = 0,
            recentMatches = emptyList(),
        )
        whenever(service.findById(PlayerId("p1"))).thenReturn(profile)

        val response = controller.getProfile("p1").block()!!

        assertThat(response.body!!.profile!!.name).isEqualTo("Player")
        assertThat(response.body!!.profile!!.averageRating).isEqualByComparingTo("7.50")
        assertThat(response.body!!.profile!!.craques).isEqualTo(1)
        verify(service).findById(PlayerId("p1"))
        verifyNoMoreInteractions(service)
    }

    @Test
    fun `unknown player returns not found`() {
        whenever(service.findById(PlayerId("missing"))).thenReturn(null)

        val response = controller.getProfile("missing").block()!!

        assertThat(response.statusCode.value()).isEqualTo(404)
        assertThat(response.body!!.status).isEqualTo("not_found")
        assertThat(response.body!!.profile).isNull()
    }
}
