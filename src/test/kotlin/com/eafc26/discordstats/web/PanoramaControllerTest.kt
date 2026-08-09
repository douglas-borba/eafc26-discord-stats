package com.eafc26.discordstats.web

import com.eafc26.discordstats.canonical.CanonicalMatch
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.llm.LlmEditorialService
import com.eafc26.discordstats.service.MatchHistoryService
import com.eafc26.discordstats.support.defaultClubProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PanoramaControllerTest {
    private lateinit var editorialService: LlmEditorialService
    private lateinit var historyService: MatchHistoryService
    private lateinit var controller: PanoramaController

    @BeforeEach
    fun setUp() {
        editorialService = mock()
        historyService = mock()
        controller = PanoramaController(editorialService, historyService, defaultClubProvider(DEFAULT_CLUB))
    }

    @Test
    fun `legacy panorama resolves the default club at the controller boundary`() {
        whenever(editorialService.getPersistedPanorama(DEFAULT_CLUB)).thenReturn("Panorama padrão")

        val response = controller.getPanorama().block()!!

        assertThat(response.body!!.text).isEqualTo("Panorama padrão")
        verify(editorialService).getPersistedPanorama(DEFAULT_CLUB)
    }

    @Test
    fun `requested panorama remains scoped to the requested club`() {
        whenever(editorialService.getPersistedPanorama(OTHER_CLUB)).thenReturn("Panorama isolado")

        val response = controller.getPanorama(OTHER_CLUB.value).block()!!

        assertThat(response.body!!.text).isEqualTo("Panorama isolado")
        verify(editorialService).getPersistedPanorama(OTHER_CLUB)
    }

    @Test
    fun `regeneration reads and writes only the requested club context`() {
        val canonical = mock<CanonicalMatch>()
        whenever(historyService.latest(OTHER_CLUB, 1)).thenReturn(listOf(canonical))
        whenever(editorialService.getPersistedPanorama(OTHER_CLUB)).thenReturn("Atualizado")

        val response = controller.regeneratePanorama(OTHER_CLUB.value).block()!!

        assertThat(response.body!!.text).isEqualTo("Atualizado")
        verify(historyService).latest(OTHER_CLUB, 1)
        verify(editorialService).generateAndPersistPanorama(canonical)
        verify(editorialService).getPersistedPanorama(OTHER_CLUB)
    }

    private companion object {
        val DEFAULT_CLUB = ClubId("1104972")
        val OTHER_CLUB = ClubId("2200000")
    }
}
