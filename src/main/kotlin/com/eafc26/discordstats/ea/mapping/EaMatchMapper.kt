package com.eafc26.discordstats.ea.mapping

import com.eafc26.discordstats.domain.match.AttackingStats
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.ClubIdentity
import com.eafc26.discordstats.domain.match.ClubMatchPerformance
import com.eafc26.discordstats.domain.match.ClubName
import com.eafc26.discordstats.domain.match.CompetitionType
import com.eafc26.discordstats.domain.match.DefendingStats
import com.eafc26.discordstats.domain.match.DisciplineStats
import com.eafc26.discordstats.domain.match.DisplayName
import com.eafc26.discordstats.domain.match.EaRecognition
import com.eafc26.discordstats.domain.match.FootballMatch
import com.eafc26.discordstats.domain.match.GoalkeepingStats
import com.eafc26.discordstats.domain.match.MatchId
import com.eafc26.discordstats.domain.match.MatchCompletion
import com.eafc26.discordstats.domain.match.MatchRating
import com.eafc26.discordstats.domain.match.Participation
import com.eafc26.discordstats.domain.match.ParticipationStatus
import com.eafc26.discordstats.domain.match.PassingStats
import com.eafc26.discordstats.domain.match.PlayerId
import com.eafc26.discordstats.domain.match.PlayerIdentity
import com.eafc26.discordstats.domain.match.PlayerMatchPerformance
import com.eafc26.discordstats.domain.match.PlayerRole
import com.eafc26.discordstats.domain.match.ReportedMatchResult
import com.eafc26.discordstats.domain.match.SaveBreakdown
import com.eafc26.discordstats.domain.match.Score
import com.eafc26.discordstats.ea.model.ClubMatchEntry
import com.eafc26.discordstats.ea.model.MatchResponse
import com.eafc26.discordstats.ea.model.PlayerEntry
import com.eafc26.discordstats.ea.normalizeEaText
import java.time.Duration
import java.time.Instant

/**
 * Anti-corruption layer between EA transport DTOs and the normalized match domain.
 *
 * This mapper is intentionally not a Spring component in Phase 2. No production
 * flow uses it yet.
 */
class EaMatchMapper {

    fun map(
        source: MatchResponse,
        proNames: Map<String, String> = emptyMap(),
    ): MatchNormalizationResult {
        val errors = validateRequiredMatchFacts(source)
        if (errors.isNotEmpty()) return MatchNormalizationResult.Rejected(errors)

        val warnings = mutableListOf<NormalizationWarning>()
        val parser = EaStatParser(warnings)

        val scoreByClub = resolveScores(source, parser, warnings)
        val completion = resolveCompletion(source, warnings)
        val participants = source.clubs.entries.map { (clubId, clubEntry) ->
            mapClub(
                source = source,
                clubId = clubId,
                clubEntry = clubEntry,
                score = scoreByClub.getValue(clubId),
                proNames = proNames,
                parser = parser,
                warnings = warnings,
            )
        }

        source.players.keys
            .filter { it !in source.clubs }
            .forEach { unknownClubId ->
                warnings += NormalizationWarning(
                    code = NormalizationIssueCode.UNKNOWN_PLAYER_CLUB,
                    path = "players[$unknownClubId]",
                    message = "Players for a club absent from match.clubs were ignored",
                    rawValue = unknownClubId,
                )
            }

        return MatchNormalizationResult.Success(
            match = FootballMatch(
                id = MatchId(source.matchId),
                playedAt = Instant.ofEpochSecond(source.timestamp),
                competition = mapCompetition(source.matchType, warnings),
                participants = participants,
                completion = completion,
            ),
            warnings = warnings.toList(),
        )
    }

    private fun resolveCompletion(source: MatchResponse, warnings: MutableList<NormalizationWarning>): MatchCompletion {
        if (source.clubs.size != 2) return MatchCompletion.UNKNOWN
        val flags = source.clubs.mapValues { it.value.winnerByDnf }
        if (flags.values.any { it == null }) return MatchCompletion.UNKNOWN
        if (flags.values.any { it != "0" && it != "1" }) {
            warnings += NormalizationWarning(NormalizationIssueCode.INVALID_MATCH_COMPLETION, "clubs.winnerByDnf", "Invalid winnerByDnf value", flags.values.joinToString())
            return MatchCompletion.UNKNOWN
        }
        val winners = flags.filterValues { it == "1" }.keys
        return when (winners.size) {
            0 -> MatchCompletion.COMPLETED
            1 -> MatchCompletion.dnf(ClubId(source.clubs.keys.first { it != winners.single() }))
            else -> {
                warnings += NormalizationWarning(NormalizationIssueCode.INVALID_MATCH_COMPLETION, "clubs.winnerByDnf", "Multiple DNF winners", winners.joinToString())
                MatchCompletion.UNKNOWN
            }
        }
    }

    private fun validateRequiredMatchFacts(source: MatchResponse): List<NormalizationError> {
        val errors = mutableListOf<NormalizationError>()
        if (source.matchId.isBlank()) {
            errors += NormalizationError(
                code = NormalizationIssueCode.BLANK_MATCH_ID,
                path = "matchId",
                message = "A normalized match requires a non-blank match ID",
                rawValue = source.matchId,
            )
        }
        if (runCatching { Instant.ofEpochSecond(source.timestamp) }.isFailure) {
            errors += NormalizationError(
                code = NormalizationIssueCode.INVALID_TIMESTAMP,
                path = "timestamp",
                message = "Match timestamp is outside the supported Instant range",
                rawValue = source.timestamp.toString(),
            )
        }
        if (source.clubs.size < 2) {
            errors += NormalizationError(
                code = NormalizationIssueCode.INSUFFICIENT_CLUBS,
                path = "clubs",
                message = "A normalized football match requires at least two clubs",
                rawValue = source.clubs.size.toString(),
            )
        }
        source.clubs.keys.filter { it.isBlank() }.forEach {
            errors += NormalizationError(
                code = NormalizationIssueCode.BLANK_CLUB_ID,
                path = "clubs",
                message = "A normalized club requires a non-blank map key",
                rawValue = it,
            )
        }
        return errors
    }

    private fun resolveScores(
        source: MatchResponse,
        parser: EaStatParser,
        warnings: MutableList<NormalizationWarning>,
    ): Map<String, Int> {
        val directScores = source.clubs.mapValues { (clubId, entry) ->
            parser.nonNegativeInt(entry.score, "clubs[$clubId].score")
        }
        val goalsAgainst = source.clubs.mapValues { (clubId, entry) ->
            parser.nonNegativeInt(entry.goalsAgainst, "clubs[$clubId].goalsAgainst")
        }

        val resolved = source.clubs.keys.associateWith { clubId ->
            val direct = directScores[clubId]
            if (direct != null) return@associateWith direct

            val fallback = if (source.clubs.size == 2) {
                val opponentId = source.clubs.keys.first { it != clubId }
                goalsAgainst[opponentId]
            } else {
                null
            }

            if (fallback != null) {
                warnings += NormalizationWarning(
                    code = NormalizationIssueCode.SCORE_FALLBACK_TO_GOALS_AGAINST,
                    path = "clubs[$clubId].score",
                    message = "Missing or invalid score recovered from the opponent's goalsAgainst",
                    rawValue = fallback.toString(),
                )
                fallback
            } else {
                warnings += NormalizationWarning(
                    code = NormalizationIssueCode.SCORE_FALLBACK_TO_ZERO,
                    path = "clubs[$clubId].score",
                    message = "Missing or invalid score defaulted to zero",
                    rawValue = source.clubs.getValue(clubId).score,
                )
                0
            }
        }

        if (source.clubs.size == 2) {
            source.clubs.keys.forEach { clubId ->
                val opponentId = source.clubs.keys.first { it != clubId }
                val reportedAgainst = goalsAgainst[clubId]
                val opponentScore = resolved.getValue(opponentId)
                if (reportedAgainst != null && reportedAgainst != opponentScore) {
                    warnings += NormalizationWarning(
                        code = NormalizationIssueCode.SCORE_GOALS_AGAINST_CONFLICT,
                        path = "clubs[$clubId].goalsAgainst",
                        message = "goalsAgainst conflicts with the opponent's resolved score; score remains authoritative",
                        rawValue = reportedAgainst.toString(),
                    )
                }
            }
        }
        return resolved
    }

    private fun mapClub(
        source: MatchResponse,
        clubId: String,
        clubEntry: ClubMatchEntry,
        score: Int,
        proNames: Map<String, String>,
        parser: EaStatParser,
        warnings: MutableList<NormalizationWarning>,
    ): ClubMatchPerformance {
        val resolvedName = clubEntry.resolvedName()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        if (resolvedName == null) {
            warnings += NormalizationWarning(
                code = NormalizationIssueCode.MISSING_CLUB_NAME,
                path = "clubs[$clubId].name",
                message = "Club name is absent; identity is preserved by ClubId",
            )
        }

        val players = (source.players[clubId] ?: emptyMap()).entries.mapIndexed { index, (playerId, entry) ->
            mapPlayer(
                clubId = clubId,
                sourcePlayerId = playerId,
                fallbackIndex = index,
                source = entry,
                proNames = proNames,
                parser = parser,
                warnings = warnings,
            )
        }

        return ClubMatchPerformance(
            club = ClubIdentity(
                id = ClubId(clubId),
                name = resolvedName?.let(::ClubName),
            ),
            score = Score(score),
            reportedResult = mapReportedResult(clubEntry.result, clubId, warnings),
            players = players,
        )
    }

    private fun mapPlayer(
        clubId: String,
        sourcePlayerId: String,
        fallbackIndex: Int,
        source: PlayerEntry,
        proNames: Map<String, String>,
        parser: EaStatParser,
        warnings: MutableList<NormalizationWarning>,
    ): PlayerMatchPerformance {
        val path = "players[$clubId][$sourcePlayerId]"
        val playerId = sourcePlayerId.takeIf { it.isNotBlank() } ?: run {
            val fallback = "anonymous-player-${fallbackIndex + 1}"
            warnings += NormalizationWarning(
                code = NormalizationIssueCode.BLANK_PLAYER_ID,
                path = path,
                message = "Blank player map key replaced with a deterministic match-local ID",
                rawValue = fallback,
            )
            fallback
        }

        val platformName = normalizedDisplayName(source.playerName)
        val proName = findProName(source.playerName, proNames)
        if (platformName == null && proName == null) {
            warnings += NormalizationWarning(
                code = NormalizationIssueCode.MISSING_PLAYER_NAME,
                path = "$path.playername",
                message = "Player has no display name; PlayerId remains the canonical identity",
                rawValue = source.playerName,
            )
        }

        val passAttempts = parser.nonNegativeInt(source.passAttempts, "$path.passattempts")
        val passesMade = parser.nonNegativeInt(source.passesMade, "$path.passesmade")
        val normalizedPassing = parser.completedAttempts(passAttempts, passesMade, "$path.passing")

        val tackleAttempts = parser.nonNegativeInt(source.tackleAttempts, "$path.tackleattempts")
        val tacklesMade = parser.nonNegativeInt(source.tacklesMade, "$path.tacklesmade")
        val normalizedDefending = parser.completedAttempts(tackleAttempts, tacklesMade, "$path.defending")

        val role = if (source.isGoalkeeper()) PlayerRole.Goalkeeper else PlayerRole.Outfield(position = null)

        return PlayerMatchPerformance(
            player = PlayerIdentity(
                id = PlayerId(playerId),
                platformName = platformName,
                proName = proName,
            ),
            role = role,
            participation = Participation(
                duration = parser.nonNegativeInt(source.secondsPlayed, "$path.secondsplayed")
                    ?.let { Duration.ofSeconds(it.toLong()) },
                status = mapParticipationStatus(source.status),
            ),
            rating = parser.nonNegativeDecimal(source.rating, "$path.rating")?.let(::MatchRating),
            attacking = AttackingStats(
                goals = parser.nonNegativeInt(source.goals, "$path.goals"),
                assists = parser.nonNegativeInt(source.assists, "$path.assists"),
                shots = parser.nonNegativeInt(source.shots, "$path.shots"),
            ),
            passing = PassingStats(
                attempted = normalizedPassing.first,
                completed = normalizedPassing.second,
            ),
            defending = DefendingStats(
                tacklesAttempted = normalizedDefending.first,
                tacklesCompleted = normalizedDefending.second,
            ),
            discipline = DisciplineStats(
                redCards = parser.nonNegativeInt(source.redCards, "$path.redcards"),
            ),
            goalkeeping = if (role == PlayerRole.Goalkeeper) mapGoalkeeping(source, path, parser) else null,
            eaRecognition = EaRecognition(
                manOfTheMatch = parser.booleanFlag(source.manOfTheMatch, "$path.mom"),
            ),
        )
    }

    private fun mapGoalkeeping(
        source: PlayerEntry,
        path: String,
        parser: EaStatParser,
    ): GoalkeepingStats = GoalkeepingStats(
        saves = parser.nonNegativeInt(source.saves, "$path.saves"),
        goalsConceded = parser.nonNegativeInt(source.goalsConceded, "$path.goalsconceded"),
        cleanSheetAsGoalkeeper = parser.booleanFlag(source.cleanSheetsGk, "$path.cleansheetsgk"),
        cleanSheetAsAny = parser.booleanFlag(source.cleanSheetsAny, "$path.cleansheetsany"),
        saveBreakdown = SaveBreakdown(
            goodDirection = parser.nonNegativeInt(source.goodDirectionSaves, "$path.goodDirectionSaves"),
            reflex = parser.nonNegativeInt(source.reflexSaves, "$path.reflexSaves"),
            parry = parser.nonNegativeInt(source.parrySaves, "$path.parrySaves"),
            punch = parser.nonNegativeInt(source.punchSaves, "$path.punchSaves"),
            diving = parser.nonNegativeInt(source.ballDiveSaves, "$path.ballDiveSaves"),
            crosses = parser.nonNegativeInt(source.crossSaves, "$path.crossSaves"),
        ),
    )

    private fun mapCompetition(
        raw: String?,
        warnings: MutableList<NormalizationWarning>,
    ): CompetitionType? {
        if (raw == null) return null
        return when (raw.trim().lowercase()) {
            "friendlymatch" -> CompetitionType.FRIENDLY
            "leaguematch" -> CompetitionType.LEAGUE
            "playoffmatch" -> CompetitionType.PLAYOFF
            else -> {
                warnings += NormalizationWarning(
                    code = NormalizationIssueCode.INVALID_COMPETITION_TYPE,
                    path = "matchType",
                    message = "Unknown match type treated as absent",
                    rawValue = raw,
                )
                null
            }
        }
    }

    private fun mapReportedResult(
        raw: String?,
        clubId: String,
        warnings: MutableList<NormalizationWarning>,
    ): ReportedMatchResult? {
        if (raw == null) return null
        return when (raw.trim()) {
            "0" -> ReportedMatchResult.LOSS
            "1" -> ReportedMatchResult.WIN
            "2" -> ReportedMatchResult.DRAW
            else -> {
                warnings += NormalizationWarning(
                    code = NormalizationIssueCode.INVALID_REPORTED_RESULT,
                    path = "clubs[$clubId].result",
                    message = "Unknown reported result treated as absent",
                    rawValue = raw,
                )
                null
            }
        }
    }

    private fun mapParticipationStatus(raw: String?): ParticipationStatus? {
        if (raw == null) return null
        return when (raw.trim().lowercase()) {
            "completed", "complete" -> ParticipationStatus.COMPLETED
            "disconnected", "disconnect" -> ParticipationStatus.DISCONNECTED
            "replaced", "substituted" -> ParticipationStatus.REPLACED
            else -> ParticipationStatus.UNKNOWN
        }
    }

    private fun findProName(
        platformName: String?,
        proNames: Map<String, String>,
    ): DisplayName? {
        val key = platformName?.trim()?.lowercase() ?: return null
        val raw = proNames[platformName]
            ?: proNames.entries.firstOrNull { it.key.trim().lowercase() == key }?.value
        return normalizedDisplayName(raw)
    }

    private fun normalizedDisplayName(raw: String?): DisplayName? =
        raw
            ?.let(::normalizeEaText)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let(::DisplayName)
}
