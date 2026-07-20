package com.eafc26.discordstats.scheduler

import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Holds the status of the polling scheduler.
 *
 * The [enabled] flag defaults to `true` because the scheduler is enabled by default
 * (via `@ConditionalOnProperty` with `matchIfMissing = true`). If the scheduler bean
 * is not created (explicitly disabled), the flag will remain `true` but no polling
 * will actually occur.
 *
 * This class is intentionally simple and does not track pause/resume state since
 * the scheduler cannot be paused at runtime — it's either enabled at startup or not.
 */
@Component
class PollingStatusHolder {
    /**
     * Whether the scheduler is enabled.
     * Defaults to `true` because polling is enabled by default.
     * Set to `true` explicitly by [MatchPollingScheduler] on initialization.
     */
    @Volatile var enabled: Boolean = true
    
    @Volatile var intervalSeconds: Int = 60  // 1 minute default
    @Volatile var running: Boolean = false
    @Volatile var lastCheck: Instant? = null
    @Volatile var nextCheck: Instant? = null
    @Volatile var lastResult: String = "Aguardando primeira verificação..."
}
