package com.eafc26.discordstats.discord

import com.eafc26.discordstats.config.WebhookConfigService
import com.eafc26.discordstats.domain.match.ClubId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID
import java.util.prefs.Preferences

class PreferencesDiscordWebhookSecretStoreTest {
    private val preferences = Preferences.userRoot().node("eafc-tests/${UUID.randomUUID()}")
    private val webhookConfig: WebhookConfigService = mock()
    private val store = PreferencesDiscordWebhookSecretStore(webhookConfig, preferences)

    @AfterEach
    fun cleanUp() {
        preferences.removeNode()
        preferences.flush()
    }

    @Test
    fun `stores secret outside monitored club and resolves it through opaque reference`() {
        val url = "https://discord.com/api/webhooks/123/secret-token"

        val reference = store.store(ClubId("8874106"), url)

        verify(webhookConfig).validateUrl(url)
        assertThat(reference.value).startsWith("preferences:club:8874106:")
        assertThat(reference.value).doesNotContain("discord.com", "secret-token")
        assertThat(store.resolve(reference).toString()).isEqualTo("DiscordDestination(configured=true)")

        store.remove(reference)
        assertThat(store.resolve(reference)).isNull()
    }

    @Test
    fun `legacy reference remains resolved by existing configuration`() {
        whenever(webhookConfig.getWebhookUrl()).thenReturn("https://discord.com/api/webhooks/legacy/token")

        assertThat(store.resolve(com.eafc26.discordstats.application.club.DefaultClubProvider.LEGACY_WEBHOOK_REFERENCE))
            .isNotNull
    }
}
