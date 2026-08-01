package com.eafc26.discordstats.ea

import com.eafc26.discordstats.config.AppProperties
import com.eafc26.discordstats.config.EaProperties
import com.eafc26.discordstats.config.PlaywrightProperties
import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.PlaywrightException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PlaywrightBrowserFetcherTest {
    private val playwright = mock<Playwright>()
    private val browserType = mock<BrowserType>()
    private val browser = mock<Browser>()
    private val context = mock<BrowserContext>()
    private val page = mock<Page>()
    private val factory = mock<PlaywrightFactory>()

    @BeforeEach
    fun setUp() {
        whenever(factory.create()).thenReturn(playwright)
        whenever(playwright.chromium()).thenReturn(browserType)
        whenever(browserType.launch(any<BrowserType.LaunchOptions>())).thenReturn(browser)
        whenever(browser.newContext(any<Browser.NewContextOptions>())).thenReturn(context)
        whenever(context.newPage()).thenReturn(page)
        whenever(browser.isConnected).thenReturn(true)
        whenever(page.isClosed).thenReturn(false)
    }

    @Test
    fun `launches internal Chromium headless with devtools disabled and preserves Akamai session flow`() {
        val fetcher = fetcher()
        whenever(page.evaluate(any(), any())).thenReturn(successfulEvaluation())

        assertThat(fetcher.fetch("https://proclubs.ea.com/api/fc/clubs/matches").status).isEqualTo(200)

        val options = argumentCaptor<BrowserType.LaunchOptions>()
        verify(browserType).launch(options.capture())
        assertThat(options.firstValue.headless).isTrue()
        assertThat(options.firstValue.devtools).isFalse()
        assertThat(options.firstValue.args).contains("--headless=new")
        assertThat(options.firstValue.ignoreDefaultArgs).containsExactly("--headless=old")
        assertThat(options.firstValue.executablePath).isNull()
        assertThat(options.firstValue.channel).isNull()
        assertThat(BrowserType.LaunchOptions::class.java.getField("slowMo").get(options.firstValue)).isNull()
        verify(page).navigate(org.mockito.kotlin.eq("https://proclubs.ea.com"), any<Page.NavigateOptions>())
        verify(page).evaluate(any(), org.mockito.kotlin.eq("https://proclubs.ea.com/api/fc/clubs/matches"))
        val contextOptions = argumentCaptor<Browser.NewContextOptions>()
        verify(browser).newContext(contextOptions.capture())
        assertThat(contextOptions.firstValue.userAgent).doesNotContain("HeadlessChrome")
        assertThat(contextOptions.firstValue.userAgent).contains("Chrome/124.0.0.0")

        fetcher.destroy()
        inOrder(page, context, browser, playwright) {
            verify(page).close()
            verify(context).close()
            verify(browser).close()
            verify(playwright).close()
        }
    }

    @Test
    fun `closes page context browser and Playwright when a fetch fails`() {
        val fetcher = fetcher()
        whenever(page.evaluate(any(), any())).thenThrow(PlaywrightException("network failure"))

        assertThatThrownBy { fetcher.fetch("https://proclubs.ea.com/api/fc/clubs/matches") }
            .isInstanceOf(PlaywrightException::class.java)
            .hasMessageContaining("network failure")

        inOrder(page, context, browser, playwright) {
            verify(page).close()
            verify(context).close()
            verify(browser).close()
            verify(playwright).close()
        }
    }

    @Test
    fun `explicit headed diagnostic override removes every headless launch argument`() {
        val fetcher = fetcher(headless = false)
        whenever(page.evaluate(any(), any())).thenReturn(successfulEvaluation())

        fetcher.fetch("https://proclubs.ea.com/api/fc/clubs/matches")

        val options = argumentCaptor<BrowserType.LaunchOptions>()
        verify(browserType).launch(options.capture())
        assertThat(options.firstValue.headless).isFalse()
        assertThat(options.firstValue.args).noneMatch { it.startsWith("--headless") }
        assertThat(options.firstValue.ignoreDefaultArgs).isEmpty()
        fetcher.destroy()
    }

    private fun fetcher(headless: Boolean = true) = PlaywrightBrowserFetcher(
        AppProperties(
            ea = EaProperties(
                playwright = PlaywrightProperties(
                    headless = headless,
                    startupRetries = 0,
                ),
            ),
        ),
        factory,
    )

    private fun successfulEvaluation(): Map<String, Any?> = mapOf(
        "status" to 200,
        "contentType" to "application/json",
        "body" to "[]",
        "error" to null,
    )
}
