package com.eafc26.discordstats.store

/**
 * Persistent record of a single match's Discord delivery state.
 *
 * The state machine follows a Write-Ahead Log (WAL) pattern:
 *
 * ```
 *  PENDING ──or── FAILED_TRANSIENT (after its bounded retry delay)
 *       │
 *       ▼  lock + write before HTTP
 *   DELIVERING ──── crash / die ──► DELIVERY_UNCERTAIN (after restart)
 *       │
 *       ├── HTTP 2xx ──► DELIVERED
 *       ├── HTTP 404/410/4xx permanent ──► FAILED_PERMANENT
 *       ├── HTTP 429/5xx transient ──► FAILED_TRANSIENT ──► RETRY_EXHAUSTED (after attempt 5)
 *       │                                             │
 *       │                                             └──► delayed reconciliation retry
 *       └── network error (ambiguous) ──► DELIVERY_UNCERTAIN
 * ```
 *
 * State semantics:
 * - [PublicationState.PENDING]: A canonical match is durably marked for automatic
 *   publication before the fast-path delivery begins. No Discord request has been
 *   attempted yet, so a recovery worker may safely claim it.
 * - [PublicationState.DELIVERING]: Written to disk BEFORE the Discord HTTP call starts.
 *   If this state is found on startup it means the process was killed during the delivery
 *   window. On startup all DELIVERING records are upgraded to [PublicationState.DELIVERY_UNCERTAIN].
 * - [PublicationState.DELIVERED]: Discord returned HTTP 2xx AND the state was atomically
 *   persisted after the response. This is the only terminal-success state.
 * - [PublicationState.DELIVERY_UNCERTAIN]: The send outcome cannot be proven, either after
 *   a network failure or during startup recovery of a DELIVERING record. The message MAY or
 *   MAY NOT have reached Discord. Automatic resend is permanently blocked until explicit
 *   administrative resolution. Its preserved diagnostic uses [DeliveryUncertaintyReason].
 * - [PublicationState.FAILED_PERMANENT]: Discord explicitly rejected the request with a
 *   definitive error (404, 401, 403, 400, 413). The message was NOT delivered.
 * - [PublicationState.FAILED_TRANSIENT]: Discord explicitly proved non-delivery, so the
 *   bounded automatic retry policy may schedule another attempt.
 * - [PublicationState.RETRY_EXHAUSTED]: The bounded *immediate* retry budget ended without a
 *   confirmed delivery. The record is parked until its persisted, low-frequency automatic
 *   recovery time. It remains eligible only for explicit, proven non-delivery failures.
 */
data class PublicationRecord(
    val matchId: String,
    val state: PublicationState,
    /** Epoch-seconds of the last state write. */
    val updatedAt: Long = java.time.Instant.now().epochSecond,
    /** Number of delivery attempts (for audit). */
    val attemptCount: Int = 0,
    /** Epoch-seconds of the last delivery attempt. */
    val lastAttemptAt: Long? = null,
    /** Last error message (sanitized, no stack traces). */
    val lastError: String? = null,
    /** Last HTTP status code received (if any). */
    val lastHttpStatus: Int? = null,
    /** Why this match was baselined (only meaningful when state == BASELINED). */
    val baselineReason: BaselineReason? = null,
    /** Epoch-seconds after which an automatic reconciliation may claim this record again. */
    val nextAutomaticAttemptAt: Long? = null,
    /** Number of low-frequency recovery attempts made after the immediate retry budget. */
    val recoveryAttemptCount: Int = 0,
)

/**
 * Classified reason persisted inside [PublicationRecord.lastError] while a delivery is
 * uncertain. The database schema intentionally keeps the existing sanitized diagnostic
 * field; the prefix makes the reason stable and machine-readable without inventing a new
 * persistence model before durable publication work is designed.
 */
enum class DeliveryUncertaintyReason {
    NETWORK_TIMEOUT,
    NETWORK_EXCEPTION,
    STARTUP_RECOVERY,
    DELIVERED_STATE_PERSISTENCE_FAILURE,
    UNKNOWN,
    ;

    fun diagnosticMessage(detail: String?): String =
        buildString {
            append(name)
            detail?.takeIf { it.isNotBlank() }?.let {
                append(": ")
                append(it)
            }
        }
}

/**
 * Origin recorded in immutable operational events. It is deliberately not stored as the
 * current publication state: a manual resend must not erase the previous automatic attempt.
 */
enum class DiscordPublicationOrigin {
    AUTOMATIC_ACQUISITION,
    AUTOMATIC_RECONCILIATION,
    FORCE_PUBLISH,
    STARTUP_RECOVERY,
}

enum class BaselineReason {
    FIRST_RUN,
    NO_DESTINATION,
}

enum class PublicationState {
    /** Durable, unattempted intention to publish. Safe for automatic processing. */
    PENDING,

    /** Pre-send write-ahead marker. Persisted BEFORE the HTTP call. */
    DELIVERING,

    /** HTTP 2xx confirmed and persisted. Terminal success state. */
    DELIVERED,

    /**
     * Post-restart upgrade from DELIVERING.
     * Delivery outcome is unknown. Automatic resend blocked.
     * Requires administrative resolution.
     */
    DELIVERY_UNCERTAIN,

    /**
     * Discord explicitly rejected the request (404, 401, 403, 400, 413).
     * The message definitively was NOT delivered.
     * Requires correction before manual resend.
     */
    FAILED_PERMANENT,

    /**
     * Delivery failed with a transient error (429, 5xx, pre-send persistence).
     * The message was NOT delivered. Automatic retry is allowed on the next cycle.
     * Attempt metadata (count, error, HTTP status) is preserved for diagnostics.
     */
    FAILED_TRANSIENT,

    /**
     * The configured budget for safe automatic attempts was exhausted. This is neither
     * an ambiguous delivery nor an explicit permanent Discord rejection. It is parked for a
     * persisted, low-frequency automatic reconciliation attempt; manual resend remains an
     * explicit operator override.
     */
    RETRY_EXHAUSTED,

    /**
     * Match is part of the initial baseline (historical window).
     * These matches were known to the system but intentionally NOT published to Discord
     * to avoid flooding with old data.
     * 
     * Semantics: "Known by the system, deliberately not sent."
     * 
     * This state allows the system to:
     * - Track that the match was never published (unlike DELIVERED)
     * - Prevent automatic publication (deduplication)
     * - Enable future selective publication of historical matches
     */
    BASELINED,
}

/**
 * Retry policy for failures for which Discord explicitly proved that delivery did not
 * happen. [PublicationRecord.attemptCount] is incremented immediately before every
 * HTTP attempt; after attempts 1–4 fail, the corresponding delay applies before the
 * next attempt. A fifth failed automatic attempt moves the record to RETRY_EXHAUSTED, which
 * is then retried by the reconciler with a deliberately slower persisted cadence.
 */
object PublicationRetryPolicy {
    const val MAX_AUTOMATIC_ATTEMPTS = 5

    fun delayAfter(attemptCount: Int): java.time.Duration? = when (attemptCount) {
        1 -> java.time.Duration.ofMinutes(1)
        2 -> java.time.Duration.ofMinutes(2)
        3 -> java.time.Duration.ofMinutes(5)
        4 -> java.time.Duration.ofMinutes(15)
        else -> null
    }

    fun nextRetryAt(record: PublicationRecord): java.time.Instant? {
        if (record.state != PublicationState.FAILED_TRANSIENT) return null
        record.nextAutomaticAttemptAt?.let { return java.time.Instant.ofEpochSecond(it) }
        val lastAttemptAt = record.lastAttemptAt ?: return null
        val delay = delayAfter(record.attemptCount) ?: return null
        return java.time.Instant.ofEpochSecond(lastAttemptAt).plus(delay)
    }

    /**
     * Recovery starts well after the four short retries (1, 2, 5 and 15 minutes), then slows
     * down to a maximum of one attempt per twelve hours. This prevents a Discord incident from
     * becoming a permanent tight loop while still recovering without operator intervention.
     */
    fun recoveryDelayAfter(recoveryAttemptCount: Int): java.time.Duration = when (recoveryAttemptCount) {
        0 -> java.time.Duration.ofMinutes(30)
        1 -> java.time.Duration.ofHours(1)
        2 -> java.time.Duration.ofHours(3)
        3 -> java.time.Duration.ofHours(6)
        else -> java.time.Duration.ofHours(12)
    }

    fun nextRecoveryAt(record: PublicationRecord): java.time.Instant? {
        if (record.state != PublicationState.RETRY_EXHAUSTED) return null
        record.nextAutomaticAttemptAt?.let { return java.time.Instant.ofEpochSecond(it) }
        return null // Pre-V19 exhausted records are intentionally not re-armed in memory.
    }

    fun nextAutomaticAttemptAt(
        state: PublicationState,
        lastAttemptAt: Long,
        attemptCount: Int,
        recoveryAttemptCount: Int,
        retryAfter: java.time.Duration? = null,
    ): java.time.Instant? {
        val policyDelay = when (state) {
            PublicationState.FAILED_TRANSIENT -> delayAfter(attemptCount)
            PublicationState.RETRY_EXHAUSTED -> recoveryDelayAfter(recoveryAttemptCount)
            else -> null
        } ?: return null
        val effectiveDelay = listOfNotNull(policyDelay, retryAfter).maxOrNull() ?: policyDelay
        return java.time.Instant.ofEpochSecond(lastAttemptAt).plus(effectiveDelay)
    }

    fun isRetryExhausted(attemptCount: Int): Boolean = attemptCount >= MAX_AUTOMATIC_ATTEMPTS
}
