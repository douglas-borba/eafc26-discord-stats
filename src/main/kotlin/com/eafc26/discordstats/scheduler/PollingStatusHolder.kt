package com.eafc26.discordstats.scheduler

import com.eafc26.discordstats.domain.match.ClubId
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

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
    private val states = ConcurrentHashMap<ClubId, PollingStatus>()

    fun current(clubId: ClubId): PollingStatus = states[clubId] ?: PollingStatus()

    fun update(clubId: ClubId, transform: (PollingStatus) -> PollingStatus): PollingStatus =
        states.compute(clubId) { _, current -> transform(current ?: PollingStatus()) }!!
}

data class PollingStatus(
    val enabled: Boolean = true,
    val intervalSeconds: Int = 60,
    val running: Boolean = false,
    val lastCheck: Instant? = null,
    val nextCheck: Instant? = null,
    val lastResult: String = "Aguardando primeira verificação...",
)
