package com.eafc26.discordstats.scheduler

import com.eafc26.discordstats.application.club.DefaultClubProvider
import com.eafc26.discordstats.service.AcquisitionResult
import com.eafc26.discordstats.service.AcquisitionTrigger
import com.eafc26.discordstats.service.MatchAcquisitionService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Drives [MatchAcquisitionService.acquire] on a fixed-rate schedule of 60 seconds.
 *
 * Uses fixed-rate scheduling so that polling occurs at exact one-minute boundaries
 * (e.g., 12:00:00, 12:01:00, 12:02:00) regardless of how long each request takes.
 * A slow request does not shift subsequent polling cycles.
 *
 * Concurrency is controlled internally by [MatchAcquisitionService] via its
 * internal lock. If a cycle is still running when the next minute arrives,
 * that execution is skipped, but the following cycle remains aligned with the
 * original one-minute cadence.
 *
 * Acquisition state is reported through internal state holder, which is
 * updated by [MatchAcquisitionService] at each phase transition.
 *
 * First execution runs immediately after Spring startup (initialDelay = 0).
 * 
 * DISABLED when replay mode is active (app.replay.enabled=true).
 */
@Component
@ConditionalOnProperty(
    prefix = "app.replay",
    name = ["enabled"],
    havingValue = "false",
    matchIfMissing = true
)
class MatchPollingScheduler(
    private val acquisitionService: MatchAcquisitionService,
    private val statusHolder: PollingStatusHolder,
    private val defaultClubProvider: DefaultClubProvider,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val INTERVAL_MS = 60_000L  // 1 minute
    }

    init {
        statusHolder.update(defaultClubProvider.get().clubId) {
            it.copy(enabled = true, intervalSeconds = (INTERVAL_MS / 1000).toInt())
        }
    }

    @Scheduled(fixedRate = INTERVAL_MS, initialDelay = 0)
    fun poll() {
        val clubId = defaultClubProvider.get().clubId
        val now = Instant.now()
        statusHolder.update(clubId) {
            it.copy(lastCheck = now, nextCheck = now.plusMillis(INTERVAL_MS), running = true)
        }

        log.debug("Scheduler poll triggered")

        val result = acquisitionService.acquire(clubId, AcquisitionTrigger.SCHEDULER)

        // Update legacy status holder based on result
        when (result) {
            is AcquisitionResult.Busy -> {
                log.info("Scheduled poll skipped — another execution in progress")
                statusHolder.update(clubId) { it.copy(lastResult = "Uma verificação já está em andamento.") }
            }
            is AcquisitionResult.Processed -> {
                val lastResult = when {
                    result.baselineEstablished -> "Histórico inicial configurado. Aguardando novas partidas."
                    result.published.isNotEmpty() -> "Partida enviada com sucesso."
                    result.allSkipped() -> "Nenhuma partida nova."
                    result.failed.isNotEmpty() -> "EA indisponível. Nova tentativa em 1 minuto."
                    else -> "Nenhuma partida nova."
                }
                statusHolder.update(clubId) { it.copy(lastResult = lastResult) }
            }
            is AcquisitionResult.NoMatches -> {
                statusHolder.update(clubId) { it.copy(lastResult = "Nenhuma partida nova.") }
            }
            is AcquisitionResult.EaUnavailable -> {
                statusHolder.update(clubId) { it.copy(lastResult = "EA indisponível. Nova tentativa em 1 minuto.") }
            }
            is AcquisitionResult.WebhookNotConfigured -> {
                statusHolder.update(clubId) { it.copy(lastResult = "Webhook não configurado.") }
            }
            is AcquisitionResult.ForceResent -> {
                statusHolder.update(clubId) { it.copy(lastResult = "Partida reenviada com sucesso.") }
            }
        }

        // The 'running' field is now derived from AcquisitionStateHolder in PollingStatusController
        statusHolder.update(clubId) { it.copy(running = false) }
    }
}
