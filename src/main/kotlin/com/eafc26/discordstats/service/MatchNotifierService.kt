package com.eafc26.discordstats.service

import com.eafc26.discordstats.config.AppProperties
import com.eafc26.discordstats.discord.DiscordDeliveryException
import com.eafc26.discordstats.discord.DiscordEmbedBuilder
import com.eafc26.discordstats.discord.DiscordWebhookClient
import com.eafc26.discordstats.ea.EaApiResult
import com.eafc26.discordstats.ea.EaClubsGateway
import com.eafc26.discordstats.store.PublishedMatchStore
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class MatchNotifierService(
    private val gateway: EaClubsGateway,
    private val store: PublishedMatchStore,
    private val discord: DiscordWebhookClient,
    private val props: AppProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * One polling cycle:
     * 1. Fetch latest matches from EA.
     * 2. On first run (empty store) establish a baseline or publish all, per config.
     * 3. Otherwise, send each unpublished match oldest-first.
     *    A match is marked published only after Discord confirms delivery.
     */
    fun process() {
        val clubId = props.ea.clubId

        val matches = when (val result = gateway.getLatestMatches(clubId)) {
            is EaApiResult.Success -> result.data
            EaApiResult.NoMatches -> {
                log.info("No matches found for club-id={}", clubId)
                return
            }
            is EaApiResult.Unavailable -> {
                log.warn("EA API unavailable (HTTP {}): {}", result.statusCode, result.message)
                return
            }
            is EaApiResult.UnexpectedPayload -> {
                log.error("EA API returned unexpected payload", result.cause)
                return
            }
        }

        val publishedIds = store.loadIds()

        if (publishedIds.isEmpty()) {
            handleFirstRun(matches)
            return
        }

        val newMatches = matches
            .filter { it.matchId !in publishedIds }
            .sortedBy { it.timestamp }

        if (newMatches.isEmpty()) {
            log.debug("No new matches to publish")
            return
        }

        log.info("Found {} new match(es) to publish", newMatches.size)

        val updatedIds = publishedIds.toMutableSet()
        for (match in newMatches) {
            try {
                val payload = DiscordEmbedBuilder.build(match, clubId)
                discord.send(payload)
                updatedIds += match.matchId
                store.saveIds(updatedIds)
                log.info("Published match {}", match.matchId)
            } catch (ex: IllegalStateException) {
                log.error("Discord webhook not configured — aborting cycle: {}", ex.message)
                return
            } catch (ex: DiscordDeliveryException) {
                log.warn("Discord delivery failed for match {} — will retry next cycle: {}", match.matchId, ex.message)
            } catch (ex: Exception) {
                log.error("Unexpected error publishing match {}", match.matchId, ex)
            }
        }
    }

    private fun handleFirstRun(matches: List<com.eafc26.discordstats.ea.model.MatchResponse>) {
        if (props.polling.publishExistingOnFirstRun) {
            log.info("First run with publish-existing-on-first-run=true: will publish {} match(es)", matches.size)
            val publishedIds = mutableSetOf<String>()
            store.saveIds(publishedIds)
            for (match in matches.sortedBy { it.timestamp }) {
                try {
                    val payload = DiscordEmbedBuilder.build(match, props.ea.clubId)
                    discord.send(payload)
                    publishedIds += match.matchId
                    store.saveIds(publishedIds)
                    log.info("Published match {}", match.matchId)
                } catch (ex: IllegalStateException) {
                    log.error("Discord webhook not configured — aborting cycle: {}", ex.message)
                    return
                } catch (ex: DiscordDeliveryException) {
                    log.warn("Discord delivery failed for match {} — will retry next cycle: {}", match.matchId, ex.message)
                } catch (ex: Exception) {
                    log.error("Unexpected error publishing match {}", match.matchId, ex)
                }
            }
        } else {
            log.info(
                "First run: establishing baseline of {} match(es), not publishing existing matches",
                matches.size,
            )
            store.saveIds(matches.map { it.matchId }.toSet())
        }
    }
}
