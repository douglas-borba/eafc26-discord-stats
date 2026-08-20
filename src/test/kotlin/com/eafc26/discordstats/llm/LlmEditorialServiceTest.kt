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

        val mockContext = EditorialContext(
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

        whenever(contextBuilder.buildFullContext(any(), any(), any())).thenReturn(mockContext)
        whenever(contextBuilder.buildPanoramaContext(any(), any(), any())).thenReturn(mockContext)

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

    private fun successResult(text: String = "O clube apresenta desempenho sólido com 8 vitórias nas últimas 10 partidas, evidenciando consistência ofensiva ao marcar 28 gols. A equipe mantém solidez defensiva e busca consolidar posição na tabela.") =
        LlmEditorialResult.Success(text, LlmMetadata("test-provider", "test-model", 50, 30))

    @Nested
    inner class PanoramaIdempotency {

        @Test
        fun `panorama reads only the requested club latest ten matches`() {
            val clubA = ClubId("club-a")
            val clubB = ClubId("club-b")
            val match = canonical("shared", clubA.value)
            val recentMatches = listOf(canonical("shared", clubA.value), canonical("a2", clubA.value))
            whenever(historyService.latest(clubA, LlmEditorialService.PANORAMA_MATCH_COUNT)).thenReturn(recentMatches)
            whenever(panoramaRepository.findByContextKey(eq(clubA.value), any())).thenReturn(null)
            whenever(provider.generatePanorama(any())).thenReturn(successResult())

            service.generateAndPersistPanorama(match)

            verify(historyService).latest(clubA, LlmEditorialService.PANORAMA_MATCH_COUNT)
            verify(historyService, never()).latest(clubB, LlmEditorialService.PANORAMA_MATCH_COUNT)
            verify(panoramaRepository).upsert(org.mockito.kotlin.check { record ->
                assertThat(record.clubId).isEqualTo(clubA.value)
                assertThat(record.matchIds).containsExactly("shared", "a2")
            })
        }

        @Test
        fun `same three matches do not regenerate`() {
            val match = canonical("m1")
            val recentMatches = listOf(canonical("m1"), canonical("m2"), canonical("m3"))
            whenever(historyService.latest(ClubId("club1"), LlmEditorialService.PANORAMA_MATCH_COUNT)).thenReturn(recentMatches)
            whenever(panoramaRepository.findByContextKey(eq("club1"), any()))
                .thenReturn(PanoramaRecord(
                    clubId = "club1", contextKey = "existing-key", matchIds = listOf("m1", "m2", "m3"),
                    narrative = "O clube mantém desempenho consistente com 7 vitórias nas últimas 10 partidas. A equipe demonstra solidez ao marcar 24 gols e sofrer apenas 11 no período.",
                    provider = "openrouter", model = "anthropic/claude-sonnet-4",
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
            whenever(historyService.latest(ClubId("club1"), LlmEditorialService.PANORAMA_MATCH_COUNT)).thenReturn(recentMatches)
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
            whenever(historyService.latest(ClubId("club1"), LlmEditorialService.PANORAMA_MATCH_COUNT)).thenReturn(recentMatches)
            whenever(panoramaRepository.findByContextKey(eq("club1"), any())).thenReturn(null)
            whenever(provider.generatePanorama(any())).thenReturn(successResult())

            service.generateAndPersistPanorama(match)

            // Context key must use chronological order (as returned by historyService.latest)
            val expectedKey = LlmEditorialService.computeContextKey(
                "club1", listOf("m1", "m2", "m3"), LlmEditorialService.PROMPT_VERSION, enabledProps.model
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
            whenever(historyService.latest(ClubId("club1"), LlmEditorialService.PANORAMA_MATCH_COUNT)).thenReturn(listOf(match))
            whenever(panoramaRepository.findByContextKey(any(), any())).thenReturn(null)
            val validText = "O clube vive grande fase com 8 vitórias em 10 partidas. A equipe marcou 30 gols e sofreu apenas 12, consolidando-se entre os favoritos do campeonato."
            whenever(provider.generatePanorama(any())).thenReturn(successResult(validText))

            service.generateAndPersistPanorama(match)

            verify(panoramaRepository).upsert(org.mockito.kotlin.check { record ->
                assertThat(record.clubId).isEqualTo("club1")
                assertThat(record.narrative).isEqualTo(validText)
                assertThat(record.status).isEqualTo("success")
                assertThat(record.provider).isNotNull()
                assertThat(record.promptVersion).isEqualTo("v3")
                assertThat(record.generatedAt).isEqualTo(fixedInstant)
            })
        }

        @Test
        fun `provider failure preserves deterministic panorama`() {
            val match = canonical("m1")
            whenever(historyService.latest(ClubId("club1"), LlmEditorialService.PANORAMA_MATCH_COUNT)).thenReturn(listOf(match))
            whenever(panoramaRepository.findByContextKey(any(), any())).thenReturn(null)
            whenever(provider.generatePanorama(any()))
                .thenReturn(LlmEditorialResult.Failure("timeout", RuntimeException("Connection timed out")))

            service.generateAndPersistPanorama(match)

            verify(panoramaRepository).upsert(org.mockito.kotlin.check { record ->
                assertThat(record.status).isEqualTo("failed")
                assertThat(record.narrative).isNull()
                assertThat(record.errorCategory).isEqualTo("timeout")
            })

            val persisted = service.getPersistedPanorama(ClubId("club1"))
            assertThat(persisted).isNull()
        }

        @Test
        fun `HTTP 429 rate limit uses deterministic fallback`() {
            val match = canonical("m1")
            whenever(historyService.latest(ClubId("club1"), LlmEditorialService.PANORAMA_MATCH_COUNT)).thenReturn(listOf(match))
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
            whenever(historyService.latest(ClubId("club1"), LlmEditorialService.PANORAMA_MATCH_COUNT)).thenReturn(listOf(match))
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
    inner class ResilientCache {

        @Test
        fun `cached valid panorama is reused`() {
            val match = canonical("m1")
            val recentMatches = listOf(canonical("m1"), canonical("m2"), canonical("m3"))
            whenever(historyService.latest(ClubId("club1"), LlmEditorialService.PANORAMA_MATCH_COUNT)).thenReturn(recentMatches)
            whenever(panoramaRepository.findByContextKey(eq("club1"), any()))
                .thenReturn(PanoramaRecord(
                    clubId = "club1", contextKey = "key", matchIds = listOf("m1", "m2", "m3"),
                    narrative = "Grande momento do clube com vitórias consecutivas.", 
                    provider = "openrouter", model = "anthropic/claude-sonnet-4",
                    promptVersion = "v3", status = "success", generatedAt = fixedInstant,
                ))

            service.generateAndPersistPanorama(match)

            verify(provider, never()).generatePanorama(any())
            verify(panoramaRepository, never()).upsert(any())
        }

        @Test
        fun `cached prompt echo is ignored and regenerated`() {
            val match = canonical("m1")
            val recentMatches = listOf(canonical("m1"), canonical("m2"), canonical("m3"))
            whenever(historyService.latest(ClubId("club1"), LlmEditorialService.PANORAMA_MATCH_COUNT)).thenReturn(recentMatches)
            whenever(panoramaRepository.findByContextKey(eq("club1"), any()))
                .thenReturn(PanoramaRecord(
                    clubId = "club1", contextKey = "key", matchIds = listOf("m1", "m2", "m3"),
                    narrative = "We need to write between 350 and 550 characters. Must compare the matches. Should not use markdown.",
                    provider = "openrouter", model = "anthropic/claude-sonnet-4",
                    promptVersion = "v3", status = "success", generatedAt = fixedInstant,
                ))
            whenever(provider.generatePanorama(any())).thenReturn(successResult("A equipe atravessa fase irregular com 5 vitórias e 5 derrotas nas últimas partidas. O ataque produziu 20 gols, mas a defesa sofreu 18, comprometendo o aproveitamento no campeonato."))

            service.generateAndPersistPanorama(match)

            verify(provider).generatePanorama(any())
            verify(panoramaRepository).upsert(org.mockito.kotlin.check { record ->
                assertThat(record.narrative).contains("equipe atravessa fase irregular")
                assertThat(record.status).isEqualTo("success")
            })
        }

        @Test
        fun `cached prompt echo in Portuguese is ignored and regenerated`() {
            val match = canonical("m1")
            val recentMatches = listOf(canonical("m1"), canonical("m2"), canonical("m3"))
            whenever(historyService.latest(ClubId("club1"), LlmEditorialService.PANORAMA_MATCH_COUNT)).thenReturn(recentMatches)
            whenever(panoramaRepository.findByContextKey(eq("club1"), any()))
                .thenReturn(PanoramaRecord(
                    clubId = "club1", contextKey = "key", matchIds = listOf("m1", "m2", "m3"),
                    narrative = "Escreva entre 350 e 550 caracteres. Utilize apenas os fatos. Compare as partidas. Não utilize markdown.",
                    provider = "openrouter", model = "anthropic/claude-sonnet-4",
                    promptVersion = "v3", status = "success", generatedAt = fixedInstant,
                ))
            whenever(provider.generatePanorama(any())).thenReturn(successResult("O clube busca recuperação após sequência de resultados irregulares, com 6 vitórias e 4 derrotas. A campanha revela fragilidade defensiva ao sofrer 22 gols, demandando ajustes para retomar consistência."))

            service.generateAndPersistPanorama(match)

            verify(provider).generatePanorama(any())
            verify(panoramaRepository).upsert(org.mockito.kotlin.check { record ->
                assertThat(record.narrative).contains("clube busca recuperação")
                assertThat(record.status).isEqualTo("success")
            })
        }

        @Test
        fun `fresh prompt echo is not persisted`() {
            val match = canonical("m1")
            whenever(historyService.latest(ClubId("club1"), LlmEditorialService.PANORAMA_MATCH_COUNT)).thenReturn(listOf(match))
            whenever(panoramaRepository.findByContextKey(any(), any())).thenReturn(null)
            whenever(provider.generatePanorama(any()))
                .thenReturn(successResult("We need to produce a narrative. Must compare all matches. Should not use lists or markdown."))

            service.generateAndPersistPanorama(match)

            verify(panoramaRepository).upsert(org.mockito.kotlin.check { record ->
                assertThat(record.status).isEqualTo("failed")
                assertThat(record.narrative).isNull()
                assertThat(record.errorCategory).isEqualTo("llm_prompt_echo")
            })
        }

        @Test
        fun `cached failed status prevents regeneration`() {
            val match = canonical("m1")
            val recentMatches = listOf(canonical("m1"), canonical("m2"), canonical("m3"))
            whenever(historyService.latest(ClubId("club1"), LlmEditorialService.PANORAMA_MATCH_COUNT)).thenReturn(recentMatches)
            whenever(panoramaRepository.findByContextKey(eq("club1"), any()))
                .thenReturn(PanoramaRecord(
                    clubId = "club1", contextKey = "key", matchIds = listOf("m1", "m2", "m3"),
                    narrative = null, provider = "openrouter", model = "anthropic/claude-sonnet-4",
                    promptVersion = "v3", status = "failed", errorCategory = "timeout",
                    generatedAt = fixedInstant,
                ))

            service.generateAndPersistPanorama(match)

            verify(provider, never()).generatePanorama(any())
            verify(panoramaRepository, never()).upsert(any())
        }

        @Test
        fun `cached prompt echo failure allows retry`() {
            val match = canonical("m1")
            val recentMatches = listOf(canonical("m1"), canonical("m2"), canonical("m3"))
            whenever(historyService.latest(ClubId("club1"), LlmEditorialService.PANORAMA_MATCH_COUNT)).thenReturn(recentMatches)
            whenever(panoramaRepository.findByContextKey(eq("club1"), any()))
                .thenReturn(PanoramaRecord(
                    clubId = "club1", contextKey = "key", matchIds = listOf("m1", "m2", "m3"),
                    narrative = null, provider = "openrouter", model = "anthropic/claude-sonnet-4",
                    promptVersion = "v3", status = "failed", errorCategory = "llm_prompt_echo",
                    generatedAt = fixedInstant,
                ))
            whenever(provider.generatePanorama(any())).thenReturn(successResult("A campanha recente mostra desempenho positivo com 7 vitórias em 10 partidas. O time marcou 25 gols e sofreu 15, consolidando-se entre os favoritos da competição."))

            service.generateAndPersistPanorama(match)

            verify(provider).generatePanorama(any())
            verify(panoramaRepository).upsert(org.mockito.kotlin.check { record ->
                assertThat(record.narrative).contains("campanha recente mostra desempenho positivo")
                assertThat(record.status).isEqualTo("success")
            })
        }
    }

    @Nested
    inner class NextJsIntegration {

        @Test
        fun `reads persisted panorama from repository for current context`() {
            val recentMatches = listOf(canonical("m1"), canonical("m2"), canonical("m3"))
            val recentIds = recentMatches.map { it.matchId }
            whenever(historyService.latestMatchIds(ClubId("club1"), LlmEditorialService.PANORAMA_MATCH_COUNT)).thenReturn(recentIds)
            
            val expectedKey = LlmEditorialService.computeContextKey(
                "club1", listOf("m1", "m2", "m3"), "v3", enabledProps.model
            )
            
            whenever(panoramaRepository.findSuccessfulByContextKey("club1", expectedKey))
                .thenReturn(PanoramaRecord(
                    clubId = "club1", contextKey = expectedKey, matchIds = listOf("m1", "m2", "m3"),
                    narrative = "AI panorama text", provider = "openrouter",
                    model = "anthropic/claude-sonnet-4", promptVersion = "v3",
                    status = "success", generatedAt = fixedInstant,
                ))

            val result = service.getPersistedPanorama(ClubId("club1"))
            assertThat(result).isEqualTo("AI panorama text")
            verify(historyService).latestMatchIds(ClubId("club1"), LlmEditorialService.PANORAMA_MATCH_COUNT)
            verify(historyService, never()).latest(ClubId("club1"), LlmEditorialService.PANORAMA_MATCH_COUNT)
        }

        @Test
        fun `returns null when no panorama exists for current context`() {
            val recentMatches = listOf(canonical("m1"), canonical("m2"), canonical("m3"))
            val recentIds = recentMatches.map { it.matchId }
            whenever(historyService.latestMatchIds(ClubId("club1"), LlmEditorialService.PANORAMA_MATCH_COUNT))
                .thenReturn(recentIds)
            
            val expectedKey = LlmEditorialService.computeContextKey(
                "club1", listOf("m1", "m2", "m3"), "v3", enabledProps.model
            )
            
            whenever(panoramaRepository.findSuccessfulByContextKey("club1", expectedKey)).thenReturn(null)

            val result = service.getPersistedPanorama(ClubId("club1"))
            assertThat(result).isNull()
        }

        @Test
        fun `lightweight recent IDs preserve the legacy canonical context key byte for byte`() {
            val recentMatches = listOf(canonical("m3"), canonical("m2"), canonical("m1"))
            val lightweightIds = recentMatches.map { it.matchId }
            val legacyKey = LlmEditorialService.computeContextKey(
                "club1",
                recentMatches.map { it.matchId.value },
                LlmEditorialService.PROMPT_VERSION,
                enabledProps.model,
            )
            val lightweightKey = LlmEditorialService.computeContextKey(
                "club1",
                lightweightIds.map { it.value },
                LlmEditorialService.PROMPT_VERSION,
                enabledProps.model,
            )
            whenever(historyService.latestMatchIds(ClubId("club1"), LlmEditorialService.PANORAMA_MATCH_COUNT))
                .thenReturn(lightweightIds)
            whenever(panoramaRepository.findSuccessfulByContextKey("club1", legacyKey))
                .thenReturn(PanoramaRecord(
                    clubId = "club1", contextKey = legacyKey, matchIds = lightweightIds.map { it.value },
                    narrative = "Panorama já persistido", provider = "openrouter", model = enabledProps.model,
                    promptVersion = LlmEditorialService.PROMPT_VERSION, status = "success", generatedAt = fixedInstant,
                ))

            assertThat(lightweightKey).isEqualTo(legacyKey)
            assertThat(service.getPersistedPanorama(ClubId("club1"))).isEqualTo("Panorama já persistido")
            verify(panoramaRepository).findSuccessfulByContextKey("club1", legacyKey)
        }
    }

    @Nested
    inner class DiscordDeduplication {

        @Test
        fun `retries do not create duplicate LLM generation`() {
            val match = canonical("m1")
            whenever(historyService.latest(ClubId("club1"), LlmEditorialService.PANORAMA_MATCH_COUNT)).thenReturn(listOf(match))
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
        fun `different chronological order produces different key`() {
            // Chronological order matters - ensures cache invalidation when match order changes
            val key1 = LlmEditorialService.computeContextKey("c1", listOf("m2", "m1"), "v1", "model")
            val key2 = LlmEditorialService.computeContextKey("c1", listOf("m1", "m2"), "v1", "model")
            assertThat(key1).isNotEqualTo(key2)
        }

        @Test
        fun `different model produces different key`() {
            val key1 = LlmEditorialService.computeContextKey("c1", listOf("m1"), "v1", "model-a")
            val key2 = LlmEditorialService.computeContextKey("c1", listOf("m1"), "v1", "model-b")
            assertThat(key1).isNotEqualTo(key2)
        }

        @Test
        fun `same matches have independent context keys for different clubs`() {
            val matches = listOf("shared", "m2")
            val keyA = LlmEditorialService.computeContextKey("club-a", matches, "v1", "model")
            val keyB = LlmEditorialService.computeContextKey("club-b", matches, "v1", "model")

            assertThat(keyA).isNotEqualTo(keyB)
        }
    }
}
