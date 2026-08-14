package com.eafc26.discordstats.domain.match

import java.time.Instant

data class FootballMatch(
    val id: MatchId,
    val playedAt: Instant,
    val competition: CompetitionType?,
    val participants: List<ClubMatchPerformance>,
    val completion: MatchCompletion = MatchCompletion.UNKNOWN,
) {
    init {
        require(participants.size >= 2) { "A football match must have at least two participants" }
        require(participants.map { it.club.id }.distinct().size == participants.size) {
            "FootballMatch participant club IDs must be unique"
        }
        require(completion.dnfClubId == null || completion.dnfClubId in participants.map { it.club.id }) {
            "DNF club must be a match participant"
        }
    }
}

enum class CompetitionType {
    FRIENDLY,
    LEAGUE,
    PLAYOFF,
}
