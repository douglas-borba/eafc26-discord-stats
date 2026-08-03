package com.eafc26.discordstats.service

import com.eafc26.discordstats.canonical.CanonicalMatch
import com.eafc26.discordstats.discord.DiscordDeliveryException
import com.eafc26.discordstats.discord.DiscordRenderer
import com.eafc26.discordstats.discord.DiscordWebhookClient
import com.eafc26.discordstats.store.PublicationRecord
import com.eafc26.discordstats.store.PublicationState
import com.eafc26.discordstats.store.PublishedMatchStore
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

/**
 * Single authoritative boundary for all Discord match publication.
 *
 * ## Write-Ahead Log (WAL) guarantee
 * Before every HTTP call to Discord, this service persists a [PublicationState.DELIVERING]
 * record to disk. This means:
 *
 * - If the process dies BEFORE the HTTP call: DELIVERING → DELIVERY_UNCERTAIN on restart.
 *   No automatic resend. Administrative resolution required.
 * - If the process dies AFTER HTTP 2xx but BEFORE [PublicationState.DELIVERED] is persisted:
 *   Same as above — DELIVERING → DELIVERY_UNCERTAIN on restart. No automatic resend.
 * - If [PublicationState.DELIVERED] is successfully persisted: deduplication is permanent.
 *
 * ## Delivered-but-uncertain edge case
 * If the Discord HTTP call succeeds but the subsequent [PublicationState.DELIVERED] write
 * fails (e.g., disk full), the record stays in [PublicationState.DELIVERING] on disk.
 * On restart it will become [PublicationState.DELIVERY_UNCERTAIN]. This is intentionally
 * conservative: the message WAS delivered, but we cannot prove it automatically. The
 * caller receives [PublicationOutcome.DELIVERED_BUT_STATE_UNCERTAIN] so the operator
 * is informed.
 *
 * ## Guarantee achieved
 * **At-most-once automatic delivery with administrable ambiguity window.**
 * Exactly-once is not achievable because Discord webhooks offer no idempotency key.
 *
 * ## Thread safety
 * Per-MatchId [ReentrantLock] prevents TOCTOU races within this JVM.
 * Cross-process safety is provided by [SingleInstanceGuard].
 */
@Service
class DiscordMatchPublicationService(
    private val store: PublishedMatchStore,
    private val webhookClient: DiscordWebhookClient,
    private val discordRenderer: DiscordRenderer,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val matchLocks = ConcurrentHashMap<String, ReentrantLock>()

    /**
     * Publishes a match to Discord using the WAL pattern.
     *
     * Flow (under per-MatchId lock):
     * 1. Load [PublishedMatchStore] records
     * 2. DELIVERED → [PublicationOutcome.SKIPPED_ALREADY_DELIVERED] (zero HTTP calls)
     * 3. DELIVERY_UNCERTAIN → [PublicationOutcome.SKIPPED_DELIVERY_UNCERTAIN] (zero HTTP calls)
     * 4. Persist [PublicationState.DELIVERING] → if fails → [PublicationOutcome.FAILED_BEFORE_SEND] (zero HTTP calls)
     * 5. Call Discord webhook
     * 6. On HTTP failure: remove DELIVERING marker → [PublicationOutcome.FAILED_HTTP]
     * 7. On HTTP success: persist [PublicationState.DELIVERED] → [PublicationOutcome.PUBLISHED]
     * 8. If DELIVERED write fails: state remains DELIVERING on disk (→ DELIVERY_UNCERTAIN on restart)
     *    → [PublicationOutcome.DELIVERED_BUT_STATE_UNCERTAIN]
     */
    fun publishIfNeeded(canonical: CanonicalMatch): DiscordPublicationResult {
        val matchId = canonical.matchId.value
        val lock = matchLocks.computeIfAbsent(matchId) { ReentrantLock() }
        lock.lock()
        try {
            // 1. Check existing state
            val existing = store.loadRecords()[matchId]
            when (existing?.state) {
                PublicationState.DELIVERED -> {
                    log.info("Match {} already DELIVERED — skipping", matchId)
                    return DiscordPublicationResult(PublicationOutcome.SKIPPED_ALREADY_DELIVERED, matchId)
                }
                PublicationState.DELIVERY_UNCERTAIN -> {
                    log.warn(
                        "Match {} is DELIVERY_UNCERTAIN — blocking automatic resend. " +
                            "Administrative resolution required (resolveAsDelivered / resolveAsUndelivered).",
                        matchId,
                    )
                    return DiscordPublicationResult(PublicationOutcome.SKIPPED_DELIVERY_UNCERTAIN, matchId)
                }
                PublicationState.DELIVERING -> {
                    // Should not happen within a live session (we hold the lock).
                    // Treat defensively as uncertain to prevent duplicate.
                    log.warn("Match {} found in DELIVERING state within session — treating as DELIVERY_UNCERTAIN", matchId)
                    return DiscordPublicationResult(PublicationOutcome.SKIPPED_DELIVERY_UNCERTAIN, matchId)
                }
                null -> { /* not in store → proceed */ }
            }

            // 2. WAL: persist DELIVERING BEFORE HTTP
            try {
                store.saveRecord(PublicationRecord(matchId, PublicationState.DELIVERING))
            } catch (ex: Exception) {
                log.error(
                    "Cannot persist DELIVERING state for match {} — aborting to prevent ambiguous delivery. Error: {}",
                    matchId, ex.message,
                )
                return DiscordPublicationResult(PublicationOutcome.FAILED_BEFORE_SEND, matchId,
                    errorMessage = "Pre-send persistence failed: ${ex.message}")
            }

            // 3. Call Discord
            val sendError = trySend(canonical, matchId)
            if (sendError != null) {
                // HTTP definitively failed (exception thrown) → remove DELIVERING marker
                safeRemoveDelivering(matchId)
                return DiscordPublicationResult(sendError.first, matchId, errorMessage = sendError.second)
            }

            // 4. HTTP 2xx — persist DELIVERED
            return try {
                store.saveRecord(PublicationRecord(matchId, PublicationState.DELIVERED))
                log.info("Published and DELIVERED match {}", matchId)
                DiscordPublicationResult(PublicationOutcome.PUBLISHED, matchId)
            } catch (ex: Exception) {
                log.error(
                    "Match {} was delivered to Discord (HTTP 2xx) but DELIVERED write failed. " +
                        "State remains DELIVERING on disk → will become DELIVERY_UNCERTAIN after restart. " +
                        "Error: {}",
                    matchId, ex.message,
                )
                DiscordPublicationResult(
                    PublicationOutcome.DELIVERED_BUT_STATE_UNCERTAIN, matchId,
                    errorMessage = "HTTP succeeded but DELIVERED write failed: ${ex.message}",
                )
            }
        } finally {
            lock.unlock()
        }
    }

    /**
     * Force-publishes a match, bypassing the deduplication check.
     *
     * Intended for administrative use only. Persists DELIVERING before HTTP and
     * DELIVERED after HTTP 2xx, so the scheduler will not re-publish it automatically.
     */
    fun forcePublish(canonical: CanonicalMatch): DiscordPublicationResult {
        val matchId = canonical.matchId.value

        // WAL: persist DELIVERING before HTTP (even for force-resend)
        try {
            store.saveRecord(PublicationRecord(matchId, PublicationState.DELIVERING))
        } catch (ex: Exception) {
            log.error("Cannot persist DELIVERING for force-resend of match {}: {}", matchId, ex.message)
            return DiscordPublicationResult(PublicationOutcome.FAILED_BEFORE_SEND, matchId,
                errorMessage = "Pre-send persistence failed: ${ex.message}")
        }

        val sendError = trySend(canonical, matchId)
        if (sendError != null) {
            safeRemoveDelivering(matchId)
            return DiscordPublicationResult(sendError.first, matchId, errorMessage = sendError.second)
        }

        return try {
            store.saveRecord(PublicationRecord(matchId, PublicationState.DELIVERED))
            log.info("Force-published and DELIVERED match {}", matchId)
            DiscordPublicationResult(PublicationOutcome.PUBLISHED, matchId)
        } catch (ex: Exception) {
            log.error("Force-resend of match {} succeeded but DELIVERED write failed: {}", matchId, ex.message)
            DiscordPublicationResult(
                PublicationOutcome.DELIVERED_BUT_STATE_UNCERTAIN, matchId,
                errorMessage = "HTTP succeeded but DELIVERED write failed: ${ex.message}",
            )
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Attempts to send the match to Discord.
     * @return null on success, or Pair(failure outcome, message) on error.
     */
    private fun trySend(canonical: CanonicalMatch, matchId: String): Pair<PublicationOutcome, String>? {
        return try {
            val payload = discordRenderer.renderMatch(
                canonical.footballMatch,
                canonical.interpretation,
                canonical.stories,
            )
            webhookClient.send(payload)
            null
        } catch (ex: IllegalStateException) {
            log.error("Discord webhook not configured: {}", ex.message)
            Pair(PublicationOutcome.FAILED_HTTP, ex.message ?: "Webhook not configured")
        } catch (ex: DiscordDeliveryException) {
            log.warn("Discord delivery failed for match {}: {}", matchId, ex.message)
            Pair(PublicationOutcome.FAILED_HTTP, ex.message ?: "Delivery failed")
        }
    }

    private fun safeRemoveDelivering(matchId: String) {
        try {
            store.removeRecord(matchId)
        } catch (ex: Exception) {
            log.warn("Could not remove DELIVERING marker for match {} after HTTP failure: {}", matchId, ex.message)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Result model
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Outcome of a [DiscordMatchPublicationService] call.
 */
enum class PublicationOutcome {
    /** HTTP 2xx confirmed and DELIVERED state persisted. */
    PUBLISHED,

    /** Match already has a DELIVERED record in the store — zero HTTP calls made. */
    SKIPPED_ALREADY_DELIVERED,

    /**
     * Match is in DELIVERY_UNCERTAIN state (post-restart upgrade from DELIVERING).
     * Automatic resend is blocked. Zero HTTP calls made.
     */
    SKIPPED_DELIVERY_UNCERTAIN,

    /**
     * Persisting the DELIVERING write-ahead marker failed before the HTTP call.
     * Zero HTTP calls made. Safe to retry.
     */
    FAILED_BEFORE_SEND,

    /**
     * Discord HTTP call failed or webhook is not configured.
     * DELIVERING marker was removed. Safe to retry.
     */
    FAILED_HTTP,

    /**
     * HTTP 2xx was received but the subsequent DELIVERED write failed.
     * The message WAS delivered to Discord. The record on disk may remain DELIVERING,
     * which will become DELIVERY_UNCERTAIN after restart.
     * Automatic resend is blocked on next startup.
     */
    DELIVERED_BUT_STATE_UNCERTAIN,
}

/**
 * Result returned by [DiscordMatchPublicationService].
 */
data class DiscordPublicationResult(
    val outcome: PublicationOutcome,
    val matchId: String,
    val errorMessage: String? = null,
) {
    /** True if the match was actually sent to Discord (PUBLISHED or DELIVERED_BUT_STATE_UNCERTAIN). */
    val delivered: Boolean
        get() = outcome == PublicationOutcome.PUBLISHED || outcome == PublicationOutcome.DELIVERED_BUT_STATE_UNCERTAIN
}
