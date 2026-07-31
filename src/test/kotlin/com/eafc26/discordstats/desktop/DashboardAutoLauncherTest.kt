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

    @Test
    fun `regular server execution does not open browser`() {
        DashboardAutoLauncher(false, context, browser).onApplicationReady()

        verify(browser, never()).open(org.mockito.kotlin.any())
    }

    @Test
    fun `packaged execution opens dashboard only after ready event`() {
        webServerAt(18080)
        val launcher = DashboardAutoLauncher(true, context, browser)

        launcher.onApplicationReady()

        verify(browser).open(URI("http://localhost:18080/"))
    }

    @Test
    fun `dashboard is opened at most once when ready event repeats`() {
        webServerAt(8080)
        val launcher = DashboardAutoLauncher(true, context, browser)

        launcher.onApplicationReady()
        launcher.onApplicationReady()

        verify(browser).open(URI("http://localhost:8080/"))
    }

    private fun webServerAt(port: Int) {
        val webServer = mock<WebServer>()
        whenever(webServer.port).thenReturn(port)
        whenever(context.webServer).thenReturn(webServer)
    }
}
