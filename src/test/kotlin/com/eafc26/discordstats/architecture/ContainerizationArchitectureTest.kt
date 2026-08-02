package com.eafc26.discordstats.architecture

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.readText

class ContainerizationArchitectureTest {
    private val dockerfile = Path.of("Dockerfile").readText()
    private val compose = Path.of("compose.yml").readText()
    private val build = Path.of("build.gradle.kts").readText()

    @Test
    fun `container pins the exact Playwright dependency and Java runtime`() {
        assertThat(build).contains("com.microsoft.playwright:playwright:1.47.0")
        assertThat(dockerfile)
            .contains("mcr.microsoft.com/playwright/java:v1.47.0-noble")
            .contains("eclipse-temurin:21-jdk-noble")
            .contains("PLAYWRIGHT_BROWSERS_PATH=/ms-playwright")
    }

    @Test
    fun `container exposes only presentation runtime configuration`() {
        assertThat(dockerfile)
            .contains("APP_WEB_NETWORK_ENABLED=true")
            .contains("EAFC_DASHBOARD_AUTO_OPEN=false")
            .doesNotContain("headless=false", "PWDEBUG")
        assertThat(compose)
            .contains("APP_WEB_NETWORK_ENABLED: \"true\"")
            .contains("EAFC_DASHBOARD_AUTO_OPEN: \"false\"")
            .contains("JAVA_TOOL_OPTIONS:")
            .contains("EAFC_DISCORD_MATCH_WEBHOOK_URL:")
            .doesNotContain("EAFC_DISCORD_HISTORY_WEBHOOK_URL:")
    }

    @Test
    fun `container reserves a bounded JVM share for the Playwright process tree`() {
        assertThat(dockerfile)
            .contains("JAVA_TOOL_OPTIONS=")
            .contains("-XX:MaxRAMPercentage=20.0")
            .contains("-XX:+ExitOnOutOfMemoryError")
            .doesNotContain("-Xmx", "MaxMetaspaceSize", "ReservedCodeCacheSize")
    }

    @Test
    fun `container runs non-root with health lifecycle and persistent canonical data`() {
        assertThat(dockerfile)
            .contains("USER pwuser")
            .contains("HEALTHCHECK")
            .contains("/api/health")
        assertThat(compose)
            .contains("init: true")
            .contains("ipc: host")
            .contains("eafc-data:/home/pwuser/Library/Application Support/EAFC26DiscordStats")
    }
}
