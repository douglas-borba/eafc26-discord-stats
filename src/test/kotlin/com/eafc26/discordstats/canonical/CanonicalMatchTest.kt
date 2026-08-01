package com.eafc26.discordstats.canonical

import com.eafc26.discordstats.application.interpretation.MatchInterpreter
import com.eafc26.discordstats.application.story.MatchStoryExtractor
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.ea.mapping.EaMatchMapper
import com.eafc26.discordstats.ea.mapping.MatchNormalizationResult
import com.eafc26.discordstats.ea.model.ClubDetails
import com.eafc26.discordstats.ea.model.ClubMatchEntry
import com.eafc26.discordstats.ea.model.MatchResponse
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant

class CanonicalMatchTest {
    @Test
    fun `current envelope uses explicit schema and engine versions`() {
        val match = canonical()

        assertThat(match.schemaVersion).isEqualTo(CanonicalMatch.CURRENT_SCHEMA_VERSION)
        assertThat(match.engineVersion).isEqualTo(CanonicalMatch.CURRENT_ENGINE_VERSION)
        assertThat(match.generatedAt).isEqualTo(Instant.EPOCH)
    }

    @Test
    fun `engine version requires semantic format`() {
        assertThatThrownBy { EngineVersion("v1") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun canonical(): CanonicalMatch {
        val source = MatchResponse(
            matchId = "canonical-test",
            timestamp = 1_718_500_000L,
            clubs = linkedMapOf(
                "ours" to ClubMatchEntry(ClubDetails("Ours", "ours"), score = "1", result = "1"),
                "theirs" to ClubMatchEntry(ClubDetails("Theirs", "theirs"), score = "0", result = "0"),
            ),
        )
        val match = (EaMatchMapper().map(source) as MatchNormalizationResult.Success).match
        val interpretation = MatchInterpreter().interpret(match, ClubId("ours"))
        return CanonicalMatch.current(
            match,
            interpretation,
            MatchStoryExtractor().extract(interpretation),
            Instant.EPOCH,
        )
    }
}
