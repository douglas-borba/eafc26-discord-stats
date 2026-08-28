package com.eafc26.discordstats.service

import com.eafc26.discordstats.canonical.CanonicalMatch
import com.eafc26.discordstats.domain.interpretation.AwardDecision
import com.eafc26.discordstats.domain.interpretation.AwardType
import com.eafc26.discordstats.domain.interpretation.MatchAwards
import com.eafc26.discordstats.domain.interpretation.MatchInterpretation
import com.eafc26.discordstats.domain.interpretation.MatchOutcome
import com.eafc26.discordstats.domain.interpretation.ResultDecision
import com.eafc26.discordstats.domain.match.AttackingStats
import com.eafc26.discordstats.domain.match.AdvancedPlayerStats
import com.eafc26.discordstats.domain.match.AdvancedStatsCoverage
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.ClubIdentity
import com.eafc26.discordstats.domain.match.ClubMatchPerformance
import com.eafc26.discordstats.domain.match.MatchCompletion
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
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
        whenever(history.list(OUR_CLUB)).thenReturn(emptyList())

        assertThat(service.listPlayers(OUR_CLUB)).isEmpty()
    }

    @Test
    fun `complete profile list is empty when history is empty and reads history once`() {
        whenever(history.list(OUR_CLUB)).thenReturn(emptyList())

        assertThat(service.listProfiles(OUR_CLUB)).isEmpty()

        verify(history, times(1)).list(OUR_CLUB)
        verifyNoMoreInteractions(history)
    }

    @Test
    fun `unknown player has no profile`() {
        whenever(history.list(OUR_CLUB, MatchHistoryQuery(playerId = PlayerId("missing")))).thenReturn(emptyList())

        assertThat(service.findById(OUR_CLUB, PlayerId("missing"))).isNull()
        verify(history).list(OUR_CLUB, MatchHistoryQuery(playerId = PlayerId("missing")))
    }

    @Test
    fun `aggregates canonical results ratings production awards and discipline`() {
        val playerId = PlayerId("player-1")
        val matches = listOf(
            canonical("m3", "2026-07-03T10:00:00Z", playerId, "Current Name", MatchOutcome.WIN, "8.0", 2, 1, 0, setOf(AwardType.CRAQUE)),
            canonical("m2", "2026-07-02T10:00:00Z", playerId, "Old Name", MatchOutcome.DRAW, "6.0", 0, 2, 1, setOf(AwardType.BAGRE)),
            canonical("m1", "2026-07-01T10:00:00Z", playerId, "Old Name", MatchOutcome.LOSS, null, 1, 0, 0, setOf(AwardType.XERIFE)),
        )
        whenever(history.list(OUR_CLUB)).thenReturn(matches)
        whenever(history.list(OUR_CLUB, MatchHistoryQuery(playerId = playerId))).thenReturn(matches)

        val profile = service.listProfiles(OUR_CLUB).single()
        val detail = service.findById(OUR_CLUB, playerId)

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
        assertThat(profile).isEqualTo(detail)
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
        whenever(history.list(OUR_CLUB, MatchHistoryQuery(playerId = playerId))).thenReturn(matches)

        val profile = service.findById(OUR_CLUB, playerId, recentMatchLimit = 3)!!

        assertThat(profile.matchCount).isEqualTo(6)
        assertThat(profile.recentMatches.map { it.matchId.value }).containsExactly("m6", "m5", "m4")
    }

    @Test
    fun `player index includes perspective players once per appearance`() {
        val first = canonical("new", "2026-07-03T10:00:00Z", PlayerId("one"), "Ana", MatchOutcome.WIN, "8", 0, 0, 0, emptySet())
        val second = canonical("old", "2026-07-02T10:00:00Z", PlayerId("one"), "Ana Antiga", MatchOutcome.LOSS, "7", 0, 0, 0, emptySet())
        val third = canonical("other", "2026-07-01T10:00:00Z", PlayerId("two"), "Bruno", MatchOutcome.DRAW, "6", 0, 0, 0, emptySet())
        whenever(history.list(OUR_CLUB)).thenReturn(listOf(first, second, third))

        val result = service.listPlayers(OUR_CLUB)

        assertThat(result.map { it.playerId.value }).containsExactly("one", "two")
        assertThat(result.first().displayName).isEqualTo("Ana")
        assertThat(result.first().matchCount).isEqualTo(2)
    }

    @Test
    fun `complete profile list uses one shared history snapshot regardless of player count`() {
        val matches = listOf(
            canonical("m4", "2026-07-04T10:00:00Z", PlayerId("one"), "Ana", MatchOutcome.WIN, "8", 1, 0, 0, emptySet()),
            canonical("m3", "2026-07-03T10:00:00Z", PlayerId("two"), "Bruno", MatchOutcome.DRAW, "7", 0, 1, 0, emptySet()),
            canonical("m2", "2026-07-02T10:00:00Z", PlayerId("three"), "Carla", MatchOutcome.LOSS, null, 0, 0, 1, emptySet()),
            canonical("m1", "2026-07-01T10:00:00Z", PlayerId("one"), "Ana antiga", MatchOutcome.WIN, "9", 2, 0, 0, emptySet()),
        )
        whenever(history.list(OUR_CLUB)).thenReturn(matches)

        val profiles = service.listProfiles(OUR_CLUB)

        assertThat(profiles.map { it.playerId.value }).containsExactly("one", "two", "three")
        assertThat(profiles.first().matchCount).isEqualTo(2)
        assertThat(profiles.first().goals).isEqualTo(3)
        verify(history, times(1)).list(OUR_CLUB)
        verifyNoMoreInteractions(history)
    }

    @Test
    fun `DNF is excluded while unknown completion remains eligible in the shared snapshot`() {
        val unknown = canonical(
            "unknown", "2026-07-03T10:00:00Z", PlayerId("unknown"), "Disponível", MatchOutcome.WIN,
            "7", 0, 0, 0, emptySet(), completion = MatchCompletion.UNKNOWN,
        )
        val dnf = canonical(
            "dnf", "2026-07-02T10:00:00Z", PlayerId("dnf"), "Ausente", MatchOutcome.LOSS,
            "8", 1, 0, 0, emptySet(), completion = MatchCompletion.dnf(OUR_CLUB),
        )
        whenever(history.list(OUR_CLUB)).thenReturn(listOf(unknown, dnf))

        val profiles = service.listProfiles(OUR_CLUB)

        assertThat(profiles).singleElement().extracting { it.playerId.value }.isEqualTo("unknown")
        verify(history, times(1)).list(OUR_CLUB)
        verifyNoMoreInteractions(history)
    }

    @Test
    fun `latest pro name takes precedence over the platform name`() {
        val playerId = PlayerId("player-1")
        val latest = canonical(
            "latest", "2026-07-03T10:00:00Z", playerId, "Platform name", MatchOutcome.WIN,
            "7", 0, 0, 0, emptySet(), proName = "Pro name",
        )
        whenever(history.list(OUR_CLUB)).thenReturn(listOf(latest))

        assertThat(service.listProfiles(OUR_CLUB)).singleElement()
            .extracting { it.displayName }
            .isEqualTo("Pro name")
    }

    @Test
    fun `same player ID and name in two clubs never mix profile statistics`() {
        val otherClub = ClubId("other-club")
        val playerId = PlayerId("ronaldo")
        val ourMatch = canonical("ours", "2026-07-03T10:00:00Z", playerId, "Ronaldo", MatchOutcome.WIN, "9.0", 3, 1, 0, setOf(AwardType.CRAQUE))
        val theirMatch = canonical("theirs", "2026-07-02T10:00:00Z", playerId, "Ronaldo", MatchOutcome.LOSS, "5.0", 0, 0, 1, setOf(AwardType.BAGRE), otherClub)
        whenever(history.list(OUR_CLUB, MatchHistoryQuery(playerId = playerId))).thenReturn(listOf(ourMatch))
        whenever(history.list(otherClub, MatchHistoryQuery(playerId = playerId))).thenReturn(listOf(theirMatch))

        val ours = service.findById(OUR_CLUB, playerId)!!
        val theirs = service.findById(otherClub, playerId)!!

        assertThat(ours.goals).isEqualTo(3)
        assertThat(ours.craques).isEqualTo(1)
        assertThat(ours.bagres).isZero()
        assertThat(theirs.goals).isZero()
        assertThat(theirs.craques).isZero()
        assertThat(theirs.bagres).isEqualTo(1)
    }

    @Test
    fun `detail profile reads its history exactly once`() {
        val playerId = PlayerId("player-1")
        val query = MatchHistoryQuery(playerId = playerId)
        val match = canonical("m1", "2026-07-01T10:00:00Z", playerId, "Player", MatchOutcome.WIN, "7", 0, 0, 0, emptySet())
        whenever(history.list(OUR_CLUB, query)).thenReturn(listOf(match))

        assertThat(service.findById(OUR_CLUB, playerId)).isNotNull

        verify(history, times(1)).list(OUR_CLUB, query)
        verify(history, never()).list(OUR_CLUB)
        verifyNoMoreInteractions(history)
    }

    @Test
    fun `current form compares five latest eligible appearances against only prior history`() {
        val playerId = PlayerId("form-player")
        val matches = (10 downTo 1).map { day ->
            canonical(
                "m$day", "2026-07-${day.toString().padStart(2, '0')}T10:00:00Z", playerId, "Forma",
                MatchOutcome.WIN, if (day >= 6) "9.0" else "7.0", if (day >= 6) 2 else 0, 0, 0, emptySet(),
            )
        }
        whenever(history.list(OUR_CLUB, MatchHistoryQuery(playerId = playerId))).thenReturn(matches)

        val form = service.findById(OUR_CLUB, playerId)!!.xRay!!.currentForm

        assertThat(form.state.name).isEqualTo("COMPARED")
        assertThat(form.recent!!.averageRating).isEqualByComparingTo("9.00")
        assertThat(form.previous!!.averageRating).isEqualByComparingTo("7.00")
        assertThat(form.previous!!.goalsPerMatch).isEqualByComparingTo("0.00")
        assertThat(form.differences!!.goalsPerMatch).isEqualByComparingTo("2.00")
    }

    @Test
    fun `current form remains factual without a trend below five eligible appearances`() {
        val playerId = PlayerId("forming-player")
        val matches = (4 downTo 1).map { day ->
            canonical("m$day", "2026-07-${day.toString().padStart(2, '0')}T10:00:00Z", playerId, "Início", MatchOutcome.WIN, "7", 0, 0, 0, emptySet())
        }
        whenever(history.list(OUR_CLUB, MatchHistoryQuery(playerId = playerId))).thenReturn(matches)

        val form = service.findById(OUR_CLUB, playerId)!!.xRay!!.currentForm

        assertThat(form.state.name).isEqualTo("FORMING")
        assertThat(form.recent).isNull()
        assertThat(form.differences).isNull()
    }

    @Test
    fun `five to nine eligible appearances show recent facts without a baseline claim`() {
        val playerId = PlayerId("recent-only-player")
        val matches = (7 downTo 1).map { day ->
            canonical("m$day", "2026-07-${day.toString().padStart(2, '0')}T10:00:00Z", playerId, "Recente", MatchOutcome.WIN, "7", 1, 0, 0, emptySet())
        }
        whenever(history.list(OUR_CLUB, MatchHistoryQuery(playerId = playerId))).thenReturn(matches)

        val form = service.findById(OUR_CLUB, playerId)!!.xRay!!.currentForm

        assertThat(form.state.name).isEqualTo("RECENT_ONLY")
        assertThat(form.recent!!.appearances).isEqualTo(5)
        assertThat(form.previous).isNull()
        assertThat(form.differences).isNull()
    }

    @Test
    fun `advanced facts are exposed only for explicitly covered appearances`() {
        val playerId = PlayerId("advanced-player")
        val full = canonical(
            "full", "2026-07-03T10:00:00Z", playerId, "Avançado", MatchOutcome.WIN, "8", 0, 0, 0, emptySet(),
            advancedCoverage = AdvancedStatsCoverage.FULL, dribblesCompleted = 4, beats = 2,
        )
        val old = canonical("old", "2026-07-02T10:00:00Z", playerId, "Avançado", MatchOutcome.WIN, "8", 0, 0, 0, emptySet())
        whenever(history.list(OUR_CLUB, MatchHistoryQuery(playerId = playerId))).thenReturn(listOf(full, old))

        val xRay = service.findById(OUR_CLUB, playerId)!!.xRay!!

        assertThat(xRay.advancedCoverage.coverage).isEqualTo(AdvancedStatsCoverage.PARTIAL)
        assertThat(xRay.advancedCoverage.fullAppearances).isEqualTo(1)
        assertThat(xRay.advancedCoverage.unavailableAppearances).isEqualTo(1)
        assertThat(xRay.oneOnOne!!.dribblesCompleted).isEqualTo(4)
        assertThat(xRay.oneOnOne!!.opponentsBeaten).isEqualTo(2)
    }

    @Test
    fun `fully covered appearances expose factual 1v1 totals for their complete scope`() {
        val playerId = PlayerId("fully-covered-player")
        val first = canonical(
            "full-1", "2026-07-03T10:00:00Z", playerId, "Coberto", MatchOutcome.WIN, "8", 0, 0, 0, emptySet(),
            advancedCoverage = AdvancedStatsCoverage.FULL, dribblesCompleted = 4, beats = 2,
        )
        val second = canonical(
            "full-2", "2026-07-02T10:00:00Z", playerId, "Coberto", MatchOutcome.WIN, "8", 0, 0, 0, emptySet(),
            advancedCoverage = AdvancedStatsCoverage.FULL, dribblesCompleted = 3, beats = 5,
        )
        whenever(history.list(OUR_CLUB, MatchHistoryQuery(playerId = playerId))).thenReturn(listOf(first, second))

        val xRay = service.findById(OUR_CLUB, playerId)!!.xRay!!

        assertThat(xRay.advancedCoverage.coverage).isEqualTo(AdvancedStatsCoverage.FULL)
        assertThat(xRay.advancedCoverage.fullAppearances).isEqualTo(2)
        assertThat(xRay.oneOnOne).isNotNull
        assertThat(xRay.oneOnOne!!.dribblesCompleted).isEqualTo(7)
        assertThat(xRay.oneOnOne!!.opponentsBeaten).isEqualTo(7)
    }

    @Test
    fun `covered advanced zero is preserved as factual zero and differs from unavailable coverage`() {
        val playerId = PlayerId("advanced-zero-player")
        val covered = canonical(
            "covered", "2026-07-03T10:00:00Z", playerId, "Cobertura", MatchOutcome.WIN, "8", 0, 0, 0, emptySet(),
            advancedCoverage = AdvancedStatsCoverage.FULL, dribblesCompleted = 0, beats = 0,
        )
        whenever(history.list(OUR_CLUB, MatchHistoryQuery(playerId = playerId))).thenReturn(listOf(covered))

        val coveredXRay = service.findById(OUR_CLUB, playerId)!!.xRay!!

        assertThat(coveredXRay.advancedCoverage.coverage).isEqualTo(AdvancedStatsCoverage.FULL)
        assertThat(coveredXRay.oneOnOne).isNotNull
        assertThat(coveredXRay.oneOnOne!!.dribblesCompleted).isZero()
        val unavailable = canonical("unavailable", "2026-07-02T10:00:00Z", playerId, "Cobertura", MatchOutcome.WIN, "8", 0, 0, 0, emptySet())
        whenever(history.list(OUR_CLUB, MatchHistoryQuery(playerId = playerId))).thenReturn(listOf(unavailable))

        val unavailableXRay = service.findById(OUR_CLUB, playerId)!!.xRay!!

        assertThat(unavailableXRay.advancedCoverage.coverage).isEqualTo(AdvancedStatsCoverage.UNAVAILABLE)
        assertThat(unavailableXRay.oneOnOne).isNull()
    }

    @Test
    fun `historical appearances without explicit coverage stay unavailable rather than zero`() {
        val playerId = PlayerId("historical-player")
        val match = canonical("old", "2026-07-02T10:00:00Z", playerId, "Histórico", MatchOutcome.WIN, "8", 0, 0, 0, emptySet())
        whenever(history.list(OUR_CLUB, MatchHistoryQuery(playerId = playerId))).thenReturn(listOf(match))

        val profile = service.findById(OUR_CLUB, playerId)!!
        val xRay = profile.xRay!!

        assertThat(xRay.advancedCoverage.coverage).isEqualTo(AdvancedStatsCoverage.UNAVAILABLE)
        assertThat(xRay.oneOnOne).isNull()
        assertThat(xRay.analysis.summary).contains("Histórico")
        assertThat(xRay.attack.goals).isZero()
        assertThat(xRay.creation.assists).isZero()
        assertThat(xRay.defense.tacklesAttempted).isGreaterThanOrEqualTo(0)
        assertThat(xRay.records.ratingTenMatches).isZero()
        assertThat(profile.matchCount).isEqualTo(1)
    }

    @Test
    fun `personal record ties choose the newest match deterministically`() {
        val playerId = PlayerId("record-player")
        val earlier = canonical("a", "2026-07-01T10:00:00Z", playerId, "Recordista", MatchOutcome.WIN, "8", 3, 0, 0, emptySet())
        val later = canonical("b", "2026-07-02T10:00:00Z", playerId, "Recordista", MatchOutcome.WIN, "8", 3, 0, 0, emptySet())
        whenever(history.list(OUR_CLUB, MatchHistoryQuery(playerId = playerId))).thenReturn(listOf(later, earlier))

        val record = service.findById(OUR_CLUB, playerId)!!.xRay!!.records.mostGoalsInMatch

        assertThat(record!!.matchId.value).isEqualTo("b")
        assertThat(record.value).isEqualTo(3)
    }

    @Test
    fun `passing opportunity requires volume and a material personal decline`() {
        val playerId = PlayerId("passing-player")
        val matches = (10 downTo 1).map { day ->
            canonical(
                "m$day", "2026-07-${day.toString().padStart(2, '0')}T10:00:00Z", playerId, "Passador",
                MatchOutcome.WIN, "7", 0, 0, 0, emptySet(),
                passAttempts = 30, passesCompleted = if (day >= 6) 15 else 27,
            )
        }
        whenever(history.list(OUR_CLUB, MatchHistoryQuery(playerId = playerId))).thenReturn(matches)

        val improvement = service.findById(OUR_CLUB, playerId)!!.xRay!!.analysis.improvement
        val opportunity = improvement.opportunity

        assertThat(opportunity!!.area.name).isEqualTo("PASSING")
        assertThat(opportunity.source.name).isEqualTo("RECENT_REGRESSION")
        assertThat(opportunity.message).contains("acertou")
        assertThat(opportunity.evidence.numerator).isEqualTo(75)
        assertThat(opportunity.evidence.denominator).isEqualTo(150)
        assertThat(improvement.state.name).isEqualTo("FOUND")
    }

    @Test
    fun `no improvement opportunity is inferred without enough evidence volume`() {
        val playerId = PlayerId("low-volume-player")
        val matches = (10 downTo 1).map { day ->
            canonical(
                "m$day", "2026-07-${day.toString().padStart(2, '0')}T10:00:00Z", playerId, "Amostra curta",
                MatchOutcome.WIN, "7", 0, 0, 0, emptySet(),
                passAttempts = 1, passesCompleted = if (day >= 6) 0 else 1, shots = 0,
            )
        }
        whenever(history.list(OUR_CLUB, MatchHistoryQuery(playerId = playerId))).thenReturn(matches)

        val improvement = service.findById(OUR_CLUB, playerId)!!.xRay!!.analysis.improvement

        assertThat(improvement.opportunity).isNull()
        assertThat(improvement.state.name).isEqualTo("INSUFFICIENT_EVIDENCE")
    }

    @Test
    fun `regression below the explicit threshold does not create an opportunity`() {
        val playerId = PlayerId("small-decline-player")
        val matches = (10 downTo 1).map { day ->
            canonical(
                "m$day", "2026-07-${day.toString().padStart(2, '0')}T10:00:00Z", playerId, "Variação pequena",
                MatchOutcome.WIN, "8", 0, 0, 0, emptySet(),
                passAttempts = 30, passesCompleted = if (day >= 6) 25 else 27, shots = 0,
            )
        }
        whenever(history.list(OUR_CLUB, MatchHistoryQuery(playerId = playerId))).thenReturn(matches)

        val improvement = service.findById(OUR_CLUB, playerId)!!.xRay!!.analysis.improvement

        assertThat(improvement.state.name).isEqualTo("INSUFFICIENT_EVIDENCE")
        assertThat(improvement.opportunity).isNull()
    }

    @Test
    fun `multiple regressions choose the highest priority when their materiality is equal`() {
        val playerId = PlayerId("multiple-regressions-player")
        val matches = (10 downTo 1).map { day ->
            canonical(
                "m$day", "2026-07-${day.toString().padStart(2, '0')}T10:00:00Z", playerId, "Critério estável",
                MatchOutcome.WIN, "8", 0, 0, 0, emptySet(),
                passAttempts = 30, passesCompleted = if (day >= 6) 24 else 27,
                tackleAttempts = 20, tacklesCompleted = if (day >= 6) 15 else 18,
            )
        }
        whenever(history.list(OUR_CLUB, MatchHistoryQuery(playerId = playerId))).thenReturn(matches)

        val opportunity = service.findById(OUR_CLUB, playerId)!!.xRay!!.analysis.improvement.opportunity

        assertThat(opportunity!!.area).isEqualTo(com.eafc26.discordstats.profile.PlayerImprovementArea.PASSING)
        assertThat(opportunity.evidence.delta).isEqualByComparingTo("-10.00")
    }

    @Test
    fun `structural low efficiency is considered after recent regression candidates`() {
        val playerId = PlayerId("structural-player")
        val matches = (5 downTo 1).map { day ->
            canonical(
                "m$day", "2026-07-${day.toString().padStart(2, '0')}T10:00:00Z", playerId, "Base factual",
                MatchOutcome.WIN, "8", 0, 0, 0, emptySet(),
                passAttempts = 20, passesCompleted = 10,
            )
        }
        whenever(history.list(OUR_CLUB, MatchHistoryQuery(playerId = playerId))).thenReturn(matches)

        val opportunity = service.findById(OUR_CLUB, playerId)!!.xRay!!.analysis.improvement.opportunity

        assertThat(opportunity!!.source.name).isEqualTo("STRUCTURAL_LOW_EFFICIENCY")
        assertThat(opportunity.area.name).isEqualTo("PASSING")
        assertThat(opportunity.evidence.numerator).isEqualTo(50)
        assertThat(opportunity.evidence.denominator).isEqualTo(100)
    }

    @Test
    fun `strong overall production can still expose a factual structural opportunity`() {
        val playerId = PlayerId("strong-with-opportunity")
        val matches = (10 downTo 1).map { day ->
            canonical(
                "m$day", "2026-07-${day.toString().padStart(2, '0')}T10:00:00Z", playerId, "Destaque com recorte",
                MatchOutcome.WIN, "9", 2, 1, 0, setOf(AwardType.CRAQUE),
                passAttempts = 30, passesCompleted = 10, shots = 3,
            )
        }
        whenever(history.list(OUR_CLUB, MatchHistoryQuery(playerId = playerId))).thenReturn(matches)

        val xRay = service.findById(OUR_CLUB, playerId)!!.xRay!!

        assertThat(xRay.analysis.strengths.first().category.name).isEqualTo("OFFENSIVE_PRODUCTION")
        assertThat(xRay.analysis.improvement.opportunity)
            .extracting { it!!.source.name to it.area.name }
            .isEqualTo("STRUCTURAL_LOW_EFFICIENCY" to "PASSING")
    }

    @Test
    fun `strengths are ranked deterministically and limited to a principal and secondary`() {
        val playerId = PlayerId("strength-player")
        val matches = (10 downTo 1).map { day ->
            canonical(
                "m$day", "2026-07-${day.toString().padStart(2, '0')}T10:00:00Z", playerId, "Destaque",
                MatchOutcome.WIN, "9", 2, 1, 0, emptySet(),
                passAttempts = 30, passesCompleted = 27, shots = 3,
            )
        }
        whenever(history.list(OUR_CLUB, MatchHistoryQuery(playerId = playerId))).thenReturn(matches)

        val strengths = service.findById(OUR_CLUB, playerId)!!.xRay!!.analysis.strengths

        assertThat(strengths).hasSize(2)
        assertThat(strengths.map { it.category.name }).containsExactly("OFFENSIVE_PRODUCTION", "FINISHING")
        assertThat(strengths.first().evidence.numerator).isEqualTo(30)
        assertThat(strengths.first().evidence.appearances).isEqualTo(10)
    }

    @Test
    fun `a secondary strength remains optional when no other rule has enough evidence`() {
        val playerId = PlayerId("one-strength-player")
        val matches = (5 downTo 1).map { day ->
            canonical(
                "m$day", "2026-07-${day.toString().padStart(2, '0')}T10:00:00Z", playerId, "Uma força",
                MatchOutcome.WIN, "8", 2, 0, 0, emptySet(),
                passAttempts = 0, passesCompleted = 0, tackleAttempts = 0, tacklesCompleted = 0, shots = 0,
            )
        }
        whenever(history.list(OUR_CLUB, MatchHistoryQuery(playerId = playerId))).thenReturn(matches)

        val strengths = service.findById(OUR_CLUB, playerId)!!.xRay!!.analysis.strengths

        assertThat(strengths).singleElement().extracting { it.category.name }.isEqualTo("OFFENSIVE_PRODUCTION")
    }

    @Test
    fun `trend is rising only after a material rating increase against prior history`() {
        val playerId = PlayerId("rising-player")
        val matches = (10 downTo 1).map { day ->
            canonical("m$day", "2026-07-${day.toString().padStart(2, '0')}T10:00:00Z", playerId, "Em alta", MatchOutcome.WIN, if (day >= 6) "9" else "8", 0, 0, 0, emptySet())
        }
        whenever(history.list(OUR_CLUB, MatchHistoryQuery(playerId = playerId))).thenReturn(matches)

        val trend = service.findById(OUR_CLUB, playerId)!!.xRay!!.trend

        assertThat(trend.status.name).isEqualTo("RISING")
        assertThat(trend.ratingDelta).isEqualByComparingTo("1.00")
        assertThat(trend.metrics.map { it.type.name }).contains("RATING", "DIRECT_CONTRIBUTIONS_PER_MATCH")
    }

    @Test
    fun `trend is stable when the rating change is below its material threshold`() {
        val playerId = PlayerId("stable-player")
        val matches = (10 downTo 1).map { day ->
            canonical("m$day", "2026-07-${day.toString().padStart(2, '0')}T10:00:00Z", playerId, "Estável", MatchOutcome.WIN, if (day >= 6) "8.1" else "8.0", 0, 0, 0, emptySet())
        }
        whenever(history.list(OUR_CLUB, MatchHistoryQuery(playerId = playerId))).thenReturn(matches)

        assertThat(service.findById(OUR_CLUB, playerId)!!.xRay!!.trend.status.name).isEqualTo("STABLE")
    }

    @Test
    fun `trend is falling after a material rating decline`() {
        val playerId = PlayerId("falling-player")
        val matches = (10 downTo 1).map { day ->
            canonical("m$day", "2026-07-${day.toString().padStart(2, '0')}T10:00:00Z", playerId, "Em baixa", MatchOutcome.WIN, if (day >= 6) "7" else "8", 0, 0, 0, emptySet())
        }
        whenever(history.list(OUR_CLUB, MatchHistoryQuery(playerId = playerId))).thenReturn(matches)

        assertThat(service.findById(OUR_CLUB, playerId)!!.xRay!!.trend.status.name).isEqualTo("FALLING")
    }

    @Test
    fun `trend stays in formation below ten appearances including the five to nine range`() {
        val playerId = PlayerId("forming-trend-player")
        val matches = (7 downTo 1).map { day ->
            canonical("m$day", "2026-07-${day.toString().padStart(2, '0')}T10:00:00Z", playerId, "Em formação", MatchOutcome.WIN, "8", 0, 0, 0, emptySet())
        }
        whenever(history.list(OUR_CLUB, MatchHistoryQuery(playerId = playerId))).thenReturn(matches)

        assertThat(service.findById(OUR_CLUB, playerId)!!.xRay!!.trend.status.name).isEqualTo("FORMING")
    }

    @Test
    fun `trend stays in formation when the comparison has no sufficient rating evidence`() {
        val playerId = PlayerId("unrated-trend-player")
        val matches = (10 downTo 1).map { day ->
            canonical("m$day", "2026-07-${day.toString().padStart(2, '0')}T10:00:00Z", playerId, "Sem nota", MatchOutcome.WIN, null, 1, 0, 0, emptySet())
        }
        whenever(history.list(OUR_CLUB, MatchHistoryQuery(playerId = playerId))).thenReturn(matches)

        val trend = service.findById(OUR_CLUB, playerId)!!.xRay!!.trend

        assertThat(trend.status.name).isEqualTo("FORMING")
        assertThat(trend.ratingDelta).isNull()
    }

    @Test
    fun `consistency stays factual and requires five rated appearances for distribution`() {
        val playerId = PlayerId("consistency-player")
        val fourMatches = (4 downTo 1).map { day ->
            canonical("m$day", "2026-07-${day.toString().padStart(2, '0')}T10:00:00Z", playerId, "Amostra", MatchOutcome.WIN, "8", 0, 0, 0, emptySet())
        }
        whenever(history.list(OUR_CLUB, MatchHistoryQuery(playerId = playerId))).thenReturn(fourMatches)

        val smallSample = service.findById(OUR_CLUB, playerId)!!.xRay!!.consistency

        assertThat(smallSample.state.name).isEqualTo("INSUFFICIENT_SAMPLE")
        assertThat(smallSample.ratingStandardDeviation).isNull()
        val fiveMatches = fourMatches + canonical("m5", "2026-07-05T10:00:00Z", playerId, "Amostra", MatchOutcome.WIN, "10", 0, 0, 0, emptySet())
        whenever(history.list(OUR_CLUB, MatchHistoryQuery(playerId = playerId))).thenReturn(fiveMatches)

        val sufficientSample = service.findById(OUR_CLUB, playerId)!!.xRay!!.consistency

        assertThat(sufficientSample.state.name).isEqualTo("AVAILABLE")
        assertThat(sufficientSample.ratingsAtLeastEight).isEqualTo(5)
        assertThat(sufficientSample.ratingsAtLeastNine).isEqualTo(1)
        assertThat(sufficientSample.ratingTenMatches).isEqualTo(1)
        assertThat(sufficientSample.ratingsAtLeastEightRate).isEqualByComparingTo("100.00")
    }

    @Test
    fun `recognitions expose rates over eligible appearances without inventing zeros`() {
        val playerId = PlayerId("recognition-player")
        val matches = listOf(
            canonical("m3", "2026-07-03T10:00:00Z", playerId, "Reconhecido", MatchOutcome.WIN, "8", 0, 0, 0, setOf(AwardType.CRAQUE)),
            canonical("m2", "2026-07-02T10:00:00Z", playerId, "Reconhecido", MatchOutcome.WIN, "8", 0, 0, 0, emptySet()),
            canonical("m1", "2026-07-01T10:00:00Z", playerId, "Reconhecido", MatchOutcome.WIN, "8", 0, 0, 0, emptySet()),
        )
        whenever(history.list(OUR_CLUB, MatchHistoryQuery(playerId = playerId))).thenReturn(matches)

        val recognitions = service.findById(OUR_CLUB, playerId)!!.xRay!!.recognitions

        assertThat(recognitions.eligibleAppearances).isEqualTo(3)
        assertThat(recognitions.craqueRate).isEqualByComparingTo("33.33")
        assertThat(recognitions.bagreRate).isEqualByComparingTo("0.00")
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
        perspectiveClubId: ClubId = OUR_CLUB,
        completion: MatchCompletion = MatchCompletion.UNKNOWN,
        proName: String? = null,
        passAttempts: Int = 20,
        passesCompleted: Int = 18,
        tackleAttempts: Int = 4,
        tacklesCompleted: Int = 2,
        shots: Int = 3,
        advancedCoverage: AdvancedStatsCoverage = AdvancedStatsCoverage.UNAVAILABLE,
        dribblesCompleted: Int = 0,
        beats: Int = 0,
    ): CanonicalMatch {
        val player = PlayerMatchPerformance(
            player = PlayerIdentity(playerId, DisplayName(name), proName?.let(::DisplayName)),
            role = PlayerRole.Outfield(null),
            participation = Participation(Duration.ofMinutes(90), ParticipationStatus.COMPLETED),
            rating = rating?.let { MatchRating(BigDecimal(it)) },
            attacking = AttackingStats(goals, assists, shots),
            passing = PassingStats(passAttempts, passesCompleted),
            defending = DefendingStats(tackleAttempts, tacklesCompleted),
            discipline = DisciplineStats(redCards),
            goalkeeping = null,
            eaRecognition = EaRecognition(false),
            advanced = AdvancedPlayerStats(dribblesCompleted = dribblesCompleted, beats = beats),
            advancedCoverage = advancedCoverage,
        )
        val ours = ClubMatchPerformance(
            ClubIdentity(perspectiveClubId, ClubName("Our FC")),
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
            completion,
        )
        val result = mock<ResultDecision>()
        whenever(result.ourClub).thenReturn(perspectiveClubId)
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
        whenever(interpretation.perspectiveClubId).thenReturn(perspectiveClubId)
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
