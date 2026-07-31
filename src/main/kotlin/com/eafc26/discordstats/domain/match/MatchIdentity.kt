package com.eafc26.discordstats.domain.match

@JvmInline
value class MatchId(val value: String) {
    init {
        require(value.isNotBlank()) { "MatchId must not be blank" }
    }
}
