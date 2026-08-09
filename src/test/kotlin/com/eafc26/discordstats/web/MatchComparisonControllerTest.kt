package com.eafc26.discordstats.web

import com.eafc26.discordstats.comparison.MatchComparisonOption
import com.eafc26.discordstats.comparison.MatchComparisonResult
import com.eafc26.discordstats.domain.interpretation.MatchOutcome
import com.eafc26.discordstats.domain.match.MatchId
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.service.MatchComparisonService
import com.eafc26.discordstats.support.defaultClubProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import java.time.Instant

class MatchComparisonControllerTest {
    private lateinit var service: MatchComparisonService
    private lateinit var controller: MatchComparisonController

    @BeforeEach
    fun setUp() {
        service = mock()
        controller = MatchComparisonController(service, defaultClubProvider(CLUB_ID))
    }

    @Test
    fun `comparison page serves dedicated resource`() {
        assertThat(controller.comparisonPage().body!!.path).isEqualTo("compare.html")
        verifyNoMoreInteractions(service)
    }

    @Test
    fun `empty options have explicit state`() {
        whenever(service.listOptions(CLUB_ID)).thenReturn(emptyList())

        val response = controller.listOptions().block()!!

        assertThat(response.body!!.status).isEqualTo("empty")
        assertThat(response.body!!.matches).isEmpty()
    }

    @Test
    fun `options are exposed through comparison layer`() {
        whenever(service.listOptions(CLUB_ID)).thenReturn(
            listOf(
                MatchComparisonOption(
                    MatchId("m1"),
                    Instant.parse("2026-07-01T10:00:00Z"),
                    MatchOutcome.WIN,
                    "Our FC",
                    "Opponent",
                    3,
                    1,
                )
            )
        )

        val option = controller.listOptions().block()!!.body!!.matches.single()

        assertThat(option.matchId).isEqualTo("m1")
        assertThat(option.label).contains("Vitória", "3×1", "Opponent")
    }

    @Test
    fun `missing comparison match returns not found with IDs`() {
        val first = MatchId("missing")
        val second = MatchId("existing")
        whenever(service.compare(CLUB_ID, first, second)).thenReturn(
            MatchComparisonResult.NotFound(setOf(first))
        )

        val response = controller.compare(first.value, second.value).block()!!

        assertThat(response.statusCode.value()).isEqualTo(404)
        assertThat(response.body!!.status).isEqualTo("not_found")
        assertThat(response.body!!.missingMatchIds).containsExactly("missing")
    }

    private companion object {
        val CLUB_ID = ClubId("our-club")
    }
}
