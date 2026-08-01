package com.eafc26.discordstats.desktop

import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.boot.web.server.WebServer
import org.springframework.boot.web.context.WebServerApplicationContext
import java.net.URI

class DashboardAutoLauncherTest {
    private val context = mock<WebServerApplicationContext>()
    private val browser = mock<DashboardBrowser>()
    private val readinessProbe = mock<DashboardReadinessProbe>()

    @Test
    fun `regular server execution does not open browser`() {
        DashboardAutoLauncher(false, context, browser, readinessProbe).onApplicationReady()

        verify(browser, never()).open(org.mockito.kotlin.any())
        verify(readinessProbe, never()).awaitHealthy(org.mockito.kotlin.any())
    }

    @Test
    fun `enabled execution opens dashboard only after health endpoint is ready`() {
        webServerAt(18080)
        whenever(readinessProbe.awaitHealthy(URI("http://localhost:18080/api/health"))).thenReturn(true)
        val launcher = DashboardAutoLauncher(true, context, browser, readinessProbe)

        launcher.onApplicationReady()

        verify(browser).open(URI("http://localhost:18080/"))
    }

    @Test
    fun `browser remains closed when health endpoint does not become ready`() {
        webServerAt(18080)
        whenever(readinessProbe.awaitHealthy(URI("http://localhost:18080/api/health"))).thenReturn(false)

        DashboardAutoLauncher(true, context, browser, readinessProbe).onApplicationReady()

        verify(browser, never()).open(org.mockito.kotlin.any())
    }

    @Test
    fun `dashboard is opened at most once when ready event repeats`() {
        webServerAt(8080)
        whenever(readinessProbe.awaitHealthy(URI("http://localhost:8080/api/health"))).thenReturn(true)
        val launcher = DashboardAutoLauncher(true, context, browser, readinessProbe)

        launcher.onApplicationReady()
        launcher.onApplicationReady()

        verify(browser).open(URI("http://localhost:8080/"))
        verify(readinessProbe).awaitHealthy(URI("http://localhost:8080/api/health"))
    }

    private fun webServerAt(port: Int) {
        val webServer = mock<WebServer>()
        whenever(webServer.port).thenReturn(port)
        whenever(context.webServer).thenReturn(webServer)
    }
}
