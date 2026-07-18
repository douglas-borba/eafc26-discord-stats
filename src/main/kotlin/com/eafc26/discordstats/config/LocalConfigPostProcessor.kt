package com.eafc26.discordstats.config

import org.springframework.boot.SpringApplication
import org.springframework.boot.env.EnvironmentPostProcessor
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.PropertiesPropertySource
import java.util.Properties
import java.util.prefs.Preferences

/**
 * Loads settings from Java Preferences API into the Spring Environment before
 * any beans are created, giving it the highest priority so it overrides application.yml.
 *
 * Also derives server.address from the network-enabled preference:
 *   false (default) → 127.0.0.1  (localhost only)
 *   true            → 0.0.0.0    (all interfaces)
 */
open class LocalConfigPostProcessor : EnvironmentPostProcessor {

    override fun postProcessEnvironment(environment: ConfigurableEnvironment, application: SpringApplication) {
        val prefs = Preferences.userNodeForPackage(SettingsService::class.java)

        // Derive server.address from network-enabled preference
        val networkEnabled = prefs.getBoolean("web.network-enabled", false)
        val derived = Properties()
        derived.setProperty("server.address", if (networkEnabled) "0.0.0.0" else "127.0.0.1")
        environment.propertySources.addFirst(
            PropertiesPropertySource("localPreferencesConfig", derived),
        )
    }
}
