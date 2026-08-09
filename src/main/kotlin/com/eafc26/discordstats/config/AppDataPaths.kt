package com.eafc26.discordstats.config

import java.nio.file.Path
import com.eafc26.discordstats.domain.match.ClubId

object AppDataPaths {
    val appSupportDir: Path
        get() = Path.of(
            System.getProperty("user.home"),
            "Library", "Application Support", "EAFC26DiscordStats",
        )

    val storeFile: Path get() = appSupportDir.resolve("published-matches.json")
    fun publicationStoreFile(clubId: ClubId): Path =
        appSupportDir.resolve("clubs").resolve(clubId.value).resolve("published-matches.json")
    val lockFile: Path get() = appSupportDir.resolve("collector.lock")
    val configFile: Path get() = appSupportDir.resolve("config.properties")
    val phrasesFile: Path get() = appSupportDir.resolve("phrases.json")
    val canonicalMatchesDir: Path get() = appSupportDir.resolve("canonical-matches")
}
