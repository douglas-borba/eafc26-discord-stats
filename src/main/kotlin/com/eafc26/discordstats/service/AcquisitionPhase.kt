package com.eafc26.discordstats.service

/**
 * Represents the phases of an acquisition pipeline execution.
 *
 * Phases are executed in order during a successful acquisition:
 * IDLE → FETCHING → PROCESSING → CACHING → DELIVERING → PERSISTING → COMPLETED
 *
 * A failure at any phase transitions directly to FAILED.
 * When no acquisition is in progress, the phase is IDLE.
 */
enum class AcquisitionPhase {
    /** No acquisition in progress. */
    IDLE,

    /** Fetching match data from EA API. */
    FETCHING,

    /** Processing matches: deduplication, match selection. */
    PROCESSING,

    /** Caching presentation in LatestMatchHolder. */
    CACHING,

    /** Delivering to Discord webhooks. */
    DELIVERING,

    /** Persisting published match IDs to store. */
    PERSISTING,

    /** Acquisition completed successfully. */
    COMPLETED,

    /** Acquisition failed at some phase. */
    FAILED,
}

