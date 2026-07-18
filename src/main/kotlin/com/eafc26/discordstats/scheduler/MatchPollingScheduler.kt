package com.eafc26.discordstats.scheduler

import com.eafc26.discordstats.service.MatchNotifierService
import com.eafc26.discordstats.service.NotifyLatestService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Drives [MatchNotifierService.process] on a fixed-rate schedule of 60 seconds.
 *
 * Uses fixed-rate scheduling so that polling occurs at exact one-minute boundaries
 * (e.g., 12:00:00, 12:01:00, 12:02:00) regardless of how long each request takes.
 * A slow request does not shift subsequent polling cycles.
 *
 * Concurrency is controlled by [NotifyLatestService.runIfIdle], which is the same
 * guard used by the manual web button. If a cycle is still running when the next
 * minute arrives, that execution is skipped, but the following cycle remains
 * aligned with the original one-minute cadence.
 *
 * First execution runs immediately after Spring startup (initialDelay = 0).
 */
@Component
@ConditionalOnProperty(name = ["app.polling.enabled"], havingValue = "true", matchIfMissing = true)
class MatchPollingScheduler(
    private val matchNotifierService: MatchNotifierService,
    private val notifyLatestService: NotifyLatestService,
    private val statusHolder: PollingStatusHolder,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val INTERVAL_MS = 60_000L  // 1 minute
    }

    init {
        statusHolder.enabled = true
        statusHolder.intervalSeconds = (INTERVAL_MS / 1000).toInt()
    }

    @Scheduled(fixedRate = INTERVAL_MS, initialDelay = 0)
    fun poll() {
        val now = Instant.now()
        statusHolder.lastCheck = now
        statusHolder.nextCheck = now.plusMillis(INTERVAL_MS)

        val ran = notifyLatestService.runIfIdle {
            statusHolder.running = true
            statusHolder.lastResult = "Consultando a EA..."
            try {
                matchNotifierService.process { msg ->
                    statusHolder.lastResult = msg
                }
            } catch (ex: Exception) {
                log.error("Unhandled error in poll cycle", ex)
                statusHolder.lastResult = "EA indisponível. Nova tentativa em 1 minuto."
            } finally {
                statusHolder.running = false
            }
        }

        if (!ran) {
            log.info("Scheduled poll skipped — another execution in progress")
            statusHolder.lastResult = "Uma verificação já está em andamento."
        }
    }
}
