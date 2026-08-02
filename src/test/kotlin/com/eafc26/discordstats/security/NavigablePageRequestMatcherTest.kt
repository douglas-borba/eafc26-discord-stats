package com.eafc26.discordstats.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange

class NavigablePageRequestMatcherTest {
    private val matcher = NavigablePageRequestMatcher()

    @Test
    fun `only GET HTML page navigation is eligible for saving`() {
        assertThat(matches(MockServerHttpRequest.get("/players?playerId=1").accept(MediaType.TEXT_HTML))).isTrue()
        assertThat(matches(MockServerHttpRequest.method(HttpMethod.POST, "/players").accept(MediaType.TEXT_HTML))).isFalse()
        assertThat(matches(MockServerHttpRequest.get("/players").accept(MediaType.APPLICATION_JSON))).isFalse()
        assertThat(matches(MockServerHttpRequest.get("/players").accept(MediaType.ALL))).isFalse()
        assertThat(matches(MockServerHttpRequest.get("/players").accept(MediaType.TEXT_HTML).header("X-Requested-With", "XMLHttpRequest"))).isFalse()
        assertThat(matches(MockServerHttpRequest.get("/players").accept(MediaType.TEXT_HTML).header("Sec-Fetch-Mode", "cors"))).isFalse()
    }

    @Test
    fun `assets APIs and internal paths are never eligible for saving`() {
        listOf("/app-shell.css", "/app-shell.js", "/favicon.ico", "/images/club.png", "/api/history", "/api/health", "/login").forEach { path ->
            assertThat(matches(MockServerHttpRequest.get(path).accept(MediaType.TEXT_HTML))).describedAs(path).isFalse()
        }
    }

    @Test
    fun `public static resource policy covers current and nested interface assets`() {
        listOf("/app-shell.css", "/app-shell.js", "/favicon.ico", "/images/club.png", "/icons/ball.svg", "/fonts/sport.woff2", "/static/theme.css").forEach {
            assertThat(PublicStaticResources.contains(it)).describedAs(it).isTrue()
        }
        listOf("/", "/players", "/history", "/api/history").forEach {
            assertThat(PublicStaticResources.contains(it)).describedAs(it).isFalse()
        }
    }

    @Test
    fun `redirect target accepts only known protected pages`() {
        assertThat(matcher.safeRedirectTarget("/history?matchId=1")).isEqualTo("/history?matchId=1")
        assertThat(matcher.safeRedirectTarget("/opponents/club-1")).isEqualTo("/opponents/club-1")
        listOf("/app-shell.css", "/api/history", "//external.example", "https://external.example").forEach { target ->
            assertThat(matcher.safeRedirectTarget(target)).isEqualTo("/")
        }
    }

    private fun matches(request: MockServerHttpRequest.BaseBuilder<*>): Boolean = matcher
        .matches(MockServerWebExchange.from(request.build())).block()!!.isMatch
}
