package com.eafc26.discordstats.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
@ConditionalOnProperty(name = ["app.ea.client"], havingValue = "webclient", matchIfMissing = true)
class WebClientConfig {

    /**
     * WebClient pre-configured for proclubs.ea.com.
     * Headers verified as required by community projects to avoid 403 responses.
     */
    @Bean
    fun eaWebClient(props: AppProperties): WebClient =
        WebClient.builder()
            .baseUrl(props.ea.baseUrl)
            .defaultHeader("User-Agent", props.ea.userAgent)
            .defaultHeader("Accept", "application/json")
            .defaultHeader("Origin", "https://www.ea.com")
            .defaultHeader("Referer", "https://www.ea.com/")
            .build()
}
