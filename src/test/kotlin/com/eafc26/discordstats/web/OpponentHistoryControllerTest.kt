package com.eafc26.discordstats.web

import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.service.OpponentHistoryService
import com.eafc26.discordstats.support.defaultClubProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class OpponentHistoryControllerTest {
    private lateinit var service: OpponentHistoryService
    private lateinit var controller: OpponentHistoryController
    @BeforeEach fun setUp(){service=mock();controller=OpponentHistoryController(service, defaultClubProvider(CLUB_ID))}

    @Test fun `serves shared opponents page for index and detail routes`() {
        assertThat(controller.page(null).body!!.path).isEqualTo("opponents.html")
        assertThat(controller.page("club-1").body!!.path).isEqualTo("opponents.html")
    }

    @Test fun `legacy insights route redirects to opponents`() {
        val response=controller.legacyRedirect()
        assertThat(response.statusCode.value()).isEqualTo(302)
        assertThat(response.headers.location.toString()).isEqualTo("/opponents")
    }

    @Test fun `empty index has explicit status`() {
        whenever(service.listOpponents(CLUB_ID)).thenReturn(emptyList())
        val response=controller.list().block()!!.body!!
        assertThat(response.status).isEqualTo("empty")
        assertThat(response.opponents).isEmpty()
    }

    @Test fun `unknown canonical ClubId returns not found`() {
        whenever(service.findByClubId(CLUB_ID, ClubId("missing"))).thenReturn(null)
        val response=controller.detail("missing").block()!!
        assertThat(response.statusCode.value()).isEqualTo(404)
        assertThat(response.body!!.status).isEqualTo("not_found")
    }

    private companion object {
        val CLUB_ID = ClubId("our-club")
    }
}
