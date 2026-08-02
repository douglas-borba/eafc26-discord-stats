package com.eafc26.discordstats.architecture

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.readText

class PlaywrightVisibilityArchitectureTest {
    private val fetcher = Path.of("src/main/kotlin/com/eafc26/discordstats/ea/PlaywrightBrowserFetcher.kt").readText()
    private val gateway = Path.of("src/main/kotlin/com/eafc26/discordstats/ea/PlaywrightEaClubsGateway.kt").readText()
    private val dashboard = Path.of("src/main/kotlin/com/eafc26/discordstats/desktop/DashboardAutoLauncher.kt").readText()
    private val application = Path.of("src/main/resources/application.yml").readText()
    private val build = Path.of("build.gradle.kts").readText()

    @Test
    fun `production and packaged application never opt into headed Playwright`() {
        assertThat(application).contains("headless: true").doesNotContain("headless: false")
        assertThat(build).doesNotContain("app.ea.playwright.headless=false", "PWDEBUG")
        assertThat(fetcher).contains(
            ".setHeadless(pw.headless)",
            ".setDevtools(false)",
            "listOf(\"--headless=old\")",
        )
    }

    @Test
    fun `only Dashboard launcher invokes the macOS default browser`() {
        assertThat(fetcher).doesNotContain("/usr/bin/open", "Desktop.browse", "ProcessBuilder")
        assertThat(gateway).doesNotContain("/usr/bin/open", "Desktop.browse", "ProcessBuilder")
        assertThat(dashboard).contains("ProcessBuilder(\"/usr/bin/open\", uri.toString())")
    }

    @Test
    fun `bootRun is the local flow that opens the dashboard while deployment stays headless`() {
        assertThat(build).contains(
            "tasks.named<BootRun>(\"bootRun\")",
            "systemProperty(\"eafc.dashboard.auto-open\", \"true\")",
            "providers.environmentVariable(\"EAFC_VIEWER_PASSWORD\")",
            "providers.environmentVariable(\"EAFC_ADMIN_PASSWORD\")",
            "systemProperty(\"app.security.viewer-password\", \"viewer-local\")",
            "systemProperty(\"app.security.admin-password\", \"admin-local\")",
        )
        assertThat(Path.of("Dockerfile").readText())
            .contains("EAFC_DASHBOARD_AUTO_OPEN=false")
            .doesNotContain("viewer-local", "admin-local")
        assertThat(application).doesNotContain("viewer-local", "admin-local")
    }

    @Test
    fun `Playwright lifecycle closes every owned resource`() {
        assertThat(fetcher).contains(
            "page?.close()",
            "context?.close()",
            "browser?.close()",
            "playwright?.close()",
            "override fun destroy()",
        )
    }
}
