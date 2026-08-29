package com.eafc26.discordstats.explorer

import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.MatchId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ExplorerObservationTest {
    @Test
    fun `preserves exact phrase and defaults completeness to AT LEAST`() {
        val repository = InMemoryExplorerObservationRepository()
        val stored = repository.save(ExplorerObservation(ClubId("club"), MatchId("match"), "player", "Bom passe", 0))

        assertThat(stored.phrase).isEqualTo("Bom passe")
        assertThat(stored.completeness).isEqualTo(ObservationCompleteness.AT_LEAST)
        assertThat(repository.findForPlayerMatch(ClubId("club"), MatchId("match"), "player")).containsExactly(stored)
    }

    @Test
    fun `rejects negative observations without changing canonical data`() {
        assertThatThrownBy { ExplorerObservation(ClubId("club"), MatchId("match"), "player", "Bom passe", -1) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
