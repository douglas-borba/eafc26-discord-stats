package com.eafc26.discordstats.diagnostics

import com.eafc26.discordstats.application.repository.CanonicalMatchRepository
import com.eafc26.discordstats.canonical.CanonicalMatch
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.MatchId
import com.eafc26.discordstats.service.DiscordMatchPublicationService
import com.eafc26.discordstats.service.MatchComparisonService
import com.eafc26.discordstats.service.MatchHistoryService
import com.eafc26.discordstats.service.OpponentHistoryService
import com.eafc26.discordstats.service.PlayerProfileService
import com.eafc26.discordstats.service.PublicationReconciliationService
import com.eafc26.discordstats.store.PublicationStateStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class CanonicalReadOriginAttributionTest {
    private val clubId = ClubId("club")

    @Test
    fun `history reads receive list and detail origins while preserving an explicit outer origin`() {
        val context = CanonicalReadOriginContext()
        val repository = mock<CanonicalMatchRepository>()
        val history = MatchHistoryService(repository, context)
        val observed = mutableListOf<CanonicalReadOrigin>()
        whenever(repository.findAll(clubId)).thenAnswer {
            observed += context.current()
            emptyList<CanonicalMatch>()
        }
        whenever(repository.findById(clubId, MatchId("match"))).thenAnswer {
            observed += context.current()
            null
        }

        history.list(clubId)
        history.findById(clubId, MatchId("match"))
        context.withOrigin(CanonicalReadOrigin.PLAYERS) { history.list(clubId) }

        assertThat(observed).containsExactly(
            CanonicalReadOrigin.HISTORY_LIST,
            CanonicalReadOrigin.HISTORY_DETAIL,
            CanonicalReadOrigin.PLAYERS,
        )
        assertThat(context.current()).isEqualTo(CanonicalReadOrigin.UNKNOWN)
    }

    @Test
    fun `player opponent and comparison services attribute their complete-history reads`() {
        val context = CanonicalReadOriginContext()
        val history = mock<MatchHistoryService>()
        val observed = mutableListOf<CanonicalReadOrigin>()
        whenever(history.list(clubId)).thenAnswer {
            observed += context.current()
            emptyList<CanonicalMatch>()
        }

        PlayerProfileService(history, context).listProfiles(clubId)
        OpponentHistoryService(history, context).listOpponents(clubId)
        MatchComparisonService(history, context).listOptions(clubId)

        assertThat(observed).containsExactly(
            CanonicalReadOrigin.PLAYERS,
            CanonicalReadOrigin.OPPONENTS,
            CanonicalReadOrigin.COMPARISON,
        )
        assertThat(context.current()).isEqualTo(CanonicalReadOrigin.UNKNOWN)
    }

    @Test
    fun `publication reconciliation attributes its full history read as admin`() {
        val context = CanonicalReadOriginContext()
        val repository = mock<CanonicalMatchRepository>()
        val store = mock<PublicationStateStore>()
        val publication = mock<DiscordMatchPublicationService>()
        val observed = mutableListOf<CanonicalReadOrigin>()
        whenever(repository.findAll(clubId)).thenAnswer {
            observed += context.current()
            emptyList<CanonicalMatch>()
        }
        whenever(store.loadRecords(clubId)).thenReturn(emptyMap())

        PublicationReconciliationService(repository, store, publication, context)
            .inspectLatestPublications(clubId)

        assertThat(observed).containsExactly(CanonicalReadOrigin.ADMIN)
        assertThat(context.current()).isEqualTo(CanonicalReadOrigin.UNKNOWN)
    }
}
