package com.eafc26.discordstats.store

import com.eafc26.discordstats.application.repository.CanonicalMatchRepository
import com.eafc26.discordstats.application.repository.CanonicalRepositoryMetadata
import com.eafc26.discordstats.application.club.DefaultClubProvider
import com.eafc26.discordstats.canonical.CanonicalMatch
import com.eafc26.discordstats.config.AppDataPaths
import com.eafc26.discordstats.domain.match.ClubId
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
    private val appSupportRoot: Path,
    private val legacyClubId: ClubId?,
) : CanonicalMatchRepository {

    @Autowired
    constructor(sourceMapper: ObjectMapper, defaultClubProvider: DefaultClubProvider) : this(
        sourceMapper,
        AppDataPaths.appSupportDir,
        defaultClubProvider.get().clubId,
    )

    private val log = LoggerFactory.getLogger(javaClass)
    private val mapper = CanonicalObjectMapperFactory.create(sourceMapper)
    private var legacyMigrationCompleted = false

    @Synchronized
    override fun save(match: CanonicalMatch) {
        require(match.schemaVersion == CanonicalMatch.CURRENT_SCHEMA_VERSION) {
            "Cannot write unsupported canonical schema ${match.schemaVersion.value}"
        }
        val clubId = match.interpretation.perspectiveClubId
        migrateLegacyFilesIfNeeded(clubId)
        val root = rootFor(clubId)
        Files.createDirectories(root)
        val target = pathFor(root, match.matchId)
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
    override fun findById(clubId: ClubId, matchId: MatchId): CanonicalMatch? {
        migrateLegacyFilesIfNeeded(clubId)
        val path = pathFor(rootFor(clubId), matchId)
        if (!path.exists()) return null
        return read(path)
    }

    @Synchronized
    override fun findMatchIds(clubId: ClubId): Set<MatchId> {
        migrateLegacyFilesIfNeeded(clubId)
        val root = rootFor(clubId)
        if (!root.exists()) return emptySet()
        return Files.list(root).use { paths ->
            paths
                .filter { it.isRegularFile() && it.extension == JSON_EXTENSION }
                .map { path ->
                    val encoded = path.fileName.toString().removeSuffix(".$JSON_EXTENSION")
                    MatchId(String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8))
                }
                .toList()
                .toCollection(linkedSetOf())
        }
    }

    @Synchronized
    override fun findAll(clubId: ClubId): List<CanonicalMatch> {
        migrateLegacyFilesIfNeeded(clubId)
        val root = rootFor(clubId)
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
    override fun metadata(clubId: ClubId): CanonicalRepositoryMetadata {
        val matches = findAll(clubId)
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

    private fun pathFor(root: Path, matchId: MatchId): Path {
        val encoded = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(matchId.value.toByteArray(StandardCharsets.UTF_8))
        return root.resolve("$encoded.$JSON_EXTENSION")
    }

    private fun rootFor(clubId: ClubId): Path = appSupportRoot
        .resolve("clubs")
        .resolve(safeClubSegment(clubId))
        .resolve("canonical-matches")

    private fun safeClubSegment(clubId: ClubId): String {
        require(clubId.value.matches(Regex("[A-Za-z0-9._-]+"))) { "ClubId cannot be used as a path segment" }
        return clubId.value
    }

    /** Copies verified legacy files into the default club namespace and keeps originals untouched. */
    private fun migrateLegacyFilesIfNeeded(clubId: ClubId) {
        if (clubId != legacyClubId || legacyMigrationCompleted) return
        val legacyRoot = appSupportRoot.resolve("canonical-matches")
        if (!legacyRoot.exists()) {
            legacyMigrationCompleted = true
            return
        }
        val targetRoot = rootFor(clubId)
        Files.createDirectories(targetRoot)
        Files.list(legacyRoot).use { paths ->
            paths.filter { it.isRegularFile() && it.extension == JSON_EXTENSION }.forEach { legacyPath ->
                val canonical = read(legacyPath)
                check(canonical.interpretation.perspectiveClubId == clubId) {
                    "Legacy canonical match ${canonical.matchId.value} belongs to another club"
                }
                val target = pathFor(targetRoot, canonical.matchId)
                if (target.exists()) {
                    val namespaced = read(target)
                    check(namespaced.matchId == canonical.matchId && namespaced.interpretation.perspectiveClubId == clubId) {
                        "Namespaced canonical match does not match legacy identity ${canonical.matchId.value}"
                    }
                } else {
                    val temporary = target.resolveSibling("${target.fileName}.migration.tmp")
                    Files.copy(legacyPath, temporary, StandardCopyOption.REPLACE_EXISTING)
                    try {
                        Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE)
                    } catch (_: Exception) {
                        Files.move(temporary, target)
                    }
                    check(read(target) == canonical) { "Copied canonical match could not be validated" }
                }
            }
        }
        legacyMigrationCompleted = true
    }

    private companion object {
        const val JSON_EXTENSION = "json"
    }
}
