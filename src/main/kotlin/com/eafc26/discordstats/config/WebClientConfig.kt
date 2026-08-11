package com.eafc26.discordstats.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import reactor.netty.http.client.HttpClient
import io.netty.channel.ChannelOption
import java.time.Duration

@Configuration
class WebClientConfig {

    /** HTTP client for the authenticated internal EA Gateway contract. */
    @Bean
    @Primary
    fun eaGatewayWebClient(props: AppProperties): WebClient =
        internalEaGatewayClient(props, connectTimeoutMillis = 10_000, responseTimeout = Duration.ofSeconds(30))

    /**
     * A deliberately short client used only by the administrative health probe.
     * It must never inherit the acquisition client's retry/read budget, otherwise a
     * degraded EA Gateway could make the aggregated system health unavailable.
     */
    @Bean("eaGatewayHealthWebClient")
    fun eaGatewayHealthWebClient(props: AppProperties): WebClient =
        internalEaGatewayClient(props, connectTimeoutMillis = 2_500, responseTimeout = Duration.ofMillis(2_500))

    private fun internalEaGatewayClient(
        props: AppProperties,
        connectTimeoutMillis: Int,
        responseTimeout: Duration,
    ): WebClient =
        WebClient.builder()
            .baseUrl(props.ea.gatewayBaseUrl)
            .defaultHeader("Accept", "application/json")
            .defaultHeader("Authorization", "Bearer ${props.ea.gatewayInternalToken}")
            .clientConnector(ReactorClientHttpConnector(HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMillis)
                .responseTimeout(responseTimeout)))
            .build()
}
