package com.eafc26.discordstats.store.postgres

import com.eafc26.discordstats.application.club.DiscordWebhookSecretReference
import com.eafc26.discordstats.config.WebhookConfigService
import com.eafc26.discordstats.discord.PostgresDiscordWebhookSecretStore
import com.eafc26.discordstats.discord.WebhookSecretCryptography
import com.eafc26.discordstats.domain.match.ClubId
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.Base64

@Testcontainers
@EnabledIf("isDockerAvailable")
class PostgresDiscordWebhookSecretStoreTest {
    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")
        private lateinit var jdbcTemplate: JdbcTemplate
        private val encryptionKey = Base64.getEncoder().encodeToString(ByteArray(32) { (it + 3).toByte() })
        private const val WEBHOOK = "https://discord.com/api/webhooks/123/secret-token"
        private const val WEBHOOK_B = "https://discord.com/api/webhooks/456/other-token"

        @JvmStatic
        fun isDockerAvailable(): Boolean = try {
            org.testcontainers.DockerClientFactory.instance().isDockerAvailable
        } catch (_: Exception) { false }

        @BeforeAll
        @JvmStatic
        fun migrate() {
            val ds = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            jdbcTemplate = JdbcTemplate(ds)
            jdbcTemplate.execute("DO $$ BEGIN IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'anon') THEN CREATE ROLE anon NOLOGIN; END IF; END $$")
            Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate()
        }
    }

    private val webhookConfig: WebhookConfigService = mock()
    private lateinit var store: PostgresDiscordWebhookSecretStore

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("DELETE FROM discord_webhook_secrets")
        store = newStore()
    }

    @Test
    fun `persists encrypted secret across a recreated store without plaintext`() {
        val club = ClubId("1104972")
        val reference = store.store(club, WEBHOOK)
        val stored = jdbcTemplate.queryForObject(
            "SELECT encode(encrypted_webhook_url, 'escape') FROM discord_webhook_secrets WHERE reference = ?",
            String::class.java,
            reference.value,
        )

        assertThat(reference.value).startsWith("postgres:club:1104972:")
        assertThat(stored).doesNotContain("discord.com", "secret-token")
        assertThat(newStore().resolve(reference)).isNotNull
        verify(webhookConfig).validateUrl(WEBHOOK)
    }

    @Test
    fun `clubs keep isolated references and deletion is idempotent`() {
        val clubA = ClubId("1104972")
        val clubB = ClubId("10361179")
        val refA = store.store(clubA, WEBHOOK)
        val refB = store.store(clubB, WEBHOOK_B)

        store.remove(refA)
        store.remove(refA)

        assertThat(store.resolve(refA)).isNull()
        assertThat(store.resolve(refB)).isNotNull
        assertThat(jdbcTemplate.queryForObject("SELECT club_id FROM discord_webhook_secrets WHERE reference = ?", String::class.java, refB.value))
            .isEqualTo(clubB.value)
    }

    @Test
    fun `unknown legacy preferences reference remains unresolved in production store`() {
        assertThat(store.resolve(DiscordWebhookSecretReference("preferences:club:1104972:old"))).isNull()
    }

    private fun newStore() = PostgresDiscordWebhookSecretStore(
        jdbcTemplate,
        webhookConfig,
        WebhookSecretCryptography.fromBase64(encryptionKey),
    )
}
