package com.eafc26.discordstats.ea

import com.eafc26.discordstats.ea.model.ClubSearchResult
import com.eafc26.discordstats.ea.model.MatchResponse
import com.eafc26.discordstats.ea.model.MemberStats
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class EaResponseParser(private val objectMapper: ObjectMapper) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun parseSearch(json: String): EaApiResult<List<ClubSearchResult>> {
        log.trace("EA search raw body: {}", json)
        return try {
            val results: List<ClubSearchResult> = objectMapper.readValue(json)
            if (results.isEmpty()) EaApiResult.NoMatches
            else EaApiResult.Success(results)
        } catch (ex: JsonProcessingException) {
            log.warn("Failed to parse search response", ex)
            EaApiResult.UnexpectedPayload(ex)
        }
    }

    fun parseMatches(json: String): EaApiResult<List<MatchResponse>> {
        log.trace("EA matches raw body: {}", json)
        return try {
            val matches: List<MatchResponse> = objectMapper.readValue(json)
            if (matches.isEmpty()) {
                EaApiResult.NoMatches
            } else {
                // Log player details at DEBUG level to help diagnose goalkeeper data issues
                matches.forEach { match ->
                    val playersByClub = match.players
                    playersByClub.forEach { (clubId, players) ->
                        log.debug("Match {} Club {}: {} players", match.matchId, clubId, players.size)
                        players.forEach { (playerId, player) ->
                            log.debug("  Player {}: pos={} name={} saves={} goalsConceded={}",
                                playerId, player.position, player.playerName, player.saves, player.goalsConceded)
                        }
                    }
                }
                EaApiResult.Success(matches)
            }
        } catch (ex: JsonProcessingException) {
            log.warn("Failed to parse matches response", ex)
            EaApiResult.UnexpectedPayload(ex)
        }
    }

    fun parseMembersStats(json: String): EaApiResult<List<MemberStats>> {
        log.trace("EA members/stats raw body: {}", json)
        return try {
            val members: List<MemberStats> = objectMapper.readValue(json)
            EaApiResult.Success(members)
        } catch (ex: JsonProcessingException) {
            log.warn("Failed to parse members/stats response", ex)
            EaApiResult.UnexpectedPayload(ex)
        }
    }
}
