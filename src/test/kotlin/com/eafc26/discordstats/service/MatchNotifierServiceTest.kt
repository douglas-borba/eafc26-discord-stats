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

        val order = mutableListOf<String>()
        whenever(discord.send(any())).thenAnswer { inv ->
            val payload = inv.getArgument<com.eafc26.discordstats.discord.DiscordPayload>(0)
            // Footer text is "Match ID: <matchId>"
            order += payload.embeds[0].footer?.text ?: ""
            Unit
        }

        makeService().process()

        assertThat(order).hasSize(2)
        assertThat(order[0]).contains("older")
        assertThat(order[1]).contains("newer")
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
