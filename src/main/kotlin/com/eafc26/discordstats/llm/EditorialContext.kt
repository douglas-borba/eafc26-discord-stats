package com.eafc26.discordstats.llm

import com.eafc26.discordstats.domain.interpretation.MatchOutcome

data class EditorialContext(
    val match: MatchContext,
    val recentForm: RecentFormContext?,
)

data class MatchContext(
    val ourClub: String,
    val opponent: String,
    val ourScore: Int,
    val opponentScore: Int,
    val outcome: MatchOutcome,
    val date: String,
    val mvp: PlayerHighlight?,
    val worstPerformer: PlayerHighlight?,
    val xerife: PlayerHighlight?,
    val goalkeeper: GoalkeeperHighlight?,
    val goals: List<ScorerContext>,
    val assists: List<AssistContext>,
    val teamAverageRating: String?,
    val notableEvents: List<String>,
)

data class PlayerHighlight(
    val name: String,
    val rating: String?,
    val detail: String?,
)

data class GoalkeeperHighlight(
    val name: String,
    val saves: Int,
    val goalsConceded: Int,
    val archetype: String,
)

data class ScorerContext(val name: String, val goals: Int)
data class AssistContext(val name: String, val assists: Int)

data class RecentFormContext(
    val results: List<RecentMatchResult>,
    val wins: Int,
    val draws: Int,
    val losses: Int,
    val goalsScored: Int,
    val goalsConceded: Int,
    val streak: String,
)

data class RecentMatchResult(
    val opponent: String,
    val ourScore: Int,
    val opponentScore: Int,
    val outcome: MatchOutcome,
)
