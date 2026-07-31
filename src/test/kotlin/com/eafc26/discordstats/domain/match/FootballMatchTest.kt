package com.eafc26.discordstats.domain.match

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

class FootballMatchTest {

    @Test
    fun `football match represents normalized immutable facts for both participants`() {
        val ourPlayer = player("our-player", "Our Player")
        val opponentPlayer = player("opponent-player", "Opponent Player")

        val match = FootballMatch(
            id = MatchId("match-1"),
            playedAt = Instant.parse("2026-07-30T20:00:00Z"),
            competition = CompetitionType.LEAGUE,
            participants = listOf(
                clubPerformance("our-club", "Our FC", 3, ReportedMatchResult.WIN, ourPlayer),
                clubPerformance("opponent", "Opponent FC", 1, ReportedMatchResult.LOSS, opponentPlayer),
            ),
        )

        assertThat(match.id).isEqualTo(MatchId("match-1"))
        assertThat(match.participants).hasSize(2)
        assertThat(match.participants[0].score).isEqualTo(Score(3))
        assertThat(match.participants[0].players.single()).isEqualTo(ourPlayer)
        assertThat(match.participants[1].players.single()).isEqualTo(opponentPlayer)
    }

    @Test
    fun `football match is neutral and does not designate our club`() {
        val match = validMatch()

        assertThat(match.participants.map { it.club.id })
            .containsExactly(ClubId("club-a"), ClubId("club-b"))
        assertThat(FootballMatch::class.java.declaredFields.map { it.name })
            .doesNotContain("ourClub", "opponent")
    }

    @Test
    fun `football match requires at least two participants`() {
        assertThatThrownBy {
            FootballMatch(
                id = MatchId("match-1"),
                playedAt = Instant.EPOCH,
                competition = null,
                participants = listOf(clubPerformance("only-club", "Only FC", 0, null)),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `football match rejects duplicate club identities`() {
        val first = clubPerformance("same-club", "First Name", 1, ReportedMatchResult.WIN)
        val duplicate = clubPerformance("same-club", "Second Name", 0, ReportedMatchResult.LOSS)

        assertThatThrownBy {
            FootballMatch(
                id = MatchId("match-1"),
                playedAt = Instant.EPOCH,
                competition = CompetitionType.FRIENDLY,
                participants = listOf(first, duplicate),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `club performance rejects duplicate player identities`() {
        val player = player("same-player", "Player")

        assertThatThrownBy {
            clubPerformance(
                id = "club-a",
                name = "Club A",
                score = 1,
                result = ReportedMatchResult.WIN,
                player,
                player.copy(player = player.player.copy(platformName = DisplayName("Changed Name"))),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `goalkeeper facts remain typed and separate from outfield role`() {
        val goalkeeper = player("gk-id", "Keeper").copy(
            role = PlayerRole.Goalkeeper,
            goalkeeping = GoalkeepingStats(
                saves = 7,
                goalsConceded = 1,
                cleanSheetAsGoalkeeper = false,
                cleanSheetAsAny = false,
                saveBreakdown = SaveBreakdown(
                    goodDirection = 2,
                    reflex = 4,
                    parry = 1,
                    punch = 0,
                    diving = 2,
                    crosses = 1,
                ),
            ),
        )

        assertThat(goalkeeper.role).isEqualTo(PlayerRole.Goalkeeper)
        assertThat(goalkeeper.goalkeeping!!.saves).isEqualTo(7)
        assertThat(goalkeeper.goalkeeping!!.saveBreakdown.reflex).isEqualTo(4)
    }

    private fun validMatch(): FootballMatch = FootballMatch(
        id = MatchId("match-1"),
        playedAt = Instant.EPOCH,
        competition = CompetitionType.PLAYOFF,
        participants = listOf(
            clubPerformance("club-a", "Club A", 2, ReportedMatchResult.DRAW),
            clubPerformance("club-b", "Club B", 2, ReportedMatchResult.DRAW),
        ),
    )

    private fun clubPerformance(
        id: String,
        name: String,
        score: Int,
        result: ReportedMatchResult?,
        vararg players: PlayerMatchPerformance,
    ): ClubMatchPerformance = ClubMatchPerformance(
        club = ClubIdentity(ClubId(id), ClubName(name)),
        score = Score(score),
        reportedResult = result,
        players = players.toList(),
    )

    private fun player(id: String, name: String): PlayerMatchPerformance = PlayerMatchPerformance(
        player = PlayerIdentity(
            id = PlayerId(id),
            platformName = DisplayName(name),
            proName = null,
        ),
        role = PlayerRole.Outfield(OutfieldPosition.MIDFIELDER),
        participation = Participation(Duration.ofMinutes(90), ParticipationStatus.COMPLETED),
        rating = MatchRating(BigDecimal("7.5")),
        attacking = AttackingStats(goals = 1, assists = 0, shots = 3),
        passing = PassingStats(attempted = 20, completed = 16),
        defending = DefendingStats(tacklesAttempted = 5, tacklesCompleted = 3),
        discipline = DisciplineStats(redCards = 0),
        goalkeeping = null,
        eaRecognition = EaRecognition(manOfTheMatch = false),
    )
}
