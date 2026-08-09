package com.eafc26.discordstats.web

import com.eafc26.discordstats.application.interpretation.MatchInterpreter
import com.eafc26.discordstats.application.repository.CanonicalRepositoryMetadata
import com.eafc26.discordstats.application.story.MatchStoryExtractor
import com.eafc26.discordstats.canonical.CanonicalMatch
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.MatchId
import com.eafc26.discordstats.ea.mapping.EaMatchMapper
import com.eafc26.discordstats.ea.mapping.MatchNormalizationResult
import com.eafc26.discordstats.ea.model.ClubDetails
import com.eafc26.discordstats.ea.model.ClubMatchEntry
import com.eafc26.discordstats.ea.model.MatchResponse
import com.eafc26.discordstats.ea.model.PlayerEntry
import com.eafc26.discordstats.service.MatchHistoryService
import com.eafc26.discordstats.support.defaultClubProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import java.time.Instant

class MatchHistoryControllerTest {
    private lateinit var historyService: MatchHistoryService
    private lateinit var controller: MatchHistoryController

    @BeforeEach
    fun setUp() {
        historyService = mock()
        controller = MatchHistoryController(historyService, defaultClubProvider(OUR_CLUB))
    }

    @Test
    fun `list exposes persisted matches in service chronological order`() {
        val newest = canonical("newest", 1_801_000_000L)
        val oldest = canonical("oldest", 1_701_000_000L)
        whenever(historyService.list(OUR_CLUB)).thenReturn(listOf(newest, oldest))
        whenever(historyService.metadata(OUR_CLUB)).thenReturn(metadata(2))

        val response = controller.listMatches().block()!!

        assertThat(response.statusCode.is2xxSuccessful).isTrue()
        assertThat(response.body!!.status).isEqualTo("success")
        assertThat(response.body!!.matches.map { it.matchId }).containsExactly("newest", "oldest")
        assertThat(response.body!!.matches.first().outcome.code).isEqualTo("WIN")
        assertThat(response.body!!.matches.first().ourClub.score).isEqualTo(3)
        assertThat(response.body!!.matches.first().competition).isEqualTo("Liga")
        verify(historyService).list(OUR_CLUB)
        verify(historyService).metadata(OUR_CLUB)
    }

    @Test
    fun `empty history returns an explicit empty state`() {
        whenever(historyService.list(OUR_CLUB)).thenReturn(emptyList())
        whenever(historyService.metadata(OUR_CLUB)).thenReturn(metadata(0))

        val response = controller.listMatches().block()!!

        assertThat(response.body!!.status).isEqualTo("empty")
        assertThat(response.body!!.matches).isEmpty()
        assertThat(response.body!!.metadata.matchCount).isZero()
    }

    @Test
    fun `detail projects the persisted canonical match without running pipeline components`() {
        val canonical = canonical("detail-1", 1_801_000_000L)
        whenever(historyService.findById(OUR_CLUB, MatchId("detail-1"))).thenReturn(canonical)

        val response = controller.getMatch("detail-1").block()!!
        val detail = response.body!!.match!!

        assertThat(detail.summary.matchId).isEqualTo("detail-1")
        assertThat(detail.summary.ourClub.name).isEqualTo("Our FC")
        assertThat(detail.summary.opponentClub.name).isEqualTo("Opponent FC")
        assertThat(detail.players.map { it.name }).containsExactly("MVP", "Defender", "Bagre")
        assertThat(detail.awards.map { it.type }).containsExactly("CRAQUE", "BAGRE", "XERIFE")
        assertThat(detail.awards.first { it.type == "CRAQUE" }.winnerName).isEqualTo("MVP")
        assertThat(detail.awards.first { it.type == "BAGRE" }.label).isEqualTo("Menor Desempenho")
        assertThat(detail.stories).isNotEmpty
        assertThat(detail.stories.first().ruleIds).isNotEmpty
        assertThat(detail.stories.first().evidenceCount).isPositive()
        assertThat(detail.provenance.schemaVersion).isEqualTo(1)
        verify(historyService).findById(OUR_CLUB, MatchId("detail-1"))
        verifyNoMoreInteractions(historyService)
    }

    @Test
    fun `unknown MatchId returns not found`() {
        whenever(historyService.findById(OUR_CLUB, MatchId("missing"))).thenReturn(null)

        val response = controller.getMatch("missing").block()!!

        assertThat(response.statusCode.value()).isEqualTo(404)
        assertThat(response.body!!.status).isEqualTo("not_found")
        assertThat(response.body!!.match).isNull()
        assertThat(response.body!!.message).contains("não encontrada")
    }

    @Test
    fun `history page serves the dedicated dashboard resource`() {
        val response = controller.historyPage()

        assertThat(response.body!!.path).isEqualTo("history.html")
        verifyNoMoreInteractions(historyService)
    }

    private fun canonical(id: String, timestamp: Long): CanonicalMatch {
        val source = MatchResponse(
            matchId = id,
            timestamp = timestamp,
            matchType = "leagueMatch",
            clubs = linkedMapOf(
                OUR_CLUB.value to ClubMatchEntry(
                    details = ClubDetails("Our FC", OUR_CLUB.value),
                    score = "3",
                    result = "1",
                ),
                "opponent" to ClubMatchEntry(
                    details = ClubDetails("Opponent FC", "opponent"),
                    score = "1",
                    result = "0",
                ),
            ),
            players = mapOf(
                OUR_CLUB.value to linkedMapOf(
                    "mvp" to player("MVP", "9.2", goals = "2", mom = "1"),
                    "defender" to player(
                        "Defender",
                        "8.0",
                        tacklesMade = "5",
                        tackleAttempts = "6",
                    ),
                    "bagre" to player("Bagre", "5.5"),
                )
            ),
        )
        val footballMatch = (EaMatchMapper().map(source) as MatchNormalizationResult.Success).match
        val interpretation = MatchInterpreter().interpret(footballMatch, OUR_CLUB)
        val stories = MatchStoryExtractor().extract(interpretation)
        return CanonicalMatch.current(
            footballMatch,
            interpretation,
            stories,
            generatedAt = Instant.parse("2026-07-30T10:00:00Z"),
        )
    }

    private fun player(
        name: String,
        rating: String,
        goals: String = "0",
        mom: String = "0",
        tacklesMade: String = "2",
        tackleAttempts: String = "4",
    ) = PlayerEntry(
        playerName = name,
        position = "14",
        rating = rating,
        goals = goals,
        assists = "1",
        shots = "3",
        manOfTheMatch = mom,
        passesMade = "18",
        passAttempts = "20",
        tacklesMade = tacklesMade,
        tackleAttempts = tackleAttempts,
        redCards = "0",
        secondsPlayed = "5400",
    )

    private fun metadata(count: Int) = CanonicalRepositoryMetadata(
        matchCount = count,
        oldestMatchAt = null,
        newestMatchAt = null,
        lastGeneratedAt = null,
        schemaVersions = emptySet(),
        engineVersions = emptySet(),
    )

    private companion object {
        val OUR_CLUB = ClubId("our-club")
    }
}
