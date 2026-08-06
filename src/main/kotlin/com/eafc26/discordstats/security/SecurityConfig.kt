package com.eafc26.discordstats.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository
import org.springframework.security.web.server.csrf.CsrfToken
import org.springframework.security.web.server.csrf.ServerCsrfTokenRequestAttributeHandler
import org.springframework.web.server.WebFilter
import reactor.core.publisher.Mono

@Configuration
@EnableWebFluxSecurity
class SecurityConfig {
    @Bean
    fun securityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        val csrfRepository = CookieServerCsrfTokenRepository.withHttpOnlyFalse().apply {
            setCookieCustomizer { cookie ->
                cookie.httpOnly(false)
                cookie.sameSite("Lax")
            }
        }

        return http
            .csrf {
                it.csrfTokenRepository(csrfRepository)
                    .csrfTokenRequestHandler(ServerCsrfTokenRequestAttributeHandler())
            }
            .authorizeExchange { it.anyExchange().permitAll() }
            .formLogin { it.disable() }
            .logout { it.disable() }
            .httpBasic { it.disable() }
            .build()
    }

    @Bean
    fun csrfTokenSubscriptionFilter(): WebFilter = WebFilter { exchange, chain ->
        exchange.getAttributeOrDefault<Mono<CsrfToken>>(CsrfToken::class.java.name, Mono.empty())
            .then(chain.filter(exchange))
    }
}
