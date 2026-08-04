package com.eafc26.discordstats.presentation.editorial

import com.eafc26.discordstats.application.repository.CanonicalMatchRepository
import com.eafc26.discordstats.domain.match.ClubId
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

/**
 * Backfill service for generating editorial presentations from existing
 * canonical matches.
 *
 * This service is idempotent and safe to run multiple times. It does NOT:
 * - Call the EA API
 * - Send Discord webhooks
 * - Modify PublishedMatchStore
 * - Change canonical matches
 */
@Service
@ConditionalOnProperty(name = ["app.postgres.mirror-enabled"], havingValue = "true")
class MatchEditorialBackfillService(
    private val canonicalMatchRepository: CanonicalMatchRepository,
    private val editorialPresentationService: MatchEditorialPresentationService,
    private val editorialRepository: MatchEditorialPresentationRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Generates editorial presentations for all canonical matches of a club.
     *
     * @param clubId The club to backfill presentations for
     * @param dryRun If true, only counts matches without persisting
     * @return Summary of the backfill operation
     */
    fun backfillForClub(clubId: ClubId, dryRun: Boolean = false): BackfillResult {
        log.info("Starting editorial backfill for club {} (dryRun={})", clubId.value, dryRun)

        val allCanonical = canonicalMatchRepository.findAll()
            .filter { it.interpretation.perspectiveClubId == clubId }
        log.info("Found {} canonical match(es) for club {}", allCanonical.size, clubId.value)

        if (allCanonical.isEmpty()) {
            return BackfillResult(
                clubId = clubId.value,
                total = 0,
                processed = 0,
                errors = 0,
                skipped = 0,
                dryRun = dryRun,
            )
        }

        var processed = 0
        var errors = 0
        var skipped = 0

        for ((index, canonical) in allCanonical.withIndex()) {
            if (index > 0 && index % 10 == 0) {
                log.info("Backfill progress: {}/{} matches", index, allCanonical.size)
            }

            try {
                // Check if already exists
                val existing = editorialRepository.findByClubAndMatch(
                    canonical.interpretation.perspectiveClubId,
                    canonical.matchId,
                )

                if (existing != null) {
                    log.debug("Editorial presentation already exists for match {}, skipping", canonical.matchId.value)
                    skipped++
                    continue
                }

                if (!dryRun) {
                    editorialPresentationService.generateAndPersist(canonical)
                }
                processed++
            } catch (ex: Exception) {
                log.error("Failed to backfill match {}: {}", canonical.matchId.value, ex.message)
                errors++
            }
        }

        val result = BackfillResult(
            clubId = clubId.value,
            total = allCanonical.size,
            processed = processed,
            errors = errors,
            skipped = skipped,
            dryRun = dryRun,
        )

        log.info(
            "Editorial backfill complete for club {}: {} processed, {} skipped, {} errors (dryRun={})",
            clubId.value,
            processed,
            skipped,
            errors,
            dryRun,
        )

        return result
    }

    /**
     * Regenerates ALL editorial presentations for a club.
     *
     * Forces recreation of all presentations regardless of phrase_bank_version.
     * Used by explicit administrative command only.
     *
     * @param clubId The club to regenerate presentations for
     * @return Summary of the regeneration operation
     */
    fun regenerateForClub(clubId: ClubId): BackfillResult {
        log.info("Starting EXPLICIT editorial regeneration for club {}", clubId.value)

        val allCanonical = canonicalMatchRepository.findAll()
            .filter { it.interpretation.perspectiveClubId == clubId }
        log.info("Found {} canonical match(es) for club {}", allCanonical.size, clubId.value)

        if (allCanonical.isEmpty()) {
            return BackfillResult(
                clubId = clubId.value,
                total = 0,
                processed = 0,
                errors = 0,
                skipped = 0,
                dryRun = false,
            )
        }

        var processed = 0
        var errors = 0

        for ((index, canonical) in allCanonical.withIndex()) {
            if (index > 0 && index % 10 == 0) {
                log.info("Regeneration progress: {}/{} matches", index, allCanonical.size)
            }

            try {
                // Use explicit regenerate method (not generateAndPersist)
                editorialPresentationService.regenerate(canonical)
                processed++
            } catch (ex: Exception) {
                log.error("Failed to regenerate match {}: {}", canonical.matchId.value, ex.message)
                errors++
            }
        }

        val result = BackfillResult(
            clubId = clubId.value,
            total = allCanonical.size,
            processed = processed,
            errors = errors,
            skipped = 0,
            dryRun = false,
        )

        log.info(
            "EXPLICIT editorial regeneration complete for club {}: {} processed, {} errors",
            clubId.value,
            processed,
            errors,
        )

        return result
    }
}

data class BackfillResult(
    val clubId: String,
    val total: Int,
    val processed: Int,
    val errors: Int,
    val skipped: Int,
    val dryRun: Boolean,
)
