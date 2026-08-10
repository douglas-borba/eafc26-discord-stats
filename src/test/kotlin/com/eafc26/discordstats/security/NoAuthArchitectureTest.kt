package com.eafc26.discordstats.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class NoAuthArchitectureTest {
    private val root = Path.of("src/main")

    @Test
    fun `no login pages exist`() {
        assertThat(Files.exists(root.resolve("kotlin/com/eafc26/discordstats/security/AccessController.kt"))).isFalse()
    }

    @Test
    fun `no password properties exist`() {
        assertThat(Files.exists(root.resolve("kotlin/com/eafc26/discordstats/security/AccessSecurityProperties.kt"))).isFalse()
        val yml = Files.readString(root.resolve("resources/application.yml"))
        assertThat(yml).doesNotContain("viewer-password", "admin-password")
    }

    @Test
    fun `no role selection exists in production code`() {
        val shell = Files.readString(root.resolve("resources/static/app-shell.js"))
        val css = Files.readString(root.resolve("resources/static/app-shell.css"))
        val dashboard = Files.readString(root.resolve("resources/index.html"))

        assertThat(shell).doesNotContain("data-logout", "VIEWER", "/logout", "/login")
        assertThat(css).doesNotContain("data-admin-only", "data-access-role", "app-shell-logout", "app-shell-role")
        assertThat(dashboard).doesNotContain("data-admin-only", "data-admin-hidden")
    }

    @Test
    fun `security config keeps sports public and fails closed for administrative routes`() {
        val config = Files.readString(root.resolve("kotlin/com/eafc26/discordstats/security/SecurityConfig.kt"))
        assertThat(config).contains("HttpMethod.GET, \"/api/health\"")
        assertThat(config).contains("pathMatchers(\"/api/admin/**\").authenticated()")
        assertThat(config).contains("anyExchange().denyAll()")
        assertThat(config).doesNotContain("PasswordEncoder", "MapReactiveUserDetailsService", "loginPage")
        assertThat(config).contains("formLogin { it.disable() }")
        assertThat(config).contains("logout { it.disable() }")
    }

    @Test
    fun `no admin or viewer role references in app shell`() {
        val shell = Files.readString(root.resolve("resources/static/app-shell.js"))
        assertThat(shell).doesNotContain("role === \"ADMIN\"", "role === \"VIEWER\"", "Administrador", "Sair")
    }

    @Test
    fun `setup page does not use password input type`() {
        val setup = Files.readString(root.resolve("resources/setup.html"))
        assertThat(setup).doesNotContain("type=\"password\"")
        assertThat(setup).doesNotContain("autocomplete=\"new-password\"")
    }

    @Test
    fun `setup redirect filter does not depend on authentication`() {
        val filter = Files.readString(root.resolve("kotlin/com/eafc26/discordstats/web/SetupRedirectFilter.kt"))
        assertThat(filter).doesNotContain("Authentication", "ROLE_ADMIN", "isAdmin", "getPrincipal")
    }

    @Test
    fun `build configuration does not set security passwords`() {
        val build = Files.readString(Path.of("build.gradle.kts"))
        assertThat(build).doesNotContain("viewer-password", "admin-password", "viewer-local", "admin-local")
        assertThat(build).doesNotContain("EAFC_VIEWER_PASSWORD", "EAFC_ADMIN_PASSWORD")
    }

    @Test
    fun `compose configuration does not set security passwords`() {
        val compose = Files.readString(Path.of("compose.yml"))
        assertThat(compose).doesNotContain("EAFC_VIEWER_PASSWORD", "EAFC_ADMIN_PASSWORD")
        assertThat(compose).doesNotContain("EAFC_COOKIE_SECURE", "EAFC_SESSION_TIMEOUT")
    }

    @Test
    fun `csrf protection is preserved for mutations`() {
        val config = Files.readString(root.resolve("kotlin/com/eafc26/discordstats/security/SecurityConfig.kt"))
        assertThat(config).contains("CookieServerCsrfTokenRepository")
        val shell = Files.readString(root.resolve("resources/static/app-shell.js"))
        assertThat(shell).contains("X-XSRF-TOKEN")
    }

    @Test
    fun `railway uses the public Spring health endpoint`() {
        val railway = Files.readString(Path.of("railway.json"))
        assertThat(railway).contains("\"healthcheckPath\": \"/api/health\"")
    }
}
