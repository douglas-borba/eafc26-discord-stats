package com.eafc26.discordstats

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import com.eafc26.discordstats.config.AppProperties
import com.eafc26.discordstats.security.AccessSecurityProperties

@SpringBootApplication(exclude = [DataSourceAutoConfiguration::class, FlywayAutoConfiguration::class])
@EnableConfigurationProperties(AppProperties::class, AccessSecurityProperties::class)
class Eafc26DiscordStatsApplication

fun main(args: Array<String>) {
    runApplication<Eafc26DiscordStatsApplication>(*args)
}
