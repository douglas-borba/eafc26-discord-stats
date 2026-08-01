package com.eafc26.discordstats.web

import com.eafc26.discordstats.config.WebhookConfigService
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.security.core.Authentication
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.net.URI

/**
 * Redirects all requests to /setup when the Discord webhook has not yet been configured.
 *
 * Pass-through paths (always allowed regardless of setup state):
 *   /setup, /api/setup/..., /api/health
 */
@Component
class SetupRedirectFilter(private val webhookConfigService: WebhookConfigService) : WebFilter {

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        // Both webhooks must be configured before the app is usable
        if (webhookConfigService.isConfigured() && webhookConfigService.isHistoryConfigured())
            return chain.filter(exchange)

        val path = exchange.request.path.value()
        if (isPassThrough(path)) return chain.filter(exchange)

        return exchange.getPrincipal<Authentication>()
            .map { authentication -> authentication.authorities.any { it.authority == "ROLE_ADMIN" } }
            // Anonymous requests are rejected by Spring Security before this filter in
            // production. Treating a missing principal as the legacy local-admin case
            // preserves isolated filter/controller tests and first-run behavior.
            .defaultIfEmpty(true)
            .flatMap { isAdmin ->
                if (isAdmin) {
                    val response = exchange.response
                    response.statusCode = HttpStatus.FOUND
                    response.headers.location = URI.create("/setup")
                    response.setComplete()
                } else {
                    chain.filter(exchange)
                }
            }
    }

    private fun isPassThrough(path: String): Boolean =
        path == "/setup" ||
        path.startsWith("/api/setup") ||
        path == "/api/health" ||
        path == "/login" ||
        path == "/admin/login" ||
        path == "/access-denied" ||
        path == "/session-expired"
}
