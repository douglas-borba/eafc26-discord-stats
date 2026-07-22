package com.eafc26.discordstats.ea

import com.eafc26.discordstats.config.AppProperties
import com.eafc26.discordstats.config.EaProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient

/**
 * All tests run entirely against local fixtures served by MockWebServer.
 * No real network calls are made; the live EA endpoint is not required.
 */
class WebClientEaClubsGatewayTest {

    private lateinit var server: MockWebServer
    private lateinit var gateway: WebClientEaClubsGateway

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()

        val props = AppProperties(
            ea = EaProperties(
                baseUrl = server.url("/").toString().trimEnd('/'),
                platform = "common-gen5",
                clubId = "12345",
                matchType = "leagueMatch",
                maxResultCount = 5,
            )
        )

        val webClient = WebClient.builder()
            .baseUrl(props.ea.baseUrl)
            .defaultHeader("User-Agent", props.ea.userAgent)
            .defaultHeader("Accept", "application/json")
            .build()

        val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        val parser = EaResponseParser(objectMapper)

        gateway = WebClientEaClubsGateway(webClient, props, parser)
    }

    @AfterEach
    fun tearDown() = server.shutdown()

    // -- Club search --

    @Test
    fun `searchClubs returns Success with parsed results`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(fixture("clubs-search.json"))
        )

        val result = gateway.searchClubs("Test FC")

        assertThat(result).isInstanceOf(EaApiResult.Success::class.java)
        val clubs = (result as EaApiResult.Success).data
        assertThat(clubs).hasSize(1)
        assertThat(clubs[0].clubId).isEqualTo("1104972")
        assertThat(clubs[0].resolvedName()).isEqualTo("Associação BF")
    }

    @Test
    fun `searchClubs returns NoMatches on empty array`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("[]")
        )

        val result = gateway.searchClubs("NonExistent Club")

        assertThat(result).isEqualTo(EaApiResult.NoMatches)
    }

    @Test
    fun `searchClubs returns Unavailable on HTTP 403`() {
        server.enqueue(MockResponse().setResponseCode(403))

        val result = gateway.searchClubs("Test FC")

        assertThat(result).isInstanceOf(EaApiResult.Unavailable::class.java)
        assertThat((result as EaApiResult.Unavailable).statusCode).isEqualTo(403)
    }

    @Test
    fun `searchClubs returns Unavailable on HTTP 503`() {
        server.enqueue(MockResponse().setResponseCode(503))

        val result = gateway.searchClubs("Test FC")

        assertThat(result).isInstanceOf(EaApiResult.Unavailable::class.java)
        assertThat((result as EaApiResult.Unavailable).statusCode).isEqualTo(503)
    }

    @Test
    fun `searchClubs returns UnexpectedPayload on malformed JSON`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("{not valid json{{")
        )

        val result = gateway.searchClubs("Test FC")

        assertThat(result).isInstanceOf(EaApiResult.UnexpectedPayload::class.java)
    }

    // -- Match retrieval --

    @Test
    fun `getLatestMatches returns Success with two parsed matches`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(fixture("clubs-matches.json"))
        )

        val result = gateway.getLatestMatches("12345")

        assertThat(result).isInstanceOf(EaApiResult.Success::class.java)
        val matches = (result as EaApiResult.Success).data
        assertThat(matches).hasSize(2)
    }

    @Test
    fun `getLatestMatches parses matchId and timestamp correctly`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(fixture("clubs-matches.json"))
        )

        val matches = (gateway.getLatestMatches("12345") as EaApiResult.Success).data
        val first = matches[0]

        assertThat(first.matchId).isEqualTo("345758684140013")
        assertThat(first.timestamp).isEqualTo(1718500000L)
        assertThat(first.matchType).isEqualTo("leagueMatch")
    }

    @Test
    fun `getLatestMatches parses clubs with nested details`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(fixture("clubs-matches.json"))
        )

        val matches = (gateway.getLatestMatches("12345") as EaApiResult.Success).data
        val clubs = matches[0].clubs

        assertThat(clubs).containsKey("12345")
        assertThat(clubs["12345"]!!.resolvedName()).isEqualTo("Test FC")
        assertThat(clubs["12345"]!!.score).isEqualTo("3")
        assertThat(clubs["12345"]!!.goalsAgainst).isEqualTo("1")
        assertThat(clubs["99999"]!!.resolvedName()).isEqualTo("Opponent Club")
    }

    @Test
    fun `getLatestMatches parses player stats as strings`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(fixture("clubs-matches.json"))
        )

        val matches = (gateway.getLatestMatches("12345") as EaApiResult.Success).data
        val players = matches[0].players["12345"] ?: error("missing club players")
        val player = players["player_abc"] ?: error("missing player")

        assertThat(player.playerName).isEqualTo("Striker99")
        assertThat(player.goals).isEqualTo("2")
        assertThat(player.assists).isEqualTo("1")
        assertThat(player.rating).isEqualTo("8.5")
        assertThat(player.manOfTheMatch).isEqualTo("1")
    }

    @Test
    fun `getLatestMatches returns NoMatches on empty array`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("[]")
        )

        val result = gateway.getLatestMatches("12345")

        assertThat(result).isEqualTo(EaApiResult.NoMatches)
    }

    @Test
    fun `getLatestMatches returns Unavailable on HTTP 403`() {
        server.enqueue(MockResponse().setResponseCode(403))

        val result = gateway.getLatestMatches("12345")

        assertThat(result).isInstanceOf(EaApiResult.Unavailable::class.java)
        assertThat((result as EaApiResult.Unavailable).statusCode).isEqualTo(403)
    }

    @Test
    fun `getLatestMatches returns Unavailable on HTTP 500`() {
        server.enqueue(MockResponse().setResponseCode(500))

        val result = gateway.getLatestMatches("12345")

        assertThat(result).isInstanceOf(EaApiResult.Unavailable::class.java)
        assertThat((result as EaApiResult.Unavailable).statusCode).isEqualTo(500)
    }

    @Test
    fun `getLatestMatches returns UnexpectedPayload on malformed JSON`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("[{\"matchId\": BROKEN")
        )

        val result = gateway.getLatestMatches("12345")

        assertThat(result).isInstanceOf(EaApiResult.UnexpectedPayload::class.java)
    }

    @Test
    fun `getLatestMatches request includes required query parameters`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("[]")
        )

        gateway.getLatestMatches("12345")

        val recorded = server.takeRequest()
        val path = recorded.path ?: ""
        assertThat(path).contains("platform=common-gen5")
        assertThat(path).contains("clubIds=12345")
        assertThat(path).contains("matchType=leagueMatch")
        assertThat(path).contains("maxResultCount=5")
    }

    @Test
    fun `searchClubs request includes required query parameters`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("[]")
        )

        gateway.searchClubs("My Club")

        val recorded = server.takeRequest()
        val path = recorded.path ?: ""
        assertThat(path).contains("platform=common-gen5")
        assertThat(path).contains("clubName=")
    }

    // -- Members stats --

    @Test
    fun `getMembersStats returns Success with parsed entries`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(fixture("members-stats.json"))
        )

        val result = gateway.getMembersStats("12345")

        assertThat(result).isInstanceOf(EaApiResult.Success::class.java)
        val members = (result as EaApiResult.Success).data
        assertThat(members).hasSize(3)
        assertThat(members.map { it.playerName }).containsExactlyInAnyOrder("dbeng_bass", "Striker99", "GoalieKing")
        assertThat(members.first { it.playerName == "dbeng_bass" }.proName).isEqualTo("R. Nazário")
    }

    @Test
    fun `getMembersStats returns Success with empty list for empty array`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("[]")
        )

        val result = gateway.getMembersStats("12345")

        assertThat(result).isInstanceOf(EaApiResult.Success::class.java)
        assertThat((result as EaApiResult.Success).data).isEmpty()
    }

    @Test
    fun `getMembersStats returns Unavailable on HTTP 503`() {
        server.enqueue(MockResponse().setResponseCode(503))

        val result = gateway.getMembersStats("12345")

        assertThat(result).isInstanceOf(EaApiResult.Unavailable::class.java)
        assertThat((result as EaApiResult.Unavailable).statusCode).isEqualTo(503)
    }

    @Test
    fun `getMembersStats request includes platform and clubId query parameters`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("[]")
        )

        gateway.getMembersStats("12345")

        val recorded = server.takeRequest()
        val path = recorded.path ?: ""
        assertThat(path).contains("/members/stats")
        assertThat(path).contains("platform=common-gen5")
        assertThat(path).contains("clubId=12345")
    }

    // -- Helpers --

    private fun fixture(name: String): String =
        javaClass.classLoader!!
            .getResourceAsStream("fixtures/$name")
            ?.bufferedReader()
            ?.readText()
            ?: error("fixture not found: $name")
}
