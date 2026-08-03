package com.eafc26.discordstats.store

/**
 * Persistent record of a single match's Discord delivery state.
 *
 * The state machine follows a Write-Ahead Log (WAL) pattern:
 *
 * ```
 *  (not in store)
 *       │
 *       ▼  lock + write before HTTP
 *   DELIVERING ──── crash / die ──► DELIVERY_UNCERTAIN (after restart)
 *       │
 *       │  HTTP 2xx confirmed
 *       ▼
 *   DELIVERED
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
 */
data class PublicationRecord(
    val matchId: String,
    val state: PublicationState,
    /** Epoch-seconds of the last state write. */
    val updatedAt: Long = java.time.Instant.now().epochSecond,
)

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
}

