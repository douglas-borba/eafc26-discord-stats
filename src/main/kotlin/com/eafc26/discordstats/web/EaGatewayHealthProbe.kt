package com.eafc26.discordstats.web

import com.eafc26.discordstats.config.AppProperties
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration

/**
 * Bounded, non-retrying probe for the EA Gateway used by the administrative
 * aggregate health endpoint. It is intentionally separate from acquisition:
 * acquisition is allowed to retry, while health must always return promptly.
 */
@Component
class EaGatewayHealthProbe(
    @Qualifier("eaGatewayHealthWebClient") private val webClient: WebClient,
    private val props: AppProperties,
) {
    fun check(): Map<String, Any> {
        val startedAt = System.nanoTime()
        return try {
            val statusCode = webClient.get()
                .uri { builder ->
                    builder.path("/ea/clubs/search")
                        .queryParam("name", "__health_check_ping__")
                        .queryParam("platform", props.ea.platform)
                        .build()
                }
                .exchangeToMono { response -> response.releaseBody().thenReturn(response.statusCode().value()) }
                .block(PROBE_TIMEOUT)
                ?: return down("No response")

            val latencyMs = elapsedMillis(startedAt)
            if (statusCode in 200..299) {
                mapOf("status" to "UP", "latencyMs" to latencyMs)
            } else {
                mapOf(
                    "status" to "DOWN",
                    "latencyMs" to latencyMs,
                    "statusCode" to statusCode,
                    "message" to "EA Gateway returned HTTP $statusCode",
                )
            }
        } catch (ex: Exception) {
            down(ex.javaClass.simpleName, elapsedMillis(startedAt))
        }
    }

    private fun down(error: String, latencyMs: Long = PROBE_TIMEOUT.toMillis()): Map<String, Any> = mapOf(
        "status" to "DOWN",
        "latencyMs" to latencyMs,
        "error" to error,
    )

    private fun elapsedMillis(startedAt: Long): Long = Duration.ofNanos(System.nanoTime() - startedAt).toMillis()

    private companion object {
        val PROBE_TIMEOUT: Duration = Duration.ofMillis(2_500)
    }
}
