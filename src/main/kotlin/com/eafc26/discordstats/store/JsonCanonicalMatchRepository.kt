package com.eafc26.discordstats.store

import com.eafc26.discordstats.application.repository.CanonicalMatchRepository
import com.eafc26.discordstats.application.repository.CanonicalRepositoryMetadata
import com.eafc26.discordstats.canonical.CanonicalMatch
import com.eafc26.discordstats.config.AppDataPaths
import com.eafc26.discordstats.domain.match.MatchId
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Base64
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile

/**
 * One atomically-written JSON document per canonical match.
 */
@Component
class JsonCanonicalMatchRepository(
    private val sourceMapper: ObjectMapper,
    private val root: Path,
) : CanonicalMatchRepository {

    @Autowired
    constructor(sourceMapper: ObjectMapper) : this(sourceMapper, AppDataPaths.canonicalMatchesDir)

    private val log = LoggerFactory.getLogger(javaClass)
    private val mapper = CanonicalObjectMapperFactory.create(sourceMapper)

    @Synchronized
    override fun save(match: CanonicalMatch) {
        require(match.schemaVersion == CanonicalMatch.CURRENT_SCHEMA_VERSION) {
            "Cannot write unsupported canonical schema ${match.schemaVersion.value}"
        }
        Files.createDirectories(root)
        val target = pathFor(match.matchId)
        val temporary = target.resolveSibling("${target.fileName}.tmp")
        mapper.writeValue(temporary.toFile(), match)
        try {
            Files.move(
                temporary,
                target,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: Exception) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
        }
        log.debug("Stored canonical match {} using schema {}", match.matchId.value, match.schemaVersion.value)
    }

    @Synchronized
    override fun findById(matchId: MatchId): CanonicalMatch? {
        val path = pathFor(matchId)
        if (!path.exists()) return null
        return read(path)
    }

    @Synchronized
    override fun findAll(): List<CanonicalMatch> {
        if (!root.exists()) return emptyList()
        return Files.list(root).use { paths ->
            paths
                .filter { it.isRegularFile() && it.extension == JSON_EXTENSION }
                .map(::read)
                .sorted(
                    compareByDescending<CanonicalMatch> { it.footballMatch.playedAt }
                        .thenBy { it.matchId.value }
                )
                .toList()
        }
    }

    @Synchronized
    override fun metadata(): CanonicalRepositoryMetadata {
        val matches = findAll()
        return CanonicalRepositoryMetadata(
            matchCount = matches.size,
            oldestMatchAt = matches.minOfOrNull { it.footballMatch.playedAt },
            newestMatchAt = matches.maxOfOrNull { it.footballMatch.playedAt },
            lastGeneratedAt = matches.maxOfOrNull { it.generatedAt },
            schemaVersions = matches.mapTo(linkedSetOf()) { it.schemaVersion },
            engineVersions = matches.mapTo(linkedSetOf()) { it.engineVersion },
        )
    }

    private fun read(path: Path): CanonicalMatch = try {
        mapper.readValue(path.toFile(), CanonicalMatch::class.java).also {
            check(it.schemaVersion == CanonicalMatch.CURRENT_SCHEMA_VERSION) {
                "Canonical match ${it.matchId.value} uses unsupported schema ${it.schemaVersion.value}"
            }
        }
    } catch (ex: Exception) {
        throw IllegalStateException("Canonical match file at $path cannot be read", ex)
    }

    private fun pathFor(matchId: MatchId): Path {
        val encoded = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(matchId.value.toByteArray(StandardCharsets.UTF_8))
        return root.resolve("$encoded.$JSON_EXTENSION")
    }

    private companion object {
        const val JSON_EXTENSION = "json"
    }
}
