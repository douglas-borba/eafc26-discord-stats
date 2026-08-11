package com.eafc26.discordstats.web

import com.eafc26.discordstats.application.club.MonitoredClub
import com.eafc26.discordstats.application.club.MonitoredClubRepository
import com.eafc26.discordstats.application.club.EaPlatform
import com.eafc26.discordstats.config.AppProperties
import com.eafc26.discordstats.config.EaProperties
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.ClubName
import com.eafc26.discordstats.scheduler.PollingStatusHolder
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.reactive.function.client.WebClient
import java.time.Instant
import java.util.concurrent.TimeUnit

class SystemHealthControllerTest {
    private lateinit var server: MockWebServer

    @BeforeEach fun startServer() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach fun stopServer() = server.shutdown()

    @Test fun `all healthy components return an UP aggregate`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        val response = controller(jdbc = healthyJdbc()).health()

        assertThat(response["overall"]).isEqualTo("UP")
        assertThat(component(response, "eaGateway")["status"]).isEqualTo("UP")
    }

    @Test fun `EA failure degrades health without failing the aggregate`() {
        server.enqueue(MockResponse().setResponseCode(503))
        val response = controller(jdbc = healthyJdbc()).health()

        assertThat(response["overall"]).isEqualTo("DEGRADED")
        assertThat(component(response, "eaGateway")["status"]).isEqualTo("DOWN")
        assertThat(component(response, "eaGateway")["statusCode"]).isEqualTo(503)
    }

    @Test fun `EA timeout is bounded and reported as degraded`() {
        server.enqueue(MockResponse().setHeadersDelay(4, TimeUnit.SECONDS).setResponseCode(200))
        val startedAt = System.nanoTime()
        val response = controller(jdbc = healthyJdbc()).health()
        val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)

        assertThat(elapsedMs).isLessThan(3_500)
        assertThat(response["overall"]).isEqualTo("DEGRADED")
        assertThat(component(response, "eaGateway")["status"]).isEqualTo("DOWN")
    }

    @Test fun `stale scheduler degrades health without failing the aggregate`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        val holder = PollingStatusHolder().also {
            it.update(ClubId("1104972")) { status -> status.copy(lastCheck = Instant.now().minusSeconds(181)) }
        }
        val response = controller(jdbc = healthyJdbc(), clubs = listOf(enabledClub()), pollingStatus = holder).health()

        assertThat(response["overall"]).isEqualTo("DEGRADED")
        assertThat(component(response, "scheduler")["status"]).isEqualTo("STALE")
    }

    @Test fun `Postgres failure degrades health without failing the aggregate`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        val jdbc: JdbcTemplate = mock()
        whenever(jdbc.queryForObject("SELECT 1", Int::class.java)).thenThrow(IllegalStateException("database down"))

        val response = controller(jdbc = jdbc).health()

        assertThat(response["overall"]).isEqualTo("DEGRADED")
        assertThat(component(response, "postgres")["status"]).isEqualTo("DOWN")
    }

    private fun controller(
        jdbc: JdbcTemplate?,
        clubs: List<MonitoredClub> = emptyList(),
        pollingStatus: PollingStatusHolder = PollingStatusHolder(),
    ): SystemHealthController {
        val props = AppProperties(ea = EaProperties(gatewayBaseUrl = server.url("/").toString().trimEnd('/')))
        val probe = EaGatewayHealthProbe(WebClient.builder().baseUrl(props.ea.gatewayBaseUrl).build(), props)
        return SystemHealthController(jdbc, probe, pollingStatus, repository(clubs), props)
    }

    private fun healthyJdbc(): JdbcTemplate = mock<JdbcTemplate>().also {
        whenever(it.queryForObject("SELECT 1", Int::class.java)).thenReturn(1)
    }

    @Suppress("UNCHECKED_CAST")
    private fun component(response: Map<String, Any>, name: String): Map<String, Any> = response[name] as Map<String, Any>

    private fun repository(clubs: List<MonitoredClub>) = object : MonitoredClubRepository {
        override fun save(club: MonitoredClub) = club
        override fun findById(clubId: ClubId) = clubs.find { it.clubId == clubId }
        override fun findAll() = clubs
        override fun existsById(clubId: ClubId) = clubs.any { it.clubId == clubId }
        override fun deleteById(clubId: ClubId) = false
    }

    private fun enabledClub() = MonitoredClub(
        clubId = ClubId("1104972"),
        displayName = ClubName("Associação BF"),
        platform = EaPlatform("common-gen5"),
        monitoringEnabled = true,
        discordWebhookSecretReference = null,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )
}
