package com.eafc26.discordstats.llm

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.time.Duration

class OpenRouterEditorialProvider(
    private val webClient: WebClient,
    private val objectMapper: ObjectMapper,
    private val properties: LlmProperties,
) : EditorialLlmProvider {

    private val log = LoggerFactory.getLogger(javaClass)

    override val providerName: String = "openrouter"

    override fun generateMatchNarrative(context: EditorialContext): LlmEditorialResult {
        val (system, user) = EditorialPrompts.matchNarrativePrompt(context)
        return callApi(system, user)
    }

    override fun generatePanorama(context: EditorialContext): LlmEditorialResult {
        val (system, user) = EditorialPrompts.panoramaPrompt(context)
        return callApi(system, user)
    }

    private fun callApi(system: String, userMessage: String): LlmEditorialResult {
        val requestBody = mapOf(
            "model" to properties.model,
            "max_tokens" to properties.maxTokens,
            "temperature" to properties.temperature,
            "messages" to listOf(
                mapOf("role" to "system", "content" to system),
                mapOf("role" to "user", "content" to userMessage),
            ),
        )

        val startNanos = System.nanoTime()
        return try {
            val responseBody = webClient.post()
                .uri("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer ${properties.apiKey}")
                .bodyValue(objectMapper.writeValueAsString(requestBody))
                .retrieve()
                .bodyToMono(String::class.java)
                .block(Duration.ofSeconds(properties.timeoutSeconds))
                ?: return LlmEditorialResult.Failure("Empty response from OpenRouter API")

            val latencyMs = (System.nanoTime() - startNanos) / 1_000_000
            parseResponse(responseBody, latencyMs)
        } catch (ex: WebClientResponseException) {
            log.warn("OpenRouter API error {}: {}", ex.statusCode, ex.responseBodyAsString.take(200))
            LlmEditorialResult.Failure("OpenRouter API HTTP ${ex.statusCode}", ex)
        } catch (ex: Exception) {
            log.warn("OpenRouter API call failed: {}", ex.message)
            LlmEditorialResult.Failure(ex.message ?: "Unknown error", ex)
        }
    }

    private fun parseResponse(body: String, latencyMs: Long): LlmEditorialResult {
        return try {
            val root = objectMapper.readTree(body)

            val error = root.path("error")
            if (!error.isMissingNode && error.has("message")) {
                return LlmEditorialResult.Failure("OpenRouter error: ${error.path("message").asText()}")
            }

            val text = root.path("choices")
                .firstOrNull()
                ?.path("message")
                ?.path("content")
                ?.asText()
                ?: return LlmEditorialResult.Failure("No content in OpenRouter response")

            val usage = root.path("usage")
            LlmEditorialResult.Success(
                text = text.trim(),
                metadata = LlmMetadata(
                    provider = providerName,
                    model = root.path("model").asText(properties.model),
                    inputTokens = usage.path("prompt_tokens").takeIf { !it.isMissingNode }?.asInt(),
                    outputTokens = usage.path("completion_tokens").takeIf { !it.isMissingNode }?.asInt(),
                    latencyMs = latencyMs,
                ),
            )
        } catch (ex: Exception) {
            log.warn("Failed to parse OpenRouter response: {}", ex.message)
            LlmEditorialResult.Failure("Response parse error: ${ex.message}", ex)
        }
    }
}
