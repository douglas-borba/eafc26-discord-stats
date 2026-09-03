package com.eafc26.discordstats.application.interpretation

import com.eafc26.discordstats.domain.interpretation.ContributionDecision
import com.eafc26.discordstats.domain.interpretation.BagrePerformanceDecision
import com.eafc26.discordstats.domain.interpretation.AwardDecision
import com.eafc26.discordstats.domain.interpretation.AwardMetrics
import com.eafc26.discordstats.domain.interpretation.BehindThePlayDecision
import com.eafc26.discordstats.domain.interpretation.DecisionEvidence
import com.eafc26.discordstats.domain.interpretation.EligibilityInterpretation
import com.eafc26.discordstats.domain.interpretation.EaRecognizedMvpDecision
import com.eafc26.discordstats.domain.interpretation.GoalkeeperArchetype
import com.eafc26.discordstats.domain.interpretation.GoalkeeperDecision
import com.eafc26.discordstats.domain.interpretation.GoalkeeperNarrativeVariant
import com.eafc26.discordstats.domain.interpretation.FeatureEvaluation
import com.eafc26.discordstats.domain.interpretation.HighlightsDecision
import com.eafc26.discordstats.domain.interpretation.LostMailDecision
import com.eafc26.discordstats.domain.interpretation.MatchAwards
import com.eafc26.discordstats.domain.interpretation.MatchFeatures
import com.eafc26.discordstats.domain.interpretation.MatchFeatureType
import com.eafc26.discordstats.domain.interpretation.OffensiveNarrativeCategory
import com.eafc26.discordstats.domain.interpretation.OffensiveNarrativeDecision
import com.eafc26.discordstats.domain.interpretation.OneOnOneDecision
import com.eafc26.discordstats.domain.interpretation.PassPrecisionDecision
import com.eafc26.discordstats.domain.interpretation.PlayerContribution
import com.eafc26.discordstats.domain.interpretation.RatedHighlight
import com.eafc26.discordstats.domain.interpretation.RedCardDecision
import com.eafc26.discordstats.domain.interpretation.ResultDecision
import com.eafc26.discordstats.domain.interpretation.RuleId
import com.eafc26.discordstats.domain.interpretation.RuleReference
import com.eafc26.discordstats.domain.interpretation.TeamMetrics
import com.eafc26.discordstats.domain.match.PlayerMatchPerformance
import com.eafc26.discordstats.domain.match.PlayerRole
import java.math.BigDecimal

/**
 * Completes the deterministic, presentation-neutral interpretation currently
 * required by Dashboard and Discord.
 */
class MatchFeaturesEvaluator {

    fun evaluate(
        players: Collection<PlayerMatchPerformance>,
        eligibility: EligibilityInterpretation,
        result: ResultDecision,
        teamMetrics: TeamMetrics,
        awards: MatchAwards,
    ): MatchFeatures {
        val eligible = players.filter { it.player.id in eligibility.eligiblePlayerIds }
        val outfield = eligible.filter { it.role is PlayerRole.Outfield }
        val bagreId = awards.bagre.winnerId
        val positiveOutfield = outfield.filter { it.player.id != bagreId }

        val contributions = contributions(eligible)
        val highlights = highlights(positiveOutfield, eligible, teamMetrics, bagreId)
        val bagrePerformance = bagrePerformance(awards.bagre)
        val offensive = offensive(positiveOutfield, result, bagreId)
        val behindThePlay = behindThePlay(positiveOutfield)
        val oneOnOne = oneOnOne(positiveOutfield)
        val redCard = redCard(outfield)
        val passPrecision = passPrecision(positiveOutfield, bagreId)
        val lostMail = lostMail(outfield)
        val goalkeeper = goalkeeper(players)
        val eaRecognizedMvp = eaRecognizedMvp(eligible)
        val population = listOf(populationEvidence(players))

        return MatchFeatures(
            contributions = contributions,
            highlights = highlights,
            bagrePerformance = bagrePerformance,
            offensiveNarratives = offensive,
            behindThePlay = behindThePlay,
            oneOnOne = oneOnOne,
            redCard = redCard,
            passPrecision = passPrecision,
            lostMail = lostMail,
            goalkeeper = goalkeeper,
            eaRecognizedMvp = eaRecognizedMvp,
            evaluations = listOf(
                FeatureEvaluation(
                    MatchFeatureType.CONTRIBUTIONS,
                    contributions.goalScorers.isNotEmpty() || contributions.assistProviders.isNotEmpty(),
                    CONTRIBUTIONS_RULE,
                    contributions.evidence,
                ),
                FeatureEvaluation(
                    MatchFeatureType.HIGHLIGHTS,
                    highlights.players.isNotEmpty() || highlights.teamAverageRating != null,
                    HIGHLIGHTS_RULE,
                    highlights.evidence,
                ),
                FeatureEvaluation(
                    MatchFeatureType.BAGRE_PERFORMANCE,
                    bagrePerformance != null,
                    bagrePerformance?.rule ?: BAGRE_PERFORMANCE_RULE,
                    bagrePerformance?.evidence ?: population,
                ),
                FeatureEvaluation(
                    MatchFeatureType.OFFENSIVE_NARRATIVES,
                    offensive.isNotEmpty(),
                    OFFENSIVE_RULE,
                    offensive.flatMap { it.evidence }.ifEmpty {
                        population + positiveOutfield.map(::attackingEvidence) +
                            DecisionEvidence.Scoreboard(
                                result.ourScore.goals,
                                result.opponentScore.goals,
                            )
                    },
                ),
                FeatureEvaluation(
                    MatchFeatureType.BEHIND_THE_PLAY,
                    behindThePlay != null,
                    BEHIND_THE_PLAY_RULE,
                    behindThePlay?.evidence ?: (population + positiveOutfield.map(::advancedEvidence)),
                ),
                FeatureEvaluation(
                    MatchFeatureType.ONE_ON_ONE,
                    oneOnOne != null,
                    ONE_ON_ONE_RULE,
                    oneOnOne?.evidence ?: (population + positiveOutfield.map(::advancedEvidence)),
                ),
                FeatureEvaluation(
                    MatchFeatureType.RED_CARD,
                    redCard != null,
                    RED_CARD_RULE,
                    redCard?.evidence ?: (
                        population + outfield.flatMap {
                            listOf(
                                DecisionEvidence.Discipline(it.player.id, it.discipline.redCards),
                                DecisionEvidence.Rating(it.player.id, it.rating?.value, null),
                            )
                        }
                    ),
                ),
                FeatureEvaluation(
                    MatchFeatureType.PASS_PRECISION,
                    passPrecision != null,
                    PASS_PRECISION_RULE,
                    passPrecision?.evidence ?: (population + positiveOutfield.map(::passingEvidence)),
                ),
                FeatureEvaluation(
                    MatchFeatureType.LOST_MAIL,
                    lostMail != null,
                    LOST_MAIL_RULE,
                    lostMail?.evidence ?: (population + outfield.map(::passingEvidence)),
                ),
                FeatureEvaluation(
                    MatchFeatureType.GOALKEEPER,
                    goalkeeper != null,
                    GOALKEEPER_RULE,
                    goalkeeper?.evidence ?: population,
                ),
                FeatureEvaluation(
                    MatchFeatureType.EA_RECOGNIZED_MVP,
                    eaRecognizedMvp != null,
                    EA_RECOGNIZED_MVP_RULE,
                    eaRecognizedMvp?.evidence ?: (
                        population + eligible.map {
                            DecisionEvidence.EaRecognition(
                                it.player.id,
                                it.eaRecognition.manOfTheMatch,
                            )
                        }
                    ),
                ),
            ),
        )
    }

    private fun contributions(players: List<PlayerMatchPerformance>): ContributionDecision {
        val goalScorers = players
            .filter { (it.attacking.goals ?: 0) > 0 }
            .sortedByDescending { it.attacking.goals ?: 0 }
            .map { PlayerContribution(it.player.id, it.attacking.goals ?: 0, it.attacking.assists ?: 0) }
        val assistProviders = players
            .filter { (it.attacking.assists ?: 0) > 0 }
            .sortedByDescending { it.attacking.assists ?: 0 }
            .map { PlayerContribution(it.player.id, it.attacking.goals ?: 0, it.attacking.assists ?: 0) }
        return ContributionDecision(
            goalScorers,
            assistProviders,
            CONTRIBUTIONS_RULE,
            listOf(populationEvidence(players)) + players.map(::attackingEvidence),
        )
    }

    private fun highlights(
        players: List<PlayerMatchPerformance>,
        teamPlayers: List<PlayerMatchPerformance>,
        metrics: TeamMetrics,
        bagreId: com.eafc26.discordstats.domain.match.PlayerId?,
    ): HighlightsDecision = HighlightsDecision(
        players = players
            .filter { it.rating != null }
            .sortedByDescending { it.rating!!.value }
            .take(HIGHLIGHT_LIMIT)
            .map { RatedHighlight(it.player.id, it.rating!!.value) },
        unfilteredPlayers = teamPlayers
            .filter { it.role is PlayerRole.Outfield && it.rating != null }
            .sortedByDescending { it.rating!!.value }
            .take(HIGHLIGHT_LIMIT)
            .map { RatedHighlight(it.player.id, it.rating!!.value) },
        teamAverageRating = metrics.averageRating,
        rule = HIGHLIGHTS_RULE,
        evidence = listOf(populationEvidence(teamPlayers)) + teamPlayers.map {
            DecisionEvidence.Rating(it.player.id, it.rating?.value, minimumRequired = null)
        } + listOfNotNull(
            bagreId?.let {
                DecisionEvidence.AwardCandidate(
                    playerId = it,
                    statisticallyEligible = true,
                    outfield = true,
                    excludedByAward = com.eafc26.discordstats.domain.interpretation.AwardType.BAGRE,
                )
            }
        ),
    )

    private fun eaRecognizedMvp(
        players: List<PlayerMatchPerformance>,
    ): EaRecognizedMvpDecision? {
        val winner = players.firstOrNull { it.eaRecognition.manOfTheMatch == true } ?: return null
        return EaRecognizedMvpDecision(
            playerId = winner.player.id,
            rating = winner.rating?.value,
            rule = EA_RECOGNIZED_MVP_RULE,
            evidence = players.map {
                DecisionEvidence.EaRecognition(it.player.id, it.eaRecognition.manOfTheMatch)
            },
        )
    }

    private fun bagrePerformance(decision: AwardDecision): BagrePerformanceDecision? {
        val playerId = decision.winnerId ?: return null
        val metrics = decision.metrics as? AwardMetrics.Bagre ?: return null
        return BagrePerformanceDecision(
            playerId = playerId,
            rating = metrics.rating,
            criticism = metrics.criticism,
            tackleSummary = metrics.tackleSummary,
            passingSummary = metrics.passingSummary,
            rule = BAGRE_PERFORMANCE_RULE,
            evidence = decision.evidence,
            recognition = metrics.recognition,
            peerAverageRating = metrics.peerAverageRating,
            ratingDeficit = metrics.ratingDeficit,
            peerAveragePassErrors = metrics.peerAveragePassErrors,
        )
    }

    private fun offensive(
        players: List<PlayerMatchPerformance>,
        result: ResultDecision,
        bagreId: com.eafc26.discordstats.domain.match.PlayerId?,
    ): List<OffensiveNarrativeDecision> {
        data class Candidate(
            val player: PlayerMatchPerformance,
            val shots: Int,
            val goals: Int,
            val category: OffensiveNarrativeCategory,
        )
        val goalDifference = result.ourScore.goals - result.opponentScore.goals
        val candidates = players.mapNotNull { player ->
            val shots = player.attacking.shots ?: return@mapNotNull null
            if (shots < MIN_OFFENSIVE_SHOTS) return@mapNotNull null
            val goals = player.attacking.goals ?: 0
            Candidate(player, shots, goals, classifyOffensive(shots, goals, goalDifference))
        }
        val bestByCategory = candidates.groupBy { it.category }.mapValues { (_, candidates) ->
            candidates.maxWithOrNull(
                    compareBy<Candidate> { it.shots }
                        .thenBy { it.goals }
                        .thenByDescending { sourceName(it.player) }
            )!!
        }
        val sharedEvidence = players.map(::attackingEvidence) +
            DecisionEvidence.Scoreboard(result.ourScore.goals, result.opponentScore.goals) +
            listOfNotNull(
                bagreId?.let {
                    DecisionEvidence.AwardCandidate(
                        it,
                        statisticallyEligible = true,
                        outfield = true,
                        excludedByAward = com.eafc26.discordstats.domain.interpretation.AwardType.BAGRE,
                    )
                }
            )
        return OFFENSIVE_PRIORITY.mapNotNull { category ->
            val winner = bestByCategory[category] ?: return@mapNotNull null
            OffensiveNarrativeDecision(
                winner.player.player.id,
                winner.shots,
                winner.goals,
                category,
                OFFENSIVE_RULE,
                sharedEvidence,
            )
        }
    }

    private fun classifyOffensive(
        shots: Int,
        goals: Int,
        goalDifference: Int,
    ): OffensiveNarrativeCategory {
        val conversion = if (shots > 0) goals.toDouble() / shots else 0.0
        return when {
            (goalDifference == 0 || goalDifference == -1) &&
                shots >= MIN_OFFENSIVE_SHOTS && conversion < 0.50 ->
                OffensiveNarrativeCategory.COULD_HAVE_DECIDED
            goals == 0 && shots >= MIN_OFFENSIVE_SHOTS ->
                OffensiveNarrativeCategory.LACKED_COMPOSURE
            goalDifference > 0 && goals >= 2 && conversion >= 0.50 ->
                OffensiveNarrativeCategory.DECISIVE
            shots >= MIN_OFFENSIVE_SHOTS && goals >= 1 && conversion < 0.35 ->
                OffensiveNarrativeCategory.FELL_SHORT
            else -> OffensiveNarrativeCategory.CONSTANT_THREAT
        }
    }

    private fun behindThePlay(players: List<PlayerMatchPerformance>): BehindThePlayDecision? {
        data class Candidate(
            val player: PlayerMatchPerformance,
            val secondAssists: Int,
            val throughPasses: Int,
        )
        val winner = players.mapNotNull { player ->
            val secondAssists = player.advanced.secondAssists
            if (secondAssists <= 0) return@mapNotNull null
            Candidate(player, secondAssists, player.advanced.throughPasses)
        }.maxWithOrNull(
            compareBy<Candidate> { it.secondAssists }
                .thenBy { it.throughPasses }
                .thenBy { it.player.rating?.value ?: BigDecimal.ZERO }
                .thenBy { sourceName(it.player) }
        ) ?: return null

        return BehindThePlayDecision(
            winner.player.player.id,
            winner.secondAssists,
            winner.throughPasses,
            winner.player.rating?.value,
            BEHIND_THE_PLAY_RULE,
            players.map(::advancedEvidence),
        )
    }

    private fun oneOnOne(players: List<PlayerMatchPerformance>): OneOnOneDecision? {
        data class Candidate(
            val player: PlayerMatchPerformance,
            val beats: Int,
        )
        val winner = players.mapNotNull { player ->
            val beats = player.advanced.beats
            if (beats < MIN_BEATS_FOR_ONE_ON_ONE) return@mapNotNull null
            Candidate(player, beats)
        }.maxWithOrNull(
            compareBy<Candidate> { it.beats }
                .thenBy { it.player.rating?.value ?: BigDecimal.ZERO }
                .thenBy { sourceName(it.player) }
        ) ?: return null

        return OneOnOneDecision(
            winner.player.player.id,
            winner.beats,
            winner.player.rating?.value,
            ONE_ON_ONE_RULE,
            players.map(::advancedEvidence),
        )
    }

    private fun redCard(players: List<PlayerMatchPerformance>): RedCardDecision? {
        val winner = players
            .filter { (it.discipline.redCards ?: 0) > 0 }
            .maxWithOrNull(
                compareByDescending<PlayerMatchPerformance> {
                    it.rating?.value ?: BigDecimal.valueOf(Double.MAX_VALUE)
                }.thenBy { sourceName(it) }
            ) ?: return null
        return RedCardDecision(
            winner.player.id,
            winner.discipline.redCards ?: 0,
            RED_CARD_RULE,
            players.flatMap {
                listOf(
                    DecisionEvidence.Discipline(it.player.id, it.discipline.redCards),
                    DecisionEvidence.Rating(it.player.id, it.rating?.value, null),
                )
            },
        )
    }

    private fun passPrecision(
        players: List<PlayerMatchPerformance>,
        bagreId: com.eafc26.discordstats.domain.match.PlayerId?,
    ): PassPrecisionDecision? {
        data class Candidate(
            val player: PlayerMatchPerformance,
            val completed: Int,
            val attempted: Int,
            val accuracy: Int,
        )
        val winner = players.mapNotNull {
            val attempted = it.passing.attempted ?: return@mapNotNull null
            if (attempted < MIN_PRECISION_ATTEMPTS) return@mapNotNull null
            val completed = it.passing.completed ?: 0
            Candidate(it, completed, attempted, completed * 100 / attempted)
        }.maxWithOrNull(compareBy<Candidate> { it.accuracy }.thenBy { it.attempted })
            ?: return null
        return PassPrecisionDecision(
            winner.player.player.id,
            winner.completed,
            winner.attempted,
            winner.accuracy,
            PASS_PRECISION_RULE,
            players.map(::passingEvidence) + listOfNotNull(
                bagreId?.let {
                    DecisionEvidence.AwardCandidate(
                        it,
                        statisticallyEligible = true,
                        outfield = true,
                        excludedByAward = com.eafc26.discordstats.domain.interpretation.AwardType.BAGRE,
                    )
                }
            ),
        )
    }

    private fun lostMail(players: List<PlayerMatchPerformance>): LostMailDecision? {
        val samples = players.mapNotNull {
            val attempted = it.passing.attempted ?: return@mapNotNull null
            val completed = it.passing.completed ?: return@mapNotNull null
            if (attempted <= 0) return@mapNotNull null
            PassingSample(it, completed.coerceAtMost(attempted), attempted)
        }
        val totalAttempts = samples.sumOf { it.attempted }
        if (totalAttempts == 0) return null
        val totalCompleted = samples.sumOf { it.completed }
        val teamAccuracy = totalCompleted * 100 / totalAttempts
        val winner = samples.mapNotNull {
            if (it.attempted < MIN_LOST_MAIL_ATTEMPTS) return@mapNotNull null
            val accuracy = it.completed * 100 / it.attempted
            val delta = teamAccuracy - accuracy
            val highVolume = it.attempted >= HIGH_VOLUME_ATTEMPTS
            if (highVolume && (accuracy >= MAX_LOST_MAIL_ACCURACY || delta < MIN_TEAM_DELTA)) {
                return@mapNotNull null
            }
            if (!highVolume && accuracy > LOW_VOLUME_MAX_ACCURACY) return@mapNotNull null
            LostMailCandidate(it, accuracy, delta, highVolume)
        }.minWithOrNull(
            compareBy<LostMailCandidate> { it.accuracy }
                .thenBy { if (it.highVolume) 0 else 1 }
                .thenByDescending { it.sample.attempted - it.sample.completed }
                .thenBy { sourceName(it.sample.player) }
        ) ?: return null
        return LostMailDecision(
            winner.sample.player.player.id,
            winner.sample.completed,
            winner.sample.attempted,
            winner.accuracy,
            teamAccuracy,
            winner.delta,
            LOST_MAIL_RULE,
            players.map(::passingEvidence) + DecisionEvidence.TeamPassingPerformance(
                totalCompleted,
                totalAttempts,
                teamAccuracy,
            ),
        )
    }

    private fun goalkeeper(players: Collection<PlayerMatchPerformance>): GoalkeeperDecision? {
        val player = players
            .filter { it.role == PlayerRole.Goalkeeper }
            .maxByOrNull { it.participation.duration?.seconds ?: 0L }
            ?: return null
        val stats = player.goalkeeping
        val saves = stats?.saves ?: 0
        val conceded = stats?.goalsConceded ?: 0
        val rating = player.rating?.value?.toDouble() ?: 0.0
        val breakdown = stats?.saveBreakdown
        val impact = (breakdown?.goodDirection ?: 0) +
            (breakdown?.reflex ?: 0) +
            (breakdown?.parry ?: 0)
        val archetype = when {
            saves <= 1 && conceded <= 1 -> GoalkeeperArchetype.QUIET
            rating >= 8.0 && (conceded == 0 || impact >= 3) -> GoalkeeperArchetype.WALL
            rating < 6.0 -> GoalkeeperArchetype.POOR
            saves >= 6 -> GoalkeeperArchetype.UNDER_SIEGE
            else -> GoalkeeperArchetype.SOLID
        }
        val variant = goalkeeperNarrativeVariant(
            archetype,
            rating,
            breakdown?.reflex ?: 0,
            breakdown?.parry ?: 0,
            breakdown?.crosses ?: 0,
            breakdown?.goodDirection ?: 0,
        )
        return GoalkeeperDecision(
            player.player.id,
            saves,
            conceded,
            archetype,
            variant,
            GOALKEEPER_RULE,
            listOf(
                DecisionEvidence.GoalkeepingPerformance(
                    player.player.id,
                    stats?.saves,
                    stats?.goalsConceded,
                    player.rating?.value,
                    breakdown?.goodDirection,
                    breakdown?.reflex,
                    breakdown?.parry,
                    breakdown?.crosses,
                )
            ),
        )
    }

    private fun goalkeeperNarrativeVariant(
        archetype: GoalkeeperArchetype,
        rating: Double,
        reflex: Int,
        parry: Int,
        crosses: Int,
        goodDirection: Int,
    ): GoalkeeperNarrativeVariant {
        if (archetype == GoalkeeperArchetype.POOR) {
            return when {
                rating < 4.5 -> GoalkeeperNarrativeVariant.POOR_SEVERE
                rating < 5.5 -> GoalkeeperNarrativeVariant.POOR_MODERATE
                else -> GoalkeeperNarrativeVariant.POOR_MILD
            }
        }
        if (archetype != GoalkeeperArchetype.WALL &&
            archetype != GoalkeeperArchetype.UNDER_SIEGE
        ) {
            return GoalkeeperNarrativeVariant.DEFAULT
        }
        val best = maxOf(reflex, parry, goodDirection)
        return when {
            best < 3 && crosses < 3 -> GoalkeeperNarrativeVariant.DEFAULT
            reflex == best && reflex >= 3 -> GoalkeeperNarrativeVariant.REFLEX
            parry == best && parry >= 3 -> GoalkeeperNarrativeVariant.PARRY
            goodDirection >= 3 -> GoalkeeperNarrativeVariant.GOOD_DIRECTION
            crosses >= 3 -> GoalkeeperNarrativeVariant.CROSS
            else -> GoalkeeperNarrativeVariant.DEFAULT
        }
    }

    private fun attackingEvidence(player: PlayerMatchPerformance) =
        DecisionEvidence.AttackingContribution(
            player.player.id,
            player.attacking.goals,
            player.attacking.assists,
            player.attacking.shots,
        )

    private fun passingEvidence(player: PlayerMatchPerformance) =
        DecisionEvidence.PassingPerformance(
            player.player.id,
            player.passing.completed,
            player.passing.attempted,
        )

    private fun advancedEvidence(player: PlayerMatchPerformance) =
        DecisionEvidence.AdvancedPerformance(
            player.player.id,
            player.advanced.secondAssists,
            player.advanced.throughPasses,
            player.advanced.dribblesCompleted,
            player.advanced.beats,
        )

    private fun sourceName(player: PlayerMatchPerformance): String =
        player.player.platformName?.value ?: ""

    private fun populationEvidence(players: Collection<PlayerMatchPerformance>) =
        DecisionEvidence.PlayerPopulation(
            totalPlayers = players.size,
            statisticallyEligiblePlayers = players.size,
            eligibleOutfieldPlayers = players.count { it.role is PlayerRole.Outfield },
        )

    private data class PassingSample(
        val player: PlayerMatchPerformance,
        val completed: Int,
        val attempted: Int,
    )

    private data class LostMailCandidate(
        val sample: PassingSample,
        val accuracy: Int,
        val delta: Int,
        val highVolume: Boolean,
    )

    companion object {
        private const val HIGHLIGHT_LIMIT = 3
        private const val MIN_OFFENSIVE_SHOTS = 5
        private const val MIN_BEATS_FOR_ONE_ON_ONE = 3
        private const val MIN_PRECISION_ATTEMPTS = 10
        private const val MIN_LOST_MAIL_ATTEMPTS = 3
        private const val HIGH_VOLUME_ATTEMPTS = 10
        private const val MAX_LOST_MAIL_ACCURACY = 75
        private const val MIN_TEAM_DELTA = 5
        private const val LOW_VOLUME_MAX_ACCURACY = 33

        val CONTRIBUTIONS_RULE = RuleReference(RuleId("match.player-contributions"), 1)
        val HIGHLIGHTS_RULE = RuleReference(RuleId("match.rated-highlights"), 1)
        val BAGRE_PERFORMANCE_RULE = RuleReference(RuleId("narrative.bagre-performance"), 1)
        val OFFENSIVE_RULE = RuleReference(RuleId("narrative.offensive-performance"), 1)
        val BEHIND_THE_PLAY_RULE = RuleReference(RuleId("narrative.behind-the-play"), 1)
        val ONE_ON_ONE_RULE = RuleReference(RuleId("narrative.one-on-one"), 1)
        val RED_CARD_RULE = RuleReference(RuleId("narrative.red-card"), 1)
        val PASS_PRECISION_RULE = RuleReference(RuleId("award.pass-precision"), 1)
        val LOST_MAIL_RULE = RuleReference(RuleId("award.lost-mail"), 1)
        val GOALKEEPER_RULE = RuleReference(RuleId("narrative.goalkeeper"), 1)
        val EA_RECOGNIZED_MVP_RULE = RuleReference(RuleId("recognition.ea-mvp"), 1)

        private val OFFENSIVE_PRIORITY = listOf(
            OffensiveNarrativeCategory.DECISIVE,
            OffensiveNarrativeCategory.COULD_HAVE_DECIDED,
            OffensiveNarrativeCategory.FELL_SHORT,
            OffensiveNarrativeCategory.LACKED_COMPOSURE,
            OffensiveNarrativeCategory.CONSTANT_THREAT,
        )
    }
}
