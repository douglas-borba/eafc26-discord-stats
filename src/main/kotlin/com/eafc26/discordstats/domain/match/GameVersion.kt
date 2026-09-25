package com.eafc26.discordstats.domain.match

/**
 * EA Clubs contract era that produced a record.
 *
 * This is provenance, not a display preference: a raw aggregate or player
 * identity only has meaning inside the game contract that produced it.
 */
enum class GameVersion {
    FC26,
    FC27,
    ;

    /** FC27 transport is known, but its FC26 advanced-code semantics are not. */
    fun supportsRevalidatedAdvancedStats(): Boolean = this == FC26

    companion object {
        fun fromGatewayValue(raw: String?): GameVersion? = raw
            ?.trim()
            ?.uppercase()
            ?.let { value -> entries.firstOrNull { it.name == value } }
    }
}
