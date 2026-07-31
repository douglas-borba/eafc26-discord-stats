package com.eafc26.discordstats.ea.mapping

import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.CompetitionType
import com.eafc26.discordstats.domain.match.DisplayName
import com.eafc26.discordstats.domain.match.PlayerId
import com.eafc26.discordstats.domain.match.PlayerRole
import com.eafc26.discordstats.domain.match.ReportedMatchResult
import com.eafc26.discordstats.ea.model.ClubDetails
import com.eafc26.discordstats.ea.model.ClubMatchEntry
import com.eafc26.discordstats.ea.model.MatchResponse
import com.eafc26.discordstats.ea.model.PlayerEntry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

class EaMatchMapperTest {

    private val mapper = EaMatchMapper()

    @Test
    fun `maps a complete EA response into typed normalized match facts`() {
        val source = match(
            players = mapOf(
                "our-club" to linkedMapOf(
                    "outfield-id" to player(
                        name = "platform_tag",
                        rating = "8.75",
                        goals = "2",
                        assists = "1",
                        shots = "5",
                        passAttempts = "20",
                        passesMade = "18",
                        tackleAttempts = "7",
                        tacklesMade = "5",
                        redCards = "0",
                        secondsPlayed = "5400",
                        mom = "1",
                    ),
                    "goalkeeper-id" to goalkeeper("Keeper"),
                ),
            ),
        )

        val result = mapper.map(source, mapOf("PLATFORM_TAG" to "Camisa 10"))
            .success()
        val normalized = result.match
        val ourClub = normalized.participants.single { it.club.id == ClubId("our-club") }
        val outfield = ourClub.players.single { it.player.id == PlayerId("outfield-id") }
        val goalkeeper = ourClub.players.single { it.player.id == PlayerId("goalkeeper-id") }

        assertThat(normalized.id.value).isEqualTo("match-1")
        assertThat(normalized.playedAt).isEqualTo(Instant.ofEpochSecond(1_718_500_000L))
        assertThat(normalized.competition).isEqualTo(CompetitionType.LEAGUE)
        assertThat(ourClub.score.goals).isEqualTo(3)
        assertThat(ourClub.reportedResult).isEqualTo(ReportedMatchResult.WIN)
        assertThat(outfield.player.platformName).isEqualTo(DisplayName("platform_tag"))
        assertThat(outfield.player.proName).isEqualTo(DisplayName("Camisa 10"))
        assertThat(outfield.rating!!.value).isEqualByComparingTo(BigDecimal("8.75"))
        assertThat(outfield.attacking.goals).isEqualTo(2)
        assertThat(outfield.passing.accuracy!!.numerator).isEqualTo(18)
        assertThat(outfield.defending.tackleAccuracy!!.denominator).isEqualTo(7)
        assertThat(outfield.participation.duration).isEqualTo(Duration.ofSeconds(5400))
        assertThat(outfield.eaRecognition.manOfTheMatch).isTrue()
        assertThat(outfield.role).isInstanceOf(PlayerRole.Outfield::class.java)
        assertThat(goalkeeper.role).isEqualTo(PlayerRole.Goalkeeper)
        assertThat(goalkeeper.goalkeeping!!.saveBreakdown.reflex).isEqualTo(4)
        assertThat(result.warnings).isEmpty()
    }

    @Test
    fun `optional absent fields remain absent without preventing a valid match`() {
        val source = match(
            matchType = null,
            clubNames = false,
            players = mapOf(
                "our-club" to mapOf(
                    "unnamed-id" to PlayerEntry(
                        playerName = null,
                        position = null,
                        rating = null,
                        goals = null,
                        assists = null,
                        shots = null,
                        passAttempts = null,
                        passesMade = null,
                        tackleAttempts = null,
                        tacklesMade = null,
                        redCards = null,
                        secondsPlayed = null,
                        manOfTheMatch = null,
                    ),
                ),
            ),
        )

        val result = mapper.map(source).success()
        val player = result.match.participants.first().players.single()

        assertThat(result.match.competition).isNull()
        assertThat(result.match.participants.map { it.club.name }).containsOnlyNulls()
        assertThat(player.player.preferredDisplayName).isNull()
        assertThat(player.rating).isNull()
        assertThat(player.attacking.goals).isNull()
        assertThat(player.passing.accuracy).isNull()
        assertThat(player.participation.duration).isNull()
        assertThat(result.warnings.map { it.code }).contains(
            NormalizationIssueCode.MISSING_CLUB_NAME,
            NormalizationIssueCode.MISSING_PLAYER_NAME,
        )
    }

    @Test
    fun `invalid statistics become absent and inconsistent attempt pairs are clamped`() {
        val source = match(
            players = mapOf(
                "our-club" to mapOf(
                    "anomalous-id" to player(
                        name = "Anomalous",
                        rating = "-1",
                        goals = "bad",
                        assists = "-2",
                        shots = "",
                        passAttempts = "10",
                        passesMade = "12",
                        tackleAttempts = "4",
                        tacklesMade = "7",
                        redCards = "invalid",
                        secondsPlayed = "-10",
                        mom = "yes",
                    ),
                ),
            ),
        )

        val result = mapper.map(source).success()
        val player = result.match.participants.first().players.single()

        assertThat(player.rating).isNull()
        assertThat(player.attacking.goals).isNull()
        assertThat(player.attacking.assists).isNull()
        assertThat(player.attacking.shots).isNull()
        assertThat(player.passing.attempted).isEqualTo(10)
        assertThat(player.passing.completed).isEqualTo(10)
        assertThat(player.defending.tacklesAttempted).isEqualTo(4)
        assertThat(player.defending.tacklesCompleted).isEqualTo(4)
        assertThat(player.discipline.redCards).isNull()
        assertThat(player.participation.duration).isNull()
        assertThat(player.eaRecognition.manOfTheMatch).isNull()
        assertThat(result.warnings.map { it.code }).contains(
            NormalizationIssueCode.NEGATIVE_RATING,
            NormalizationIssueCode.INVALID_INTEGER,
            NormalizationIssueCode.NEGATIVE_STATISTIC,
            NormalizationIssueCode.COMPLETED_EXCEEDS_ATTEMPTED,
            NormalizationIssueCode.INVALID_BOOLEAN_FLAG,
        )
    }

    @Test
    fun `missing scores are recovered from opponent goalsAgainst when possible`() {
        val source = match(
            ourScore = null,
            opponentScore = null,
            ourGoalsAgainst = "1",
            opponentGoalsAgainst = "3",
        )

        val result = mapper.map(source).success()

        assertThat(result.match.participants.map { it.score.goals }).containsExactly(3, 1)
        assertThat(result.warnings.count {
            it.code == NormalizationIssueCode.SCORE_FALLBACK_TO_GOALS_AGAINST
        }).isEqualTo(2)
    }

    @Test
    fun `unrecoverable missing score falls back to zero explicitly`() {
        val source = match(
            ourScore = "invalid",
            opponentScore = "2",
            opponentGoalsAgainst = null,
        )

        val result = mapper.map(source).success()

        assertThat(result.match.participants.first().score.goals).isZero()
        assertThat(result.warnings.map { it.code })
            .contains(NormalizationIssueCode.INVALID_INTEGER, NormalizationIssueCode.SCORE_FALLBACK_TO_ZERO)
    }

    @Test
    fun `score remains authoritative when goalsAgainst conflicts`() {
        val result = mapper.map(
            match(
                ourScore = "3",
                opponentScore = "1",
                ourGoalsAgainst = "8",
                opponentGoalsAgainst = "9",
            )
        ).success()

        assertThat(result.match.participants.map { it.score.goals }).containsExactly(3, 1)
        assertThat(result.warnings.count {
            it.code == NormalizationIssueCode.SCORE_GOALS_AGAINST_CONFLICT
        }).isEqualTo(2)
    }

    @Test
    fun `blank player map key gets a match-local fallback identity`() {
        val result = mapper.map(
            match(players = mapOf("our-club" to linkedMapOf("" to player(name = "Anonymous"))))
        ).success()
        val player = result.match.participants.first().players.single()

        assertThat(player.player.id).isEqualTo(PlayerId("anonymous-player-1"))
        assertThat(result.warnings.map { it.code }).contains(NormalizationIssueCode.BLANK_PLAYER_ID)
    }

    @Test
    fun `players belonging to a club absent from clubs are ignored with warning`() {
        val result = mapper.map(
            match(players = mapOf("unknown-club" to mapOf("p1" to player(name = "Ignored"))))
        ).success()

        assertThat(result.match.participants.flatMap { it.players }).isEmpty()
        assertThat(result.warnings.map { it.code }).contains(NormalizationIssueCode.UNKNOWN_PLAYER_CLUB)
    }

    @Test
    fun `unknown competition and result values become absent with warnings`() {
        val source = match(matchType = "mysteryMatch", ourResult = "9")

        val result = mapper.map(source).success()

        assertThat(result.match.competition).isNull()
        assertThat(result.match.participants.first().reportedResult).isNull()
        assertThat(result.warnings.map { it.code }).contains(
            NormalizationIssueCode.INVALID_COMPETITION_TYPE,
            NormalizationIssueCode.INVALID_REPORTED_RESULT,
        )
    }

    @Test
    fun `blank match identity rejects normalization`() {
        val result = mapper.map(match(matchId = " "))

        assertThat(result).isInstanceOf(MatchNormalizationResult.Rejected::class.java)
        assertThat((result as MatchNormalizationResult.Rejected).errors.map { it.code })
            .containsExactly(NormalizationIssueCode.BLANK_MATCH_ID)
    }

    @Test
    fun `timestamp outside Instant range rejects normalization explicitly`() {
        val result = mapper.map(match().copy(timestamp = Long.MAX_VALUE))

        assertThat(result).isInstanceOf(MatchNormalizationResult.Rejected::class.java)
        assertThat((result as MatchNormalizationResult.Rejected).errors.map { it.code })
            .containsExactly(NormalizationIssueCode.INVALID_TIMESTAMP)
    }

    @Test
    fun `fewer than two clubs rejects normalization instead of inventing an opponent`() {
        val result = mapper.map(
            match().copy(clubs = mapOf("our-club" to club("Our FC", "1", "1", null)))
        )

        assertThat(result).isInstanceOf(MatchNormalizationResult.Rejected::class.java)
        assertThat((result as MatchNormalizationResult.Rejected).errors.map { it.code })
            .containsExactly(NormalizationIssueCode.INSUFFICIENT_CLUBS)
    }

    private fun match(
        matchId: String = "match-1",
        matchType: String? = "leagueMatch",
        ourScore: String? = "3",
        opponentScore: String? = "1",
        ourGoalsAgainst: String? = null,
        opponentGoalsAgainst: String? = null,
        ourResult: String? = "1",
        clubNames: Boolean = true,
        players: Map<String, Map<String, PlayerEntry>> = emptyMap(),
    ): MatchResponse = MatchResponse(
        matchId = matchId,
        timestamp = 1_718_500_000L,
        matchType = matchType,
        clubs = linkedMapOf(
            "our-club" to club(
                if (clubNames) "Our FC" else null,
                ourScore,
                ourResult,
                ourGoalsAgainst,
            ),
            "opponent" to club(
                if (clubNames) "Opponent FC" else null,
                opponentScore,
                "0",
                opponentGoalsAgainst,
            ),
        ),
        players = players,
    )

    private fun club(
        name: String?,
        score: String?,
        result: String?,
        goalsAgainst: String?,
    ): ClubMatchEntry = ClubMatchEntry(
        details = name?.let { ClubDetails(name = it) },
        score = score,
        goalsAgainst = goalsAgainst,
        result = result,
    )

    private fun player(
        name: String,
        rating: String = "7.0",
        goals: String = "0",
        assists: String = "0",
        shots: String = "0",
        passAttempts: String = "20",
        passesMade: String = "15",
        tackleAttempts: String = "5",
        tacklesMade: String = "3",
        redCards: String = "0",
        secondsPlayed: String = "5400",
        mom: String = "0",
    ): PlayerEntry = PlayerEntry(
        playerName = name,
        position = "14",
        rating = rating,
        goals = goals,
        assists = assists,
        shots = shots,
        passAttempts = passAttempts,
        passesMade = passesMade,
        tackleAttempts = tackleAttempts,
        tacklesMade = tacklesMade,
        redCards = redCards,
        secondsPlayed = secondsPlayed,
        manOfTheMatch = mom,
    )

    private fun goalkeeper(name: String): PlayerEntry = PlayerEntry(
        playerName = name,
        position = PlayerEntry.POSITION_GOALKEEPER,
        rating = "8.5",
        saves = "7",
        goalsConceded = "1",
        reflexSaves = "4",
        cleanSheetsGk = "0",
        cleanSheetsAny = "0",
        secondsPlayed = "5400",
    )

    private fun MatchNormalizationResult.success(): MatchNormalizationResult.Success {
        assertThat(this).isInstanceOf(MatchNormalizationResult.Success::class.java)
        return this as MatchNormalizationResult.Success
    }
}
