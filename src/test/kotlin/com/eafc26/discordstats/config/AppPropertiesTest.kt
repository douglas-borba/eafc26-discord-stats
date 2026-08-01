package com.eafc26.discordstats.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource

class AppPropertiesTest {

    @Test
    fun `EA acquisition window defaults to twenty matches`() {
        assertThat(EaProperties().maxResultCount).isEqualTo(20)
    }

    @Test
    fun `Playwright is headless by default`() {
        assertThat(PlaywrightProperties().headless).isTrue()
    }

    @Test
    fun `headed Playwright requires an explicit configuration override`() {
        val source = MapConfigurationPropertySource(
            mapOf("app.ea.playwright.headless" to "false")
        )

        val configured = Binder(source)
            .bind("app", Bindable.of(AppProperties::class.java))
            .get()

        assertThat(configured.ea.playwright.headless).isFalse()
    }
}
