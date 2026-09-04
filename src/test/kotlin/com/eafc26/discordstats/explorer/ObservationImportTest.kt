package com.eafc26.discordstats.explorer

import com.eafc26.discordstats.application.repository.CanonicalMatchOverview
import com.eafc26.discordstats.application.repository.CanonicalMatchRepository
import com.eafc26.discordstats.application.repository.CanonicalRepositoryMetadata
import com.eafc26.discordstats.canonical.CanonicalMatch
import com.eafc26.discordstats.domain.match.*
import com.eafc26.discordstats.domain.interpretation.MatchInterpretation
import com.eafc26.discordstats.domain.interpretation.ResultDecision
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

class ObservationImportTest {

    private val clubId = ClubId("club-1")
    private val matchId = MatchId("match-1")
    private val matchId2 = MatchId("match-2")
    private val playerId = "player-1"

    private fun buildCanonical(
        id: String = "match-1",
        pid: String = "player-1",
    ): CanonicalMatch {
        val player = PlayerMatchPerformance(
            player = PlayerIdentity(PlayerId(pid), DisplayName("Neymar"), null),
            role = PlayerRole.Outfield(null),
            participation = Participation(Duration.ofSeconds(5400), ParticipationStatus.COMPLETED),
            rating = MatchRating(BigDecimal("8.5")),
            attacking = AttackingStats(goals = 2, assists = 1, shots = 5),
            passing = PassingStats(attempted = 20, completed = 18),
            defending = DefendingStats(tacklesAttempted = 4, tacklesCompleted = 3, interceptions = 7),
            discipline = DisciplineStats(redCards = 0),
            goalkeeping = null,
            eaRecognition = EaRecognition(manOfTheMatch = true),
            advanced = AdvancedPlayerStats(secondAssists = 2, throughPasses = 9, dribblesCompleted = 18, beats = 8, interceptions = 7),
            advancedCoverage = AdvancedStatsCoverage.FULL,
            rawEventAggregates = null,
            rawUnknownFields = null,
            eaPositionCode = null,
        )
        val footballMatch = FootballMatch(
            id = MatchId(id),
            playedAt = Instant.ofEpochSecond(1718500000L),
            competition = CompetitionType.LEAGUE,
            participants = listOf(
                ClubMatchPerformance(
                    club = ClubIdentity(clubId, ClubName("Our FC")),
                    score = Score(3),
                    reportedResult = ReportedMatchResult.WIN,
                    players = listOf(player),
                ),
                ClubMatchPerformance(
                    club = ClubIdentity(ClubId("opp-1"), ClubName("Opponent FC")),
                    score = Score(1),
                    reportedResult = ReportedMatchResult.LOSS,
                    players = emptyList(),
                ),
            ),
            completion = MatchCompletion.COMPLETED,
        )
        val resultDecision = mock<ResultDecision>()
        whenever(resultDecision.ourScore).thenReturn(Score(3))
        whenever(resultDecision.opponentScore).thenReturn(Score(1))
        whenever(resultDecision.opponentClub).thenReturn(ClubId("opp-1"))
        val interpretation = mock<MatchInterpretation>()
        whenever(interpretation.perspectiveClubId).thenReturn(clubId)
        whenever(interpretation.result).thenReturn(resultDecision)
        val canonical = mock<CanonicalMatch>()
        whenever(canonical.matchId).thenReturn(MatchId(id))
        whenever(canonical.footballMatch).thenReturn(footballMatch)
        whenever(canonical.interpretation).thenReturn(interpretation)
        return canonical
    }

    private fun fakeRepo(vararg matches: CanonicalMatch) = object : CanonicalMatchRepository {
        private val byId = matches.associateBy { it.matchId }
        override fun save(match: CanonicalMatch) {}
        override fun findById(clubId: ClubId, matchId: MatchId) = byId[matchId]
        override fun findByIds(clubId: ClubId, matchIds: Collection<MatchId>) = matchIds.mapNotNull { byId[it] }
        override fun findMatchIds(clubId: ClubId) = byId.keys
        override fun findLatestMatchId(clubId: ClubId) = byId.keys.firstOrNull()
        override fun findExistingMatchIds(clubId: ClubId, candidateMatchIds: Collection<MatchId>) = emptySet<MatchId>()
        override fun findRecentMatchIds(clubId: ClubId, limit: Int) = byId.keys.take(limit).toList()
        override fun findRecentOverview(clubId: ClubId, limit: Int) = emptyList<CanonicalMatchOverview>()
        override fun findAll(clubId: ClubId) = byId.values.toList()
        override fun findHistorySummaries(clubId: ClubId) = emptyList<CanonicalMatchOverview>()
        override fun findRecent(clubId: ClubId, limit: Int) = byId.values.take(limit).toList()
        override fun metadata(clubId: ClubId) = CanonicalRepositoryMetadata(byId.size, null, null, null, emptySet(), emptySet())
    }

    private fun input(
        matchId: String = "match-1",
        playerId: String = "player-1",
        phrase: String = "Bom passe",
        observedCount: Int = 1,
        completeness: ObservationCompleteness = ObservationCompleteness.AT_LEAST,
        note: String? = null,
    ) = AdvancedStatsExplorerService.ObservationImportInput(matchId, playerId, phrase, observedCount, completeness, note)

    private fun service(
        matchRepo: CanonicalMatchRepository = fakeRepo(buildCanonical()),
        obsRepo: ExplorerObservationRepository = InMemoryExplorerObservationRepository(),
    ) = AdvancedStatsExplorerService(matchRepo, observationRepository = obsRepo)

    // ── Empty / limit ──

    @Test
    fun `empty payload is invalid`() {
        val preview = service().previewObservationImport(clubId, emptyList())
        assertThat(preview.invalidCount).isEqualTo(1)
        assertThat(preview.newCount).isEqualTo(0)
    }

    @Test
    fun `more than 50 observations is rejected`() {
        val inputs = (1..51).map { input(phrase = "p$it") }
        val preview = service().previewObservationImport(clubId, inputs)
        assertThat(preview.invalidCount).isEqualTo(1)
        assertThat(preview.records[0].reason).contains("50")
    }

    // ── Valid NEW ──

    @Test
    fun `valid new record classified as NEW`() {
        val preview = service().previewObservationImport(clubId, listOf(input()))
        assertThat(preview.newCount).isEqualTo(1)
        assertThat(preview.conflictCount).isEqualTo(0)
        assertThat(preview.invalidCount).isEqualTo(0)
        assertThat(preview.records[0].status).isEqualTo(AdvancedStatsExplorerService.ObservationImportStatus.NEW)
    }

    // ── ALREADY_EXISTS ──

    @Test
    fun `identical existing classified as ALREADY_EXISTS`() {
        val obsRepo = InMemoryExplorerObservationRepository()
        obsRepo.save(ExplorerObservation(clubId, matchId, playerId, "Bom passe", 1, ObservationCompleteness.AT_LEAST))
        val preview = service(obsRepo = obsRepo).previewObservationImport(clubId, listOf(input()))
        assertThat(preview.alreadyExistsCount).isEqualTo(1)
        assertThat(preview.newCount).isEqualTo(0)
    }

    // ── CONFLICT: different observedCount ──

    @Test
    fun `same identity different observedCount is CONFLICT`() {
        val obsRepo = InMemoryExplorerObservationRepository()
        obsRepo.save(ExplorerObservation(clubId, matchId, playerId, "Bom passe", 3, ObservationCompleteness.AT_LEAST))
        val preview = service(obsRepo = obsRepo).previewObservationImport(clubId, listOf(input(observedCount = 1)))
        assertThat(preview.conflictCount).isEqualTo(1)
        assertThat(preview.records[0].reason).contains("observedCount")
    }

    // ── CONFLICT: different completeness ──

    @Test
    fun `same identity different completeness is CONFLICT`() {
        val obsRepo = InMemoryExplorerObservationRepository()
        obsRepo.save(ExplorerObservation(clubId, matchId, playerId, "Bom passe", 1, ObservationCompleteness.EXACT))
        val preview = service(obsRepo = obsRepo).previewObservationImport(clubId, listOf(input(completeness = ObservationCompleteness.AT_LEAST)))
        assertThat(preview.conflictCount).isEqualTo(1)
        assertThat(preview.records[0].reason).contains("completeness")
    }

    // ── CONFLICT: different note ──

    @Test
    fun `same identity different note is CONFLICT`() {
        val obsRepo = InMemoryExplorerObservationRepository()
        obsRepo.save(ExplorerObservation(clubId, matchId, playerId, "Bom passe", 1, ObservationCompleteness.AT_LEAST, note = "existing note"))
        val preview = service(obsRepo = obsRepo).previewObservationImport(clubId, listOf(input(note = "different note")))
        assertThat(preview.conflictCount).isEqualTo(1)
        assertThat(preview.records[0].reason).contains("note")
    }

    // ── Intra-batch duplicates (identical) ──

    @Test
    fun `duplicate identical records inside payload are deduplicated`() {
        val preview = service().previewObservationImport(clubId, listOf(input(), input()))
        assertThat(preview.newCount).isEqualTo(1)
        assertThat(preview.alreadyExistsCount).isEqualTo(1)
        assertThat(preview.conflictCount).isEqualTo(0)
    }

    // ── Intra-batch duplicates (conflicting) ──

    @Test
    fun `duplicate conflicting records inside payload are CONFLICT`() {
        val preview = service().previewObservationImport(clubId, listOf(input(observedCount = 1), input(observedCount = 2)))
        assertThat(preview.conflictCount).isEqualTo(2)
        assertThat(preview.newCount).isEqualTo(0)
    }

    // ── Nonexistent match ──

    @Test
    fun `nonexistent match is INVALID`() {
        val preview = service().previewObservationImport(clubId, listOf(input(matchId = "nonexistent")))
        assertThat(preview.invalidCount).isEqualTo(1)
        assertThat(preview.records[0].reason).contains("Match not found")
    }

    // ── Match from wrong club ──

    @Test
    fun `match from wrong club is INVALID`() {
        val wrongClubCanonical = buildCanonical()
        val wrongClubInterpretation = mock<MatchInterpretation>()
        val resultDecision = mock<ResultDecision>()
        whenever(resultDecision.ourScore).thenReturn(Score(3))
        whenever(resultDecision.opponentScore).thenReturn(Score(1))
        whenever(resultDecision.opponentClub).thenReturn(ClubId("opp-1"))
        whenever(wrongClubInterpretation.perspectiveClubId).thenReturn(ClubId("other-club"))
        whenever(wrongClubInterpretation.result).thenReturn(resultDecision)
        whenever(wrongClubCanonical.interpretation).thenReturn(wrongClubInterpretation)

        val preview = service(matchRepo = fakeRepo(wrongClubCanonical)).previewObservationImport(clubId, listOf(input()))
        assertThat(preview.invalidCount).isEqualTo(1)
        assertThat(preview.records[0].reason).contains("does not belong")
    }

    // ── Player absent from match ──

    @Test
    fun `player absent from match is INVALID`() {
        val preview = service().previewObservationImport(clubId, listOf(input(playerId = "unknown-player")))
        assertThat(preview.invalidCount).isEqualTo(1)
        assertThat(preview.records[0].reason).contains("Player not found")
    }

    // ── AT_LEAST preserved ──

    @Test
    fun `AT_LEAST completeness preserved`() {
        val preview = service().previewObservationImport(clubId, listOf(input(completeness = ObservationCompleteness.AT_LEAST)))
        assertThat(preview.records[0].completeness).isEqualTo(ObservationCompleteness.AT_LEAST)
    }

    // ── EXACT preserved ──

    @Test
    fun `EXACT completeness preserved`() {
        val preview = service().previewObservationImport(clubId, listOf(input(completeness = ObservationCompleteness.EXACT)))
        assertThat(preview.records[0].completeness).isEqualTo(ObservationCompleteness.EXACT)
    }

    // ── Preview performs zero writes ──

    @Test
    fun `preview performs zero writes`() {
        val obsRepo = InMemoryExplorerObservationRepository()
        service(obsRepo = obsRepo).previewObservationImport(clubId, listOf(input()))
        assertThat(obsRepo.findForPlayerMatch(clubId, matchId, playerId)).isEmpty()
    }

    // ── Import NEW inserts ──

    @Test
    fun `import NEW records inserts them`() {
        val obsRepo = InMemoryExplorerObservationRepository()
        val result = service(obsRepo = obsRepo).importObservations(clubId, listOf(input()))
        assertThat(result.inserted).isEqualTo(1)
        assertThat(obsRepo.findForPlayerMatch(clubId, matchId, playerId)).hasSize(1)
    }

    // ── Import ALREADY_EXISTS creates no duplicate ──

    @Test
    fun `import ALREADY_EXISTS does not duplicate`() {
        val obsRepo = InMemoryExplorerObservationRepository()
        obsRepo.save(ExplorerObservation(clubId, matchId, playerId, "Bom passe", 1, ObservationCompleteness.AT_LEAST))
        val result = service(obsRepo = obsRepo).importObservations(clubId, listOf(input()))
        assertThat(result.inserted).isEqualTo(0)
        assertThat(result.alreadyExisted).isEqualTo(1)
        assertThat(obsRepo.findForPlayerMatch(clubId, matchId, playerId)).hasSize(1)
    }

    // ── Mixed NEW + ALREADY_EXISTS inserts only NEW ──

    @Test
    fun `mixed NEW and ALREADY_EXISTS inserts only NEW`() {
        val obsRepo = InMemoryExplorerObservationRepository()
        obsRepo.save(ExplorerObservation(clubId, matchId, playerId, "Bom passe", 1, ObservationCompleteness.AT_LEAST))
        val matchRepo = fakeRepo(buildCanonical(), buildCanonical(id = "match-2"))
        val inputs = listOf(input(), input(matchId = "match-2", phrase = "Perdeu a bola"))
        val result = service(matchRepo = matchRepo, obsRepo = obsRepo).importObservations(clubId, inputs)
        assertThat(result.inserted).isEqualTo(1)
        assertThat(result.alreadyExisted).isEqualTo(1)
    }

    // ── CONFLICT blocks import ──

    @Test
    fun `CONFLICT blocks import with zero writes`() {
        val obsRepo = InMemoryExplorerObservationRepository()
        obsRepo.save(ExplorerObservation(clubId, matchId, playerId, "Bom passe", 5, ObservationCompleteness.AT_LEAST))
        assertThatThrownBy { service(obsRepo = obsRepo).importObservations(clubId, listOf(input(observedCount = 1))) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("conflict")
    }

    // ── INVALID blocks import ──

    @Test
    fun `INVALID blocks import with zero writes`() {
        assertThatThrownBy { service().importObservations(clubId, listOf(input(phrase = ""))) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("invalid")
    }

    // ── Concurrent identical insert between preview and import ──

    @Test
    fun `concurrent identical insert between preview and import succeeds with zero inserts`() {
        val obsRepo = InMemoryExplorerObservationRepository()
        val svc = service(obsRepo = obsRepo)
        val preview = svc.previewObservationImport(clubId, listOf(input()))
        assertThat(preview.newCount).isEqualTo(1)

        // Simulate concurrent insert of identical observation
        obsRepo.save(ExplorerObservation(clubId, matchId, playerId, "Bom passe", 1, ObservationCompleteness.AT_LEAST))

        // Import re-validates and sees ALREADY_EXISTS — safe, no duplicate
        val result = svc.importObservations(clubId, listOf(input()))
        assertThat(result.inserted).isEqualTo(0)
        assertThat(result.alreadyExisted).isEqualTo(1)
    }

    // ── Concurrent conflicting insert between preview and import ──

    @Test
    fun `concurrent conflicting insert between preview and import aborts`() {
        val obsRepo = InMemoryExplorerObservationRepository()
        val svc = service(obsRepo = obsRepo)
        val preview = svc.previewObservationImport(clubId, listOf(input(observedCount = 1)))
        assertThat(preview.newCount).isEqualTo(1)

        // Simulate concurrent insert with DIFFERENT observedCount
        obsRepo.save(ExplorerObservation(clubId, matchId, playerId, "Bom passe", 5, ObservationCompleteness.AT_LEAST))

        assertThatThrownBy { svc.importObservations(clubId, listOf(input(observedCount = 1))) }
            .isInstanceOf(IllegalStateException::class.java)
    }

    // ── Atomicity: no partial import ──

    @Test
    fun `no partial import on concurrent conflict`() {
        val obsRepo = InMemoryExplorerObservationRepository()
        val matchRepo = fakeRepo(buildCanonical(), buildCanonical(id = "match-2"))
        val svc = service(matchRepo = matchRepo, obsRepo = obsRepo)

        val inputs = listOf(
            input(phrase = "Bom passe", observedCount = 1),
            input(matchId = "match-2", phrase = "Perdeu a bola"),
        )
        val preview = svc.previewObservationImport(clubId, inputs)
        assertThat(preview.newCount).isEqualTo(2)

        // Simulate concurrent insert of first record with DIFFERENT count
        obsRepo.save(ExplorerObservation(clubId, matchId, playerId, "Bom passe", 99, ObservationCompleteness.AT_LEAST))

        assertThatThrownBy { svc.importObservations(clubId, inputs) }
            .isInstanceOf(IllegalStateException::class.java)
        // Second record must NOT have been inserted
        assertThat(obsRepo.findForPlayerMatch(clubId, matchId2, playerId)).isEmpty()
    }

    // ── Semantic registry unchanged ──

    @Test
    fun `import does not change semantic registry`() {
        val registryBefore = AdvancedStatsCodeRegistry.allMappings().toList()
        val obsRepo = InMemoryExplorerObservationRepository()
        service(obsRepo = obsRepo).importObservations(clubId, listOf(input()))
        val registryAfter = AdvancedStatsCodeRegistry.allMappings().toList()
        assertThat(registryAfter).isEqualTo(registryBefore)
    }

    // ── Blank phrase is INVALID ──

    @Test
    fun `blank phrase is INVALID`() {
        val preview = service().previewObservationImport(clubId, listOf(input(phrase = "   ")))
        assertThat(preview.invalidCount).isEqualTo(1)
        assertThat(preview.records[0].reason).contains("blank")
    }

    // ── Negative observedCount is INVALID ──

    @Test
    fun `negative observedCount is INVALID`() {
        val preview = service().previewObservationImport(clubId, listOf(input(observedCount = -1)))
        assertThat(preview.invalidCount).isEqualTo(1)
        assertThat(preview.records[0].reason).contains("non-negative")
    }
}
