package com.eafc26.discordstats.store

import com.eafc26.discordstats.application.repository.CanonicalMatchRepository
import com.eafc26.discordstats.application.repository.CanonicalMatchOverview
import com.eafc26.discordstats.application.repository.CanonicalRepositoryMetadata
import com.eafc26.discordstats.application.club.DefaultClubProvider
import com.eafc26.discordstats.canonical.CanonicalMatch
import com.eafc26.discordstats.config.AppDataPaths
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.ClubName
import com.eafc26.discordstats.domain.match.CompetitionType
import com.eafc26.discordstats.domain.match.MatchCompletion
import com.eafc26.discordstats.domain.match.MatchCompletionStatus
import com.eafc26.discordstats.domain.match.MatchId
import com.eafc26.discordstats.domain.match.Score
import com.eafc26.discordstats.domain.interpretation.MatchOutcome
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
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
    override fun findByIds(clubId: ClubId, matchIds: Collection<MatchId>): List<CanonicalMatch> {
        migrateLegacyFilesIfNeeded(clubId)
        val root = rootFor(clubId)
        if (!root.exists() || matchIds.isEmpty()) return emptyList()
        return matchIds.distinct().mapNotNull { matchId ->
            pathFor(root, matchId).takeIf { it.exists() }?.let(::read)
        }
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
    override fun findLatestMatchId(clubId: ClubId): MatchId? = findAll(clubId).firstOrNull()?.matchId

    @Synchronized
    override fun findExistingMatchIds(clubId: ClubId, candidateMatchIds: Collection<MatchId>): Set<MatchId> {
        migrateLegacyFilesIfNeeded(clubId)
        val root = rootFor(clubId)
        if (!root.exists() || candidateMatchIds.isEmpty()) return emptySet()
        return candidateMatchIds.asSequence()
            .distinct()
            .filter { pathFor(root, it).exists() }
            .toCollection(linkedSetOf())
    }

    @Synchronized
    override fun findRecentMatchIds(clubId: ClubId, limit: Int): List<MatchId> {
        require(limit >= 0) { "limit must be non-negative" }
        migrateLegacyFilesIfNeeded(clubId)
        val root = rootFor(clubId)
        if (!root.exists()) return emptyList()
        return Files.list(root).use { paths ->
            paths
                .filter { it.isRegularFile() && it.extension == JSON_EXTENSION }
                .map(::readIdentity)
                .sorted(compareByDescending<CanonicalMatchIdentity> { it.playedAt }.thenBy { it.matchId.value })
                .limit(limit.toLong())
                .map { it.matchId }
                .toList()
        }
    }

    @Synchronized
    override fun findRecentOverview(clubId: ClubId, limit: Int): List<CanonicalMatchOverview> {
        require(limit >= 0) { "limit must be non-negative" }
        migrateLegacyFilesIfNeeded(clubId)
        val root = rootFor(clubId)
        if (!root.exists()) return emptyList()
        return Files.list(root).use { paths ->
            paths
                .filter { it.isRegularFile() && it.extension == JSON_EXTENSION }
                .map { readOverview(it, clubId) }
                .sorted(compareByDescending<CanonicalMatchOverview> { it.playedAt }.thenBy { it.matchId.value })
                .limit(limit.toLong())
                .toList()
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
    override fun findHistorySummaries(clubId: ClubId): List<CanonicalMatchOverview> {
        migrateLegacyFilesIfNeeded(clubId)
        val root = rootFor(clubId)
        if (!root.exists()) return emptyList()
        return Files.list(root).use { paths ->
            paths
                .filter { it.isRegularFile() && it.extension == JSON_EXTENSION }
                .map { readOverview(it, clubId) }
                .sorted(compareByDescending<CanonicalMatchOverview> { it.playedAt }.thenBy { it.matchId.value })
                .toList()
        }
    }

    @Synchronized
    override fun findRecent(clubId: ClubId, limit: Int): List<CanonicalMatch> {
        require(limit >= 0) { "limit must be non-negative" }
        return findAll(clubId).take(limit)
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

    private fun readIdentity(path: Path): CanonicalMatchIdentity = try {
        mapper.factory.createParser(path.toFile()).use { parser ->
            var matchId: MatchId? = null
            var playedAt: Instant? = null
            while (parser.nextToken() != null) {
                if (parser.currentName() != "footballMatch") continue
                parser.nextToken()
                while (parser.nextToken() != null && !parser.currentToken.isStructEnd) {
                    when (parser.currentName()) {
                        "id" -> matchId = MatchId(parser.nextTextValue())
                        "playedAt" -> playedAt = Instant.parse(parser.nextTextValue())
                        else -> {
                            parser.nextToken()
                            parser.skipChildren()
                        }
                    }
                }
                break
            }
            CanonicalMatchIdentity(
                matchId ?: error("Canonical match identity is missing footballMatch.id"),
                playedAt ?: error("Canonical match identity is missing footballMatch.playedAt"),
            )
        }
    } catch (ex: Exception) {
        throw IllegalStateException("Canonical match identity at $path cannot be read", ex)
    }

    private fun readOverview(path: Path, perspectiveClubId: ClubId): CanonicalMatchOverview = try {
        mapper.factory.createParser(path.toFile()).use { parser ->
            var football: OverviewFootballFacts? = null
            var result: OverviewResultFacts? = null
            parser.nextToken()
            while (parser.nextToken() != null && !parser.currentToken.isStructEnd) {
                when (parser.currentName()) {
                    "footballMatch" -> {
                        parser.nextToken()
                        football = readOverviewFootballFacts(parser)
                    }
                    "interpretation" -> {
                        parser.nextToken()
                        result = readOverviewResultFacts(parser)
                    }
                    else -> {
                        parser.nextToken()
                        parser.skipChildren()
                    }
                }
            }
            val match = requireNotNull(football) { "Canonical overview is missing footballMatch" }
            val interpretation = requireNotNull(result) { "Canonical overview is missing interpretation.result" }
            val ourClub = requireNotNull(match.clubs[interpretation.ourClubId]) {
                "Canonical overview is missing the interpreted perspective club"
            }
            val opponentClub = requireNotNull(match.clubs[interpretation.opponentClubId]) {
                "Canonical overview is missing the interpreted opponent club"
            }
            CanonicalMatchOverview(
                matchId = match.matchId,
                perspectiveClubId = perspectiveClubId,
                opponentClubId = interpretation.opponentClubId,
                playedAt = match.playedAt,
                competition = match.competition,
                ourClubName = ourClub.name,
                opponentClubName = opponentClub.name,
                ourScore = interpretation.ourScore,
                opponentScore = interpretation.opponentScore,
                outcome = interpretation.outcome,
                completion = match.completion,
            )
        }
    } catch (ex: Exception) {
        throw IllegalStateException("Canonical match overview at $path cannot be read", ex)
    }

    private fun readOverviewFootballFacts(parser: com.fasterxml.jackson.core.JsonParser): OverviewFootballFacts {
        var matchId: MatchId? = null
        var playedAt: Instant? = null
        var competition: CompetitionType? = null
        var completion = MatchCompletion.UNKNOWN
        val clubs = mutableMapOf<ClubId, OverviewClubFacts>()
        while (parser.nextToken() != null && !parser.currentToken.isStructEnd) {
            when (parser.currentName()) {
                "id" -> matchId = MatchId(parser.requiredScalar())
                "playedAt" -> playedAt = Instant.parse(parser.requiredScalar())
                "competition" -> competition = parser.optionalScalar()?.let(CompetitionType::valueOf)
                "completion" -> {
                    parser.nextToken()
                    completion = readOverviewCompletion(parser)
                }
                "participants" -> {
                    parser.nextToken()
                    while (parser.nextToken() != null && !parser.currentToken.isStructEnd) {
                        readOverviewClubFacts(parser).also { club -> clubs[club.id] = club }
                    }
                }
                else -> {
                    parser.nextToken()
                    parser.skipChildren()
                }
            }
        }
        return OverviewFootballFacts(
            matchId = requireNotNull(matchId) { "Canonical overview is missing footballMatch.id" },
            playedAt = requireNotNull(playedAt) { "Canonical overview is missing footballMatch.playedAt" },
            competition = competition,
            completion = completion,
            clubs = clubs,
        )
    }

    private fun readOverviewResultFacts(parser: com.fasterxml.jackson.core.JsonParser): OverviewResultFacts {
        var result: OverviewResultFacts? = null
        while (parser.nextToken() != null && !parser.currentToken.isStructEnd) {
            if (parser.currentName() == "result") {
                parser.nextToken()
                var ourClubId: ClubId? = null
                var opponentClubId: ClubId? = null
                var ourScore: Score? = null
                var opponentScore: Score? = null
                var outcome: MatchOutcome? = null
                while (parser.nextToken() != null && !parser.currentToken.isStructEnd) {
                    when (parser.currentName()) {
                        "ourClub" -> ourClubId = ClubId(parser.requiredScalar())
                        "opponentClub" -> opponentClubId = ClubId(parser.requiredScalar())
                        "ourScore" -> ourScore = Score(parser.requiredInt())
                        "opponentScore" -> opponentScore = Score(parser.requiredInt())
                        "outcome" -> outcome = MatchOutcome.valueOf(parser.requiredScalar())
                        else -> {
                            parser.nextToken()
                            parser.skipChildren()
                        }
                    }
                }
                result = OverviewResultFacts(
                    ourClubId = requireNotNull(ourClubId) { "Canonical overview is missing result.ourClub" },
                    opponentClubId = requireNotNull(opponentClubId) { "Canonical overview is missing result.opponentClub" },
                    ourScore = requireNotNull(ourScore) { "Canonical overview is missing result.ourScore" },
                    opponentScore = requireNotNull(opponentScore) { "Canonical overview is missing result.opponentScore" },
                    outcome = requireNotNull(outcome) { "Canonical overview is missing result.outcome" },
                )
                break
            }
            parser.nextToken()
            parser.skipChildren()
        }
        return requireNotNull(result) { "Canonical overview is missing interpretation.result" }
    }

    private fun readOverviewCompletion(parser: com.fasterxml.jackson.core.JsonParser): MatchCompletion {
        var status = MatchCompletionStatus.UNKNOWN
        var dnfClubId: ClubId? = null
        while (parser.nextToken() != null && !parser.currentToken.isStructEnd) {
            when (parser.currentName()) {
                "status" -> status = MatchCompletionStatus.valueOf(parser.requiredScalar())
                "dnfClubId" -> dnfClubId = parser.optionalScalar()?.let(::ClubId)
                else -> {
                    parser.nextToken()
                    parser.skipChildren()
                }
            }
        }
        return MatchCompletion(status, dnfClubId)
    }

    private fun readOverviewClubFacts(parser: com.fasterxml.jackson.core.JsonParser): OverviewClubFacts {
        var clubId: ClubId? = null
        var name: ClubName? = null
        while (parser.nextToken() != null && !parser.currentToken.isStructEnd) {
            when (parser.currentName()) {
                "club" -> {
                    parser.nextToken()
                    while (parser.nextToken() != null && !parser.currentToken.isStructEnd) {
                        when (parser.currentName()) {
                            "id" -> clubId = ClubId(parser.requiredScalar())
                            "name" -> name = parser.optionalScalar()?.let(::ClubName)
                            else -> {
                                parser.nextToken()
                                parser.skipChildren()
                            }
                        }
                    }
                }
                else -> {
                    parser.nextToken()
                    parser.skipChildren()
                }
            }
        }
        return OverviewClubFacts(
            id = requireNotNull(clubId) { "Canonical overview is missing participant club.id" },
            name = name,
        )
    }

    private fun com.fasterxml.jackson.core.JsonParser.requiredScalar(): String {
        nextToken()
        return requireNotNull(valueAsString) { "Canonical overview field must be a scalar value" }
    }

    private fun com.fasterxml.jackson.core.JsonParser.optionalScalar(): String? {
        nextToken()
        return valueAsString
    }

    private fun com.fasterxml.jackson.core.JsonParser.requiredInt(): Int {
        nextToken()
        return intValue
    }

    private fun pathFor(root: Path, matchId: MatchId): Path {
        val encoded = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(matchId.value.toByteArray(StandardCharsets.UTF_8))
        return root.resolve("$encoded.$JSON_EXTENSION")
    }

    private data class CanonicalMatchIdentity(val matchId: MatchId, val playedAt: Instant)

    private data class OverviewFootballFacts(
        val matchId: MatchId,
        val playedAt: Instant,
        val competition: CompetitionType?,
        val completion: MatchCompletion,
        val clubs: Map<ClubId, OverviewClubFacts>,
    )

    private data class OverviewResultFacts(
        val ourClubId: ClubId,
        val opponentClubId: ClubId,
        val ourScore: Score,
        val opponentScore: Score,
        val outcome: MatchOutcome,
    )

    private data class OverviewClubFacts(val id: ClubId, val name: ClubName?)

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
