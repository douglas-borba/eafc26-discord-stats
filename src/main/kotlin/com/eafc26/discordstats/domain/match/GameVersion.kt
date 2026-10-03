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

    /** The next supported EA contract generation, if one exists. */
    fun nextGeneration(): GameVersion? = entries.getOrNull(ordinal + 1)

    /**
     * Validates an operational generation transition. The admin operation is
     * intentionally forward-only so a downgrade cannot silently route future
     * acquisitions back to an older EA contract.
     */
    fun transitionTo(target: GameVersion): GameVersion {
        if (target == this) return this
        require(target == nextGeneration()) {
            "Game version transition from $name to ${target.name} is not allowed"
        }
        return target
    }

    /** FC27 transport is known, but its FC26 advanced-code semantics are not. */
    fun supportsRevalidatedAdvancedStats(): Boolean = this == FC26

    companion object {
        fun fromGatewayValue(raw: String?): GameVersion? = raw
            ?.trim()
            ?.uppercase()
            ?.let { value -> entries.firstOrNull { it.name == value } }
    }
}
