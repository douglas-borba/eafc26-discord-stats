package com.eafc26.discordstats.application.interpretation

import com.eafc26.discordstats.domain.interpretation.AccuracySummary
import com.eafc26.discordstats.domain.interpretation.AwardDecision
import com.eafc26.discordstats.domain.interpretation.AwardDecisionReason
import com.eafc26.discordstats.domain.interpretation.AwardMetrics
import com.eafc26.discordstats.domain.interpretation.AwardType
import com.eafc26.discordstats.domain.interpretation.BagreCriticism
import com.eafc26.discordstats.domain.interpretation.DecisionEvidence
import com.eafc26.discordstats.domain.interpretation.EligibilityInterpretation
import com.eafc26.discordstats.domain.interpretation.RuleId
import com.eafc26.discordstats.domain.interpretation.RuleReference
import com.eafc26.discordstats.domain.match.PlayerMatchPerformance
import java.math.BigDecimal

/**
 * Selects a negative recognition only after a player has independently
 * qualified through factual poor-performance evidence. Being the lowest-rated
 * eligible player is deliberately not sufficient.
 */
class BagreEvaluator {

    fun evaluate(
        players: Collection<PlayerMatchPerformance>,
        eligibility: EligibilityInterpretation,
    ): AwardDecision {
        val pool = AwardCandidatePool.outfield(players, eligibility)
        val evidence = pool.evidence + pool.candidates.flatMap(::performanceEvidence)
        val ratedCandidates = pool.candidates.filter { it.rating?.value?.let { rating -> rating >= MINIMUM_RATING } == true }
        val qualified = ratedCandidates.map(::qualification).filter { it.qualifies }
        val winner = qualified.minWithOrNull(QUALIFIED_CANDIDATE_ORDER)

        return AwardDecision(
            type = AwardType.BAGRE,
            winnerId = winner?.player?.player?.id,
            reason = when {
                winner != null -> AwardDecisionReason.QUALIFIED_NEGATIVE_PERFORMANCE
                ratedCandidates.isEmpty() -> AwardDecisionReason.NO_ELIGIBLE_CANDIDATE
                else -> AwardDecisionReason.NO_QUALIFIED_CANDIDATE
            },
            rule = RULE,
            evidence = evidence,
            metrics = winner?.let(::metrics),
        )
    }

    private fun qualification(player: PlayerMatchPerformance): QualifiedCandidate {
        val rating = requireNotNull(player.rating).value
        val passing = accuracySummary(player.passing.completed, player.passing.attempted)
        val tackling = accuracySummary(player.defending.tacklesCompleted, player.defending.tacklesAttempted)
        val negativeSignals = buildList {
            ratingSignal(rating)?.let(::add)
            passingSignal(passing)?.let(::add)
            tacklingSignal(tackling)?.let(::add)
        }
        val sportingSignals = negativeSignals.filter { it.criticism != BagreCriticism.RATING }
        val positiveGuard = hasPositiveGuard(player, rating)
        val qualifies = !positiveGuard && when {
            rating <= VERY_LOW_RATING -> true
            rating <= LOW_RATING && sportingSignals.isNotEmpty() -> true
            rating <= MULTI_SIGNAL_RATING && sportingSignals.size >= 2 -> true
            else -> false
        }
        val primarySignal = negativeSignals
            .sortedWith(
                compareByDescending<NegativeSignal> { it.severity }
                    .thenByDescending { it.summary?.attempted ?: 0 }
                    .thenBy { it.criticism.ordinal },
            )
            .firstOrNull()

        return QualifiedCandidate(
            player = player,
            rating = rating,
            qualifyingPassing = passingSignal(passing)?.summary,
            qualifyingTackling = tacklingSignal(tackling)?.summary,
            primaryCriticism = primarySignal?.criticism ?: BagreCriticism.RATING,
            severity = negativeSignals.sumOf { it.severity },
            strongestSignalSeverity = negativeSignals.maxOfOrNull { it.severity } ?: 0,
            negativeEvidenceVolume = sportingSignals.sumOf { it.summary?.attempted ?: 0 },
            qualifies = qualifies,
        )
    }

    private fun metrics(candidate: QualifiedCandidate) = AwardMetrics.Bagre(
        rating = candidate.rating,
        severity = candidate.severity,
        criticism = candidate.primaryCriticism,
        tackleSummary = candidate.qualifyingTackling,
        passingSummary = candidate.qualifyingPassing,
    )

    private fun performanceEvidence(player: PlayerMatchPerformance): List<DecisionEvidence> = listOf(
        DecisionEvidence.Rating(player.player.id, player.rating?.value, MINIMUM_RATING),
        DecisionEvidence.PassingPerformance(player.player.id, player.passing.completed, player.passing.attempted),
        DecisionEvidence.DefensivePerformance(
            player.player.id,
            player.defending.tacklesCompleted,
            player.defending.tacklesAttempted,
            defensiveImpactScore = null,
        ),
        DecisionEvidence.AttackingContribution(
            player.player.id,
            player.attacking.goals,
            player.attacking.assists,
            player.attacking.shots,
        ),
        DecisionEvidence.EaRecognition(player.player.id, player.eaRecognition.manOfTheMatch),
    )

    private fun hasPositiveGuard(player: PlayerMatchPerformance, rating: BigDecimal): Boolean =
        rating >= POSITIVE_RATING_GUARD ||
            player.eaRecognition.manOfTheMatch == true ||
            (player.attacking.goals ?: 0) + (player.attacking.assists ?: 0) >= STRONG_DIRECT_CONTRIBUTIONS

    private fun ratingSignal(rating: BigDecimal): NegativeSignal? = when {
        rating <= VERY_LOW_RATING -> NegativeSignal(BagreCriticism.RATING, severity = 3, summary = null)
        rating <= LOW_RATING -> NegativeSignal(BagreCriticism.RATING, severity = 1, summary = null)
        else -> null
    }

    private fun passingSignal(summary: AccuracySummary?): NegativeSignal? = when {
        summary == null -> null
        summary.attempted >= VERY_POOR_PASSING_MIN_ATTEMPTS &&
            summary.accuracyPercent <= VERY_POOR_PASSING_MAX_PERCENT ->
            NegativeSignal(BagreCriticism.PASSING, severity = 2, summary)
        summary.attempted >= POOR_PASSING_MIN_ATTEMPTS &&
            summary.accuracyPercent <= POOR_PASSING_MAX_PERCENT ->
            NegativeSignal(BagreCriticism.PASSING, severity = 1, summary)
        else -> null
    }

    private fun tacklingSignal(summary: AccuracySummary?): NegativeSignal? = when {
        summary == null -> null
        summary.attempted >= VERY_POOR_TACKLING_MIN_ATTEMPTS &&
            summary.accuracyPercent <= VERY_POOR_TACKLING_MAX_PERCENT ->
            NegativeSignal(BagreCriticism.TACKLING, severity = 2, summary)
        summary.attempted >= POOR_TACKLING_MIN_ATTEMPTS &&
            summary.accuracyPercent <= POOR_TACKLING_MAX_PERCENT ->
            NegativeSignal(BagreCriticism.TACKLING, severity = 1, summary)
        else -> null
    }

    private fun accuracySummary(completed: Int?, attempted: Int?): AccuracySummary? {
        val attempts = attempted?.takeIf { it > 0 } ?: return null
        val made = completed ?: 0
        return AccuracySummary(made, attempts, made * 100 / attempts)
    }

    private data class NegativeSignal(
        val criticism: BagreCriticism,
        val severity: Int,
        val summary: AccuracySummary?,
    )

    private data class QualifiedCandidate(
        val player: PlayerMatchPerformance,
        val rating: BigDecimal,
        val qualifyingPassing: AccuracySummary?,
        val qualifyingTackling: AccuracySummary?,
        val primaryCriticism: BagreCriticism,
        val severity: Int,
        val strongestSignalSeverity: Int,
        val negativeEvidenceVolume: Int,
        val qualifies: Boolean,
    )

    companion object {
        val MINIMUM_RATING: BigDecimal = BigDecimal("5.0")
        val VERY_LOW_RATING: BigDecimal = BigDecimal("5.5")
        val LOW_RATING: BigDecimal = BigDecimal("6.0")
        val MULTI_SIGNAL_RATING: BigDecimal = BigDecimal("6.5")
        val POSITIVE_RATING_GUARD: BigDecimal = BigDecimal("8.5")

        const val STRONG_DIRECT_CONTRIBUTIONS = 2
        const val POOR_PASSING_MIN_ATTEMPTS = 10
        const val POOR_PASSING_MAX_PERCENT = 60
        const val VERY_POOR_PASSING_MIN_ATTEMPTS = 15
        const val VERY_POOR_PASSING_MAX_PERCENT = 50
        const val POOR_TACKLING_MIN_ATTEMPTS = 5
        const val POOR_TACKLING_MAX_PERCENT = 40
        const val VERY_POOR_TACKLING_MIN_ATTEMPTS = 8
        const val VERY_POOR_TACKLING_MAX_PERCENT = 25

        val RULE = RuleReference(RuleId("award.bagre"), version = 2)

        private val QUALIFIED_CANDIDATE_ORDER = compareByDescending<QualifiedCandidate> { it.severity }
            .thenBy { it.rating }
            .thenByDescending { it.strongestSignalSeverity }
            .thenByDescending { it.negativeEvidenceVolume }
            .thenBy { it.player.player.id.value }
    }
}
