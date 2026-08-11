package com.eafc26.discordstats.store

/**
 * Persistent record of a single match's Discord delivery state.
 *
 * The state machine follows a Write-Ahead Log (WAL) pattern:
 *
 * ```
 *  (not in store) ──or── FAILED_TRANSIENT (retry)
 *       │
 *       ▼  lock + write before HTTP
 *   DELIVERING ──── crash / die ──► DELIVERY_UNCERTAIN (after restart)
 *       │
 *       ├── HTTP 2xx ──► DELIVERED
 *       ├── HTTP 404/410/4xx permanent ──► FAILED_PERMANENT
 *       ├── HTTP 429/5xx transient ──► FAILED_TRANSIENT (auto-retry next cycle)
 *       └── network error (ambiguous) ──► DELIVERY_UNCERTAIN
 * ```
 *
 * State semantics:
 * - [PublicationState.DELIVERING]: Written to disk BEFORE the Discord HTTP call starts.
 *   If this state is found on startup it means the process was killed during the delivery
 *   window. On startup all DELIVERING records are upgraded to [PublicationState.DELIVERY_UNCERTAIN].
 * - [PublicationState.DELIVERED]: Discord returned HTTP 2xx AND the state was atomically
 *   persisted after the response. This is the only terminal-success state.
 * - [PublicationState.DELIVERY_UNCERTAIN]: Post-restart upgrade of a DELIVERING record.
 *   The process died between the pre-send write and the 2xx confirmation. The message
 *   MAY or MAY NOT have reached Discord. Automatic resend is permanently blocked until
 *   explicit administrative resolution.
 * - [PublicationState.FAILED_PERMANENT]: Discord explicitly rejected the request with a
 *   definitive error (404, 401, 403, 400, 413). The message was NOT delivered.
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
)

enum class BaselineReason {
    FIRST_RUN,
    NO_DESTINATION,
}

enum class PublicationState {
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

