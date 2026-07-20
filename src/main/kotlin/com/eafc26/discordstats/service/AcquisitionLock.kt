package com.eafc26.discordstats.service

import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Internal concurrency guard for match acquisition.
 *
 * This class is an implementation detail of [MatchAcquisitionService].
 * External callers (scheduler, CLI, manual endpoint, simulator) must never
 * interact with this lock directly — they call MatchAcquisitionService.acquire()
 * which internally uses this lock.
 *
 * The lock ensures that only one acquisition can run at a time across all
 * entry points, preventing duplicate EA fetches and duplicate Discord deliveries.
 */
internal class AcquisitionLock {

    private val log = LoggerFactory.getLogger(javaClass)
    private val busy = AtomicBoolean(false)

    /**
     * Attempts to acquire the lock and run [action].
     *
     * @return The result of [action] if the lock was acquired, or `null` if
     *         another acquisition is already in progress.
     */
    fun <T> tryRun(action: () -> T): T? {
        if (!busy.compareAndSet(false, true)) {
            log.debug("Acquisition skipped — another execution is already in progress")
            return null
        }
        return try {
            action()
        } finally {
            busy.set(false)
        }
    }

    /**
     * Returns true if an acquisition is currently in progress.
     * This is for status reporting only — do not use for synchronization decisions.
     */
    fun isBusy(): Boolean = busy.get()
}

