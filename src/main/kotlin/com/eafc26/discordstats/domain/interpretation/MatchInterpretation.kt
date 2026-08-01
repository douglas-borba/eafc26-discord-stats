package com.eafc26.discordstats.domain.interpretation

import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.FootballMatch
import com.eafc26.discordstats.domain.match.MatchId

/**
 * Canonical deterministic interpretation of one match from one club's
 * perspective. It contains decisions and supporting facts, but no
 * presentation-oriented text.
 */
data class MatchInterpretation(
    val footballMatch: FootballMatch,
    val perspectiveClubId: ClubId,
    val result: ResultDecision,
    val eligibility: EligibilityInterpretation,
    val teamMetrics: TeamMetrics,
    val awards: MatchAwards,
    val features: MatchFeatures,
) {
    val matchId: MatchId
        get() = footballMatch.id

    init {
        require(result.ourClub == perspectiveClubId) {
            "Result decision must use the interpretation perspective"
        }
    }

    val appliedRules: List<RuleReference>
        get() = buildList {
            add(result.rule)
            add(eligibility.rule)
            add(awards.craque.rule)
            add(awards.bagre.rule)
            add(awards.xerife.rule)
            addAll(features.rules)
        }.distinct()

    val evidence: List<DecisionEvidence>
        get() = buildList {
            addAll(result.evidence)
            eligibility.decisions.forEach { addAll(it.evidence) }
            addAll(awards.craque.evidence)
            addAll(awards.bagre.evidence)
            addAll(awards.xerife.evidence)
            addAll(features.evidence)
        }
}
