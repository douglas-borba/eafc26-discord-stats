package com.eafc26.discordstats.web

import com.eafc26.discordstats.config.WebhookConfigService
import com.eafc26.discordstats.config.WebhookConfigurationSource
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

    /** Returns configuration status and origin without exposing either URL. */
    @GetMapping("/api/setup/webhook", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getWebhookInfo(): Mono<ResponseEntity<Map<String, Any>>> =
        Mono.fromCallable {
            ResponseEntity.ok(
                mapOf(
                    "configured" to webhookConfigService.isConfigured(),
                    "source" to webhookConfigService.getWebhookSource().name,
                )
            )
        }

    /** Saves the match webhook when it is not managed by the environment. */
    @PostMapping("/api/setup/webhook", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun saveWebhook(@RequestBody body: WebhookSetupRequest): Mono<ResponseEntity<Map<String, String>>> =
        Mono.fromCallable {
            val errors = mutableMapOf<String, String>()

            val matchEnvironmentManaged =
                webhookConfigService.getWebhookSource() == WebhookConfigurationSource.ENVIRONMENT
            if (!matchEnvironmentManaged && body.webhookUrl.isBlank() && !webhookConfigService.isConfigured()) {
                errors["statsError"] = "Informe a URL do webhook do canal de estatísticas."
            } else if (!matchEnvironmentManaged && body.webhookUrl.isNotBlank()) {
                try { webhookConfigService.validateUrl(body.webhookUrl.trim()) }
                catch (ex: IllegalArgumentException) { errors["statsError"] = ex.message ?: "URL inválida." }
            }

            if (errors.isNotEmpty()) {
                return@fromCallable ResponseEntity.badRequest().body(errors)
            }

            if (!matchEnvironmentManaged && body.webhookUrl.isNotBlank()) {
                webhookConfigService.configure(body.webhookUrl.trim())
            }
            ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create("/"))
                .body<Map<String, String>>(mapOf("status" to "ok"))
        }
}

data class WebhookSetupRequest(
    val webhookUrl: String = "",
)
