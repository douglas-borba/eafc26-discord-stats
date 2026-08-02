package com.eafc26.discordstats.web

import com.eafc26.discordstats.config.PhraseBank
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
class SettingsController(
    private val webhookConfigService: WebhookConfigService,
    private val phraseBank: PhraseBank,
) {

    @GetMapping("/settings", produces = [MediaType.TEXT_HTML_VALUE])
    fun settingsPage(): ResponseEntity<ClassPathResource> =
        ResponseEntity.ok()
            .contentType(MediaType.TEXT_HTML)
            .body(ClassPathResource("settings.html"))

    /** Returns current settings — never exposes the webhook URL. */
    @GetMapping("/api/settings/info", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun info(): Mono<ResponseEntity<Map<String, Any>>> =
        Mono.fromCallable {
            val networkEnabled = webhookConfigService.isNetworkEnabled()
            val networkUrl: String? = if (networkEnabled) localNetworkUrl() else null
            val devMode = webhookConfigService.isDevelopmentModeEnabled()
            ResponseEntity.ok(
                buildMap {
                    put("webhookConfigured", webhookConfigService.isConfigured())
                    put("webhookSource", webhookConfigService.getWebhookSource().name)
                    put("networkEnabled", networkEnabled)
                    put("devMode", devMode)
                    if (networkUrl != null) put("networkUrl", networkUrl)
                    put("logFile", webhookConfigService.logFilePath())
                }
            )
        }

    /** Clears local fallbacks only; environment-managed secrets remain effective. */
    @PostMapping("/api/settings/reconfigure-webhook")
    fun reconfigureWebhook(): Mono<ResponseEntity<Map<String, String>>> =
        Mono.fromCallable {
            webhookConfigService.resetStoredWebhooks()
            ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create("/setup"))
                .body<Map<String, String>>(mapOf("status" to "ok"))
        }

    @PostMapping("/api/settings/network", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun setNetwork(@RequestBody body: NetworkSettingRequest): Mono<ResponseEntity<Map<String, Any>>> =
        Mono.fromCallable {
            webhookConfigService.setNetworkEnabled(body.enabled)
            ResponseEntity.ok(
                mapOf<String, Any>(
                    "status" to "saved",
                    "message" to "Configuração salva. Reinicie o aplicativo para aplicar.",
                )
            )
        }

    /** Sets development mode on or off. Takes effect immediately without restart. */
    @PostMapping("/api/settings/development-mode", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun setDevelopmentMode(@RequestBody body: DevelopmentModeRequest): Mono<ResponseEntity<Map<String, Any>>> =
        Mono.fromCallable {
            webhookConfigService.setDevelopmentModeEnabled(body.enabled)
            ResponseEntity.ok(
                mapOf<String, Any>(
                    "status" to "saved",
                    "enabled" to body.enabled,
                    "message" to if (body.enabled) "Modo de desenvolvimento ativado." else "Modo de desenvolvimento desativado.",
                )
            )
        }

    @GetMapping("/api/settings/phrases", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getPhrases(): Mono<ResponseEntity<Map<String, List<String>>>> =
        Mono.fromCallable { ResponseEntity.ok(phraseBank.getAll()) }

    @PostMapping("/api/settings/phrases", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun savePhrases(
        @RequestBody body: Map<String, List<String>>,
    ): Mono<ResponseEntity<Map<String, String>>> =
        Mono.fromCallable {
            phraseBank.saveAll(body)
            ResponseEntity.ok(mapOf("status" to "saved"))
        }

    private fun localNetworkUrl(): String {
        return try {
            val addr = java.net.InetAddress.getLocalHost().hostAddress
            "http://$addr:8080"
        } catch (_: Exception) {
            "http://localhost:8080"
        }
    }
}

data class NetworkSettingRequest(val enabled: Boolean = false)
data class DevelopmentModeRequest(val enabled: Boolean = false)
