package com.eafc26.discordstats.ea.mapping

import com.eafc26.discordstats.ea.model.MatchResponse
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Verifies normalization against every match fixture currently used by the app.
 *
 * The test fixture remains synthetic, as documented by the project README. The
 * name is retained from the Phase 2 roadmap and represents payload-shaped fixture
 * compatibility rather than a claim that the data was captured live.
 */
class LiveFixtureNormalizationTest {

    private val objectMapper = jacksonObjectMapper()
    private val mapper = EaMatchMapper()

    @Test
    fun `all EA match test fixtures normalize successfully`() {
        val matches = readMatches("fixtures/clubs-matches.json")

        assertThat(matches).isNotEmpty()
        matches.forEach { source ->
            val result = mapper.map(source)
            assertThat(result)
                .describedAs("Expected match ${source.matchId} to normalize")
                .isInstanceOf(MatchNormalizationResult.Success::class.java)

            val normalized = (result as MatchNormalizationResult.Success).match
            assertThat(normalized.id.value).isEqualTo(source.matchId)
            assertThat(normalized.participants.map { it.club.id.value })
                .containsExactlyElementsOf(source.clubs.keys)
        }
    }

    @Test
    fun `development fixture normalizes through the same ACL`() {
        val matches = readMatches("fixtures/dev/latest-matches.json")

        assertThat(matches).hasSize(1)
        val result = mapper.map(matches.single())

        assertThat(result).isInstanceOf(MatchNormalizationResult.Success::class.java)
        val normalized = (result as MatchNormalizationResult.Success).match
        assertThat(normalized.participants).hasSize(2)
        assertThat(normalized.participants.flatMap { it.players }).isNotEmpty()
    }

    private fun readMatches(path: String): List<MatchResponse> {
        val stream = checkNotNull(javaClass.classLoader.getResourceAsStream(path)) {
            "Missing match fixture: $path"
        }
        return stream.use { objectMapper.readValue(it) }
    }
}
