package com.eafc26.discordstats.service

/**
 * Identifies the origin of an acquisition request.
 *
 * The trigger determines processing behavior:
 * - [SCHEDULER]: Processes all new matches since last check
 * - [MANUAL]: Processes only the latest match
 * - [CLI]: Processes only the latest match
 * - [DEV_SIMULATOR]: Processes all matches (like scheduler, for testing)
 * - [FORCE_RESEND]: Resends latest match, bypassing deduplication
 */
enum class AcquisitionTrigger {
    SCHEDULER,
    MANUAL,
    CLI,
    DEV_SIMULATOR,
    FORCE_RESEND,
}

