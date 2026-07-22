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
        return try {
            val root = objectMapper.readTree(json)
            val members: List<MemberStats> = when {
                // Direct array — kept for unit tests and resilience
                root.isArray -> objectMapper.readValue(json)

                // Real EA response shape (confirmed 2026-07-22):
                // {"members": [{…}, …]} — "name" is the gamertag, "proName" is the Virtual Pro name
                root.isObject -> {
                    val membersNode = root.get("members")
                    when {
                        membersNode == null -> emptyList()
                        membersNode.isArray -> objectMapper.readValue(membersNode.toString())
                        else -> emptyList()
                    }
                }

                else -> emptyList()
            }
            log.info("EA members/stats: parsed {} member(s)", members.size)
            EaApiResult.Success(members)
        } catch (ex: JsonProcessingException) {
            log.warn("Failed to parse members/stats response", ex)
            EaApiResult.UnexpectedPayload(ex)
        }
    }
}
