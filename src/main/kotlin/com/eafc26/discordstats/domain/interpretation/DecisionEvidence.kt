package com.eafc26.discordstats.domain.interpretation

import com.eafc26.discordstats.domain.match.PlayerId
import com.eafc26.discordstats.domain.match.ReportedMatchResult
import java.math.BigDecimal

sealed interface DecisionEvidence {
    data class PlayerPopulation(
        val totalPlayers: Int,
        val statisticallyEligiblePlayers: Int,
        val eligibleOutfieldPlayers: Int,
    ) : DecisionEvidence

    data class Scoreboard(
        val ourScore: Int?,
        val opponentScore: Int?,
    ) : DecisionEvidence

    data class ReportedResult(
        val value: ReportedMatchResult?,
    ) : DecisionEvidence

    data class PlayingTime(
        val playerId: PlayerId,
        val playerSeconds: Long?,
        val maximumTeamSeconds: Long?,
        val requiredPercent: Int,
        val passed: Boolean,
    ) : DecisionEvidence

    data class AwardCandidate(
        val playerId: PlayerId,
        val statisticallyEligible: Boolean,
        val outfield: Boolean,
        val excludedByAward: AwardType?,
    ) : DecisionEvidence

    data class Rating(
        val playerId: PlayerId,
        val value: BigDecimal?,
        val minimumRequired: BigDecimal?,
    ) : DecisionEvidence

    data class AttackingContribution(
        val playerId: PlayerId,
        val goals: Int?,
        val assists: Int?,
        val shots: Int?,
    ) : DecisionEvidence

    data class PassingPerformance(
        val playerId: PlayerId,
        val completed: Int?,
        val attempted: Int?,
    ) : DecisionEvidence

    data class DefensivePerformance(
        val playerId: PlayerId,
        val tacklesCompleted: Int?,
        val tacklesAttempted: Int?,
        val defensiveImpactScore: BigDecimal?,
    ) : DecisionEvidence

    data class Discipline(
        val playerId: PlayerId,
        val redCards: Int?,
    ) : DecisionEvidence

    data class EaRecognition(
        val playerId: PlayerId,
        val manOfTheMatch: Boolean?,
    ) : DecisionEvidence

    data class TeamPassingPerformance(
        val completed: Int?,
        val attempted: Int?,
        val accuracyPercent: Int?,
    ) : DecisionEvidence

    data class GoalkeepingPerformance(
        val playerId: PlayerId,
        val saves: Int?,
        val goalsConceded: Int?,
        val rating: BigDecimal?,
        val goodDirectionSaves: Int?,
        val reflexSaves: Int?,
        val parrySaves: Int?,
        val crossSaves: Int?,
    ) : DecisionEvidence
}
