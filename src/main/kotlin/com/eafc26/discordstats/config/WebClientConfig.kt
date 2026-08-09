package com.eafc26.discordstats.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class WebClientConfig {

    /** HTTP client for the authenticated internal EA Gateway contract. */
    @Bean
    fun eaGatewayWebClient(props: AppProperties): WebClient =
        WebClient.builder()
            .baseUrl(props.ea.gatewayBaseUrl)
            .defaultHeader("Accept", "application/json")
            .defaultHeader("Authorization", "Bearer ${props.ea.gatewayInternalToken}")
            .build()
}
