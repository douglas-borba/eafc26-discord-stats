package com.eafc26.discordstats.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AppPropertiesTest {

    @Test
    fun `EA acquisition window defaults to twenty matches`() {
        assertThat(EaProperties().maxResultCount).isEqualTo(20)
    }
}
