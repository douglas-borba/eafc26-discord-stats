package com.eafc26.discordstats.ea

import com.eafc26.discordstats.config.AppProperties
import com.eafc26.discordstats.ea.model.ClubSearchResult
import com.eafc26.discordstats.ea.model.MatchResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException

@Component
@Qualifier("production")
@ConditionalOnProperty(name = ["app.ea.client"], havingValue = "webclient", matchIfMissing = true)
class WebClientEaClubsGateway(
    private val webClient: WebClient,
    private val props: AppProperties,
    private val parser: EaResponseParser,
) : EaClubsGateway {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun searchClubs(clubName: String): EaApiResult<List<ClubSearchResult>> {
        val uri = "/allTimeLeaderboard/search?platform=${props.ea.platform}&clubName=${encode(clubName)}"
        log.debug("EA search request: {}{}", props.ea.baseUrl, uri)

        return try {
            val body = webClient.get().uri(uri).retrieve()
                .bodyToMono(String::class.java)
                .block() ?: "[]"
            parser.parseSearch(body)
        } catch (ex: WebClientResponseException) {
            log.warn("EA search returned HTTP {}: {}", ex.statusCode.value(), ex.message)
            EaApiResult.Unavailable(ex.statusCode.value(), ex.message ?: ex.statusCode.toString())
        } catch (ex: Exception) {
            log.warn("EA search failed with unexpected error", ex)
            EaApiResult.Unavailable(0, ex.message ?: "unknown error")
        }
    }

    override fun getLatestMatches(clubId: String): EaApiResult<List<MatchResponse>> {
        val uri = "/clubs/matches" +
                "?platform=${props.ea.platform}" +
                "&clubIds=${encode(clubId)}" +
                "&matchType=${props.ea.matchType}" +
                "&maxResultCount=${props.ea.maxResultCount}"

        log.debug("EA matches request: {}{}", props.ea.baseUrl, uri)

        return try {
            val body = webClient.get().uri(uri).retrieve()
                .bodyToMono(String::class.java)
                .block() ?: "[]"
            parser.parseMatches(body)
        } catch (ex: WebClientResponseException) {
            log.warn("EA matches returned HTTP {}: {}", ex.statusCode.value(), ex.message)
            EaApiResult.Unavailable(ex.statusCode.value(), ex.message ?: ex.statusCode.toString())
        } catch (ex: Exception) {
            log.warn("EA matches failed with unexpected error", ex)
            EaApiResult.Unavailable(0, ex.message ?: "unknown error")
        }
    }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8)
}
