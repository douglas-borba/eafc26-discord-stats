package com.eafc26.discordstats.scheduler

import com.eafc26.discordstats.service.PostgresSyncService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicBoolean

@Component
@ConditionalOnProperty(
    name = ["app.postgres.mirror-enabled", "app.postgres.sync-enabled"],
    havingValue = "true",
)
class PostgresSyncScheduler(
    private val syncService: PostgresSyncService,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val running = AtomicBoolean(false)

    @Scheduled(
        fixedRateString = "\${app.postgres.sync-interval-ms:300000}",
        initialDelayString = "\${app.postgres.sync-interval-ms:300000}",
    )
    fun sync() {
        if (!running.compareAndSet(false, true)) {
            log.debug("PostgreSQL sync skipped — previous cycle still running")
            return
        }
        try {
            syncService.sync()
        } finally {
            running.set(false)
        }
    }
}
