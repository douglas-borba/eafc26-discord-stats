package com.eafc26.discordstats.service

import com.eafc26.discordstats.application.interpretation.MatchInterpreter
import com.eafc26.discordstats.application.story.MatchStoryExtractor
import com.eafc26.discordstats.canonical.CanonicalMatch
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.ea.mapping.EaMatchMapper
import com.eafc26.discordstats.ea.mapping.MatchNormalizationResult
import com.eafc26.discordstats.ea.model.MatchResponse
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant

/**
 * Shared runtime boundary that turns one EA match DTO into the complete
 * canonical record. It contains no persistence, delivery or presentation.
 */
@Component
class CanonicalMatchFactory(
    private val clock: Clock = Clock.systemUTC(),
) {
    private val eaMatchMapper = EaMatchMapper()
    private val matchInterpreter = MatchInterpreter()
    private val matchStoryExtractor = MatchStoryExtractor()

    fun create(
        source: MatchResponse,
        perspectiveClubId: String,
        proNames: Map<String, String> = emptyMap(),
    ): CanonicalMatch {
        val normalized = when (val result = eaMatchMapper.map(source, proNames)) {
            is MatchNormalizationResult.Success -> result.match
            is MatchNormalizationResult.Rejected -> error(
                "EA match ${source.matchId} cannot be normalized: " +
                    result.errors.joinToString { it.message }
            )
        }
        val interpretation = matchInterpreter.interpret(normalized, ClubId(perspectiveClubId))
        val stories = matchStoryExtractor.extract(interpretation)
        return CanonicalMatch.current(
            footballMatch = normalized,
            interpretation = interpretation,
            stories = stories,
            generatedAt = Instant.now(clock),
        )
    }
}
