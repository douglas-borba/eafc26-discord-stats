package com.eafc26.discordstats.domain.match

/**
 * Recognition reported by the upstream EA match source.
 *
 * This is a source fact, not the application's deterministic Craque decision.
 */
data class EaRecognition(
    val manOfTheMatch: Boolean?,
)
