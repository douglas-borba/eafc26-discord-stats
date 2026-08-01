package com.eafc26.discordstats.ea

import com.microsoft.playwright.Playwright
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/** Small creation seam that keeps Playwright lifecycle testable. */
fun interface PlaywrightFactory {
    fun create(): Playwright
}

@Component
@ConditionalOnProperty(name = ["app.ea.client"], havingValue = "playwright")
class DefaultPlaywrightFactory : PlaywrightFactory {
    override fun create(): Playwright = Playwright.create()
}
