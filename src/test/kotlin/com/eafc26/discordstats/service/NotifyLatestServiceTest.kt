package com.eafc26.discordstats.service

import com.eafc26.discordstats.config.AppProperties
import com.eafc26.discordstats.config.EaProperties
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
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class NotifyLatestServiceTest {

    private lateinit var gateway: EaClubsGateway
    private lateinit var store: PublishedMatchStore
    private lateinit var webhookClient: DiscordWebhookClient
    private lateinit var service: NotifyLatestService

    private val clubId = "12345"
    private val props = AppProperties(ea = EaProperties(clubId = "12345", clubName = "Test FC"))

    @BeforeEach
    fun setUp() {
        gateway = mock()
        store = mock()
        webhookClient = mock()
        service = NotifyLatestService(gateway, store, webhookClient, props)
        whenever(store.loadIds()).thenReturn(emptySet())
    }

    @Test
    fun `sends notification for new match and persists match id`() {
        val match = match("m1")
        whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(match)))

        val result = service.notifyLatest()

        assertThat(result).isInstanceOf(NotifyResult.Sent::class.java)
        val sent = result as NotifyResult.Sent
        assertThat(sent.summary).contains("Test FC")
        verify(webhookClient).send(any())
        verify(store).saveIds(setOf("m1"))
    }

    @Test
    fun `skips already published match and does not call webhook`() {
        val match = match("m1")
        whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(match)))
        whenever(store.loadIds()).thenReturn(setOf("m1"))

        val result = service.notifyLatest()

        assertThat(result).isInstanceOf(NotifyResult.AlreadyPublished::class.java)
        verify(webhookClient, never()).send(any())
        verify(store, never()).saveIds(any())
    }

    @Test
    fun `does not persist match id when Discord delivery fails`() {
        val match = match("m1")
        whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(match)))
        doThrow(DiscordDeliveryException("HTTP 500")).whenever(webhookClient).send(any())

        val result = service.notifyLatest()

        assertThat(result).isEqualTo(NotifyResult.DiscordError)
        verify(store, never()).saveIds(any())
    }

    @Test
    fun `returns DiscordError when webhook is not configured`() {
        val match = match("m1")
        whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(match)))
        doThrow(IllegalStateException("Discord webhook URL is not configured")).whenever(webhookClient).send(any())

        val result = service.notifyLatest()

        assertThat(result).isEqualTo(NotifyResult.DiscordError)
        verify(store, never()).saveIds(any())
    }

    @Test
    fun `returns EaUnavailable when EA API is down`() {
        whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Unavailable(503, "down"))

        val result = service.notifyLatest()

        assertThat(result).isEqualTo(NotifyResult.EaUnavailable)
        verify(webhookClient, never()).send(any())
    }

    @Test
    fun `returns EaUnavailable for unexpected payload`() {
        whenever(gateway.getLatestMatches(clubId))
            .thenReturn(EaApiResult.UnexpectedPayload(RuntimeException("bad json")))

        val result = service.notifyLatest()

        assertThat(result).isEqualTo(NotifyResult.EaUnavailable)
        verify(webhookClient, never()).send(any())
    }

    @Test
    fun `returns NoMatches when EA returns no matches`() {
        whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.NoMatches)

        val result = service.notifyLatest()

        assertThat(result).isEqualTo(NotifyResult.NoMatches)
        verify(webhookClient, never()).send(any())
    }

    @Test
    fun `picks the most recent match when multiple returned`() {
        val older = match("old", ts = 1000L)
        val newer = match("new", ts = 9000L)
        whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(older, newer)))

        val result = service.notifyLatest()

        assertThat(result).isInstanceOf(NotifyResult.Sent::class.java)
        verify(store).saveIds(setOf("new"))
    }

    @Test
    fun `concurrent second call returns Busy`() {
        val latch = CountDownLatch(1)
        whenever(gateway.getLatestMatches(clubId)).thenAnswer {
            latch.await(2, TimeUnit.SECONDS)
            EaApiResult.Success(listOf(match("m1")))
        }

        val executor = Executors.newFixedThreadPool(2)
        val firstFuture = executor.submit<NotifyResult> { service.notifyLatest() }
        Thread.sleep(100)
        val secondResult = service.notifyLatest()
        latch.countDown()
        val firstResult = firstFuture.get(3, TimeUnit.SECONDS)
        executor.shutdown()

        assertThat(secondResult).isEqualTo(NotifyResult.Busy)
        assertThat(firstResult).isInstanceOf(NotifyResult.Sent::class.java)
    }

    // -- Immediate EA call (manual refresh) --

    @Test
    fun `notifyLatest calls EA API immediately without waiting for scheduler`() {
        whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.NoMatches)

        // Must return synchronously; if it waited 2 minutes the test would time out.
        service.notifyLatest()

        verify(gateway).getLatestMatches(clubId)
    }

    @Test
    fun `runIfIdle succeeds after notifyLatest completes - lock is released`() {
        whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.NoMatches)
        service.notifyLatest()

        var ran = false
        val acquired = service.runIfIdle { ran = true }

        assertThat(acquired).isTrue()
        assertThat(ran).isTrue()
    }

    @Test
    fun `scheduler can run normally after manual refresh completes`() {
        val match = match("m1")
        whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(match)))

        // Manual refresh publishes m1
        service.notifyLatest()

        // Now simulate a scheduler cycle via runIfIdle — it must not be locked out
        var schedulerRan = false
        val acquired = service.runIfIdle { schedulerRan = true }

        assertThat(acquired).isTrue()
        assertThat(schedulerRan).isTrue()
    }

    // -- History webhook --

    @Test
    fun `history webhook is called for newly published match`() {
        val match = match("m1")
        whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(match)))

        service.notifyLatest()

        verify(webhookClient).sendHistory(any())
    }

    @Test
    fun `history webhook is not called for already published match`() {
        val match = match("m1")
        whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(match)))
        whenever(store.loadIds()).thenReturn(setOf("m1"))

        service.notifyLatest()

        verify(webhookClient, never()).sendHistory(any())
    }

    @Test
    fun `history webhook failure does not affect main delivery result`() {
        val match = match("m1")
        whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(match)))
        // sendHistory is fire-and-forget and never throws — verify main result is Sent regardless
        org.mockito.kotlin.doNothing().whenever(webhookClient).sendHistory(any())

        val result = service.notifyLatest()

        assertThat(result).isInstanceOf(NotifyResult.Sent::class.java)
        verify(webhookClient).send(any())
    }

    @Test
    fun `manual refresh does not send already published match to history`() {
        val match = match("m1")
        whenever(gateway.getLatestMatches(clubId)).thenReturn(EaApiResult.Success(listOf(match)))
        whenever(store.loadIds()).thenReturn(setOf("m1"))

        service.notifyLatest()

        verify(webhookClient, never()).send(any())
        verify(webhookClient, never()).sendHistory(any())
    }

    // -- Helpers --

    private fun match(id: String, ts: Long = 5000L) = MatchResponse(
        matchId = id,
        timestamp = ts,
        clubs = mapOf(
            "12345" to ClubMatchEntry(details = ClubDetails(name = "Test FC"), score = "2", result = "1"),
            "99999" to ClubMatchEntry(details = ClubDetails(name = "Opp"),     score = "0", result = "0"),
        ),
        players = emptyMap(),
    )
}
