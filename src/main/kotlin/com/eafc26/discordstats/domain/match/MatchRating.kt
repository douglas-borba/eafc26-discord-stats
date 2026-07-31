package com.eafc26.discordstats.domain.match

import java.math.BigDecimal

@JvmInline
value class MatchRating(val value: BigDecimal) {
    init {
        require(value >= BigDecimal.ZERO) { "MatchRating must not be negative" }
    }
}
