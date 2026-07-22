package com.eafc26.discordstats.ea

import com.eafc26.discordstats.ea.model.ClubSearchResult
import com.eafc26.discordstats.ea.model.MatchResponse
import com.eafc26.discordstats.ea.model.MemberStats
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
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
                // Root is already an array (unit-test fixture / future API change)
                root.isArray -> objectMapper.readValue(json)

                // Root is an object — the real EA response.
                // EA wraps member entries in a single top-level field whose value is
                // either an array of member objects or an object keyed by playerId /
                // playername (same pattern as the "players" field in match payloads).
                root.isObject -> extractMembersFromRootObject(root)

                else -> emptyList()
            }
            log.info("EA members/stats: parsed {} member(s)", members.size)
            EaApiResult.Success(members)
        } catch (ex: JsonProcessingException) {
            log.warn("Failed to parse members/stats response", ex)
            EaApiResult.UnexpectedPayload(ex)
        }
    }

    /**
     * Inspects every top-level field of [root] and returns the first collection
     * of [MemberStats] found, supporting two EA response shapes:
     *
     * - `{"someField": [ {member}, … ]}` — array of member objects
     * - `{"someField": { "id1": {member}, "id2": {member}, … }}` — map of member objects
     */
    private fun extractMembersFromRootObject(root: JsonNode): List<MemberStats> {
        root.fields().forEach { (fieldName, fieldValue) ->
            when {
                fieldValue.isArray -> {
                    val list: List<MemberStats> = objectMapper.readValue(fieldValue.toString())
                    if (list.isNotEmpty()) {
                        log.debug("EA members/stats: found member array in field '{}'", fieldName)
                        return list
                    }
                }
                fieldValue.isObject && fieldValue.size() > 0 -> {
                    val entries = fieldValue.fields().asSequence()
                        .map { (_, node) ->
                            if (node.isObject) objectMapper.treeToValue(node, MemberStats::class.java)
                            else null
                        }
                        .filterNotNull()
                        .filter { !it.playerName.isNullOrBlank() }
                        .toList()
                    if (entries.isNotEmpty()) {
                        log.debug("EA members/stats: found {} member entries in object field '{}'", entries.size, fieldName)
                        return entries
                    }
                }
                else -> {}
            }
        }
        return emptyList()
    }
}
