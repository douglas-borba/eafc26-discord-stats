package com.eafc26.discordstats.ea.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Represents a single entry from the /members/stats endpoint.
 *
 * The endpoint returns an array of objects, each containing the platform
 * gamertag ([playerName]) and the in-game Virtual Pro name ([proName]).
 *
 * Used to resolve display names: [proName] is shown in Discord when
 * available; otherwise [playerName] is used as fallback.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class MemberStats(
    @JsonProperty("playername") val playerName: String? = null,
    @JsonProperty("proName")    val proName: String? = null,
)

