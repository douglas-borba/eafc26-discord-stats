package com.eafc26.discordstats.store

import com.eafc26.discordstats.config.AppDataPaths
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * Persists the set of published match IDs as a JSON array in Application Support.
 *
 * Path: ~/Library/Application Support/EAFC26DiscordStats/published-matches.json
 *
 * - Missing file is treated as an empty store (first run).
 * - Malformed JSON is an explicit error — the caller decides how to handle it.
 * - Writes are atomic: content goes to a sibling .tmp file first, then
 *   renamed over the target. Falls back to non-atomic write on ATOMIC_MOVE failure.
 * - On first use the Application Support directory is created automatically.
 * - On startup, migrates the legacy ./data/published-matches.json if present.
 */
@Component
class PublishedMatchStore(private val objectMapper: ObjectMapper) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val storePath: Path get() = AppDataPaths.storeFile

    init {
        log.info("Published match store initialized at: {}", storePath.toAbsolutePath())
        migrateLegacyFile()
    }

    fun loadIds(): Set<String> {
        val path = storePath
        if (!path.exists()) {
            log.debug("Store file not found at {}, treating as empty", path)
            return emptySet()
        }
        val json = path.readText()
        return try {
            objectMapper.readValue<List<String>>(json).toSet()
        } catch (ex: JsonProcessingException) {
            throw IllegalStateException("Published match store at $path contains malformed JSON", ex)
        }
    }

    fun saveIds(ids: Set<String>) {
        val path = storePath
        Files.createDirectories(path.parent)
        val json = objectMapper.writeValueAsString(ids.sorted())
        val tmp = path.resolveSibling(path.fileName.toString() + ".tmp")
        tmp.toFile().writeText(json)
        try {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING)
        }
        log.debug("Saved {} published match IDs", ids.size)
    }

    private fun migrateLegacyFile() {
        val legacy = Path.of("data", "published-matches.json")
        val dest = storePath
        if (!legacy.exists() || dest.exists()) return
        try {
            Files.createDirectories(dest.parent)
            Files.copy(legacy, dest)
            log.info("Migrated published match store from legacy location to Application Support")
        } catch (ex: Exception) {
            log.warn("Could not migrate legacy match store: {}", ex.message)
        }
    }
}
