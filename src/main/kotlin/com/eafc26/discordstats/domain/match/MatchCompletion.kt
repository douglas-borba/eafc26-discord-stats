package com.eafc26.discordstats.domain.match

/** Factual completion state supplied by the EA match record. */
data class MatchCompletion(
    val status: MatchCompletionStatus = MatchCompletionStatus.UNKNOWN,
    val dnfClubId: ClubId? = null,
) {
    init {
        require((status == MatchCompletionStatus.DNF) == (dnfClubId != null)) {
            "Only DNF matches may identify a DNF club"
        }
    }

    val hasCompleteSportingStatistics: Boolean get() = status != MatchCompletionStatus.DNF

    companion object {
        val COMPLETED = MatchCompletion(MatchCompletionStatus.COMPLETED)
        val UNKNOWN = MatchCompletion(MatchCompletionStatus.UNKNOWN)
        fun dnf(clubId: ClubId) = MatchCompletion(MatchCompletionStatus.DNF, clubId)
    }
}

enum class MatchCompletionStatus { COMPLETED, DNF, UNKNOWN }
