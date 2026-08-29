package com.eafc26.discordstats.domain.match

/**
 * Preserves the original EA event histogram strings so that unknown codes
 * are available for exploratory analysis without altering the decoded
 * [AdvancedPlayerStats] pipeline.
 *
 * Values are nullable: null means the aggregate was absent in the EA payload
 * (UNAVAILABLE), which is semantically different from an empty string (no events).
 */
data class RawEventAggregates(
    val aggregate0: String? = null,
    val aggregate1: String? = null,
    val aggregate2: String? = null,
    val aggregate3: String? = null,
)
