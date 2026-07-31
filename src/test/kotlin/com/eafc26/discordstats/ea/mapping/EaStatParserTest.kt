package com.eafc26.discordstats.ea.mapping

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class EaStatParserTest {

    private lateinit var warnings: MutableList<NormalizationWarning>
    private lateinit var parser: EaStatParser

    @BeforeEach
    fun setUp() {
        warnings = mutableListOf()
        parser = EaStatParser(warnings)
    }

    @Test
    fun `valid non-negative integers are parsed and absent values remain absent`() {
        assertThat(parser.nonNegativeInt(" 42 ", "stat")).isEqualTo(42)
        assertThat(parser.nonNegativeInt("0", "stat")).isZero()
        assertThat(parser.nonNegativeInt(null, "stat")).isNull()
        assertThat(warnings).isEmpty()
    }

    @Test
    fun `blank malformed and overflowing integers become absent with warnings`() {
        assertThat(parser.nonNegativeInt(" ", "blank")).isNull()
        assertThat(parser.nonNegativeInt("not-a-number", "malformed")).isNull()
        assertThat(parser.nonNegativeInt("999999999999999999", "overflow")).isNull()

        assertThat(warnings.map { it.code }).containsExactly(
            NormalizationIssueCode.INVALID_INTEGER,
            NormalizationIssueCode.INVALID_INTEGER,
            NormalizationIssueCode.INVALID_INTEGER,
        )
    }

    @Test
    fun `negative integer becomes absent instead of violating domain invariants`() {
        assertThat(parser.nonNegativeInt("-1", "shots")).isNull()
        assertThat(warnings.single().code).isEqualTo(NormalizationIssueCode.NEGATIVE_STATISTIC)
    }

    @Test
    fun `valid decimal rating is preserved exactly`() {
        assertThat(parser.nonNegativeDecimal("8.75", "rating"))
            .isEqualByComparingTo(BigDecimal("8.75"))
        assertThat(warnings).isEmpty()
    }

    @Test
    fun `invalid and negative ratings become absent with distinct warnings`() {
        assertThat(parser.nonNegativeDecimal("bad", "rating.invalid")).isNull()
        assertThat(parser.nonNegativeDecimal("-0.1", "rating.negative")).isNull()

        assertThat(warnings.map { it.code }).containsExactly(
            NormalizationIssueCode.INVALID_DECIMAL,
            NormalizationIssueCode.NEGATIVE_RATING,
        )
    }

    @Test
    fun `EA boolean flags accept numeric and textual representations`() {
        assertThat(parser.booleanFlag("1", "flag")).isTrue()
        assertThat(parser.booleanFlag("true", "flag")).isTrue()
        assertThat(parser.booleanFlag("0", "flag")).isFalse()
        assertThat(parser.booleanFlag("FALSE", "flag")).isFalse()
        assertThat(parser.booleanFlag(null, "flag")).isNull()
        assertThat(warnings).isEmpty()
    }

    @Test
    fun `invalid boolean flag becomes absent with warning`() {
        assertThat(parser.booleanFlag("yes", "mom")).isNull()
        assertThat(warnings.single().code).isEqualTo(NormalizationIssueCode.INVALID_BOOLEAN_FLAG)
    }

    @Test
    fun `completed count is clamped when it exceeds attempted count`() {
        assertThat(parser.completedAttempts(10, 12, "passing")).isEqualTo(10 to 10)
        assertThat(warnings.single().code)
            .isEqualTo(NormalizationIssueCode.COMPLETED_EXCEEDS_ATTEMPTED)
    }
}
