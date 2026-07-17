package com.eafc26.discordstats.scheduler

import com.eafc26.discordstats.service.MatchNotifierService
import com.eafc26.discordstats.service.NotifyLatestService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Drives [MatchNotifierService.process] on a fixed delay of 120 seconds.
 *
 * Concurrency is controlled exclusively by [NotifyLatestService.runIfIdle], which is
 * the same guard used by the manual web button. Manual and automatic executions
 * therefore share a single lock — they can never run simultaneously.
 *
 * First execution runs immediately after Spring startup (no initial delay).
 * Subsequent executions are separated by [intervalMs] from the completion of the
 * previous one (fixed delay, not fixed rate).
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
        private const val intervalMs = 120_000L
    }

    init {
        statusHolder.enabled = true
        statusHolder.intervalSeconds = (intervalMs / 1000).toInt()
    }

    @Scheduled(fixedDelayString = "\${app.polling.interval-ms:120000}", initialDelay = 0)
    fun poll() {
        val now = Instant.now()
        statusHolder.lastCheck = now
        statusHolder.running = true
        statusHolder.lastResult = "Consultando a EA..."

        val ran = notifyLatestService.runIfIdle {
            try {
                matchNotifierService.process { msg ->
                    statusHolder.lastResult = msg
                }
            } catch (ex: Exception) {
                log.error("Unhandled error in poll cycle", ex)
                statusHolder.lastResult = "EA indisponível. Nova tentativa em 2 minutos."
            }
        }

        if (!ran) {
            log.info("Scheduled poll skipped — manual verification in progress")
            statusHolder.lastResult = "Uma verificação já está em andamento."
        }

        statusHolder.running = false
        statusHolder.nextCheck = Instant.now().plusMillis(intervalMs)
    }
}
