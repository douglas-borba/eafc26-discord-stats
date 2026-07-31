package com.eafc26.discordstats.domain.match

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class RatioTest {

    @Test
    fun `ratio exposes deterministic decimal precision`() {
        assertThat(Ratio(2, 3).decimal).isEqualByComparingTo("0.666667")
        assertThat(Ratio(7, 10).decimal).isEqualByComparingTo("0.700000")
    }

    @Test
    fun `ratio supports exact zero and one boundaries`() {
        assertThat(Ratio(0, 5).decimal).isEqualByComparingTo("0.000000")
        assertThat(Ratio(5, 5).decimal).isEqualByComparingTo("1.000000")
    }

    @Test
    fun `ratio rejects zero and negative denominators`() {
        assertThatThrownBy { Ratio(0, 0) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { Ratio(0, -1) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `ratio rejects an invalid numerator`() {
        assertThatThrownBy { Ratio(-1, 5) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { Ratio(6, 5) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `passing derives missed passes and accuracy from source counts`() {
        val passing = PassingStats(attempted = 20, completed = 15)

        assertThat(passing.missed).isEqualTo(5)
        assertThat(passing.accuracy).isEqualTo(Ratio(15, 20))
    }

    @Test
    fun `accuracy is absent when attempts are zero or either count is unknown`() {
        assertThat(PassingStats(attempted = 0, completed = 0).accuracy).isNull()
        assertThat(PassingStats(attempted = null, completed = null).accuracy).isNull()
        assertThat(DefendingStats(tacklesAttempted = null, tacklesCompleted = 3).tackleAccuracy).isNull()
    }
}
