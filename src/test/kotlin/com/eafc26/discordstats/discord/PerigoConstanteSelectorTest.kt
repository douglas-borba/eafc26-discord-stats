package com.eafc26.discordstats.discord

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * PerigoConstanteSelector is deprecated.
 * All behavioural coverage has moved to [OffensiveNarrativeEvaluatorTest].
 *
 * This file keeps one compile-and-pass test to confirm that the deprecated
 * class still compiles and that its MIN_SHOTS constant delegates correctly
 * to [OffensiveNarrativeEvaluator.MIN_SHOTS].
 */
@Suppress("DEPRECATION")
class PerigoConstanteSelectorTest {

    @Test
    fun `MIN_SHOTS constant delegates to OffensiveNarrativeEvaluator`() {
        assertThat(PerigoConstanteSelector.MIN_SHOTS)
            .isEqualTo(OffensiveNarrativeEvaluator.MIN_SHOTS)
    }
}
