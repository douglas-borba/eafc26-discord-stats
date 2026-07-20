package com.eafc26.discordstats.service

import com.eafc26.discordstats.config.AppProperties
import com.eafc26.discordstats.discord.DiscordDeliveryException
import com.eafc26.discordstats.discord.DiscordEmbedBuilder
import com.eafc26.discordstats.discord.DiscordWebhookClient
import com.eafc26.discordstats.discord.HistoryEmbedBuilder
import com.eafc26.discordstats.ea.EaApiResult
import com.eafc26.discordstats.ea.EaClubsGateway
import com.eafc26.discordstats.ea.model.MatchResponse
import com.eafc26.discordstats.presentation.MatchSummaryBuilder
import com.eafc26.discordstats.store.PublishedMatchStore
import org.slf4j.LoggerFactory
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
 * 3. Applies deduplication
 * 4. Generates presentation and caches in [LatestMatchHolder]
 * 5. Delivers to Discord webhooks (if applicable)
 * 6. Persists published match IDs
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
    private val defaultGateway: EaClubsGateway,
    private val store: PublishedMatchStore,
    private val webhookClient: DiscordWebhookClient,
    private val props: AppProperties,
    private val stateHolder: AcquisitionStateHolder,
    private val latestMatchHolder: LatestMatchHolder,
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

        // Phase: PROCESSING
        stateHolder.enterPhase(AcquisitionPhase.PROCESSING, "Processando partidas...")

        // Step 2: Route to appropriate processing mode
        val result = when (trigger) {
            AcquisitionTrigger.FORCE_RESEND -> processForceResend(matches, clubId)
            AcquisitionTrigger.MANUAL, AcquisitionTrigger.CLI -> processLatestOnly(matches, clubId)
            AcquisitionTrigger.SCHEDULER, AcquisitionTrigger.DEV_SIMULATOR -> processAllNew(matches, clubId)
        }

        // Update final state based on result
        when (result) {
            is AcquisitionResult.Processed -> {
                val status = when {
                    result.baselineEstablished -> "Histórico inicial configurado."
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
        clubId: String,
    ): AcquisitionResult {
        val latest = matches.maxByOrNull { it.timestamp }
            ?: return AcquisitionResult.NoMatches

        val summary = buildSummary(latest, clubId)
        val publishedIds = store.loadIds()

        // Phase: CACHING - Generate and cache presentation BEFORE deduplication check
        stateHolder.enterPhase(AcquisitionPhase.CACHING, "Atualizando cache...")
        val presentation = MatchSummaryBuilder.build(latest, clubId)
        val newVersion = latestMatchHolder.update(presentation)
        log.debug("Cached presentation for match {} (version={})", latest.matchId, newVersion)

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
        val deliveryResult = deliverToDiscord(latest, clubId)
        if (deliveryResult != null) {
            // Discord failed, but presentation was already cached - return the failure
            return deliveryResult
        }

        // Phase: PERSISTING
        stateHolder.enterPhase(AcquisitionPhase.PERSISTING, "Salvando histórico...")

        // Persist
        val persisted = persistMatch(latest.matchId, publishedIds)

        log.info("Published match {}", latest.matchId)
        return AcquisitionResult.Processed(
            published = listOf(AcquisitionResult.MatchSummary(latest.matchId, summary, persisted)),
            alreadyPublished = emptyList(),
            failed = emptyList(),
        )
    }

    /**
     * Process all new matches (for scheduler/simulator triggers).
     * Handles first-run logic with [publishExistingOnFirstRun].
     */
    private fun processAllNew(
        matches: List<MatchResponse>,
        clubId: String,
    ): AcquisitionResult {
        val publishedIds = store.loadIds()

        // First-run detection
        if (publishedIds.isEmpty()) {
            return handleFirstRun(matches, clubId)
        }

        // Find the latest match for caching (regardless of publication status)
        val latestMatch = matches.maxByOrNull { it.timestamp }

        // Phase: CACHING - Cache the latest presentation BEFORE checking deduplication
        if (latestMatch != null) {
            stateHolder.enterPhase(AcquisitionPhase.CACHING, "Atualizando cache...")
            val presentation = MatchSummaryBuilder.build(latestMatch, clubId)
            val newVersion = latestMatchHolder.update(presentation)
            log.debug("Cached presentation for match {} (version={})", latestMatch.matchId, newVersion)
        }

        // Find new matches to publish
        val newMatches = matches
            .filter { it.matchId !in publishedIds }
            .sortedBy { it.timestamp }

        if (newMatches.isEmpty()) {
            log.debug("No new matches to publish")
            val latestSummary = latestMatch?.let { buildSummary(it, clubId) }
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
            val summary = buildSummary(match, clubId)
            stateHolder.enterPhase(AcquisitionPhase.DELIVERING, "Enviando partida ${index + 1}/${newMatches.size}...")

            try {
                // Deliver to Discord
                val payload = DiscordEmbedBuilder.build(match, clubId)
                webhookClient.send(payload)
                webhookClient.sendHistory(HistoryEmbedBuilder.build(match, clubId))

                // Phase: PERSISTING (per match)
                stateHolder.enterPhase(AcquisitionPhase.PERSISTING, "Salvando partida ${index + 1}/${newMatches.size}...")

                // Persist incrementally
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
                // Webhook not configured — abort entire cycle
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
     * Handle first run based on [publishExistingOnFirstRun] setting.
     */
    private fun handleFirstRun(
        matches: List<MatchResponse>,
        clubId: String,
    ): AcquisitionResult {
        // Cache the latest presentation regardless of publish mode
        val latestMatch = matches.maxByOrNull { it.timestamp }
        if (latestMatch != null) {
            stateHolder.enterPhase(AcquisitionPhase.CACHING, "Atualizando cache...")
            val presentation = MatchSummaryBuilder.build(latestMatch, clubId)
            val newVersion = latestMatchHolder.update(presentation)
            log.debug("Cached presentation for match {} (version={})", latestMatch.matchId, newVersion)
        }

        if (props.polling.publishExistingOnFirstRun) {
            log.info("First run with publish-existing-on-first-run=true: will publish {} match(es)", matches.size)
            return publishExistingMatches(matches, clubId)
        } else {
            log.info("Automatic polling baseline established with {} matches.", matches.size)
            store.saveIds(matches.map { it.matchId }.toSet())
            return AcquisitionResult.Processed(
                published = emptyList(),
                alreadyPublished = emptyList(),
                failed = emptyList(),
                baselineEstablished = true,
            )
        }
    }

    /**
     * Publish all existing matches on first run.
     */
    private fun publishExistingMatches(
        matches: List<MatchResponse>,
        clubId: String,
    ): AcquisitionResult {
        val sortedMatches = matches.sortedBy { it.timestamp }
        val published = mutableListOf<AcquisitionResult.MatchSummary>()
        val failed = mutableListOf<AcquisitionResult.MatchFailure>()
        val currentIds = mutableSetOf<String>()

        // Initialize empty store
        store.saveIds(currentIds)

        for (match in sortedMatches) {
            val summary = buildSummary(match, clubId)

            try {
                val payload = DiscordEmbedBuilder.build(match, clubId)
                webhookClient.send(payload)
                webhookClient.sendHistory(HistoryEmbedBuilder.build(match, clubId))

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
            baselineEstablished = false, // Not a baseline — we published matches
        )
    }

    /**
     * Force resend the latest match, bypassing deduplication.
     * Does not modify the published IDs store.
     */
    private fun processForceResend(
        matches: List<MatchResponse>,
        clubId: String,
    ): AcquisitionResult {
        val latest = matches.maxByOrNull { it.timestamp }
            ?: return AcquisitionResult.NoMatches

        val summary = buildSummary(latest, clubId)
        val alreadyPublished = store.loadIds().contains(latest.matchId)

        // Phase: CACHING - Generate and cache presentation
        stateHolder.enterPhase(AcquisitionPhase.CACHING, "Atualizando cache...")
        val presentation = MatchSummaryBuilder.build(latest, clubId)
        val newVersion = latestMatchHolder.update(presentation)
        log.debug("Cached presentation for match {} (version={})", latest.matchId, newVersion)

        // Phase: DELIVERING
        stateHolder.enterPhase(AcquisitionPhase.DELIVERING, "Reenviando para Discord...")

        // Deliver to Discord (no dedup check)
        val deliveryResult = deliverToDiscord(latest, clubId)
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
     * Delivers a match to Discord webhooks.
     * @return An error result if delivery failed, or null if successful.
     */
    private fun deliverToDiscord(
        match: MatchResponse,
        clubId: String,
    ): AcquisitionResult? {
        val payload = DiscordEmbedBuilder.build(match, clubId)

        try {
            webhookClient.send(payload)
        } catch (ex: IllegalStateException) {
            log.error("Discord webhook not configured: {}", ex.message)
            return AcquisitionResult.WebhookNotConfigured
        } catch (ex: DiscordDeliveryException) {
            log.warn("Discord delivery failed for match {}: {}", match.matchId, ex.message)
            // For single-match operations, return as a Processed with failure
            val summary = buildSummary(match, clubId)
            return AcquisitionResult.Processed(
                published = emptyList(),
                alreadyPublished = emptyList(),
                failed = listOf(AcquisitionResult.MatchFailure(
                    match.matchId,
                    summary,
                    ex.message ?: "Delivery failed"
                )),
            )
        }

        // History webhook — optional, fire-and-forget
        webhookClient.sendHistory(HistoryEmbedBuilder.build(match, clubId))

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

    /**
     * Builds a human-readable summary of a match.
     */
    private fun buildSummary(match: MatchResponse, clubId: String): String {
        val ourEntry = match.clubs[clubId]
        val oppEntry = match.clubs.entries.firstOrNull { it.key != clubId }
        val ourName = ourEntry?.resolvedName() ?: props.ea.clubName
        val oppName = oppEntry?.value?.resolvedName() ?: "Adversário"
        val ourScore = ourEntry?.score?.toIntOrNull() ?: 0
        val oppScore = oppEntry?.value?.score?.toIntOrNull() ?: 0
        return "$ourName $ourScore × $oppScore $oppName"
    }
}

