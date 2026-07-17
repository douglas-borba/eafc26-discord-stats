package com.eafc26.discordstats.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.boot.SpringApplication
import org.springframework.core.env.StandardEnvironment
import java.nio.file.Path
import java.util.Properties

class LocalConfigPostProcessorTest {

    private val app = SpringApplication()

    @Test
    fun `no config file results in server address 127_0_0_1`() {
        val env = StandardEnvironment()
        val processor = processorWithDir(null)
        processor.postProcessEnvironment(env, app)
        assertThat(env.getProperty("server.address")).isEqualTo("127.0.0.1")
    }

    @Test
    fun `network-enabled false in config results in 127_0_0_1`(@TempDir tempDir: Path) {
        writeConfig(tempDir, "web.network-enabled=false")
        val env = StandardEnvironment()
        val processor = processorWithDir(tempDir)
        processor.postProcessEnvironment(env, app)
        assertThat(env.getProperty("server.address")).isEqualTo("127.0.0.1")
    }

    @Test
    fun `network-enabled true in config results in 0_0_0_0`(@TempDir tempDir: Path) {
        writeConfig(tempDir, "web.network-enabled=true")
        val env = StandardEnvironment()
        val processor = processorWithDir(tempDir)
        processor.postProcessEnvironment(env, app)
        assertThat(env.getProperty("server.address")).isEqualTo("0.0.0.0")
    }

    @Test
    fun `webhook URL is loaded from config file`(@TempDir tempDir: Path) {
        writeConfig(tempDir, "discord.webhook.url=https://discord.com/api/webhooks/1/tok")
        val env = StandardEnvironment()
        val processor = processorWithDir(tempDir)
        processor.postProcessEnvironment(env, app)
        assertThat(env.getProperty("discord.webhook.url"))
            .isEqualTo("https://discord.com/api/webhooks/1/tok")
    }

    @Test
    fun `empty config file results in server address 127_0_0_1`(@TempDir tempDir: Path) {
        writeConfig(tempDir, "")
        val env = StandardEnvironment()
        val processor = processorWithDir(tempDir)
        processor.postProcessEnvironment(env, app)
        assertThat(env.getProperty("server.address")).isEqualTo("127.0.0.1")
    }

    private fun writeConfig(dir: Path, content: String) {
        dir.resolve("config.properties").toFile().writeText(content)
    }

    private fun processorWithDir(tempDir: Path?): LocalConfigPostProcessor {
        return object : LocalConfigPostProcessor() {
            override fun configFile(userHome: String) =
                if (tempDir != null) tempDir.resolve("config.properties").toFile()
                else java.io.File("/nonexistent/path/config.properties")
        }
    }
}
