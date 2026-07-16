package com.eafc26.discordstats.scheduler

import com.eafc26.discordstats.service.MatchNotifierService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Drives [MatchNotifierService.process] on a fixed delay.
 *
 * The overlap guard uses an [AtomicBoolean] so that if EA or Discord is slow
 * and the previous cycle has not finished, the new firing is skipped rather
 * than stacking up parallel runs. EA or Discord failures inside process() are
 * caught there and never propagate here, keeping the scheduler alive.
 */
@Component
@ConditionalOnProperty(name = ["app.polling.enabled"], havingValue = "true", matchIfMissing = true)
class MatchPollingScheduler(private val service: MatchNotifierService) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val running = AtomicBoolean(false)

    @Scheduled(fixedDelayString = "\${app.polling.interval-ms:300000}")
    fun poll() {
        if (!running.compareAndSet(false, true)) {
            log.warn("Previous poll cycle still running — skipping this firing")
            return
        }
        try {
            service.process()
        } catch (ex: Exception) {
            log.error("Unhandled error in poll cycle", ex)
        } finally {
            running.set(false)
        }
    }
}
