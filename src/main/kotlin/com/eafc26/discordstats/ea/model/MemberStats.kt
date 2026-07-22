package com.eafc26.discordstats.ea.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Represents a single entry from the /members/stats endpoint.
 *
 * The endpoint returns `{"members": [{…}, …]}`. Each member object uses
 * `"name"` (not `"playername"`) as the gamertag identifier, and `"proName"`
 * (camelCase) for the in-game Virtual Pro display name.
 *
 * Both field names are confirmed from the live EA API response (2026-07-22).
 *
 * Used to resolve display names: [proName] is shown in Discord when available;
 * otherwise the match-payload [playerName] (from the `"playername"` field) is
 * used as fallback.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class MemberStats(
    @JsonProperty("name")    val playerName: String? = null,
    @JsonProperty("proName") val proName: String? = null,
)

