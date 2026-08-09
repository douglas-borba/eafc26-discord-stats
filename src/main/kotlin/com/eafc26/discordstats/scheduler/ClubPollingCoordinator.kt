package com.eafc26.discordstats.scheduler

import com.eafc26.discordstats.application.club.MonitoredClubRepository
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.service.AcquisitionResult
import com.eafc26.discordstats.service.AcquisitionTrigger
import com.eafc26.discordstats.service.MatchAcquisitionService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant

/** Sequentially coordinates one automatic acquisition cycle for every enabled club. */
@Component
class ClubPollingCoordinator(
    private val monitoredClubs: MonitoredClubRepository,
    private val acquisitionService: MatchAcquisitionService,
    private val statusHolder: PollingStatusHolder,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun pollEnabledClubs(intervalMs: Long): ClubPollingCycleResult {
        val registered = monitoredClubs.findAll().sortedBy { it.clubId.value }
        registered.filterNot { it.monitoringEnabled }.forEach { club ->
            statusHolder.update(club.clubId) {
                it.copy(enabled = false, running = false, nextCheck = null)
            }
        }
        val clubs = registered.filter { it.monitoringEnabled }

        log.info("Club polling cycle started: enabledClubs={}", clubs.size)
        val results = clubs.map { club -> pollClub(club.clubId, intervalMs) }
        log.info(
            "Club polling cycle completed: processed={}, failed={}",
            results.size,
            results.count { it.failed },
        )
        return ClubPollingCycleResult(results)
    }

    private fun pollClub(clubId: ClubId, intervalMs: Long): ClubPollingResult {
        val startedAt = Instant.now(clock)
        statusHolder.update(clubId) {
            it.copy(
                enabled = true,
                intervalSeconds = (intervalMs / 1000).toInt(),
                lastCheck = startedAt,
                nextCheck = startedAt.plusMillis(intervalMs),
                running = true,
            )
        }
        log.info("Club polling started: clubId={}", clubId.value)

        return try {
            val result = acquisitionService.acquire(clubId, AcquisitionTrigger.SCHEDULER)
            statusHolder.update(clubId) { it.copy(lastResult = statusMessage(result), running = false) }
            log.info("Club polling completed: clubId={}, result={}", clubId.value, result::class.simpleName)
            ClubPollingResult(clubId, result, failed = result.isFailure())
        } catch (ex: Exception) {
            statusHolder.update(clubId) {
                it.copy(lastResult = "Falha inesperada. Nova tentativa em 1 minuto.", running = false)
            }
            log.error("Club polling failed: clubId={}, errorType={}", clubId.value, ex::class.simpleName)
            ClubPollingResult(clubId, result = null, failed = true)
        }
    }

    private fun statusMessage(result: AcquisitionResult): String = when (result) {
        is AcquisitionResult.Busy -> "Uma verificação já está em andamento."
        is AcquisitionResult.Processed -> when {
            result.baselineEstablished -> "Histórico inicial configurado. Aguardando novas partidas."
            result.published.isNotEmpty() -> "Partida enviada com sucesso."
            result.allSkipped() -> "Nenhuma partida nova."
            result.failed.isNotEmpty() -> "EA indisponível. Nova tentativa em 1 minuto."
            else -> "Nenhuma partida nova."
        }
        is AcquisitionResult.NoMatches -> "Nenhuma partida nova."
        is AcquisitionResult.EaUnavailable -> "EA indisponível. Nova tentativa em 1 minuto."
        is AcquisitionResult.WebhookNotConfigured -> "Webhook não configurado; aquisição concluída normalmente."
        is AcquisitionResult.ForceResent -> "Partida reenviada com sucesso."
    }

    private fun AcquisitionResult.isFailure(): Boolean =
        this is AcquisitionResult.EaUnavailable ||
            this is AcquisitionResult.Processed && failed.isNotEmpty()
}

data class ClubPollingCycleResult(val clubs: List<ClubPollingResult>)

data class ClubPollingResult(
    val clubId: ClubId,
    val result: AcquisitionResult?,
    val failed: Boolean,
)
