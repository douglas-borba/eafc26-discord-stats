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

    @PostMapping("/api/setup/webhook", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun saveWebhook(@RequestBody body: WebhookSetupRequest): Mono<ResponseEntity<Map<String, String>>> =
        Mono.fromCallable {
            try {
                webhookConfigService.configure(body.webhookUrl)
                ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create("/"))
                    .body<Map<String, String>>(mapOf("status" to "ok"))
            } catch (ex: IllegalArgumentException) {
                ResponseEntity.badRequest()
                    .body(mapOf("error" to (ex.message ?: "URL inválida")))
            }
        }
}

data class WebhookSetupRequest(val webhookUrl: String = "")
