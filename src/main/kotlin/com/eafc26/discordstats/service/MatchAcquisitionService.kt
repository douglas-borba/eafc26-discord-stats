package com.eafc26.discordstats.service

import com.eafc26.discordstats.config.AppProperties
import com.eafc26.discordstats.discord.DiscordDeliveryException
import com.eafc26.discordstats.discord.DiscordPayload
import com.eafc26.discordstats.discord.DiscordRenderer
import com.eafc26.discordstats.discord.DiscordWebhookClient
import com.eafc26.discordstats.ea.EaApiResult
import com.eafc26.discordstats.ea.EaClubsGateway
import com.eafc26.discordstats.ea.model.MatchResponse
import com.eafc26.discordstats.application.repository.CanonicalMatchRepository
import com.eafc26.discordstats.canonical.CanonicalMatch
import com.eafc26.discordstats.presentation.MatchSummaryBuilder
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
 * 4. Applies Discord publication deduplication
 * 5. Generates presentation and caches in [LatestMatchHolder]
 * 6. Delivers new matches to Discord (if applicable)
 * 7. Persists published match IDs
 *
 * The [AcquisitionLock] is an internal implementation detail.
 * Callers never interact with it directly.
 *
 * Acquisition state is reported through [AcquisitionStateHolder], which is
 * updated at each phase transition. This is the single source of truth for
 * acquisition status.
 *
 * The [LatestMatchHolder] caches the most recent presentation, allowing
 * MatchCardService to display data without querying EA on every request.
 */
@Service
class MatchAcquisitionService(
    @Qualifier("production") private val defaultGateway: EaClubsGateway,
    private val store: PublishedMatchStore,
    private val webhookClient: DiscordWebhookClient,
    private val props: AppProperties,
    private val stateHolder: AcquisitionStateHolder,
    private val latestMatchHolder: LatestMatchHolder,
    private val matchSummaryBuilder: MatchSummaryBuilder,
    private val discordRenderer: DiscordRenderer,
    private val canonicalMatchRepository: CanonicalMatchRepository,
    private val canonicalMatchFactory: CanonicalMatchFactory,
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
        trigger: AcquisitionTrigger,
        gateway: EaClubsGateway = defaultGateway,
    ): AcquisitionResult {
        val result = lock.tryRun {
            val executionId = stateHolder.start(trigger)
            log.debug("Acquisition started: executionId={}, trigger={}", executionId, trigger)
            try {
                executeAcquisition(trigger, gateway)
            } catch (ex: Exception) {
                log.error("Unexpected error during acquisition", ex)
                stateHolder.fail(ex.message ?: "Unknown error", "Erro inesperado na aquisição.")
                throw ex
            }
        }

        if (result == null) {
            stateHolder.recordBusy(trigger)
            log.debug("Acquisition rejected (busy): trigger={}", trigger)
            return AcquisitionResult.Busy
        }

        return result
    }

    // -------------------------------------------------------------------------
    // Internal orchestration
    // -------------------------------------------------------------------------

    private fun executeAcquisition(
        trigger: AcquisitionTrigger,
        gateway: EaClubsGateway,
    ): AcquisitionResult {
        val clubId = props.ea.clubId

        // Phase: FETCHING
        stateHolder.enterPhase(AcquisitionPhase.FETCHING, "Consultando a EA...")

        // Step 1: Fetch matches from EA
        val matches = when (val result = gateway.getLatestMatches(clubId)) {
            is EaApiResult.Success -> result.data
            EaApiResult.NoMatches -> {
                log.info("No matches found for club-id={}", clubId)
                stateHolder.complete("Nenhuma partida encontrada.")
                return AcquisitionResult.NoMatches
            }
            is EaApiResult.Unavailable -> {
                log.warn("EA API unavailable (HTTP {}): {}", result.statusCode, result.message)
                stateHolder.fail("EA API unavailable: ${result.message}", "EA indisponível. Nova tentativa em breve.")
                return AcquisitionResult.EaUnavailable(result.statusCode, result.message)
            }
            is EaApiResult.UnexpectedPayload -> {
                log.error("EA API returned unexpected payload", result.cause)
                stateHolder.fail("Unexpected payload: ${result.cause.message}", "EA indisponível. Nova tentativa em breve.")
                return AcquisitionResult.EaUnavailable(0, result.cause.message ?: "Unexpected payload")
            }
        }

        if (matches.isEmpty()) {
            log.info("EA returned empty match list for club-id={}", clubId)
            stateHolder.complete("Nenhuma partida encontrada.")
            return AcquisitionResult.NoMatches
        }

        // Step 2: Fetch Virtual Pro names (best-effort; fall back to empty map on any failure)
        val proNames: Map<String, String> = when (val result = gateway.getMembersStats(clubId)) {
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
        stateHolder.enterPhase(AcquisitionPhase.PROCESSING, "Processando partidas...")

        // Canonical storage is independent from presentation and Discord delivery.
        // Development fixtures remain intentionally non-persistent.
        val canonicalByMatchId = matches
            .sortedWith(compareBy<MatchResponse> { it.timestamp }.thenBy { it.matchId })
            .associate { match ->
                match.matchId to canonicalMatchFactory.create(match, clubId, proNames)
            }
        if (trigger != AcquisitionTrigger.DEV_SIMULATOR) {
            stateHolder.enterPhase(AcquisitionPhase.PERSISTING, "Salvando acervo canônico...")
            canonicalByMatchId.values.forEach(canonicalMatchRepository::save)
        }

        // Step 3: Route to appropriate processing mode
        val result = when (trigger) {
            AcquisitionTrigger.FORCE_RESEND -> processForceResend(matches, canonicalByMatchId)
            AcquisitionTrigger.MANUAL, AcquisitionTrigger.CLI -> processLatestOnly(matches, canonicalByMatchId)
            AcquisitionTrigger.SCHEDULER -> processAllNew(matches, canonicalByMatchId)
            AcquisitionTrigger.DEV_SIMULATOR -> processSimulation(matches, canonicalByMatchId)
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
                stateHolder.complete(status)
            }
            is AcquisitionResult.ForceResent -> {
                stateHolder.complete("Partida reenviada com sucesso.")
            }
            AcquisitionResult.WebhookNotConfigured -> {
                stateHolder.fail("Webhook not configured", "Webhook não configurado.")
            }
            else -> {
                stateHolder.complete("Aquisição concluída.")
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
        matches: List<MatchResponse>,
        canonicalByMatchId: Map<String, CanonicalMatch>,
    ): AcquisitionResult {
        val latest = matches.maxByOrNull { it.timestamp }
            ?: return AcquisitionResult.NoMatches
        val canonical = canonicalByMatchId.getValue(latest.matchId)

        val summary = buildSummary(canonical)
        val publishedIds = store.loadIds()

        // Phase: CACHING - Generate and cache presentation BEFORE deduplication check
        stateHolder.enterPhase(AcquisitionPhase.CACHING, "Atualizando cache...")
        val presentation = buildDashboardPresentation(canonical)
        val newVersion = latestMatchHolder.update(presentation)
        log.debug("Cached presentation for match {} (version={})", latest.matchId, newVersion)

        // A new environment has no publication history. Establish the complete
        // returned EA window as its baseline before any normal publication,
        // regardless of whether the first trigger is manual, CLI or scheduled.
        if (publishedIds.isEmpty()) {
            return establishBaseline(matches)
        }

        // Check deduplication AFTER caching
        if (latest.matchId in publishedIds) {
            log.info("Match {} already published, skipping Discord delivery", latest.matchId)
            return AcquisitionResult.Processed(
                published = emptyList(),
                alreadyPublished = listOf(AcquisitionResult.MatchSummary(latest.matchId, summary)),
                failed = emptyList(),
            )
        }

        // Phase: DELIVERING
        stateHolder.enterPhase(AcquisitionPhase.DELIVERING, "Enviando para Discord...")

        // Deliver to Discord
        val deliveryResult = deliverToDiscord(canonical)
        if (deliveryResult != null) {
            return deliveryResult
        }

        // Phase: PERSISTING
        stateHolder.enterPhase(AcquisitionPhase.PERSISTING, "Salvando histórico...")

        val persisted = persistMatch(latest.matchId, publishedIds)

        log.info("Published match {}", latest.matchId)
        return AcquisitionResult.Processed(
            published = listOf(AcquisitionResult.MatchSummary(latest.matchId, summary, persisted)),
            alreadyPublished = emptyList(),
            failed = emptyList(),
        )
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
        matches: List<MatchResponse>,
        canonicalByMatchId: Map<String, CanonicalMatch>,
    ): AcquisitionResult {
        val latestMatch = matches.maxByOrNull { it.timestamp }
            ?: return AcquisitionResult.NoMatches
        val canonical = canonicalByMatchId.getValue(latestMatch.matchId)

        val summary = buildSummary(canonical)

        // Phase: CACHING - Generate and cache presentation (marked as simulated)
        // Use forceRandomPhrases=true so each simulation gets new random phrases
        stateHolder.enterPhase(AcquisitionPhase.CACHING, "Gerando card simulado...")
        val presentation = buildDashboardPresentation(
            canonical,
            forceRandomPhrases = true,
        )
        val newVersion = latestMatchHolder.update(presentation, simulated = true)
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
        matches: List<MatchResponse>,
        canonicalByMatchId: Map<String, CanonicalMatch>,
    ): AcquisitionResult {
        val publishedIds = store.loadIds()

        // First-run detection
        if (publishedIds.isEmpty()) {
            return handleFirstRun(matches, canonicalByMatchId)
        }

        // Find the latest match for caching (regardless of publication status)
        val latestMatch = matches.maxByOrNull { it.timestamp }

        // Phase: CACHING - Cache the latest presentation BEFORE checking deduplication
        if (latestMatch != null) {
            stateHolder.enterPhase(AcquisitionPhase.CACHING, "Atualizando cache...")
            val presentation = buildDashboardPresentation(canonicalByMatchId.getValue(latestMatch.matchId))
            val newVersion = latestMatchHolder.update(presentation)
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

        // Phase: DELIVERING (for multiple matches)
        stateHolder.enterPhase(AcquisitionPhase.DELIVERING, "Enviando ${newMatches.size} partida(s)...")

        // Process each match
        val published = mutableListOf<AcquisitionResult.MatchSummary>()
        val failed = mutableListOf<AcquisitionResult.MatchFailure>()
        val currentIds = publishedIds.toMutableSet()

        for ((index, match) in newMatches.withIndex()) {
            val canonical = canonicalByMatchId.getValue(match.matchId)
            val summary = buildSummary(canonical)
            stateHolder.enterPhase(AcquisitionPhase.DELIVERING, "Enviando partida ${index + 1}/${newMatches.size}...")

            try {
                webhookClient.send(buildDiscordPayload(canonical))

                stateHolder.enterPhase(AcquisitionPhase.PERSISTING, "Salvando partida ${index + 1}/${newMatches.size}...")

                currentIds += match.matchId
                val persisted = try {
                    store.saveIds(currentIds)
                    log.info("Published match {}", match.matchId)
                    true
                } catch (ex: Exception) {
                    log.error("Persistence failed after Discord delivery for match {}", match.matchId, ex)
                    false
                }

                published += AcquisitionResult.MatchSummary(match.matchId, summary, persisted)

            } catch (ex: IllegalStateException) {
                log.error("Discord webhook not configured — aborting cycle: {}", ex.message)
                return AcquisitionResult.WebhookNotConfigured
            } catch (ex: DiscordDeliveryException) {
                log.warn("Discord delivery failed for match {} — will retry next cycle: {}", match.matchId, ex.message)
                failed += AcquisitionResult.MatchFailure(match.matchId, summary, ex.message ?: "Delivery failed")
            } catch (ex: Exception) {
                log.error("Unexpected error publishing match {}", match.matchId, ex)
                failed += AcquisitionResult.MatchFailure(match.matchId, summary, ex.message ?: "Unexpected error")
            }
        }

        return AcquisitionResult.Processed(
            published = published,
            alreadyPublished = emptyList(),
            failed = failed,
        )
    }

    /**
     * Handles first use without publishing the existing EA window.
     */
    private fun handleFirstRun(
        matches: List<MatchResponse>,
        canonicalByMatchId: Map<String, CanonicalMatch>,
    ): AcquisitionResult {
        // Cache the latest presentation regardless of publish mode
        val latestMatch = matches.maxByOrNull { it.timestamp }
        if (latestMatch != null) {
            stateHolder.enterPhase(AcquisitionPhase.CACHING, "Atualizando cache...")
            val presentation = buildDashboardPresentation(canonicalByMatchId.getValue(latestMatch.matchId))
            val newVersion = latestMatchHolder.update(presentation)
            log.debug("Cached presentation for match {} (version={})", latestMatch.matchId, newVersion)
        }

        return establishBaseline(matches)
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
     * Force resend the latest match, bypassing deduplication.
     * Does not modify the published IDs store.
     */
    private fun processForceResend(
        matches: List<MatchResponse>,
        canonicalByMatchId: Map<String, CanonicalMatch>,
    ): AcquisitionResult {
        val latest = matches.maxByOrNull { it.timestamp }
            ?: return AcquisitionResult.NoMatches
        val canonical = canonicalByMatchId.getValue(latest.matchId)

        val summary = buildSummary(canonical)
        val alreadyPublished = store.loadIds().contains(latest.matchId)

        // Phase: CACHING - Generate and cache presentation
        stateHolder.enterPhase(AcquisitionPhase.CACHING, "Atualizando cache...")
        val presentation = buildDashboardPresentation(canonical)
        val newVersion = latestMatchHolder.update(presentation)
        log.debug("Cached presentation for match {} (version={})", latest.matchId, newVersion)

        // Phase: DELIVERING
        stateHolder.enterPhase(AcquisitionPhase.DELIVERING, "Reenviando para Discord...")

        // Deliver to Discord (no dedup check)
        val deliveryResult = deliverToDiscord(canonical)
        if (deliveryResult != null) {
            return deliveryResult
        }

        // Do NOT persist — force resend doesn't affect the store
        log.info("Force-resent match {} (already published: {})", latest.matchId, alreadyPublished)

        return AcquisitionResult.ForceResent(
            match = AcquisitionResult.MatchSummary(latest.matchId, summary)
        )
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Delivers a match to the configured Discord webhook.
     * @return An error result if delivery failed, or null if successful.
     */
    private fun deliverToDiscord(
        canonical: CanonicalMatch,
    ): AcquisitionResult? {
        try {
            webhookClient.send(buildDiscordPayload(canonical))
        } catch (ex: IllegalStateException) {
            log.error("Discord webhook not configured: {}", ex.message)
            return AcquisitionResult.WebhookNotConfigured
        } catch (ex: DiscordDeliveryException) {
            log.warn("Discord delivery failed for match {}: {}", canonical.matchId.value, ex.message)
            // For single-match operations, return as a Processed with failure
            val summary = buildSummary(canonical)
            return AcquisitionResult.Processed(
                published = emptyList(),
                alreadyPublished = emptyList(),
                failed = listOf(AcquisitionResult.MatchFailure(
                    canonical.matchId.value,
                    summary,
                    ex.message ?: "Delivery failed"
                )),
            )
        }

        return null // Success
    }

    /**
     * Persists a match ID to the published store.
     * @return true if persistence succeeded, false otherwise.
     */
    private fun persistMatch(matchId: String, existingIds: Set<String>): Boolean {
        return try {
            store.saveIds(existingIds + matchId)
            true
        } catch (ex: Exception) {
            log.error("Discord delivery succeeded but persistence failed for match {}", matchId, ex)
            false
        }
    }

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

    private fun buildDiscordPayload(
        canonical: CanonicalMatch,
    ): DiscordPayload {
        return discordRenderer.renderMatch(
            canonical.footballMatch,
            canonical.interpretation,
            canonical.stories,
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
            "${interpretation.result.ourScore.goals} × ${interpretation.result.opponentScore.goals} " +
            (opponent.club.name?.value ?: "Adversário")
    }
}
