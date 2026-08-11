package com.eafc26.discordstats.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import reactor.netty.http.client.HttpClient
import io.netty.channel.ChannelOption
import java.time.Duration

@Configuration
class WebClientConfig {

    /** HTTP client for the authenticated internal EA Gateway contract. */
    @Bean
    fun eaGatewayWebClient(props: AppProperties): WebClient =
        WebClient.builder()
            .baseUrl(props.ea.gatewayBaseUrl)
            .defaultHeader("Accept", "application/json")
            .defaultHeader("Authorization", "Bearer ${props.ea.gatewayInternalToken}")
            .clientConnector(ReactorClientHttpConnector(HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
                .responseTimeout(Duration.ofSeconds(30))))
            .build()
}
