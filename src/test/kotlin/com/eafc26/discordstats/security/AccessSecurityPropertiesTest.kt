package com.eafc26.discordstats.security

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

class AccessSecurityPropertiesTest {
    private val config = SecurityConfig()

    @Test
    fun `startup rejects missing viewer password`() {
        assertThatThrownBy { config.users(AccessSecurityProperties(adminPassword = "admin-secret"), BCryptPasswordEncoder()) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("EAFC_VIEWER_PASSWORD")
    }

    @Test
    fun `startup rejects missing admin password`() {
        assertThatThrownBy { config.users(AccessSecurityProperties(viewerPassword = "viewer-secret"), BCryptPasswordEncoder()) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("EAFC_ADMIN_PASSWORD")
    }

    @Test
    fun `roles cannot share a password`() {
        assertThatThrownBy { config.users(AccessSecurityProperties("same-secret", "same-secret"), BCryptPasswordEncoder()) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("must be different")
    }
}
