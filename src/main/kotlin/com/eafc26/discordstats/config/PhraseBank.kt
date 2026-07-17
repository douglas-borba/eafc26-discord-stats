package com.eafc26.discordstats.config

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * Loads and saves custom phrases from
 * ~/Library/Application Support/EAFC26DiscordStats/phrases.json.
 *
 * The file is a JSON object keyed by [PhraseCategory.key].
 * Missing file or missing category key → category defaults used.
 */
@Component
class PhraseBank(private val objectMapper: ObjectMapper) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Volatile
    private var phrases: Map<String, List<String>> = emptyMap()

    init {
        reload()
    }

    fun get(category: PhraseCategory): List<String> {
        val custom = phrases[category.key]
        return if (!custom.isNullOrEmpty()) custom else category.defaults
    }

    fun getAll(): Map<String, List<String>> =
        PhraseCategory.entries.associate { it.key to get(it) }

    fun saveAll(incoming: Map<String, List<String>>) {
        val sanitized = incoming.mapValues { (_, lines) ->
            lines.map { it.trim() }.filter { it.isNotEmpty() }
        }
        val path = AppDataPaths.phrasesFile
        Files.createDirectories(path.parent)
        val json = objectMapper.writeValueAsString(sanitized)
        val tmp = path.resolveSibling(path.fileName.toString() + ".tmp")
        tmp.toFile().writeText(json)
        try {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING)
        }
        phrases = sanitized
        log.debug("Saved custom phrases for {} categories", sanitized.size)
    }

    private fun reload() {
        val path = AppDataPaths.phrasesFile
        if (!path.exists()) return
        try {
            phrases = objectMapper.readValue<Map<String, List<String>>>(path.readText())
        } catch (ex: JsonProcessingException) {
            log.warn("phrases.json is malformed, using defaults: {}", ex.message)
        }
    }
}
