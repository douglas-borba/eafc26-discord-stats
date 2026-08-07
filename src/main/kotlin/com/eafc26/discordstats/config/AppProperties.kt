package com.eafc26.discordstats.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app")
data class AppProperties(
    val ea: EaProperties = EaProperties(),
    val polling: PollingProperties = PollingProperties(),
    val discord: DiscordProperties = DiscordProperties(),
    val web: WebProperties = WebProperties(),
    val postgres: PostgresProperties = PostgresProperties(),
    val acquisition: AcquisitionProperties = AcquisitionProperties(),
    val replay: ReplayProperties = ReplayProperties(),
)

data class PostgresProperties(
    val mirrorEnabled: Boolean = false,
    val syncEnabled: Boolean = false,
    val syncIntervalMs: Long = 300_000,
)

data class AcquisitionProperties(
    val enabled: Boolean = true,
)

data class WebProperties(
    val networkEnabled: Boolean = false,
)

data class PollingProperties(
    val enabled: Boolean = true,
    val intervalMs: Long = 300_000,
)

data class DiscordProperties(
    val matchWebhookUrl: String = "",
)

data class EaProperties(
    val baseUrl: String = "https://proclubs.ea.com/api/fc",
    val platform: String = "common-gen5",
    val clubId: String = "",
    val clubName: String = "",
    val matchType: String = "leagueMatch",
    val maxResultCount: Int = 20,
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
    /** Run Chromium invisibly. Override to false only for explicit local diagnosis. */
    val headless: Boolean = true,
    /** Navigation timeout in milliseconds for the initial page load. */
    val navTimeoutMs: Long = 30_000,
    /** Fetch timeout in milliseconds for each window.fetch() call inside the browser. */
    val fetchTimeoutMs: Long = 30_000,
    /** Number of additional browser restart attempts after the first failure. */
    val startupRetries: Int = 1,
    /** Page to navigate on startup so Akamai can establish its session cookies. */
    val initialPageUrl: String = "https://proclubs.ea.com",
    /**
     * Extra Chromium launch arguments used by the internal headless browser.
     */
    val launchArgs: List<String> = listOf(
        "--no-first-run",
        "--no-default-browser-check",
        "--disable-extensions",
        "--disable-infobars",
        "--disable-gpu",
        "--disable-software-rasterizer",
        "--renderer-process-limit=1",
    ),
)

data class ReplayProperties(
    val enabled: Boolean = false,
    val matches: Int? = null,
    val matchId: String? = null,
    val dryRun: Boolean = false,
)

