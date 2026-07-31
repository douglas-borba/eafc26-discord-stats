package com.eafc26.discordstats.architecture

import com.eafc26.discordstats.application.interpretation.MatchInterpreter
import com.eafc26.discordstats.config.PhraseBank
import com.eafc26.discordstats.domain.interpretation.OffensiveNarrativeCategory
import com.eafc26.discordstats.ea.mapping.EaMatchMapper
import com.eafc26.discordstats.ea.mapping.MatchNormalizationResult
import com.eafc26.discordstats.ea.model.ClubDetails
import com.eafc26.discordstats.ea.model.ClubMatchEntry
import com.eafc26.discordstats.ea.model.MatchResponse
import com.eafc26.discordstats.ea.model.PlayerEntry
import com.eafc26.discordstats.presentation.MatchSummaryBuilder
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.ZoneOffset

class CanonicalInterpretationParityTest {

    @Test
    fun `canonical interpretation covers every current dashboard football decision`() {
        val source = richMatch()
        val normalized = EaMatchMapper().map(source) as MatchNormalizationResult.Success
        val interpretation = MatchInterpreter().interpret(
            normalized.match,
            com.eafc26.discordstats.domain.match.ClubId(OUR_CLUB),
        )
        val legacy = MatchSummaryBuilder(PhraseBank(jacksonObjectMapper()))
            .build(source, OUR_CLUB, ZoneOffset.UTC)
        val names = interpretation.footballMatch.participants
            .first { it.club.id.value == OUR_CLUB }
            .players.associate { it.player.id to it.player.platformName!!.value }
        fun name(id: com.eafc26.discordstats.domain.match.PlayerId?) = id?.let(names::get)

        assertThat(interpretation.result.ourScore.goals).isEqualTo(legacy.ourScore)
        assertThat(interpretation.result.opponentScore.goals).isEqualTo(legacy.oppScore)
        assertThat(name(interpretation.awards.craque.winnerId)).isEqualTo(legacy.craque!!.name)
        assertThat(name(interpretation.awards.bagre.winnerId)).isEqualTo(legacy.bagre!!.name)
        assertThat(name(interpretation.awards.xerife.winnerId)).isEqualTo(legacy.xerife!!.name)

        assertThat(
            interpretation.features.contributions.goalScorers.map {
                name(it.playerId) to it.goals
            }
        ).containsExactlyElementsOf(legacy.goals!!.scorers.map { it.name to it.count })
        assertThat(
            interpretation.features.contributions.assistProviders.map {
                name(it.playerId) to it.assists
            }
        ).containsExactlyElementsOf(legacy.assists!!.assisters.map { it.name to it.count })
        assertThat(interpretation.features.highlights.players.map { name(it.playerId) })
            .containsExactlyElementsOf(legacy.highlights!!.top3.map { it.name })

        val canonicalOffensive = interpretation.features.offensiveNarratives.map {
            Triple(name(it.playerId), it.shots, it.goals)
        }
        assertThat(canonicalOffensive).containsExactlyElementsOf(
            legacy.offensiveNarratives.map { Triple(it.name, it.shots, it.goals) }
        )
        assertThat(interpretation.features.offensiveNarratives.map { it.category })
            .containsExactly(
                OffensiveNarrativeCategory.FELL_SHORT,
                OffensiveNarrativeCategory.LACKED_COMPOSURE,
            )

        assertThat(name(interpretation.features.redCard!!.playerId))
            .isEqualTo(legacy.redCard!!.name)
        assertThat(name(interpretation.features.passPrecision!!.playerId))
            .isEqualTo(legacy.passePrecisao!!.name)
        assertThat(interpretation.features.passPrecision!!.accuracyPercent)
            .isEqualTo(legacy.passePrecisao!!.accuracy)
        assertThat(name(interpretation.features.lostMail!!.playerId))
            .isEqualTo(legacy.correioExtraviado!!.name)
        assertThat(interpretation.features.lostMail!!.teamAccuracyPercent)
            .isEqualTo(legacy.correioExtraviado!!.teamAccuracyPct)
        assertThat(name(interpretation.features.goalkeeper!!.playerId))
            .isEqualTo(legacy.muralha!!.name)
        assertThat(interpretation.features.goalkeeper!!.archetype.name)
            .isEqualTo(legacy.muralha!!.archetype.name)
    }

    private fun richMatch() = MatchResponse(
        matchId = "canonical-rich-match",
        timestamp = 1_767_225_600,
        matchType = "leagueMatch",
        clubs = linkedMapOf(
            OUR_CLUB to ClubMatchEntry(
                details = ClubDetails(name = "Our FC"),
                score = "3",
                result = "1",
            ),
            "opponent" to ClubMatchEntry(
                details = ClubDetails(name = "Opponent FC"),
                score = "1",
                result = "0",
            ),
        ),
        players = mapOf(
            OUR_CLUB to linkedMapOf(
                "bagre-id" to player(
                    "Bagre",
                    rating = "5.0",
                    passesMade = "2",
                    passAttempts = "10",
                    tacklesMade = "1",
                    tackleAttempts = "5",
                ),
                "star-id" to player(
                    "Star",
                    rating = "9.0",
                    goals = "2",
                    assists = "1",
                    shots = "6",
                    passesMade = "10",
                    passAttempts = "10",
                    tacklesMade = "4",
                    tackleAttempts = "5",
                    redCards = "1",
                    mom = "1",
                ),
                "creator-id" to player(
                    "Creator",
                    rating = "8.0",
                    assists = "2",
                    shots = "5",
                    passesMade = "8",
                    passAttempts = "10",
                ),
                "defender-id" to player(
                    "Defender",
                    rating = "7.0",
                    passesMade = "9",
                    passAttempts = "10",
                    tacklesMade = "5",
                    tackleAttempts = "6",
                ),
                "keeper-id" to player(
                    "Keeper",
                    rating = "8.5",
                    position = "0",
                    saves = "5",
                    goalsConceded = "1",
                    reflexSaves = "3",
                ),
            )
        ),
    )

    private fun player(
        name: String,
        rating: String,
        goals: String = "0",
        assists: String = "0",
        shots: String = "0",
        passesMade: String? = null,
        passAttempts: String? = null,
        tacklesMade: String? = null,
        tackleAttempts: String? = null,
        redCards: String = "0",
        mom: String = "0",
        position: String = "midfielder",
        saves: String? = null,
        goalsConceded: String? = null,
        reflexSaves: String? = null,
    ) = PlayerEntry(
        playerName = name,
        position = position,
        goals = goals,
        assists = assists,
        rating = rating,
        manOfTheMatch = mom,
        shots = shots,
        passesMade = passesMade,
        passAttempts = passAttempts,
        tacklesMade = tacklesMade,
        tackleAttempts = tackleAttempts,
        redCards = redCards,
        saves = saves,
        goalsConceded = goalsConceded,
        reflexSaves = reflexSaves,
        secondsPlayed = "5400",
    )

    companion object {
        const val OUR_CLUB = "our-club"
    }
}
