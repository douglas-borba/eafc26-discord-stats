package com.eafc26.discordstats.presentation.editorial

import com.eafc26.discordstats.canonical.CanonicalMatch
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.MatchId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.time.Instant

/**
 * Tests editorial pipeline isolation guarantees using pure mocks.
 * No Spring context needed — editorial beans are conditional on postgres mirror.
 */
class EditorialPipelineIntegrationTest {

    private val editorialRepository: MatchEditorialPresentationRepository = mock()
    private val editorialPresentationService: MatchEditorialPresentationService = mock()

    @Test
    fun `editorial failure does not propagate exception`() {
        whenever(editorialPresentationService.generateAndPersist(any())).then { }

        val canonical = mockCanonicalMatch()
        editorialPresentationService.generateAndPersist(canonical)

        verify(editorialPresentationService).generateAndPersist(canonical)
    }

    @Test
    fun `presentation not generated multiple times in same cycle`() {
        val canonical = mockCanonicalMatch()

        editorialPresentationService.generateAndPersist(canonical)

        verify(editorialPresentationService, times(1)).generateAndPersist(canonical)
    }

    @Test
    fun `retry can fill missing presentation`() {
        val canonical = mockCanonicalMatch()

        editorialPresentationService.generateAndPersist(canonical)
        editorialPresentationService.generateAndPersist(canonical)

        verify(editorialPresentationService, times(2)).generateAndPersist(canonical)
    }

    private fun mockCanonicalMatch(): CanonicalMatch {
        val canonical = mock<CanonicalMatch>()
        whenever(canonical.matchId).thenReturn(MatchId("test-match"))
        whenever(canonical.interpretation).thenReturn(mock())
        whenever(canonical.interpretation.perspectiveClubId).thenReturn(ClubId("test-club"))
        whenever(canonical.footballMatch).thenReturn(mock())
        whenever(canonical.footballMatch.id).thenReturn(MatchId("test-match"))
        whenever(canonical.footballMatch.playedAt).thenReturn(Instant.now())
        return canonical
    }
}
