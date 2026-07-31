package com.eafc26.discordstats.desktop

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.web.context.WebServerApplicationContext
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Opens the local Dashboard only for executions that explicitly opt in.
 *
 * The macOS package and the Gradle development task supply the opt-in JVM
 * property. Regular server and test executions remain unchanged.
 */
@Component
class DashboardAutoLauncher(
    @Value("\${eafc.dashboard.auto-open:false}")
    private val enabled: Boolean,
    private val webServerContext: WebServerApplicationContext,
    private val browser: DashboardBrowser,
    private val readinessProbe: DashboardReadinessProbe,
) {
    private val opened = AtomicBoolean(false)

    @EventListener(ApplicationReadyEvent::class)
    fun onApplicationReady() {
        if (!enabled || !opened.compareAndSet(false, true)) return

        val baseUri = URI("http://localhost:${webServerContext.webServer.port}/")
        val healthUri = baseUri.resolve("api/health")

        if (!readinessProbe.awaitHealthy(healthUri)) {
            log.error("Application started, but its health endpoint did not become ready at {}", healthUri)
            return
        }

        val dashboardUri = baseUri
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

fun interface DashboardReadinessProbe {
    fun awaitHealthy(uri: URI): Boolean
}

@Component
class HttpDashboardReadinessProbe : DashboardReadinessProbe {
    private val client = HttpClient.newBuilder()
        .connectTimeout(REQUEST_TIMEOUT)
        .build()

    override fun awaitHealthy(uri: URI): Boolean {
        val deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos()

        while (System.nanoTime() < deadline) {
            if (isHealthy(uri)) return true

            try {
                Thread.sleep(RETRY_INTERVAL.toMillis())
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        }

        return false
    }

    private fun isHealthy(uri: URI): Boolean =
        runCatching {
            val request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build()
            client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() in 200..299
        }.getOrDefault(false)

    private companion object {
        val STARTUP_TIMEOUT: Duration = Duration.ofSeconds(30)
        val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(1)
        val RETRY_INTERVAL: Duration = Duration.ofMillis(100)
    }
}

@Component
class MacOsDashboardBrowser : DashboardBrowser {
    override fun open(uri: URI) {
        ProcessBuilder("/usr/bin/open", uri.toString()).start()
    }
}
