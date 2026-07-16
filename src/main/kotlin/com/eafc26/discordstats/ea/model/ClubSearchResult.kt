package com.eafc26.discordstats.ea.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.eafc26.discordstats.ea.normalizeEaText

/**
 * One entry returned by /allTimeLeaderboard/search.
 *
 * Live payload shape (EA FC 26, confirmed 2025-07):
 * {
 *   "clubId":        "1104972",          // string
 *   "clubName":      "Associação BF",    // root-level name
 *   "clubInfo": { "name": "...", "clubId": 1104972, "regionId": ..., "teamId": ... },
 *   "platform":      "common-gen5",
 *   "currentDivision": "4",             // string, not int
 *   "wins","losses","ties","gamesPlayed","goals","goalsAgainst","points": all strings
 * }
 *
 * There is no root-level "name" field. resolvedName() prefers clubName over clubInfo.name.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ClubSearchResult(
    @JsonProperty("clubId") val clubId: String,
    @JsonProperty("clubName") val clubName: String? = null,
    @JsonProperty("clubInfo") val clubInfo: ClubInfo? = null,
    @JsonProperty("platform") val platform: String? = null,
    @JsonProperty("currentDivision") val currentDivision: String? = null,
    @JsonProperty("wins") val wins: String? = null,
    @JsonProperty("losses") val losses: String? = null,
    @JsonProperty("ties") val ties: String? = null,
    @JsonProperty("gamesPlayed") val gamesPlayed: String? = null,
    @JsonProperty("goals") val goals: String? = null,
    @JsonProperty("goalsAgainst") val goalsAgainst: String? = null,
    @JsonProperty("points") val points: String? = null,
) {
    fun resolvedName(): String? = (clubName ?: clubInfo?.name)?.let { normalizeEaText(it) }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class ClubInfo(
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("clubId") val clubId: Long? = null,
    @JsonProperty("regionId") val regionId: Long? = null,
    @JsonProperty("teamId") val teamId: Long? = null,
)
