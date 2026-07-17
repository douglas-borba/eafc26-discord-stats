package com.eafc26.discordstats.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app")
data class AppProperties(
    val ea: EaProperties = EaProperties(),
    val polling: PollingProperties = PollingProperties(),
    val discord: DiscordProperties = DiscordProperties(),
    val web: WebProperties = WebProperties(),
)

data class WebProperties(
    val networkEnabled: Boolean = false,
)

data class PollingProperties(
    val enabled: Boolean = true,
    val intervalMs: Long = 300_000,
    val publishExistingOnFirstRun: Boolean = false,
)

data class DiscordProperties(
    val webhookUrl: String = "",
)

data class EaProperties(
    val baseUrl: String = "https://proclubs.ea.com/api/fc",
    val platform: String = "common-gen5",
    val clubId: String = "",
    val clubName: String = "",
    val matchType: String = "leagueMatch",
    val maxResultCount: Int = 5,
    /**
     * EA's proclubs.ea.com endpoints return 403 without a browser-like User-Agent.
     * Confirmed by multiple community projects (Maldini80/bot-torneos-pro,
     * DotExectur/eproclubs_core). Externalized here so it can be updated
     * without code changes if EA alters bot-detection logic.
     */
    val userAgent: String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.0.0 Safari/537.36",
    /** Gateway implementation: "playwright" or "webclient". */
    val client: String = "webclient",
    val playwright: PlaywrightProperties = PlaywrightProperties(),
)

data class PlaywrightProperties(
    /** Run Chromium in headless mode. On Linux servers use xvfb-run with headless=false. */
    val headless: Boolean = false,
    /** Navigation timeout in milliseconds for the initial page load. */
    val navTimeoutMs: Long = 30_000,
    /** Fetch timeout in milliseconds for each window.fetch() call inside the browser. */
    val fetchTimeoutMs: Long = 30_000,
    /** Number of additional browser restart attempts after the first failure. */
    val startupRetries: Int = 1,
    /** Page to navigate on startup so Akamai can establish its session cookies. */
    val initialPageUrl: String = "https://proclubs.ea.com",
)
