package com.eafc26.discordstats.ea

import com.eafc26.discordstats.config.AppProperties
import com.eafc26.discordstats.ea.model.ClubSearchResult
import com.eafc26.discordstats.ea.model.MatchResponse
import com.eafc26.discordstats.ea.model.MemberStats
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException

@Component
@Qualifier("production")
class NodeEaClubsGateway(
    private val eaGatewayWebClient: WebClient,
    private val props: AppProperties,
    private val parser: EaResponseParser,
) : EaClubsGateway {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun searchClubs(clubName: String) = request(
        eaGatewayWebClient.get().uri { builder -> builder.path("/ea/clubs/search")
            .queryParam("name", clubName).queryParam("platform", props.ea.platform).build() },
        parser::parseSearch,
    )

    override fun getLatestMatches(clubId: String) = request(
        "/ea/clubs/${encode(clubId)}/matches?platform=${encode(props.ea.platform)}&maxResultCount=${props.ea.maxResultCount}",
        parser::parseMatches,
    )

    override fun getMembersStats(clubId: String) = request(
        "/ea/clubs/${encode(clubId)}/members?platform=${encode(props.ea.platform)}",
        parser::parseMembersStats,
    )

    private fun <T> request(path: String, parse: (String) -> EaApiResult<T>): EaApiResult<T> =
        request(eaGatewayWebClient.get().uri(path), parse)

    private fun <T> request(spec: WebClient.RequestHeadersSpec<*>, parse: (String) -> EaApiResult<T>): EaApiResult<T> = try {
        val body = spec.retrieve().bodyToMono(String::class.java).block() ?: "[]"
        parse(body)
    } catch (ex: WebClientResponseException) {
        log.warn("EA gateway returned HTTP {}", ex.statusCode.value())
        EaApiResult.Unavailable(ex.statusCode.value(), ex.statusText)
    } catch (ex: Exception) {
        log.warn("EA gateway request failed: {}", ex.message)
        EaApiResult.Unavailable(0, ex.message ?: "EA gateway unavailable")
    }

    private fun encode(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8)
}
