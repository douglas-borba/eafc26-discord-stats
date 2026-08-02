package com.eafc26.discordstats.security

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.core.userdetails.User
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationEntryPoint
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository
import org.springframework.security.web.server.csrf.ServerCsrfTokenRequestAttributeHandler
import org.springframework.security.web.server.savedrequest.WebSessionServerRequestCache
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
class SecurityConfig {
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun users(properties: AccessSecurityProperties, encoder: PasswordEncoder): MapReactiveUserDetailsService {
        require(properties.viewerPassword.isNotBlank()) {
            "EAFC_VIEWER_PASSWORD must be configured before the application can start."
        }
        require(properties.adminPassword.isNotBlank()) {
            "EAFC_ADMIN_PASSWORD must be configured before the application can start."
        }
        require(properties.viewerPassword != properties.adminPassword) {
            "EAFC_VIEWER_PASSWORD and EAFC_ADMIN_PASSWORD must be different."
        }
        return MapReactiveUserDetailsService(
            User.withUsername("viewer").password(encoder.encode(properties.viewerPassword)).roles("VIEWER").build(),
            User.withUsername("admin").password(encoder.encode(properties.adminPassword)).roles("ADMIN", "VIEWER").build(),
        )
    }

    @Bean
    fun securityWebFilterChain(http: ServerHttpSecurity, objectMapper: ObjectMapper): SecurityWebFilterChain {
        val csrfRepository = CookieServerCsrfTokenRepository.withHttpOnlyFalse().apply {
            setCookieCustomizer { cookie -> cookie.sameSite("Lax") }
        }
        val apiDenied = ServerAccessDeniedHandler { exchange, _ -> jsonError(exchange, HttpStatus.FORBIDDEN, "forbidden", objectMapper) }
        val navigablePages = NavigablePageRequestMatcher()
        val requestCache = WebSessionServerRequestCache().apply {
            setSaveRequestMatcher(navigablePages)
        }
        val loginEntryPoint = RedirectServerAuthenticationEntryPoint("/login")

        return http
            .csrf {
                it.csrfTokenRepository(csrfRepository)
                    .csrfTokenRequestHandler(ServerCsrfTokenRequestAttributeHandler())
            }
            .requestCache { it.requestCache(requestCache) }
            .authorizeExchange {
                it.pathMatchers("/login", "/admin/login", "/access-denied", "/session-expired", "/api/health", "/error").permitAll()
                it.pathMatchers(*PublicStaticResources.pathPatterns).permitAll()
                it.pathMatchers("/settings", "/setup").hasRole("ADMIN")
                it.pathMatchers(
                    "/api/settings/**", "/api/setup/**", "/api/dev/**", "/api/matches/**",
                    "/api/polling/**", "/api/application/**",
                ).hasRole("ADMIN")
                it.anyExchange().authenticated()
            }
            .formLogin {
                it.loginPage("/login")
                    .authenticationSuccessHandler { webFilterExchange, _ ->
                        webFilterExchange.exchange.session.flatMap { session ->
                            val target = navigablePages.safeRedirectTarget(session.attributes.remove(SAVED_REQUEST) as? String)
                            webFilterExchange.exchange.response.apply {
                                statusCode = HttpStatus.SEE_OTHER
                                headers.location = java.net.URI.create(target)
                            }.setComplete()
                        }
                    }
                    .authenticationFailureHandler { webFilterExchange, _ ->
                        webFilterExchange.exchange.formData.flatMap { data ->
                            val path = if (data.getFirst("username") == "admin") "/admin/login?error" else "/login?error"
                            webFilterExchange.exchange.response.apply {
                                statusCode = HttpStatus.SEE_OTHER
                                headers.location = java.net.URI.create(path)
                            }.setComplete()
                        }
                    }
            }
            .logout {
                it.logoutUrl("/logout")
                    .logoutSuccessHandler { exchange, _ ->
                        exchange.exchange.response.apply {
                            statusCode = HttpStatus.SEE_OTHER
                            headers.location = java.net.URI.create("/login?logout")
                        }.setComplete()
                    }
            }
            .exceptionHandling {
                it.authenticationEntryPoint { exchange, exception ->
                    if (exchange.request.path.value().startsWith("/api/")) {
                        jsonError(exchange, HttpStatus.UNAUTHORIZED, "authentication_required", objectMapper)
                    } else {
                        if (!navigablePages.isNavigablePageRequest(exchange)) {
                            loginEntryPoint.commence(exchange, exception)
                        } else exchange.session.flatMap { session ->
                            session.attributes[SAVED_REQUEST] = exchange.request.uri.rawPath +
                                (exchange.request.uri.rawQuery?.let { query -> "?$query" } ?: "")
                            loginEntryPoint.commence(exchange, exception)
                        }
                    }
                }
                it.accessDeniedHandler { exchange, denied ->
                    if (exchange.request.path.value().startsWith("/api/")) apiDenied.handle(exchange, denied)
                    else exchange.response.apply {
                        statusCode = HttpStatus.SEE_OTHER
                        headers.location = java.net.URI.create("/access-denied")
                    }.setComplete()
                }
            }
            .build()
    }

    companion object {
        private const val SAVED_REQUEST = "EAFC_SAVED_REQUEST"
    }

    private fun jsonError(exchange: ServerWebExchange, status: HttpStatus, code: String, mapper: ObjectMapper): Mono<Void> {
        exchange.response.statusCode = status
        exchange.response.headers.contentType = MediaType.APPLICATION_JSON
        val bytes = mapper.writeValueAsBytes(mapOf("error" to code))
        return exchange.response.writeWith(Mono.just(exchange.response.bufferFactory().wrap(bytes)))
    }
}
