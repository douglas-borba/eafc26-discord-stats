package com.eafc26.discordstats.service

import com.eafc26.discordstats.canonical.CanonicalMatch
import com.eafc26.discordstats.domain.interpretation.AwardType
import com.eafc26.discordstats.domain.interpretation.MatchOutcome
import com.eafc26.discordstats.domain.match.PlayerId
import com.eafc26.discordstats.domain.match.ClubId
import com.eafc26.discordstats.domain.match.PlayerMatchPerformance
import com.eafc26.discordstats.domain.match.AdvancedStatsCoverage
import com.eafc26.discordstats.application.repository.PlayerProfileReadRepository
import com.eafc26.discordstats.history.MatchHistoryQuery
import com.eafc26.discordstats.diagnostics.CanonicalReadOrigin
import com.eafc26.discordstats.diagnostics.CanonicalReadOriginContext
import com.eafc26.discordstats.profile.PlayerProfile
import com.eafc26.discordstats.profile.PlayerProfileAppearance
import com.eafc26.discordstats.profile.PlayerProfileIndexEntry
import com.eafc26.discordstats.profile.PlayerProfileMatch
import com.eafc26.discordstats.profile.PlayerXRay
import com.eafc26.discordstats.profile.PlayerCurrentForm
import com.eafc26.discordstats.profile.PlayerCurrentFormState
import com.eafc26.discordstats.profile.PlayerFormPeriod
import com.eafc26.discordstats.profile.PlayerFormDifferences
import com.eafc26.discordstats.profile.PlayerAttackMetrics
import com.eafc26.discordstats.profile.PlayerCreationMetrics
import com.eafc26.discordstats.profile.PlayerDefenseMetrics
import com.eafc26.discordstats.profile.PlayerAdvancedCoverage
import com.eafc26.discordstats.profile.PlayerOneOnOneMetrics
import com.eafc26.discordstats.profile.PlayerRecognitions
import com.eafc26.discordstats.profile.PlayerPersonalRecords
import com.eafc26.discordstats.profile.PlayerSingleMatchRecord
import com.eafc26.discordstats.profile.PlayerAnalysis
import com.eafc26.discordstats.profile.PlayerAnalysisEvidence
import com.eafc26.discordstats.profile.PlayerConsistency
import com.eafc26.discordstats.profile.PlayerConsistencyState
import com.eafc26.discordstats.profile.PlayerEvidenceUnit
import com.eafc26.discordstats.profile.PlayerImprovementArea
import com.eafc26.discordstats.profile.PlayerImprovementAssessment
import com.eafc26.discordstats.profile.PlayerImprovementAssessmentState
import com.eafc26.discordstats.profile.PlayerImprovementOpportunity
import com.eafc26.discordstats.profile.PlayerImprovementSource
import com.eafc26.discordstats.profile.PlayerStrength
import com.eafc26.discordstats.profile.PlayerStrengthCategory
import com.eafc26.discordstats.profile.PlayerTrend
import com.eafc26.discordstats.profile.PlayerTrendMetric
import com.eafc26.discordstats.profile.PlayerTrendMetricType
import com.eafc26.discordstats.profile.PlayerTrendStatus
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.sqrt

@Service
class PlayerProfileService(
    private val matchHistoryService: MatchHistoryService,
    private val readOriginContext: CanonicalReadOriginContext = CanonicalReadOriginContext(),
    private val playerProfileReadRepository: PlayerProfileReadRepository? = null,
) {
    fun listPlayers(clubId: ClubId): List<PlayerProfileIndexEntry> = readOriginContext.withOrigin(CanonicalReadOrigin.PLAYERS) {
        playerProfileReadRepository?.findPlayerIndex(clubId) ?: playerIndex(loadAppearances(clubId))
    }

    /** Legacy aggregate collection retained for internal callers. The public
     * selector uses [listPlayers] and never builds every X-Ray. */
    fun listProfiles(
        clubId: ClubId,
        recentMatchLimit: Int = DEFAULT_RECENT_MATCH_LIMIT,
    ): List<PlayerProfile> = readOriginContext.withOrigin(CanonicalReadOrigin.PLAYERS) {
        require(recentMatchLimit > 0) { "Recent match limit must be positive" }

        val appearances = loadAppearances(clubId)
        playerIndex(appearances).mapNotNull { entry ->
            profileFrom(appearances, entry.playerId, recentMatchLimit)
        }
    }

    fun findById(
        clubId: ClubId,
        playerId: PlayerId,
        recentMatchLimit: Int = DEFAULT_RECENT_MATCH_LIMIT,
    ): PlayerProfile? = readOriginContext.withOrigin(CanonicalReadOrigin.PLAYERS) {
        require(recentMatchLimit > 0) { "Recent match limit must be positive" }

        profileFrom(loadAppearances(clubId, playerId), playerId, recentMatchLimit)
    }

    private fun loadAppearances(clubId: ClubId): List<PlayerProfileAppearance> =
        playerProfileReadRepository?.findAppearances(clubId)
            ?: canonicalAppearances(matchHistoryService.list(clubId))

    private fun loadAppearances(clubId: ClubId, playerId: PlayerId): List<PlayerProfileAppearance> =
        playerProfileReadRepository?.findAppearances(clubId, playerId)
            ?: canonicalAppearances(matchHistoryService.list(clubId, MatchHistoryQuery(playerId = playerId)))

    private fun playerIndex(appearances: List<PlayerProfileAppearance>): List<PlayerProfileIndexEntry> {
        val accumulated = linkedMapOf<PlayerId, MutablePlayerIndex>()

        appearances.forEach { appearance ->
            if (!appearance.completion.hasCompleteSportingStatistics) return@forEach
            val current = accumulated.getOrPut(appearance.playerId) {
                MutablePlayerIndex(
                    displayName = appearance.preferredDisplayName,
                    matchCount = 0,
                    latestMatchAt = appearance.playedAt,
                )
            }
            current.matchCount += 1
            appearance.rating?.let {
                current.ratingTotal += it
                current.ratedMatchCount += 1
            }
        }

        return accumulated.map { (playerId, value) ->
            PlayerProfileIndexEntry(
                playerId = playerId,
                displayName = value.displayName,
                matchCount = value.matchCount,
                latestMatchAt = value.latestMatchAt,
                averageRating = value.ratingTotal.takeIf { value.ratedMatchCount > 0 }
                    ?.divide(value.ratedMatchCount.toBigDecimal(), 2, RoundingMode.HALF_UP),
                ratedMatchCount = value.ratedMatchCount,
            )
        }.sortedWith(
            compareByDescending<PlayerProfileIndexEntry> { it.latestMatchAt }
                .thenBy { it.displayName.lowercase() }
                .thenBy { it.playerId.value }
        )
    }

    private fun profileFrom(
        allAppearances: List<PlayerProfileAppearance>,
        playerId: PlayerId,
        recentMatchLimit: Int,
    ): PlayerProfile? {
        val appearances = allAppearances
            .asSequence()
            .filter { it.playerId == playerId }
            .filter { it.completion.hasCompleteSportingStatistics }
            .sortedWith(compareByDescending<PlayerProfileAppearance> { it.playedAt }.thenBy { it.matchId.value })
            .toList()

        if (appearances.isEmpty()) return null

        val ratings = appearances.mapNotNull { it.rating }
        val latestAppearance = appearances.first()

        val profile = PlayerProfile(
            playerId = playerId,
            displayName = latestAppearance.preferredDisplayName,
            matchCount = appearances.size,
            wins = appearances.count { it.outcome == MatchOutcome.WIN },
            draws = appearances.count { it.outcome == MatchOutcome.DRAW },
            losses = appearances.count { it.outcome == MatchOutcome.LOSS },
            averageRating = ratings.averageOrNull(),
            ratedMatchCount = ratings.size,
            goals = appearances.sumOf { it.goals ?: 0 },
            assists = appearances.sumOf { it.assists ?: 0 },
            craques = appearances.count { AwardType.CRAQUE in it.awards },
            bagres = appearances.count { AwardType.BAGRE in it.awards },
            xerifes = appearances.count { AwardType.XERIFE in it.awards },
            redCards = appearances.sumOf { it.redCards ?: 0 },
            shots = appearances.sumOf { it.shots ?: 0 },
            passesCompleted = appearances.sumOf { it.passesCompleted ?: 0 },
            passesAttempted = appearances.sumOf { it.passesAttempted ?: 0 },
            tacklesCompleted = appearances.sumOf { it.tacklesCompleted ?: 0 },
            tacklesAttempted = appearances.sumOf { it.tacklesAttempted ?: 0 },
            recentMatches = appearances.take(recentMatchLimit).map { it.toProfileMatch() },
        )
        return profile.copy(xRay = buildXRay(profile, appearances))
    }

    private fun canonicalAppearances(history: List<CanonicalMatch>): List<PlayerProfileAppearance> =
        history.flatMap { canonical ->
            val result = canonical.interpretation.result
            val participants = canonical.footballMatch.participants.associateBy { it.club.id }
            val ourClub = participants[result.ourClub]
            val opponentClub = participants[result.opponentClub]
            canonical.perspectivePlayers().map { performance ->
                PlayerProfileAppearance(
                    playerId = performance.player.id,
                    platformName = performance.player.platformName?.value,
                    proName = performance.player.proName?.value,
                    matchId = canonical.matchId,
                    playedAt = canonical.footballMatch.playedAt,
                    competition = canonical.footballMatch.competition,
                    ourClubName = ourClub?.club?.name?.value,
                    opponentClubName = opponentClub?.club?.name?.value,
                    ourScore = result.ourScore.goals,
                    opponentScore = result.opponentScore.goals,
                    outcome = result.outcome,
                    completion = canonical.footballMatch.completion,
                    rating = performance.rating?.value,
                    goals = performance.attacking.goals,
                    assists = performance.attacking.assists,
                    shots = performance.attacking.shots,
                    passesCompleted = performance.passing.completed,
                    passesAttempted = performance.passing.attempted,
                    tacklesCompleted = performance.defending.tacklesCompleted,
                    tacklesAttempted = performance.defending.tacklesAttempted,
                    redCards = performance.discipline.redCards,
                    awards = canonical.interpretation.awards.all()
                        .filter { it.winnerId == performance.player.id }
                        .mapTo(linkedSetOf()) { it.type },
                    advancedCoverage = performance.advancedCoverage,
                    advancedDribblesCompleted = performance.advanced.dribblesCompleted.takeIf {
                        performance.advancedCoverage == AdvancedStatsCoverage.FULL
                    },
                    advancedBeats = performance.advanced.beats.takeIf {
                        performance.advancedCoverage == AdvancedStatsCoverage.FULL
                    },
                    durationSeconds = performance.participation.duration?.seconds?.toInt(),
                )
            }
        }

    private fun CanonicalMatch.perspectivePlayers(): List<PlayerMatchPerformance> =
        footballMatch.participants
            .firstOrNull { it.club.id == interpretation.perspectiveClubId }
            ?.players
            .orEmpty()

    private fun PlayerProfileAppearance.toProfileMatch(): PlayerProfileMatch {
        return PlayerProfileMatch(
            matchId = matchId,
            playedAt = playedAt,
            competition = competition,
            ourClubName = ourClubName,
            opponentClubName = opponentClubName,
            ourScore = ourScore,
            opponentScore = opponentScore,
            outcome = outcome,
            rating = rating,
            goals = goals,
            assists = assists,
            awards = awards,
        )
    }

    private fun List<BigDecimal>.averageOrNull(): BigDecimal? =
        takeIf { it.isNotEmpty() }
            ?.reduce(BigDecimal::add)
            ?.divide(size.toBigDecimal(), 2, RoundingMode.HALF_UP)

    private fun buildXRay(profile: PlayerProfile, appearances: List<PlayerProfileAppearance>): PlayerXRay {
        val currentForm = currentForm(appearances)
        val attack = PlayerAttackMetrics(
            goals = profile.goals,
            goalsPerMatch = perMatch(profile.goals, profile.matchCount),
            shots = profile.shots,
            shotsPerMatch = perMatch(profile.shots, profile.matchCount),
            finishingConversion = percentage(profile.goals, profile.shots),
        )
        val creation = PlayerCreationMetrics(
            assists = profile.assists,
            assistsPerMatch = perMatch(profile.assists, profile.matchCount),
            passesAttempted = profile.passesAttempted,
            passesCompleted = profile.passesCompleted,
            passAccuracy = percentage(profile.passesCompleted, profile.passesAttempted),
            directContributions = profile.goals + profile.assists,
            directContributionsPerMatch = perMatch(profile.goals + profile.assists, profile.matchCount),
        )
        val defense = PlayerDefenseMetrics(
            tacklesAttempted = profile.tacklesAttempted,
            tacklesCompleted = profile.tacklesCompleted,
            tackleEfficiency = percentage(profile.tacklesCompleted, profile.tacklesAttempted),
            tacklesCompletedPerMatch = perMatch(profile.tacklesCompleted, profile.matchCount),
        )
        val fullAdvanced = appearances.filter { it.advancedCoverage == AdvancedStatsCoverage.FULL }
        val partialAdvanced = appearances.count { it.advancedCoverage == AdvancedStatsCoverage.PARTIAL }
        val advancedCoverage = PlayerAdvancedCoverage(
            eligibleAppearances = appearances.size,
            fullAppearances = fullAdvanced.size,
            partialAppearances = partialAdvanced,
            unavailableAppearances = appearances.count { it.advancedCoverage == AdvancedStatsCoverage.UNAVAILABLE },
            coverage = when {
                fullAdvanced.size == appearances.size -> AdvancedStatsCoverage.FULL
                fullAdvanced.isNotEmpty() || partialAdvanced > 0 -> AdvancedStatsCoverage.PARTIAL
                else -> AdvancedStatsCoverage.UNAVAILABLE
            },
        )
        val oneOnOne = fullAdvanced.takeIf { it.isNotEmpty() }?.let { covered ->
            PlayerOneOnOneMetrics(
                coveredAppearances = covered.size,
                opponentsBeaten = covered.sumOf { it.advancedBeats ?: 0 },
            )
        }
        val recognitions = PlayerRecognitions(
            craques = profile.craques,
            bagres = profile.bagres,
            xerifes = profile.xerifes,
            eligibleAppearances = profile.matchCount,
            craqueRate = percentage(profile.craques, profile.matchCount),
            bagreRate = percentage(profile.bagres, profile.matchCount),
            xerifeRate = percentage(profile.xerifes, profile.matchCount),
        )
        val records = records(appearances)
        val trend = trend(currentForm)
        val consistency = consistency(appearances)
        val analysis = analysis(profile, currentForm, creation, attack, defense)
        return PlayerXRay(
            currentForm = currentForm,
            trend = trend,
            consistency = consistency,
            attack = attack,
            creation = creation,
            defense = defense,
            advancedCoverage = advancedCoverage,
            oneOnOne = oneOnOne,
            recognitions = recognitions,
            records = records,
            analysis = analysis,
        )
    }

    private fun currentForm(appearances: List<PlayerProfileAppearance>): PlayerCurrentForm = when {
        appearances.size < CURRENT_FORM_MATCH_COUNT -> PlayerCurrentForm(PlayerCurrentFormState.FORMING, null, null, null)
        appearances.size < CURRENT_FORM_MATCH_COUNT * 2 -> PlayerCurrentForm(
            PlayerCurrentFormState.RECENT_ONLY,
            period(appearances.take(CURRENT_FORM_MATCH_COUNT)),
            null,
            null,
        )
        else -> {
            val recent = period(appearances.take(CURRENT_FORM_MATCH_COUNT))
            val previous = period(appearances.drop(CURRENT_FORM_MATCH_COUNT))
            PlayerCurrentForm(
                PlayerCurrentFormState.COMPARED,
                recent,
                previous,
                PlayerFormDifferences(
                    averageRating = difference(recent.averageRating, previous.averageRating),
                    goalsPerMatch = recent.goalsPerMatch - previous.goalsPerMatch,
                    assistsPerMatch = recent.assistsPerMatch - previous.assistsPerMatch,
                    directContributionsPerMatch = recent.directContributionsPerMatch - previous.directContributionsPerMatch,
                    passAccuracyPoints = difference(recent.passAccuracy, previous.passAccuracy),
                    tackleEfficiencyPoints = difference(recent.tackleEfficiency, previous.tackleEfficiency),
                    finishingConversionPoints = difference(recent.finishingConversion, previous.finishingConversion),
                ),
            )
        }
    }

    private fun period(appearances: List<PlayerProfileAppearance>): PlayerFormPeriod {
        val ratings = appearances.mapNotNull { it.rating }
        val goals = appearances.sumOf { it.goals ?: 0 }
        val assists = appearances.sumOf { it.assists ?: 0 }
        val passesCompleted = appearances.sumOf { it.passesCompleted ?: 0 }
        val passesAttempted = appearances.sumOf { it.passesAttempted ?: 0 }
        val tacklesCompleted = appearances.sumOf { it.tacklesCompleted ?: 0 }
        val tacklesAttempted = appearances.sumOf { it.tacklesAttempted ?: 0 }
        val shots = appearances.sumOf { it.shots ?: 0 }
        return PlayerFormPeriod(
            appearances = appearances.size,
            ratedAppearances = ratings.size,
            averageRating = ratings.averageOrNull(),
            goals = goals,
            assists = assists,
            directContributions = goals + assists,
            goalsPerMatch = perMatch(goals, appearances.size),
            assistsPerMatch = perMatch(assists, appearances.size),
            directContributionsPerMatch = perMatch(goals + assists, appearances.size),
            passAccuracy = percentage(passesCompleted, passesAttempted),
            tackleEfficiency = percentage(tacklesCompleted, tacklesAttempted),
            finishingConversion = percentage(goals, shots),
            passesCompleted = passesCompleted,
            passAttempts = passesAttempted,
            tacklesCompleted = tacklesCompleted,
            tackleAttempts = tacklesAttempted,
            shots = shots,
        )
    }

    private fun records(appearances: List<PlayerProfileAppearance>): PlayerPersonalRecords = PlayerPersonalRecords(
        mostGoalsInMatch = matchRecord(appearances) { it.goals ?: 0 },
        mostAssistsInMatch = matchRecord(appearances) { it.assists ?: 0 },
        mostDirectContributionsInMatch = matchRecord(appearances) { (it.goals ?: 0) + (it.assists ?: 0) },
        scoringStreak = longestStreak(appearances) { (it.goals ?: 0) > 0 },
        assistStreak = longestStreak(appearances) { (it.assists ?: 0) > 0 },
        directContributionStreak = longestStreak(appearances) { (it.goals ?: 0) + (it.assists ?: 0) > 0 },
        ratingTenMatches = appearances.count { it.rating?.compareTo(BigDecimal.TEN) == 0 },
    )

    /** Highest value; ties use the most recent match, then match id ascending. */
    private fun matchRecord(
        appearances: List<PlayerProfileAppearance>,
        value: (PlayerProfileAppearance) -> Int,
    ): PlayerSingleMatchRecord? = appearances
        .asSequence()
        .map { it to value(it) }
        .filter { (_, candidate) -> candidate > 0 }
        .sortedWith(
            compareByDescending<Pair<PlayerProfileAppearance, Int>> { it.second }
                .thenByDescending { it.first.playedAt }
                .thenBy { it.first.matchId.value },
        )
        .firstOrNull()
        ?.let { (appearance, candidate) ->
            PlayerSingleMatchRecord(candidate, appearance.matchId, appearance.playedAt, appearance.opponentClubName)
        }

    private fun longestStreak(
        appearances: List<PlayerProfileAppearance>,
        qualifies: (PlayerProfileAppearance) -> Boolean,
    ): Int {
        var current = 0
        var longest = 0
        appearances.asReversed().forEach { appearance ->
            current = if (qualifies(appearance)) current + 1 else 0
            longest = maxOf(longest, current)
        }
        return longest
    }

    private fun analysis(
        profile: PlayerProfile,
        form: PlayerCurrentForm,
        creation: PlayerCreationMetrics,
        attack: PlayerAttackMetrics,
        defense: PlayerDefenseMetrics,
    ): PlayerAnalysis {
        val strengths = strengths(profile, creation, attack, defense)
        val direct = profile.goals + profile.assists
        val summary = strengths.firstOrNull()?.let { primary ->
            "${primary.label} é o principal destaque nos dados registrados: ${primary.message}"
        } ?: if (direct > 0) {
            "${profile.displayName} soma $direct participações diretas em ${profile.matchCount} partidas elegíveis."
        } else {
            "${profile.displayName} tem ${profile.matchCount} partidas elegíveis registradas no histórico."
        }
        return PlayerAnalysis(
            summary = summary,
            strengths = strengths,
            improvement = improvementAssessment(form, profile, creation, attack, defense),
        )
    }

    /**
     * Finds at most two strengths. Scores only rank candidates that already
     * meet their explicit volume and quality rule; they never compare players.
     */
    private fun strengths(
        profile: PlayerProfile,
        creation: PlayerCreationMetrics,
        attack: PlayerAttackMetrics,
        defense: PlayerDefenseMetrics,
    ): List<PlayerStrength> {
        val directContributions = creation.directContributions
        val candidates = buildList {
            if (profile.matchCount >= MIN_STRENGTH_MATCHES &&
                directContributions >= MIN_DIRECT_CONTRIBUTIONS_FOR_STRENGTH &&
                creation.directContributionsPerMatch >= STRENGTH_DIRECT_CONTRIBUTIONS_PER_MATCH
            ) {
                add(
                    StrengthCandidate(
                        score = normalizedScore(creation.directContributionsPerMatch, STRENGTH_DIRECT_CONTRIBUTIONS_PER_MATCH),
                        priority = 0,
                        strength = PlayerStrength(
                            category = PlayerStrengthCategory.OFFENSIVE_PRODUCTION,
                            label = "Produção ofensiva",
                            evidence = PlayerAnalysisEvidence(
                                value = creation.directContributionsPerMatch,
                                unit = PlayerEvidenceUnit.PER_MATCH,
                                numerator = directContributions,
                                denominator = null,
                                appearances = profile.matchCount,
                            ),
                            rule = "Pelo menos $MIN_DIRECT_CONTRIBUTIONS_FOR_STRENGTH participações diretas e média mínima de ${formatDecimal(STRENGTH_DIRECT_CONTRIBUTIONS_PER_MATCH)} por partida em ao menos $MIN_STRENGTH_MATCHES partidas.",
                            message = "$directContributions participações diretas em ${profile.matchCount} partidas, média de ${formatDecimal(creation.directContributionsPerMatch)} por jogo.",
                        ),
                    ),
                )
            }
            attack.finishingConversion?.takeIf {
                attack.shots >= MIN_STRUCTURAL_SHOT_VOLUME && it >= STRENGTH_FINISHING_CONVERSION
            }?.let { conversion ->
                add(
                    StrengthCandidate(
                        score = normalizedScore(conversion, STRENGTH_FINISHING_CONVERSION),
                        priority = 1,
                        strength = PlayerStrength(
                            category = PlayerStrengthCategory.FINISHING,
                            label = "Finalização",
                            evidence = PlayerAnalysisEvidence(
                                value = conversion,
                                unit = PlayerEvidenceUnit.PERCENTAGE,
                                numerator = attack.goals,
                                denominator = attack.shots,
                                appearances = profile.matchCount,
                            ),
                            rule = "Conversão mínima de ${formatPercentage(STRENGTH_FINISHING_CONVERSION)} em pelo menos $MIN_STRUCTURAL_SHOT_VOLUME finalizações.",
                            message = "${attack.goals} gols em ${attack.shots} finalizações, conversão de ${formatPercentage(conversion)}.",
                        ),
                    ),
                )
            }
            if (profile.matchCount >= MIN_STRENGTH_MATCHES && profile.assists >= MIN_ASSISTS_FOR_STRENGTH &&
                creation.assistsPerMatch >= STRENGTH_ASSISTS_PER_MATCH
            ) {
                add(
                    StrengthCandidate(
                        score = normalizedScore(creation.assistsPerMatch, STRENGTH_ASSISTS_PER_MATCH),
                        priority = 2,
                        strength = PlayerStrength(
                            category = PlayerStrengthCategory.CREATION,
                            label = "Criação",
                            evidence = PlayerAnalysisEvidence(
                                value = creation.assistsPerMatch,
                                unit = PlayerEvidenceUnit.PER_MATCH,
                                numerator = profile.assists,
                                denominator = null,
                                appearances = profile.matchCount,
                            ),
                            rule = "Pelo menos $MIN_ASSISTS_FOR_STRENGTH assistências e média mínima de ${formatDecimal(STRENGTH_ASSISTS_PER_MATCH)} por partida em ao menos $MIN_STRENGTH_MATCHES partidas.",
                            message = "${profile.assists} assistências em ${profile.matchCount} partidas, média de ${formatDecimal(creation.assistsPerMatch)} por jogo.",
                        ),
                    ),
                )
            }
            creation.passAccuracy?.takeIf {
                creation.passesAttempted >= MIN_STRUCTURAL_PASS_VOLUME && it >= STRENGTH_PASS_ACCURACY
            }?.let { accuracy ->
                add(
                    StrengthCandidate(
                        score = normalizedScore(accuracy, STRENGTH_PASS_ACCURACY),
                        priority = 3,
                        strength = PlayerStrength(
                            category = PlayerStrengthCategory.PASSING,
                            label = "Distribuição",
                            evidence = PlayerAnalysisEvidence(
                                value = accuracy,
                                unit = PlayerEvidenceUnit.PERCENTAGE,
                                numerator = creation.passesCompleted,
                                denominator = creation.passesAttempted,
                                appearances = profile.matchCount,
                            ),
                            rule = "Precisão mínima de ${formatPercentage(STRENGTH_PASS_ACCURACY)} em pelo menos $MIN_STRUCTURAL_PASS_VOLUME passes tentados.",
                            message = "${creation.passesCompleted} passes completos em ${creation.passesAttempted} tentativas, precisão de ${formatPercentage(accuracy)}.",
                        ),
                    ),
                )
            }
            defense.tackleEfficiency?.takeIf {
                defense.tacklesAttempted >= MIN_STRUCTURAL_TACKLE_VOLUME && it >= STRENGTH_TACKLE_EFFICIENCY
            }?.let { efficiency ->
                add(
                    StrengthCandidate(
                        score = normalizedScore(efficiency, STRENGTH_TACKLE_EFFICIENCY),
                        priority = 4,
                        strength = PlayerStrength(
                            category = PlayerStrengthCategory.TACKLING,
                            label = "Desarme",
                            evidence = PlayerAnalysisEvidence(
                                value = efficiency,
                                unit = PlayerEvidenceUnit.PERCENTAGE,
                                numerator = defense.tacklesCompleted,
                                denominator = defense.tacklesAttempted,
                                appearances = profile.matchCount,
                            ),
                            rule = "Eficiência mínima de ${formatPercentage(STRENGTH_TACKLE_EFFICIENCY)} em pelo menos $MIN_STRUCTURAL_TACKLE_VOLUME desarmes tentados.",
                            message = "${defense.tacklesCompleted} desarmes certos em ${defense.tacklesAttempted} tentativas, eficiência de ${formatPercentage(efficiency)}.",
                        ),
                    ),
                )
            }
        }
        return candidates
            .sortedWith(compareByDescending<StrengthCandidate> { it.score }.thenBy { it.priority })
            .take(MAX_STRENGTHS)
            .map { it.strength }
    }

    /** Always searches regression first, then structural efficiency. */
    private fun improvementAssessment(
        form: PlayerCurrentForm,
        profile: PlayerProfile,
        creation: PlayerCreationMetrics,
        attack: PlayerAttackMetrics,
        defense: PlayerDefenseMetrics,
    ): PlayerImprovementAssessment {
        val opportunity = regressionCandidates(form).best()
            ?: structuralLowEfficiencyCandidates(profile, creation, attack, defense).best()
        return if (opportunity == null) {
            PlayerImprovementAssessment(
                state = PlayerImprovementAssessmentState.INSUFFICIENT_EVIDENCE,
                opportunity = null,
                message = "Não há evidência suficiente para apontar uma oportunidade confiável nos fundamentos cobertos.",
            )
        } else {
            PlayerImprovementAssessment(
                state = PlayerImprovementAssessmentState.FOUND,
                opportunity = opportunity.opportunity,
                message = opportunity.opportunity.message,
            )
        }
    }

    private fun regressionCandidates(form: PlayerCurrentForm): List<OpportunityCandidate> {
        if (form.state != PlayerCurrentFormState.COMPARED) return emptyList()
        val recent = form.recent ?: return emptyList()
        val previous = form.previous ?: return emptyList()
        val differences = form.differences ?: return emptyList()
        return buildList {
            differences.averageRating?.takeIf {
                recent.ratedAppearances >= MIN_RATED_APPEARANCES_FOR_TREND &&
                    previous.ratedAppearances >= MIN_RATED_APPEARANCES_FOR_TREND && it <= -RATING_REGRESSION_THRESHOLD
            }?.let { delta ->
                add(regressionCandidate(
                    area = PlayerImprovementArea.RATING,
                    label = "Nota média",
                    recentValue = recent.averageRating!!,
                    baselineValue = previous.averageRating!!,
                    delta = delta,
                    unit = PlayerEvidenceUnit.RATING,
                    recentNumerator = null,
                    recentDenominator = null,
                    recentAppearances = recent.ratedAppearances,
                    baselineNumerator = null,
                    baselineDenominator = null,
                    baselineAppearances = previous.ratedAppearances,
                    threshold = RATING_REGRESSION_THRESHOLD,
                    priority = 0,
                    rule = "Queda de pelo menos ${formatDecimal(RATING_REGRESSION_THRESHOLD)} na nota média, com ao menos $MIN_RATED_APPEARANCES_FOR_TREND atuações com nota em cada período.",
                    message = "Nas últimas cinco partidas com comparação, a nota média caiu de ${formatDecimal(previous.averageRating)} para ${formatDecimal(recent.averageRating)} (${formatSignedDecimal(delta)}).",
                ))
            }
            differences.directContributionsPerMatch.takeIf {
                previous.directContributions >= MIN_DIRECT_CONTRIBUTIONS_FOR_REGRESSION && it <= -DIRECT_CONTRIBUTION_REGRESSION_THRESHOLD
            }?.let { delta ->
                add(regressionCandidate(
                    area = PlayerImprovementArea.DIRECT_CONTRIBUTIONS,
                    label = "Participações diretas por jogo",
                    recentValue = recent.directContributionsPerMatch,
                    baselineValue = previous.directContributionsPerMatch,
                    delta = delta,
                    unit = PlayerEvidenceUnit.PER_MATCH,
                    recentNumerator = recent.directContributions,
                    recentDenominator = null,
                    recentAppearances = recent.appearances,
                    baselineNumerator = previous.directContributions,
                    baselineDenominator = null,
                    baselineAppearances = previous.appearances,
                    threshold = DIRECT_CONTRIBUTION_REGRESSION_THRESHOLD,
                    priority = 1,
                    rule = "Queda de pelo menos ${formatDecimal(DIRECT_CONTRIBUTION_REGRESSION_THRESHOLD)} participações diretas por partida, com ao menos $MIN_DIRECT_CONTRIBUTIONS_FOR_REGRESSION participações no histórico anterior.",
                    message = "Nas últimas cinco partidas, foram ${recent.directContributions} participações diretas (${formatDecimal(recent.directContributionsPerMatch)} por jogo), contra ${previous.directContributions} (${formatDecimal(previous.directContributionsPerMatch)} por jogo) no histórico anterior (${formatSignedDecimal(delta)}).",
                ))
            }
            differences.goalsPerMatch.takeIf {
                previous.goals >= MIN_GOALS_FOR_REGRESSION && it <= -GOALS_REGRESSION_THRESHOLD
            }?.let { delta ->
                add(regressionCandidate(
                    area = PlayerImprovementArea.GOALS,
                    label = "Gols por jogo",
                    recentValue = recent.goalsPerMatch,
                    baselineValue = previous.goalsPerMatch,
                    delta = delta,
                    unit = PlayerEvidenceUnit.PER_MATCH,
                    recentNumerator = recent.goals,
                    recentDenominator = null,
                    recentAppearances = recent.appearances,
                    baselineNumerator = previous.goals,
                    baselineDenominator = null,
                    baselineAppearances = previous.appearances,
                    threshold = GOALS_REGRESSION_THRESHOLD,
                    priority = 2,
                    rule = "Queda de pelo menos ${formatDecimal(GOALS_REGRESSION_THRESHOLD)} gols por partida, com ao menos $MIN_GOALS_FOR_REGRESSION gols no histórico anterior.",
                    message = "Nas últimas cinco partidas, marcou ${recent.goals} gol(s) (${formatDecimal(recent.goalsPerMatch)} por jogo), contra ${previous.goals} (${formatDecimal(previous.goalsPerMatch)} por jogo) no histórico anterior (${formatSignedDecimal(delta)}).",
                ))
            }
            differences.assistsPerMatch.takeIf {
                previous.assists >= MIN_ASSISTS_FOR_REGRESSION && it <= -ASSISTS_REGRESSION_THRESHOLD
            }?.let { delta ->
                add(regressionCandidate(
                    area = PlayerImprovementArea.ASSISTS,
                    label = "Assistências por jogo",
                    recentValue = recent.assistsPerMatch,
                    baselineValue = previous.assistsPerMatch,
                    delta = delta,
                    unit = PlayerEvidenceUnit.PER_MATCH,
                    recentNumerator = recent.assists,
                    recentDenominator = null,
                    recentAppearances = recent.appearances,
                    baselineNumerator = previous.assists,
                    baselineDenominator = null,
                    baselineAppearances = previous.appearances,
                    threshold = ASSISTS_REGRESSION_THRESHOLD,
                    priority = 3,
                    rule = "Queda de pelo menos ${formatDecimal(ASSISTS_REGRESSION_THRESHOLD)} assistências por partida, com ao menos $MIN_ASSISTS_FOR_REGRESSION assistências no histórico anterior.",
                    message = "Nas últimas cinco partidas, deu ${recent.assists} assistência(s) (${formatDecimal(recent.assistsPerMatch)} por jogo), contra ${previous.assists} (${formatDecimal(previous.assistsPerMatch)} por jogo) no histórico anterior (${formatSignedDecimal(delta)}).",
                ))
            }
            differences.passAccuracyPoints?.takeIf {
                recent.passAttempts >= MIN_PASS_VOLUME && previous.passAttempts >= MIN_PASS_VOLUME && it <= -PASS_REGRESSION_THRESHOLD
            }?.let { delta ->
                add(regressionCandidate(
                    area = PlayerImprovementArea.PASSING,
                    label = "Precisão de passe",
                    recentValue = recent.passAccuracy!!,
                    baselineValue = previous.passAccuracy!!,
                    delta = delta,
                    unit = PlayerEvidenceUnit.PERCENTAGE,
                    recentNumerator = recent.passesCompleted,
                    recentDenominator = recent.passAttempts,
                    recentAppearances = recent.appearances,
                    baselineNumerator = previous.passesCompleted,
                    baselineDenominator = previous.passAttempts,
                    baselineAppearances = previous.appearances,
                    threshold = PASS_REGRESSION_THRESHOLD,
                    priority = 4,
                    rule = "Queda de pelo menos ${formatPercentagePoints(PASS_REGRESSION_THRESHOLD)} com no mínimo $MIN_PASS_VOLUME passes tentados em cada período.",
                    message = "Nas últimas cinco partidas, acertou ${formatPercentage(recent.passAccuracy)} dos passes (${recent.passesCompleted} de ${recent.passAttempts}), contra ${formatPercentage(previous.passAccuracy)} (${previous.passesCompleted} de ${previous.passAttempts}) no histórico anterior (${formatSignedPercentagePoints(delta)}).",
                ))
            }
            differences.tackleEfficiencyPoints?.takeIf {
                recent.tackleAttempts >= MIN_TACKLE_VOLUME && previous.tackleAttempts >= MIN_TACKLE_VOLUME && it <= -TACKLE_REGRESSION_THRESHOLD
            }?.let { delta ->
                add(regressionCandidate(
                    area = PlayerImprovementArea.TACKLING,
                    label = "Eficiência de desarme",
                    recentValue = recent.tackleEfficiency!!,
                    baselineValue = previous.tackleEfficiency!!,
                    delta = delta,
                    unit = PlayerEvidenceUnit.PERCENTAGE,
                    recentNumerator = recent.tacklesCompleted,
                    recentDenominator = recent.tackleAttempts,
                    recentAppearances = recent.appearances,
                    baselineNumerator = previous.tacklesCompleted,
                    baselineDenominator = previous.tackleAttempts,
                    baselineAppearances = previous.appearances,
                    threshold = TACKLE_REGRESSION_THRESHOLD,
                    priority = 5,
                    rule = "Queda de pelo menos ${formatPercentagePoints(TACKLE_REGRESSION_THRESHOLD)} com no mínimo $MIN_TACKLE_VOLUME desarmes tentados em cada período.",
                    message = "Nas últimas cinco partidas, acertou ${formatPercentage(recent.tackleEfficiency)} dos desarmes (${recent.tacklesCompleted} de ${recent.tackleAttempts}), contra ${formatPercentage(previous.tackleEfficiency)} (${previous.tacklesCompleted} de ${previous.tackleAttempts}) no histórico anterior (${formatSignedPercentagePoints(delta)}).",
                ))
            }
            differences.finishingConversionPoints?.takeIf {
                recent.shots >= MIN_SHOT_VOLUME && previous.shots >= MIN_SHOT_VOLUME && it <= -FINISHING_REGRESSION_THRESHOLD
            }?.let { delta ->
                add(regressionCandidate(
                    area = PlayerImprovementArea.FINISHING,
                    label = "Conversão de finalizações",
                    recentValue = recent.finishingConversion!!,
                    baselineValue = previous.finishingConversion!!,
                    delta = delta,
                    unit = PlayerEvidenceUnit.PERCENTAGE,
                    recentNumerator = recent.goals,
                    recentDenominator = recent.shots,
                    recentAppearances = recent.appearances,
                    baselineNumerator = previous.goals,
                    baselineDenominator = previous.shots,
                    baselineAppearances = previous.appearances,
                    threshold = FINISHING_REGRESSION_THRESHOLD,
                    priority = 6,
                    rule = "Queda de pelo menos ${formatPercentagePoints(FINISHING_REGRESSION_THRESHOLD)} com no mínimo $MIN_SHOT_VOLUME finalizações em cada período.",
                    message = "Nas últimas cinco partidas, converteu ${formatPercentage(recent.finishingConversion)} das finalizações (${recent.goals} de ${recent.shots}), contra ${formatPercentage(previous.finishingConversion)} (${previous.goals} de ${previous.shots}) no histórico anterior (${formatSignedPercentagePoints(delta)}).",
                ))
            }
        }
    }

    private fun regressionCandidate(
        area: PlayerImprovementArea,
        label: String,
        recentValue: BigDecimal,
        baselineValue: BigDecimal,
        delta: BigDecimal,
        unit: PlayerEvidenceUnit,
        recentNumerator: Int?,
        recentDenominator: Int?,
        recentAppearances: Int,
        baselineNumerator: Int?,
        baselineDenominator: Int?,
        baselineAppearances: Int,
        threshold: BigDecimal,
        priority: Int,
        rule: String,
        message: String,
    ): OpportunityCandidate = OpportunityCandidate(
        score = delta.abs().divide(threshold, 4, RoundingMode.HALF_UP),
        priority = priority,
        opportunity = PlayerImprovementOpportunity(
            source = PlayerImprovementSource.RECENT_REGRESSION,
            area = area,
            label = label,
            evidence = PlayerAnalysisEvidence(
                value = recentValue,
                unit = unit,
                numerator = recentNumerator,
                denominator = recentDenominator,
                appearances = recentAppearances,
                baselineValue = baselineValue,
                delta = delta,
                baselineNumerator = baselineNumerator,
                baselineDenominator = baselineDenominator,
                baselineAppearances = baselineAppearances,
            ),
            rule = rule,
            message = message,
        ),
    )

    private fun structuralLowEfficiencyCandidates(
        profile: PlayerProfile,
        creation: PlayerCreationMetrics,
        attack: PlayerAttackMetrics,
        defense: PlayerDefenseMetrics,
    ): List<OpportunityCandidate> = buildList {
        creation.passAccuracy?.takeIf {
            creation.passesAttempted >= MIN_STRUCTURAL_PASS_VOLUME && it < STRUCTURAL_LOW_PASS_ACCURACY
        }?.let { accuracy ->
            add(structuralCandidate(
                area = PlayerImprovementArea.PASSING,
                label = "Precisão de passe",
                value = accuracy,
                numerator = creation.passesCompleted,
                denominator = creation.passesAttempted,
                appearances = profile.matchCount,
                threshold = STRUCTURAL_LOW_PASS_ACCURACY,
                priority = 0,
                rule = "Precisão abaixo de ${formatPercentage(STRUCTURAL_LOW_PASS_ACCURACY)} em pelo menos $MIN_STRUCTURAL_PASS_VOLUME passes tentados no histórico elegível.",
                message = "Nos dados registrados, acertou ${creation.passesCompleted} de ${creation.passesAttempted} passes (${formatPercentage(accuracy)}). É o indicador de eficiência de passe abaixo do limiar com volume suficiente.",
            ))
        }
        defense.tackleEfficiency?.takeIf {
            defense.tacklesAttempted >= MIN_STRUCTURAL_TACKLE_VOLUME && it < STRUCTURAL_LOW_TACKLE_EFFICIENCY
        }?.let { efficiency ->
            add(structuralCandidate(
                area = PlayerImprovementArea.TACKLING,
                label = "Eficiência de desarme",
                value = efficiency,
                numerator = defense.tacklesCompleted,
                denominator = defense.tacklesAttempted,
                appearances = profile.matchCount,
                threshold = STRUCTURAL_LOW_TACKLE_EFFICIENCY,
                priority = 1,
                rule = "Eficiência abaixo de ${formatPercentage(STRUCTURAL_LOW_TACKLE_EFFICIENCY)} em pelo menos $MIN_STRUCTURAL_TACKLE_VOLUME desarmes tentados no histórico elegível.",
                message = "Nos dados registrados, acertou ${defense.tacklesCompleted} de ${defense.tacklesAttempted} desarmes (${formatPercentage(efficiency)}). É o indicador de eficiência de desarme abaixo do limiar com volume suficiente.",
            ))
        }
        attack.finishingConversion?.takeIf {
            attack.shots >= MIN_STRUCTURAL_SHOT_VOLUME && it < STRUCTURAL_LOW_FINISHING_CONVERSION
        }?.let { conversion ->
            add(structuralCandidate(
                area = PlayerImprovementArea.FINISHING,
                label = "Conversão de finalizações",
                value = conversion,
                numerator = attack.goals,
                denominator = attack.shots,
                appearances = profile.matchCount,
                threshold = STRUCTURAL_LOW_FINISHING_CONVERSION,
                priority = 2,
                rule = "Conversão abaixo de ${formatPercentage(STRUCTURAL_LOW_FINISHING_CONVERSION)} em pelo menos $MIN_STRUCTURAL_SHOT_VOLUME finalizações no histórico elegível.",
                message = "Nos dados registrados, marcou ${attack.goals} gol(s) em ${attack.shots} finalizações (${formatPercentage(conversion)}). É o indicador de conversão abaixo do limiar com volume suficiente.",
            ))
        }
    }

    private fun structuralCandidate(
        area: PlayerImprovementArea,
        label: String,
        value: BigDecimal,
        numerator: Int,
        denominator: Int,
        appearances: Int,
        threshold: BigDecimal,
        priority: Int,
        rule: String,
        message: String,
    ): OpportunityCandidate = OpportunityCandidate(
        score = threshold.subtract(value).divide(threshold, 4, RoundingMode.HALF_UP),
        priority = priority,
        opportunity = PlayerImprovementOpportunity(
            source = PlayerImprovementSource.STRUCTURAL_LOW_EFFICIENCY,
            area = area,
            label = label,
            evidence = PlayerAnalysisEvidence(
                value = value,
                unit = PlayerEvidenceUnit.PERCENTAGE,
                numerator = numerator,
                denominator = denominator,
                appearances = appearances,
            ),
            rule = rule,
            message = message,
        ),
    )

    private fun List<OpportunityCandidate>.best(): OpportunityCandidate? =
        sortedWith(compareByDescending<OpportunityCandidate> { it.score }.thenBy { it.priority }).firstOrNull()

    private fun normalizedScore(value: BigDecimal, threshold: BigDecimal): BigDecimal =
        value.divide(threshold, 4, RoundingMode.HALF_UP)

    private fun trend(form: PlayerCurrentForm): PlayerTrend {
        val recent = form.recent
        val previous = form.previous
        val differences = form.differences
        if (form.state != PlayerCurrentFormState.COMPARED || recent == null || previous == null || differences == null) {
            return PlayerTrend(PlayerTrendStatus.FORMING, recent?.averageRating, null, null, emptyList())
        }
        val ratingDelta = differences.averageRating.takeIf {
            recent.ratedAppearances >= MIN_RATED_APPEARANCES_FOR_TREND && previous.ratedAppearances >= MIN_RATED_APPEARANCES_FOR_TREND
        }
        val status = when {
            ratingDelta == null -> PlayerTrendStatus.FORMING
            ratingDelta >= RATING_TREND_THRESHOLD -> PlayerTrendStatus.RISING
            ratingDelta <= -RATING_TREND_THRESHOLD -> PlayerTrendStatus.FALLING
            else -> PlayerTrendStatus.STABLE
        }
        return PlayerTrend(
            status = status,
            recentRating = recent.averageRating,
            baselineRating = previous.averageRating,
            ratingDelta = ratingDelta,
            metrics = listOf(
                PlayerTrendMetric(PlayerTrendMetricType.GOALS_PER_MATCH, recent.goalsPerMatch, previous.goalsPerMatch, differences.goalsPerMatch),
                PlayerTrendMetric(PlayerTrendMetricType.ASSISTS_PER_MATCH, recent.assistsPerMatch, previous.assistsPerMatch, differences.assistsPerMatch),
                PlayerTrendMetric(PlayerTrendMetricType.DIRECT_CONTRIBUTIONS_PER_MATCH, recent.directContributionsPerMatch, previous.directContributionsPerMatch, differences.directContributionsPerMatch),
            ) + listOfNotNull(
                ratingDelta?.let { PlayerTrendMetric(PlayerTrendMetricType.RATING, recent.averageRating!!, previous.averageRating!!, it) },
            ),
        )
    }

    private fun consistency(appearances: List<PlayerProfileAppearance>): PlayerConsistency {
        val ratings = appearances.mapNotNull { it.rating }
        val average = ratings.averageOrNull()
        val available = ratings.size >= MIN_RATED_APPEARANCES_FOR_CONSISTENCY
        val deviation = average?.takeIf { available }?.let { mean ->
            val variance = ratings
                .map { rating -> rating.subtract(mean).toDouble().let { it * it } }
                .average()
            BigDecimal.valueOf(sqrt(variance)).setScale(2, RoundingMode.HALF_UP)
        }
        val atLeastEight = ratings.count { it >= RATING_EIGHT }
        val atLeastNine = ratings.count { it >= RATING_NINE }
        val tens = ratings.count { it.compareTo(BigDecimal.TEN) == 0 }
        return PlayerConsistency(
            state = if (available) PlayerConsistencyState.AVAILABLE else PlayerConsistencyState.INSUFFICIENT_SAMPLE,
            ratedAppearances = ratings.size,
            averageRating = average,
            ratingStandardDeviation = deviation,
            ratingsAtLeastEight = atLeastEight,
            ratingsAtLeastEightRate = percentage(atLeastEight, ratings.size),
            ratingsAtLeastNine = atLeastNine,
            ratingsAtLeastNineRate = percentage(atLeastNine, ratings.size),
            ratingTenMatches = tens,
        )
    }

    private data class StrengthCandidate(
        val score: BigDecimal,
        val priority: Int,
        val strength: PlayerStrength,
    )

    private data class OpportunityCandidate(
        val score: BigDecimal,
        val priority: Int,
        val opportunity: PlayerImprovementOpportunity,
    )

    private fun perMatch(value: Int, matches: Int): BigDecimal =
        if (matches == 0) BigDecimal.ZERO else value.toBigDecimal().divide(matches.toBigDecimal(), 2, RoundingMode.HALF_UP)

    private fun percentage(numerator: Int, denominator: Int): BigDecimal? =
        denominator.takeIf { it > 0 }?.let {
            numerator.toBigDecimal().multiply(BigDecimal(100)).divide(it.toBigDecimal(), 2, RoundingMode.HALF_UP)
        }

    private fun difference(current: BigDecimal?, previous: BigDecimal?): BigDecimal? =
        if (current == null || previous == null) null else current - previous

    private fun formatPercentage(value: BigDecimal): String = "${value.setScale(1, RoundingMode.HALF_UP)}%"

    private fun formatPercentagePoints(value: BigDecimal): String = "${value.setScale(1, RoundingMode.HALF_UP)} p.p."

    private fun formatDecimal(value: BigDecimal): String = value.setScale(2, RoundingMode.HALF_UP).toPlainString()

    private fun formatSignedDecimal(value: BigDecimal): String =
        "${if (value.signum() > 0) "+" else ""}${formatDecimal(value)}"

    private fun formatSignedPercentagePoints(value: BigDecimal): String =
        "${if (value.signum() > 0) "+" else ""}${formatPercentagePoints(value)}"

    private fun com.eafc26.discordstats.domain.interpretation.MatchAwards.all() =
        listOf(craque, bagre, xerife)

    private data class MutablePlayerIndex(
        val displayName: String,
        var matchCount: Int,
        val latestMatchAt: java.time.Instant,
        var ratingTotal: BigDecimal = BigDecimal.ZERO,
        var ratedMatchCount: Int = 0,
    )

    companion object {
        const val DEFAULT_RECENT_MATCH_LIMIT = 5
        private const val CURRENT_FORM_MATCH_COUNT = 5
        private const val MIN_RATED_APPEARANCES_FOR_TREND = 3
        private const val MIN_RATED_APPEARANCES_FOR_CONSISTENCY = 5
        private const val MIN_STRENGTH_MATCHES = 5
        private const val MIN_DIRECT_CONTRIBUTIONS_FOR_STRENGTH = 5
        private const val MIN_ASSISTS_FOR_STRENGTH = 3
        private const val MIN_DIRECT_CONTRIBUTIONS_FOR_REGRESSION = 5
        private const val MIN_GOALS_FOR_REGRESSION = 3
        private const val MIN_ASSISTS_FOR_REGRESSION = 3
        private const val MIN_PASS_VOLUME = 25
        private const val MIN_TACKLE_VOLUME = 10
        private const val MIN_SHOT_VOLUME = 8
        private const val MIN_STRUCTURAL_PASS_VOLUME = 75
        private const val MIN_STRUCTURAL_TACKLE_VOLUME = 30
        private const val MIN_STRUCTURAL_SHOT_VOLUME = 25
        private const val MAX_STRENGTHS = 2

        private val RATING_REGRESSION_THRESHOLD = BigDecimal("0.30")
        private val GOALS_REGRESSION_THRESHOLD = BigDecimal("0.50")
        private val ASSISTS_REGRESSION_THRESHOLD = BigDecimal("0.40")
        private val DIRECT_CONTRIBUTION_REGRESSION_THRESHOLD = BigDecimal("0.75")
        private val PASS_REGRESSION_THRESHOLD = BigDecimal("10.00")
        private val TACKLE_REGRESSION_THRESHOLD = BigDecimal("15.00")
        private val FINISHING_REGRESSION_THRESHOLD = BigDecimal("15.00")
        private val RATING_TREND_THRESHOLD = BigDecimal("0.30")

        private val STRENGTH_DIRECT_CONTRIBUTIONS_PER_MATCH = BigDecimal("1.00")
        private val STRENGTH_FINISHING_CONVERSION = BigDecimal("25.00")
        private val STRENGTH_ASSISTS_PER_MATCH = BigDecimal("0.50")
        private val STRENGTH_PASS_ACCURACY = BigDecimal("80.00")
        private val STRENGTH_TACKLE_EFFICIENCY = BigDecimal("60.00")
        private val STRUCTURAL_LOW_PASS_ACCURACY = BigDecimal("70.00")
        private val STRUCTURAL_LOW_TACKLE_EFFICIENCY = BigDecimal("40.00")
        private val STRUCTURAL_LOW_FINISHING_CONVERSION = BigDecimal("15.00")
        private val RATING_EIGHT = BigDecimal("8.00")
        private val RATING_NINE = BigDecimal("9.00")
    }
}
