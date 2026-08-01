package com.eafc26.discordstats.application.interpretation

import com.eafc26.discordstats.domain.interpretation.DecisionEvidence
import com.eafc26.discordstats.domain.interpretation.EligibilityInterpretation
import com.eafc26.discordstats.domain.interpretation.EligibilityReason
import com.eafc26.discordstats.domain.interpretation.EligibilityStatus
import com.eafc26.discordstats.domain.interpretation.PlayerEligibilityDecision
import com.eafc26.discordstats.domain.interpretation.RuleId
import com.eafc26.discordstats.domain.interpretation.RuleReference
import com.eafc26.discordstats.domain.match.PlayerMatchPerformance

/**
 * Applies the current relative playing-time eligibility rule to normalized players.
 *
 * Rating and participation status intentionally do not affect eligibility.
 */
class PlayerEligibilityEvaluator {

    fun evaluate(players: Collection<PlayerMatchPerformance>): EligibilityInterpretation {
        val validDurations = players.mapNotNull { player ->
            player.participation.duration?.takeIf { !it.isZero && !it.isNegative }
        }

        if (validDurations.isEmpty()) {
            return EligibilityInterpretation(
                decisions = players.map { player ->
                    PlayerEligibilityDecision(
                        playerId = player.player.id,
                        status = EligibilityStatus.ELIGIBLE,
                        reason = EligibilityReason.NO_VALID_TEAM_PLAYING_TIME_FALLBACK,
                        evidence = listOf(
                            DecisionEvidence.PlayingTime(
                                playerId = player.player.id,
                                playerSeconds = player.participation.duration?.seconds,
                                maximumTeamSeconds = null,
                                requiredPercent = REQUIRED_PERCENT,
                                passed = true,
                            )
                        ),
                    )
                },
                maximumValidDuration = null,
                rule = RULE,
            )
        }

        val maximum = validDurations.max()
        val maximumSeconds = maximum.seconds
        val decisions = players.map { player ->
            val duration = player.participation.duration
            val seconds = duration?.seconds?.takeIf { it > 0 }
            val eligible = seconds != null &&
                seconds * PERCENT_BASE >= maximumSeconds * REQUIRED_PERCENT

            PlayerEligibilityDecision(
                playerId = player.player.id,
                status = if (eligible) EligibilityStatus.ELIGIBLE else EligibilityStatus.INELIGIBLE,
                reason = when {
                    seconds == null -> EligibilityReason.INVALID_PLAYING_TIME
                    eligible -> EligibilityReason.PLAYED_AT_LEAST_TEAM_THRESHOLD
                    else -> EligibilityReason.PLAYED_BELOW_TEAM_THRESHOLD
                },
                evidence = listOf(
                    DecisionEvidence.PlayingTime(
                        playerId = player.player.id,
                        playerSeconds = seconds,
                        maximumTeamSeconds = maximumSeconds,
                        requiredPercent = REQUIRED_PERCENT,
                        passed = eligible,
                    )
                ),
            )
        }

        return EligibilityInterpretation(
            decisions = decisions,
            maximumValidDuration = maximum,
            rule = RULE,
        )
    }

    companion object {
        const val REQUIRED_PERCENT = 90
        private const val PERCENT_BASE = 100L
        val RULE = RuleReference(RuleId("player.statistical-eligibility"), version = 1)
    }
}
