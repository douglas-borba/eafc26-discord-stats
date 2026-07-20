package com.eafc26.discordstats.service

/**
 * Domain model representing the outcome of a match acquisition.
 *
 * This sealed class models acquisition outcomes around the domain,
 * independent of transport-specific concerns (REST, CLI, etc.).
 * Controllers and CLI map these results to their respective formats.
 */
sealed class AcquisitionResult {

    /**
     * Acquisition completed and processed matches.
     *
     * This result covers multiple scenarios:
     * - New matches published successfully
     * - All matches already published (nothing new)
     * - Partial success (some published, some failed)
     * - First-run baseline establishment
     * - Simulated match (web-only, no Discord delivery)
     *
     * @property published Matches successfully sent to Discord
     * @property alreadyPublished Matches skipped because they were already sent
     * @property failed Matches that could not be delivered to Discord
     * @property baselineEstablished True if this was a first-run that established the baseline
     * @property simulated True if this was a development simulation (no Discord delivery)
     * @property simulatedMatch The simulated match summary (only set when simulated is true)
     */
    data class Processed(
        val published: List<MatchSummary>,
        val alreadyPublished: List<MatchSummary>,
        val failed: List<MatchFailure>,
        val baselineEstablished: Boolean = false,
        val simulated: Boolean = false,
        val simulatedMatch: MatchSummary? = null,
    ) : AcquisitionResult() {

        /** True if at least one match was published. */
        fun hasPublished(): Boolean = published.isNotEmpty()

        /** True if all matches were skipped (already published). */
        fun allSkipped(): Boolean =
            published.isEmpty() && failed.isEmpty() && alreadyPublished.isNotEmpty() && !simulated

        /** Returns the latest match summary, preferring published over skipped over simulated. */
        fun latestSummary(): String? =
            published.lastOrNull()?.summary 
                ?: alreadyPublished.lastOrNull()?.summary 
                ?: simulatedMatch?.summary
    }

    /**
     * Force resend completed successfully.
     *
     * This result is returned when a match is resent bypassing deduplication.
     * The match ID is not persisted to the published store.
     */
    data class ForceResent(
        val match: MatchSummary,
    ) : AcquisitionResult()

    /**
     * No matches found from EA API.
     */
    object NoMatches : AcquisitionResult()

    /**
     * EA API is unavailable or returned an error.
     */
    data class EaUnavailable(
        val statusCode: Int,
        val message: String,
    ) : AcquisitionResult()

    /**
     * Discord webhook is not configured.
     *
     * Acquisition cannot proceed without a webhook URL.
     */
    object WebhookNotConfigured : AcquisitionResult()

    /**
     * Another acquisition is already in progress.
     *
     * The caller should retry later or inform the user.
     */
    object Busy : AcquisitionResult()

    // -------------------------------------------------------------------------
    // Supporting types
    // -------------------------------------------------------------------------

    /**
     * Summary of a match that was processed.
     *
     * @property matchId Unique identifier from EA API
     * @property summary Human-readable summary (e.g., "Team A 2 × 1 Team B")
     * @property persistedSuccessfully True if the match ID was saved to the store
     */
    data class MatchSummary(
        val matchId: String,
        val summary: String,
        val persistedSuccessfully: Boolean = true,
    )

    /**
     * Details of a match that failed to be delivered.
     *
     * @property matchId Unique identifier from EA API
     * @property summary Human-readable summary
     * @property reason Why the delivery failed
     */
    data class MatchFailure(
        val matchId: String,
        val summary: String,
        val reason: String,
    )
}

