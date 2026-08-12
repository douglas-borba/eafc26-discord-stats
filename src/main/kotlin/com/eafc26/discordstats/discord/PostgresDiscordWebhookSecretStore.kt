package com.eafc26.discordstats.discord

import com.eafc26.discordstats.application.club.DefaultClubProvider
import com.eafc26.discordstats.application.club.DiscordWebhookSecretReference
import com.eafc26.discordstats.config.WebhookConfigService
import com.eafc26.discordstats.domain.match.ClubId
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/** Durable per-club secret store used only with PostgreSQL administration enabled. */
class PostgresDiscordWebhookSecretStore(
    private val jdbcTemplate: JdbcTemplate,
    private val webhookConfigService: WebhookConfigService,
    private val cryptography: WebhookSecretCryptography,
) : DiscordWebhookSecretStore {
    override fun store(clubId: ClubId, webhookUrl: String): DiscordWebhookSecretReference {
        webhookConfigService.validateUrl(webhookUrl)
        val reference = DiscordWebhookSecretReference("$REFERENCE_PREFIX${clubId.value}:${UUID.randomUUID()}")
        val encrypted = cryptography.encrypt(webhookUrl.trim())
        val now = Instant.now()
        jdbcTemplate.update(
            """
            INSERT INTO discord_webhook_secrets
                (reference, club_id, encrypted_webhook_url, nonce, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            reference.value,
            clubId.value,
            encrypted.ciphertext,
            encrypted.nonce,
            Timestamp.from(now),
            Timestamp.from(now),
        )
        return reference
    }

    override fun remove(reference: DiscordWebhookSecretReference) {
        if (reference == DefaultClubProvider.LEGACY_WEBHOOK_REFERENCE) return
        jdbcTemplate.update("DELETE FROM discord_webhook_secrets WHERE reference = ?", reference.value)
    }

    override fun resolve(reference: DiscordWebhookSecretReference): DiscordDestination? {
        if (reference == DefaultClubProvider.LEGACY_WEBHOOK_REFERENCE) {
            return webhookConfigService.getWebhookUrl().takeIf(String::isNotBlank)?.let(::DiscordDestination)
        }
        if (!reference.value.startsWith(REFERENCE_PREFIX)) return null
        val secret = jdbcTemplate.query(
            "SELECT encrypted_webhook_url, nonce FROM discord_webhook_secrets WHERE reference = ?",
            { rs, _ -> rs.getBytes("encrypted_webhook_url") to rs.getBytes("nonce") },
            reference.value,
        ).firstOrNull() ?: return null
        val webhookUrl = cryptography.decrypt(secret.first, secret.second)
        if (webhookUrl == null) {
            logger.warn("Configured Discord webhook secret could not be decrypted")
            return null
        }
        return webhookUrl.takeIf(String::isNotBlank)?.let(::DiscordDestination)
    }

    companion object {
        private const val REFERENCE_PREFIX = "postgres:club:"
        private val logger = LoggerFactory.getLogger(PostgresDiscordWebhookSecretStore::class.java)
    }
}
