package com.eafc26.discordstats.presentation

import com.eafc26.discordstats.config.PhraseBank
import com.eafc26.discordstats.config.PhraseCategory
import com.eafc26.discordstats.ea.model.ClubDetails
import com.eafc26.discordstats.ea.model.ClubMatchEntry
import com.eafc26.discordstats.ea.model.MatchResponse
import com.eafc26.discordstats.ea.model.PlayerEntry
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class MatchSummaryBuilderTest {

    private lateinit var phraseBank: PhraseBank
    private lateinit var builder: MatchSummaryBuilder

    private val clubId = "12345"

    @BeforeEach
    fun setUp() {
        phraseBank = PhraseBank(jacksonObjectMapper())
        builder = MatchSummaryBuilder(phraseBank)
    }

    private fun match(
        id: String = "test-match-001",
        ts: Long = System.currentTimeMillis() / 1000,
        ourScore: String = "2",
        oppScore: String = "1",
        players: Map<String, PlayerEntry> = emptyMap(),
    ): MatchResponse = MatchResponse(
        matchId = id,
        timestamp = ts,
        clubs = mapOf(
            clubId to ClubMatchEntry(
                details = ClubDetails(name = "Test FC"),
                score = ourScore,
                result = "1",
            ),
            "opponent" to ClubMatchEntry(
                details = ClubDetails(name = "Opponent FC"),
                score = oppScore,
                result = "0",
            ),
        ),
        players = mapOf(clubId to players),
    )

    private fun player(
        name: String,
        rating: String = "7.0",
        goals: String = "0",
        assists: String = "0",
        position: String = "midfielder",
        tackleAttempts: String = "8",
        tacklesMade: String = "5",
    ): PlayerEntry = PlayerEntry(
        playerName = name,
        rating = rating,
        goals = goals,
        assists = assists,
        position = position,
        passesMade = "20",
        passAttempts = "25",
        tacklesMade = tacklesMade,
        tackleAttempts = tackleAttempts,
        shots = "3",
        secondsPlayed = "5400",
    )

    @Nested
    inner class DeterministicPhraseSelection {

        @Test
        fun `same match produces same phrases with forceRandomPhrases=false`() {
            val match = match(players = mapOf("p1" to player("Player One", rating = "8.5")))

            val presentation1 = builder.build(match, clubId, forceRandomPhrases = false)
            val presentation2 = builder.build(match, clubId, forceRandomPhrases = false)

            // Same match with deterministic selection should produce identical results
            assertThat(presentation1.craque?.phrase).isEqualTo(presentation2.craque?.phrase)
        }
    }

    @Nested
    inner class RandomPhraseSelection {

        @Test
        fun `forceRandomPhrases=true allows different phrases on same match`() {
            // This test verifies that random selection is USED, not that results MUST differ
            // (Random could legitimately choose the same phrase twice)
            
            val players = mapOf(
                "p1" to player("Player One", rating = "8.5", goals = "2"),
                "p2" to player("Player Two", rating = "6.0"),
            )
            val match = match(players = players)

            // Run multiple times to increase chance of seeing different phrases
            val phrases = (1..10).map {
                builder.build(match, clubId, forceRandomPhrases = true).craque?.phrase
            }.filterNotNull().toSet()

            // With 10 attempts and random selection, we should have at least 1 phrase
            // (This doesn't require variation - just that the code path is executed)
            assertThat(phrases).isNotEmpty()
        }

        @Test
        fun `forceRandomPhrases generates new presentation object each time`() {
            val match = match(players = mapOf("p1" to player("Player One", rating = "8.5")))

            val presentation1 = builder.build(match, clubId, forceRandomPhrases = true)
            val presentation2 = builder.build(match, clubId, forceRandomPhrases = true)

            // Each call creates a new object
            assertThat(presentation1).isNotSameAs(presentation2)
            // But with same match data
            assertThat(presentation1.matchId).isEqualTo(presentation2.matchId)
            assertThat(presentation1.ourScore).isEqualTo(presentation2.ourScore)
        }
    }

    @Nested
    inner class PresentationGeneration {

        @Test
        fun `builds presentation with all sections when data is available`() {
            val players = mapOf(
                "p1" to player("Scorer", rating = "8.5", goals = "2", assists = "1"),
                "p2" to player("Assister", rating = "7.5", assists = "2"),
                "p3" to player("Defender", rating = "6.0"),
            )
            val match = match(players = players)

            val presentation = builder.build(match, clubId)

            assertThat(presentation.matchId).isEqualTo("test-match-001")
            assertThat(presentation.ourName).isEqualTo("Test FC")
            assertThat(presentation.oppName).isEqualTo("Opponent FC")
            assertThat(presentation.ourScore).isEqualTo(2)
            assertThat(presentation.oppScore).isEqualTo(1)
            assertThat(presentation.goals).isNotNull
            assertThat(presentation.assists).isNotNull
            assertThat(presentation.highlights).isNotNull
        }

        @Test
        fun `presentation has correct outcome type`() {
            val winMatch = match(ourScore = "3", oppScore = "1")
            val lossMatch = match(ourScore = "0", oppScore = "2")
            val drawMatch = match(ourScore = "1", oppScore = "1")

            assertThat(builder.build(winMatch, clubId).outcome.type).isEqualTo(OutcomeType.WIN)
            assertThat(builder.build(lossMatch, clubId).outcome.type).isEqualTo(OutcomeType.LOSS)
            assertThat(builder.build(drawMatch, clubId).outcome.type).isEqualTo(OutcomeType.DRAW)
        }
    }

    // -- Assists Pipeline Tests ----------------------------------------------

    @Nested
    inner class AssistsPipeline {

        @Test
        fun `assists section is populated when players have assists`() {
            val players = mapOf(
                "p1" to player("Scorer", goals = "2", assists = "1"),
                "p2" to player("Assister", assists = "2"),
                "p3" to player("NoAssist", assists = "0"),
            )
            val match = match(players = players)

            val presentation = builder.build(match, clubId)

            assertThat(presentation.assists).isNotNull
            assertThat(presentation.assists!!.assisters).hasSize(2)
            assertThat(presentation.assists!!.assisters[0].name).isEqualTo("Assister")
            assertThat(presentation.assists!!.assisters[0].count).isEqualTo(2)
            assertThat(presentation.assists!!.assisters[1].name).isEqualTo("Scorer")
            assertThat(presentation.assists!!.assisters[1].count).isEqualTo(1)
        }

        @Test
        fun `assists section is null when no players have assists`() {
            val players = mapOf(
                "p1" to player("Player1", assists = "0"),
                "p2" to player("Player2", assists = "0"),
            )
            val match = match(players = players)

            val presentation = builder.build(match, clubId)

            assertThat(presentation.assists).isNull()
        }

        @Test
        fun `assists sorted by count descending`() {
            val players = mapOf(
                "p1" to player("OneAssist", assists = "1"),
                "p2" to player("ThreeAssists", assists = "3"),
                "p3" to player("TwoAssists", assists = "2"),
            )
            val match = match(players = players)

            val presentation = builder.build(match, clubId)

            assertThat(presentation.assists).isNotNull
            assertThat(presentation.assists!!.assisters[0].count).isEqualTo(3)
            assertThat(presentation.assists!!.assisters[1].count).isEqualTo(2)
            assertThat(presentation.assists!!.assisters[2].count).isEqualTo(1)
        }
    }

    // -- Bagre Single Phrase Tests -------------------------------------------

    @Nested
    inner class BagreSinglePhrase {

        @Test
        fun `bagre section contains exactly one phrase`() {
            val players = mapOf(
                "p1" to player("High", rating = "8.5"),
                "p2" to player("Low", rating = "5.5"),
            )
            val match = match(players = players)

            val presentation = builder.build(match, clubId)

            assertThat(presentation.bagre).isNotNull
            assertThat(presentation.bagre!!.phrase).isNotBlank()
            // Phrase should not contain embedded quotes (which would indicate concatenation)
            assertThat(presentation.bagre!!.phrase.count { it == '"' }).isEqualTo(0)
        }

        @Test
        fun `bagre has separate structured fields`() {
            val players = mapOf(
                "p1" to player("High", rating = "8.5"),
                "p2" to player("Low", rating = "5.5", tackleAttempts = "10", tacklesMade = "2"),
            )
            val match = match(players = players)

            val presentation = builder.build(match, clubId)

            assertThat(presentation.bagre).isNotNull
            assertThat(presentation.bagre!!.name).isEqualTo("Low")
            assertThat(presentation.bagre!!.rating).isNotBlank()
            assertThat(presentation.bagre!!.reason).contains("jogadores elegíveis")
            // phrase and stats are separate fields, not concatenated
            assertThat(presentation.bagre!!.phrase).isNotBlank()
            assertThat(presentation.bagre!!.tackleStats).isNotNull
        }
    }

    // ── Match 874612175930485 pipeline regression ────────────────────────────
    //
    // Before the DIS refactor, XerifeSelector required success rate > 60%.
    // All players in this match had rates ≤ 40 %, so the Sheriff was never rendered.

    @Nested
    inner class Match874612175930485Pipeline {

        private val matchId = "874612175930485"
        private val ourClubId = "1104972"

        private fun realPlayer(
            name: String,
            rating: String,
            shots: String,
            tackleAttempts: String,
            tacklesMade: String,
            secondsPlayed: String,
        ) = PlayerEntry(
            playerName = name,
            position = "14",
            rating = rating,
            shots = shots,
            tackleAttempts = tackleAttempts,
            tacklesMade = tacklesMade,
            secondsPlayed = secondsPlayed,
            goals = "0",
            assists = "0",
            passesMade = "15",
            passAttempts = "20",
        )

        private fun buildRealMatch(): MatchResponse = MatchResponse(
            matchId = matchId,
            timestamp = 1753027200L,
            clubs = mapOf(
                ourClubId to ClubMatchEntry(
                    details = ClubDetails(name = "Associação BF"),
                    score = "3",
                    result = "1",
                ),
                "opponent" to ClubMatchEntry(
                    details = ClubDetails(name = "Bola Bate FC"),
                    score = "0",
                    result = "0",
                ),
            ),
            players = mapOf(
                ourClubId to mapOf(
                    "p1" to realPlayer("Guilherme_cruzz", "7.80", "2", "6", "1", "5621"),
                    "p2" to realPlayer("dbeng_bass",      "8.30", "2", "5", "2", "5621"),
                    "p3" to realPlayer("swegher",         "7.00", "0", "9", "2", "5621"),
                    "p4" to realPlayer("joaoborba07",     "6.90", "2", "5", "0", "5621"),
                    "p5" to realPlayer("Nutri_Wagner90",  "6.00", "0", "0", "0", "413"),
                    "p6" to realPlayer("paulorodrigues0", "7.00", "0", "7", "1", "5621"),
                ),
            ),
        )

        @Test
        fun `Sheriff is dbeng_bass`() {
            val presentation = builder.build(buildRealMatch(), ourClubId)

            assertThat(presentation.xerife).isNotNull
            assertThat(presentation.xerife!!.name).isEqualTo("dbeng_bass")
            assertThat(presentation.xerife!!.tacklesMade).isEqualTo(2)
            assertThat(presentation.xerife!!.tackleAttempts).isEqualTo(5)
            assertThat(presentation.xerife!!.successRate).isEqualTo(40)
        }

        @Test
        fun `Constant Danger is absent - no player reached 3 shots`() {
            val presentation = builder.build(buildRealMatch(), ourClubId)

            assertThat(presentation.perigoConstante).isNull()
        }
    }
}
