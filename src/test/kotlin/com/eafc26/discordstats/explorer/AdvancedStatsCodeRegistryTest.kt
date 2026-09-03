package com.eafc26.discordstats.explorer

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AdvancedStatsCodeRegistryTest {

    @Test
    fun `known codes return CONFIRMED status with metric name`() {
        val preAssists = AdvancedStatsCodeRegistry.lookup(0, 115)
        assertThat(preAssists.confidence).isEqualTo(CodeConfidence.CONFIRMED)
        assertThat(preAssists.metricName).isEqualTo("Pre-assists")

        val throughPasses = AdvancedStatsCodeRegistry.lookup(0, 152)
        assertThat(throughPasses.confidence).isEqualTo(CodeConfidence.CONFIRMED)
        assertThat(throughPasses.metricName).isEqualTo("Through passes")

        val beats = AdvancedStatsCodeRegistry.lookup(0, 112)
        assertThat(beats.confidence).isEqualTo(CodeConfidence.CONFIRMED)
        assertThat(beats.metricName).isEqualTo("Beats")
    }

    @Test
    fun `unknown codes return UNKNOWN status without metric name`() {
        val unknown = AdvancedStatsCodeRegistry.lookup(0, 42)
        assertThat(unknown.confidence).isEqualTo(CodeConfidence.UNKNOWN)
        assertThat(unknown.metricName).isNull()
    }

    @Test
    fun `code 6 is HYPOTHESIS not CONFIRMED and has no metric name`() {
        val code6agg0 = AdvancedStatsCodeRegistry.lookup(0, 6)
        assertThat(code6agg0.confidence).isEqualTo(CodeConfidence.HYPOTHESIS)
        assertThat(code6agg0.metricName).isNull()

        val code6agg1 = AdvancedStatsCodeRegistry.lookup(1, 6)
        assertThat(code6agg1.confidence).isEqualTo(CodeConfidence.HYPOTHESIS)
        assertThat(code6agg1.metricName).isNull()
    }

    @Test
    fun `code 174 remains observable but has no assigned sporting meaning`() {
        val code174 = AdvancedStatsCodeRegistry.lookup(0, 174)

        assertThat(code174.confidence).isEqualTo(CodeConfidence.UNKNOWN)
        assertThat(code174.metricName).isNull()
    }

    @Test
    fun `isKnown returns true only for CONFIRMED and HIGH_CONFIDENCE`() {
        assertThat(AdvancedStatsCodeRegistry.isKnown(0, 115)).isTrue()
        assertThat(AdvancedStatsCodeRegistry.isKnown(0, 6)).isFalse()
        assertThat(AdvancedStatsCodeRegistry.isKnown(0, 999)).isFalse()
    }

    @Test
    fun `aggregate_0 and aggregate_1 are tracked separately`() {
        val agg0 = AdvancedStatsCodeRegistry.lookup(0, 6)
        val agg1 = AdvancedStatsCodeRegistry.lookup(1, 6)
        assertThat(agg0.aggregate).isEqualTo(0)
        assertThat(agg1.aggregate).isEqualTo(1)
    }
}
