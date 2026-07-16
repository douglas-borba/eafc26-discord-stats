package com.eafc26.discordstats.store

import com.eafc26.discordstats.config.AppProperties
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
 * Persists the set of published match IDs as a JSON array in a local file.
 *
 * - Missing file is treated as an empty store (first run).
 * - Malformed JSON is an explicit error — the caller decides how to handle it.
 * - Writes are atomic: content goes to a sibling .tmp file first, then
 *   renamed over the target so a crash mid-write never leaves a partial file.
 */
@Component
class PublishedMatchStore(
    private val props: AppProperties,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val storePath: Path get() = Path.of(props.store.path)

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
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        log.debug("Saved {} published match IDs to {}", ids.size, path)
    }
}
