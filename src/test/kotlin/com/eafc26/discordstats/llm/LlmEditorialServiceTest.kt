package com.eafc26.discordstats.llm

import com.eafc26.discordstats.canonical.CanonicalMatch
import com.eafc26.discordstats.canonical.CanonicalSchemaVersion
import com.eafc26.discordstats.canonical.EngineVersion
import com.eafc26.discordstats.domain.interpretation.MatchInterpretation
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.FootballMatch
import com.eafc26.discordstats.domain.match.MatchId
import com.eafc26.discordstats.domain.story.MatchStories
import com.eafc26.discordstats.service.MatchHistoryService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class LlmEditorialServiceTest {

    private lateinit var contextBuilder: EditorialContextBuilder
    private lateinit var provider: EditorialLlmProvider
    private lateinit var historyService: MatchHistoryService
    private lateinit var panoramaRepository: PanoramaRepository
    private lateinit var clock: Clock
    private lateinit var service: LlmEditorialService

    private val fixedInstant = Instant.parse("2026-08-05T12:00:00Z")
    private val enabledProps = LlmProperties(enabled = true)

    @BeforeEach
    fun setUp() {
        contextBuilder = mock()
        provider = mock()
        historyService = mock()
        panoramaRepository = mock()
        clock = Clock.fixed(fixedInstant, ZoneId.of("UTC"))

        whenever(contextBuilder.buildFullContext(any(), any(), any())).thenReturn(
            EditorialContext(
                match = MatchContext(
                    ourClub = "Associação BF", opponent = "Rival FC",
                    ourScore = 3, opponentScore = 1,
                    outcome = com.eafc26.discordstats.domain.interpretation.MatchOutcome.WIN,
                    date = "05/08/2026", mvp = null, worstPerformer = null,
                    xerife = null, goalkeeper = null, goals = emptyList(),
                    assists = emptyList(), teamAverageRating = null, notableEvents = emptyList(),
                ),
                recentForm = null,
            )
        )

        service = LlmEditorialService(
            contextBuilder, provider, historyService, enabledProps, panoramaRepository, clock,
        )
    }

    private fun canonical(matchId: String = "m1", clubId: String = "club1"): CanonicalMatch {
        val footballMatch = mock<FootballMatch>()
        val interpretation = mock<MatchInterpretation>()
        val stories = mock<MatchStories>()
        whenever(footballMatch.id).thenReturn(MatchId(matchId))
        whenever(footballMatch.playedAt).thenReturn(fixedInstant)
        whenever(interpretation.matchId).thenReturn(MatchId(matchId))
        whenever(interpretation.perspectiveClubId).thenReturn(ClubId(clubId))
        whenever(stories.matchId).thenReturn(MatchId(matchId))
        return CanonicalMatch(
            schemaVersion = CanonicalSchemaVersion(1),
            engineVersion = EngineVersion("1.0.0"),
            generatedAt = fixedInstant,
            footballMatch = footballMatch,
            interpretation = interpretation,
            stories = stories,
        )
    }

    private fun successResult(text: String = "Narrativa gerada pelo LLM.") =
        LlmEditorialResult.Success(text, LlmMetadata("test-provider", "test-model", 50, 30))

    @Nested
    inner class PanoramaIdempotency {

        @Test
        fun `same three matches do not regenerate`() {
            val match = canonical("m1")
            val recentMatches = listOf(canonical("m1"), canonical("m2"), canonical("m3"))
            whenever(historyService.latest(any())).thenReturn(recentMatches)
            whenever(panoramaRepository.findByContextKey(eq("club1"), any()))
                .thenReturn(PanoramaRecord(
                    clubId = "club1", contextKey = "existing-key", matchIds = listOf("m1", "m2", "m3"),
                    narrative = "Existing", provider = "openrouter", model = "anthropic/claude-sonnet-4",
                    promptVersion = "v2", status = "success", generatedAt = fixedInstant,
                ))

            service.generateAndPersistPanorama(match)

            verify(provider, never()).generatePanorama(any())
            verify(panoramaRepository, never()).upsert(any())
        }

        @Test
        fun `changed recent match set regenerates`() {
            val match = canonical("m4")
            val recentMatches = listOf(canonical("m4"), canonical("m2"), canonical("m3"))
            whenever(historyService.latest(any())).thenReturn(recentMatches)
            whenever(panoramaRepository.findByContextKey(eq("club1"), any())).thenReturn(null)
            whenever(provider.generatePanorama(any())).thenReturn(successResult())

            service.generateAndPersistPanorama(match)

            verify(provider).generatePanorama(any())
            verify(panoramaRepository).upsert(any())
        }

        @Test
        fun `prompt version change regenerates`() {
            val match = canonical("m1")
            val recentMatches = listOf(canonical("m1"), canonical("m2"), canonical("m3"))
            whenever(historyService.latest(any())).thenReturn(recentMatches)
            whenever(panoramaRepository.findByContextKey(eq("club1"), any())).thenReturn(null)
            whenever(provider.generatePanorama(any())).thenReturn(successResult())

            service.generateAndPersistPanorama(match)

            val expectedKey = LlmEditorialService.computeContextKey(
                "club1", listOf("m1", "m2", "m3").sorted(), LlmEditorialService.PROMPT_VERSION, enabledProps.model
            )
            verify(panoramaRepository).findByContextKey("club1", expectedKey)
            verify(provider).generatePanorama(any())
        }
    }

    @Nested
    inner class PanoramaPersistence {

        @Test
        fun `successful panorama persists with correct fields`() {
            val match = canonical("m1")
            whenever(historyService.latest(any())).thenReturn(listOf(match))
            whenever(panoramaRepository.findByContextKey(any(), any())).thenReturn(null)
            whenever(provider.generatePanorama(any())).thenReturn(successResult("Grande fase do clube."))

            service.generateAndPersistPanorama(match)

            verify(panoramaRepository).upsert(org.mockito.kotlin.check { record ->
                assertThat(record.clubId).isEqualTo("club1")
                assertThat(record.narrative).isEqualTo("Grande fase do clube.")
                assertThat(record.status).isEqualTo("success")
                assertThat(record.provider).isNotNull()
                assertThat(record.promptVersion).isEqualTo("v3")
                assertThat(record.generatedAt).isEqualTo(fixedInstant)
            })
        }

        @Test
        fun `provider failure preserves deterministic panorama`() {
            val match = canonical("m1")
            whenever(historyService.latest(any())).thenReturn(listOf(match))
            whenever(panoramaRepository.findByContextKey(any(), any())).thenReturn(null)
            whenever(provider.generatePanorama(any()))
                .thenReturn(LlmEditorialResult.Failure("timeout", RuntimeException("Connection timed out")))

            service.generateAndPersistPanorama(match)

            verify(panoramaRepository).upsert(org.mockito.kotlin.check { record ->
                assertThat(record.status).isEqualTo("failed")
                assertThat(record.narrative).isNull()
                assertThat(record.errorCategory).isEqualTo("timeout")
            })

            val persisted = service.getPersistedPanorama("club1")
            assertThat(persisted).isNull()
        }

        @Test
        fun `HTTP 429 rate limit uses deterministic fallback`() {
            val match = canonical("m1")
            whenever(historyService.latest(any())).thenReturn(listOf(match))
            whenever(panoramaRepository.findByContextKey(any(), any())).thenReturn(null)
            whenever(provider.generatePanorama(any()))
                .thenReturn(LlmEditorialResult.Failure("OpenRouter API HTTP 429", RuntimeException("Too Many Requests")))

            service.generateAndPersistPanorama(match)

            verify(panoramaRepository).upsert(org.mockito.kotlin.check { record ->
                assertThat(record.status).isEqualTo("failed")
                assertThat(record.narrative).isNull()
                assertThat(record.errorCategory).isEqualTo("rate_limited")
            })
        }

        @Test
        fun `free model unavailability uses deterministic fallback`() {
            val match = canonical("m1")
            whenever(historyService.latest(any())).thenReturn(listOf(match))
            whenever(panoramaRepository.findByContextKey(any(), any())).thenReturn(null)
            whenever(provider.generatePanorama(any()))
                .thenReturn(LlmEditorialResult.Failure("OpenRouter API HTTP 503", RuntimeException("Service Unavailable")))

            service.generateAndPersistPanorama(match)

            verify(panoramaRepository).upsert(org.mockito.kotlin.check { record ->
                assertThat(record.status).isEqualTo("failed")
                assertThat(record.narrative).isNull()
                assertThat(record.errorCategory).isEqualTo("server_error")
            })
        }
    }

    @Nested
    inner class NextJsIntegration {

        @Test
        fun `reads persisted panorama from repository`() {
            whenever(panoramaRepository.findLatestSuccessful("club1"))
                .thenReturn(PanoramaRecord(
                    clubId = "club1", contextKey = "key", matchIds = listOf("m1"),
                    narrative = "AI panorama text", provider = "openrouter",
                    model = "anthropic/claude-sonnet-4", promptVersion = "v2",
                    status = "success", generatedAt = fixedInstant,
                ))

            val result = service.getPersistedPanorama("club1")
            assertThat(result).isEqualTo("AI panorama text")
        }

        @Test
        fun `returns null when no AI panorama exists`() {
            whenever(panoramaRepository.findLatestSuccessful("club1")).thenReturn(null)

            val result = service.getPersistedPanorama("club1")
            assertThat(result).isNull()
        }
    }

    @Nested
    inner class DiscordDeduplication {

        @Test
        fun `retries do not create duplicate LLM generation`() {
            val match = canonical("m1")
            whenever(historyService.latest(any())).thenReturn(listOf(match))
            whenever(provider.generateMatchNarrative(any())).thenReturn(successResult("Narrativa Discord."))

            val first = service.generateMatchNarrative(match)
            val second = service.generateMatchNarrative(match)

            assertThat(first).isEqualTo("Narrativa Discord.")
            assertThat(second).isEqualTo("Narrativa Discord.")
            verify(provider, times(1)).generateMatchNarrative(any())
        }
    }

    @Nested
    inner class ValidateAndTrim {

        @Test
        fun `strips markdown and respects max chars`() {
            val text = "**Bold** _italic_ text here."
            val result = LlmEditorialService.validateAndTrim(text, 450)
            assertThat(result).doesNotContain("*")
            assertThat(result).doesNotContain("_")
            assertThat(result).isEqualTo("Bold italic text here.")
        }

        @Test
        fun `truncates at sentence boundary`() {
            val text = "First sentence. Second sentence. Third sentence that is very long."
            val result = LlmEditorialService.validateAndTrim(text, 35)
            assertThat(result).isEqualTo("First sentence. Second sentence.")
        }
    }

    @Nested
    inner class ContextKey {

        @Test
        fun `same inputs produce same key`() {
            val key1 = LlmEditorialService.computeContextKey("c1", listOf("m1", "m2"), "v1", "model")
            val key2 = LlmEditorialService.computeContextKey("c1", listOf("m1", "m2"), "v1", "model")
            assertThat(key1).isEqualTo(key2)
            assertThat(key1).hasSize(64)
        }

        @Test
        fun `different match order with sorted IDs produces same key`() {
            val ids1 = listOf("m2", "m1").sorted()
            val ids2 = listOf("m1", "m2").sorted()
            val key1 = LlmEditorialService.computeContextKey("c1", ids1, "v1", "model")
            val key2 = LlmEditorialService.computeContextKey("c1", ids2, "v1", "model")
            assertThat(key1).isEqualTo(key2)
        }

        @Test
        fun `different model produces different key`() {
            val key1 = LlmEditorialService.computeContextKey("c1", listOf("m1"), "v1", "model-a")
            val key2 = LlmEditorialService.computeContextKey("c1", listOf("m1"), "v1", "model-b")
            assertThat(key1).isNotEqualTo(key2)
        }
    }
}
