package com.eafc26.discordstats.ea

import com.eafc26.discordstats.config.AppProperties
import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.PlaywrightException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * Playwright-backed browser fetcher.
 *
 * The browser is initialized lazily on the first fetch call so that Spring
 * can start even when Chromium, Xvfb, or the display is temporarily unavailable.
 *
 * All public methods are synchronized to allow safe concurrent use from a future
 * scheduler without requiring external locking.
 *
 * On a PlaywrightException or fetch error the browser is restarted up to
 * [AppProperties.ea.playwright.startupRetries] times before the error is surfaced
 * to the caller.
 *
 * Deployment: launch with `xvfb-run -a java -jar app.jar` on Linux servers
 * to provide a virtual display for the headed browser.
 */
@Component
@ConditionalOnProperty(name = ["app.ea.client"], havingValue = "playwright")
class PlaywrightBrowserFetcher(
    private val props: AppProperties,
) : BrowserFetcher, DisposableBean {

    private val log = LoggerFactory.getLogger(javaClass)

    private val lock = Any()

    private var playwright: Playwright? = null
    private var browser: Browser? = null
    private var context: BrowserContext? = null
    private var page: Page? = null

    // ---------------------------------------------------------------------------
    // JS executed inside the browser page to perform the fetch
    // ---------------------------------------------------------------------------

    companion object {
        // Use arrayBuffer + TextDecoder('utf-8') instead of r.text() to force
        // explicit UTF-8 decoding. r.text() honors the charset in Content-Type,
        // and if EA omits it or sends text/plain the browser defaults to
        // windows-1252, corrupting non-ASCII characters like ç and ã.
        //
        // cache: 'no-store' ensures every fetch bypasses browser cache and
        // makes a fresh network request. Without this, the browser may serve
        // stale responses if the EA API returns caching headers.
        private val FETCH_JS = """
            async (url) => {
                try {
                    const r = await window.fetch(url, {
                        credentials: 'include',
                        cache: 'no-store',
                        headers: { 'Accept': 'application/json' }
                    });
                    const buffer = await r.arrayBuffer();
                    const body = new TextDecoder('utf-8').decode(buffer);
                    return {
                        status: r.status,
                        contentType: r.headers.get('content-type'),
                        cacheControl: r.headers.get('cache-control'),
                        etag: r.headers.get('etag'),
                        expires: r.headers.get('expires'),
                        age: r.headers.get('age'),
                        lastModified: r.headers.get('last-modified'),
                        body: body,
                        error: null
                    };
                } catch (e) {
                    return { status: 0, contentType: null, body: '', error: String(e) };
                }
            }
        """.trimIndent()
    }

    // ---------------------------------------------------------------------------
    // BrowserFetcher
    // ---------------------------------------------------------------------------

    override fun fetch(url: String): BrowserFetchResult = synchronized(lock) {
        val pw = props.ea.playwright
        var lastException: Exception? = null

        repeat(pw.startupRetries + 1) { attempt ->
            try {
                ensureReady()
                return@synchronized doFetch(url)
            } catch (ex: PlaywrightException) {
                log.warn("Playwright fetch attempt ${attempt + 1} failed, restarting browser", ex)
                lastException = ex
                closeBrowser()
            } catch (ex: Exception) {
                log.warn("Unexpected error during fetch attempt ${attempt + 1}", ex)
                lastException = ex
                closeBrowser()
            }
        }

        throw lastException ?: PlaywrightException("Fetch failed after ${pw.startupRetries + 1} attempt(s)")
    }

    // ---------------------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------------------

    private fun ensureReady() {
        val pw = props.ea.playwright
        if (isHealthy()) return

        log.info("Initializing Playwright browser (headless={})", pw.headless)
        closeBrowser()

        playwright = Playwright.create()
        browser = playwright!!.chromium().launch(
            com.microsoft.playwright.BrowserType.LaunchOptions()
                .setHeadless(pw.headless)
                .setArgs(pw.launchArgs)
        )
        context = browser!!.newContext()
        page = context!!.newPage()

        log.info("Navigating to {} to establish Akamai session", pw.initialPageUrl)
        page!!.navigate(
            pw.initialPageUrl,
            Page.NavigateOptions()
                .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                .setTimeout(pw.navTimeoutMs.toDouble())
        )
        // Note: Chromium is already started with --start-minimized and --window-size=1,1
        // so no additional window hiding is needed. Previously this called hideChromiumOnMac()
        // which used System Events osascript, but that caused focus issues with other apps.
        log.info("Browser ready")
    }


    private fun isHealthy(): Boolean {
        return try {
            browser?.isConnected == true && page?.isClosed == false
        } catch (_: Exception) {
            false
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun doFetch(url: String): BrowserFetchResult {
        val raw = page!!.evaluate(FETCH_JS, url)

        // Playwright returns the JS object as a LinkedHashMap
        val map = raw as? Map<String, Any?> ?: error("Unexpected evaluate result type: ${raw?.javaClass}")

        val result = BrowserFetchResult(
            status = (map["status"] as? Number)?.toInt() ?: 0,
            contentType = map["contentType"] as? String,
            body = map["body"] as? String ?: "",
            error = map["error"] as? String,
            cacheControl = map["cacheControl"] as? String,
            etag = map["etag"] as? String,
            expires = map["expires"] as? String,
            age = map["age"] as? String,
            lastModified = map["lastModified"] as? String,
        )

        // Log caching headers for diagnostics
        log.info("EA API response: status={}, Cache-Control={}, ETag={}, Expires={}, Age={}, Last-Modified={}",
            result.status,
            result.cacheControl ?: "(none)",
            result.etag ?: "(none)",
            result.expires ?: "(none)",
            result.age ?: "(none)",
            result.lastModified ?: "(none)")

        return result
    }

    private fun closeBrowser() {
        runCatching { page?.close() }
        runCatching { context?.close() }
        runCatching { browser?.close() }
        runCatching { playwright?.close() }
        page = null
        context = null
        browser = null
        playwright = null
    }

    // ---------------------------------------------------------------------------
    // DisposableBean
    // ---------------------------------------------------------------------------

    override fun destroy() {
        synchronized(lock) {
            log.info("Shutting down Playwright browser")
            closeBrowser()
        }
    }
}
