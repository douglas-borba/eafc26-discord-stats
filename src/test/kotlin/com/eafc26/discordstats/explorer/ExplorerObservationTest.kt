package com.eafc26.discordstats.explorer

import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.GameVersion
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

    @Test
    fun `keeps passive and controlled evidence isolated by game version`() {
        val clubId = ClubId("club")
        val matchId = MatchId("match")
        val passive = InMemoryExplorerObservationRepository()
        val controlled = InMemoryControlledObservationRepository()

        val fc26 = passive.save(
            ExplorerObservation(clubId, matchId, "player", "Frase", 2, gameVersion = GameVersion.FC26),
        )
        val fc27 = passive.save(
            ExplorerObservation(clubId, matchId, "player", "Frase", 7, gameVersion = GameVersion.FC27),
        )
        controlled.saveIfAbsent(
            ControlledObservation(
                clubId, matchId, "player", "Frase", 2, ObservationCompleteness.EXACT,
                0, 183, ControlledExperimentType.COUNT_MATCH, gameVersion = GameVersion.FC26,
            ),
        )
        controlled.saveIfAbsent(
            ControlledObservation(
                clubId, matchId, "player", "Frase", 7, ObservationCompleteness.EXACT,
                0, 183, ControlledExperimentType.COUNT_MATCH, gameVersion = GameVersion.FC27,
            ),
        )

        assertThat(passive.findForPlayerMatch(clubId, matchId, "player", GameVersion.FC26)).containsExactly(fc26)
        assertThat(passive.findForPlayerMatch(clubId, matchId, "player", GameVersion.FC27)).containsExactly(fc27)
        val candidate = listOf(ControlledCandidateIdentity("player", "Frase", 0, 183))
        assertThat(controlled.findForCandidates(clubId, candidate, 5, GameVersion.FC26))
            .allSatisfy { assertThat(it.gameVersion).isEqualTo(GameVersion.FC26) }
        assertThat(controlled.findForCandidates(clubId, candidate, 5, GameVersion.FC27))
            .allSatisfy { assertThat(it.gameVersion).isEqualTo(GameVersion.FC27) }
    }
}
