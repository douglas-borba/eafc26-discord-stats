package com.eafc26.discordstats.service

import com.eafc26.discordstats.canonical.CanonicalMatch
import com.eafc26.discordstats.domain.interpretation.AwardDecision
import com.eafc26.discordstats.domain.interpretation.AwardType
import com.eafc26.discordstats.domain.interpretation.MatchAwards
import com.eafc26.discordstats.domain.interpretation.MatchInterpretation
import com.eafc26.discordstats.domain.interpretation.MatchOutcome
import com.eafc26.discordstats.domain.interpretation.ResultDecision
import com.eafc26.discordstats.domain.match.AttackingStats
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.ClubIdentity
import com.eafc26.discordstats.domain.match.ClubMatchPerformance
import com.eafc26.discordstats.domain.match.ClubName
import com.eafc26.discordstats.domain.match.DefendingStats
import com.eafc26.discordstats.domain.match.DisciplineStats
import com.eafc26.discordstats.domain.match.DisplayName
import com.eafc26.discordstats.domain.match.EaRecognition
import com.eafc26.discordstats.domain.match.FootballMatch
import com.eafc26.discordstats.domain.match.MatchId
import com.eafc26.discordstats.domain.match.MatchRating
import com.eafc26.discordstats.domain.match.Participation
import com.eafc26.discordstats.domain.match.ParticipationStatus
import com.eafc26.discordstats.domain.match.PassingStats
import com.eafc26.discordstats.domain.match.PlayerId
import com.eafc26.discordstats.domain.match.PlayerIdentity
import com.eafc26.discordstats.domain.match.PlayerMatchPerformance
import com.eafc26.discordstats.domain.match.PlayerRole
import com.eafc26.discordstats.domain.match.Score
import com.eafc26.discordstats.history.MatchHistoryQuery
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

class PlayerProfileServiceTest {
    private lateinit var history: MatchHistoryService
    private lateinit var service: PlayerProfileService

    @BeforeEach
    fun setUp() {
        history = mock()
        service = PlayerProfileService(history)
    }

    @Test
    fun `empty history produces no player profiles`() {
        whenever(history.list()).thenReturn(emptyList())

        assertThat(service.listPlayers()).isEmpty()
    }

    @Test
    fun `unknown player has no profile`() {
        whenever(history.list(any<MatchHistoryQuery>())).thenReturn(emptyList())

        assertThat(service.findById(PlayerId("missing"))).isNull()
        verify(history).list(MatchHistoryQuery(playerId = PlayerId("missing")))
    }

    @Test
    fun `aggregates canonical results ratings production awards and discipline`() {
        val playerId = PlayerId("player-1")
        val matches = listOf(
            canonical("m3", "2026-07-03T10:00:00Z", playerId, "Current Name", MatchOutcome.WIN, "8.0", 2, 1, 0, setOf(AwardType.CRAQUE)),
            canonical("m2", "2026-07-02T10:00:00Z", playerId, "Old Name", MatchOutcome.DRAW, "6.0", 0, 2, 1, setOf(AwardType.BAGRE)),
            canonical("m1", "2026-07-01T10:00:00Z", playerId, "Old Name", MatchOutcome.LOSS, null, 1, 0, 0, setOf(AwardType.XERIFE)),
        )
        whenever(history.list(any<MatchHistoryQuery>())).thenReturn(matches)

        val profile = service.findById(playerId)!!

        assertThat(profile.displayName).isEqualTo("Current Name")
        assertThat(profile.matchCount).isEqualTo(3)
        assertThat(profile.wins).isEqualTo(1)
        assertThat(profile.draws).isEqualTo(1)
        assertThat(profile.losses).isEqualTo(1)
        assertThat(profile.averageRating).isEqualByComparingTo("7.00")
        assertThat(profile.ratedMatchCount).isEqualTo(2)
        assertThat(profile.goals).isEqualTo(3)
        assertThat(profile.assists).isEqualTo(3)
        assertThat(profile.craques).isEqualTo(1)
        assertThat(profile.bagres).isEqualTo(1)
        assertThat(profile.xerifes).isEqualTo(1)
        assertThat(profile.redCards).isEqualTo(1)
        assertThat(profile.recentMatches.map { it.matchId.value }).containsExactly("m3", "m2", "m1")
        assertThat(profile.recentMatches.first().awards).containsExactly(AwardType.CRAQUE)
    }

    @Test
    fun `recent matches honor limit and preserve history order`() {
        val playerId = PlayerId("player-1")
        val matches = (6 downTo 1).map { day ->
            canonical(
                "m$day",
                "2026-07-${day.toString().padStart(2, '0')}T10:00:00Z",
                playerId,
                "Player",
                MatchOutcome.WIN,
                "7.0",
                0,
                0,
                0,
                emptySet(),
            )
        }
        whenever(history.list(any<MatchHistoryQuery>())).thenReturn(matches)

        val profile = service.findById(playerId, recentMatchLimit = 3)!!

        assertThat(profile.matchCount).isEqualTo(6)
        assertThat(profile.recentMatches.map { it.matchId.value }).containsExactly("m6", "m5", "m4")
    }

    @Test
    fun `player index includes perspective players once per appearance`() {
        val first = canonical("new", "2026-07-03T10:00:00Z", PlayerId("one"), "Ana", MatchOutcome.WIN, "8", 0, 0, 0, emptySet())
        val second = canonical("old", "2026-07-02T10:00:00Z", PlayerId("one"), "Ana Antiga", MatchOutcome.LOSS, "7", 0, 0, 0, emptySet())
        val third = canonical("other", "2026-07-01T10:00:00Z", PlayerId("two"), "Bruno", MatchOutcome.DRAW, "6", 0, 0, 0, emptySet())
        whenever(history.list()).thenReturn(listOf(first, second, third))

        val result = service.listPlayers()

        assertThat(result.map { it.playerId.value }).containsExactly("one", "two")
        assertThat(result.first().displayName).isEqualTo("Ana")
        assertThat(result.first().matchCount).isEqualTo(2)
    }

    private fun canonical(
        id: String,
        playedAt: String,
        playerId: PlayerId,
        name: String,
        outcome: MatchOutcome,
        rating: String?,
        goals: Int,
        assists: Int,
        redCards: Int,
        awards: Set<AwardType>,
    ): CanonicalMatch {
        val player = PlayerMatchPerformance(
            player = PlayerIdentity(playerId, DisplayName(name), null),
            role = PlayerRole.Outfield(null),
            participation = Participation(Duration.ofMinutes(90), ParticipationStatus.COMPLETED),
            rating = rating?.let { MatchRating(BigDecimal(it)) },
            attacking = AttackingStats(goals, assists, 3),
            passing = PassingStats(20, 18),
            defending = DefendingStats(4, 2),
            discipline = DisciplineStats(redCards),
            goalkeeping = null,
            eaRecognition = EaRecognition(false),
        )
        val ours = ClubMatchPerformance(
            ClubIdentity(OUR_CLUB, ClubName("Our FC")),
            Score(if (outcome == MatchOutcome.WIN) 2 else if (outcome == MatchOutcome.DRAW) 1 else 0),
            null,
            listOf(player),
        )
        val opponent = ClubMatchPerformance(
            ClubIdentity(OPPONENT, ClubName("Opponent FC")),
            Score(if (outcome == MatchOutcome.LOSS) 2 else if (outcome == MatchOutcome.DRAW) 1 else 0),
            null,
            emptyList(),
        )
        val footballMatch = FootballMatch(
            MatchId(id),
            Instant.parse(playedAt),
            null,
            listOf(ours, opponent),
        )
        val result = mock<ResultDecision>()
        whenever(result.ourClub).thenReturn(OUR_CLUB)
        whenever(result.opponentClub).thenReturn(OPPONENT)
        whenever(result.ourScore).thenReturn(ours.score)
        whenever(result.opponentScore).thenReturn(opponent.score)
        whenever(result.outcome).thenReturn(outcome)

        fun award(type: AwardType): AwardDecision {
            val decision = mock<AwardDecision>()
            whenever(decision.type).thenReturn(type)
            whenever(decision.winnerId).thenReturn(playerId.takeIf { type in awards })
            return decision
        }

        val matchAwards = mock<MatchAwards>()
        val craque = award(AwardType.CRAQUE)
        val bagre = award(AwardType.BAGRE)
        val xerife = award(AwardType.XERIFE)
        whenever(matchAwards.craque).thenReturn(craque)
        whenever(matchAwards.bagre).thenReturn(bagre)
        whenever(matchAwards.xerife).thenReturn(xerife)
        val interpretation = mock<MatchInterpretation>()
        whenever(interpretation.perspectiveClubId).thenReturn(OUR_CLUB)
        whenever(interpretation.result).thenReturn(result)
        whenever(interpretation.awards).thenReturn(matchAwards)
        val canonical = mock<CanonicalMatch>()
        whenever(canonical.matchId).thenReturn(footballMatch.id)
        whenever(canonical.footballMatch).thenReturn(footballMatch)
        whenever(canonical.interpretation).thenReturn(interpretation)
        return canonical
    }

    private companion object {
        val OUR_CLUB = ClubId("ours")
        val OPPONENT = ClubId("opponent")
    }
}
