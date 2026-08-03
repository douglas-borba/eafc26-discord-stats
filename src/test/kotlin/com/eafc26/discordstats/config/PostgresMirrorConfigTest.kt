package com.eafc26.discordstats.config

import com.eafc26.discordstats.store.MirroringCanonicalMatchRepository
import com.eafc26.discordstats.store.PostgresCanonicalMatchRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PostgresMirrorConfigTest {

    @Test
    fun `mirror-enabled defaults to false in AppProperties`() {
        val props = AppProperties()
        assertThat(props.postgres.mirrorEnabled).isFalse()
    }

    @Test
    fun `acquisition-enabled defaults to true in AppProperties`() {
        val props = AppProperties()
        assertThat(props.acquisition.enabled).isTrue()
    }
}
