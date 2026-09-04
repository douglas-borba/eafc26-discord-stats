package com.eafc26.discordstats.explorer

import com.eafc26.discordstats.application.repository.CanonicalMatchRepository
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.MatchId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.time.Instant

class ObservationPhraseReconciliationTest {
    private val clubId = ClubId("club-1")
    private val matchId = MatchId("match-1")
    private val playerId = "player-1"

    private fun service(repository: ExplorerObservationRepository) =
        AdvancedStatsExplorerService(mock<CanonicalMatchRepository>(), observationRepository = repository)

    private fun observation(
        phrase: String = "otimo emepenho ofensivo",
        count: Int = 2,
        completeness: ObservationCompleteness = ObservationCompleteness.AT_LEAST,
        note: String? = "recorded during live match",
        position: String? = "CAM",
        createdAt: Instant? = Instant.parse("2026-09-04T10:00:00Z"),
    ) = ExplorerObservation(clubId, matchId, playerId, phrase, count, completeness, note, position, createdAt)

    @Test
    fun `reconciliation changes only the explicitly confirmed exact phrase`() {
        val repository = InMemoryExplorerObservationRepository()
        val original = repository.save(observation())

        val result = service(repository).reconcileObservationPhrase(
            clubId, matchId, playerId, original.phrase, "Ótimo empenho ofensivo",
        )

        assertThat(result.status).isEqualTo(ObservationPhraseReconciliationStatus.SUCCESS)
        val reconciled = result.observation!!
        assertThat(reconciled.phrase).isEqualTo("Ótimo empenho ofensivo")
        assertThat(reconciled.observedCount).isEqualTo(original.observedCount)
        assertThat(reconciled.completeness).isEqualTo(original.completeness)
        assertThat(reconciled.note).isEqualTo(original.note)
        assertThat(reconciled.observedPositionContext).isEqualTo(original.observedPositionContext)
        assertThat(reconciled.createdAt).isEqualTo(original.createdAt)
        assertThat(repository.findForPlayerPhrase(clubId, playerId, original.phrase, 20)).isEmpty()
        assertThat(repository.findForPlayerPhrase(clubId, playerId, reconciled.phrase, 20)).containsExactly(reconciled)
    }

    @Test
    fun `reconciliation rejects a blank target without mutation`() {
        val repository = InMemoryExplorerObservationRepository()
        val original = repository.save(observation())

        val result = service(repository).reconcileObservationPhrase(clubId, matchId, playerId, original.phrase, "  ")

        assertThat(result.status).isEqualTo(ObservationPhraseReconciliationStatus.INVALID_TARGET)
        assertThat(repository.findForPlayerMatch(clubId, matchId, playerId)).containsExactly(original)
    }

    @Test
    fun `reconciliation is a safe no-op when source and target are identical`() {
        val repository = InMemoryExplorerObservationRepository()
        val original = repository.save(observation(phrase = "Ótima finta"))

        val result = service(repository).reconcileObservationPhrase(clubId, matchId, playerId, original.phrase, original.phrase)

        assertThat(result.status).isEqualTo(ObservationPhraseReconciliationStatus.NO_CHANGE)
        assertThat(repository.findForPlayerMatch(clubId, matchId, playerId)).containsExactly(original)
    }

    @Test
    fun `missing source reports a deterministic outcome`() {
        val result = service(InMemoryExplorerObservationRepository()).reconcileObservationPhrase(
            clubId, matchId, playerId, "missing", "Ótima finta",
        )

        assertThat(result.status).isEqualTo(ObservationPhraseReconciliationStatus.SOURCE_NOT_FOUND)
    }

    @Test
    fun `target collision blocks reconciliation without summing or changing either evidence row`() {
        val repository = InMemoryExplorerObservationRepository()
        val variant = repository.save(observation(phrase = "otima finta", count = 1))
        val target = repository.save(observation(phrase = "Ótima finta", count = 2, completeness = ObservationCompleteness.EXACT))

        val result = service(repository).reconcileObservationPhrase(clubId, matchId, playerId, variant.phrase, target.phrase)

        assertThat(result.status).isEqualTo(ObservationPhraseReconciliationStatus.TARGET_ALREADY_EXISTS)
        assertThat(result.existingTarget).isEqualTo(target)
        assertThat(repository.findForPlayerMatch(clubId, matchId, playerId)).containsExactly(variant, target)
        assertThat(repository.findForPlayerMatch(clubId, matchId, playerId).sumOf { it.observedCount }).isEqualTo(3)
    }
}
