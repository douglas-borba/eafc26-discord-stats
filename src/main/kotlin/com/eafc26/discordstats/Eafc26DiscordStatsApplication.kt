package com.eafc26.discordstats

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import com.eafc26.discordstats.config.AppProperties

@SpringBootApplication
@EnableConfigurationProperties(AppProperties::class)
class Eafc26DiscordStatsApplication

fun main(args: Array<String>) {
    runApplication<Eafc26DiscordStatsApplication>(*args)
}
