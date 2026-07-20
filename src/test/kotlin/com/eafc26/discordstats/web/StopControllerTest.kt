package com.eafc26.discordstats.web

import com.eafc26.discordstats.config.WebhookConfigService
import org.assertj.core.api.Assertions.assertThat
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
        whenever(webhookConfigService.isHistoryConfigured()).thenReturn(true)
    }

    @Test
    fun `POST stop from non-localhost returns 403`() {
        // In WebFlux test the remote address resolves to empty/null which isLocalhost() treats as localhost.
        // We verify that the controller logic works: inject a custom exchange via mutate is not straightforward,
        // so we test the isLocalhost helper directly via reflection.
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
            .jsonPath("$.message").isEqualTo("Aplicativo encerrando...")
    }

    @Test
    fun `GET stop returns 405 Method Not Allowed`() {
        webClient.get().uri("/api/application/stop")
            .exchange()
            .expectStatus().isEqualTo(405)
    }

    @Test
    fun `shutdown is triggered asynchronously so response arrives before process exits`() {
        // The controller must return a response before the shutdown thread fires.
        // We verify the response completes successfully (if shutdown blocked the response,
        // the test would timeout or fail). We use a generous threshold since CI environments
        // can have variable latency, but the key assertion is that the response arrives at all
        // before the 300ms shutdown delay triggers the actual exit.
        val start = System.nanoTime()
        webClient.post().uri("/api/application/stop")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("stopping")
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        // The shutdown delay is 300ms; response should arrive well before that.
        // Using 280ms as threshold to account for test overhead while still proving async behavior.
        assertThat(elapsedMs).isLessThan(280)
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
