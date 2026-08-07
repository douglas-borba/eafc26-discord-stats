package com.eafc26.discordstats.ea

import com.eafc26.discordstats.config.AppProperties
import com.eafc26.discordstats.ea.model.ClubSearchResult
import com.eafc26.discordstats.ea.model.MatchResponse
import com.eafc26.discordstats.ea.model.MemberStats
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
        log.info("========================================")
        log.info("Fetching matches for club: {}", clubId)
        log.info("Strategy: Fetch both leagueMatch and playoffMatch, then merge")
        log.info("========================================")
        
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
                    log.info("Returning only league matches due to playoff fetch failure")
                    return EaApiResult.Success(leagueMatches.sortedByDescending { it.timestamp })
                }
                return playoffResult
            }
            is EaApiResult.UnexpectedPayload -> {
                log.error("Unexpected payload when fetching playoff matches", playoffResult.cause)
                // Se league funcionou mas playoff falhou, retorna apenas league
                if (leagueMatches.isNotEmpty()) {
                    log.info("Returning only league matches due to playoff parse failure")
                    return EaApiResult.Success(leagueMatches.sortedByDescending { it.timestamp })
                }
                return playoffResult
            }
        }
        
        log.info("League matches: {}", leagueMatches.size)
        log.info("Playoff matches: {}", playoffMatches.size)
        
        // Merge and deduplicate by matchId
        val allMatches = (leagueMatches + playoffMatches)
            .distinctBy { it.matchId }
            .sortedByDescending { it.timestamp }
        
        log.info("Total matches after merge and dedupe: {}", allMatches.size)
        
        if (allMatches.isEmpty()) {
            log.info("No matches found")
            return EaApiResult.NoMatches
        }
        
        log.info("========================================")
        log.info("EA API PARSE SUCCESS")
        log.info("All Match IDs:")
        allMatches.take(10).forEach { match ->
            log.info("  ID: {} | Timestamp: {} | Date: {}", 
                match.matchId, 
                match.timestamp,
                java.time.Instant.ofEpochSecond(match.timestamp)
            )
        }
        log.info("========================================")
        
        return EaApiResult.Success(allMatches)
    }
    
    private fun fetchMatchesByType(clubId: String, matchType: String): EaApiResult<List<MatchResponse>> {
        val uri = "/clubs/matches" +
                "?platform=${props.ea.platform}" +
                "&clubIds=${encode(clubId)}" +
                "&matchType=${matchType}" +
                "&maxResultCount=${props.ea.maxResultCount}"

        log.info("Fetching matchType={}", matchType)
        log.info("URL: {}{}", props.ea.baseUrl, uri)

        return try {
            val body = webClient.get().uri(uri).retrieve()
                .bodyToMono(String::class.java)
                .block() ?: "[]"
            
            val result = parser.parseMatches(body)
            
            if (result is EaApiResult.Success) {
                log.info("  {} matches returned", result.data.size)
            }
            
            result
        } catch (ex: WebClientResponseException) {
            log.warn("EA matches returned HTTP {}: {}", ex.statusCode.value(), ex.message)
            EaApiResult.Unavailable(ex.statusCode.value(), ex.message ?: ex.statusCode.toString())
        } catch (ex: Exception) {
            log.warn("EA matches failed with unexpected error", ex)
            EaApiResult.Unavailable(0, ex.message ?: "unknown error")
        }
    }

    override fun getMembersStats(clubId: String): EaApiResult<List<MemberStats>> {
        val uri = "/members/stats?platform=${props.ea.platform}&clubId=${encode(clubId)}"
        log.debug("EA members/stats request: {}{}", props.ea.baseUrl, uri)

        return try {
            val body = webClient.get().uri(uri).retrieve()
                .bodyToMono(String::class.java)
                .block() ?: "[]"
            parser.parseMembersStats(body)
        } catch (ex: WebClientResponseException) {
            log.warn("EA members/stats returned HTTP {}: {}", ex.statusCode.value(), ex.message)
            EaApiResult.Unavailable(ex.statusCode.value(), ex.message ?: ex.statusCode.toString())
        } catch (ex: Exception) {
            log.warn("EA members/stats failed with unexpected error", ex)
            EaApiResult.Unavailable(0, ex.message ?: "unknown error")
        }
    }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8)
}
