package com.eafc26.discordstats.domain.interpretation

import com.eafc26.discordstats.domain.match.PlayerId
import java.time.Duration

data class EligibilityInterpretation(
    val decisions: List<PlayerEligibilityDecision>,
    val maximumValidDuration: Duration?,
    val rule: RuleReference,
) {
    val eligiblePlayerIds: Set<PlayerId>
        get() = decisions
            .filter { it.status == EligibilityStatus.ELIGIBLE }
            .mapTo(linkedSetOf()) { it.playerId }
}

data class PlayerEligibilityDecision(
    val playerId: PlayerId,
    val status: EligibilityStatus,
    val reason: EligibilityReason,
    val evidence: List<DecisionEvidence>,
)

enum class EligibilityStatus {
    ELIGIBLE,
    INELIGIBLE,
}

enum class EligibilityReason {
    PLAYED_AT_LEAST_TEAM_THRESHOLD,
    PLAYED_BELOW_TEAM_THRESHOLD,
    INVALID_PLAYING_TIME,
    NO_VALID_TEAM_PLAYING_TIME_FALLBACK,
}
