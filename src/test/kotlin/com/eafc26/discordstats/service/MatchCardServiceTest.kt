package com.eafc26.discordstats.service

import com.eafc26.discordstats.presentation.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class MatchCardServiceTest {

    private lateinit var latestMatchHolder: LatestMatchHolder
    private lateinit var service: MatchCardService

    @BeforeEach
    fun setUp() {
        latestMatchHolder = LatestMatchHolder()
        service = MatchCardService(latestMatchHolder)
    }

    // -------------------------------------------------------------------------
    // No Cached Presentation
    // -------------------------------------------------------------------------

    @Nested
    inner class NoCachedPresentation {

        @Test
        fun `returns NoMatches when no presentation has been cached`() {
            val result = service.getLatestMatchCard()

            assertThat(result).isInstanceOf(MatchCardService.MatchCardResult.NoMatches::class.java)
        }

        @Test
        fun `version returns 0 when no presentation cached`() {
            assertThat(service.version()).isEqualTo(0)
        }
    }

    // -------------------------------------------------------------------------
    // Cached Presentation
    // -------------------------------------------------------------------------

    @Nested
    inner class CachedPresentation {

        @Test
        fun `returns Success with presentation when cached`() {
            val presentation = createPresentation("m1")
            latestMatchHolder.update(presentation)

            val result = service.getLatestMatchCard()

            assertThat(result).isInstanceOf(MatchCardService.MatchCardResult.Success::class.java)
            val success = result as MatchCardService.MatchCardResult.Success
            assertThat(success.presentation).isEqualTo(presentation)
        }

        @Test
        fun `returns latest presentation after multiple updates`() {
            latestMatchHolder.update(createPresentation("m1"))
            latestMatchHolder.update(createPresentation("m2"))
            val latest = createPresentation("m3")
            latestMatchHolder.update(latest)

            val result = service.getLatestMatchCard()

            val success = result as MatchCardService.MatchCardResult.Success
            assertThat(success.presentation.matchId).isEqualTo("m3")
        }

        @Test
        fun `version reflects cache state`() {
            latestMatchHolder.update(createPresentation("m1"))
            assertThat(service.version()).isEqualTo(1)

            latestMatchHolder.update(createPresentation("m2"))
            assertThat(service.version()).isEqualTo(2)
        }
    }

    // -------------------------------------------------------------------------
    // Integration with Acquisition (simulated)
    // -------------------------------------------------------------------------

    @Nested
    inner class IntegrationScenarios {

        @Test
        fun `card available immediately after acquisition caches presentation`() {
            // Simulate what MatchAcquisitionService does
            val presentation = createPresentation("m1")
            latestMatchHolder.update(presentation)

            // Now MatchCardService should return it
            val result = service.getLatestMatchCard()
            assertThat(result).isInstanceOf(MatchCardService.MatchCardResult.Success::class.java)
        }

        @Test
        fun `card reflects skipped match that was already published`() {
            // Simulate: acquisition caches presentation but skips Discord delivery
            val presentation = createPresentation("already-published")
            latestMatchHolder.update(presentation)

            // Card should still show it
            val result = service.getLatestMatchCard()
            val success = result as MatchCardService.MatchCardResult.Success
            assertThat(success.presentation.matchId).isEqualTo("already-published")
        }

        @Test
        fun `card remains valid even if Discord delivery failed`() {
            // Simulate: acquisition caches presentation, then Discord fails
            val presentation = createPresentation("discord-failed")
            latestMatchHolder.update(presentation)

            // Card should still show the valid presentation
            val result = service.getLatestMatchCard()
            val success = result as MatchCardService.MatchCardResult.Success
            assertThat(success.presentation.matchId).isEqualTo("discord-failed")
        }

        @Test
        fun `card version increases after each acquisition`() {
            assertThat(service.version()).isEqualTo(0)

            latestMatchHolder.update(createPresentation("m1"))
            assertThat(service.version()).isEqualTo(1)

            latestMatchHolder.update(createPresentation("m2"))
            assertThat(service.version()).isEqualTo(2)

            // Simulate another acquisition (even if same match)
            latestMatchHolder.update(createPresentation("m2"))
            assertThat(service.version()).isEqualTo(3)
        }
    }

    // -------------------------------------------------------------------------
    // Result Type Verification
    // -------------------------------------------------------------------------

    @Nested
    inner class ResultTypes {

        @Test
        fun `Success contains the presentation`() {
            val presentation = createPresentation("test")
            latestMatchHolder.update(presentation)

            val result = service.getLatestMatchCard()

            assertThat(result).isInstanceOf(MatchCardService.MatchCardResult.Success::class.java)
            val success = result as MatchCardService.MatchCardResult.Success
            assertThat(success.presentation.ourName).isEqualTo("Test FC")
            assertThat(success.presentation.oppName).isEqualTo("Opponent FC")
            assertThat(success.presentation.ourScore).isEqualTo(2)
            assertThat(success.presentation.oppScore).isEqualTo(1)
        }

        @Test
        fun `NoMatches has no presentation data`() {
            val result = service.getLatestMatchCard()

            assertThat(result).isEqualTo(MatchCardService.MatchCardResult.NoMatches)
        }
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private fun createPresentation(matchId: String): MatchSummaryPresentation =
        MatchSummaryPresentation(
            ourName = "Test FC",
            oppName = "Opponent FC",
            ourScore = 2,
            oppScore = 1,
            outcome = MatchOutcome(
                emoji = "🏆",
                label = "Vitória",
                color = 0x00FF00,
                type = OutcomeType.WIN,
            ),
            date = "19 Jul 2026 • 10:00",
            timestamp = "2026-07-19T10:00:00Z",
            matchId = matchId,
            goals = null,
            assists = null,
            highlights = null,
            craque = null,
            perigoConstante = null,
            bagre = null,
            xerife = null,
            passePrecisao = null,
            correioExtraviado = null,
            muralha = null,
        )
}

