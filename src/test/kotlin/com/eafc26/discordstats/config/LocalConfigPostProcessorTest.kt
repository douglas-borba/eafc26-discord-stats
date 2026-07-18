package com.eafc26.discordstats.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.SpringApplication
import org.springframework.core.env.StandardEnvironment
import java.util.prefs.Preferences

class LocalConfigPostProcessorTest {

    private val app = SpringApplication()
    private lateinit var prefs: Preferences

    @BeforeEach
    fun setup() {
        prefs = Preferences.userNodeForPackage(SettingsService::class.java)
        prefs.clear()
        prefs.flush()
    }

    @AfterEach
    fun cleanup() {
        prefs.clear()
        prefs.flush()
    }

    @Test
    fun `default results in server address 127_0_0_1`() {
        val env = StandardEnvironment()
        val processor = LocalConfigPostProcessor()
        processor.postProcessEnvironment(env, app)
        assertThat(env.getProperty("server.address")).isEqualTo("127.0.0.1")
    }

    @Test
    fun `network-enabled false results in 127_0_0_1`() {
        prefs.putBoolean("web.network-enabled", false)
        prefs.flush()

        val env = StandardEnvironment()
        val processor = LocalConfigPostProcessor()
        processor.postProcessEnvironment(env, app)
        assertThat(env.getProperty("server.address")).isEqualTo("127.0.0.1")
    }

    @Test
    fun `network-enabled true results in 0_0_0_0`() {
        prefs.putBoolean("web.network-enabled", true)
        prefs.flush()

        val env = StandardEnvironment()
        val processor = LocalConfigPostProcessor()
        processor.postProcessEnvironment(env, app)
        assertThat(env.getProperty("server.address")).isEqualTo("0.0.0.0")
    }
}
