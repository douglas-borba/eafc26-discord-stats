package com.eafc26.discordstats.presentation

import com.eafc26.discordstats.application.interpretation.MatchInterpreter
import com.eafc26.discordstats.application.story.MatchStoryExtractor
import com.eafc26.discordstats.config.PhraseBank
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.ea.mapping.EaMatchMapper
import com.eafc26.discordstats.ea.mapping.MatchNormalizationResult
import com.eafc26.discordstats.ea.model.ClubDetails
import com.eafc26.discordstats.ea.model.ClubMatchEntry
import com.eafc26.discordstats.ea.model.MatchResponse
import com.eafc26.discordstats.ea.model.PlayerEntry
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.ZoneOffset

class AdvancedMatchSummaryBuilderTest {

    @Test
    fun `renders automatic low performance with its own factual recognition title`() {
        val source = MatchResponse(
            matchId = "low-performance-match",
            timestamp = 1_767_225_600,
            clubs = linkedMapOf(
                OUR_CLUB to ClubMatchEntry(details = ClubDetails(name = "Our FC"), score = "1", result = "1"),
                "opponent" to ClubMatchEntry(details = ClubDetails(name = "Opponent"), score = "0", result = "0"),
            ),
            players = mapOf(
                OUR_CLUB to mapOf(
                    "high" to player("High", "8.5", "2", "2", null),
                    "low" to player("Low", "7.3", "2", "2", null),
                ),
            ),
        )
        val match = (EaMatchMapper().map(source) as MatchNormalizationResult.Success).match
        val interpretation = MatchInterpreter().interpret(match, ClubId(OUR_CLUB))
        val stories = MatchStoryExtractor().extract(interpretation)

        val presentation = MatchSummaryBuilder(PhraseBank(jacksonObjectMapper()))
            .build(match, interpretation, stories, ZoneOffset.UTC)

        assertThat(presentation.bagre!!.title).isEqualTo("📉 BAIXO RENDIMENTO")
        assertThat(presentation.bagre!!.reason).isEqualTo("Menor nota entre os jogadores elegíveis.")
    }

    @Test
    fun `projects decoded advanced facts into the conditional card sections`() {
        val source = MatchResponse(
            matchId = "advanced-match",
            timestamp = 1_767_225_600,
            clubs = linkedMapOf(
                OUR_CLUB to ClubMatchEntry(details = ClubDetails(name = "Our FC"), score = "3", result = "1"),
                "opponent" to ClubMatchEntry(details = ClubDetails(name = "Opponent"), score = "1", result = "0"),
            ),
            players = mapOf(
                OUR_CLUB to mapOf(
                    "reader" to player("Reader", "8.5", "2", "2", "6:6,112:8,115:2,152:9,174:18"),
                    "support" to player("Support", "7.0", "5", "5", null),
                    "low" to player("Low", "5.0", "0", "0", null),
                ),
            ),
        )
        val match = (EaMatchMapper().map(source) as MatchNormalizationResult.Success).match
        val interpretation = MatchInterpreter().interpret(match, ClubId(OUR_CLUB))
        val stories = MatchStoryExtractor().extract(interpretation)

        val presentation = MatchSummaryBuilder(PhraseBank(jacksonObjectMapper()))
            .build(match, interpretation, stories, ZoneOffset.UTC)

        assertThat(presentation.behindThePlay).isNotNull
        assertThat(presentation.behindThePlay!!.name).isEqualTo("Reader")
        assertThat(presentation.behindThePlay!!.secondAssists).isEqualTo(2)
        assertThat(presentation.behindThePlay!!.throughPasses).isEqualTo(9)
        assertThat(presentation.oneOnOne).isNotNull
        assertThat(presentation.oneOnOne!!.beats).isEqualTo(8)
        assertThat(presentation.oneOnOne!!.dribblesCompleted).isEqualTo(18)
        assertThat(presentation.xerife).isNotNull
        assertThat(presentation.xerife!!.interceptions).isEqualTo(6)
        assertThat(presentation.xerife!!.tacklesMade).isEqualTo(2)
    }

    private fun player(
        name: String,
        rating: String,
        tacklesMade: String,
        tackleAttempts: String,
        aggregate0: String?,
    ) = PlayerEntry(
        playerName = name,
        position = "14",
        rating = rating,
        goals = "0",
        assists = "0",
        shots = "0",
        passAttempts = "12",
        passesMade = "10",
        tackleAttempts = tackleAttempts,
        tacklesMade = tacklesMade,
        secondsPlayed = "5400",
        matchEventAggregate0 = aggregate0,
    )

    private companion object {
        const val OUR_CLUB = "ours"
    }
}
