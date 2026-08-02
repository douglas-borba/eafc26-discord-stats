package com.eafc26.discordstats.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class SecurityArchitectureTest {
    private val root = Path.of("src/main")

    @Test
    fun `security uses encoder and session instead of manual password comparison or JWT`() {
        val security = Files.readString(root.resolve("kotlin/com/eafc26/discordstats/security/SecurityConfig.kt"))
        assertThat(security).contains("PasswordEncoder", "formLogin", "CookieServerCsrfTokenRepository")
        assertThat(security).doesNotContain("JWT", "localStorage", "viewerPassword ==", "adminPassword ==")
    }

    @Test
    fun `viewer shell is role aware and administrative controls default to hidden`() {
        val shell = Files.readString(root.resolve("resources/static/app-shell.js"))
        val dashboard = Files.readString(root.resolve("resources/index.html"))
        assertThat(shell).contains("role === \"ADMIN\"", "data-logout")
        assertThat(dashboard).contains("data-admin-only")
    }

    @Test
    fun `administrative fetch materializes csrf and sends session credentials`() {
        val shell = Files.readString(root.resolve("resources/static/app-shell.js"))
        val dashboard = Files.readString(root.resolve("resources/index.html"))

        assertThat(shell).contains(
            "window.eafcFetch = applicationFetch",
            "credentials: \"same-origin\"",
            "headers.set(\"X-XSRF-TOKEN\", token)",
            "nativeFetch(\"/api/auth/session\"",
            "(await sessionInfo())?.csrfToken",
        )
        assertThat(dashboard).contains(
            "res.status === 401",
            "res.status === 403",
            "Esta ação exige acesso administrativo.",
        ).doesNotContain("const data = await res.json();\n        showFeedback(data);")
    }

    @Test
    fun `real secrets are excluded and compose maps external variables`() {
        val compose = Files.readString(Path.of("compose.yml"))
        val gitignore = Files.readString(Path.of(".gitignore"))
        assertThat(compose).contains("EAFC_VIEWER_PASSWORD: \${EAFC_VIEWER_PASSWORD", "EAFC_ADMIN_PASSWORD: \${EAFC_ADMIN_PASSWORD")
        assertThat(gitignore).contains(".env")
    }
}
