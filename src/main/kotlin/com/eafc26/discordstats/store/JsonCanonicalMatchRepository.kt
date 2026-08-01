package com.eafc26.discordstats.store

import com.eafc26.discordstats.application.repository.CanonicalMatchRepository
import com.eafc26.discordstats.application.repository.CanonicalRepositoryMetadata
import com.eafc26.discordstats.canonical.CanonicalMatch
import com.eafc26.discordstats.config.AppDataPaths
import com.eafc26.discordstats.domain.interpretation.AwardDecision
import com.eafc26.discordstats.domain.interpretation.AwardMetrics
import com.eafc26.discordstats.domain.interpretation.DecisionEvidence
import com.eafc26.discordstats.domain.interpretation.EligibilityInterpretation
import com.eafc26.discordstats.domain.interpretation.MatchFeatures
import com.eafc26.discordstats.domain.interpretation.MatchInterpretation
import com.eafc26.discordstats.domain.match.DefendingStats
import com.eafc26.discordstats.domain.match.MatchId
import com.eafc26.discordstats.domain.match.PassingStats
import com.eafc26.discordstats.domain.match.PlayerIdentity
import com.eafc26.discordstats.domain.match.PlayerRole
import com.eafc26.discordstats.domain.story.StoryContent
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
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
    private val mapper = canonicalMapper(sourceMapper)

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

        fun canonicalMapper(source: ObjectMapper): ObjectMapper = source.copy()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .addMixIn(PlayerRole::class.java, PlayerRoleMixin::class.java)
            .addMixIn(AwardMetrics::class.java, AwardMetricsMixin::class.java)
            .addMixIn(DecisionEvidence::class.java, DecisionEvidenceMixin::class.java)
            .addMixIn(StoryContent::class.java, StoryContentMixin::class.java)
            .addMixIn(CanonicalMatch::class.java, CanonicalMatchDerivedMixin::class.java)
            .addMixIn(MatchInterpretation::class.java, MatchInterpretationDerivedMixin::class.java)
            .addMixIn(EligibilityInterpretation::class.java, EligibilityDerivedMixin::class.java)
            .addMixIn(AwardDecision::class.java, AwardDecisionDerivedMixin::class.java)
            .addMixIn(MatchFeatures::class.java, MatchFeaturesDerivedMixin::class.java)
            .addMixIn(PlayerIdentity::class.java, PlayerIdentityDerivedMixin::class.java)
            .addMixIn(PassingStats::class.java, PassingStatsDerivedMixin::class.java)
            .addMixIn(DefendingStats::class.java, DefendingStatsDerivedMixin::class.java)
    }
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes(
    JsonSubTypes.Type(PlayerRole.Goalkeeper::class, name = "goalkeeper"),
    JsonSubTypes.Type(PlayerRole.Outfield::class, name = "outfield"),
    JsonSubTypes.Type(PlayerRole.Unknown::class, name = "unknown"),
)
private abstract class PlayerRoleMixin

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes(
    JsonSubTypes.Type(AwardMetrics.Craque::class, name = "craque"),
    JsonSubTypes.Type(AwardMetrics.Xerife::class, name = "xerife"),
)
private abstract class AwardMetricsMixin

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes(
    JsonSubTypes.Type(DecisionEvidence.PlayerPopulation::class, name = "player_population"),
    JsonSubTypes.Type(DecisionEvidence.Scoreboard::class, name = "scoreboard"),
    JsonSubTypes.Type(DecisionEvidence.ReportedResult::class, name = "reported_result"),
    JsonSubTypes.Type(DecisionEvidence.PlayingTime::class, name = "playing_time"),
    JsonSubTypes.Type(DecisionEvidence.AwardCandidate::class, name = "award_candidate"),
    JsonSubTypes.Type(DecisionEvidence.Rating::class, name = "rating"),
    JsonSubTypes.Type(DecisionEvidence.AttackingContribution::class, name = "attacking_contribution"),
    JsonSubTypes.Type(DecisionEvidence.PassingPerformance::class, name = "passing_performance"),
    JsonSubTypes.Type(DecisionEvidence.DefensivePerformance::class, name = "defensive_performance"),
    JsonSubTypes.Type(DecisionEvidence.Discipline::class, name = "discipline"),
    JsonSubTypes.Type(DecisionEvidence.EaRecognition::class, name = "ea_recognition"),
    JsonSubTypes.Type(DecisionEvidence.TeamPassingPerformance::class, name = "team_passing"),
    JsonSubTypes.Type(DecisionEvidence.GoalkeepingPerformance::class, name = "goalkeeping"),
)
private abstract class DecisionEvidenceMixin

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes(
    JsonSubTypes.Type(StoryContent.MatchResult::class, name = "match_result"),
    JsonSubTypes.Type(StoryContent.Award::class, name = "award"),
    JsonSubTypes.Type(StoryContent.Contributions::class, name = "contributions"),
    JsonSubTypes.Type(StoryContent.Highlights::class, name = "highlights"),
    JsonSubTypes.Type(StoryContent.EaRecognizedMvp::class, name = "ea_recognized_mvp"),
    JsonSubTypes.Type(StoryContent.BagrePerformance::class, name = "bagre_performance"),
    JsonSubTypes.Type(StoryContent.OffensiveNarrative::class, name = "offensive_narrative"),
    JsonSubTypes.Type(StoryContent.RedCard::class, name = "red_card"),
    JsonSubTypes.Type(StoryContent.PassPrecision::class, name = "pass_precision"),
    JsonSubTypes.Type(StoryContent.LostMail::class, name = "lost_mail"),
    JsonSubTypes.Type(StoryContent.Goalkeeper::class, name = "goalkeeper"),
)
private abstract class StoryContentMixin

@JsonIgnoreProperties("matchId")
private abstract class CanonicalMatchDerivedMixin

@JsonIgnoreProperties("matchId", "appliedRules", "evidence")
private abstract class MatchInterpretationDerivedMixin

@JsonIgnoreProperties("eligiblePlayerIds")
private abstract class EligibilityDerivedMixin

@JsonIgnoreProperties("awarded")
private abstract class AwardDecisionDerivedMixin

@JsonIgnoreProperties("rules", "evidence")
private abstract class MatchFeaturesDerivedMixin

@JsonIgnoreProperties("preferredDisplayName")
private abstract class PlayerIdentityDerivedMixin

@JsonIgnoreProperties("missed", "accuracy")
private abstract class PassingStatsDerivedMixin

@JsonIgnoreProperties("tackleAccuracy")
private abstract class DefendingStatsDerivedMixin
