package com.eafc26.discordstats.security

import com.eafc26.discordstats.config.AppProperties
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.security.MessageDigest

/** Authenticates only the server-to-server administrative boundary. */
@Component
class AdminInternalTokenWebFilter(private val properties: AppProperties) : WebFilter {
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val expected = properties.security.adminInternalToken.toByteArray()
        val supplied = exchange.request.headers.getFirst("Authorization")
            ?.removePrefix("Bearer ")?.toByteArray() ?: ByteArray(0)
        if (expected.isEmpty() || !MessageDigest.isEqual(expected, supplied)) return chain.filter(exchange)
        val auth = UsernamePasswordAuthenticationToken(
            "nextjs-admin-bff", null, listOf(SimpleGrantedAuthority("ROLE_INTERNAL_ADMIN")),
        )
        return chain.filter(exchange).contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth))
    }
}
