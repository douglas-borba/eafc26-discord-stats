package com.eafc26.discordstats.desktop

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URI

class HttpDashboardReadinessProbeTest {
    @Test
    fun `reports ready after health endpoint succeeds`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503))
            server.enqueue(MockResponse().setResponseCode(200))
            server.start()

            val healthUri = URI(server.url("/api/health").toString())

            assertTrue(HttpDashboardReadinessProbe().awaitHealthy(healthUri))
            assertTrue(server.requestCount >= 2)
        }
    }
}
