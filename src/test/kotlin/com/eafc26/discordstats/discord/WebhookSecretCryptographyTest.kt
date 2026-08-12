package com.eafc26.discordstats.discord

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.Base64

class WebhookSecretCryptographyTest {
    private val key = Base64.getEncoder().encodeToString(ByteArray(32) { (it + 1).toByte() })

    @Test
    fun `encrypts and decrypts with AES GCM using a fresh nonce for every write`() {
        val crypto = WebhookSecretCryptography.fromBase64(key)
        val first = crypto.encrypt(WEBHOOK)
        val second = crypto.encrypt(WEBHOOK)

        assertThat(String(first.ciphertext)).doesNotContain("discord.com", "secret-token")
        assertThat(first.nonce).isNotEqualTo(second.nonce)
        assertThat(crypto.decrypt(first.ciphertext, first.nonce)).isEqualTo(WEBHOOK)
        assertThat(crypto.decrypt(second.ciphertext, second.nonce)).isEqualTo(WEBHOOK)
    }

    @Test
    fun `tampered ciphertext is not resolvable`() {
        val crypto = WebhookSecretCryptography.fromBase64(key)
        val encrypted = crypto.encrypt(WEBHOOK)
        encrypted.ciphertext[0] = (encrypted.ciphertext[0].toInt() xor 1).toByte()

        assertThat(crypto.decrypt(encrypted.ciphertext, encrypted.nonce)).isNull()
    }

    @Test
    fun `missing invalid or non AES 256 keys fail fast without revealing key material`() {
        assertThatThrownBy { WebhookSecretCryptography.fromBase64("") }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("EAFC_DISCORD_SECRET_ENCRYPTION_KEY")
        assertThatThrownBy { WebhookSecretCryptography.fromBase64("not-base64") }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Base64")
        assertThatThrownBy { WebhookSecretCryptography.fromBase64(Base64.getEncoder().encodeToString(ByteArray(16))) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("32 bytes")
    }

    companion object {
        private const val WEBHOOK = "https://discord.com/api/webhooks/123/secret-token"
    }
}
