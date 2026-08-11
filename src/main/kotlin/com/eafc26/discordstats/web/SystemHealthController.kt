package com.eafc26.discordstats.web

import com.eafc26.discordstats.application.club.MonitoredClubRepository
import com.eafc26.discordstats.config.AppProperties
import com.eafc26.discordstats.ea.EaApiResult
import com.eafc26.discordstats.ea.EaClubsGateway
import com.eafc26.discordstats.scheduler.MatchPollingScheduler
import com.eafc26.discordstats.scheduler.PollingStatusHolder
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import java.time.Instant

@RestController
@RequestMapping("/api/admin/system", produces = [MediaType.APPLICATION_JSON_VALUE])
class SystemHealthController(
    private val jdbcTemplate: JdbcTemplate?,
    @Qualifier("production") private val eaGateway: EaClubsGateway,
    private val pollingStatusHolder: PollingStatusHolder,
    private val monitoredClubRepository: MonitoredClubRepository,
    private val props: AppProperties,
) {
    private val startedAt: Instant = Instant.now()

    @GetMapping("/health")
    fun health(): Map<String, Any> {
        return mapOf(
            "application" to applicationHealth(),
            "postgres" to postgresHealth(),
            "eaGateway" to eaGatewayHealth(),
            "scheduler" to schedulerHealth(),
            "build" to buildInfo(),
        )
    }

    private fun applicationHealth(): Map<String, Any> = mapOf(
        "status" to "UP",
        "startedAt" to startedAt.toString(),
        "uptimeSeconds" to Duration.between(startedAt, Instant.now()).seconds,
    )

    private fun postgresHealth(): Map<String, Any> {
        val jdbc = jdbcTemplate ?: return mapOf("status" to "NOT_CONFIGURED")
        return try {
            val start = System.currentTimeMillis()
            jdbc.queryForObject("SELECT 1", Int::class.java)
            mapOf(
                "status" to "UP",
                "latencyMs" to (System.currentTimeMillis() - start),
            )
        } catch (ex: Exception) {
            mapOf(
                "status" to "DOWN",
                "error" to (ex.message ?: "Unknown error"),
            )
        }
    }

    private fun eaGatewayHealth(): Map<String, Any> {
        return try {
            when (val result = eaGateway.searchClubs("__health_check_ping__")) {
                is EaApiResult.Success -> mapOf("status" to "UP")
                EaApiResult.NoMatches -> mapOf("status" to "UP")
                is EaApiResult.Unavailable -> mapOf(
                    "status" to if (result.statusCode >= 500) "EA_UPSTREAM_DOWN" else "GATEWAY_DOWN",
                    "statusCode" to result.statusCode,
                    "message" to result.message,
                )
                is EaApiResult.UnexpectedPayload -> mapOf(
                    "status" to "UP",
                    "note" to "Unexpected payload shape: ${result.cause.message}",
                )
            }
        } catch (ex: Exception) {
            mapOf("status" to "GATEWAY_DOWN", "error" to (ex.message ?: "Unknown error"))
        }
    }

    private fun schedulerHealth(): Map<String, Any> {
        val enabledClubs = monitoredClubRepository.findAll().filter { it.monitoringEnabled }
        if (enabledClubs.isEmpty()) {
            return mapOf("status" to "NO_CLUBS")
        }
        val lastChecks = enabledClubs.mapNotNull { pollingStatusHolder.current(it.clubId).lastCheck }
        if (lastChecks.isEmpty()) {
            return mapOf("status" to "STALE", "reason" to "No polling cycle has completed yet")
        }
        val mostRecent = lastChecks.max()
        val staleThreshold = MatchPollingScheduler.INTERVAL_MS * 3
        val status = if (Duration.between(mostRecent, Instant.now()).toMillis() <= staleThreshold) "HEALTHY" else "STALE"
        return mapOf(
            "status" to status,
            "mostRecentPollAt" to mostRecent.toString(),
            "monitoredClubCount" to enabledClubs.size,
        )
    }

    private fun buildInfo(): Map<String, Any?> = mapOf(
        "commitSha" to (System.getenv("RAILWAY_GIT_COMMIT_SHA") ?: System.getenv("GIT_COMMIT_SHA")),
        "branch" to System.getenv("RAILWAY_GIT_BRANCH"),
    )
}
