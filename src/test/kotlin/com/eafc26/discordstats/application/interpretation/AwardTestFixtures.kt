package com.eafc26.discordstats.application.interpretation

import com.eafc26.discordstats.domain.interpretation.DecisionEvidence
import com.eafc26.discordstats.domain.interpretation.EligibilityInterpretation
import com.eafc26.discordstats.domain.interpretation.EligibilityReason
import com.eafc26.discordstats.domain.interpretation.EligibilityStatus
import com.eafc26.discordstats.domain.interpretation.PlayerEligibilityDecision
import com.eafc26.discordstats.domain.match.AttackingStats
import com.eafc26.discordstats.domain.match.DefendingStats
import com.eafc26.discordstats.domain.match.DisciplineStats
import com.eafc26.discordstats.domain.match.DisplayName
import com.eafc26.discordstats.domain.match.EaRecognition
import com.eafc26.discordstats.domain.match.MatchRating
import com.eafc26.discordstats.domain.match.GoalkeepingStats
import com.eafc26.discordstats.domain.match.Participation
import com.eafc26.discordstats.domain.match.PassingStats
import com.eafc26.discordstats.domain.match.PlayerId
import com.eafc26.discordstats.domain.match.PlayerIdentity
import com.eafc26.discordstats.domain.match.PlayerMatchPerformance
import com.eafc26.discordstats.domain.match.PlayerRole
import com.eafc26.discordstats.domain.match.SaveBreakdown
import java.math.BigDecimal

internal fun awardPlayer(
    id: String,
    rating: String? = "7.0",
    goals: Int? = 0,
    assists: Int? = 0,
    shots: Int? = 0,
    passesCompleted: Int? = 8,
    passesAttempted: Int? = 10,
    tacklesCompleted: Int? = 0,
    tacklesAttempted: Int? = 0,
    redCards: Int? = 0,
    eaMvp: Boolean? = false,
    role: PlayerRole = PlayerRole.Outfield(null),
    saves: Int? = null,
    goalsConceded: Int? = null,
    goodDirectionSaves: Int? = null,
    reflexSaves: Int? = null,
    parrySaves: Int? = null,
    crossSaves: Int? = null,
): PlayerMatchPerformance = PlayerMatchPerformance(
    player = PlayerIdentity(PlayerId(id), platformName = DisplayName(id), proName = null),
    role = role,
    participation = Participation(duration = null, status = null),
    rating = rating?.let { MatchRating(BigDecimal(it)) },
    attacking = AttackingStats(goals, assists, shots),
    passing = PassingStats(passesAttempted, passesCompleted),
    defending = DefendingStats(tacklesAttempted, tacklesCompleted),
    discipline = DisciplineStats(redCards),
    goalkeeping = if (role == PlayerRole.Goalkeeper) {
        GoalkeepingStats(
            saves = saves,
            goalsConceded = goalsConceded,
            cleanSheetAsGoalkeeper = null,
            cleanSheetAsAny = null,
            saveBreakdown = SaveBreakdown(
                goodDirection = goodDirectionSaves,
                reflex = reflexSaves,
                parry = parrySaves,
                punch = null,
                diving = null,
                crosses = crossSaves,
            ),
        )
    } else {
        null
    },
    eaRecognition = EaRecognition(eaMvp),
)

internal fun eligibility(
    players: Collection<PlayerMatchPerformance>,
    ineligible: Set<PlayerId> = emptySet(),
): EligibilityInterpretation = EligibilityInterpretation(
    decisions = players.map {
        val eligible = it.player.id !in ineligible
        PlayerEligibilityDecision(
            playerId = it.player.id,
            status = if (eligible) EligibilityStatus.ELIGIBLE else EligibilityStatus.INELIGIBLE,
            reason = if (eligible) {
                EligibilityReason.NO_VALID_TEAM_PLAYING_TIME_FALLBACK
            } else {
                EligibilityReason.PLAYED_BELOW_TEAM_THRESHOLD
            },
            evidence = listOf(
                DecisionEvidence.PlayingTime(it.player.id, null, null, 90, eligible)
            ),
        )
    },
    maximumValidDuration = null,
    rule = PlayerEligibilityEvaluator.RULE,
)
