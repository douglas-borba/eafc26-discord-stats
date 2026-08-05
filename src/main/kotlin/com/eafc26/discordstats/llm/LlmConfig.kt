package com.eafc26.discordstats.llm

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
@EnableConfigurationProperties(LlmProperties::class)
class LlmConfig {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun editorialLlmProvider(
        properties: LlmProperties,
        objectMapper: ObjectMapper,
    ): EditorialLlmProvider? {
        if (!properties.enabled) {
            log.info("LLM editorial generation is disabled")
            return null
        }

        return when (properties.provider) {
            "openrouter" -> {
                require(properties.apiKey.isNotBlank()) {
                    "app.llm.api-key must be set when LLM is enabled"
                }
                val webClient = WebClient.builder()
                    .baseUrl(properties.baseUrl)
                    .build()
                log.info("LLM editorial provider: OpenRouter (model={})", properties.model)
                OpenRouterEditorialProvider(webClient, objectMapper, properties)
            }
            else -> {
                log.warn("Unknown LLM provider '{}', editorial generation disabled", properties.provider)
                null
            }
        }
    }
}
