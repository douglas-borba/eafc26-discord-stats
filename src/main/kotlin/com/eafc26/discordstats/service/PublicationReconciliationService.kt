package com.eafc26.discordstats.service

import com.eafc26.discordstats.application.repository.CanonicalMatchRepository
import com.eafc26.discordstats.canonical.CanonicalMatch
import com.eafc26.discordstats.service.DiscordMatchPublicationService
import com.eafc26.discordstats.store.PublicationRecord
import com.eafc26.discordstats.store.PublicationState
import com.eafc26.discordstats.store.PublicationStateStore
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.diagnostics.CanonicalReadOrigin
import com.eafc26.discordstats.diagnostics.CanonicalReadOriginContext

/**
 * Safe administrative reconciliation of publication status.
 *
 * ## Design principles
 * - Only automatically publish an already persisted, safe publication intention
 * - Never automatically resend DELIVERY_UNCERTAIN
 * - Provide detailed diagnostic information for manual resolution
 *
 * ## Automatic safe publication
 * A match can be automatically published ONLY if:
 * - PENDING, or
 * - Previous failure was explicitly safe to retry (FAILED_TRANSIENT)
 *
 * Everything else requires manual review.
 */
@Service
class PublicationReconciliationService(
    private val canonicalMatchRepository: CanonicalMatchRepository,
    private val store: PublicationStateStore,
    private val publicationService: DiscordMatchPublicationService,
    private val readOriginContext: CanonicalReadOriginContext = CanonicalReadOriginContext(),
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Inspects the latest N matches and their publication states.
     *
     * @param limit Maximum number of matches to inspect (default: 5)
     * @return Reconciliation report with detailed status for each match
     */
    fun inspectLatestPublications(clubId: ClubId, limit: Int = 5): ReconciliationReport {
        require(limit > 0) { "limit must be positive" }

        val allMatches = readOriginContext.withOrigin(CanonicalReadOrigin.ADMIN) {
            canonicalMatchRepository.findAll(clubId)
        }.take(limit)
        val records = store.loadRecords(clubId)

        val inspections = allMatches.map { match ->
            val matchId = match.matchId.value
            val record = records[matchId]

            // Extract scores from participants - first is perspective, second is opponent
            val participants = match.footballMatch.participants
            val ourScore = participants.getOrNull(0)?.score?.goals ?: 0
            val oppScore = participants.getOrNull(1)?.score?.goals ?: 0

            MatchPublicationInspection(
                matchId = matchId,
                matchDate = match.footballMatch.playedAt,
                ourScore = ourScore,
                oppScore = oppScore,
                publicationState = record?.state,
                attemptCount = record?.attemptCount ?: 0,
                lastAttemptAt = record?.lastAttemptAt?.let { Instant.ofEpochSecond(it) },
                lastError = record?.lastError,
                lastHttpStatus = record?.lastHttpStatus,
                safeToAutoPublish = PublicationStateClassifier.isSafeToAutoPublish(record?.state),
            )
        }

        val summary = ReconciliationSummary(
            totalInspected = inspections.size,
            delivered = inspections.count { it.publicationState == PublicationState.DELIVERED },
            neverAttempted = inspections.count { it.publicationState == null },
            delivering = inspections.count { it.publicationState == PublicationState.DELIVERING },
            uncertain = inspections.count { it.publicationState == PublicationState.DELIVERY_UNCERTAIN },
            failedPermanent = inspections.count { it.publicationState == PublicationState.FAILED_PERMANENT },
            failedTransient = inspections.count { it.publicationState == PublicationState.FAILED_TRANSIENT },
            baselined = inspections.count { it.publicationState == PublicationState.BASELINED },
        )

        return ReconciliationReport(
            inspections = inspections,
            summary = summary,
        )
    }

    /**
     * Automatically publishes all matches that are provably safe to publish.
     *
     * Safe conditions:
     * - PENDING state
     * - FAILED_TRANSIENT state after its delivery backoff
     *
     * Returns the number of matches published and any errors encountered.
     */
    fun autoPublishSafe(clubId: ClubId): AutoPublishResult {
        val allMatches = readOriginContext.withOrigin(CanonicalReadOrigin.ADMIN) {
            canonicalMatchRepository.findAll(clubId)
        }
        val records = store.loadRecords(clubId)

        val published = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        val errors = mutableListOf<PublishError>()

        for (match in allMatches) {
            val matchId = match.matchId.value
            val record = records[matchId]

            if (!PublicationStateClassifier.isSafeToAutoPublish(record?.state)) {
                skipped.add(matchId)
                continue
            }

            try {
                val result = publicationService.publishIfNeeded(match)
                if (result.delivered) {
                    published.add(matchId)
                    log.info("Auto-published match {} during reconciliation", matchId)
                } else {
                    errors.add(PublishError(
                        matchId = matchId,
                        error = result.errorMessage ?: "Unknown error",
                        outcome = result.outcome.name,
                    ))
                }
            } catch (ex: Exception) {
                log.error("Failed to auto-publish match {} during reconciliation", matchId, ex)
                errors.add(PublishError(
                    matchId = matchId,
                    error = ex.message ?: "Erro ao processar publicação",
                    outcome = "EXCEPTION",
                ))
            }
        }

        return AutoPublishResult(
            publishedCount = published.size,
            skippedCount = skipped.size,
            errorCount = errors.size,
            published = published,
            errors = errors,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Result models
// ─────────────────────────────────────────────────────────────────────────────

data class ReconciliationReport(
    val inspections: List<MatchPublicationInspection>,
    val summary: ReconciliationSummary,
)

data class MatchPublicationInspection(
    val matchId: String,
    val matchDate: Instant,
    val ourScore: Int,
    val oppScore: Int,
    val publicationState: PublicationState?,
    val attemptCount: Int,
    val lastAttemptAt: Instant?,
    val lastError: String?,
    val lastHttpStatus: Int?,
    val safeToAutoPublish: Boolean,
)

data class ReconciliationSummary(
    val totalInspected: Int,
    val delivered: Int,
    val neverAttempted: Int,
    val delivering: Int,
    val uncertain: Int,
    val failedPermanent: Int,
    val failedTransient: Int,
    val baselined: Int,
)

data class AutoPublishResult(
    val publishedCount: Int,
    val skippedCount: Int,
    val errorCount: Int,
    val published: List<String>,
    val errors: List<PublishError>,
)

data class PublishError(
    val matchId: String,
    val error: String,
    val outcome: String,
)
