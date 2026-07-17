package com.eafc26.discordstats.config

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

/**
 * Manages the Discord webhook URL at runtime.
 *
 * The URL is loaded from AppProperties at startup (which itself may have been populated
 * from the local config file by LocalConfigPostProcessor). A mutable holder allows it to
 * be updated at runtime via the setup page without restarting the application.
 *
 * The config file path is ~/Library/Application Support/EAFC26DiscordStats/config.properties.
 * On non-macOS platforms the directory simply lives at the equivalent home path.
 */
@Service
class WebhookConfigService(private val props: AppProperties) {

    private val log = LoggerFactory.getLogger(javaClass)

    open val configDir: Path = Path.of(
        System.getProperty("user.home"),
        "Library", "Application Support", "EAFC26DiscordStats",
    )

    private val configFile: Path get() = configDir.resolve("config.properties")

    @Volatile
    private var webhookUrl: String = props.discord.webhookUrl

    @Volatile
    private var historyWebhookUrl: String =
        loadProps().getProperty("discord.history-webhook.url", "").trim()

    fun isConfigured(): Boolean = webhookUrl.isNotBlank()

    fun getWebhookUrl(): String = webhookUrl

    fun isHistoryConfigured(): Boolean = historyWebhookUrl.isNotBlank()

    fun getHistoryWebhookUrl(): String = historyWebhookUrl

    /**
     * Validates, persists, and activates a new webhook URL.
     * @throws IllegalArgumentException if the URL is invalid.
     */
    fun configure(url: String) {
        validateUrl(url)
        persistProperty("discord.webhook.url", url)
        webhookUrl = url
        log.info("Discord webhook configured and saved")
    }

    /** Clears the webhook URL so the next request is redirected to /setup. */
    fun reset() {
        persistProperty("discord.webhook.url", "")
        webhookUrl = ""
        log.info("Discord webhook cleared — setup required")
    }

    /** Saves and activates the history webhook URL. Blank URL clears it. */
    fun configureHistory(url: String) {
        if (url.isNotBlank()) validateUrl(url)
        persistProperty("discord.history-webhook.url", url)
        historyWebhookUrl = url.trim()
        log.info("History webhook {}", if (url.isBlank()) "cleared" else "configured and saved")
    }

    fun isNetworkEnabled(): Boolean =
        loadProps().getProperty("web.network-enabled", "false").trim().equals("true", ignoreCase = true)

    fun setNetworkEnabled(enabled: Boolean) {
        persistProperty("web.network-enabled", enabled.toString())
        log.info("Network mode set to: {}", enabled)
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

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private fun loadProps(): Properties {
        val p = Properties()
        val f = configFile.toFile()
        if (f.exists()) FileInputStream(f).use { p.load(it) }
        return p
    }

    private fun persistProperty(key: String, value: String) {
        Files.createDirectories(configDir)
        val p = loadProps()
        if (value.isBlank()) p.remove(key) else p.setProperty(key, value)
        FileOutputStream(configFile.toFile()).use { p.store(it, "EA FC 26 Discord Stats") }
        applyRestrictivePermissions()
    }

    private fun applyRestrictivePermissions() {
        runCatching {
            val f = configFile.toFile()
            f.setReadable(false, false)
            f.setWritable(false, false)
            f.setReadable(true, true)
            f.setWritable(true, true)
        }
    }
}
