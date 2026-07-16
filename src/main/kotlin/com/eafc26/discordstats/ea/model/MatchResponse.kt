package com.eafc26.discordstats.ea.model

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.eafc26.discordstats.ea.normalizeEaText

/**
 * Top-level match object from /clubs/matches.
 *
 * Field names verified from:
 *   - BryanAriza/proclubs26  packages/shared/src/types.ts
 *   - BryanAriza/proclubs26  apps/api/src/clubs/clubs.service.ts
 *   - Maldini80/bot-torneos-pro scratch/fetch_ea_matches.js
 *
 * EA returns numeric values (goals, assists, rating, mom) as JSON strings.
 * @JsonAlias handles the documented case-variation EA uses ("matchId" / "matchid",
 * "matchType" / "matchtype").
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class MatchResponse(
    @JsonAlias("matchid")
    @JsonProperty("matchId") val matchId: String,

    @JsonProperty("timestamp") val timestamp: Long,

    @JsonAlias("matchtype")
    @JsonProperty("matchType") val matchType: String? = null,

    // Keyed by clubId string
    @JsonProperty("clubs") val clubs: Map<String, ClubMatchEntry> = emptyMap(),

    // Keyed by clubId, then by playerId
    @JsonProperty("players") val players: Map<String, Map<String, PlayerEntry>> = emptyMap(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ClubMatchEntry(
    // EA nests name under "details" in some responses; service code handles both
    @JsonProperty("details") val details: ClubDetails? = null,
    @JsonProperty("name") val name: String? = null,

    // Goals scored by this club — EA returns as string
    @JsonAlias("goals")
    @JsonProperty("score") val score: String? = null,

    @JsonProperty("goalsAgainst") val goalsAgainst: String? = null,

    // 0 = loss, 1 = win, 2 = draw (as documented in community code)
    @JsonProperty("result") val result: String? = null,
) {
    fun resolvedName(): String? = (details?.name ?: name)?.let { normalizeEaText(it) }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class ClubDetails(
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("clubId") val clubId: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PlayerEntry(
    @JsonProperty("playername") val playerName: String? = null,
    @JsonProperty("pos") val position: String? = null,
    // All numeric stat fields come as strings from the EA API
    @JsonProperty("goals") val goals: String? = null,
    @JsonProperty("assists") val assists: String? = null,
    @JsonProperty("rating") val rating: String? = null,
    @JsonProperty("mom") val manOfTheMatch: String? = null,
    @JsonProperty("shots") val shots: String? = null,
    @JsonProperty("passattempts") val passAttempts: String? = null,
    @JsonProperty("passesmade") val passesMade: String? = null,
    @JsonProperty("tackleattempts") val tackleAttempts: String? = null,
    @JsonProperty("tacklesmade") val tacklesMade: String? = null,
    @JsonProperty("redcards") val redCards: String? = null,
    @JsonProperty("saves") val saves: String? = null,
    @JsonProperty("goalsconceded") val goalsConceded: String? = null,
    @JsonProperty("secondsplayed") val secondsPlayed: String? = null,
)
