package com.eafc26.discordstats.discord

import java.nio.charset.StandardCharsets.UTF_8
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** AES-256-GCM boundary for durable Discord destinations. It never logs secret material. */
class WebhookSecretCryptography private constructor(
    private val key: SecretKeySpec,
    private val secureRandom: SecureRandom = SecureRandom(),
) {
    fun encrypt(value: String): EncryptedWebhookSecret {
        val nonce = ByteArray(NONCE_BYTES).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
        return EncryptedWebhookSecret(cipher.doFinal(value.toByteArray(UTF_8)), nonce)
    }

    fun decrypt(encrypted: ByteArray, nonce: ByteArray): String? = runCatching {
        require(nonce.size == NONCE_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
        String(cipher.doFinal(encrypted), UTF_8)
    }.getOrNull()

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val NONCE_BYTES = 12
        private const val TAG_BITS = 128
        private const val AES_256_BYTES = 32

        fun fromBase64(encodedKey: String): WebhookSecretCryptography {
            if (encodedKey.isBlank()) {
                throw IllegalStateException(
                    "EAFC_DISCORD_SECRET_ENCRYPTION_KEY must be configured when PostgreSQL webhook storage is enabled.",
                )
            }
            val decoded = try {
                Base64.getDecoder().decode(encodedKey)
            } catch (_: IllegalArgumentException) {
                throw IllegalStateException(
                    "EAFC_DISCORD_SECRET_ENCRYPTION_KEY must be a Base64-encoded 32-byte AES-256 key.",
                )
            }
            if (decoded.size != AES_256_BYTES) {
                throw IllegalStateException(
                    "EAFC_DISCORD_SECRET_ENCRYPTION_KEY must decode to exactly 32 bytes for AES-256.",
                )
            }
            return WebhookSecretCryptography(SecretKeySpec(decoded, "AES"))
        }
    }
}

data class EncryptedWebhookSecret(
    val ciphertext: ByteArray,
    val nonce: ByteArray,
)
