package com.eafc26.discordstats.architecture

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class DesktopLauncherArchitectureTest {
    private val launcherDir = Path.of("launcher")
    private val swiftSource = launcherDir.resolve("EAFCStatsLauncher.swift")
    private val buildScript = launcherDir.resolve("build-app.sh")

    @Test
    fun `launcher Swift source exists`() {
        assertThat(Files.exists(swiftSource)).isTrue()
    }

    @Test
    fun `launcher build script exists`() {
        assertThat(Files.exists(buildScript)).isTrue()
    }

    @Test
    fun `launcher disables DashboardAutoLauncher via system property`() {
        val swift = Files.readString(swiftSource)
        assertThat(swift).contains("-Deafc.dashboard.auto-open=false")
    }

    @Test
    fun `launcher does not embed secrets or env values`() {
        val swift = Files.readString(swiftSource)
        assertThat(swift).doesNotContain("SPRING_DATASOURCE_PASSWORD")
        assertThat(swift).doesNotContain("service_role")
        assertThat(swift).doesNotContain("170818")
    }

    @Test
    fun `launcher loads env from runtime file not embedded values`() {
        val swift = Files.readString(swiftSource)
        assertThat(swift).contains(".env.local")
        assertThat(swift).contains("loadEnvLocal")
    }

    @Test
    fun `launcher uses pre-built JAR not bootRun`() {
        val swift = Files.readString(swiftSource)
        assertThat(swift).contains("-jar")
        assertThat(swift).doesNotContain("bootRun")
    }

    @Test
    fun `launcher performs health check before opening browser`() {
        val swift = Files.readString(swiftSource)
        assertThat(swift).contains("api/health")
        assertThat(swift).contains("isBackendHealthy")
        assertThat(swift).contains("waitForReadiness")
    }

    @Test
    fun `launcher has graceful shutdown`() {
        val swift = Files.readString(swiftSource)
        assertThat(swift).contains("shutdownApplication")
        assertThat(swift).contains("performShutdownBackend")
        assertThat(swift).contains("backendStartedByUs")
    }

    @Test
    fun `launcher persists project path in Application Support`() {
        val swift = Files.readString(swiftSource)
        assertThat(swift).contains("EAFC26DiscordStats")
        assertThat(swift).contains("project-path")
    }

    @Test
    fun `launcher separates log files`() {
        val swift = Files.readString(swiftSource)
        assertThat(swift).contains("launcher.log")
        assertThat(swift).contains("backend.log")
    }

    @Test
    fun `DashboardAutoLauncher defaults to disabled`() {
        val autoLauncher = Files.readString(
            Path.of("src/main/kotlin/com/eafc26/discordstats/desktop/DashboardAutoLauncher.kt")
        )
        assertThat(autoLauncher).contains("auto-open:false")
    }

    @Test
    fun `bootRun enables DashboardAutoLauncher`() {
        val build = Files.readString(Path.of("build.gradle.kts"))
        assertThat(build).contains("eafc.dashboard.auto-open", "true")
    }

    @Test
    fun `app bundle Info plist uses correct identifier`() {
        val buildSh = Files.readString(buildScript)
        assertThat(buildSh).contains("com.eafc26.stats.launcher")
        assertThat(buildSh).contains("CFBundleExecutable")
    }

    @Test
    fun `GitHub Actions workflow builds native Swift launcher`() {
        val workflow = Files.readString(Path.of(".github/workflows/build-macos-launcher.yml"))
        assertThat(workflow).contains("launcher/build-app.sh")
        assertThat(workflow).doesNotContain("heredoc")
    }

    // Lifecycle tests

    @Test
    fun `shutdown is idempotent via isShuttingDown guard`() {
        val swift = Files.readString(swiftSource)
        assertThat(swift).contains("isShuttingDown")
        assertThat(swift).contains("guard !isShuttingDown else { return }")
    }

    @Test
    fun `all shutdown paths use single shutdownApplication method`() {
        val swift = Files.readString(swiftSource)
        val quitButtonCalls = swift.split("shutdownApplication").size - 1
        assertThat(quitButtonCalls).isGreaterThanOrEqualTo(4)
        assertThat(swift).doesNotContain("func quit()")
    }

    @Test
    fun `applicationShouldTerminate delegates to shutdownApplication`() {
        val swift = Files.readString(swiftSource)
        assertThat(swift).contains("applicationShouldTerminate")
        assertThat(swift).contains("terminateCancel")
        assertThat(swift).contains("terminateNow")
    }

    @Test
    fun `window close does not auto-terminate`() {
        val swift = Files.readString(swiftSource)
        assertThat(swift).contains("applicationShouldTerminateAfterLastWindowClosed")
        assertThat(swift).contains("false")
    }

    @Test
    fun `shutdown cancels startup via cancelled flag`() {
        val swift = Files.readString(swiftSource)
        assertThat(swift).contains("cancelled = true")
        assertThat(swift).contains("if cancelled { return")
    }

    @Test
    fun `preexisting backend is not terminated on shutdown`() {
        val swift = Files.readString(swiftSource)
        assertThat(swift).contains("Backend not owned by launcher")
    }

    @Test
    fun `shutdown escalates from SIGTERM to SIGKILL`() {
        val swift = Files.readString(swiftSource)
        assertThat(swift).contains("terminate()")
        assertThat(swift).contains("SIGKILL")
    }

    @Test
    fun `restart waits for port release before starting new backend`() {
        val swift = Files.readString(swiftSource)
        assertThat(swift).contains("waitForPortRelease")
    }

    @Test
    fun `shutdown shows shuttingDown phase in UI`() {
        val swift = Files.readString(swiftSource)
        assertThat(swift).contains("case .shuttingDown")
        assertThat(swift).contains("Encerrando EA FC STATS")
    }

    @Test
    fun `buttons are disabled during shutdown`() {
        val swift = Files.readString(swiftSource)
        assertThat(swift).contains(".disabled(launcher.isShuttingDown)")
    }

    @Test
    fun `launcher starts java directly not via shell`() {
        val swift = Files.readString(swiftSource)
        assertThat(swift).contains("process.executableURL = URL(fileURLWithPath: javaPath)")
        assertThat(swift).doesNotContain("/bin/bash -c")
        assertThat(swift).doesNotContain("/bin/sh -c")
    }

    @Test
    fun `dashboard shutdown handles connection drop gracefully`() {
        val settings = Files.readString(Path.of("src/main/resources/settings.html"))
        assertThat(settings).contains("instanceof TypeError")
        assertThat(settings).contains("fetch")
    }
}
