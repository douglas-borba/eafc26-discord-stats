package com.eafc26.discordstats.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.config.web.server.SecurityWebFiltersOrder
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository
import org.springframework.security.web.server.csrf.CsrfToken
import org.springframework.security.web.server.csrf.ServerCsrfTokenRequestAttributeHandler
import org.springframework.security.web.server.csrf.CsrfWebFilter
import org.springframework.web.server.WebFilter
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher
import reactor.core.publisher.Mono

@Configuration
@EnableWebFluxSecurity
class SecurityConfig(private val adminInternalTokenWebFilter: AdminInternalTokenWebFilter) {
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
                    // The public trial-interest form has no authenticated session to protect.
                    .requireCsrfProtectionMatcher(ServerWebExchangeMatcher { exchange ->
                        if (exchange.request.method == HttpMethod.POST && exchange.request.path.value() == "/api/trial-requests") {
                            ServerWebExchangeMatcher.MatchResult.notMatch()
                        } else CsrfWebFilter.DEFAULT_CSRF_MATCHER.matches(exchange)
                    })
            }
            .authorizeExchange {
                it.pathMatchers(HttpMethod.GET, "/api/health", "/api/clubs/**").permitAll()
                    .pathMatchers(HttpMethod.POST, "/api/trial-requests").permitAll()
                    .pathMatchers("/api/admin/**").authenticated()
                    .pathMatchers("/api/dev/**").denyAll()
                    .pathMatchers("/api/matches/**", "/api/panorama/regenerate", "/api/settings/**", "/api/setup/**", "/api/application/**", "/settings", "/setup").authenticated()
                    .pathMatchers(HttpMethod.GET, "/api/history/**", "/api/player-profiles/**", "/api/opponents/**", "/api/match-card/**", "/api/match-comparisons/**", "/api/panorama", "/api/polling/status").permitAll()
                    .pathMatchers(HttpMethod.GET, "/", "/history", "/players", "/opponents/**", "/compare", "/match-card", "/insights").permitAll()
                    .pathMatchers(HttpMethod.GET, *PublicStaticResources.pathPatterns).permitAll()
                    .anyExchange().denyAll()
            }
            .addFilterAt(adminInternalTokenWebFilter, SecurityWebFiltersOrder.AUTHENTICATION)
            .formLogin { it.disable() }
            .logout { it.disable() }
            .httpBasic { it.disable() }
            .build()
    }

    @Bean
    fun csrfTokenSubscriptionFilter(): WebFilter = WebFilter { exchange, chain ->
        // In WebFlux, CSRF token must be subscribed to be materialized in the cookie.
        // Without subscription, the token generation never happens.
        exchange.getAttribute<Mono<CsrfToken>>(CsrfToken::class.java.name)
            ?.flatMap { chain.filter(exchange) }
            ?: chain.filter(exchange)
    }
}
