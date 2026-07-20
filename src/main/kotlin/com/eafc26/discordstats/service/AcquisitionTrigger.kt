package com.eafc26.discordstats.service

/**
 * Identifies the origin of an acquisition request.
 *
 * The trigger determines processing behavior:
 * - [SCHEDULER]: Processes all new matches since last check
 * - [MANUAL]: Processes only the latest match
 * - [CLI]: Processes only the latest match
 * - [DEV_SIMULATOR]: Processes all matches but is web-only (no Discord delivery)
 * - [FORCE_RESEND]: Resends latest match, bypassing deduplication
 *
 * Delivery and persistence policies are centralized here to avoid scattered
 * `if (trigger == DEV_SIMULATOR)` checks throughout the codebase.
 */
enum class AcquisitionTrigger {
    SCHEDULER,
    MANUAL,
    CLI,
    DEV_SIMULATOR,
    FORCE_RESEND;

    /**
     * Returns true if this trigger should deliver matches to Discord.
     *
     * [DEV_SIMULATOR] is web-only and never delivers to Discord.
     */
    fun shouldDeliverToDiscord(): Boolean = this != DEV_SIMULATOR

    /**
     * Returns true if this trigger should persist published match IDs.
     *
     * [DEV_SIMULATOR] does not persist to avoid corrupting production state.
     * [FORCE_RESEND] does not persist because it's a resend, not a new publish.
     */
    fun shouldPersist(): Boolean = when (this) {
        DEV_SIMULATOR -> false
        FORCE_RESEND -> false
        else -> true
    }

    /**
     * Returns true if matches from this trigger should be marked as simulated.
     */
    fun isSimulated(): Boolean = this == DEV_SIMULATOR
}
