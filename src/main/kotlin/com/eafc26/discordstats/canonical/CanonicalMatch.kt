package com.eafc26.discordstats.canonical

import com.eafc26.discordstats.domain.interpretation.MatchInterpretation
import com.eafc26.discordstats.domain.match.FootballMatch
import com.eafc26.discordstats.domain.match.MatchId
import com.eafc26.discordstats.domain.story.MatchStories
import java.time.Instant

/**
 * Stable persistence envelope for one fully interpreted match.
 */
data class CanonicalMatch(
    val schemaVersion: CanonicalSchemaVersion,
    val engineVersion: EngineVersion,
    val generatedAt: Instant,
    val footballMatch: FootballMatch,
    val interpretation: MatchInterpretation,
    val stories: MatchStories,
) {
    val matchId: MatchId
        get() = footballMatch.id

    init {
        require(interpretation.matchId == matchId && stories.matchId == matchId) {
            "Canonical facts, interpretation and stories must belong to the same match"
        }
    }

    companion object {
        val CURRENT_SCHEMA_VERSION = CanonicalSchemaVersion(1)
        val CURRENT_ENGINE_VERSION = EngineVersion("1.0.0")

        fun current(
            footballMatch: FootballMatch,
            interpretation: MatchInterpretation,
            stories: MatchStories,
            generatedAt: Instant,
        ) = CanonicalMatch(
            schemaVersion = CURRENT_SCHEMA_VERSION,
            engineVersion = CURRENT_ENGINE_VERSION,
            generatedAt = generatedAt,
            footballMatch = footballMatch,
            interpretation = interpretation,
            stories = stories,
        )
    }
}

@JvmInline
value class CanonicalSchemaVersion(val value: Int) {
    init {
        require(value > 0) { "Canonical schema version must be positive" }
    }
}

@JvmInline
value class EngineVersion(val value: String) {
    init {
        require(value.matches(Regex("\\d+\\.\\d+\\.\\d+"))) {
            "Engine version must use semantic major.minor.patch format"
        }
    }
}
