package com.eafc26.discordstats.service

import com.eafc26.discordstats.config.AppProperties
import com.eafc26.discordstats.ea.EaApiResult
import com.eafc26.discordstats.ea.EaClubsGateway
import com.eafc26.discordstats.ea.model.MatchResponse
import com.eafc26.discordstats.application.repository.CanonicalMatchRepository
import com.eafc26.discordstats.canonical.CanonicalMatch
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.llm.LlmEditorialService
import com.eafc26.discordstats.presentation.MatchSummaryBuilder
import com.eafc26.discordstats.presentation.editorial.MatchEditorialPresentationService
import com.eafc26.discordstats.store.PublishedMatchStore
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

/**
 * Single acquisition pipeline for match data.
 *
 * This service is the sole orchestrator for all match acquisition flows:
 * - Scheduler polling
 * - Manual web button
 * - CLI commands
 * - Development simulator
 *
 * All callers use [acquire] which internally:
 * 1. Acquires the shared lock (via [AcquisitionLock])
 * 2. Fetches matches from EA API
 * 3. Canonicalizes and persists the complete returned window
 * 4. Applies Discord publication deduplication via [DiscordMatchPublicationService]
 * 5. Generates presentation and caches in [LatestMatchHolder]
 * 6. Delivers new matches to Discord (if applicable)
 * 7. Persists published match IDs (inside [DiscordMatchPublicationService])
 *
 * Discord publication is fully delegated to [DiscordMatchPublicationService],
 * which is the single authoritative component for deduplication, mutex, and
 * webhook interaction. This service never calls [com.eafc26.discordstats.discord.DiscordWebhookClient] directly.
 *
 * The [AcquisitionLock] is an internal implementation detail.
 * Callers never interact with it directly.
 */
@Service
class MatchAcquisitionService(
    @Qualifier("production") private val defaultGateway: EaClubsGateway,
    private val store: PublishedMatchStore,
    private val publicationService: DiscordMatchPublicationService,
    private val props: AppProperties,
    private val stateHolder: AcquisitionStateHolder,
    private val latestMatchHolder: LatestMatchHolder,
    private val matchSummaryBuilder: MatchSummaryBuilder,
    private val canonicalMatchRepository: CanonicalMatchRepository,
    private val canonicalMatchFactory: CanonicalMatchFactory,
    private val editorialPresentationService: MatchEditorialPresentationService?,
    private val llmEditorialService: LlmEditorialService,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val lock = AcquisitionLock()

    /**
     * Executes the acquisition pipeline.
     *
     * @param trigger Identifies the origin of the request (affects processing mode)
     * @param gateway Data source for matches. Production callers should not specify this.
     *                Only the development simulator passes a [FixtureEaClubsGateway].
     * @return The outcome of the acquisition
     */
    fun acquire(
        clubId: ClubId,
        trigger: AcquisitionTrigger,
        gateway: EaClubsGateway = defaultGateway,
    ): AcquisitionResult {
        val result = lock.tryRun(clubId) {
            val executionId = stateHolder.start(clubId, trigger)
            log.debug("Acquisition started: executionId={}, trigger={}", executionId, trigger)
            try {
                executeAcquisition(clubId, trigger, gateway)
            } catch (ex: Exception) {
                log.error("Unexpected error during acquisition", ex)
                stateHolder.fail(clubId, ex.message ?: "Unknown error", "Erro inesperado na aquisição.")
                throw ex
            }
        }

        if (result == null) {
            stateHolder.recordBusy(clubId, trigger)
            log.debug("Acquisition rejected (busy): trigger={}", trigger)
            return AcquisitionResult.Busy
        }

        return result
    }

    // -------------------------------------------------------------------------
    // Internal orchestration
    // -------------------------------------------------------------------------

    private fun executeAcquisition(
        clubId: ClubId,
        trigger: AcquisitionTrigger,
        gateway: EaClubsGateway,
    ): AcquisitionResult {
        // Phase: FETCHING
        stateHolder.enterPhase(clubId, AcquisitionPhase.FETCHING, "Consultando a EA...")

        // Step 1: Fetch matches from EA
        val matches = when (val result = gateway.getLatestMatches(clubId.value)) {
            is EaApiResult.Success -> result.data
            EaApiResult.NoMatches -> {
                log.info("No matches found for club-id={}", clubId)
                stateHolder.complete(clubId, "Nenhuma partida encontrada.")
                return AcquisitionResult.NoMatches
            }
            is EaApiResult.Unavailable -> {
                log.warn("EA API unavailable (HTTP {}): {}", result.statusCode, result.message)
                stateHolder.fail(clubId, "EA API unavailable: ${result.message}", "EA indisponível. Nova tentativa em breve.")
                return AcquisitionResult.EaUnavailable(result.statusCode, result.message)
            }
            is EaApiResult.UnexpectedPayload -> {
                log.error("EA API returned unexpected payload", result.cause)
                stateHolder.fail(clubId, "Unexpected payload: ${result.cause.message}", "EA indisponível. Nova tentativa em breve.")
                return AcquisitionResult.EaUnavailable(0, result.cause.message ?: "Unexpected payload")
            }
        }

        if (matches.isEmpty()) {
            log.info("EA returned empty match list for club-id={}", clubId)
            stateHolder.complete(clubId, "Nenhuma partida encontrada.")
            return AcquisitionResult.NoMatches
        }

        // Step 2: Fetch Virtual Pro names (best-effort; fall back to empty map on any failure)
        val proNames: Map<String, String> = when (val result = gateway.getMembersStats(clubId.value)) {
            is EaApiResult.Success -> {
                result.data
                    .filter { !it.playerName.isNullOrBlank() && !it.proName.isNullOrBlank() }
                    .associate { it.playerName!! to it.proName!! }
                    .also { map ->
                        log.info("Loaded {} Virtual Pro name(s) for club-id={}", map.size, clubId)
                    }
            }
            else -> {
                log.warn("Could not fetch members/stats for club-id={} — falling back to gamertags", clubId)
                emptyMap()
            }
        }


        // Phase: PROCESSING
        stateHolder.enterPhase(clubId, AcquisitionPhase.PROCESSING, "Processando partidas...")

        // Canonical storage is independent from presentation and Discord delivery.
        // Development fixtures remain intentionally non-persistent.
        val canonicalByMatchId = matches
            .sortedWith(compareBy<MatchResponse> { it.timestamp }.thenBy { it.matchId })
            .associate { match ->
                match.matchId to canonicalMatchFactory.create(match, clubId.value, proNames)
            }
        if (trigger != AcquisitionTrigger.DEV_SIMULATOR) {
            stateHolder.enterPhase(clubId, AcquisitionPhase.PERSISTING, "Salvando acervo canônico...")
            canonicalByMatchId.values.forEach { canonical ->
                // Step 1: Save canonical match (always first)
                canonicalMatchRepository.save(canonical)

                try {
                    editorialPresentationService?.generateAndPersist(canonical)
                } catch (ex: Exception) {
                    log.error("Editorial presentation failed for match {}: {}", canonical.matchId.value, ex.message)
                }
            }
            canonicalByMatchId.values.lastOrNull()?.let { latest ->
                try {
                    llmEditorialService.generateAndPersistPanorama(latest)
                } catch (ex: Exception) {
                    log.error("LLM panorama generation failed: {}", ex.message)
                }
            }
        }

        // Step 3: Route to appropriate processing mode
        val result = when (trigger) {
            AcquisitionTrigger.FORCE_RESEND -> processForceResend(clubId, matches, canonicalByMatchId)
            AcquisitionTrigger.MANUAL, AcquisitionTrigger.CLI -> processLatestOnly(clubId, matches, canonicalByMatchId)
            AcquisitionTrigger.SCHEDULER -> processAllNew(clubId, matches, canonicalByMatchId)
            AcquisitionTrigger.DEV_SIMULATOR -> processSimulation(clubId, matches, canonicalByMatchId)
        }

        // Update final state based on result
        when (result) {
            is AcquisitionResult.Processed -> {
                val status = when {
                    result.baselineEstablished -> "Histórico inicial configurado."
                    result.simulated -> "Simulação concluída. Card disponível na interface."
                    result.published.isNotEmpty() -> "Partida enviada com sucesso."
                    result.allSkipped() -> "Nenhuma partida nova."
                    result.failed.isNotEmpty() -> "Algumas partidas falharam."
                    else -> "Nenhuma partida nova."
                }
                stateHolder.complete(clubId, status)
            }
            is AcquisitionResult.ForceResent -> {
                stateHolder.complete(clubId, "Partida reenviada com sucesso.")
            }
            AcquisitionResult.WebhookNotConfigured -> {
                stateHolder.fail(clubId, "Webhook not configured", "Webhook não configurado.")
            }
            else -> {
                stateHolder.complete(clubId, "Aquisição concluída.")
            }
        }

        return result
    }

    // -------------------------------------------------------------------------
    // Processing modes
    // -------------------------------------------------------------------------

    /**
     * Process only the latest match (for manual/CLI triggers).
     */
    private fun processLatestOnly(
        clubId: ClubId,
        matches: List<MatchResponse>,
        canonicalByMatchId: Map<String, CanonicalMatch>,
    ): AcquisitionResult {
        val latest = matches.maxByOrNull { it.timestamp }
            ?: return AcquisitionResult.NoMatches
        val canonical = canonicalByMatchId.getValue(latest.matchId)
        val summary = buildSummary(canonical)

        // Cache presentation BEFORE deduplication check
        stateHolder.enterPhase(clubId, AcquisitionPhase.CACHING, "Atualizando cache...")
        val presentation = buildDashboardPresentation(canonical)
        val newVersion = latestMatchHolder.update(clubId, presentation)
        log.debug("Cached presentation for match {} (version={})", latest.matchId, newVersion)

        // First-run: establish baseline without publishing
        val publishedIds = store.loadIds()
        if (publishedIds.isEmpty()) {
            return establishBaseline(matches)
        }

        // Delegate to centralized publication service (handles dedup + mutex + persistence)
        stateHolder.enterPhase(clubId, AcquisitionPhase.DELIVERING, "Verificando e enviando para Discord...")
        val pubResult = publicationService.publishIfNeeded(canonical)

        return when (pubResult.outcome) {
            PublicationOutcome.SKIPPED_ALREADY_DELIVERED -> {
                AcquisitionResult.Processed(
                    published = emptyList(),
                    alreadyPublished = listOf(AcquisitionResult.MatchSummary(latest.matchId, summary)),
                    failed = emptyList(),
                )
            }
            PublicationOutcome.SKIPPED_DELIVERY_UNCERTAIN -> {
                log.warn("Match {} is DELIVERY_UNCERTAIN — blocked from automatic resend", latest.matchId)
                AcquisitionResult.Processed(
                    published = emptyList(),
                    alreadyPublished = emptyList(),
                    failed = emptyList(),
                    deliveryUncertain = listOf(AcquisitionResult.MatchSummary(latest.matchId, summary)),
                )
            }
            PublicationOutcome.SKIPPED_FAILED_PERMANENT -> {
                log.warn("Match {} has FAILED_PERMANENT — blocked from automatic resend", latest.matchId)
                AcquisitionResult.Processed(
                    published = emptyList(),
                    alreadyPublished = emptyList(),
                    failed = listOf(AcquisitionResult.MatchFailure(
                        latest.matchId, summary,
                        "Falha permanente - requer correção e reenvio manual"
                    )),
                )
            }
            PublicationOutcome.PUBLISHED, PublicationOutcome.DELIVERED_BUT_STATE_UNCERTAIN -> {
                stateHolder.enterPhase(clubId, AcquisitionPhase.PERSISTING, "Salvando histórico...")
                log.info("Published match {}", latest.matchId)
                AcquisitionResult.Processed(
                    published = listOf(AcquisitionResult.MatchSummary(latest.matchId, summary,
                        pubResult.outcome == PublicationOutcome.PUBLISHED)),
                    alreadyPublished = emptyList(),
                    failed = emptyList(),
                )
            }
            PublicationOutcome.FAILED_BEFORE_SEND, PublicationOutcome.FAILED_HTTP -> {
                val reason = pubResult.errorMessage ?: pubResult.outcome.name
                if (reason.contains("not configured", ignoreCase = true) ||
                    reason.contains("Webhook", ignoreCase = true)) {
                    AcquisitionResult.WebhookNotConfigured
                } else {
                    AcquisitionResult.Processed(
                        published = emptyList(),
                        alreadyPublished = emptyList(),
                        failed = listOf(AcquisitionResult.MatchFailure(latest.matchId, summary, reason)),
                    )
                }
            }
            PublicationOutcome.FAILED_AMBIGUOUS -> {
                log.warn(
                    "Match {} delivery AMBIGUOUS — DELIVERY_UNCERTAIN saved. " +
                        "Manual resolution required via admin endpoint. Error: {}",
                    latest.matchId, pubResult.errorMessage,
                )
                AcquisitionResult.Processed(
                    published = emptyList(),
                    alreadyPublished = emptyList(),
                    failed = listOf(AcquisitionResult.MatchFailure(
                        latest.matchId, summary,
                        "DELIVERY_UNCERTAIN: ${pubResult.errorMessage ?: "network error after potential send"}"
                    )),
                    deliveryUncertain = listOf(AcquisitionResult.MatchSummary(latest.matchId, summary)),
                )
            }
        }
    }

    /**
     * Process matches for development simulation (web-only).
     *
     * This method:
     * - Generates and caches the presentation with random phrase selection
     * - Marks the match as simulated
     * - Does NOT deliver to Discord
     * - Does NOT persist to the published match store
     *
     * The simulation exercises the real fetch, processing, and caching flow
     * but stops before external delivery and production persistence.
     *
     * Each simulation regenerates the presentation with new random phrases,
     * even if the fixture match data is the same.
     */
    private fun processSimulation(
        clubId: ClubId,
        matches: List<MatchResponse>,
        canonicalByMatchId: Map<String, CanonicalMatch>,
    ): AcquisitionResult {
        val latestMatch = matches.maxByOrNull { it.timestamp }
            ?: return AcquisitionResult.NoMatches
        val canonical = canonicalByMatchId.getValue(latestMatch.matchId)
        val summary = buildSummary(canonical)

        // Phase: CACHING - Generate and cache presentation (marked as simulated)
        // Use forceRandomPhrases=true so each simulation gets new random phrases
        stateHolder.enterPhase(clubId, AcquisitionPhase.CACHING, "Gerando card simulado...")
        val presentation = buildDashboardPresentation(
            canonical,
            forceRandomPhrases = true,
        )
        val newVersion = latestMatchHolder.update(clubId, presentation, simulated = true)
        log.debug("Cached simulated presentation for match {} (version={})", latestMatch.matchId, newVersion)

        log.info("DevSimulator: Simulation complete for match {} (no Discord delivery)", latestMatch.matchId)

        // Return as simulated - no Discord delivery, no persistence
        return AcquisitionResult.Processed(
            published = emptyList(),
            alreadyPublished = emptyList(),
            failed = emptyList(),
            simulated = true,
            simulatedMatch = AcquisitionResult.MatchSummary(latestMatch.matchId, summary),
        )
    }

    /**
     * Process all new matches (for scheduler triggers).
     * Establishes a safe baseline when the publication store is empty.
     */
    private fun processAllNew(
        clubId: ClubId,
        matches: List<MatchResponse>,
        canonicalByMatchId: Map<String, CanonicalMatch>,
    ): AcquisitionResult {
        val publishedIds = store.loadIds()

        // First-run detection
        if (publishedIds.isEmpty()) {
            return handleFirstRun(clubId, matches, canonicalByMatchId)
        }

        // Find the latest match for caching (regardless of publication status)
        val latestMatch = matches.maxByOrNull { it.timestamp }

        // Phase: CACHING - Cache the latest presentation BEFORE checking deduplication
        if (latestMatch != null) {
            stateHolder.enterPhase(clubId, AcquisitionPhase.CACHING, "Atualizando cache...")
            val presentation = buildDashboardPresentation(canonicalByMatchId.getValue(latestMatch.matchId))
            val newVersion = latestMatchHolder.update(clubId, presentation)
            log.debug("Cached presentation for match {} (version={})", latestMatch.matchId, newVersion)
        }

        // Find new matches to publish
        val newMatches = matches
            .filter { it.matchId !in publishedIds }
            .sortedBy { it.timestamp }

        if (newMatches.isEmpty()) {
            log.debug("No new matches to publish")
            val latestSummary = latestMatch?.let { buildSummary(canonicalByMatchId.getValue(it.matchId)) }
            return AcquisitionResult.Processed(
                published = emptyList(),
                alreadyPublished = latestSummary?.let {
                    listOf(AcquisitionResult.MatchSummary(latestMatch.matchId, it))
                } ?: emptyList(),
                failed = emptyList(),
            )
        }

        log.info("Found {} new match(es) to publish", newMatches.size)
        stateHolder.enterPhase(clubId, AcquisitionPhase.DELIVERING, "Enviando ${newMatches.size} partida(s)...")

        val published = mutableListOf<AcquisitionResult.MatchSummary>()
        val failed = mutableListOf<AcquisitionResult.MatchFailure>()

        for ((index, match) in newMatches.withIndex()) {
            val canonical = canonicalByMatchId.getValue(match.matchId)
            val summary = buildSummary(canonical)
            stateHolder.enterPhase(clubId, AcquisitionPhase.DELIVERING, "Enviando partida ${index + 1}/${newMatches.size}...")

            val pubResult = publicationService.publishIfNeeded(canonical)

            when (pubResult.outcome) {
                PublicationOutcome.PUBLISHED -> {
                    stateHolder.enterPhase(clubId, AcquisitionPhase.PERSISTING, "Salvando partida ${index + 1}/${newMatches.size}...")
                    published += AcquisitionResult.MatchSummary(match.matchId, summary, true)
                }
                PublicationOutcome.DELIVERED_BUT_STATE_UNCERTAIN -> {
                    stateHolder.enterPhase(clubId, AcquisitionPhase.PERSISTING, "Salvando partida ${index + 1}/${newMatches.size}...")
                    published += AcquisitionResult.MatchSummary(match.matchId, summary, false)
                }
                PublicationOutcome.SKIPPED_ALREADY_DELIVERED -> {
                    log.info("Match {} concurrently delivered — skipping", match.matchId)
                }
                PublicationOutcome.SKIPPED_DELIVERY_UNCERTAIN -> {
                    log.warn("Match {} is DELIVERY_UNCERTAIN — blocked from automatic resend", match.matchId)
                }
                PublicationOutcome.SKIPPED_FAILED_PERMANENT -> {
                    log.warn("Match {} has FAILED_PERMANENT — blocked from automatic resend", match.matchId)
                }
                PublicationOutcome.FAILED_BEFORE_SEND, PublicationOutcome.FAILED_HTTP -> {
                    val reason = pubResult.errorMessage ?: pubResult.outcome.name
                    if (reason.contains("not configured", ignoreCase = true) ||
                        reason.contains("Webhook", ignoreCase = true)) {
                        return AcquisitionResult.WebhookNotConfigured
                    }
                    failed += AcquisitionResult.MatchFailure(match.matchId, summary, reason)
                }
                PublicationOutcome.FAILED_AMBIGUOUS -> {
                    log.warn(
                        "Match {} delivery AMBIGUOUS — DELIVERY_UNCERTAIN saved. " +
                            "Manual resolution required. Error: {}",
                        match.matchId, pubResult.errorMessage,
                    )
                    failed += AcquisitionResult.MatchFailure(
                        match.matchId, summary,
                        "DELIVERY_UNCERTAIN: ${pubResult.errorMessage ?: "network error after potential send"}"
                    )
                }
            }
        }

        return AcquisitionResult.Processed(
            published = published,
            alreadyPublished = emptyList(),
            failed = failed,
        )
    }

    /**
     * Handles first use: publishes the latest match and establishes baseline for the rest.
     * 
     * This ensures that in a fresh install or after data loss, the most recent match
     * is delivered to Discord (giving immediate value), while older matches are marked
     * as baseline to avoid flooding Discord with historical data.
     */
    private fun handleFirstRun(
        clubId: ClubId,
        matches: List<MatchResponse>,
        canonicalByMatchId: Map<String, CanonicalMatch>,
    ): AcquisitionResult {
        // Find the latest match
        val latestMatch = matches.maxByOrNull { it.timestamp }
            ?: return AcquisitionResult.NoMatches

        log.info("First-run detected: will publish latest match {} and establish baseline for {} older matches",
            latestMatch.matchId, matches.size - 1)

        // Cache the latest presentation
        stateHolder.enterPhase(clubId, AcquisitionPhase.CACHING, "Atualizando cache...")
        val canonical = canonicalByMatchId.getValue(latestMatch.matchId)
        val presentation = buildDashboardPresentation(canonical)
        val newVersion = latestMatchHolder.update(clubId, presentation)
        log.debug("Cached presentation for match {} (version={})", latestMatch.matchId, newVersion)

        // Try to publish the latest match
        stateHolder.enterPhase(clubId, AcquisitionPhase.DELIVERING, "Publicando partida mais recente...")
        val pubResult = publicationService.publishIfNeeded(canonical)
        val summary = buildSummary(canonical)

        val published = mutableListOf<AcquisitionResult.MatchSummary>()
        val failed = mutableListOf<AcquisitionResult.MatchFailure>()

        when (pubResult.outcome) {
            PublicationOutcome.PUBLISHED -> {
                log.info("Latest match {} published successfully during first-run", latestMatch.matchId)
                published += AcquisitionResult.MatchSummary(latestMatch.matchId, summary, true)
            }
            PublicationOutcome.DELIVERED_BUT_STATE_UNCERTAIN -> {
                log.warn("Latest match {} delivered but state uncertain during first-run", latestMatch.matchId)
                published += AcquisitionResult.MatchSummary(latestMatch.matchId, summary, false)
            }
            PublicationOutcome.FAILED_BEFORE_SEND, PublicationOutcome.FAILED_HTTP -> {
                val reason = pubResult.errorMessage ?: pubResult.outcome.name
                if (reason.contains("not configured", ignoreCase = true) ||
                    reason.contains("Webhook", ignoreCase = true)) {
                    log.warn("Webhook not configured during first-run")
                    // Still establish baseline even if webhook is not configured
                    establishBaseline(matches)
                    return AcquisitionResult.WebhookNotConfigured
                }
                log.error("Failed to publish latest match during first-run: {}", reason)
                failed += AcquisitionResult.MatchFailure(latestMatch.matchId, summary, reason)
            }
            else -> {
                log.warn("Unexpected publication outcome during first-run: {}", pubResult.outcome)
            }
        }

        // Establish baseline for all matches (including the one we just published)
        // This prevents re-publication of historical matches
        stateHolder.enterPhase(clubId, AcquisitionPhase.PERSISTING, "Estabelecendo baseline...")
        establishBaseline(matches)

        log.info("First-run complete: {} published, {} in baseline",
            if (published.isNotEmpty()) 1 else 0, matches.size)

        return AcquisitionResult.Processed(
            published = published,
            alreadyPublished = emptyList(),
            failed = failed,
            baselineEstablished = true,
        )
    }

    private fun establishBaseline(matches: List<MatchResponse>): AcquisitionResult {
        val baselineIds = matches.mapTo(linkedSetOf()) { it.matchId }
        log.info("Publication baseline established with {} match(es); no Discord delivery", baselineIds.size)
        store.saveIds(baselineIds)
        return AcquisitionResult.Processed(
            published = emptyList(),
            alreadyPublished = emptyList(),
            failed = emptyList(),
            baselineEstablished = true,
        )
    }

    /**
     * Force-resend the latest match, bypassing deduplication.
     *
     * After successful delivery, [DiscordMatchPublicationService.forcePublish]
     * marks the match as published in the store so the scheduler will NOT
     * re-publish it on the next cycle.
     */
    private fun processForceResend(
        clubId: ClubId,
        matches: List<MatchResponse>,
        canonicalByMatchId: Map<String, CanonicalMatch>,
    ): AcquisitionResult {
        val latest = matches.maxByOrNull { it.timestamp }
            ?: return AcquisitionResult.NoMatches
        val canonical = canonicalByMatchId.getValue(latest.matchId)
        val summary = buildSummary(canonical)

        // Phase: CACHING - Generate and cache presentation
        stateHolder.enterPhase(clubId, AcquisitionPhase.CACHING, "Atualizando cache...")
        val presentation = buildDashboardPresentation(canonical)
        val newVersion = latestMatchHolder.update(clubId, presentation)
        log.debug("Cached presentation for match {} (version={})", latest.matchId, newVersion)

        // Phase: DELIVERING
        stateHolder.enterPhase(clubId, AcquisitionPhase.DELIVERING, "Reenviando para Discord...")

        val pubResult = publicationService.forcePublish(canonical)

        return when (pubResult.outcome) {
            PublicationOutcome.PUBLISHED, PublicationOutcome.DELIVERED_BUT_STATE_UNCERTAIN -> {
                log.info("Force-resent match {}", latest.matchId)
                AcquisitionResult.ForceResent(
                    match = AcquisitionResult.MatchSummary(latest.matchId, summary)
                )
            }
            PublicationOutcome.FAILED_BEFORE_SEND, PublicationOutcome.FAILED_HTTP -> {
                val reason = pubResult.errorMessage ?: pubResult.outcome.name
                if (reason.contains("not configured", ignoreCase = true) ||
                    reason.contains("Webhook", ignoreCase = true)) {
                    AcquisitionResult.WebhookNotConfigured
                } else {
                    AcquisitionResult.Processed(
                        published = emptyList(),
                        alreadyPublished = emptyList(),
                        failed = listOf(AcquisitionResult.MatchFailure(latest.matchId, summary, reason)),
                    )
                }
            }
            PublicationOutcome.FAILED_AMBIGUOUS -> {
                log.warn(
                    "Force-resend of match {} delivery AMBIGUOUS — DELIVERY_UNCERTAIN saved. " +
                        "Manual resolution required. Error: {}",
                    latest.matchId, pubResult.errorMessage,
                )
                AcquisitionResult.Processed(
                    published = emptyList(),
                    alreadyPublished = emptyList(),
                    failed = listOf(AcquisitionResult.MatchFailure(
                        latest.matchId, summary,
                        "DELIVERY_UNCERTAIN: ${pubResult.errorMessage ?: "network error after potential send"}"
                    )),
                    deliveryUncertain = listOf(AcquisitionResult.MatchSummary(latest.matchId, summary)),
                )
            }
            PublicationOutcome.SKIPPED_ALREADY_DELIVERED,
            PublicationOutcome.SKIPPED_DELIVERY_UNCERTAIN,
            PublicationOutcome.SKIPPED_FAILED_PERMANENT -> error("forcePublish never returns SKIPPED")
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun buildDashboardPresentation(
        canonical: CanonicalMatch,
        forceRandomPhrases: Boolean = false,
    ): com.eafc26.discordstats.presentation.MatchSummaryPresentation {
        return matchSummaryBuilder.build(
            footballMatch = canonical.footballMatch,
            interpretation = canonical.interpretation,
            stories = canonical.stories,
            forceRandomPhrases = forceRandomPhrases,
        )
    }

    /**
     * Builds a human-readable summary of a match.
     */
    private fun buildSummary(canonical: CanonicalMatch): String {
        val normalized = canonical.footballMatch
        val interpretation = canonical.interpretation
        val ourClub = normalized.participants.first { it.club.id == interpretation.perspectiveClubId }
        val opponent = normalized.participants.first { it.club.id == interpretation.result.opponentClub }
        return "${ourClub.club.name?.value ?: props.ea.clubName} " +
            "${interpretation.result.ourScore.goals}  ${interpretation.result.opponentScore.goals} " +
            (opponent.club.name?.value ?: "Adversário")
    }
}
