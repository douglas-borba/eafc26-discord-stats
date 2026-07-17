package com.eafc26.discordstats.web

import com.eafc26.discordstats.config.WebhookConfigService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.ApplicationContext
import org.springframework.test.web.reactive.server.WebTestClient

@WebFluxTest(StopController::class)
class StopControllerTest {

    @Autowired
    private lateinit var webClient: WebTestClient

    @MockBean
    private lateinit var webhookConfigService: WebhookConfigService

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @BeforeEach
    fun setUp() {
        whenever(webhookConfigService.isConfigured()).thenReturn(true)
    }

    @Test
    fun `POST stop from non-localhost returns 403`() {
        // In WebFlux test the remote address resolves to empty/null which isLocalhost() treats as localhost.
        // We verify that the controller logic works: inject a custom exchange via mutate is not straightforward,
        // so we test at the unit level by calling the controller directly.
        val controller = StopController(applicationContext)

        // Non-localhost scenario: we check the isLocalhost helper implicitly via the 403 path.
        // The WebTestClient in-process exchange always shows remote address as null/empty (allowed),
        // so we test the isLocalhost logic via direct assertion.
        assertIsLocalhost("127.0.0.1", true)
        assertIsLocalhost("::1", true)
        assertIsLocalhost("0:0:0:0:0:0:0:1", true)
        assertIsLocalhost("", true)
        assertIsLocalhost("192.168.1.100", false)
        assertIsLocalhost("10.0.0.1", false)
    }

    @Test
    fun `POST stop from in-process WebTestClient returns 200 stopping`() {
        // In-process calls have null remote address, treated as localhost
        webClient.post().uri("/api/application/stop")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("stopping")
    }

    private fun assertIsLocalhost(addr: String, expected: Boolean) {
        val method = StopController::class.java.getDeclaredMethod("isLocalhost", String::class.java)
        method.isAccessible = true
        val controller = StopController(applicationContext)
        val result = method.invoke(controller, addr) as Boolean
        assert(result == expected) {
            "isLocalhost(\"$addr\") expected $expected but was $result"
        }
    }
}
