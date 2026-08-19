package com.eafc26.discordstats.web

import com.eafc26.discordstats.config.WebhookConfigService
import com.eafc26.discordstats.security.PublicStaticResources
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.net.URI

/**
 * Redirects all requests to /setup when the Discord webhook has not yet been configured.
 *
 * Pass-through paths (always allowed regardless of setup state):
 *   /setup, /api/setup/..., /api/admin/clubs/..., /api/admin/trial-requests/..., /api/clubs/{id}/..., /api/health
 */
@Component
class SetupRedirectFilter(private val webhookConfigService: WebhookConfigService) : WebFilter {

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        if (webhookConfigService.isConfigured())
            return chain.filter(exchange)

        val path = exchange.request.path.value()
        if (isPassThrough(path)) return chain.filter(exchange)

        val response = exchange.response
        response.statusCode = HttpStatus.FOUND
        response.headers.location = URI.create("/setup")
        return response.setComplete()
    }

    private fun isPassThrough(path: String): Boolean =
        path == "/setup" ||
        PublicStaticResources.contains(path) ||
        path.startsWith("/api/setup") ||
        path == "/api/admin/clubs" ||
        path.startsWith("/api/admin/clubs/") ||
        path == "/api/admin/trial-requests" ||
        path.startsWith("/api/admin/trial-requests/") ||
        path == "/api/admin/system/health" ||
        path == "/api/admin/system/canonical-read-diagnostics/reset" ||
        path.startsWith("/api/clubs/") ||
        path == "/api/trial-requests" ||
        path == "/api/health"
}
