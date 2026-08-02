package com.eafc26.discordstats.security

import com.eafc26.discordstats.config.WebhookConfigService
import com.eafc26.discordstats.config.WebhookConfigurationSource
import com.eafc26.discordstats.dev.DevSimulatorService
import com.eafc26.discordstats.service.AcquisitionResult
import com.eafc26.discordstats.service.AcquisitionTrigger
import com.eafc26.discordstats.service.MatchAcquisitionService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.util.MultiValueMap
import org.springframework.web.reactive.function.BodyInserters

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "app.security.viewer-password=viewer-test-secret",
        "app.security.admin-password=admin-test-secret",
        "app.polling.enabled=false",
        "eafc.dashboard.auto-open=false",
    ],
)
@AutoConfigureWebTestClient
class SecurityIntegrationTest {
    @Autowired lateinit var client: WebTestClient
    @MockBean lateinit var webhookConfigService: WebhookConfigService
    @MockBean lateinit var devSimulatorService: DevSimulatorService
    @MockBean lateinit var matchAcquisitionService: MatchAcquisitionService

    @BeforeEach
    fun configuredApplication() {
        whenever(webhookConfigService.isConfigured()).thenReturn(true)
        whenever(webhookConfigService.isHistoryConfigured()).thenReturn(true)
        whenever(webhookConfigService.getWebhookSource()).thenReturn(WebhookConfigurationSource.ENVIRONMENT)
        whenever(webhookConfigService.getHistoryWebhookSource()).thenReturn(WebhookConfigurationSource.ENVIRONMENT)
        whenever(matchAcquisitionService.acquire(AcquisitionTrigger.MANUAL)).thenReturn(AcquisitionResult.NoMatches)
    }

    @Test
    fun `health is public and minimal`() {
        client.get().uri("/api/health").exchange().expectStatus().isOk
            .expectBody().json("""{"status":"ok"}""").jsonPath("$.length()").isEqualTo(1)
    }

    @Test
    fun `anonymous page redirects while protected API returns JSON 401`() {
        client.get().uri("/players?playerId=player-1").accept(MediaType.TEXT_HTML).exchange().expectStatus().is3xxRedirection
            .expectHeader().valueEquals("Location", "/login")
        client.get().uri("/api/player-profiles").exchange().expectStatus().isUnauthorized
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody().jsonPath("$.error").isEqualTo("authentication_required")
    }

    @Test
    fun `static interface resources are public and keep their media types`() {
        listOf(
            "/app-shell.css", "/editorial-design-system.css", "/match-history.css",
            "/match-summary-card.css", "/opponents.css",
        ).forEach { path ->
            client.get().uri(path).exchange().expectStatus().isOk
                .expectHeader().contentTypeCompatibleWith(MediaType.parseMediaType("text/css"))
        }
        listOf(
            "/app-shell.js", "/editorial-design-system.js", "/match-history.js", "/opponents.js",
        ).forEach { path ->
            client.get().uri(path).exchange().expectStatus().isOk
                .expectHeader().contentTypeCompatibleWith(MediaType.parseMediaType("application/javascript"))
        }
    }

    @Test
    fun `root navigation survives public asset requests and login never redirects to an asset`() {
        val requested = client.get().uri("/").accept(MediaType.TEXT_HTML).exchange()
            .expectStatus().is3xxRedirection.returnResult(Void::class.java)
        val session = requested.responseCookies.getFirst("SESSION")!!.value

        client.get().uri("/app-shell.css").cookie("SESSION", session).exchange().expectStatus().isOk
        client.get().uri("/app-shell.js").cookie("SESSION", session).exchange().expectStatus().isOk

        assertThat(loginWithSession(session)).isEqualTo("/")
    }

    @Test
    fun `anonymous API request is not restored after login`() {
        val api = client.get().uri("/api/player-profiles").accept(MediaType.APPLICATION_JSON).exchange()
            .expectStatus().isUnauthorized.returnResult(Void::class.java)
        val session = api.responseCookies.getFirst("SESSION")?.value

        assertThat(loginWithSession(session)).isEqualTo("/")
    }

    @Test
    fun `sports pages remain protected while static resources are public`() {
        listOf("/", "/history", "/players", "/opponents", "/opponents/club-1", "/compare", "/match-card").forEach { path ->
            client.get().uri(path).accept(MediaType.TEXT_HTML).exchange().expectStatus().is3xxRedirection
                .expectHeader().valueEquals("Location", "/login")
        }
    }

    @Test
    fun `viewer login creates session and grants sports access`() {
        val auth = login("viewer", "viewer-test-secret", "/login")
        client.get().uri("/api/auth/session").cookie("SESSION", auth.session).exchange().expectStatus().isOk
            .expectBody().jsonPath("$.role").isEqualTo("VIEWER")
        listOf("/", "/history", "/players", "/opponents", "/compare", "/match-card").forEach { path ->
            client.get().uri(path).cookie("SESSION", auth.session).exchange().expectStatus().isOk
        }
    }

    @Test
    fun `viewer is blocked from settings and administrative API`() {
        val auth = login("viewer", "viewer-test-secret", "/login")
        client.get().uri("/settings").cookie("SESSION", auth.session).exchange().expectStatus().is3xxRedirection
            .expectHeader().valueEquals("Location", "/access-denied")
        client.get().uri("/api/settings/info").cookie("SESSION", auth.session).exchange().expectStatus().isForbidden
            .expectBody().jsonPath("$.error").isEqualTo("forbidden")
    }

    @Test
    fun `admin has administrative access`() {
        val auth = login("admin", "admin-test-secret", "/admin/login")
        client.get().uri("/api/auth/session").cookie("SESSION", auth.session).exchange().expectBody()
            .jsonPath("$.role").isEqualTo("ADMIN")
        client.get().uri("/settings").cookie("SESSION", auth.session).exchange().expectStatus().isOk
        client.get().uri("/api/settings/info").cookie("SESSION", auth.session).exchange().expectStatus().isOk
    }

    @Test
    fun `invalid and absent passwords do not authenticate`() {
        loginFailure("viewer", "incorrect", "/login?error")
        loginFailure("viewer", "", "/login?error")
        loginFailure("admin", "incorrect", "/admin/login?error")
    }

    @Test
    fun `administrative mutation requires csrf and admin`() {
        val admin = login("admin", "admin-test-secret", "/admin/login")
        client.post().uri("/api/dev/reset").cookie("SESSION", admin.session).exchange().expectStatus().isForbidden
        client.post().uri("/api/dev/reset")
            .cookie("SESSION", admin.session).cookie("XSRF-TOKEN", admin.csrfCookie)
            .header("X-XSRF-TOKEN", admin.csrfCookie)
            .exchange().expectStatus().isOk
    }

    @Test
    fun `notify latest remains admin only and requires valid csrf`() {
        client.post().uri("/api/matches/notify-latest").exchange().expectStatus().isForbidden

        val viewer = login("viewer", "viewer-test-secret", "/login")
        client.post().uri("/api/matches/notify-latest")
            .cookie("SESSION", viewer.session).cookie("XSRF-TOKEN", viewer.csrfCookie)
            .header("X-XSRF-TOKEN", viewer.csrfCookie)
            .exchange().expectStatus().isForbidden

        val admin = login("admin", "admin-test-secret", "/admin/login")
        client.post().uri("/api/matches/notify-latest")
            .cookie("SESSION", admin.session)
            .exchange().expectStatus().isForbidden
        client.post().uri("/api/matches/notify-latest")
            .cookie("SESSION", admin.session).cookie("XSRF-TOKEN", admin.csrfCookie)
            .header("X-XSRF-TOKEN", admin.csrfCookie)
            .exchange().expectStatus().isOk
            .expectBody().jsonPath("$.status").isEqualTo("no_matches")
    }

    @Test
    fun `logout invalidates authenticated session`() {
        val auth = login("viewer", "viewer-test-secret", "/login")
        client.post().uri("/logout")
            .cookie("SESSION", auth.session).cookie("XSRF-TOKEN", auth.csrfCookie)
            .header("X-XSRF-TOKEN", auth.csrfCookie)
            .exchange().expectStatus().is3xxRedirection
        client.get().uri("/players").cookie("SESSION", auth.session).exchange().expectStatus().is3xxRedirection
    }

    @Test
    fun `saved deep link is restored after login`() {
        val requested = client.get().uri("/players?playerId=player-1&from=history").accept(MediaType.TEXT_HTML).exchange()
            .expectStatus().is3xxRedirection.returnResult(Void::class.java)
        val initialSession = requested.responseCookies.getFirst("SESSION")!!.value
        val page = client.get().uri("/login").cookie("SESSION", initialSession).exchange().expectStatus().isOk
            .expectBody(String::class.java).returnResult()
        val token = csrfFrom(page.responseBody!!)
        val xsrf = page.responseCookies.getFirst("XSRF-TOKEN")!!.value
        client.post().uri("/login").cookie("SESSION", initialSession).cookie("XSRF-TOKEN", xsrf)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form("viewer", "viewer-test-secret", token)).exchange().expectStatus().is3xxRedirection
            .expectHeader().valueMatches("Location", ".*/players\\?playerId=player-1&from=history")
    }

    @Test
    fun `supported HTML deep links are restored after login`() {
        listOf(
            "/history?matchId=match-1",
            "/opponents/club-1",
            "/compare?left=match-1&right=match-2",
        ).forEach { target ->
            val requested = client.get().uri(target).accept(MediaType.TEXT_HTML).exchange()
                .expectStatus().is3xxRedirection.returnResult(Void::class.java)
            val session = requested.responseCookies.getFirst("SESSION")!!.value
            assertThat(loginWithSession(session)).isEqualTo(target)
        }
    }

    @Test
    fun `login and denied pages are branded and never whitelabel`() {
        client.get().uri("/login?error").exchange().expectBody(String::class.java).value { html ->
            assertThat(html).contains("EA FC STATS", "Associação BF", "Palavra-passe", "Palavra-passe inválida")
            assertThat(html).doesNotContain("Whitelabel Error Page")
            assertThat("type=\"password\"".toRegex().findAll(html).count()).isEqualTo(1)
        }
        client.get().uri("/access-denied").exchange().expectBody(String::class.java).value { html ->
            assertThat(html).contains("Acesso restrito").doesNotContain("Whitelabel Error Page")
        }
    }

    private fun login(username: String, password: String, pagePath: String): AuthSession {
        val page = client.get().uri(pagePath).exchange().expectStatus().isOk.expectBody(String::class.java).returnResult()
        val token = csrfFrom(page.responseBody!!)
        val xsrf = page.responseCookies.getFirst("XSRF-TOKEN")!!.value
        val result = client.post().uri("/login").cookie("XSRF-TOKEN", xsrf)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form(username, password, token)).exchange().expectStatus().is3xxRedirection
            .returnResult(Void::class.java)
        val session = result.responseCookies.getFirst("SESSION")!!.value
        val refreshed = client.get().uri("/api/auth/session").cookie("SESSION", session).exchange().expectStatus().isOk
            .returnResult(Void::class.java)
        val refreshedCsrf = refreshed.responseCookies.getFirst("XSRF-TOKEN")!!.value
        return AuthSession(session, refreshedCsrf)
    }

    private fun loginFailure(username: String, password: String, expectedLocation: String) {
        val pagePath = if (username == "admin") "/admin/login" else "/login"
        val page = client.get().uri(pagePath).exchange().expectBody(String::class.java).returnResult()
        val xsrf = page.responseCookies.getFirst("XSRF-TOKEN")!!.value
        client.post().uri("/login").cookie("XSRF-TOKEN", xsrf)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form(username, password, csrfFrom(page.responseBody!!))).exchange().expectStatus().is3xxRedirection
            .expectHeader().valueEquals("Location", expectedLocation)
    }

    private fun loginWithSession(initialSession: String?): String {
        val pageRequest = client.get().uri("/login").run {
            if (initialSession == null) this else cookie("SESSION", initialSession)
        }
        val page = pageRequest.exchange().expectStatus().isOk.expectBody(String::class.java).returnResult()
        val xsrf = page.responseCookies.getFirst("XSRF-TOKEN")!!.value
        val loginRequest = client.post().uri("/login").cookie("XSRF-TOKEN", xsrf).run {
            if (initialSession == null) this else cookie("SESSION", initialSession)
        }
        return loginRequest.contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form("viewer", "viewer-test-secret", csrfFrom(page.responseBody!!))).exchange()
            .expectStatus().is3xxRedirection.returnResult(Void::class.java).responseHeaders.location!!.toString()
    }

    private fun form(username: String, password: String, csrf: String) = BodyInserters.fromFormData("username", username)
        .with("password", password).with("_csrf", csrf)

    private fun csrfFrom(html: String): String = Regex("name=\"_csrf\" value=\"([^\"]+)\"").find(html)!!.groupValues[1]

    private data class AuthSession(val session: String, val csrfCookie: String)
}
