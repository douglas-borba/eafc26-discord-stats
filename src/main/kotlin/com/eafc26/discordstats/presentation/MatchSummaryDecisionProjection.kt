package com.eafc26.discordstats.presentation

import com.eafc26.discordstats.domain.interpretation.AwardType
import com.eafc26.discordstats.domain.interpretation.DecisionEvidence
import com.eafc26.discordstats.domain.interpretation.RuleReference
import com.eafc26.discordstats.domain.match.MatchId
import com.eafc26.discordstats.domain.match.PlayerId
import com.eafc26.discordstats.domain.story.NarrativeKey

/**
 * Decision-only seam for the incremental migration of MatchSummaryBuilder.
 *
 * It deliberately contains no phrases, player display names or channel
 * formatting. Those remain presentation concerns.
 */
data class MatchSummaryDecisionProjection(
    val matchId: MatchId,
    val ourScore: Int,
    val opponentScore: Int,
    val outcome: MatchOutcome,
    val awards: List<AwardPresentationDecision>,
    val trace: PresentationDecisionTrace,
)

data class AwardPresentationDecision(
    val type: AwardType,
    val winnerId: PlayerId,
    val narrativeKey: NarrativeKey,
)

data class PresentationDecisionTrace(
    val rules: List<RuleReference>,
    val evidence: List<DecisionEvidence>,
)
