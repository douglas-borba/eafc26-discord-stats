package com.eafc26.discordstats.web

import com.eafc26.discordstats.insight.HistoricalInsightsReport
import com.eafc26.discordstats.service.HistoricalInsightsService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

class HistoricalInsightsControllerTest {
    private lateinit var service: HistoricalInsightsService
    private lateinit var controller: HistoricalInsightsController

    @BeforeEach
    fun setUp() {
        service = mock()
        controller = HistoricalInsightsController(service)
    }

    @Test
    fun `insights page serves dedicated resource`() {
        assertThat(controller.insightsPage().body!!.path).isEqualTo("insights.html")
        verifyNoMoreInteractions(service)
    }

    @Test
    fun `empty report returns explicit empty status`() {
        whenever(service.generate()).thenReturn(HistoricalInsightsReport(0, emptyList()))

        val response = controller.insights().block()!!

        assertThat(response.body!!.status).isEqualTo("empty")
        assertThat(response.body!!.sourceMatchCount).isZero()
        assertThat(response.body!!.insights).isEmpty()
    }
}
