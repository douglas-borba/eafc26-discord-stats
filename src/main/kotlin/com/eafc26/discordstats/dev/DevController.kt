package com.eafc26.discordstats.dev

import com.eafc26.discordstats.service.AcquisitionResult
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

/**
 * REST controller for development simulator operations.
 *
 * These endpoints are always available but require Development Mode to be
 * enabled through the dashboard settings. When disabled, requests will
 * receive a 403 Forbidden response.
 *
 * ## Endpoints
 *
 * - `POST /api/dev/simulate/latest` - Trigger a simulated acquisition (web-only)
 * - `POST /api/dev/reprocess` - Reprocess the fixture (same as simulate, no Discord)
 * - `POST /api/dev/reset` - Reset simulator state
 *
 * Note: The simulator NEVER sends to Discord. All operations are web-only.
 */
@RestController
@RequestMapping("/api/dev")
class DevController(
    private val simulatorService: DevSimulatorService,
) {

    /**
     * Handles DevelopmentModeDisabledException and returns 403 Forbidden.
     */
    @ExceptionHandler(DevelopmentModeDisabledException::class)
    fun handleDevModeDisabled(ex: DevelopmentModeDisabledException): ResponseEntity<DevSimulatorResponse> =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(
            DevSimulatorResponse(
                status = "disabled",
                message = "Modo de desenvolvimento desativado",
                action = "Rejected",
            )
        )

    /**
     * Simulates a match acquisition using fixture data (web-only).
     *
     * This triggers the acquisition pipeline (FETCHING → PROCESSING → CACHING)
     * using data from fixture files.
     *
     * Important: No Discord delivery occurs. The simulation is web-only.
     * The match card is generated and cached for viewing in the dashboard.
     */
    @PostMapping("/simulate/latest", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun simulateLatest(): Mono<ResponseEntity<DevSimulatorResponse>> =
        Mono.fromCallable { simulatorService.simulateLatest() }
            .subscribeOn(Schedulers.boundedElastic())
            .map { result -> toResponse(result, "Simulate") }

    /**
     * Reprocesses the fixture data (web-only).
     *
     * This is equivalent to simulate/latest - it reprocesses the fixture
     * without sending to Discord.
     */
    @PostMapping("/reprocess", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun reprocess(): Mono<ResponseEntity<DevSimulatorResponse>> =
        Mono.fromCallable { simulatorService.simulateLatest() }
            .subscribeOn(Schedulers.boundedElastic())
            .map { result -> toResponse(result, "Reprocess") }

    /**
     * Resets the simulator state.
     *
     * Clears cached presentation and resets gateway settings.
     * After reset, the next simulation behaves as a fresh start.
     */
    @PostMapping("/reset", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun reset(): ResponseEntity<DevSimulatorResponse> {
        simulatorService.reset()
        return ResponseEntity.ok(
            DevSimulatorResponse(
                status = "success",
                message = "Estado do simulador resetado",
                action = "Reset",
            )
        )
    }

    private fun toResponse(result: AcquisitionResult, action: String): ResponseEntity<DevSimulatorResponse> {
        val (status, message) = when (result) {
            is AcquisitionResult.Processed -> {
                when {
                    result.simulated -> "simulated" to "Simulação concluída. Card disponível na interface."
                    result.baselineEstablished -> "success" to "Baseline established"
                    result.published.isNotEmpty() -> "success" to "Published ${result.published.size} match(es)"
                    result.alreadyPublished.isNotEmpty() -> "skipped" to "Skipped ${result.alreadyPublished.size} already published match(es)"
                    else -> "success" to "No new matches"
                }
            }
            is AcquisitionResult.ForceResent -> "success" to "Match reprocessed"
            AcquisitionResult.NoMatches -> "no_matches" to "Nenhuma partida encontrada no fixture"
            AcquisitionResult.Busy -> "busy" to "Outra aquisição em andamento"
            AcquisitionResult.WebhookNotConfigured -> "simulated" to "Simulação concluída (webhook não configurado)"
            is AcquisitionResult.EaUnavailable -> "error" to "Erro ao carregar fixture: ${result.message}"
        }

        return ResponseEntity.ok(
            DevSimulatorResponse(
                status = status,
                message = message,
                action = action,
            )
        )
    }
}

/**
 * Response model for simulator operations.
 */
data class DevSimulatorResponse(
    val status: String,
    val message: String,
    val action: String,
)

