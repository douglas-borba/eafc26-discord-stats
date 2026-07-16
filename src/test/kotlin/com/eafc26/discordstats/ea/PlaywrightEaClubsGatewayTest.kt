package com.eafc26.discordstats.ea

import com.eafc26.discordstats.config.AppProperties
import com.eafc26.discordstats.config.EaProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.microsoft.playwright.PlaywrightException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for PlaywrightEaClubsGateway using a fake BrowserFetcher.
 * No browser is launched; Playwright is never initialized.
 */
class PlaywrightEaClubsGatewayTest {

    private lateinit var browserFetcher: BrowserFetcher
    private lateinit var gateway: PlaywrightEaClubsGateway
    private lateinit var parser: EaResponseParser

    private val props = AppProperties(
        ea = EaProperties(
            baseUrl = "https://proclubs.ea.com/api/fc",
            platform = "common-gen5",
            clubId = "12345",
            clubName = "Test FC",
            matchType = "leagueMatch",
            maxResultCount = 5,
        )
    )

    @BeforeEach
    fun setUp() {
        browserFetcher = mock()
        val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        parser = EaResponseParser(objectMapper)
        gateway = PlaywrightEaClubsGateway(browserFetcher, parser, props)
    }

    // -- searchClubs URL --

    @Test
    fun `searchClubs sends correct URL with platform and encoded club name`() {
        whenever(browserFetcher.fetch(any())).thenReturn(ok("[]"))

        gateway.searchClubs("Test FC")

        verify(browserFetcher).fetch(
            "https://proclubs.ea.com/api/fc/allTimeLeaderboard/search?platform=common-gen5&clubName=Test+FC"
        )
    }

    // -- getLatestMatches URL --

    @Test
    fun `getLatestMatches sends correct URL with all query parameters`() {
        whenever(browserFetcher.fetch(any())).thenReturn(ok("[]"))

        gateway.getLatestMatches("12345")

        verify(browserFetcher).fetch(
            "https://proclubs.ea.com/api/fc/clubs/matches?platform=common-gen5&clubIds=12345&matchType=leagueMatch&maxResultCount=5"
        )
    }

    // -- Successful JSON response --

    @Test
    fun `searchClubs returns Success on valid JSON`() {
        whenever(browserFetcher.fetch(any())).thenReturn(ok(fixture("clubs-search.json")))

        val result = gateway.searchClubs("Test FC")

        assertThat(result).isInstanceOf(EaApiResult.Success::class.java)
        val clubs = (result as EaApiResult.Success).data
        assertThat(clubs).hasSize(1)
        assertThat(clubs[0].clubId).isEqualTo("1104972")
    }

    @Test
    fun `getLatestMatches returns Success on valid JSON`() {
        whenever(browserFetcher.fetch(any())).thenReturn(ok(fixture("clubs-matches.json")))

        val result = gateway.getLatestMatches("12345")

        assertThat(result).isInstanceOf(EaApiResult.Success::class.java)
        val matches = (result as EaApiResult.Success).data
        assertThat(matches).hasSize(2)
        assertThat(matches[0].matchId).isEqualTo("345758684140013")
    }

    // -- Empty array --

    @Test
    fun `searchClubs returns NoMatches on empty array`() {
        whenever(browserFetcher.fetch(any())).thenReturn(ok("[]"))

        assertThat(gateway.searchClubs("Test FC")).isEqualTo(EaApiResult.NoMatches)
    }

    @Test
    fun `getLatestMatches returns NoMatches on empty array`() {
        whenever(browserFetcher.fetch(any())).thenReturn(ok("[]"))

        assertThat(gateway.getLatestMatches("12345")).isEqualTo(EaApiResult.NoMatches)
    }

    // -- HTTP 403 --

    @Test
    fun `searchClubs returns Unavailable on HTTP 403`() {
        whenever(browserFetcher.fetch(any())).thenReturn(
            BrowserFetchResult(status = 403, contentType = "text/html", body = "Forbidden", error = null)
        )

        val result = gateway.searchClubs("Test FC")

        assertThat(result).isInstanceOf(EaApiResult.Unavailable::class.java)
        assertThat((result as EaApiResult.Unavailable).statusCode).isEqualTo(403)
    }

    @Test
    fun `getLatestMatches returns Unavailable on HTTP 403`() {
        whenever(browserFetcher.fetch(any())).thenReturn(
            BrowserFetchResult(status = 403, contentType = "text/html", body = "Forbidden", error = null)
        )

        val result = gateway.getLatestMatches("12345")

        assertThat(result).isInstanceOf(EaApiResult.Unavailable::class.java)
        assertThat((result as EaApiResult.Unavailable).statusCode).isEqualTo(403)
    }

    // -- Status 0 / fetch error --

    @Test
    fun `searchClubs returns Unavailable on browser-side fetch error`() {
        whenever(browserFetcher.fetch(any())).thenReturn(
            BrowserFetchResult(status = 0, contentType = null, body = "", error = "TypeError: Failed to fetch")
        )

        val result = gateway.searchClubs("Test FC")

        assertThat(result).isInstanceOf(EaApiResult.Unavailable::class.java)
        assertThat((result as EaApiResult.Unavailable).statusCode).isEqualTo(0)
        assertThat(result.message).contains("Failed to fetch")
    }

    @Test
    fun `getLatestMatches returns Unavailable on browser-side fetch error`() {
        whenever(browserFetcher.fetch(any())).thenReturn(
            BrowserFetchResult(status = 0, contentType = null, body = "", error = "TypeError: Failed to fetch")
        )

        val result = gateway.getLatestMatches("12345")

        assertThat(result).isInstanceOf(EaApiResult.Unavailable::class.java)
        assertThat((result as EaApiResult.Unavailable).statusCode).isEqualTo(0)
    }

    // -- Malformed JSON --

    @Test
    fun `searchClubs returns UnexpectedPayload on malformed JSON`() {
        whenever(browserFetcher.fetch(any())).thenReturn(ok("{not json}"))

        val result = gateway.searchClubs("Test FC")

        assertThat(result).isInstanceOf(EaApiResult.UnexpectedPayload::class.java)
    }

    @Test
    fun `getLatestMatches returns UnexpectedPayload on malformed JSON`() {
        whenever(browserFetcher.fetch(any())).thenReturn(ok("[{\"matchId\": BROKEN"))

        val result = gateway.getLatestMatches("12345")

        assertThat(result).isInstanceOf(EaApiResult.UnexpectedPayload::class.java)
    }

    // -- PlaywrightException propagation --

    @Test
    fun `searchClubs returns Unavailable when BrowserFetcher throws PlaywrightException`() {
        whenever(browserFetcher.fetch(any())).thenThrow(PlaywrightException("browser crashed"))

        val result = gateway.searchClubs("Test FC")

        assertThat(result).isInstanceOf(EaApiResult.Unavailable::class.java)
        assertThat((result as EaApiResult.Unavailable).statusCode).isEqualTo(0)
    }

    @Test
    fun `getLatestMatches returns Unavailable when BrowserFetcher throws PlaywrightException`() {
        whenever(browserFetcher.fetch(any())).thenThrow(PlaywrightException("browser crashed"))

        val result = gateway.getLatestMatches("12345")

        assertThat(result).isInstanceOf(EaApiResult.Unavailable::class.java)
        assertThat((result as EaApiResult.Unavailable).statusCode).isEqualTo(0)
    }

    // -- BrowserFetcher is called exactly once per gateway call (no infinite loop) --

    @Test
    fun `searchClubs calls BrowserFetcher exactly once - no infinite retry loop`() {
        whenever(browserFetcher.fetch(any())).thenReturn(ok("[]"))

        gateway.searchClubs("Test FC")

        verify(browserFetcher, times(1)).fetch(any())
    }

    @Test
    fun `getLatestMatches calls BrowserFetcher exactly once - no infinite retry loop`() {
        whenever(browserFetcher.fetch(any())).thenReturn(ok("[]"))

        gateway.getLatestMatches("12345")

        verify(browserFetcher, times(1)).fetch(any())
    }

    // -- Helpers --

    private fun ok(body: String) =
        BrowserFetchResult(status = 200, contentType = "application/json", body = body, error = null)

    private fun fixture(name: String): String =
        javaClass.classLoader!!
            .getResourceAsStream("fixtures/$name")
            ?.bufferedReader()
            ?.readText()
            ?: error("fixture not found: $name")
}
