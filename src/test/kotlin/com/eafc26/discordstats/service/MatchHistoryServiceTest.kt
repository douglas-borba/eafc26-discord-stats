package com.eafc26.discordstats.service

import com.eafc26.discordstats.application.repository.CanonicalMatchRepository
import com.eafc26.discordstats.application.repository.CanonicalRepositoryMetadata
import com.eafc26.discordstats.canonical.CanonicalMatch
import com.eafc26.discordstats.domain.match.ClubMatchPerformance
import com.eafc26.discordstats.domain.match.CompetitionType
import com.eafc26.discordstats.domain.match.DisplayName
import com.eafc26.discordstats.domain.match.FootballMatch
import com.eafc26.discordstats.domain.match.MatchId
import com.eafc26.discordstats.domain.match.PlayerId
import com.eafc26.discordstats.domain.match.PlayerIdentity
import com.eafc26.discordstats.domain.match.PlayerMatchPerformance
import com.eafc26.discordstats.history.MatchHistoryOrder
import com.eafc26.discordstats.history.MatchHistoryQuery
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

class MatchHistoryServiceTest {
    private lateinit var repository: CanonicalMatchRepository
    private lateinit var service: MatchHistoryService

    @BeforeEach
    fun setUp() {
        repository = mock()
        service = MatchHistoryService(repository)
        whenever(repository.findAll()).thenReturn(emptyList())
    }

    @Test
    fun `findById delegates directly to canonical repository`() {
        val expected = canonical("match-1", "2026-07-01T10:00:00Z")
        whenever(repository.findById(expected.matchId)).thenReturn(expected)

        assertThat(service.findById(expected.matchId)).isSameAs(expected)
        verify(repository).findById(expected.matchId)
    }

    @Test
    fun `default list orders newest first with stable ID tie break`() {
        val old = canonical("old", "2026-07-01T10:00:00Z")
        val sameTimeB = canonical("b", "2026-07-03T10:00:00Z")
        val sameTimeA = canonical("a", "2026-07-03T10:00:00Z")
        whenever(repository.findAll()).thenReturn(listOf(old, sameTimeB, sameTimeA))

        assertThat(service.list().map { it.matchId.value }).containsExactly("a", "b", "old")
    }

    @Test
    fun `list supports chronological oldest-first order`() {
        val recent = canonical("recent", "2026-07-03T10:00:00Z")
        val old = canonical("old", "2026-07-01T10:00:00Z")
        whenever(repository.findAll()).thenReturn(listOf(recent, old))

        assertThat(service.list(MatchHistoryQuery(order = MatchHistoryOrder.OLDEST_FIRST)))
            .containsExactly(old, recent)
    }

    @Test
    fun `period uses inclusive start and exclusive end`() {
        val before = canonical("before", "2026-06-30T23:59:59Z")
        val start = canonical("start", "2026-07-01T00:00:00Z")
        val inside = canonical("inside", "2026-07-15T00:00:00Z")
        val end = canonical("end", "2026-08-01T00:00:00Z")
        whenever(repository.findAll()).thenReturn(listOf(before, start, inside, end))

        val result = service.list(
            MatchHistoryQuery(
                fromInclusive = Instant.parse("2026-07-01T00:00:00Z"),
                untilExclusive = Instant.parse("2026-08-01T00:00:00Z"),
            )
        )

        assertThat(result).containsExactly(inside, start)
    }

    @Test
    fun `list filters by normalized competition`() {
        val league = canonical("league", "2026-07-03T10:00:00Z", CompetitionType.LEAGUE)
        val playoff = canonical("playoff", "2026-07-02T10:00:00Z", CompetitionType.PLAYOFF)
        val unknown = canonical("unknown", "2026-07-01T10:00:00Z", null)
        whenever(repository.findAll()).thenReturn(listOf(league, playoff, unknown))

        assertThat(service.list(MatchHistoryQuery(competition = CompetitionType.PLAYOFF)))
            .containsExactly(playoff)
    }

    @Test
    fun `list can find matches involving a canonical player ID`() {
        val target = PlayerId("target-player")
        val matching = canonical(
            "matching",
            "2026-07-03T10:00:00Z",
            players = listOf(target),
        )
        val other = canonical(
            "other",
            "2026-07-02T10:00:00Z",
            players = listOf(PlayerId("someone-else")),
        )
        whenever(repository.findAll()).thenReturn(listOf(matching, other))

        assertThat(service.list(MatchHistoryQuery(playerId = target))).containsExactly(matching)
    }

    @Test
    fun `latest applies limit after canonical ordering`() {
        val one = canonical("one", "2026-07-01T10:00:00Z")
        val three = canonical("three", "2026-07-03T10:00:00Z")
        val two = canonical("two", "2026-07-02T10:00:00Z")
        whenever(repository.findAll()).thenReturn(listOf(one, three, two))

        assertThat(service.latest(2)).containsExactly(three, two)
    }

    @Test
    fun `metadata delegates without interpretation`() {
        val metadata = CanonicalRepositoryMetadata(
            matchCount = 2,
            oldestMatchAt = Instant.parse("2026-07-01T10:00:00Z"),
            newestMatchAt = Instant.parse("2026-07-02T10:00:00Z"),
            lastGeneratedAt = Instant.parse("2026-07-02T10:01:00Z"),
            schemaVersions = emptySet(),
            engineVersions = emptySet(),
        )
        whenever(repository.metadata()).thenReturn(metadata)

        assertThat(service.metadata()).isSameAs(metadata)
        verify(repository).metadata()
    }

    @Test
    fun `query rejects invalid period and limit`() {
        assertThatThrownBy {
            MatchHistoryQuery(
                fromInclusive = Instant.parse("2026-08-01T00:00:00Z"),
                untilExclusive = Instant.parse("2026-07-01T00:00:00Z"),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)

        assertThatThrownBy { MatchHistoryQuery(limit = 0) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun canonical(
        id: String,
        playedAt: String,
        competition: CompetitionType? = CompetitionType.LEAGUE,
        players: List<PlayerId> = emptyList(),
    ): CanonicalMatch {
        val footballMatch = mock<FootballMatch>()
        val canonical = mock<CanonicalMatch>()
        val matchId = MatchId(id)
        whenever(footballMatch.id).thenReturn(matchId)
        whenever(footballMatch.playedAt).thenReturn(Instant.parse(playedAt))
        whenever(footballMatch.competition).thenReturn(competition)
        val participants = if (players.isEmpty()) {
            emptyList()
        } else {
            val performances = players.map { playerId ->
                val performance = mock<PlayerMatchPerformance>()
                whenever(performance.player).thenReturn(
                    PlayerIdentity(playerId, DisplayName(playerId.value), null)
                )
                performance
            }
            val participant = mock<ClubMatchPerformance>()
            whenever(participant.players).thenReturn(performances)
            listOf(participant)
        }
        whenever(footballMatch.participants).thenReturn(participants)
        whenever(canonical.footballMatch).thenReturn(footballMatch)
        whenever(canonical.matchId).thenReturn(matchId)
        return canonical
    }
}
