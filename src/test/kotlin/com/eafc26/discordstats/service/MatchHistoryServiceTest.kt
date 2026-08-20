package com.eafc26.discordstats.service

import com.eafc26.discordstats.application.repository.CanonicalMatchRepository
import com.eafc26.discordstats.application.repository.CanonicalRepositoryMetadata
import com.eafc26.discordstats.canonical.CanonicalMatch
import com.eafc26.discordstats.domain.match.ClubMatchPerformance
import com.eafc26.discordstats.domain.match.ClubId
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
import org.mockito.kotlin.never
import org.mockito.kotlin.whenever
import java.time.Instant

class MatchHistoryServiceTest {
    private lateinit var repository: CanonicalMatchRepository
    private lateinit var service: MatchHistoryService

    @BeforeEach
    fun setUp() {
        repository = mock()
        service = MatchHistoryService(repository)
        whenever(repository.findAll(CLUB_ID)).thenReturn(emptyList())
    }

    @Test
    fun `findById delegates directly to canonical repository`() {
        val expected = canonical("match-1", "2026-07-01T10:00:00Z")
        whenever(repository.findById(CLUB_ID, expected.matchId)).thenReturn(expected)

        assertThat(service.findById(CLUB_ID, expected.matchId)).isSameAs(expected)
        verify(repository).findById(CLUB_ID, expected.matchId)
    }

    @Test
    fun `default list orders newest first with stable ID tie break`() {
        val old = canonical("old", "2026-07-01T10:00:00Z")
        val sameTimeB = canonical("b", "2026-07-03T10:00:00Z")
        val sameTimeA = canonical("a", "2026-07-03T10:00:00Z")
        whenever(repository.findAll(CLUB_ID)).thenReturn(listOf(old, sameTimeB, sameTimeA))

        assertThat(service.list(CLUB_ID).map { it.matchId.value }).containsExactly("a", "b", "old")
    }

    @Test
    fun `list supports chronological oldest-first order`() {
        val recent = canonical("recent", "2026-07-03T10:00:00Z")
        val old = canonical("old", "2026-07-01T10:00:00Z")
        whenever(repository.findAll(CLUB_ID)).thenReturn(listOf(recent, old))

        assertThat(service.list(CLUB_ID, MatchHistoryQuery(order = MatchHistoryOrder.OLDEST_FIRST)))
            .containsExactly(old, recent)
    }

    @Test
    fun `period uses inclusive start and exclusive end`() {
        val before = canonical("before", "2026-06-30T23:59:59Z")
        val start = canonical("start", "2026-07-01T00:00:00Z")
        val inside = canonical("inside", "2026-07-15T00:00:00Z")
        val end = canonical("end", "2026-08-01T00:00:00Z")
        whenever(repository.findAll(CLUB_ID)).thenReturn(listOf(before, start, inside, end))

        val result = service.list(
            CLUB_ID,
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
        whenever(repository.findAll(CLUB_ID)).thenReturn(listOf(league, playoff, unknown))

        assertThat(service.list(CLUB_ID, MatchHistoryQuery(competition = CompetitionType.PLAYOFF)))
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
        whenever(repository.findAll(CLUB_ID)).thenReturn(listOf(matching, other))

        assertThat(service.list(CLUB_ID, MatchHistoryQuery(playerId = target))).containsExactly(matching)
    }

    @Test
    fun `latest delegates the requested limit to the repository without loading all history`() {
        val three = canonical("three", "2026-07-03T10:00:00Z")
        val two = canonical("two", "2026-07-02T10:00:00Z")
        whenever(repository.findRecent(CLUB_ID, 2)).thenReturn(listOf(three, two))

        assertThat(service.latest(CLUB_ID, 2)).containsExactly(three, two)
        verify(repository).findRecent(CLUB_ID, 2)
        verify(repository, never()).findAll(CLUB_ID)
    }

    @Test
    fun `latestMatchIds delegates the bounded identity feed without loading canonical payloads`() {
        val ids = listOf(MatchId("three"), MatchId("two"))
        whenever(repository.findRecentMatchIds(CLUB_ID, 2)).thenReturn(ids)

        assertThat(service.latestMatchIds(CLUB_ID, 2)).containsExactlyElementsOf(ids)
        verify(repository).findRecentMatchIds(CLUB_ID, 2)
        verify(repository, never()).findRecent(CLUB_ID, 2)
        verify(repository, never()).findAll(CLUB_ID)
    }

    @Test
    fun `recent delegates the bounded overview feed without loading all history`() {
        val recent = canonical("recent", "2026-07-03T10:00:00Z")
        whenever(repository.findRecent(CLUB_ID, 10)).thenReturn(listOf(recent))

        assertThat(service.recent(CLUB_ID, 10)).containsExactly(recent)
        verify(repository).findRecent(CLUB_ID, 10)
        verify(repository, never()).findAll(CLUB_ID)
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
        whenever(repository.metadata(CLUB_ID)).thenReturn(metadata)

        assertThat(service.metadata(CLUB_ID)).isSameAs(metadata)
        verify(repository).metadata(CLUB_ID)
    }

    @Test
    fun `history and metadata remain isolated between clubs including a shared match ID`() {
        val otherClub = ClubId("other-club")
        val ours = canonical("shared-match", "2026-07-03T10:00:00Z")
        val theirs = canonical("shared-match", "2026-06-03T10:00:00Z")
        val ourMetadata = metadata(1, ours.footballMatch.playedAt)
        val theirMetadata = metadata(1, theirs.footballMatch.playedAt)
        whenever(repository.findAll(CLUB_ID)).thenReturn(listOf(ours))
        whenever(repository.findAll(otherClub)).thenReturn(listOf(theirs))
        whenever(repository.findById(CLUB_ID, MatchId("shared-match"))).thenReturn(ours)
        whenever(repository.findById(otherClub, MatchId("shared-match"))).thenReturn(theirs)
        whenever(repository.metadata(CLUB_ID)).thenReturn(ourMetadata)
        whenever(repository.metadata(otherClub)).thenReturn(theirMetadata)

        assertThat(service.list(CLUB_ID)).containsExactly(ours)
        assertThat(service.list(otherClub)).containsExactly(theirs)
        assertThat(service.findById(CLUB_ID, MatchId("shared-match"))).isSameAs(ours)
        assertThat(service.findById(otherClub, MatchId("shared-match"))).isSameAs(theirs)
        assertThat(service.metadata(CLUB_ID)).isSameAs(ourMetadata)
        assertThat(service.metadata(otherClub)).isSameAs(theirMetadata)
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

    private fun metadata(count: Int, playedAt: Instant) = CanonicalRepositoryMetadata(
        matchCount = count,
        oldestMatchAt = playedAt,
        newestMatchAt = playedAt,
        lastGeneratedAt = playedAt,
        schemaVersions = emptySet(),
        engineVersions = emptySet(),
    )

    private companion object {
        val CLUB_ID = ClubId("our-club")
    }
}
