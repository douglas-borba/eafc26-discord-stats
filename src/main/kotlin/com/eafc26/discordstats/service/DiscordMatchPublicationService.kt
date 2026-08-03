package com.eafc26.discordstats.service

import com.eafc26.discordstats.canonical.CanonicalMatch
import com.eafc26.discordstats.discord.DiscordDeliveryException
import com.eafc26.discordstats.discord.DiscordRenderer
import com.eafc26.discordstats.discord.DiscordWebhookClient
import com.eafc26.discordstats.store.PublishedMatchStore
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

/**
 * Single authoritative boundary for all Discord match publication.
 *
 * This is the ONLY component allowed to call [DiscordWebhookClient] for
 * automatic publication. No controller, scheduler, or CLI may call the
 * webhook directly.
 *
 * Deduplication contract:
 * - [publishIfNeeded] checks [PublishedMatchStore] under a per-MatchId
 *   [ReentrantLock] before sending. If the match is already published,
 *   it returns [PublicationOutcome.SKIPPED_ALREADY_PUBLISHED] with zero
 *   HTTP calls to Discord.
 * - markPublished (via [PublishedMatchStore.saveIds]) occurs ONLY after
 *   a confirmed HTTP success from the webhook. A failed send does NOT
 *   mark the match as published.
 * - [forcePublish] bypasses the deduplication check but still marks the
 *   match as published afterward, preventing the scheduler from
 *   re-publishing it.
 *
 * Thread safety:
 * - Per-MatchId [ReentrantLock] prevents TOCTOU races between two
 *   threads attempting to publish the same match simultaneously.
 * - Cross-process safety is provided by [SingleInstanceGuard].
 */
@Service
class DiscordMatchPublicationService(
    private val store: PublishedMatchStore,
    private val webhookClient: DiscordWebhookClient,
    private val discordRenderer: DiscordRenderer,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Per-MatchId locks to prevent TOCTOU races within this JVM. */
    private val matchLocks = ConcurrentHashMap<String, ReentrantLock>()

    /**
     * Publishes a match to Discord if and only if it has not been published before.
     *
     * Flow (under per-MatchId lock):
     * 1. Load [PublishedMatchStore]
     * 2. If already published → return [PublicationOutcome.SKIPPED_ALREADY_PUBLISHED] (zero HTTP calls)
     * 3. Send to Discord webhook
     * 4. On HTTP success → persist MatchId to store → return [PublicationOutcome.PUBLISHED]
     * 5. On HTTP failure → do NOT persist → return [PublicationOutcome.FAILED_WEBHOOK] or [PublicationOutcome.FAILED_NOT_CONFIGURED]
     *
     * @return [DiscordPublicationResult] describing the outcome
     */
    fun publishIfNeeded(canonical: CanonicalMatch): DiscordPublicationResult {
        val matchId = canonical.matchId.value
        val lock = matchLocks.computeIfAbsent(matchId) { ReentrantLock() }
        lock.lock()
        try {
            // Authoritative deduplication check (inside lock)
            val publishedIds = store.loadIds()
            if (matchId in publishedIds) {
                log.info("Match {} already published, skipping Discord delivery", matchId)
                return DiscordPublicationResult(PublicationOutcome.SKIPPED_ALREADY_PUBLISHED, matchId)
            }

            // Send to Discord
            val sendFailure = trySend(canonical, matchId)
            if (sendFailure != null) {
                return DiscordPublicationResult(sendFailure.first, matchId, errorMessage = sendFailure.second)
            }

            // Persist AFTER confirmed delivery
            val persisted = tryPersist(matchId, publishedIds)
            return DiscordPublicationResult(PublicationOutcome.PUBLISHED, matchId, persisted)
        } finally {
            lock.unlock()
        }
    }

    /**
     * Force-publishes a match, bypassing the deduplication check.
     *
     * Unlike [publishIfNeeded], this sends to Discord even if the match was
     * already published. After successful delivery it still marks the match
     * as published in the store, so the scheduler will not re-publish it.
     *
     * @return [DiscordPublicationResult] describing the outcome
     */
    fun forcePublish(canonical: CanonicalMatch): DiscordPublicationResult {
        val matchId = canonical.matchId.value

        // Send unconditionally (no dedup check)
        val sendFailure = trySend(canonical, matchId)
        if (sendFailure != null) {
            return DiscordPublicationResult(sendFailure.first, matchId, errorMessage = sendFailure.second)
        }

        // Mark as published to prevent scheduler re-send
        val persisted = try {
            val existing = store.loadIds()
            if (matchId !in existing) {
                store.saveIds(existing + matchId)
            }
            true
        } catch (ex: Exception) {
            log.warn("Could not update store after force-resend for match {}: {}", matchId, ex.message)
            false
        }

        log.info("Force-published match {}", matchId)
        return DiscordPublicationResult(PublicationOutcome.PUBLISHED, matchId, persisted)
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Attempts to send the match to Discord.
     * @return null on success, or a pair of (failure outcome, message) on error.
     */
    private fun trySend(canonical: CanonicalMatch, matchId: String): Pair<PublicationOutcome, String>? {
        return try {
            val payload = discordRenderer.renderMatch(
                canonical.footballMatch,
                canonical.interpretation,
                canonical.stories,
            )
            webhookClient.send(payload)
            null // success
        } catch (ex: IllegalStateException) {
            log.error("Discord webhook not configured: {}", ex.message)
            Pair(PublicationOutcome.FAILED_NOT_CONFIGURED, ex.message ?: "Webhook not configured")
        } catch (ex: DiscordDeliveryException) {
            log.warn("Discord delivery failed for match {}: {}", matchId, ex.message)
            Pair(PublicationOutcome.FAILED_WEBHOOK, ex.message ?: "Delivery failed")
        }
    }

    /**
     * Persists a match ID to the store after successful Discord delivery.
     * @return true if persistence succeeded, false on I/O error.
     */
    private fun tryPersist(matchId: String, previousIds: Set<String>): Boolean {
        return try {
            store.saveIds(previousIds + matchId)
            log.info("Published and persisted match {}", matchId)
            true
        } catch (ex: Exception) {
            log.error("Discord delivery succeeded but persistence failed for match {}", matchId, ex)
            false
        }
    }
}

/**
 * Outcome of a Discord publication attempt.
 */
enum class PublicationOutcome {
    /** Match was sent to Discord and its ID was persisted. */
    PUBLISHED,

    /** Match was already in [PublishedMatchStore]; no HTTP call was made. */
    SKIPPED_ALREADY_PUBLISHED,

    /** Discord webhook URL is not configured. */
    FAILED_NOT_CONFIGURED,

    /** Discord HTTP call failed (rate limit, network error, etc.). */
    FAILED_WEBHOOK,
}

/**
 * Result returned by [DiscordMatchPublicationService].
 */
data class DiscordPublicationResult(
    val outcome: PublicationOutcome,
    val matchId: String,
    /** True if the match ID was successfully persisted to [PublishedMatchStore]. */
    val persistedSuccessfully: Boolean = true,
    /** Error message when outcome is a failure variant. */
    val errorMessage: String? = null,
)





