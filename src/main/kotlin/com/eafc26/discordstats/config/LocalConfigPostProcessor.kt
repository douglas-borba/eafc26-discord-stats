package com.eafc26.discordstats.config

import org.springframework.boot.SpringApplication
import org.springframework.boot.env.EnvironmentPostProcessor
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.PropertiesPropertySource
import java.io.File
import java.io.FileInputStream
import java.nio.file.Path
import java.util.Properties

/**
 * Loads ~/Library/Application Support/EAFC26DiscordStats/config.properties into the
 * Spring Environment before any beans are created, giving it the highest priority so
 * it overrides application.yml.
 *
 * Also derives server.address from web.network-enabled:
 *   false (default) → 127.0.0.1  (localhost only)
 *   true            → 0.0.0.0    (all interfaces)
 */
open class LocalConfigPostProcessor : EnvironmentPostProcessor {

    open fun configFile(userHome: String): File =
        Path.of(userHome, "Library", "Application Support", "EAFC26DiscordStats", "config.properties").toFile()

    override fun postProcessEnvironment(environment: ConfigurableEnvironment, application: SpringApplication) {
        val configFile = configFile(System.getProperty("user.home"))

        val fileProps = Properties()
        if (configFile.exists()) {
            FileInputStream(configFile).use { fileProps.load(it) }
            environment.propertySources.addFirst(
                PropertiesPropertySource("localFileConfig", fileProps),
            )
        }

        // Derive server.address — done unconditionally so default is always 127.0.0.1
        val networkEnabled = fileProps.getProperty("web.network-enabled", "false")
            .trim().equals("true", ignoreCase = true)
        val derived = Properties()
        derived.setProperty("server.address", if (networkEnabled) "0.0.0.0" else "127.0.0.1")
        environment.propertySources.addFirst(
            PropertiesPropertySource("localFileConfigDerived", derived),
        )
    }
}
