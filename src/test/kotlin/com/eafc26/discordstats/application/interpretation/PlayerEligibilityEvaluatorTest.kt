package com.eafc26.discordstats.application.interpretation

import com.eafc26.discordstats.domain.interpretation.DecisionEvidence
import com.eafc26.discordstats.domain.interpretation.EligibilityReason
import com.eafc26.discordstats.domain.interpretation.EligibilityStatus
import com.eafc26.discordstats.domain.match.AttackingStats
import com.eafc26.discordstats.domain.match.DefendingStats
import com.eafc26.discordstats.domain.match.DisciplineStats
import com.eafc26.discordstats.domain.match.EaRecognition
import com.eafc26.discordstats.domain.match.Participation
import com.eafc26.discordstats.domain.match.ParticipationStatus
import com.eafc26.discordstats.domain.match.PassingStats
import com.eafc26.discordstats.domain.match.PlayerId
import com.eafc26.discordstats.domain.match.PlayerIdentity
import com.eafc26.discordstats.domain.match.PlayerMatchPerformance
import com.eafc26.discordstats.domain.match.PlayerRole
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration

class PlayerEligibilityEvaluatorTest {

    private val evaluator = PlayerEligibilityEvaluator()

    @Test
    fun `player at exact ninety percent threshold is eligible`() {
        val result = evaluator.evaluate(
            listOf(player("maximum", 5400), player("boundary", 4860))
        )

        assertThat(result.eligiblePlayerIds)
            .containsExactlyInAnyOrder(PlayerId("maximum"), PlayerId("boundary"))
        assertThat(result.decisions.single { it.playerId == PlayerId("boundary") }.reason)
            .isEqualTo(EligibilityReason.PLAYED_AT_LEAST_TEAM_THRESHOLD)
    }

    @Test
    fun `player immediately below ninety percent threshold is ineligible`() {
        val result = evaluator.evaluate(
            listOf(player("maximum", 5400), player("below", 4859))
        )
        val below = result.decisions.single { it.playerId == PlayerId("below") }

        assertThat(below.status).isEqualTo(EligibilityStatus.INELIGIBLE)
        assertThat(below.reason).isEqualTo(EligibilityReason.PLAYED_BELOW_TEAM_THRESHOLD)
    }

    @Test
    fun `missing and zero playing time are invalid when team has valid duration`() {
        val result = evaluator.evaluate(
            listOf(player("maximum", 5400), player("missing", null), player("zero", 0))
        )

        assertThat(result.eligiblePlayerIds).containsExactly(PlayerId("maximum"))
        assertThat(result.decisions.filter { it.status == EligibilityStatus.INELIGIBLE })
            .allMatch { it.reason == EligibilityReason.INVALID_PLAYING_TIME }
    }

    @Test
    fun `all players are eligible when the team has no valid positive duration`() {
        val result = evaluator.evaluate(
            listOf(player("missing", null), player("zero", 0))
        )

        assertThat(result.maximumValidDuration).isNull()
        assertThat(result.eligiblePlayerIds)
            .containsExactlyInAnyOrder(PlayerId("missing"), PlayerId("zero"))
        assertThat(result.decisions)
            .allMatch { it.reason == EligibilityReason.NO_VALID_TEAM_PLAYING_TIME_FALLBACK }
    }

    @Test
    fun `status is not used for eligibility decisions`() {
        val completed = player("completed", 5400, ParticipationStatus.COMPLETED)
        val disconnected = player("disconnected", 5400, ParticipationStatus.DISCONNECTED)

        val result = evaluator.evaluate(listOf(completed, disconnected))

        assertThat(result.eligiblePlayerIds)
            .containsExactlyInAnyOrder(PlayerId("completed"), PlayerId("disconnected"))
    }

    @Test
    fun `playing time evidence records values threshold and result`() {
        val result = evaluator.evaluate(
            listOf(player("maximum", 5400), player("below", 4800))
        )
        val evidence = result.decisions
            .single { it.playerId == PlayerId("below") }
            .evidence
            .single() as DecisionEvidence.PlayingTime

        assertThat(evidence.playerSeconds).isEqualTo(4800)
        assertThat(evidence.maximumTeamSeconds).isEqualTo(5400)
        assertThat(evidence.requiredPercent).isEqualTo(90)
        assertThat(evidence.passed).isFalse()
    }

    @Test
    fun `empty team produces an empty valid interpretation`() {
        val result = evaluator.evaluate(emptyList())

        assertThat(result.decisions).isEmpty()
        assertThat(result.eligiblePlayerIds).isEmpty()
        assertThat(result.maximumValidDuration).isNull()
    }

    private fun player(
        id: String,
        seconds: Long?,
        status: ParticipationStatus? = null,
    ): PlayerMatchPerformance = PlayerMatchPerformance(
        player = PlayerIdentity(PlayerId(id), platformName = null, proName = null),
        role = PlayerRole.Outfield(position = null),
        participation = Participation(seconds?.let(Duration::ofSeconds), status),
        rating = null,
        attacking = AttackingStats(null, null, null),
        passing = PassingStats(null, null),
        defending = DefendingStats(null, null),
        discipline = DisciplineStats(null),
        goalkeeping = null,
        eaRecognition = EaRecognition(null),
    )
}
