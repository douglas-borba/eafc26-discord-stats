package com.eafc26.discordstats.ea

import com.eafc26.discordstats.config.AppProperties
import com.microsoft.playwright.PlaywrightException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@Qualifier("production")
@ConditionalOnProperty(name = ["app.ea.client"], havingValue = "playwright")
class PlaywrightEaClubsGateway(
    private val browserFetcher: BrowserFetcher,
    private val parser: EaResponseParser,
    private val props: AppProperties,
) : EaClubsGateway {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun searchClubs(clubName: String): EaApiResult<List<com.eafc26.discordstats.ea.model.ClubSearchResult>> {
        val url = "${props.ea.baseUrl}/allTimeLeaderboard/search" +
                "?platform=${props.ea.platform}" +
                "&clubName=${encode(clubName)}"

        log.debug("Playwright search: {}", url)
        return callEa(url) { parser.parseSearch(it) }
    }

    override fun getLatestMatches(clubId: String): EaApiResult<List<com.eafc26.discordstats.ea.model.MatchResponse>> {
        val url = "${props.ea.baseUrl}/clubs/matches" +
                "?platform=${props.ea.platform}" +
                "&clubIds=${encode(clubId)}" +
                "&matchType=${props.ea.matchType}" +
                "&maxResultCount=${props.ea.maxResultCount}"

        log.debug("Playwright matches: {}", url)
        return callEa(url) { parser.parseMatches(it) }
    }

    private fun <T> callEa(url: String, parse: (String) -> EaApiResult<T>): EaApiResult<T> {
        val result = try {
            browserFetcher.fetch(url)
        } catch (ex: PlaywrightException) {
            log.warn("Browser fetch failed for {}", url, ex)
            return EaApiResult.Unavailable(0, ex.message ?: "Playwright error")
        } catch (ex: Exception) {
            log.warn("Unexpected error fetching {}", url, ex)
            return EaApiResult.Unavailable(0, ex.message ?: "unknown error")
        }

        if (result.error != null) {
            log.warn("Browser-side fetch error for {}: {}", url, result.error)
            return EaApiResult.Unavailable(0, result.error)
        }

        if (result.status >= 400) {
            log.warn("EA returned HTTP {} for {}", result.status, url)
            return EaApiResult.Unavailable(result.status, "HTTP ${result.status}")
        }

        return parse(result.body)
    }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8)
}
