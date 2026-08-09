package com.eafc26.discordstats.architecture

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.readText

class ContainerizationArchitectureTest {
    private val dockerfile = Path.of("Dockerfile").readText()
    private val compose = Path.of("compose.yml").readText()
    private val gatewayDockerfile = Path.of("apps/ea-gateway/Dockerfile").readText()

    @Test
    fun `containers use bounded Java and Node runtimes without a browser`() {
        assertThat(dockerfile)
            .contains("eclipse-temurin:21-jdk-noble")
            .contains("eclipse-temurin:21-jre-noble")
        assertThat(gatewayDockerfile).contains("node:22-alpine")
    }

    @Test
    fun `container exposes only presentation runtime configuration`() {
        assertThat(dockerfile)
            .contains("APP_WEB_NETWORK_ENABLED=true")
            .contains("EAFC_DASHBOARD_AUTO_OPEN=false")
            .doesNotContain("PWDEBUG")
        assertThat(compose)
            .contains("APP_WEB_NETWORK_ENABLED: \"true\"")
            .contains("EAFC_DASHBOARD_AUTO_OPEN: \"false\"")
            .contains("JAVA_TOOL_OPTIONS:")
            .contains("EAFC_DISCORD_MATCH_WEBHOOK_URL:")
            .doesNotContain("EAFC_DISCORD_HISTORY_WEBHOOK_URL:")
    }

    @Test
    fun `container reserves a bounded JVM share`() {
        assertThat(dockerfile)
            .contains("JAVA_TOOL_OPTIONS=")
            .contains("-XX:MaxRAMPercentage=20.0")
            .contains("-XX:+ExitOnOutOfMemoryError")
            .doesNotContain("-Xmx", "MaxMetaspaceSize", "ReservedCodeCacheSize")
    }

    @Test
    fun `container runs non-root with health lifecycle and persistent canonical data`() {
        assertThat(dockerfile)
            .contains("USER eafc")
            .contains("HEALTHCHECK")
            .contains("/api/health")
        assertThat(compose)
            .contains("init: true")
            .contains("eafc-data:/home/eafc/Library/Application Support/EAFC26DiscordStats")
            .contains("EA_GATEWAY_BASE_URL: http://ea-gateway:8081")
    }
}
