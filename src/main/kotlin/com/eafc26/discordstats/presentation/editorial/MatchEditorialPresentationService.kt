package com.eafc26.discordstats.presentation.editorial

import com.eafc26.discordstats.canonical.CanonicalMatch
import com.eafc26.discordstats.config.PhraseBank
import com.eafc26.discordstats.presentation.MatchSummaryBuilder
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant

/**
 * Service for generating and persisting editorial presentations.
 *
 * This service generates presentation-ready match summaries from canonical
 * matches and persists them separately for dashboard consumption.
 *
 * Failures in editorial generation/persistence DO NOT block the canonical
 * pipeline or Discord publication.
 */
@Service
@ConditionalOnProperty(name = ["app.postgres.mirror-enabled"], havingValue = "true")
class MatchEditorialPresentationService(
    private val matchSummaryBuilder: MatchSummaryBuilder,
    private val phraseBank: PhraseBank,
    private val repository: MatchEditorialPresentationRepository,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Generates and persists an editorial presentation for a canonical match.
     *
     * This method is idempotent - repeated calls with the same canonical match
     * will upsert only if schema_version increased.
     *
     * DOES NOT regenerate automatically if phrase_bank_version changed.
     * Use [regenerate] for explicit regeneration.
     *
     * Failures are logged but not propagated to avoid blocking the canonical
     * pipeline.
     */
    fun generateAndPersist(canonical: CanonicalMatch) {
        try {
            val presentation = matchSummaryBuilder.build(
                footballMatch = canonical.footballMatch,
                interpretation = canonical.interpretation,
                stories = canonical.stories,
                forceRandomPhrases = false, // Always deterministic for persistence
            )

            val editorial = MatchEditorialPresentation(
                clubId = canonical.interpretation.perspectiveClubId,
                matchId = canonical.matchId,
                playedAt = canonical.footballMatch.playedAt,
                schemaVersion = MatchEditorialPresentation.CURRENT_SCHEMA_VERSION,
                phraseBankVersion = calculatePhraseBankVersion(),
                presentation = presentation,
                generatedAt = clock.instant(),
                updatedAt = clock.instant(),
            )

            repository.upsertIfNewer(editorial)
            log.debug(
                "Persisted editorial presentation for match {} (phraseBank={})",
                canonical.matchId.value,
                editorial.phraseBankVersion.take(8),
            )
        } catch (ex: Exception) {
            log.error(
                "Failed to persist editorial presentation for match {}: {}",
                canonical.matchId.value,
                ex.message,
                ex,
            )
            // DO NOT propagate - editorial failures must not corrupt canonical pipeline
        }
    }

    /**
     * Explicitly regenerates an editorial presentation for a match.
     *
     * Forces recreation even if presentation already exists.
     * Used by:
     * - Administrative backfill command
     * - Schema version migration
     * - Explicit regeneration command
     */
    fun regenerate(canonical: CanonicalMatch) {
        try {
            val presentation = matchSummaryBuilder.build(
                footballMatch = canonical.footballMatch,
                interpretation = canonical.interpretation,
                stories = canonical.stories,
                forceRandomPhrases = false,
            )

            val editorial = MatchEditorialPresentation(
                clubId = canonical.interpretation.perspectiveClubId,
                matchId = canonical.matchId,
                playedAt = canonical.footballMatch.playedAt,
                schemaVersion = MatchEditorialPresentation.CURRENT_SCHEMA_VERSION,
                phraseBankVersion = calculatePhraseBankVersion(),
                presentation = presentation,
                generatedAt = clock.instant(),
                updatedAt = clock.instant(),
            )

            repository.forceUpsert(editorial)
            log.info(
                "Regenerated editorial presentation for match {} (phraseBank={})",
                canonical.matchId.value,
                editorial.phraseBankVersion.take(8),
            )
        } catch (ex: Exception) {
            log.error(
                "Failed to regenerate editorial presentation for match {}: {}",
                canonical.matchId.value,
                ex.message,
                ex,
            )
            throw ex // Propagate on explicit regeneration
        }
    }

    /**
     * Calculates a version hash of the current phrase bank state.
     *
     * Uses SHA-256 with explicit stable serialization:
     * - Categories ordered by identifier
     * - Each category explicitly delimited
     * - Original phrase order preserved
     * - UTF-8 explicit encoding
     * - Same content always generates same hash
     */
    fun calculatePhraseBankVersion(): String {
        val canonicalRepresentation = buildCanonicalPhraseRepresentation(phraseBank.getAll())
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(canonicalRepresentation.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    /**
     * Builds a canonical stable representation of phrase bank content.
     *
     * Format:
     * ```
     * category:CATEGORY_ID
     * phrase:Phrase text line 1
     * phrase:Phrase text line 2
     * ---
     * category:ANOTHER_CATEGORY
     * phrase:Another phrase
     * ---
     * ```
     *
     * Guarantees:
     * - Categories sorted alphabetically by key
     * - Phrase order within category preserved
     * - Delimiter unambiguous
     * - No implicit toString() behavior
     */
    private fun buildCanonicalPhraseRepresentation(phrases: Map<String, List<String>>): String {
        return phrases
            .toSortedMap() // Sort categories alphabetically
            .entries
            .joinToString(separator = "---\n") { (category, phraseList) ->
                buildString {
                    append("category:").append(category).append('\n')
                    phraseList.forEach { phrase ->
                        append("phrase:").append(phrase).append('\n')
                    }
                }
            }
    }
}
