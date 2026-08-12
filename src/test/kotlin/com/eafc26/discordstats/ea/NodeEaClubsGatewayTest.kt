package com.eafc26.discordstats.ea

import com.eafc26.discordstats.config.AppProperties
import com.eafc26.discordstats.config.EaProperties
import com.eafc26.discordstats.config.WebClientConfig
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient

class NodeEaClubsGatewayTest {
    private lateinit var server: MockWebServer
    private lateinit var gateway: NodeEaClubsGateway

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        val props = AppProperties(ea = EaProperties(gatewayBaseUrl = server.url("/").toString().trimEnd('/'), gatewayInternalToken = "secret"))
        val client = WebClient.builder().baseUrl(props.ea.gatewayBaseUrl)
            .defaultHeader("Authorization", "Bearer ${props.ea.gatewayInternalToken}").build()
        gateway = NodeEaClubsGateway(client, props, EaResponseParser(jacksonObjectMapper()))
    }

    @AfterEach fun stop() = server.shutdown()

    @Test fun `search uses internal contract and bearer token`() {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("[]"))
        assertThat(gateway.searchClubs("Associação BF")).isEqualTo(EaApiResult.NoMatches)
        val request = server.takeRequest()
        assertThat(request.path).contains("/ea/clubs/search?name=Associa%C3%A7%C3%A3o%20BF&platform=common-gen5")
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer secret")
    }

    @Test fun `matches parse the merged gateway payload`() {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(fixture("clubs-matches.json")))
        val result = gateway.getLatestMatches("1104972")
        assertThat(result).isInstanceOf(EaApiResult.Success::class.java)
        assertThat((result as EaApiResult.Success).data).hasSize(2)
        assertThat(server.takeRequest().path).isEqualTo("/ea/clubs/1104972/matches?platform=common-gen5&maxResultCount=20")
    }

    @Test fun `members parse the EA envelope`() {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("{\"members\":[]}"))
        assertThat(gateway.getMembersStats("1104972")).isEqualTo(EaApiResult.Success(emptyList<Any>()))
        assertThat(server.takeRequest().path).isEqualTo("/ea/clubs/1104972/members?platform=common-gen5")
    }

    @Test fun `gateway errors remain unavailable`() {
        server.enqueue(MockResponse().setResponseCode(502))
        val result = gateway.getLatestMatches("1104972")
        assertThat(result).isInstanceOf(EaApiResult.Unavailable::class.java)
        assertThat((result as EaApiResult.Unavailable).statusCode).isEqualTo(502)
    }

    @Test fun `HTTP 200 with invalid JSON becomes UnexpectedPayload`() {
        server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody("not-valid-json"))
        val result = gateway.getLatestMatches("1104972")
        assertThat(result).isInstanceOf(EaApiResult.UnexpectedPayload::class.java)
    }

    @Test fun `HTTP 200 with valid JSON array is parsed successfully`() {
        server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody("[]"))
        val result = gateway.getLatestMatches("1104972")
        assertThat(result).isEqualTo(EaApiResult.NoMatches)
    }

    @Test fun `invalid JSON is unexpected payload without retry`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("not-json"))

        assertThat(gateway.getLatestMatches("1104972")).isInstanceOf(EaApiResult.UnexpectedPayload::class.java)
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test fun `configured EA gateway client reads a valid payload above 256 KiB and below 512 KiB`() {
        val body = sizedMatchesPayload(264 * 1024)
        assertThat(body.toByteArray().size).isGreaterThan(256 * 1024)
        assertThat(body.toByteArray().size).isLessThan(512 * 1024)
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(body))

        val result = configuredGateway().getLatestMatches("1104972")

        assertThat(result).isInstanceOf(EaApiResult.Success::class.java)
    }

    @Test fun `configured EA gateway client rejects a valid payload above 512 KiB`() {
        val body = sizedMatchesPayload(513 * 1024)
        assertThat(body.toByteArray().size).isGreaterThan(512 * 1024)
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(body))

        val result = configuredGateway().getLatestMatches("1104972")

        assertThat(result).isInstanceOf(EaApiResult.UnexpectedPayload::class.java)
    }

    @Test fun `configured EA gateway client keeps small valid payloads working`() {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("[]"))

        assertThat(configuredGateway().getLatestMatches("1104972")).isEqualTo(EaApiResult.NoMatches)
    }

    private fun configuredGateway(): NodeEaClubsGateway {
        val props = AppProperties(ea = EaProperties(gatewayBaseUrl = server.url("/").toString().trimEnd('/'), gatewayInternalToken = "secret"))
        return NodeEaClubsGateway(WebClientConfig().eaGatewayWebClient(props), props, EaResponseParser(jacksonObjectMapper()))
    }

    private fun sizedMatchesPayload(minimumBytes: Int): String {
        val mapper = jacksonObjectMapper()
        val template = mapper.readTree(fixture("clubs-matches.json")) as ArrayNode
        val matches = mapper.createArrayNode()
        var sequence = 0
        while (mapper.writeValueAsBytes(matches).size <= minimumBytes) {
            template.forEach { source ->
                val copy = source.deepCopy<ObjectNode>()
                copy.put("matchId", "sized-$sequence")
                copy.put("timestamp", 1_718_500_000L + sequence++)
                matches.add(copy)
            }
        }
        return mapper.writeValueAsString(matches)
    }

    private fun fixture(name: String) = checkNotNull(javaClass.getResource("/fixtures/$name")).readText()
}
