package com.eafc26.discordstats.scheduler

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Triggers one sequential multi-club polling cycle every 60 seconds.
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
    private val coordinator: ClubPollingCoordinator,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val INTERVAL_MS = 60_000L  // 1 minute
    }

    @Scheduled(fixedRate = INTERVAL_MS, initialDelay = 0)
    fun poll() {
        log.debug("Scheduler cycle triggered")
        coordinator.pollEnabledClubs(INTERVAL_MS)
    }
}
