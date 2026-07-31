package com.eafc26.discordstats.domain.interpretation

import com.eafc26.discordstats.domain.match.PlayerId

data class MatchAwards(
    val craque: AwardDecision,
    val bagre: AwardDecision,
    val xerife: AwardDecision,
)

data class AwardDecision(
    val type: AwardType,
    val winnerId: PlayerId?,
    val reason: AwardDecisionReason,
    val rule: RuleReference,
    val evidence: List<DecisionEvidence>,
) {
    init {
        require(evidence.isNotEmpty()) { "An award decision must contain evidence" }
        require((winnerId == null) == (reason == AwardDecisionReason.NO_ELIGIBLE_CANDIDATE)) {
            "Award winner and decision reason are inconsistent"
        }
    }

    val awarded: Boolean
        get() = winnerId != null
}

enum class AwardType {
    CRAQUE,
    BAGRE,
    XERIFE,
}

enum class AwardDecisionReason {
    EA_MAN_OF_THE_MATCH,
    HIGHEST_RATING,
    LOWEST_ELIGIBLE_RATING,
    HIGHEST_DEFENSIVE_IMPACT,
    NO_ELIGIBLE_CANDIDATE,
}
