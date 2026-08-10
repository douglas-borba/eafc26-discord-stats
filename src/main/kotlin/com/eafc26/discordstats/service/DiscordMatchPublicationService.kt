package com.eafc26.discordstats.service

import com.eafc26.discordstats.canonical.CanonicalMatch
import com.eafc26.discordstats.discord.DiscordDeliveryException
import com.eafc26.discordstats.discord.DiscordRenderer
import com.eafc26.discordstats.discord.DiscordDestination
import com.eafc26.discordstats.discord.DiscordDestinationResolver
import com.eafc26.discordstats.discord.DiscordWebhookClient
import com.eafc26.discordstats.llm.LlmEditorialService
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.store.PublicationRecord
import com.eafc26.discordstats.store.PublicationState
import com.eafc26.discordstats.store.PublishedMatchStore
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClientResponseException

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
 * ## HTTP failure classification
 *
 * | Situation | Outcome | Store after |
 * |-----------|---------|-------------|
 * | URL not configured (before HTTP) | FAILED_BEFORE_SEND | DELIVERING removed |
 * | Non-2xx response from Discord | FAILED_HTTP | DELIVERING removed (Discord confirmed non-delivery) |
 * | Network/timeout/reset (after request potentially sent) | FAILED_AMBIGUOUS | DELIVERING → DELIVERY_UNCERTAIN |
 * | HTTP 2xx, DELIVERED write succeeds | PUBLISHED | DELIVERED |
 * | HTTP 2xx, DELIVERED write fails | DELIVERED_BUT_STATE_UNCERTAIN | DELIVERING stays → DELIVERY_UNCERTAIN on restart |
 *
 * ## Guarantee achieved
 * **At-most-once automatic delivery with administrable ambiguity window.**
 * Exactly-once is not achievable because Discord webhooks offer no idempotency key.
 */
@Service
class DiscordMatchPublicationService(
    private val store: PublishedMatchStore,
    private val webhookClient: DiscordWebhookClient,
    private val discordRenderer: DiscordRenderer,
    private val llmEditorialService: LlmEditorialService,
    private val destinationResolver: DiscordDestinationResolver,
    private val publicationLocks: PublicationLockRegistry = PublicationLockRegistry(),
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun publishIfNeeded(canonical: CanonicalMatch): DiscordPublicationResult {
        val matchId = canonical.matchId.value
        val clubId = canonical.interpretation.perspectiveClubId
        return publicationLocks.withLock(clubId, matchId) {
            val existing = store.loadRecords(clubId)[matchId]
            when (existing?.state) {
                PublicationState.DELIVERED -> {
                    log.info("Match {} already DELIVERED — skipping", matchId)
                    return@withLock DiscordPublicationResult(PublicationOutcome.SKIPPED_ALREADY_DELIVERED, matchId)
                }
                PublicationState.DELIVERY_UNCERTAIN -> {
                    log.warn(
                        "Match {} is DELIVERY_UNCERTAIN — blocking automatic resend. " +
                            "Administrative resolution required (resolveAsDelivered / resolveAsUndelivered).",
                        matchId,
                    )
                    return@withLock DiscordPublicationResult(PublicationOutcome.SKIPPED_DELIVERY_UNCERTAIN, matchId)
                }
                PublicationState.FAILED_PERMANENT -> {
                    log.warn(
                        "Match {} has FAILED_PERMANENT — blocking automatic resend. " +
                            "Correction and manual resend required.",
                        matchId,
                    )
                    return@withLock DiscordPublicationResult(PublicationOutcome.SKIPPED_FAILED_PERMANENT, matchId)
                }
                PublicationState.DELIVERING -> {
                    log.warn("Match {} found in DELIVERING state within session — treating as DELIVERY_UNCERTAIN", matchId)
                    return@withLock DiscordPublicationResult(PublicationOutcome.SKIPPED_DELIVERY_UNCERTAIN, matchId)
                }
                PublicationState.BASELINED -> {
                    log.info("Match {} is BASELINED (never published) — blocking automatic publication", matchId)
                    return@withLock DiscordPublicationResult(PublicationOutcome.SKIPPED_ALREADY_DELIVERED, matchId)
                }
                null -> { /* not in store → proceed */ }
            }

            val destination = destinationResolver.resolve(clubId)
            if (destination == null) {
                store.saveRecord(clubId, PublicationRecord(matchId, PublicationState.BASELINED))
                log.info("Discord publication skipped: clubId={}, matchId={}, destinationConfigured=false", clubId.value, matchId)
                return@withLock DiscordPublicationResult(PublicationOutcome.SKIPPED_NO_DESTINATION, matchId)
            }

            // WAL: persist DELIVERING BEFORE HTTP
            try {
                store.saveRecord(clubId, PublicationRecord(matchId, PublicationState.DELIVERING))
            } catch (ex: Exception) {
                log.error(
                    "Cannot persist DELIVERING state for match {} — aborting to prevent ambiguous delivery. Error: {}",
                    matchId, ex.message,
                )
                return@withLock DiscordPublicationResult(
                    PublicationOutcome.FAILED_BEFORE_SEND, matchId,
                    errorMessage = "Pre-send persistence failed: ${ex.message}",
                )
            }

            return@withLock when (val send = trySend(canonical, matchId, destination)) {
                is SendOutcome.Success -> persistDelivered(clubId, matchId)
                is SendOutcome.FailedBeforeSend -> {
                    safeRemoveDelivering(clubId, matchId)
                    DiscordPublicationResult(PublicationOutcome.FAILED_BEFORE_SEND, matchId,
                        errorMessage = send.message)
                }
                is SendOutcome.FailedHttpExplicit -> {
                    // Discord returned an explicit non-2xx: definitively not delivered
                    log.warn("Discord HTTP {} for match {}: {}", send.statusCode, matchId, send.message)
                    safeRemoveDelivering(clubId, matchId)
                    DiscordPublicationResult(PublicationOutcome.FAILED_HTTP, matchId,
                        errorMessage = "HTTP ${send.statusCode}: ${send.message}",
                        httpStatusCode = send.statusCode)
                }
                is SendOutcome.Ambiguous -> {
                    // Network error after request was potentially sent — upgrade DELIVERING → DELIVERY_UNCERTAIN
                    log.warn(
                        "Match {} delivery AMBIGUOUS after network error — saving DELIVERY_UNCERTAIN. Error: {}",
                        matchId, send.message,
                    )
                    safeUpgradeToUncertain(clubId, matchId)
                    DiscordPublicationResult(PublicationOutcome.FAILED_AMBIGUOUS, matchId,
                        errorMessage = send.message)
                }
            }
        }
    }

    fun forcePublish(canonical: CanonicalMatch): DiscordPublicationResult {
        return forcePublish(canonical.interpretation.perspectiveClubId, canonical)
    }

    /** Force publication requires the complete `(ClubId, MatchId)` identity. */
    fun forcePublish(clubId: ClubId, canonical: CanonicalMatch): DiscordPublicationResult {
        val matchId = canonical.matchId.value
        require(canonical.interpretation.perspectiveClubId == clubId) {
            "Canonical match perspective does not belong to requested club"
        }
        return publicationLocks.withLock(clubId, matchId) {
            val destination = destinationResolver.resolve(clubId)
                ?: return@withLock DiscordPublicationResult(PublicationOutcome.SKIPPED_NO_DESTINATION, matchId)
            try {
                store.saveRecord(clubId, PublicationRecord(matchId, PublicationState.DELIVERING))
            } catch (ex: Exception) {
                log.error("Cannot persist DELIVERING for force-resend of match {}: {}", matchId, ex.message)
                return@withLock DiscordPublicationResult(PublicationOutcome.FAILED_BEFORE_SEND, matchId,
                    errorMessage = "Pre-send persistence failed: ${ex.message}")
            }

            return@withLock when (val send = trySend(canonical, matchId, destination)) {
                is SendOutcome.Success -> persistDelivered(clubId, matchId)
                is SendOutcome.FailedBeforeSend -> {
                    safeRemoveDelivering(clubId, matchId)
                    DiscordPublicationResult(PublicationOutcome.FAILED_BEFORE_SEND, matchId,
                        errorMessage = send.message)
                }
                is SendOutcome.FailedHttpExplicit -> {
                    log.warn("Force-resend HTTP {} for match {}: {}", send.statusCode, matchId, send.message)
                    safeRemoveDelivering(clubId, matchId)
                    DiscordPublicationResult(PublicationOutcome.FAILED_HTTP, matchId,
                        errorMessage = "HTTP ${send.statusCode}: ${send.message}",
                        httpStatusCode = send.statusCode)
                }
                is SendOutcome.Ambiguous -> {
                    log.warn("Force-resend of match {} AMBIGUOUS: {}", matchId, send.message)
                    safeUpgradeToUncertain(clubId, matchId)
                    DiscordPublicationResult(PublicationOutcome.FAILED_AMBIGUOUS, matchId,
                        errorMessage = send.message)
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * After HTTP 2xx: persists DELIVERED and returns the appropriate result.
     */
    private fun persistDelivered(clubId: ClubId, matchId: String): DiscordPublicationResult {
        return try {
            store.saveRecord(clubId, PublicationRecord(matchId, PublicationState.DELIVERED))
            log.info("Discord publication delivered: clubId={}, matchId={}", clubId.value, matchId)
            DiscordPublicationResult(PublicationOutcome.PUBLISHED, matchId)
        } catch (ex: Exception) {
            log.error(
                "Match {} was delivered to Discord (HTTP 2xx) but DELIVERED write failed. " +
                    "State remains DELIVERING on disk → DELIVERY_UNCERTAIN after restart. Error: {}",
                matchId, ex.message,
            )
            DiscordPublicationResult(PublicationOutcome.DELIVERED_BUT_STATE_UNCERTAIN, matchId,
                errorMessage = "HTTP succeeded but DELIVERED write failed: ${ex.message}")
        }
    }

    /**
     * Classifies the send attempt into one of four categories:
     *
     * - [SendOutcome.Success]: HTTP 2xx received
     * - [SendOutcome.FailedBeforeSend]: URL not configured — provably no HTTP was attempted
     * - [SendOutcome.FailedHttpExplicit]: Discord returned non-2xx — definitively not delivered
     * - [SendOutcome.Ambiguous]: Network error (timeout, reset, etc.) — delivery outcome unknown
     */
    private fun trySend(canonical: CanonicalMatch, matchId: String, destination: DiscordDestination): SendOutcome {
        return try {
            val narrative = try {
                llmEditorialService.generateMatchNarrative(canonical)
            } catch (ex: Exception) {
                log.warn("LLM narrative generation failed for match {}, proceeding without: {}", matchId, ex.message)
                null
            }
            val payload = discordRenderer.renderMatch(
                canonical.footballMatch,
                canonical.interpretation,
                canonical.stories,
                editorialNarrative = narrative,
            )
            webhookClient.send(destination, payload)
            SendOutcome.Success
        } catch (ex: IllegalStateException) {
            // Provably before any HTTP attempt: webhook URL is not configured
            log.error("Discord webhook not configured for match {}: {}", matchId, ex.message)
            SendOutcome.FailedBeforeSend(ex.message ?: "Webhook not configured")
        } catch (ex: DiscordDeliveryException) {
            classifyDeliveryException(ex, matchId)
        }
    }

    /**
     * Classifies a [DiscordDeliveryException] by inspecting its cause:
     *
     * - Cause is [WebClientResponseException]: Discord responded explicitly with non-2xx.
     *   The request definitely reached Discord but was rejected → safe to retry.
     * - Any other cause (network failure, timeout, connection reset, unknown):
     *   The request may or may not have been received by Discord → AMBIGUOUS.
     */
    private fun classifyDeliveryException(ex: DiscordDeliveryException, matchId: String): SendOutcome {
        val cause = ex.cause
        return if (cause is WebClientResponseException) {
            // Explicit HTTP response from Discord — non-2xx, definitively not delivered
            SendOutcome.FailedHttpExplicit(cause.statusCode.value(), ex.message ?: "HTTP error")
        } else {
            // WebClientRequestException, timeout, connection reset, unknown I/O error —
            // we cannot prove the request was never received by Discord
            log.warn(
                "Unclassifiable network failure for match {} (cause: {}) — treating as AMBIGUOUS: {}",
                matchId, cause?.javaClass?.simpleName, ex.message,
            )
            SendOutcome.Ambiguous(ex.message ?: "Network error")
        }
    }

    private fun safeRemoveDelivering(clubId: ClubId, matchId: String) {
        try {
            store.removeRecord(clubId, matchId)
        } catch (ex: Exception) {
            log.warn("Could not remove DELIVERING marker for match {} after failure: {}", matchId, ex.message)
        }
    }

    private fun safeUpgradeToUncertain(clubId: ClubId, matchId: String) {
        try {
            store.saveRecord(clubId, PublicationRecord(matchId, PublicationState.DELIVERY_UNCERTAIN))
        } catch (ex: Exception) {
            log.warn(
                "Could not upgrade to DELIVERY_UNCERTAIN for match {} after ambiguous error — " +
                    "DELIVERING marker remains and will be upgraded on next restart: {}",
                matchId, ex.message,
            )
        }
    }

    // -------------------------------------------------------------------------
    // Internal sealed class for send classification
    // -------------------------------------------------------------------------

    private sealed class SendOutcome {
        object Success : SendOutcome()
        data class FailedBeforeSend(val message: String) : SendOutcome()
        data class FailedHttpExplicit(val statusCode: Int, val message: String) : SendOutcome()
        data class Ambiguous(val message: String) : SendOutcome()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Result model
// ─────────────────────────────────────────────────────────────────────────────

enum class PublicationOutcome {
    /** HTTP 2xx confirmed and DELIVERED state persisted. */
    PUBLISHED,

    /** Match already has a DELIVERED record in the store — zero HTTP calls made. */
    SKIPPED_ALREADY_DELIVERED,

    /** Club has no Discord destination; canonical processing remains successful. */
    SKIPPED_NO_DESTINATION,

    /**
     * Match is in DELIVERY_UNCERTAIN state.
     * Automatic resend is blocked. Zero HTTP calls made.
     */
    SKIPPED_DELIVERY_UNCERTAIN,

    /**
     * Match has FAILED_PERMANENT state.
     * Automatic resend is blocked. Zero HTTP calls made.
     * Requires correction before manual resend.
     */
    SKIPPED_FAILED_PERMANENT,

    /**
     * Pre-send persistence of DELIVERING failed, OR webhook URL is not configured.
     * Zero HTTP calls made. Safe to retry.
     */
    FAILED_BEFORE_SEND,

    /**
     * Discord returned an explicit non-2xx HTTP response.
     * Request reached Discord but was rejected. DELIVERING removed. Safe to retry.
     * See [DiscordPublicationResult.httpStatusCode] for the specific status.
     */
    FAILED_HTTP,

    /**
     * Network error after the request was potentially sent (timeout, connection reset, etc.).
     * Delivery outcome is unknown. DELIVERY_UNCERTAIN saved. Auto-retry blocked.
     * Administrative resolution required.
     */
    FAILED_AMBIGUOUS,

    /**
     * HTTP 2xx was received but the subsequent DELIVERED write failed.
     * The message WAS delivered to Discord. DELIVERING remains on disk
     * and will become DELIVERY_UNCERTAIN after restart.
     */
    DELIVERED_BUT_STATE_UNCERTAIN,
}

data class DiscordPublicationResult(
    val outcome: PublicationOutcome,
    val matchId: String,
    val errorMessage: String? = null,
    /** HTTP status code, populated for [PublicationOutcome.FAILED_HTTP] only. */
    val httpStatusCode: Int? = null,
) {
    val delivered: Boolean
        get() = outcome == PublicationOutcome.PUBLISHED ||
            outcome == PublicationOutcome.DELIVERED_BUT_STATE_UNCERTAIN
}
