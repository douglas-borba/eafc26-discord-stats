package com.eafc26.discordstats.discord

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.WebClientRequestException

/**
 * Sends a Discord payload to a destination already resolved for its club.
 * Secret selection remains outside this transport client.
 */
@Component
class DiscordWebhookClient(
    private val objectMapper: ObjectMapper,
    private val webClient: WebClient = WebClient.create(),
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Sends [payload] to the Discord webhook.
     *
     * @throws DiscordDeliveryException if Discord rejects the request.
     */
    fun send(destination: DiscordDestination, payload: DiscordPayload) {
        val body = objectMapper.writeValueAsString(payload)
        log.debug("Sending Discord payload to webhook ({} chars)", body.length)

        try {
            webClient.post()
                .uri(destination.webhookUrl)
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .block()
            log.debug("Discord webhook delivery succeeded")
        } catch (ex: WebClientResponseException) {
            throw DiscordDeliveryException(
                "Discord rejected the match webhook (HTTP ${ex.statusCode.value()}).",
                ex,
            )
        } catch (ex: WebClientRequestException) {
            throw DiscordDeliveryException("Discord match webhook request failed.", ex)
        } catch (ex: Exception) {
            throw DiscordDeliveryException("Discord match webhook delivery failed.", ex)
        }
    }

}

class DiscordDeliveryException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
