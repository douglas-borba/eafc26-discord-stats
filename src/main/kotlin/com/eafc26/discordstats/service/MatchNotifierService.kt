package com.eafc26.discordstats.service

import com.eafc26.discordstats.config.AppProperties
import com.eafc26.discordstats.discord.DiscordDeliveryException
import com.eafc26.discordstats.discord.DiscordEmbedBuilder
import com.eafc26.discordstats.discord.DiscordWebhookClient
import com.eafc26.discordstats.discord.HistoryEmbedBuilder
import com.eafc26.discordstats.ea.EaApiResult
import com.eafc26.discordstats.ea.EaClubsGateway
import com.eafc26.discordstats.ea.model.MatchResponse
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
     * One polling cycle. [statusCallback] receives a human-readable result message
     * that the scheduler can expose via the status endpoint. Defaults to a no-op so
     * existing callers (CLI, tests) remain unchanged.
     */
    fun process(statusCallback: (String) -> Unit = {}) {
        val clubId = props.ea.clubId

        val matches = when (val result = gateway.getLatestMatches(clubId)) {
            is EaApiResult.Success -> result.data
            EaApiResult.NoMatches -> {
                log.info("No matches found for club-id={}", clubId)
                statusCallback("Nenhuma partida nova.")
                return
            }
            is EaApiResult.Unavailable -> {
                log.warn("EA API unavailable (HTTP {}): {}", result.statusCode, result.message)
                statusCallback("EA indisponível. Nova tentativa em 1 minuto.")
                return
            }
            is EaApiResult.UnexpectedPayload -> {
                log.error("EA API returned unexpected payload", result.cause)
                statusCallback("EA indisponível. Nova tentativa em 1 minuto.")
                return
            }
        }

        val publishedIds = store.loadIds()

        if (publishedIds.isEmpty()) {
            handleFirstRun(matches, statusCallback)
            return
        }

        val newMatches = matches
            .filter { it.matchId !in publishedIds }
            .sortedBy { it.timestamp }

        if (newMatches.isEmpty()) {
            log.debug("No new matches to publish")
            statusCallback("Nenhuma partida nova.")
            return
        }

        log.info("Found {} new match(es) to publish", newMatches.size)

        val updatedIds = publishedIds.toMutableSet()
        var lastStatus = "Nenhuma partida nova."
        for (match in newMatches) {
            try {
                val payload = DiscordEmbedBuilder.build(match, clubId)
                discord.send(payload)
                discord.sendHistory(HistoryEmbedBuilder.build(match, clubId))
                updatedIds += match.matchId
                try {
                    store.saveIds(updatedIds)
                    log.info("Published match {}", match.matchId)
                    lastStatus = "Partida enviada com sucesso."
                } catch (ex: Exception) {
                    log.error("Persistence failed after Discord delivery for match {}", match.matchId, ex)
                    lastStatus = "Partida enviada, mas o histórico local não pôde ser salvo."
                }
            } catch (ex: IllegalStateException) {
                log.error("Discord webhook not configured — aborting cycle: {}", ex.message)
                statusCallback("EA indisponível. Nova tentativa em 1 minuto.")
                return
            } catch (ex: DiscordDeliveryException) {
                log.warn("Discord delivery failed for match {} — will retry next cycle: {}", match.matchId, ex.message)
                lastStatus = "EA indisponível. Nova tentativa em 1 minuto."
            } catch (ex: Exception) {
                log.error("Unexpected error publishing match {}", match.matchId, ex)
                lastStatus = "EA indisponível. Nova tentativa em 1 minuto."
            }
        }
        statusCallback(lastStatus)
    }

    private fun handleFirstRun(matches: List<MatchResponse>, statusCallback: (String) -> Unit) {
        if (props.polling.publishExistingOnFirstRun) {
            log.info("First run with publish-existing-on-first-run=true: will publish {} match(es)", matches.size)
            val publishedIds = mutableSetOf<String>()
            store.saveIds(publishedIds)
            for (match in matches.sortedBy { it.timestamp }) {
                try {
                    val payload = DiscordEmbedBuilder.build(match, props.ea.clubId)
                    discord.send(payload)
                    discord.sendHistory(HistoryEmbedBuilder.build(match, props.ea.clubId))
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
            statusCallback("Partida enviada com sucesso.")
        } else {
            log.info("Automatic polling baseline established with {} matches.", matches.size)
            store.saveIds(matches.map { it.matchId }.toSet())
            statusCallback("Histórico inicial configurado. Aguardando novas partidas.")
        }
    }
}
