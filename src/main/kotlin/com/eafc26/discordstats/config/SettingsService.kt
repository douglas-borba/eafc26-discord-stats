package com.eafc26.discordstats.config

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.prefs.Preferences

/**
 * Centralized settings service using Java Preferences API.
 *
 * All persistent application settings are managed here, providing:
 * - Automatic persistence across application restarts
 * - Type-safe getters/setters
 * - Easy extensibility for future settings
 *
 * The Preferences API stores data in platform-specific locations:
 * - macOS: ~/Library/Preferences/com.apple.java.util.prefs.plist (or similar)
 * - Windows: Registry under HKEY_CURRENT_USER\Software\JavaSoft\Prefs
 * - Linux: ~/.java/.userPrefs
 */
@Service
class SettingsService {

    private val log = LoggerFactory.getLogger(javaClass)
    private val prefs: Preferences = Preferences.userNodeForPackage(SettingsService::class.java)

    companion object {
        // Preference keys
        private const val KEY_WEBHOOK_URL = "discord.webhook.url"
        private const val KEY_HISTORY_WEBHOOK_URL = "discord.history-webhook.url"
        private const val KEY_NETWORK_ENABLED = "web.network-enabled"
        private const val KEY_CLUB_ID = "ea.club.id"
        private const val KEY_PLATFORM = "ea.platform"
        private const val KEY_POLLING_INTERVAL_MS = "polling.interval-ms"
    }

    init {
        log.info("SettingsService initialized using Java Preferences API")
    }

    // ------------------------------------------------------------------
    // Discord Webhook
    // ------------------------------------------------------------------

    /**
     * Returns the saved Discord webhook URL, or empty string if not configured.
     */
    fun getWebhookUrl(): String = prefs.get(KEY_WEBHOOK_URL, "")

    /**
     * Saves the Discord webhook URL.
     * @param url The webhook URL to save. Pass empty string to clear.
     */
    fun setWebhookUrl(url: String) {
        if (url.isBlank()) {
            prefs.remove(KEY_WEBHOOK_URL)
        } else {
            prefs.put(KEY_WEBHOOK_URL, url)
        }
        flushPrefs()
        log.info("Discord webhook URL {}", if (url.isBlank()) "cleared" else "saved")
    }

    /**
     * Returns true if a Discord webhook URL is configured.
     */
    fun isWebhookConfigured(): Boolean = getWebhookUrl().isNotBlank()

    // ------------------------------------------------------------------
    // History Webhook
    // ------------------------------------------------------------------

    /**
     * Returns the saved history webhook URL, or empty string if not configured.
     */
    fun getHistoryWebhookUrl(): String = prefs.get(KEY_HISTORY_WEBHOOK_URL, "")

    /**
     * Saves the history webhook URL.
     * @param url The webhook URL to save. Pass empty string to clear.
     */
    fun setHistoryWebhookUrl(url: String) {
        if (url.isBlank()) {
            prefs.remove(KEY_HISTORY_WEBHOOK_URL)
        } else {
            prefs.put(KEY_HISTORY_WEBHOOK_URL, url)
        }
        flushPrefs()
        log.info("History webhook URL {}", if (url.isBlank()) "cleared" else "saved")
    }

    /**
     * Returns true if a history webhook URL is configured.
     */
    fun isHistoryWebhookConfigured(): Boolean = getHistoryWebhookUrl().isNotBlank()

    // ------------------------------------------------------------------
    // Network Settings
    // ------------------------------------------------------------------

    /**
     * Returns whether network mode (listen on all interfaces) is enabled.
     */
    fun isNetworkEnabled(): Boolean = prefs.getBoolean(KEY_NETWORK_ENABLED, false)

    /**
     * Sets whether network mode is enabled.
     */
    fun setNetworkEnabled(enabled: Boolean) {
        prefs.putBoolean(KEY_NETWORK_ENABLED, enabled)
        flushPrefs()
        log.info("Network mode set to: {}", enabled)
    }

    // ------------------------------------------------------------------
    // EA Configuration (for future use)
    // ------------------------------------------------------------------

    /**
     * Returns the saved EA club ID, or empty string if not configured.
     */
    fun getClubId(): String = prefs.get(KEY_CLUB_ID, "")

    /**
     * Saves the EA club ID.
     */
    fun setClubId(clubId: String) {
        if (clubId.isBlank()) {
            prefs.remove(KEY_CLUB_ID)
        } else {
            prefs.put(KEY_CLUB_ID, clubId)
        }
        flushPrefs()
        log.info("Club ID {}", if (clubId.isBlank()) "cleared" else "saved")
    }

    /**
     * Returns the saved EA platform, or empty string if not configured.
     */
    fun getPlatform(): String = prefs.get(KEY_PLATFORM, "")

    /**
     * Saves the EA platform.
     */
    fun setPlatform(platform: String) {
        if (platform.isBlank()) {
            prefs.remove(KEY_PLATFORM)
        } else {
            prefs.put(KEY_PLATFORM, platform)
        }
        flushPrefs()
        log.info("Platform {}", if (platform.isBlank()) "cleared" else "saved")
    }

    // ------------------------------------------------------------------
    // Polling Settings (for future use)
    // ------------------------------------------------------------------

    /**
     * Returns the polling interval in milliseconds, or the default if not configured.
     */
    fun getPollingIntervalMs(default: Long = 300_000L): Long =
        prefs.getLong(KEY_POLLING_INTERVAL_MS, default)

    /**
     * Saves the polling interval in milliseconds.
     */
    fun setPollingIntervalMs(intervalMs: Long) {
        prefs.putLong(KEY_POLLING_INTERVAL_MS, intervalMs)
        flushPrefs()
        log.info("Polling interval set to: {} ms", intervalMs)
    }

    // ------------------------------------------------------------------
    // Utility
    // ------------------------------------------------------------------

    /**
     * Clears all saved settings.
     */
    fun clearAll() {
        prefs.clear()
        flushPrefs()
        log.info("All settings cleared")
    }

    private fun flushPrefs() {
        try {
            prefs.flush()
        } catch (e: Exception) {
            log.warn("Failed to flush preferences: {}", e.message)
        }
    }
}

