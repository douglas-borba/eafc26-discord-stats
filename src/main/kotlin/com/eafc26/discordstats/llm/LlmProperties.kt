package com.eafc26.discordstats.llm

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.llm")
data class LlmProperties(
    val enabled: Boolean = false,
    val provider: String = "openrouter",
    val model: String = "anthropic/claude-sonnet-4",
    val apiKey: String = "",
    val baseUrl: String = "https://openrouter.ai/api",
    val timeoutSeconds: Long = 15,
    val temperature: Double = 0.7,
    val maxTokens: Int = 256,
)
