package com.eafc26.discordstats.discord

import com.eafc26.discordstats.config.WebhookConfigService
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException

/**
 * Sends a Discord embed to the configured webhook URL.
 *
 * The URL is managed by [WebhookConfigService] and can be updated at runtime
 * via the /setup page without restarting the application.
 * A blank URL throws [IllegalStateException] with a setup redirect hint.
 */
@Component
class DiscordWebhookClient(
    private val webhookConfigService: WebhookConfigService,
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
        val url = webhookConfigService.getWebhookUrl()
        if (url.isBlank()) {
            throw IllegalStateException(
                "Discord webhook URL is not configured. Open http://localhost:8080/setup to configure it."
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

    /**
     * Sends [payload] to the history webhook if configured.
     * Never throws — logs a warning on failure so the main notification flow is unaffected.
     */
    fun sendHistory(payload: DiscordPayload) {
        val url = webhookConfigService.getHistoryWebhookUrl()
        if (url.isBlank()) return
        val body = objectMapper.writeValueAsString(payload)
        try {
            webClient.post()
                .uri(url)
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .block()
            log.debug("History webhook delivery succeeded")
        } catch (ex: Exception) {
            log.warn("History webhook delivery failed (non-fatal): {}", ex.message)
        }
    }
}

class DiscordDeliveryException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
