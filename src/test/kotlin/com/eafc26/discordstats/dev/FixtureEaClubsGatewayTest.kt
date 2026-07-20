package com.eafc26.discordstats.dev

import com.eafc26.discordstats.ea.EaApiResult
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class FixtureEaClubsGatewayTest {

    private lateinit var gateway: FixtureEaClubsGateway

    @BeforeEach
    fun setUp() {
        gateway = FixtureEaClubsGateway(jacksonObjectMapper())
    }

    @Nested
    inner class GetLatestMatches {

        @Test
        fun `loads matches from default fixture`() {
            gateway.matchesFixturePath = "fixtures/dev/latest-matches.json"

            val result = gateway.getLatestMatches("1104972")

            assertThat(result).isInstanceOf(EaApiResult.Success::class.java)
            val matches = (result as EaApiResult.Success).data
            assertThat(matches).isNotEmpty
        }

        @Test
        fun `returns NoMatches when fixture not found`() {
            gateway.matchesFixturePath = "fixtures/dev/nonexistent.json"

            val result = gateway.getLatestMatches("1104972")

            assertThat(result).isEqualTo(EaApiResult.NoMatches)
        }

        @Test
        fun `returns Unavailable when simulateUnavailable is true`() {
            gateway.simulateUnavailable = true

            val result = gateway.getLatestMatches("1104972")

            assertThat(result).isInstanceOf(EaApiResult.Unavailable::class.java)
            val unavailable = result as EaApiResult.Unavailable
            assertThat(unavailable.statusCode).isEqualTo(503)
        }
    }

    @Nested
    inner class Reset {

        @Test
        fun `resets all settings to defaults`() {
            gateway.matchesFixturePath = "custom/path.json"
            gateway.simulateUnavailable = true

            gateway.reset()

            assertThat(gateway.matchesFixturePath).isEqualTo(FixtureEaClubsGateway.DEFAULT_MATCHES_FIXTURE)
            assertThat(gateway.simulateUnavailable).isFalse()
        }
    }
}

