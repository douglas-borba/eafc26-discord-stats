package com.eafc26.discordstats.admin

import com.eafc26.discordstats.application.repository.CanonicalMatchRepository
import com.eafc26.discordstats.canonical.CanonicalMatch
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.MatchId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ReplayRecentMatchesRunnerTest {
    @Test
    fun `replay selection queries only the requested club and applies exclusions within it`() {
        val repository: CanonicalMatchRepository = mock()
        val clubA = ClubId("club-a")
        val clubB = ClubId("club-b")
        val excluded = canonical("excluded")
        val included = canonical("included")
        whenever(repository.findAll(clubA)).thenReturn(listOf(excluded, included))

        val selected = selectReplayMatches(repository, clubA, null, setOf("excluded"), 10)

        assertThat(selected).containsExactly(included)
        verify(repository).findAll(clubA)
        verify(repository, never()).findAll(clubB)
    }

    @Test
    fun `specific replay lookup uses club and match id together`() {
        val repository: CanonicalMatchRepository = mock()
        val clubA = ClubId("club-a")
        val match = canonical("same")
        whenever(repository.findById(clubA, MatchId("same"))).thenReturn(match)

        assertThat(selectReplayMatches(repository, clubA, "same", emptySet(), 10)).containsExactly(match)
        verify(repository).findById(clubA, MatchId("same"))
    }

    private fun canonical(matchId: String): CanonicalMatch = mock<CanonicalMatch>().also {
        whenever(it.matchId).thenReturn(MatchId(matchId))
    }
}
