package com.eafc26.discordstats.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource

class AppPropertiesTest {
    @Test fun `EA acquisition window defaults to twenty matches`() {
        assertThat(EaProperties().maxResultCount).isEqualTo(20)
    }

    @Test fun `EA gateway settings bind from application properties`() {
        val configured = Binder(MapConfigurationPropertySource(mapOf(
            "app.ea.gateway-base-url" to "http://gateway:8081",
            "app.ea.gateway-internal-token" to "secret",
        ))).bind("app", Bindable.of(AppProperties::class.java)).get()
        assertThat(configured.ea.gatewayBaseUrl).isEqualTo("http://gateway:8081")
        assertThat(configured.ea.gatewayInternalToken).isEqualTo("secret")
    }

    @Test fun `Discord match webhook property binds`() {
        val configured = Binder(MapConfigurationPropertySource(mapOf(
            "app.discord.match-webhook-url" to "https://discord.com/api/webhooks/1/match-token",
        ))).bind("app", Bindable.of(AppProperties::class.java)).get()
        assertThat(configured.discord.matchWebhookUrl).endsWith("/match-token")
    }
}
