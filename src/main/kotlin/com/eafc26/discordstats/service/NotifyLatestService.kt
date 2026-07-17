package com.eafc26.discordstats.service

import com.eafc26.discordstats.config.AppProperties
import com.eafc26.discordstats.discord.DiscordDeliveryException
import com.eafc26.discordstats.discord.DiscordEmbedBuilder
import com.eafc26.discordstats.discord.DiscordWebhookClient
import com.eafc26.discordstats.ea.EaApiResult
import com.eafc26.discordstats.ea.EaClubsGateway
import com.eafc26.discordstats.ea.model.MatchResponse
import com.eafc26.discordstats.store.PublishedMatchStore
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Shared service used by both the web controller and the CLI notify-latest command.
 *
 * The [busy] AtomicBoolean is the single concurrency guard for ALL verification
 * flows — both manual (via [notifyLatest]) and scheduled (via [runIfIdle]).
 * This guarantees that manual and automatic executions never run concurrently,
 * preventing duplicate EA fetches and duplicate Discord deliveries.
 */
@Service
class NotifyLatestService(
    private val gateway: EaClubsGateway,
    private val store: PublishedMatchStore,
    private val webhookClient: DiscordWebhookClient,
    private val props: AppProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val busy = AtomicBoolean(false)

    fun notifyLatest(): NotifyResult {
        if (!busy.compareAndSet(false, true)) {
            log.info("notifyLatest skipped — another execution is already in progress")
            return NotifyResult.Busy
        }
        return try {
            execute()
        } finally {
            busy.set(false)
        }
    }

    /**
     * Acquires the shared concurrency guard and runs [action].
     * Returns false (and skips [action]) if already busy.
     * Used by the scheduler so it shares the same guard as the manual button.
     */
    fun runIfIdle(action: () -> Unit): Boolean {
        if (!busy.compareAndSet(false, true)) return false
        try {
            action()
        } finally {
            busy.set(false)
        }
        return true
    }

    private fun execute(): NotifyResult {
        val clubId = props.ea.clubId

        val matches = when (val result = gateway.getLatestMatches(clubId)) {
            is EaApiResult.Success -> result.data
            EaApiResult.NoMatches -> {
                log.info("No matches found for club-id={}", clubId)
                return NotifyResult.NoMatches
            }
            is EaApiResult.Unavailable -> {
                log.warn("EA API unavailable (HTTP {}): {}", result.statusCode, result.message)
                return NotifyResult.EaUnavailable
            }
            is EaApiResult.UnexpectedPayload -> {
                log.error("EA API returned unexpected payload", result.cause)
                return NotifyResult.EaUnavailable
            }
        }

        val latest = matches.maxByOrNull { it.timestamp }
            ?: return NotifyResult.NoMatches

        val summary = buildSummary(latest)

        if (store.loadIds().contains(latest.matchId)) {
            log.info("Match {} already published, skipping", latest.matchId)
            return NotifyResult.AlreadyPublished(summary)
        }

        val payload = DiscordEmbedBuilder.build(latest, clubId)
        try {
            webhookClient.send(payload)
        } catch (ex: IllegalStateException) {
            log.error("Discord webhook not configured: {}", ex.message)
            return NotifyResult.DiscordError
        } catch (ex: DiscordDeliveryException) {
            log.warn("Discord delivery failed for match {}: {}", latest.matchId, ex.message)
            return NotifyResult.DiscordError
        }

        // Discord delivery confirmed — now persist. A persistence failure must not
        // suppress the delivery confirmation or trigger a second Discord send.
        return try {
            store.saveIds(store.loadIds() + latest.matchId)
            log.info("Published match {}", latest.matchId)
            NotifyResult.Sent(summary)
        } catch (ex: Exception) {
            log.error("Discord delivery succeeded but persistence failed for match {}", latest.matchId, ex)
            NotifyResult.SentPersistenceError(summary)
        }
    }

    private fun buildSummary(match: MatchResponse): String {
        val clubId = props.ea.clubId
        val ourEntry = match.clubs[clubId]
        val oppEntry = match.clubs.entries.firstOrNull { it.key != clubId }
        val ourName = ourEntry?.resolvedName() ?: props.ea.clubName
        val oppName = oppEntry?.value?.resolvedName() ?: "Adversário"
        val ourScore = ourEntry?.score?.toIntOrNull() ?: 0
        val oppScore = oppEntry?.value?.score?.toIntOrNull() ?: 0
        return "$ourName $ourScore × $oppScore $oppName"
    }
}
