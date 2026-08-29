package com.eafc26.discordstats.ea.mapping

/**
 * Displays an internal reverse-engineering candidate for EA's raw `pos` value.
 * This is Explorer-only evidence, not a statement about a player's actual
 * position during a match and not a domain mapping.
 */
object EaPositionCodeDecoder {
    data class DecodedPosition(
        val rawCode: String?,
        val candidateLabel: String?,
        /** Transport classification; never a sporting conclusion. */
        val classification: String,
        /** An external candidate label is not proof of actual match position. */
        val semanticStatus: String,
    )

    private val externalCandidates = mapOf(
        0 to "GK", 1 to "SW", 2 to "RWB", 3 to "RB", 4 to "RCB", 5 to "CB", 6 to "LCB",
        7 to "LB", 8 to "LWB", 9 to "RDM", 10 to "CDM", 11 to "LDM", 12 to "RM",
        13 to "RCM", 14 to "CM", 15 to "LCM", 16 to "LM", 17 to "RAM", 18 to "CAM",
        19 to "LAM", 20 to "RF", 21 to "CF", 22 to "LF", 23 to "RW", 24 to "RS",
        25 to "ST", 26 to "LS", 27 to "LW",
    )

    /**
     * `pos` is preserved as an opaque EA transport value. Numeric values have
     * an external, unverified candidate table; literal role labels are valid
     * EA values, not malformed numeric codes.
     */
    fun decode(rawCode: String?): DecodedPosition {
        if (rawCode == null) return DecodedPosition(null, null, "MISSING", "NO_CANDIDATE")
        val normalized = rawCode.trim()
        if (normalized.isEmpty()) return DecodedPosition(rawCode, null, "UNKNOWN_VALUE", "NO_CANDIDATE")
        if (normalized == "forward") {
            return DecodedPosition(rawCode, null, "EA_ROLE_LABEL", "UNVERIFIED_EA_ROLE_LABEL")
        }
        val numeric = normalized.toIntOrNull() ?: return DecodedPosition(rawCode, null, "UNKNOWN_VALUE", "NO_CANDIDATE")
        val label = externalCandidates[numeric]
        return if (label == null) DecodedPosition(rawCode, null, "UNKNOWN_CODE", "NO_CANDIDATE")
        else DecodedPosition(rawCode, label, "NUMERIC_EXTERNAL_CANDIDATE", "UNVERIFIED_EXTERNAL_MAPPING")
    }
}
