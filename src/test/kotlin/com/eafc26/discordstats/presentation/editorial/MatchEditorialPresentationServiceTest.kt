package com.eafc26.discordstats.presentation.editorial

import com.eafc26.discordstats.canonical.CanonicalMatch
import com.eafc26.discordstats.config.PhraseBank
import com.eafc26.discordstats.domain.interpretation.MatchInterpretation
import com.eafc26.discordstats.domain.match.FootballMatch
import com.eafc26.discordstats.domain.match.MatchId
import com.eafc26.discordstats.domain.story.MatchStories
import com.eafc26.discordstats.presentation.MatchSummaryBuilder
import com.eafc26.discordstats.presentation.MatchSummaryPresentation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.kotlin.never
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@ExtendWith(MockitoExtension::class)
class MatchEditorialPresentationServiceTest {

    @Mock
    private lateinit var matchSummaryBuilder: MatchSummaryBuilder

    @Mock
    private lateinit var phraseBank: PhraseBank

    @Mock
    private lateinit var repository: MatchEditorialPresentationRepository

    @Mock
    private lateinit var clock: Clock

    @InjectMocks
    private lateinit var service: MatchEditorialPresentationService

    @Test
    fun `generates and persists editorial presentation`() {
        val canonical = mockCanonicalMatch()
        val presentation = mockPresentation()
        val instant = Instant.parse("2026-08-03T10:00:00Z")

        whenever(matchSummaryBuilder.build(any(), any(), any(), any(), any())).thenReturn(presentation)
        whenever(phraseBank.getAll()).thenReturn(mapOf("MVP" to listOf("Phrase 1")))
        whenever(clock.instant()).thenReturn(instant)

        service.generateAndPersist(canonical)

        verify(matchSummaryBuilder).build(
            footballMatch = any(),
            interpretation = any(),
            stories = any(),
            zoneId = any(),
            forceRandomPhrases = any(),
        )
        verify(repository).upsertIfNewer(any())
    }

    @Test
    fun `does not propagate repository failure`() {
        val canonical = mockCanonicalMatch()
        val presentation = mockPresentation()

        whenever(matchSummaryBuilder.build(any(), any(), any(), any(), any())).thenReturn(presentation)
        whenever(phraseBank.getAll()).thenReturn(mapOf("MVP" to listOf("Phrase 1")))
        whenever(clock.instant()).thenReturn(Instant.now())
        whenever(repository.upsertIfNewer(any())).thenThrow(RuntimeException("DB error"))

        // Should NOT throw - editorial failures must not block canonical pipeline
        service.generateAndPersist(canonical)

        verify(repository).upsertIfNewer(any())
    }

    @Test
    fun `calculates phrase bank version deterministically`() {
        whenever(phraseBank.getAll()).thenReturn(
            mapOf(
                "MVP" to listOf("Phrase A", "Phrase B"),
                "XERIFE" to listOf("Phrase C"),
            )
        )

        val version1 = service.calculatePhraseBankVersion()
        val version2 = service.calculatePhraseBankVersion()

        assertThat(version1).isEqualTo(version2)
        assertThat(version1).hasSize(64) // SHA-256 hex = 64 chars
    }

    @Test
    fun `phrase bank version changes when phrases change`() {
        whenever(phraseBank.getAll()).thenReturn(
            mapOf("MVP" to listOf("Phrase A"))
        )
        val version1 = service.calculatePhraseBankVersion()

        whenever(phraseBank.getAll()).thenReturn(
            mapOf("MVP" to listOf("Phrase B"))
        )
        val version2 = service.calculatePhraseBankVersion()

        assertThat(version1).isNotEqualTo(version2)
    }

    private fun mockCanonicalMatch(): CanonicalMatch {
        val footballMatch = org.mockito.kotlin.mock<FootballMatch>()
        val interpretation = org.mockito.kotlin.mock<MatchInterpretation>()
        val stories = org.mockito.kotlin.mock<MatchStories>()

        whenever(footballMatch.id).thenReturn(MatchId("match-123"))
        whenever(footballMatch.playedAt).thenReturn(Instant.parse("2026-08-03T10:00:00Z"))
        whenever(interpretation.matchId).thenReturn(MatchId("match-123"))
        whenever(interpretation.perspectiveClubId).thenReturn(
            com.eafc26.discordstats.domain.match.ClubId("club-123")
        )
        whenever(stories.matchId).thenReturn(MatchId("match-123"))

        return CanonicalMatch(
            schemaVersion = com.eafc26.discordstats.canonical.CanonicalSchemaVersion(1),
            engineVersion = com.eafc26.discordstats.canonical.EngineVersion("1.0.0"),
            generatedAt = Instant.parse("2026-08-03T10:00:00Z"),
            footballMatch = footballMatch,
            interpretation = interpretation,
            stories = stories,
        )
    }

    private fun mockPresentation(): MatchSummaryPresentation {
        return MatchSummaryPresentation(
            ourName = "Test FC",
            oppName = "Opponent FC",
            ourScore = 2,
            oppScore = 1,
            outcome = com.eafc26.discordstats.presentation.MatchOutcome(
                emoji = "🏆",
                label = "Vitória",
                color = 0x00FF00,
                type = com.eafc26.discordstats.presentation.OutcomeType.WIN,
            ),
            date = "03 ago 2026 • 10:00",
            timestamp = "2026-08-03T10:00:00Z",
            matchId = "match-123",
            goals = null,
            assists = null,
            highlights = null,
            craque = null,
            offensiveNarratives = emptyList(),
            bagre = null,
            redCard = null,
            xerife = null,
            passePrecisao = null,
            correioExtraviado = null,
            muralha = null,
        )
    }
}
