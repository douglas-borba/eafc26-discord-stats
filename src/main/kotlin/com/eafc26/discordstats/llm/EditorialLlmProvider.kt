package com.eafc26.discordstats.llm

interface EditorialLlmProvider {

    val providerName: String

    fun generateMatchNarrative(context: EditorialContext): LlmEditorialResult

    fun generatePanorama(context: EditorialContext): LlmEditorialResult
}

sealed interface LlmEditorialResult {
    data class Success(val text: String, val metadata: LlmMetadata) : LlmEditorialResult
    data class Failure(val reason: String, val cause: Exception? = null) : LlmEditorialResult
}

data class LlmMetadata(
    val provider: String,
    val model: String,
    val inputTokens: Int?,
    val outputTokens: Int?,
    val latencyMs: Long? = null,
)
