package com.eafc26.discordstats.desktop

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.web.context.WebServerApplicationContext
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Opens the local Dashboard only for distributions that explicitly opt in.
 *
 * The macOS package supplies the opt-in JVM property. Regular server, test and
 * development executions remain unchanged.
 */
@Component
class DashboardAutoLauncher(
    @Value("\${eafc.dashboard.auto-open:false}")
    private val enabled: Boolean,
    private val webServerContext: WebServerApplicationContext,
    private val browser: DashboardBrowser,
) {
    private val opened = AtomicBoolean(false)

    @EventListener(ApplicationReadyEvent::class)
    fun onApplicationReady() {
        if (!enabled || !opened.compareAndSet(false, true)) return

        val dashboardUri = URI("http://localhost:${webServerContext.webServer.port}/")
        runCatching { browser.open(dashboardUri) }
            .onSuccess { log.info("Dashboard opened at {}", dashboardUri) }
            .onFailure { error ->
                log.warn("Application started, but the Dashboard could not be opened at $dashboardUri", error)
            }
    }

    private companion object {
        val log = LoggerFactory.getLogger(DashboardAutoLauncher::class.java)
    }
}

fun interface DashboardBrowser {
    fun open(uri: URI)
}

@Component
class MacOsDashboardBrowser : DashboardBrowser {
    override fun open(uri: URI) {
        ProcessBuilder("/usr/bin/open", uri.toString()).start()
    }
}
