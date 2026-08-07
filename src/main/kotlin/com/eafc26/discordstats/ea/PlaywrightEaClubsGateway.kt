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
        log.info("====================================================================")
        log.info(">>> Fetching matches for club: {}", clubId)
        log.info(">>> Strategy: Fetch both leagueMatch and playoffMatch, then merge")
        log.info("====================================================================")
        
        // Fetch league matches
        val leagueResult = fetchMatchesByType(clubId, "leagueMatch")
        val leagueMatches = when (leagueResult) {
            is EaApiResult.Success -> leagueResult.data
            EaApiResult.NoMatches -> emptyList()
            is EaApiResult.Unavailable -> {
                log.warn("EA API unavailable when fetching league matches: {}", leagueResult.message)
                return leagueResult
            }
            is EaApiResult.UnexpectedPayload -> {
                log.error("Unexpected payload when fetching league matches", leagueResult.cause)
                return leagueResult
            }
        }
        
        // Fetch playoff matches
        val playoffResult = fetchMatchesByType(clubId, "playoffMatch")
        val playoffMatches = when (playoffResult) {
            is EaApiResult.Success -> playoffResult.data
            EaApiResult.NoMatches -> emptyList()
            is EaApiResult.Unavailable -> {
                log.warn("EA API unavailable when fetching playoff matches: {}", playoffResult.message)
                // Se league funcionou mas playoff falhou, retorna apenas league
                if (leagueMatches.isNotEmpty()) {
                    log.info(">>> Returning only league matches due to playoff fetch failure")
                    return EaApiResult.Success(leagueMatches.sortedByDescending { it.timestamp })
                }
                return playoffResult
            }
            is EaApiResult.UnexpectedPayload -> {
                log.error("Unexpected payload when fetching playoff matches", playoffResult.cause)
                // Se league funcionou mas playoff falhou, retorna apenas league
                if (leagueMatches.isNotEmpty()) {
                    log.info(">>> Returning only league matches due to playoff parse failure")
                    return EaApiResult.Success(leagueMatches.sortedByDescending { it.timestamp })
                }
                return playoffResult
            }
        }
        
        log.info(">>> League matches: {}", leagueMatches.size)
        log.info(">>> Playoff matches: {}", playoffMatches.size)
        
        // Merge and deduplicate by matchId
        val allMatches = (leagueMatches + playoffMatches)
            .distinctBy { it.matchId }
            .sortedByDescending { it.timestamp }
        
        log.info(">>> Total matches after merge and dedupe: {}", allMatches.size)
        
        if (allMatches.isEmpty()) {
            log.info(">>> No matches found")
            return EaApiResult.NoMatches
        }
        
        // Log resultado com timestamps formatados
        allMatches.take(5).forEach { match ->
            log.info(">>>   Match {} - timestamp: {} (UTC: {}, BRT: {})",
                match.matchId,
                match.timestamp,
                java.time.Instant.ofEpochSecond(match.timestamp).atZone(java.time.ZoneOffset.UTC),
                java.time.Instant.ofEpochSecond(match.timestamp).atZone(java.time.ZoneId.of("America/Sao_Paulo"))
            )
        }
        
        // Save raw response for debugging
        try {
            java.io.File("/tmp/ea_latest_response.json").writeText(
                com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().writerWithDefaultPrettyPrinter()
                    .writeValueAsString(allMatches)
            )
            log.info(">>> EA RAW RESPONSE saved to /tmp/ea_latest_response.json")
        } catch (e: Exception) {
            log.warn("Failed to save debug response: {}", e.message)
        }
        
        return EaApiResult.Success(allMatches)
    }
    
    private fun fetchMatchesByType(clubId: String, matchType: String): EaApiResult<List<com.eafc26.discordstats.ea.model.MatchResponse>> {
        val url = "${props.ea.baseUrl}/clubs/matches" +
                "?platform=${props.ea.platform}" +
                "&clubIds=${encode(clubId)}" +
                "&matchType=${matchType}" +
                "&maxResultCount=${props.ea.maxResultCount}"

        log.info(">>> Fetching matchType={}", matchType)
        log.info(">>>   URL: {}", url)
        
        val result = callEa(url, parser::parseMatches)
        
        if (result is EaApiResult.Success) {
            log.info(">>>   {} matches returned", result.data.size)
        }
        
        return result
    }

    override fun getMembersStats(clubId: String): EaApiResult<List<com.eafc26.discordstats.ea.model.MemberStats>> {
        val url = "${props.ea.baseUrl}/members/stats" +
                "?platform=${props.ea.platform}" +
                "&clubId=${encode(clubId)}"
        log.info("Fetching members/stats for clubId={}", clubId)
        return callEa(url) { parser.parseMembersStats(it) }
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

        log.info(">>> browserFetcher.fetch returned: status={} body.length={}", result.status, result.body.length)

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
