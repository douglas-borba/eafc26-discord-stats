package com.eafc26.discordstats.domain.match

import java.math.BigDecimal
import java.math.RoundingMode

data class Ratio(
    val numerator: Int,
    val denominator: Int,
) {
    init {
        require(denominator > 0) { "Ratio denominator must be positive" }
        require(numerator >= 0) { "Ratio numerator must not be negative" }
        require(numerator <= denominator) { "Ratio numerator must not exceed denominator" }
    }

    val decimal: BigDecimal
        get() = numerator.toBigDecimal().divide(
            denominator.toBigDecimal(),
            DECIMAL_SCALE,
            RoundingMode.HALF_UP,
        )

    companion object {
        const val DECIMAL_SCALE = 6
    }
}
