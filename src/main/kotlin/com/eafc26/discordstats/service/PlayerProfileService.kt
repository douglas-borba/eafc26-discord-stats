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
import com.eafc26.discordstats.profile.PlayerImprovementArea
import com.eafc26.discordstats.profile.PlayerImprovementOpportunity
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

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
                dribblesCompleted = covered.sumOf { it.advancedDribblesCompleted ?: 0 },
                opponentsBeaten = covered.sumOf { it.advancedBeats ?: 0 },
            )
        }
        val recognitions = PlayerRecognitions(profile.craques, profile.bagres, profile.xerifes)
        val records = records(appearances)
        val analysis = analysis(profile, currentForm, creation, attack)
        return PlayerXRay(currentForm, attack, creation, defense, advancedCoverage, oneOnOne, recognitions, records, analysis)
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
            averageRating = ratings.averageOrNull(),
            goalsPerMatch = perMatch(goals, appearances.size),
            assistsPerMatch = perMatch(assists, appearances.size),
            directContributionsPerMatch = perMatch(goals + assists, appearances.size),
            passAccuracy = percentage(passesCompleted, passesAttempted),
            tackleEfficiency = percentage(tacklesCompleted, tacklesAttempted),
            finishingConversion = percentage(goals, shots),
            passAttempts = passesAttempted,
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
    ): PlayerAnalysis {
        val direct = profile.goals + profile.assists
        val summary = if (direct > 0) {
            "${profile.displayName} soma $direct participações diretas em ${profile.matchCount} partidas elegíveis."
        } else {
            "${profile.displayName} tem ${profile.matchCount} partidas elegíveis registradas no histórico."
        }
        val strengths = buildList {
            creation.passAccuracy?.takeIf { creation.passesAttempted >= MIN_PASS_VOLUME && it >= BigDecimal("75.00") }?.let {
                add("Mantém ${formatPercentage(it)} de precisão de passe em ${creation.passesAttempted} tentativas.")
            }
            attack.finishingConversion?.takeIf { attack.shots >= MIN_SHOT_VOLUME && it >= BigDecimal("20.00") }?.let {
                add("Converte ${formatPercentage(it)} das finalizações registradas.")
            }
        }
        val currentForm = form.differences?.averageRating?.takeIf { it.abs() >= BigDecimal("0.01") }?.let { difference ->
            val direction = if (difference > BigDecimal.ZERO) "acima" else "abaixo"
            "Nas últimas cinco partidas, sua nota média está ${difference.abs().setScale(2, RoundingMode.HALF_UP)} $direction da própria referência anterior."
        }
        return PlayerAnalysis(summary, strengths, currentForm, improvementOpportunity(form))
    }

    private fun improvementOpportunity(form: PlayerCurrentForm): PlayerImprovementOpportunity? {
        if (form.state != PlayerCurrentFormState.COMPARED) return null
        val recent = form.recent ?: return null
        val previous = form.previous ?: return null
        val differences = form.differences ?: return null
        val passDecline = differences.passAccuracyPoints?.takeIf {
            recent.passAttempts >= MIN_PASS_VOLUME && previous.passAttempts >= MIN_PASS_VOLUME && it <= BigDecimal("-10.00")
        }
        if (passDecline != null) return PlayerImprovementOpportunity(
            PlayerImprovementArea.PASSING,
            passDecline.abs(),
            "Eficiência de passe em atenção: nas últimas cinco partidas, ficou ${formatPercentagePoints(passDecline.abs())} abaixo da sua referência anterior.",
        )
        val tackleDecline = differences.tackleEfficiencyPoints?.takeIf {
            recent.tackleAttempts >= MIN_TACKLE_VOLUME && previous.tackleAttempts >= MIN_TACKLE_VOLUME && it <= BigDecimal("-15.00")
        }
        if (tackleDecline != null) return PlayerImprovementOpportunity(
            PlayerImprovementArea.TACKLING,
            tackleDecline.abs(),
            "Eficiência de desarme em atenção: nas últimas cinco partidas, ficou ${formatPercentagePoints(tackleDecline.abs())} abaixo da sua referência anterior.",
        )
        val finishingDecline = differences.finishingConversionPoints?.takeIf {
            recent.shots >= MIN_SHOT_VOLUME && previous.shots >= MIN_SHOT_VOLUME && it <= BigDecimal("-15.00")
        }
        if (finishingDecline != null) return PlayerImprovementOpportunity(
            PlayerImprovementArea.FINISHING,
            finishingDecline.abs(),
            "Conversão de finalizações em atenção: nas últimas cinco partidas, ficou ${formatPercentagePoints(finishingDecline.abs())} abaixo da sua referência anterior.",
        )
        return null
    }

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
        private const val MIN_PASS_VOLUME = 25
        private const val MIN_TACKLE_VOLUME = 10
        private const val MIN_SHOT_VOLUME = 8
    }
}
