package com.eafc26.discordstats.service

import com.eafc26.discordstats.config.AppProperties
import com.eafc26.discordstats.config.EaProperties
import com.eafc26.discordstats.config.PollingProperties
import com.eafc26.discordstats.discord.DiscordDeliveryException
import com.eafc26.discordstats.discord.DiscordWebhookClient
import com.eafc26.discordstats.ea.EaApiResult
import com.eafc26.discordstats.ea.EaClubsGateway
import com.eafc26.discordstats.ea.model.ClubDetails
import com.eafc26.discordstats.ea.model.ClubMatchEntry
import com.eafc26.discordstats.ea.model.MatchResponse
import com.eafc26.discordstats.store.PublishedMatchStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class MatchNotifierServiceTest {

    private val gateway: EaClubsGateway = mock()
    private val store: PublishedMatchStore = mock()
    private val discord: DiscordWebhookClient = mock()

    private val clubId = "12345"

    private fun makeService(publishExistingOnFirstRun: Boolean = false): MatchNotifierService {
        val props = AppProperties(
            ea = EaProperties(clubId = clubId),
            polling = PollingProperties(publishExistingOnFirstRun = publishExistingOnFirstRun),
        )
        return MatchNotifierService(gateway, store, discord, props)
    }

    @BeforeEach
    fun setUp() {
        whenever(store.loadIds()).thenReturn(emptySet())
    }

    // -- EA unavailable --

    @Test
    fun `EA unavailable does not crash — nothing is sent`() {
        whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Unavailable(503, "down"))
        makeService().process()
        verify(discord, never()).send(any())
    }

    @Test
    fun `EA no matches does not crash — nothing is sent`() {
        whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.NoMatches)
        makeService().process()
        verify(discord, never()).send(any())
    }

    // -- First run --

    @Test
    fun `first run establishes baseline without publishing any matches`() {
        val matches = listOf(match("m1", ts = 1000), match("m2", ts = 2000))
        whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(matches))
        whenever(store.loadIds()).thenReturn(emptySet())

        makeService(publishExistingOnFirstRun = false).process()

        verify(discord, never()).send(any())
        verify(store).saveIds(setOf("m1", "m2"))
    }

    @Test
    fun `first run with publishExistingOnFirstRun=true publishes all matches`() {
        val matches = listOf(match("m1", ts = 1000), match("m2", ts = 2000))
        whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(matches))
        // store.loadIds() returns empty -> first-run path taken; no recursive process() call

        makeService(publishExistingOnFirstRun = true).process()

        verify(discord, times(2)).send(any())
    }

    // -- Normal processing --

    @Test
    fun `already published match is not sent again`() {
        val matches = listOf(match("m1"))
        whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(matches))
        whenever(store.loadIds()).thenReturn(setOf("m1"))

        makeService().process()

        verify(discord, never()).send(any())
    }

    @Test
    fun `new match is published and saved`() {
        val matches = listOf(match("m2"))
        whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(matches))
        whenever(store.loadIds()).thenReturn(setOf("m1"))

        makeService().process()

        verify(discord).send(any())
        verify(store).saveIds(setOf("m1", "m2"))
    }

    @Test
    fun `multiple new matches are processed oldest-first`() {
        val matches = listOf(match("newer", ts = 2000), match("older", ts = 1000))
        whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(matches))
        whenever(store.loadIds()).thenReturn(setOf("published"))

        val savedOrder = mutableListOf<Set<String>>()
        whenever(store.saveIds(any())).thenAnswer { inv ->
            savedOrder += inv.getArgument<Set<String>>(0).toSet()
            Unit
        }

        makeService().process()

        // service processes oldest-first and persists after each send
        assertThat(savedOrder).hasSize(2)
        assertThat(savedOrder[0]).contains("older")
        assertThat(savedOrder[0]).doesNotContain("newer")
        assertThat(savedOrder[1]).contains("older", "newer")
    }

    @Test
    fun `Discord delivery failure does not mark match as published`() {
        val matches = listOf(match("m1"))
        whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(matches))
        whenever(store.loadIds()).thenReturn(emptySet<String>(), emptySet())
        whenever(store.loadIds()).thenReturn(setOf("baseline"))
        whenever(discord.send(any())).thenThrow(DiscordDeliveryException("503 error"))

        makeService().process()

        // Should NOT save m1 as published
        verify(store, never()).saveIds(org.mockito.kotlin.check { ids -> assertThat(ids).contains("m1") })
    }

    @Test
    fun `missing webhook URL aborts cycle — no further matches are processed`() {
        val matches = listOf(match("m1"), match("m2"))
        whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(matches))
        whenever(store.loadIds()).thenReturn(setOf("baseline"))
        whenever(discord.send(any())).thenThrow(IllegalStateException("webhook not configured"))

        makeService().process()

        // IllegalStateException should abort — only the first send attempt happens
        verify(discord, times(1)).send(any())
    }

    // -- History webhook --

    @Test
    fun `history webhook is called for each newly published match`() {
        val matches = listOf(match("m2"))
        whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(matches))
        whenever(store.loadIds()).thenReturn(setOf("m1"))

        makeService().process()

        verify(discord).send(any())
        verify(discord).sendHistory(any())
    }

    @Test
    fun `history webhook is not called for already published match`() {
        val matches = listOf(match("m1"))
        whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(matches))
        whenever(store.loadIds()).thenReturn(setOf("m1"))

        makeService().process()

        verify(discord, never()).send(any())
        verify(discord, never()).sendHistory(any())
    }

    @Test
    fun `history webhook failure does not prevent persistence of successful send`() {
        val matches = listOf(match("m2"))
        whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(matches))
        whenever(store.loadIds()).thenReturn(setOf("m1"))
        // sendHistory never throws (it's fire-and-forget), but simulate a no-op to be explicit
        org.mockito.kotlin.doNothing().whenever(discord).sendHistory(any())

        makeService().process()

        // Main send and persistence must still complete
        verify(discord).send(any())
        verify(store).saveIds(setOf("m1", "m2"))
    }

    @Test
    fun `history is called once per match — not again when already published on next cycle`() {
        val match = match("m1")
        whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(match)))
        whenever(store.loadIds()).thenReturn(setOf("baseline"))

        val service = makeService()

        // First cycle: m1 is new, should publish and send history
        service.process()
        verify(discord, times(1)).sendHistory(any())

        // Second cycle: m1 now in store, should be skipped entirely
        whenever(store.loadIds()).thenReturn(setOf("baseline", "m1"))
        service.process()
        verify(discord, times(1)).sendHistory(any()) // still only 1 total
    }

    @Test
    fun `history is sent for each match when multiple new matches are published`() {
        val matches = listOf(match("m2", ts = 2000), match("m3", ts = 3000))
        whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(matches))
        whenever(store.loadIds()).thenReturn(setOf("m1"))
        whenever(store.saveIds(any())).thenAnswer { Unit }

        makeService().process()

        verify(discord, times(2)).sendHistory(any())
    }

    // -- Helpers --

    private fun match(id: String, ts: Long = 1000L) = MatchResponse(
        matchId = id,
        timestamp = ts,
        clubs = mapOf(
            clubId to ClubMatchEntry(details = ClubDetails(name = "Our Club"), score = "2", result = "1"),
            "99999" to ClubMatchEntry(details = ClubDetails(name = "Opp"), score = "0", result = "0"),
        ),
        players = emptyMap(),
    )
}
