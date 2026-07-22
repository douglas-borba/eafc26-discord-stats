package com.eafc26.discordstats.presentation

/**
 * Unified presentation model for match summaries.
 * 
 * This model is used by both:
 * - DiscordEmbedBuilder (for Discord webhook)
 * - Match Card renderer (for visual card)
 * 
 * Business logic remains in the existing selectors.
 * This class only aggregates their results into a presentation-ready format.
 *
 * The presentation is built by [MatchSummaryBuilder], a Spring-managed component.
 */
data class MatchSummaryPresentation(
    // Match header
    val ourName: String,
    val oppName: String,
    val ourScore: Int,
    val oppScore: Int,
    val outcome: MatchOutcome,
    val date: String,
    val timestamp: String,
    val matchId: String,
    
    // Sections (null if not applicable)
    val goals: GoalsSection?,
    val assists: AssistsSection?,
    val highlights: HighlightsSection?,
    val craque: CraqueSection?,
    val perigoConstante: PerigoConstanteSection?,
    val bagre: BagreSection?,
    val xerife: XerifeSection?,
    val passePrecisao: PassePrecisaoSection?,
    val correioExtraviado: CorreioExtraviadoSection?,
    val muralha: MuralhaSection?,
)

data class MatchOutcome(
    val emoji: String,
    val label: String,
    val color: Int,
    val type: OutcomeType,
)

enum class OutcomeType { WIN, DRAW, LOSS }

data class GoalsSection(
    val scorers: List<PlayerGoal>,
)

data class PlayerGoal(
    val name: String,
    val count: Int,
)

data class AssistsSection(
    val assisters: List<PlayerAssist>,
)

data class PlayerAssist(
    val name: String,
    val count: Int,
)

data class HighlightsSection(
    val top3: List<TopPlayer>,
    val teamAverage: String?,
)

data class TopPlayer(
    val medal: String,
    val name: String,
    val rating: String,
)

data class CraqueSection(
    val name: String,
    val reason: String,
    val phrase: String,
)

data class PerigoConstanteSection(
    val name: String,
    val shots: Int,
    val goals: Int,
    val efficient: Boolean,
    val phrase: String,
)

data class BagreSection(
    val name: String,
    val rating: String,
    val reason: String,
    val tackleStats: String?,    // e.g., "0/6 certos (0%)"
    val passStats: String?,      // e.g., "10/20 certos (50%)"
    val phrase: String,
)

data class XerifeSection(
    val name: String,
    val tacklesMade: Int,
    val tackleAttempts: Int,
    val successRate: Int,
    val phrase: String,
)

data class PassePrecisaoSection(
    val name: String,
    val passesMade: Int,
    val passAttempts: Int,
    val accuracy: Int,
    val phrase: String,
)

data class CorreioExtraviadoSection(
    val name: String,
    val missedPasses: Int,
    val playerAccuracyPct: Int,
    val teamAccuracyPct: Int,
    val deltaPct: Int,
    val phrase: String,
)

data class MuralhaSection(
    val name: String,
    val saves: Int,
    val goalsConceded: Int,
    val archetype: com.eafc26.discordstats.discord.GoalkeeperArchetype,
    /** Localised title, e.g. "🧱 Paredão". */
    val archetypeTitle: String,
    val phrase: String,
)

