package com.eafc26.discordstats.security

import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

/** Restricts saved requests to browser navigations to protected HTML pages. */
class NavigablePageRequestMatcher : ServerWebExchangeMatcher {
    override fun matches(exchange: ServerWebExchange): Mono<ServerWebExchangeMatcher.MatchResult> =
        if (isNavigablePageRequest(exchange)) {
            ServerWebExchangeMatcher.MatchResult.match()
        } else {
            ServerWebExchangeMatcher.MatchResult.notMatch()
        }

    fun isNavigablePageRequest(exchange: ServerWebExchange): Boolean {
        val request = exchange.request
        if (request.method != HttpMethod.GET || !isProtectedPagePath(request.path.value())) return false
        if (!request.headers.acceptsHtml()) return false
        if (request.headers.getFirst("X-Requested-With")?.equals("XMLHttpRequest", ignoreCase = true) == true) return false

        val fetchMode = request.headers.getFirst("Sec-Fetch-Mode")
        return fetchMode == null || fetchMode.equals("navigate", ignoreCase = true)
    }

    fun safeRedirectTarget(rawTarget: String?): String {
        if (rawTarget.isNullOrBlank() || !rawTarget.startsWith('/') || rawTarget.startsWith("//")) return "/"
        val path = rawTarget.substringBefore('?').substringBefore('#')
        return if (isProtectedPagePath(path)) rawTarget else "/"
    }

    private fun isProtectedPagePath(path: String): Boolean =
        path in EXACT_PROTECTED_PAGES || path.startsWith("/opponents/")

    private fun HttpHeaders.acceptsHtml(): Boolean = accept.any { accepted ->
        accepted.type.equals(MediaType.TEXT_HTML.type, ignoreCase = true) &&
            accepted.subtype.equals(MediaType.TEXT_HTML.subtype, ignoreCase = true)
    }

    private companion object {
        val EXACT_PROTECTED_PAGES = setOf(
            "/", "/history", "/players", "/opponents", "/compare", "/match-card", "/insights", "/settings", "/setup",
        )
    }
}
