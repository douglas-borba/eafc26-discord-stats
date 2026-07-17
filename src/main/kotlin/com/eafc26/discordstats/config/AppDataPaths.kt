package com.eafc26.discordstats.config

import java.nio.file.Path

object AppDataPaths {
    val appSupportDir: Path
        get() = Path.of(
            System.getProperty("user.home"),
            "Library", "Application Support", "EAFC26DiscordStats",
        )

    val storeFile: Path get() = appSupportDir.resolve("published-matches.json")
    val configFile: Path get() = appSupportDir.resolve("config.properties")
    val phrasesFile: Path get() = appSupportDir.resolve("phrases.json")
}
