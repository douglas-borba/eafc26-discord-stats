package com.eafc26.discordstats.security

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.security")
data class AccessSecurityProperties(
    val viewerPassword: String = "",
    val adminPassword: String = "",
)
