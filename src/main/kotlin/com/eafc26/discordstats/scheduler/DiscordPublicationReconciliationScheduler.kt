package com.eafc26.discordstats.scheduler

import com.eafc26.discordstats.application.repository.CanonicalMatchRepository
import com.eafc26.discordstats.domain.match.MatchId
import com.eafc26.discordstats.service.DiscordMatchPublicationService
import com.eafc26.discordstats.service.PublicationOutcome
import com.eafc26.discordstats.store.PostgresPublishedMatchStore
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant

/**
 * Recovers only durable, safely retryable Discord publication work. It is deliberately
 * independent from EA polling and never scans canonical history or canonical payloads.
 */
@Component
@ConditionalOnProperty(name = ["app.postgres.mirror-enabled"], havingValue = "true")
class DiscordPublicationReconciliationScheduler(
    private val publicationStore: PostgresPublishedMatchStore,
    private val canonicalMatches: CanonicalMatchRepository,
    private val publicationService: DiscordMatchPublicationService,
    private val statusHolder: PublicationReconciliationStatusHolder,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = INTERVAL_MS, initialDelay = INTERVAL_MS)
    fun reconcile() {
        val startedAt = Instant.now(clock)
        var claimed = 0
        var completed = 0
        var failed = 0
        try {
            val candidates = publicationStore.findAutomaticPublicationCandidates(startedAt, BATCH_SIZE)
            for (candidate in candidates) {
                val claim = publicationService.claimForReconciliation(candidate.clubId, candidate.record, startedAt) ?: continue
                claimed += 1
                try {
                    val canonical = canonicalMatches.findById(candidate.clubId, MatchId(candidate.record.matchId))
                    if (canonical == null) {
                        publicationService.failClaimBeforeHttp(claim, "Canonical match not found after publication claim")
                        failed += 1
                        continue
                    }
                    val result = publicationService.deliverReconciliationClaim(canonical, claim)
                    if (result.outcome in SUCCESSFUL_OR_SAFE_COMPLETIONS) completed += 1 else failed += 1
                } catch (ex: Exception) {
                    // No unhandled record may abort the remaining batch. The claim has not
                    // proven whether an HTTP send started, so preserve uncertainty.
                    publicationService.failClaimAmbiguously(claim, ex)
                    failed += 1
                    log.warn(
                        "Publication reconciliation item failed: clubId={}, matchId={}, errorType={}",
                        candidate.clubId.value,
                        candidate.record.matchId,
                        ex::class.simpleName,
                    )
                }
            }
            statusHolder.complete(startedAt, claimed, completed, failed)
        } catch (ex: Exception) {
            statusHolder.fail(startedAt, ex::class.simpleName ?: "UnknownException")
            log.error("Discord publication reconciliation cycle failed: errorType={}", ex::class.simpleName)
        }
    }

    private companion object {
        const val INTERVAL_MS = 60_000L
        const val BATCH_SIZE = 20
        val SUCCESSFUL_OR_SAFE_COMPLETIONS = setOf(
            PublicationOutcome.PUBLISHED,
            PublicationOutcome.FAILED_BEFORE_SEND,
            PublicationOutcome.FAILED_HTTP,
            PublicationOutcome.FAILED_AMBIGUOUS,
        )
    }
}
