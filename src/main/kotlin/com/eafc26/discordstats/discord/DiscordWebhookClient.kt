package com.eafc26.discordstats.discord

import com.eafc26.discordstats.config.AppProperties
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException

/**
 * Sends a Discord embed to the configured webhook URL.
 *
 * The webhook URL is read from the DISCORD_WEBHOOK_URL environment variable
 * via application.yml: `app.discord.webhook-url: ${DISCORD_WEBHOOK_URL:}`.
 * A blank URL produces a clear configuration error rather than a network failure.
 */
@Component
class DiscordWebhookClient(
    private val props: AppProperties,
    private val objectMapper: ObjectMapper,
    private val webClient: WebClient = WebClient.create(),
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Sends [payload] to the Discord webhook.
     *
     * @throws IllegalStateException if the webhook URL is not configured.
     * @throws DiscordDeliveryException if Discord rejects the request.
     */
    fun send(payload: DiscordPayload) {
        val url = props.discord.webhookUrl
        if (url.isBlank()) {
            throw IllegalStateException(
                "Discord webhook URL is not configured. " +
                "Set the DISCORD_WEBHOOK_URL environment variable."
            )
        }

        val body = objectMapper.writeValueAsString(payload)
        log.debug("Sending Discord payload to webhook ({} chars)", body.length)

        try {
            webClient.post()
                .uri(url)
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .block()
            log.debug("Discord webhook delivery succeeded")
        } catch (ex: WebClientResponseException) {
            throw DiscordDeliveryException(
                "Discord rejected the webhook (HTTP ${ex.statusCode.value()}): ${ex.responseBodyAsString}",
                ex,
            )
        }
    }
}

class DiscordDeliveryException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
