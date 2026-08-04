package com.eafc26.discordstats.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository
import org.springframework.security.web.server.csrf.ServerCsrfTokenRequestAttributeHandler

@Configuration
@EnableWebFluxSecurity
class SecurityConfig {
    @Bean
    fun securityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        val csrfRepository = CookieServerCsrfTokenRepository.withHttpOnlyFalse().apply {
            setCookieCustomizer { cookie -> cookie.sameSite("Lax") }
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
}
