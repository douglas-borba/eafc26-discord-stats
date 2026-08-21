package com.eafc26.discordstats.service

import com.eafc26.discordstats.canonical.CanonicalMatch
import com.eafc26.discordstats.discord.DiscordDeliveryException
import com.eafc26.discordstats.discord.DiscordRenderer
import com.eafc26.discordstats.discord.DiscordDestination
import com.eafc26.discordstats.discord.DiscordDestinationResolver
import com.eafc26.discordstats.discord.DiscordWebhookClient
import com.eafc26.discordstats.llm.LlmEditorialService
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.store.BaselineReason
import com.eafc26.discordstats.store.DeliveryUncertaintyReason
import com.eafc26.discordstats.store.DiscordPublicationOrigin
import com.eafc26.discordstats.store.PublicationRecord
import com.eafc26.discordstats.store.PublicationRetryPolicy
import com.eafc26.discordstats.store.PublicationState
import com.eafc26.discordstats.store.PublicationStateStore
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.time.Instant

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
 *   DELIVERY_UNCERTAIN is recorded immediately when possible; otherwise DELIVERING is
 *   upgraded on restart. No automatic resend.
 * - If [PublicationState.DELIVERED] is successfully persisted: deduplication is permanent.
 *
 * ## HTTP failure classification
 *
 * | Situation | Outcome | Store after |
 * |-----------|---------|-------------|
 * | No destination (before HTTP) | SKIPPED_NO_DESTINATION | BASELINED / NO_DESTINATION |
 * | Proven pre-send failure | FAILED_BEFORE_SEND | FAILED_TRANSIENT / retry policy |
 * | Non-2xx response from Discord | FAILED_HTTP | DELIVERING removed (Discord confirmed non-delivery) |
 * | Network/timeout/reset (after request potentially sent) | FAILED_AMBIGUOUS | DELIVERING → DELIVERY_UNCERTAIN |
 * | HTTP 2xx, DELIVERED write succeeds | PUBLISHED | DELIVERED |
 * | HTTP 2xx, DELIVERED write fails | DELIVERED_BUT_STATE_UNCERTAIN | DELIVERY_UNCERTAIN when diagnostic persistence succeeds; otherwise startup recovery |
 *
 * ## Guarantee achieved
 * **At-most-once automatic delivery with administrable ambiguity window.**
 * Exactly-once is not achievable because Discord webhooks offer no idempotency key.
 */
@Service
class DiscordMatchPublicationService(
    private val store: PublicationStateStore,
    private val webhookClient: DiscordWebhookClient,
    private val discordRenderer: DiscordRenderer,
    private val llmEditorialService: LlmEditorialService,
    private val destinationResolver: DiscordDestinationResolver,
    private val publicationLocks: PublicationLockRegistry = PublicationLockRegistry(),
    private val eventRecorder: OperationalEventRecorder? = null,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun publishIfNeeded(canonical: CanonicalMatch): DiscordPublicationResult {
        val matchId = canonical.matchId.value
        val clubId = canonical.interpretation.perspectiveClubId
        return publicationLocks.withLock(clubId, matchId) {
            var existing = store.find(clubId, matchId)
            if (existing == null) {
                val pending = PublicationRecord(matchId, PublicationState.PENDING)
                if (store.createRecordIfAbsent(clubId, pending)) {
                    eventRecorder?.discordPendingCreated(clubId, matchId)
                    existing = pending
                } else {
                    existing = store.find(clubId, matchId)
                }
            }
            val record = existing ?: return@withLock DiscordPublicationResult(
                PublicationOutcome.FAILED_BEFORE_SEND,
                matchId,
                "Publication intent could not be persisted",
            )
            when (record.state) {
                PublicationState.DELIVERED -> {
                    log.info("Match {} already DELIVERED — skipping", matchId)
                    eventRecorder?.discordSkipped(clubId, matchId, "ALREADY_DELIVERED")
                    return@withLock DiscordPublicationResult(PublicationOutcome.SKIPPED_ALREADY_DELIVERED, matchId)
                }
                PublicationState.DELIVERY_UNCERTAIN -> {
                    log.warn(
                        "Match {} is DELIVERY_UNCERTAIN — blocking automatic resend. " +
                            "Administrative resolution required (resolveAsDelivered / resolveAsUndelivered).",
                        matchId,
                    )
                    eventRecorder?.discordSkipped(clubId, matchId, "DELIVERY_UNCERTAIN")
                    return@withLock DiscordPublicationResult(PublicationOutcome.SKIPPED_DELIVERY_UNCERTAIN, matchId)
                }
                PublicationState.FAILED_PERMANENT -> {
                    log.warn(
                        "Match {} has FAILED_PERMANENT — blocking automatic resend. " +
                            "Correction and manual resend required.",
                        matchId,
                    )
                    eventRecorder?.discordSkipped(clubId, matchId, "FAILED_PERMANENT")
                    return@withLock DiscordPublicationResult(PublicationOutcome.SKIPPED_FAILED_PERMANENT, matchId)
                }
                PublicationState.RETRY_EXHAUSTED -> {
                    log.warn("Match {} exhausted automatic retries — manual resolution required", matchId)
                    eventRecorder?.discordSkipped(clubId, matchId, "RETRY_EXHAUSTED")
                    return@withLock DiscordPublicationResult(PublicationOutcome.SKIPPED_RETRY_EXHAUSTED, matchId)
                }
                PublicationState.DELIVERING -> {
                    log.warn("Match {} found in DELIVERING state within session — treating as DELIVERY_UNCERTAIN", matchId)
                    eventRecorder?.discordSkipped(clubId, matchId, "DELIVERING_IN_SESSION")
                    return@withLock DiscordPublicationResult(PublicationOutcome.SKIPPED_DELIVERY_UNCERTAIN, matchId)
                }
                PublicationState.BASELINED -> {
                    log.info("Match {} is BASELINED (never published) — blocking automatic publication", matchId)
                    eventRecorder?.discordSkipped(clubId, matchId, "BASELINED")
                    return@withLock DiscordPublicationResult(PublicationOutcome.SKIPPED_ALREADY_DELIVERED, matchId)
                }
                PublicationState.FAILED_TRANSIENT -> {
                    if (PublicationRetryPolicy.isRetryExhausted(record.attemptCount)) {
                        store.saveRecord(clubId, record.copy(state = PublicationState.RETRY_EXHAUSTED))
                        eventRecorder?.discordRetryExhausted(
                            clubId,
                            matchId,
                            record.attemptCount,
                            DiscordPublicationOrigin.AUTOMATIC_ACQUISITION,
                        )
                        return@withLock DiscordPublicationResult(PublicationOutcome.SKIPPED_RETRY_EXHAUSTED, matchId)
                    }
                    val nextRetry = PublicationRetryPolicy.nextRetryAt(record)
                    if (nextRetry != null && Instant.now().isBefore(nextRetry)) {
                        return@withLock DiscordPublicationResult(PublicationOutcome.SKIPPED_RETRY_BACKOFF, matchId)
                    }
                    log.info("Match {} has FAILED_TRANSIENT (attempt #{}) — retrying", matchId, record.attemptCount)
                }
                PublicationState.PENDING -> Unit
            }

            val destination = destinationResolver.resolve(clubId)
            if (destination == null) {
                if (record.state == PublicationState.PENDING) {
                    store.saveRecord(clubId, record.copy(state = PublicationState.BASELINED, baselineReason = BaselineReason.NO_DESTINATION))
                }
                log.info("Discord publication skipped: clubId={}, matchId={}, destinationConfigured=false", clubId.value, matchId)
                eventRecorder?.discordSkipped(clubId, matchId, "NO_DESTINATION")
                return@withLock DiscordPublicationResult(PublicationOutcome.SKIPPED_NO_DESTINATION, matchId)
            }

            try {
                val deliveringRecord = store.claimForAutomaticDelivery(clubId, record, Instant.now())
                    ?: return@withLock resultAfterLostClaim(clubId, matchId)
                eventRecorder?.discordClaimed(clubId, matchId, DiscordPublicationOrigin.AUTOMATIC_ACQUISITION)
                return@withLock deliverClaimed(
                    canonical = canonical,
                    destination = destination,
                    deliveringRecord = deliveringRecord,
                    previous = record,
                    origin = DiscordPublicationOrigin.AUTOMATIC_ACQUISITION,
                )
            } catch (ex: Exception) {
                log.error(
                    "Cannot claim DELIVERING state for match {} — no HTTP request will be attempted. Error: {}",
                    matchId, ex.message,
                )
                return@withLock DiscordPublicationResult(
                    PublicationOutcome.FAILED_BEFORE_SEND, matchId,
                    errorMessage = "Publication claim failed",
                )
            }
        }
    }

    /** Performs one scheduler recovery attempt after checking the current destination. */
    fun reconcile(canonical: CanonicalMatch, expected: PublicationRecord): DiscordPublicationResult {
        val claim = claimForReconciliation(canonical.interpretation.perspectiveClubId, expected)
            ?: return DiscordPublicationResult(PublicationOutcome.SKIPPED_NO_DESTINATION, canonical.matchId.value)
        return deliverReconciliationClaim(canonical, claim)
    }

    /** Claims recovery work before a caller loads the full canonical JSON payload. */
    fun claimForReconciliation(clubId: ClubId, expected: PublicationRecord): ClaimedDiscordPublication? {
        require(expected.isAutomaticReconciliationCandidate()) {
            "State ${expected.state} cannot be reconciled automatically"
        }
        return publicationLocks.withLock(clubId, expected.matchId) {
            val destination = destinationResolver.resolve(clubId) ?: return@withLock null
            val claimed = try {
                store.claimForAutomaticDelivery(clubId, expected, Instant.now())
            } catch (ex: Exception) {
                log.warn("Could not claim publication recovery: clubId={}, matchId={}, error={}", clubId.value, expected.matchId, ex::class.simpleName)
                return@withLock null
            } ?: return@withLock null

            eventRecorder?.discordClaimed(clubId, expected.matchId, DiscordPublicationOrigin.AUTOMATIC_RECONCILIATION)
            if (expected.state == PublicationState.BASELINED && expected.baselineReason == BaselineReason.NO_DESTINATION) {
                eventRecorder?.discordNoDestinationRecovered(clubId, expected.matchId)
            }
            ClaimedDiscordPublication(clubId, expected, claimed, destination)
        }
    }

    /** Executes HTTP delivery only for an already atomically claimed recovery record. */
    fun deliverReconciliationClaim(
        canonical: CanonicalMatch,
        claim: ClaimedDiscordPublication,
    ): DiscordPublicationResult {
        require(canonical.matchId.value == claim.claimed.matchId) { "Claim does not match canonical match" }
        require(canonical.interpretation.perspectiveClubId == claim.clubId) { "Claim does not match canonical club" }
        return deliverClaimed(
            canonical,
            claim.destination,
            claim.claimed,
            claim.previous,
            DiscordPublicationOrigin.AUTOMATIC_RECONCILIATION,
        )
    }

    /** Releases a claim after a failure proven to happen before the Discord HTTP call. */
    fun failClaimBeforeHttp(claim: ClaimedDiscordPublication, message: String) {
        safePersistFailure(
            clubId = claim.clubId,
            matchId = claim.claimed.matchId,
            state = PublicationState.FAILED_TRANSIENT,
            attemptCount = claim.claimed.attemptCount,
            lastAttemptAt = requireNotNull(claim.claimed.lastAttemptAt),
            errorMessage = message,
            httpStatus = null,
            origin = DiscordPublicationOrigin.AUTOMATIC_RECONCILIATION,
        )
        eventRecorder?.discordFailed(
            claim.clubId,
            claim.claimed.matchId,
            null,
            message,
            DiscordPublicationOrigin.AUTOMATIC_RECONCILIATION,
        )
    }

    /** Conservatively protects a claimed item if an unexpected delivery-path error escapes. */
    fun failClaimAmbiguously(claim: ClaimedDiscordPublication, error: Exception) {
        val message = sanitizeDiagnostic(error.message) ?: error::class.simpleName
        safeUpgradeToUncertain(
            claim.clubId,
            claim.claimed,
            DeliveryUncertaintyReason.UNKNOWN,
            message,
            claim.previous.state,
            claim.previous.lastError,
        )
        eventRecorder?.discordUncertain(
            claim.clubId,
            claim.claimed.matchId,
            DeliveryUncertaintyReason.UNKNOWN,
            message,
            DiscordPublicationOrigin.AUTOMATIC_RECONCILIATION,
            claim.previous.state,
        )
    }

    private fun deliverClaimed(
        canonical: CanonicalMatch,
        destination: DiscordDestination,
        deliveringRecord: PublicationRecord,
        previous: PublicationRecord,
        origin: DiscordPublicationOrigin,
    ): DiscordPublicationResult {
        val clubId = canonical.interpretation.perspectiveClubId
        val matchId = canonical.matchId.value
        eventRecorder?.discordAttempt(clubId, matchId, origin)
        return when (val send = trySend(canonical, matchId, destination)) {
            is SendOutcome.Success -> persistDelivered(
                clubId, matchId, deliveringRecord, origin, previous.state, previous.lastError,
            )
            is SendOutcome.FailedBeforeSend -> {
                safePersistFailure(
                    clubId, matchId, PublicationState.FAILED_TRANSIENT,
                    deliveringRecord.attemptCount, requireNotNull(deliveringRecord.lastAttemptAt), send.message, null, origin,
                )
                eventRecorder?.discordFailed(clubId, matchId, null, send.message, origin)
                DiscordPublicationResult(PublicationOutcome.FAILED_BEFORE_SEND, matchId, errorMessage = send.message)
            }
            is SendOutcome.FailedHttpExplicit -> {
                val failState = classifyHttpFailureState(send.statusCode)
                safePersistFailure(
                    clubId, matchId, failState,
                    deliveringRecord.attemptCount, requireNotNull(deliveringRecord.lastAttemptAt),
                    "HTTP ${send.statusCode}: ${send.message}", send.statusCode, origin,
                )
                eventRecorder?.discordFailed(clubId, matchId, send.statusCode, send.message, origin)
                DiscordPublicationResult(
                    PublicationOutcome.FAILED_HTTP,
                    matchId,
                    errorMessage = "HTTP ${send.statusCode}: ${send.message}",
                    httpStatusCode = send.statusCode,
                )
            }
            is SendOutcome.Ambiguous -> {
                safeUpgradeToUncertain(
                    clubId, deliveringRecord, send.reason, send.message, previous.state, previous.lastError,
                )
                eventRecorder?.discordUncertain(clubId, matchId, send.reason, send.message, origin, previous.state)
                DiscordPublicationResult(PublicationOutcome.FAILED_AMBIGUOUS, matchId, errorMessage = send.message)
            }
        }
    }

    private fun resultAfterLostClaim(clubId: ClubId, matchId: String): DiscordPublicationResult = when (store.find(clubId, matchId)?.state) {
        PublicationState.DELIVERED -> DiscordPublicationResult(PublicationOutcome.SKIPPED_ALREADY_DELIVERED, matchId)
        PublicationState.DELIVERY_UNCERTAIN, PublicationState.DELIVERING -> DiscordPublicationResult(PublicationOutcome.SKIPPED_DELIVERY_UNCERTAIN, matchId)
        PublicationState.FAILED_PERMANENT -> DiscordPublicationResult(PublicationOutcome.SKIPPED_FAILED_PERMANENT, matchId)
        PublicationState.RETRY_EXHAUSTED -> DiscordPublicationResult(PublicationOutcome.SKIPPED_RETRY_EXHAUSTED, matchId)
        PublicationState.FAILED_TRANSIENT -> DiscordPublicationResult(PublicationOutcome.SKIPPED_RETRY_BACKOFF, matchId)
        else -> DiscordPublicationResult(PublicationOutcome.FAILED_BEFORE_SEND, matchId, "Publication claim was not acquired")
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
            val existing = store.find(clubId, matchId)
            val previousAttemptCount = existing?.attemptCount ?: 0
            val nowEpoch = java.time.Instant.now().epochSecond
            eventRecorder?.discordManualResendRequested(clubId, matchId, existing)
            val deliveringRecord = PublicationRecord(
                matchId = matchId,
                state = PublicationState.DELIVERING,
                attemptCount = previousAttemptCount + 1,
                lastAttemptAt = nowEpoch,
                lastError = existing?.lastError,
                lastHttpStatus = existing?.lastHttpStatus,
            )
            try {
                store.saveRecord(clubId, deliveringRecord)
            } catch (ex: Exception) {
                log.error("Cannot persist DELIVERING for force-resend of match {}: {}", matchId, ex.message)
                return@withLock DiscordPublicationResult(PublicationOutcome.FAILED_BEFORE_SEND, matchId,
                    errorMessage = "Pre-send persistence failed: ${ex.message}")
            }
            eventRecorder?.discordAttempt(clubId, matchId, DiscordPublicationOrigin.FORCE_PUBLISH)

            return@withLock when (val send = trySend(canonical, matchId, destination)) {
                is SendOutcome.Success -> persistDelivered(
                    clubId,
                    matchId,
                    deliveringRecord,
                    DiscordPublicationOrigin.FORCE_PUBLISH,
                    existing?.state,
                    existing?.lastError,
                )
                is SendOutcome.FailedBeforeSend -> {
                    safePersistFailure(clubId, matchId, PublicationState.FAILED_TRANSIENT,
                        previousAttemptCount + 1, nowEpoch, send.message, null)
                    eventRecorder?.discordFailed(
                        clubId,
                        matchId,
                        null,
                        send.message,
                        DiscordPublicationOrigin.FORCE_PUBLISH,
                    )
                    DiscordPublicationResult(PublicationOutcome.FAILED_BEFORE_SEND, matchId,
                        errorMessage = send.message)
                }
                is SendOutcome.FailedHttpExplicit -> {
                    val failState = classifyHttpFailureState(send.statusCode)
                    log.warn("Force-resend HTTP {} for match {}: {} → {}", send.statusCode, matchId, send.message, failState)
                    safePersistFailure(clubId, matchId, failState,
                        previousAttemptCount + 1, nowEpoch, "HTTP ${send.statusCode}: ${send.message}", send.statusCode)
                    eventRecorder?.discordFailed(
                        clubId,
                        matchId,
                        send.statusCode,
                        send.message,
                        DiscordPublicationOrigin.FORCE_PUBLISH,
                    )
                    DiscordPublicationResult(PublicationOutcome.FAILED_HTTP, matchId,
                        errorMessage = "HTTP ${send.statusCode}: ${send.message}",
                        httpStatusCode = send.statusCode)
                }
                is SendOutcome.Ambiguous -> {
                    log.warn("Force-resend of match {} AMBIGUOUS: {}", matchId, send.message)
                    safeUpgradeToUncertain(
                        clubId,
                        deliveringRecord,
                        send.reason,
                        send.message,
                        existing?.state,
                        existing?.lastError,
                    )
                    eventRecorder?.discordUncertain(
                        clubId,
                        matchId,
                        send.reason,
                        send.message,
                        DiscordPublicationOrigin.FORCE_PUBLISH,
                        existing?.state,
                    )
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
    private fun persistDelivered(
        clubId: ClubId,
        matchId: String,
        deliveringRecord: PublicationRecord,
        origin: DiscordPublicationOrigin,
        previousState: PublicationState?,
        previousDiagnostic: String?,
    ): DiscordPublicationResult {
        return try {
            store.saveRecord(clubId, deliveringRecord.copy(
                state = PublicationState.DELIVERED,
                lastError = null,
                lastHttpStatus = null,
            ))
            log.info("Discord publication delivered: clubId={}, matchId={}", clubId.value, matchId)
            eventRecorder?.discordSuccess(clubId, matchId, origin)
            DiscordPublicationResult(PublicationOutcome.PUBLISHED, matchId)
        } catch (ex: Exception) {
            log.error(
                "Match {} was delivered to Discord (HTTP 2xx) but DELIVERED write failed. " +
                    "State is marked DELIVERY_UNCERTAIN when diagnostic persistence is available. Error: {}",
                matchId, sanitizeDiagnostic(ex.message),
            )
            val diagnostic = "HTTP succeeded but DELIVERED write failed: ${sanitizeDiagnostic(ex.message)}"
            safeUpgradeToUncertain(
                clubId,
                deliveringRecord,
                DeliveryUncertaintyReason.DELIVERED_STATE_PERSISTENCE_FAILURE,
                diagnostic,
                previousState,
                previousDiagnostic,
            )
            eventRecorder?.discordUncertain(
                clubId,
                matchId,
                DeliveryUncertaintyReason.DELIVERED_STATE_PERSISTENCE_FAILURE,
                diagnostic,
                origin,
                previousState,
            )
            DiscordPublicationResult(PublicationOutcome.DELIVERED_BUT_STATE_UNCERTAIN, matchId,
                errorMessage = diagnostic)
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
            // This branch is limited to local rendering/configuration failures before the
            // webhook client is invoked, so Discord delivery is provably safe to retry.
            log.error("Discord pre-send failure for match {}: {}", matchId, sanitizeDiagnostic(ex.message))
            SendOutcome.FailedBeforeSend(sanitizeDiagnostic(ex.message) ?: "Discord pre-send failure")
        } catch (ex: DiscordDeliveryException) {
            classifyDeliveryException(ex, matchId)
        } catch (ex: Exception) {
            // This boundary cannot prove whether an unexpected client/runtime failure
            // happened before or after the HTTP hand-off. Conservatively preserve the
            // DELIVERING marker as DELIVERY_UNCERTAIN instead of risking a duplicate.
            log.warn(
                "Unexpected Discord delivery-path failure for match {} — treating as ambiguous: {}",
                matchId,
                ex::class.simpleName,
            )
            SendOutcome.Ambiguous(
                sanitizeDiagnostic(ex.message) ?: ex::class.simpleName ?: "Unexpected delivery failure",
                DeliveryUncertaintyReason.UNKNOWN,
            )
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
            SendOutcome.FailedHttpExplicit(cause.statusCode.value(), sanitizeDiagnostic(ex.message) ?: "HTTP error")
        } else {
            // WebClientRequestException, timeout, connection reset, unknown I/O error —
            // we cannot prove the request was never received by Discord
            log.warn(
                "Unclassifiable network failure for match {} (cause: {}) — treating as AMBIGUOUS: {}",
                matchId, cause?.javaClass?.simpleName, sanitizeDiagnostic(ex.message),
            )
            SendOutcome.Ambiguous(
                sanitizeDiagnostic(ex.message) ?: "Network error",
                uncertaintyReasonFor(cause),
            )
        }
    }

    private fun classifyHttpFailureState(statusCode: Int): PublicationState =
        when (statusCode) {
            400, 401, 403, 404, 405, 410, 413 -> PublicationState.FAILED_PERMANENT
            else -> PublicationState.FAILED_TRANSIENT
        }

    private fun safePersistFailure(
        clubId: ClubId,
        matchId: String,
        state: PublicationState,
        attemptCount: Int,
        lastAttemptAt: Long,
        errorMessage: String?,
        httpStatus: Int?,
        origin: DiscordPublicationOrigin? = null,
    ): PublicationState {
        val persistedState = when {
            state == PublicationState.FAILED_TRANSIENT && origin.isAutomatic() && PublicationRetryPolicy.isRetryExhausted(attemptCount) ->
                PublicationState.RETRY_EXHAUSTED
            else -> state
        }
        try {
            store.saveRecord(clubId, PublicationRecord(
                matchId = matchId,
                state = persistedState,
                attemptCount = attemptCount,
                lastAttemptAt = lastAttemptAt,
                lastError = errorMessage,
                lastHttpStatus = httpStatus,
            ))
            when (persistedState) {
                PublicationState.FAILED_TRANSIENT -> PublicationRetryPolicy.nextRetryAt(
                    PublicationRecord(matchId, persistedState, attemptCount = attemptCount, lastAttemptAt = lastAttemptAt),
                )?.let { eventRecorder?.discordRetryScheduled(clubId, matchId, it) }
                PublicationState.RETRY_EXHAUSTED -> eventRecorder?.discordRetryExhausted(
                    clubId,
                    matchId,
                    attemptCount,
                    requireNotNull(origin),
                )
                else -> Unit
            }
        } catch (ex: Exception) {
            log.warn("Could not persist {} for match {} after failure: {}", persistedState, matchId, ex.message)
        }
        return persistedState
    }

    private fun safeUpgradeToUncertain(
        clubId: ClubId,
        deliveringRecord: PublicationRecord,
        reason: DeliveryUncertaintyReason,
        message: String?,
        previousState: PublicationState?,
        previousDiagnostic: String?,
    ) {
        try {
            store.saveRecord(clubId, deliveringRecord.copy(
                state = PublicationState.DELIVERY_UNCERTAIN,
                lastError = uncertaintyDiagnostic(reason, message, previousState, previousDiagnostic),
            ))
        } catch (ex: Exception) {
            log.warn(
                "Could not upgrade to DELIVERY_UNCERTAIN for match {} after ambiguous error — " +
                "DELIVERING marker remains and will be upgraded on next restart: {}",
                deliveringRecord.matchId, ex.message,
            )
        }
    }

    private fun uncertaintyReasonFor(cause: Throwable?): DeliveryUncertaintyReason {
        var current = cause
        while (current != null) {
            if (current is java.net.SocketTimeoutException || current is java.util.concurrent.TimeoutException) {
                return DeliveryUncertaintyReason.NETWORK_TIMEOUT
            }
            current = current.cause
        }
        return if (cause == null) DeliveryUncertaintyReason.UNKNOWN else DeliveryUncertaintyReason.NETWORK_EXCEPTION
    }

    private fun uncertaintyDiagnostic(
        reason: DeliveryUncertaintyReason,
        message: String?,
        previousState: PublicationState?,
        previousDiagnostic: String?,
    ): String = buildString {
        append(reason.diagnosticMessage(message))
        previousState?.let { append("; previousState=${it.name}") }
        previousDiagnostic?.takeIf { it.isNotBlank() }?.let {
            append("; previousDiagnostic=")
            append(sanitizeDiagnostic(it))
        }
    }

    private fun sanitizeDiagnostic(value: String?): String? = value
        ?.replace(DISCORD_WEBHOOK_URL, "[Discord webhook]")
        ?.take(500)

    private fun DiscordPublicationOrigin?.isAutomatic(): Boolean =
        this == DiscordPublicationOrigin.AUTOMATIC_ACQUISITION ||
            this == DiscordPublicationOrigin.AUTOMATIC_RECONCILIATION

    private fun PublicationRecord.isAutomaticReconciliationCandidate(): Boolean =
        state == PublicationState.PENDING ||
            state == PublicationState.FAILED_TRANSIENT ||
            state == PublicationState.BASELINED && baselineReason == BaselineReason.NO_DESTINATION

    // -------------------------------------------------------------------------
    // Internal sealed class for send classification
    // -------------------------------------------------------------------------

    private sealed class SendOutcome {
        object Success : SendOutcome()
        data class FailedBeforeSend(val message: String) : SendOutcome()
        data class FailedHttpExplicit(val statusCode: Int, val message: String) : SendOutcome()
        data class Ambiguous(
            val message: String,
            val reason: DeliveryUncertaintyReason,
        ) : SendOutcome()
    }

    private companion object {
        val DISCORD_WEBHOOK_URL = Regex("""https?://[^\s]+/api/webhooks/[^\s]+""")
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

    /** Automatic safe retry budget was exhausted; manual action is required. */
    SKIPPED_RETRY_EXHAUSTED,

    /** A safe transient retry exists but its bounded backoff has not expired. */
    SKIPPED_RETRY_BACKOFF,

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
     * The message WAS delivered to Discord. A diagnostic DELIVERY_UNCERTAIN state is
     * persisted when possible; otherwise the DELIVERING marker remains for startup recovery.
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

/** Server-side capability produced only after a successful atomic reconciliation claim. */
data class ClaimedDiscordPublication internal constructor(
    val clubId: ClubId,
    val previous: PublicationRecord,
    val claimed: PublicationRecord,
    internal val destination: DiscordDestination,
)
