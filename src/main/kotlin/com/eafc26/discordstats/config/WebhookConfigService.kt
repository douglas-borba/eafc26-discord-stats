package com.eafc26.discordstats.config

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.nio.file.Path

/**
 * Manages the Discord webhook URL at runtime.
 *
 * The URL is loaded from SettingsService (Java Preferences API) at startup.
 * A mutable holder allows it to be updated at runtime via the setup page
 * without restarting the application.
 */
@Service
class WebhookConfigService(
    private val settingsService: SettingsService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Volatile
    private var webhookUrl: String = settingsService.getWebhookUrl()

    @Volatile
    private var historyWebhookUrl: String = settingsService.getHistoryWebhookUrl()

    fun isConfigured(): Boolean = webhookUrl.isNotBlank()

    fun getWebhookUrl(): String = webhookUrl

    /**
     * Returns the masked webhook URL for display purposes.
     * Shows only the first and last few characters.
     */
    fun getMaskedWebhookUrl(): String {
        val url = webhookUrl
        if (url.isBlank()) return ""
        if (url.length <= 50) return "****"
        return url.take(35) + "****" + url.takeLast(10)
    }

    fun isHistoryConfigured(): Boolean = historyWebhookUrl.isNotBlank()

    fun getHistoryWebhookUrl(): String = historyWebhookUrl

    /**
     * Validates, persists, and activates a new webhook URL.
     * @throws IllegalArgumentException if the URL is invalid.
     */
    fun configure(url: String) {
        validateUrl(url)
        settingsService.setWebhookUrl(url)
        webhookUrl = url
        log.info("Discord webhook configured and saved")
    }

    /** Clears the webhook URL so the next request is redirected to /setup. */
    fun reset() {
        settingsService.setWebhookUrl("")
        webhookUrl = ""
        log.info("Discord webhook cleared — setup required")
    }

    /** Saves and activates the history webhook URL. Blank URL clears it. */
    fun configureHistory(url: String) {
        if (url.isNotBlank()) validateUrl(url)
        settingsService.setHistoryWebhookUrl(url)
        historyWebhookUrl = url.trim()
        log.info("History webhook {}", if (url.isBlank()) "cleared" else "configured and saved")
    }

    fun isNetworkEnabled(): Boolean = settingsService.isNetworkEnabled()

    fun setNetworkEnabled(enabled: Boolean) {
        settingsService.setNetworkEnabled(enabled)
        log.info("Network mode set to: {}", enabled)
    }

    fun isDevelopmentModeEnabled(): Boolean = settingsService.isDevelopmentModeEnabled()

    fun setDevelopmentModeEnabled(enabled: Boolean) {
        settingsService.setDevelopmentModeEnabled(enabled)
        log.info("Development mode set to: {}", enabled)
    }

    fun logFilePath(): String =
        Path.of(
            System.getProperty("user.home"),
            "Library", "Logs", "EAFC26DiscordStats", "app.log",
        ).toString()

    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------

    fun validateUrl(url: String) {
        require(url.isNotBlank()) { "O URL do webhook não pode estar vazio." }
        require(url.startsWith("https://discord.com/api/webhooks/")) {
            "URL inválida. Deve começar com https://discord.com/api/webhooks/"
        }
        val path = url.removePrefix("https://discord.com/api/webhooks/").split("/")
        require(path.size >= 2 && path[0].isNotBlank() && path[1].isNotBlank()) {
            "URL inválida. Deve conter o ID e o token do webhook."
        }
    }
}
