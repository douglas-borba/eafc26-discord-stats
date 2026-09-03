package com.eafc26.discordstats.web

import com.eafc26.discordstats.application.interpretation.MatchInterpreter
import com.eafc26.discordstats.application.repository.CanonicalRepositoryMetadata
import com.eafc26.discordstats.application.repository.CanonicalMatchOverview
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
import org.mockito.kotlin.never
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
        whenever(historyService.listSummaries(OUR_CLUB)).thenReturn(
            listOf(CanonicalMatchOverview.from(newest), CanonicalMatchOverview.from(oldest))
        )
        whenever(historyService.metadata(OUR_CLUB)).thenReturn(metadata(2))

        val response = controller.listMatches().block()!!

        assertThat(response.statusCode.is2xxSuccessful).isTrue()
        assertThat(response.body!!.status).isEqualTo("success")
        assertThat(response.body!!.matches.map { it.matchId }).containsExactly("newest", "oldest")
        assertThat(response.body!!.matches.first().outcome.code).isEqualTo("WIN")
        assertThat(response.body!!.matches.first().ourClub.score).isEqualTo(3)
        assertThat(response.body!!.matches.first().competition).isEqualTo("Liga")
        verify(historyService).listSummaries(OUR_CLUB)
        verify(historyService, never()).list(OUR_CLUB)
        verify(historyService).metadata(OUR_CLUB)
    }

    @Test
    fun `empty history returns an explicit empty state`() {
        whenever(historyService.listSummaries(OUR_CLUB)).thenReturn(emptyList())
        whenever(historyService.metadata(OUR_CLUB)).thenReturn(metadata(0))

        val response = controller.listMatches().block()!!

        assertThat(response.body!!.status).isEqualTo("empty")
        assertThat(response.body!!.matches).isEmpty()
        assertThat(response.body!!.metadata.matchCount).isZero()
        verify(historyService).listSummaries(OUR_CLUB)
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
        assertThat(detail.awards.first { it.type == "BAGRE" }.label).isEqualTo("Bagre da Partida")
        assertThat(detail.stories).isNotEmpty
        assertThat(detail.stories.first().ruleIds).isNotEmpty
        assertThat(detail.stories.first().evidenceCount).isPositive()
        assertThat(detail.provenance.schemaVersion).isEqualTo(2)
        verify(historyService).findById(OUR_CLUB, MatchId("detail-1"))
        verifyNoMoreInteractions(historyService)
    }

    @Test
    fun `historical detail preserves low performance recognition stored with the canonical match`() {
        val canonical = canonical("low-performance", 1_801_000_000L, negativeRating = "7.3")
        whenever(historyService.findById(OUR_CLUB, MatchId("low-performance"))).thenReturn(canonical)

        val detail = controller.getMatch("low-performance").block()!!.body!!.match!!

        assertThat(detail.awards.first { it.type == "BAGRE" }.label).isEqualTo("Baixo Rendimento")
        assertThat(detail.stories).anySatisfy {
            assertThat(it.title).isEqualTo("Baixo Rendimento")
        }
    }

    @Test
    fun `historical one on one story exposes only opponents beaten`() {
        val canonical = canonical(
            id = "one-on-one",
            timestamp = 1_801_000_000L,
            oneOnOneAggregate0 = "112:8,174:18",
        )
        whenever(historyService.findById(OUR_CLUB, MatchId("one-on-one"))).thenReturn(canonical)

        val story = controller.getMatch("one-on-one").block()!!.body!!.match!!.stories
            .single { it.type == "ONE_ON_ONE" }

        assertThat(story.facts).containsExactly(
            com.eafc26.discordstats.presentation.history.HistoricalFact("Jogador", "Dribbler"),
            com.eafc26.discordstats.presentation.history.HistoricalFact("Adversários superados", "8"),
        )
        assertThat(story.facts.map { it.label }).doesNotContain("Dribles completos")
    }

    @Test
    fun `historical DNF preserves only factual goal and assist contributions`() {
        val canonical = canonical(
            id = "dnf-contributions",
            timestamp = 1_801_000_000L,
            dnfClubId = ClubId("opponent"),
            mvpAssists = "1",
            defenderAssists = "0",
            bagreAssists = "0",
        )
        whenever(historyService.findById(OUR_CLUB, MatchId("dnf-contributions"))).thenReturn(canonical)

        val detail = controller.getMatch("dnf-contributions").block()!!.body!!.match!!

        assertThat(detail.summary.completionStatus).isEqualTo("DNF")
        assertThat(detail.players).hasSize(1)
        val player = detail.players.single()
        assertThat(player.name).isEqualTo("MVP")
        assertThat(player.goals).isEqualTo(2)
        assertThat(player.assists).isEqualTo(1)
        assertThat(player.rating).isNull()
        assertThat(player.shots).isNull()
        assertThat(player.passesCompleted).isNull()
        assertThat(player.tacklesCompleted).isNull()
        assertThat(player.manOfTheMatch).isFalse()
        assertThat(detail.awards).allMatch { !it.awarded }
    }

    @Test
    fun `historical result icon remains relative to the monitored club`() {
        val victory = canonical("historical-win", 1_801_000_000L)
        val draw = canonical("historical-draw", 1_801_000_001L, ourScore = "2", opponentScore = "2")
        val loss = canonical("historical-loss", 1_801_000_002L, ourScore = "0", opponentScore = "1")
        whenever(historyService.findById(OUR_CLUB, MatchId("historical-win"))).thenReturn(victory)
        whenever(historyService.findById(OUR_CLUB, MatchId("historical-draw"))).thenReturn(draw)
        whenever(historyService.findById(OUR_CLUB, MatchId("historical-loss"))).thenReturn(loss)

        val winOutcome = controller.getMatch("historical-win").block()!!.body!!.match!!.summary.outcome
        val drawOutcome = controller.getMatch("historical-draw").block()!!.body!!.match!!.summary.outcome
        val lossOutcome = controller.getMatch("historical-loss").block()!!.body!!.match!!.summary.outcome

        assertThat(winOutcome.code).isEqualTo("WIN")
        assertThat(winOutcome.icon).isEqualTo("🏆")
        assertThat(drawOutcome.code).isEqualTo("DRAW")
        assertThat(drawOutcome.icon).isEqualTo("🤝")
        assertThat(lossOutcome.code).isEqualTo("LOSS")
        assertThat(lossOutcome.icon).isEqualTo("📉")
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

    private fun canonical(
        id: String,
        timestamp: Long,
        negativeRating: String = "5.5",
        oneOnOneAggregate0: String? = null,
        dnfClubId: ClubId? = null,
        mvpAssists: String = "1",
        defenderAssists: String = "1",
        bagreAssists: String = "1",
        ourScore: String = "3",
        opponentScore: String = "1",
    ): CanonicalMatch {
        val source = MatchResponse(
            matchId = id,
            timestamp = timestamp,
            matchType = "leagueMatch",
            clubs = linkedMapOf(
                OUR_CLUB.value to ClubMatchEntry(
                    details = ClubDetails("Our FC", OUR_CLUB.value),
                    score = ourScore,
                    result = result(ourScore, opponentScore),
                    winnerByDnf = when (dnfClubId) {
                        null -> null
                        OUR_CLUB -> "0"
                        else -> "1"
                    },
                ),
                "opponent" to ClubMatchEntry(
                    details = ClubDetails("Opponent FC", "opponent"),
                    score = opponentScore,
                    result = result(opponentScore, ourScore),
                    winnerByDnf = when (dnfClubId) {
                        null -> null
                        OUR_CLUB -> "1"
                        else -> "0"
                    },
                ),
            ),
            players = mapOf(
                OUR_CLUB.value to buildMap {
                    put("mvp", player("MVP", "9.2", goals = "2", assists = mvpAssists, mom = "1"))
                    put(
                        "defender",
                        player(
                            "Defender",
                            "8.0",
                            tacklesMade = "5",
                            tackleAttempts = "6",
                            assists = defenderAssists,
                        ),
                    )
                    put("bagre", player("Bagre", negativeRating, assists = bagreAssists))
                    oneOnOneAggregate0?.let { aggregate0 ->
                        put("dribbler", player("Dribbler", "8.4", aggregate0 = aggregate0))
                    }
                }
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
        assists: String = "1",
        mom: String = "0",
        tacklesMade: String = "2",
        tackleAttempts: String = "4",
        aggregate0: String? = null,
    ) = PlayerEntry(
        playerName = name,
        position = "14",
        rating = rating,
        goals = goals,
        assists = assists,
        shots = "3",
        manOfTheMatch = mom,
        passesMade = "18",
        passAttempts = "20",
        tacklesMade = tacklesMade,
        tackleAttempts = tackleAttempts,
        redCards = "0",
        secondsPlayed = "5400",
        matchEventAggregate0 = aggregate0,
    )

    private fun metadata(count: Int) = CanonicalRepositoryMetadata(
        matchCount = count,
        oldestMatchAt = null,
        newestMatchAt = null,
        lastGeneratedAt = null,
        schemaVersions = emptySet(),
        engineVersions = emptySet(),
    )

    private fun result(score: String, opponentScore: String): String = when {
        score.toInt() > opponentScore.toInt() -> "1"
        score.toInt() < opponentScore.toInt() -> "0"
        else -> "2"
    }

    private companion object {
        val OUR_CLUB = ClubId("our-club")
    }
}
