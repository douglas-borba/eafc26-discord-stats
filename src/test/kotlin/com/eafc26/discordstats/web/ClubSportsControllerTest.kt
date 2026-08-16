package com.eafc26.discordstats.web

import com.eafc26.discordstats.application.club.EaPlatform
import com.eafc26.discordstats.application.club.ClubAccessPolicy
import com.eafc26.discordstats.application.club.MonitoredClub
import com.eafc26.discordstats.application.club.MonitoredClubService
import com.eafc26.discordstats.application.club.ClubAccessStatus
import com.eafc26.discordstats.application.repository.CanonicalRepositoryMetadata
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.ClubName
import com.eafc26.discordstats.domain.match.MatchId
import com.eafc26.discordstats.llm.LlmEditorialService
import com.eafc26.discordstats.service.MatchCardService
import com.eafc26.discordstats.service.MatchComparisonService
import com.eafc26.discordstats.service.MatchHistoryService
import com.eafc26.discordstats.service.OpponentHistoryService
import com.eafc26.discordstats.service.PlayerProfileService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

class ClubSportsControllerTest {
    private val clubs: MonitoredClubService = mock()
    private val history: MatchHistoryService = mock()
    private val players: PlayerProfileService = mock()
    private val opponents: OpponentHistoryService = mock()
    private val comparisons: MatchComparisonService = mock()
    private val cards: MatchCardService = mock()
    private val editorial: LlmEditorialService = mock()
    private val controller = ClubSportsController(clubs, history, players, opponents, comparisons, cards, editorial, ClubAccessPolicy(clubs))

    @Test
    fun `registered clubs are independently scoped and may have empty history`() {
        val association = club("1104972", "Associação BF")
        val brasil = club("8874106", "BRASIL 2030")
        whenever(clubs.find(association.clubId)).thenReturn(association)
        whenever(clubs.find(brasil.clubId)).thenReturn(brasil)
        whenever(history.list(association.clubId)).thenReturn(emptyList())
        whenever(history.list(brasil.clubId)).thenReturn(emptyList())
        whenever(history.metadata(association.clubId)).thenReturn(metadata())
        whenever(history.metadata(brasil.clubId)).thenReturn(metadata())

        assertThat(controller.club("1104972").displayName).isEqualTo("Associação BF")
        assertThat(controller.club("8874106").displayName).isEqualTo("BRASIL 2030")
        assertThat(controller.matches("1104972").status).isEqualTo("empty")
        assertThat(controller.matches("8874106").matches).isEmpty()
        verify(history).list(association.clubId)
        verify(history).list(brasil.clubId)
    }

    @Test
    fun `unknown club is 404 and never reaches sports services`() {
        whenever(clubs.find(ClubId("999999999"))).thenReturn(null)

        assertThatThrownBy { controller.matches("999999999") }
            .isInstanceOf(ResponseStatusException::class.java)
            .extracting("statusCode.value").isEqualTo(404)
        verify(history, never()).list(ClubId("999999999"))
    }

    @Test
    fun `trial allows overview but denies deeper dashboard areas`() {
        val trial = club("1104972", "Trial").copy(accessStatus = ClubAccessStatus.TRIAL, monitoringEnabled = false)
        whenever(clubs.find(trial.clubId)).thenReturn(trial)
        whenever(history.recent(trial.clubId, 10)).thenReturn(emptyList())
        whenever(history.metadata(trial.clubId)).thenReturn(metadata())

        assertThat(controller.overviewMatches(trial.clubId.value).status).isEqualTo("empty")
        assertThatThrownBy { controller.matches(trial.clubId.value) }
            .isInstanceOf(ResponseStatusException::class.java)
            .extracting("statusCode.value").isEqualTo(403)
        assertThatThrownBy { controller.players(trial.clubId.value) }
            .isInstanceOf(ResponseStatusException::class.java)
            .extracting("statusCode.value").isEqualTo(403)
        assertThatThrownBy { controller.opponents(trial.clubId.value) }
            .isInstanceOf(ResponseStatusException::class.java)
            .extracting("statusCode.value").isEqualTo(403)
    }

    @Test
    fun `overview requests only the bounded recent feed`() {
        val club = club("1104972", "Associação BF")
        whenever(clubs.find(club.clubId)).thenReturn(club)
        whenever(history.recent(club.clubId, 10)).thenReturn(emptyList())
        whenever(history.metadata(club.clubId)).thenReturn(metadata())

        assertThat(controller.overviewMatches(club.clubId.value).status).isEqualTo("empty")
        verify(history).recent(club.clubId, 10)
        verify(history, never()).list(club.clubId)
    }

    @Test
    fun `same external match id is always looked up inside each club perspective`() {
        val association = club("1104972", "Associação BF")
        val brasil = club("8874106", "BRASIL 2030")
        val shared = MatchId("shared-match")
        whenever(clubs.find(association.clubId)).thenReturn(association)
        whenever(clubs.find(brasil.clubId)).thenReturn(brasil)
        whenever(history.findById(association.clubId, shared)).thenReturn(null)
        whenever(history.findById(brasil.clubId, shared)).thenReturn(null)

        assertThat(controller.match(association.clubId.value, shared.value).statusCode.value()).isEqualTo(404)
        assertThat(controller.match(brasil.clubId.value, shared.value).statusCode.value()).isEqualTo(404)
        verify(history).findById(association.clubId, shared)
        verify(history).findById(brasil.clubId, shared)
    }

    private fun club(id: String, name: String) = MonitoredClub(
        ClubId(id), ClubName(name), EaPlatform("common-gen5"), true, null,
        Instant.parse("2026-08-09T12:00:00Z"), Instant.parse("2026-08-09T12:00:00Z"),
    )

    private fun metadata() = CanonicalRepositoryMetadata(0, null, null, null, emptySet(), emptySet())
}
