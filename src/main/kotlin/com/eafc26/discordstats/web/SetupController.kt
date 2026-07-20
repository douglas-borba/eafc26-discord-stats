package com.eafc26.discordstats.web

import com.eafc26.discordstats.config.WebhookConfigService
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.net.URI

@RestController
class SetupController(private val webhookConfigService: WebhookConfigService) {

    @GetMapping("/setup", produces = [MediaType.TEXT_HTML_VALUE])
    fun setupPage(): ResponseEntity<ClassPathResource> =
        ResponseEntity.ok()
            .contentType(MediaType.TEXT_HTML)
            .body(ClassPathResource("setup.html"))

    /** Returns current configuration status for both webhooks. */
    @GetMapping("/api/setup/webhook", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getWebhookInfo(): Mono<ResponseEntity<Map<String, Any>>> =
        Mono.fromCallable {
            ResponseEntity.ok(
                mapOf(
                    "configured"       to webhookConfigService.isConfigured(),
                    "historyConfigured" to webhookConfigService.isHistoryConfigured(),
                    "url"              to webhookConfigService.getWebhookUrl(),
                    "historyUrl"       to webhookConfigService.getHistoryWebhookUrl(),
                    // kept for backward-compat with older JS that reads maskedUrl
                    "maskedUrl"        to webhookConfigService.getMaskedWebhookUrl(),
                )
            )
        }

    /** Saves both webhooks. Both fields are required and must be valid Discord webhook URLs. */
    @PostMapping("/api/setup/webhook", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun saveWebhook(@RequestBody body: WebhookSetupRequest): Mono<ResponseEntity<Map<String, String>>> =
        Mono.fromCallable {
            val errors = mutableMapOf<String, String>()

            if (body.webhookUrl.isBlank()) {
                errors["statsError"] = "Informe a URL do webhook do canal de estatísticas."
            } else {
                try { webhookConfigService.validateUrl(body.webhookUrl.trim()) }
                catch (ex: IllegalArgumentException) { errors["statsError"] = ex.message ?: "URL inválida." }
            }

            if (body.historyWebhookUrl.isBlank()) {
                errors["historyError"] = "Informe a URL do webhook do canal de histórico."
            } else {
                try { webhookConfigService.validateUrl(body.historyWebhookUrl.trim()) }
                catch (ex: IllegalArgumentException) { errors["historyError"] = ex.message ?: "URL inválida." }
            }

            if (errors.isNotEmpty()) {
                return@fromCallable ResponseEntity.badRequest().body(errors)
            }

            webhookConfigService.configure(body.webhookUrl.trim())
            webhookConfigService.configureHistory(body.historyWebhookUrl.trim())

            ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create("/"))
                .body<Map<String, String>>(mapOf("status" to "ok"))
        }
}

data class WebhookSetupRequest(
    val webhookUrl: String = "",
    val historyWebhookUrl: String = "",
)
