package com.eafc26.discordstats.config

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.net.URI
import java.nio.file.Path

enum class WebhookConfigurationSource {
    ENVIRONMENT,
    STORED,
    NOT_CONFIGURED,
}

data class ResolvedWebhookConfiguration internal constructor(
    val url: String,
    val source: WebhookConfigurationSource,
) {
    val configured: Boolean get() = source != WebhookConfigurationSource.NOT_CONFIGURED
}

/**
 * Resolves Discord webhook configuration without exposing secrets to presentation code.
 *
 * Precedence is deterministic for each destination:
 * 1. non-blank environment-backed application property;
 * 2. Java Preferences value maintained by the local administrative interface;
 * 3. not configured.
 *
 * Environment values are immutable for the lifetime of the process. Invalid values
 * fail startup with the variable name only, preventing an unintended fallback to a
 * different stored channel and preventing the secret from reaching logs.
 */
@Service
class WebhookConfigService(
    private val settingsService: SettingsService,
    properties: AppProperties,
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val environmentMatchWebhook = properties.discord.matchWebhookUrl.trim()
    private val environmentHistoryWebhook = properties.discord.historyWebhookUrl.trim()

    @Volatile
    private var storedMatchWebhook: String = settingsService.getWebhookUrl().trim()

    @Volatile
    private var storedHistoryWebhook: String = settingsService.getHistoryWebhookUrl().trim()

    init {
        validateEnvironmentWebhook(ENV_MATCH, environmentMatchWebhook)
        validateEnvironmentWebhook(ENV_HISTORY, environmentHistoryWebhook)
        storedMatchWebhook = validateStoredWebhook("match", storedMatchWebhook) {
            settingsService.setWebhookUrl("")
        }
        storedHistoryWebhook = validateStoredWebhook("history", storedHistoryWebhook) {
            settingsService.setHistoryWebhookUrl("")
        }
        log.info(
            "Discord webhook configuration resolved: match={}, history={}",
            matchConfiguration().source,
            historyConfiguration().source,
        )
    }

    fun matchConfiguration(): ResolvedWebhookConfiguration =
        resolve(environmentMatchWebhook, storedMatchWebhook)

    fun historyConfiguration(): ResolvedWebhookConfiguration =
        resolve(environmentHistoryWebhook, storedHistoryWebhook)

    fun isConfigured(): Boolean = matchConfiguration().configured

    fun getWebhookUrl(): String = matchConfiguration().url

    fun getWebhookSource(): WebhookConfigurationSource = matchConfiguration().source

    /** Kept for compatibility; environment secrets are never partially displayed. */
    fun getMaskedWebhookUrl(): String = when (getWebhookSource()) {
        WebhookConfigurationSource.ENVIRONMENT -> ""
        WebhookConfigurationSource.NOT_CONFIGURED -> ""
        WebhookConfigurationSource.STORED -> "Configurado localmente"
    }

    fun isHistoryConfigured(): Boolean = historyConfiguration().configured

    fun getHistoryWebhookUrl(): String = historyConfiguration().url

    fun getHistoryWebhookSource(): WebhookConfigurationSource = historyConfiguration().source

    fun configure(url: String) {
        check(getWebhookSource() != WebhookConfigurationSource.ENVIRONMENT) {
            "$ENV_MATCH is controlled by the environment."
        }
        validateUrl(url)
        val normalized = url.trim()
        settingsService.setWebhookUrl(normalized)
        storedMatchWebhook = normalized
        log.info("Discord match webhook configured and saved locally")
    }

    fun reset() {
        settingsService.setWebhookUrl("")
        storedMatchWebhook = ""
        log.info("Stored Discord match webhook cleared")
    }

    fun configureHistory(url: String) {
        check(getHistoryWebhookSource() != WebhookConfigurationSource.ENVIRONMENT) {
            "$ENV_HISTORY is controlled by the environment."
        }
        if (url.isNotBlank()) validateUrl(url)
        val normalized = url.trim()
        settingsService.setHistoryWebhookUrl(normalized)
        storedHistoryWebhook = normalized
        log.info("Stored history webhook {}", if (normalized.isBlank()) "cleared" else "configured")
    }

    fun resetStoredWebhooks() {
        reset()
        settingsService.setHistoryWebhookUrl("")
        storedHistoryWebhook = ""
        log.info("Stored Discord webhook fallbacks cleared")
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

    fun validateUrl(url: String) {
        require(url.isNotBlank()) { "O URL do webhook não pode estar vazio." }
        val uri = try {
            URI(url.trim())
        } catch (_: Exception) {
            throw IllegalArgumentException("URL de webhook inválida.")
        }
        require(uri.scheme == "https") { "O webhook deve utilizar HTTPS." }
        require(uri.host.equals("discord.com", ignoreCase = true)) {
            "URL inválida. Deve utilizar discord.com/api/webhooks."
        }
        val segments = uri.path.split('/').filter(String::isNotBlank)
        require(segments.size >= 4 && segments[0] == "api" && segments[1] == "webhooks" &&
            segments[2].isNotBlank() && segments[3].isNotBlank()) {
            "URL inválida. Deve conter o ID e o token do webhook."
        }
    }

    private fun resolve(environment: String, stored: String): ResolvedWebhookConfiguration = when {
        environment.isNotBlank() -> ResolvedWebhookConfiguration(environment, WebhookConfigurationSource.ENVIRONMENT)
        stored.isNotBlank() -> ResolvedWebhookConfiguration(stored, WebhookConfigurationSource.STORED)
        else -> ResolvedWebhookConfiguration("", WebhookConfigurationSource.NOT_CONFIGURED)
    }

    private fun validateEnvironmentWebhook(variableName: String, value: String) {
        if (value.isBlank()) return
        try {
            validateUrl(value)
        } catch (_: IllegalArgumentException) {
            throw IllegalStateException("$variableName is invalid.")
        }
    }

    private fun validateStoredWebhook(destination: String, value: String, clear: () -> Unit): String {
        if (value.isBlank()) return ""
        return try {
            validateUrl(value)
            value
        } catch (_: IllegalArgumentException) {
            clear()
            log.warn("Invalid stored Discord {} webhook was cleared", destination)
            ""
        }
    }

    companion object {
        const val ENV_MATCH = "EAFC_DISCORD_MATCH_WEBHOOK_URL"
        const val ENV_HISTORY = "EAFC_DISCORD_HISTORY_WEBHOOK_URL"
    }
}
