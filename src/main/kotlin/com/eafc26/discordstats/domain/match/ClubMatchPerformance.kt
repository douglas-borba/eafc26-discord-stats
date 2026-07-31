package com.eafc26.discordstats.domain.match

@JvmInline
value class Score(val goals: Int) {
    init {
        require(goals >= 0) { "Score must not be negative" }
    }
}

enum class ReportedMatchResult {
    WIN,
    DRAW,
    LOSS,
}

data class ClubMatchPerformance(
    val club: ClubIdentity,
    val score: Score,
    val reportedResult: ReportedMatchResult?,
    val players: List<PlayerMatchPerformance>,
) {
    init {
        require(players.map { it.player.id }.distinct().size == players.size) {
            "Player IDs must be unique within a club performance"
        }
    }
}
