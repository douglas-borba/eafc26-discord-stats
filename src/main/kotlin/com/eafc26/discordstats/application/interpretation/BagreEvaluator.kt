package com.eafc26.discordstats.application.interpretation

import com.eafc26.discordstats.domain.interpretation.AccuracySummary
import com.eafc26.discordstats.domain.interpretation.AwardDecision
import com.eafc26.discordstats.domain.interpretation.AwardDecisionReason
import com.eafc26.discordstats.domain.interpretation.AwardMetrics
import com.eafc26.discordstats.domain.interpretation.AwardType
import com.eafc26.discordstats.domain.interpretation.BagreCriticism
import com.eafc26.discordstats.domain.interpretation.DecisionEvidence
import com.eafc26.discordstats.domain.interpretation.EligibilityInterpretation
import com.eafc26.discordstats.domain.interpretation.NegativeRecognition
import com.eafc26.discordstats.domain.interpretation.RuleId
import com.eafc26.discordstats.domain.interpretation.RuleReference
import com.eafc26.discordstats.domain.match.PlayerMatchPerformance
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * A single, rating-led negative recognition.
 *
 * Candidate selection and qualification are intentionally separate: the
 * lowest-rated eligible outfield player is selected first, then that same
 * player is classified. Supporting statistics can only qualify a borderline
 * rating; they can never select a different player.
 */
class BagreEvaluator {

    fun evaluate(
        players: Collection<PlayerMatchPerformance>,
        eligibility: EligibilityInterpretation,
    ): AwardDecision {
        val pool = AwardCandidatePool.outfield(players, eligibility)
        val ratedCandidates = pool.candidates
            .filter { it.rating != null }
            .sortedWith(compareBy<PlayerMatchPerformance> { it.rating!!.value }.thenBy { it.player.id.value })
        val evidence = pool.evidence + pool.candidates.flatMap(::performanceEvidence)
        val candidate = ratedCandidates.firstOrNull()

        val result = candidate?.let { classify(it, ratedCandidates) }
        return AwardDecision(
            type = AwardType.BAGRE,
            winnerId = result?.candidate?.player?.id,
            reason = when {
                result != null -> AwardDecisionReason.QUALIFIED_NEGATIVE_PERFORMANCE
                ratedCandidates.isEmpty() -> AwardDecisionReason.NO_ELIGIBLE_CANDIDATE
                else -> AwardDecisionReason.NO_QUALIFIED_CANDIDATE
            },
            rule = RULE,
            evidence = evidence,
            metrics = result?.let(::metrics),
        )
    }

    private fun classify(
        candidate: PlayerMatchPerformance,
        ratedCandidates: List<PlayerMatchPerformance>,
    ): NegativeResult? {
        val rating = requireNotNull(candidate.rating).value
        return when {
            rating < BAGRE_RATING -> NegativeResult(
                candidate = candidate,
                recognition = NegativeRecognition.BAGRE,
                criticism = BagreCriticism.RATING,
            )
            rating < AUTOMATIC_LOW_PERFORMANCE_RATING -> NegativeResult(
                candidate = candidate,
                recognition = NegativeRecognition.LOW_PERFORMANCE,
                criticism = BagreCriticism.RATING,
            )
            rating >= NO_NEGATIVE_RECOGNITION_RATING -> null
            else -> classifyBorderline(candidate, ratedCandidates)
        }
    }

    private fun classifyBorderline(
        candidate: PlayerMatchPerformance,
        ratedCandidates: List<PlayerMatchPerformance>,
    ): NegativeResult? {
        val peers = ratedCandidates.filterNot { it.player.id == candidate.player.id }
        if (peers.isEmpty()) return null

        val peerAverageRating = peers.map { requireNotNull(it.rating).value }.average()
        val ratingDeficit = peerAverageRating.subtract(requireNotNull(candidate.rating).value)
        if (ratingDeficit >= STRONG_RATING_DEFICIT) {
            return NegativeResult(
                candidate = candidate,
                recognition = NegativeRecognition.LOW_PERFORMANCE,
                criticism = BagreCriticism.RATING,
                peerAverageRating = peerAverageRating,
                ratingDeficit = ratingDeficit,
            )
        }
        if (ratingDeficit < MODERATE_RATING_DEFICIT) return null

        val passing = passErrorEvidence(candidate, peers)
        if (passing != null) {
            return NegativeResult(
                candidate = candidate,
                recognition = NegativeRecognition.LOW_PERFORMANCE,
                criticism = BagreCriticism.PASSING,
                passingSummary = passing.candidateSummary,
                peerAverageRating = peerAverageRating,
                ratingDeficit = ratingDeficit,
                peerAveragePassErrors = passing.peerAverageErrors,
            )
        }

        val tackling = tacklingEvidence(candidate)
        if (tackling != null) {
            return NegativeResult(
                candidate = candidate,
                recognition = NegativeRecognition.LOW_PERFORMANCE,
                criticism = BagreCriticism.TACKLING,
                tackleSummary = tackling,
                peerAverageRating = peerAverageRating,
                ratingDeficit = ratingDeficit,
            )
        }
        return null
    }

    /**
     * Passing support requires a valid, non-trivial candidate sample plus both
     * a material absolute error count and a material excess over valid peers.
     * This deliberately rejects differences such as four errors versus three.
     */
    private fun passErrorEvidence(
        candidate: PlayerMatchPerformance,
        peers: List<PlayerMatchPerformance>,
    ): PassingEvidence? {
        val candidateSummary = accuracySummary(candidate.passing.completed, candidate.passing.attempted) ?: return null
        val candidateErrors = passErrors(candidate.passing.completed, candidate.passing.attempted) ?: return null
        val peerErrors = peers.mapNotNull { peer ->
            val attempts = peer.passing.attempted ?: return@mapNotNull null
            passErrors(peer.passing.completed, attempts)
                ?.takeIf { attempts >= MATERIAL_PASS_ERROR_MIN_ATTEMPTS }
        }
        if (
            candidateSummary.attempted < MATERIAL_PASS_ERROR_MIN_ATTEMPTS ||
            candidateErrors < MATERIAL_PASS_ERROR_MIN_ERRORS ||
            peerErrors.isEmpty()
        ) return null

        val peerAverageErrors = peerErrors.map(Int::toBigDecimal).average()
        if (candidateErrors.toBigDecimal() < peerAverageErrors.add(MATERIAL_PASS_ERROR_EXCESS)) return null
        return PassingEvidence(candidateSummary, peerAverageErrors)
    }

    private fun tacklingEvidence(candidate: PlayerMatchPerformance): AccuracySummary? {
        val summary = accuracySummary(candidate.defending.tacklesCompleted, candidate.defending.tacklesAttempted) ?: return null
        return summary.takeIf {
            (it.attempted >= SEVERE_TACKLE_MIN_ATTEMPTS && it.accuracyPercent <= SEVERE_TACKLE_MAX_PERCENT) ||
                (it.attempted >= POOR_TACKLE_MIN_ATTEMPTS && it.accuracyPercent <= POOR_TACKLE_MAX_PERCENT)
        }
    }

    private fun metrics(result: NegativeResult) = AwardMetrics.Bagre(
        rating = requireNotNull(result.candidate.rating).value,
        severity = LEGACY_SEVERITY,
        criticism = result.criticism,
        tackleSummary = result.tackleSummary,
        passingSummary = result.passingSummary,
        recognition = result.recognition,
        peerAverageRating = result.peerAverageRating,
        ratingDeficit = result.ratingDeficit,
        peerAveragePassErrors = result.peerAveragePassErrors,
    )

    private fun performanceEvidence(player: PlayerMatchPerformance): List<DecisionEvidence> = listOf(
        DecisionEvidence.Rating(player.player.id, player.rating?.value, null),
        DecisionEvidence.PassingPerformance(player.player.id, player.passing.completed, player.passing.attempted),
        DecisionEvidence.DefensivePerformance(
            player.player.id,
            player.defending.tacklesCompleted,
            player.defending.tacklesAttempted,
            defensiveImpactScore = null,
        ),
    )

    private fun accuracySummary(completed: Int?, attempted: Int?): AccuracySummary? {
        if (attempted == null || completed == null || attempted <= 0 || completed < 0 || completed > attempted) return null
        return AccuracySummary(completed, attempted, completed * 100 / attempted)
    }

    private fun passErrors(completed: Int?, attempted: Int?): Int? {
        if (attempted == null || completed == null || attempted < 0 || completed < 0 || completed > attempted) return null
        return attempted - completed
    }

    private fun List<BigDecimal>.average(): BigDecimal = reduce(BigDecimal::add)
        .divide(size.toBigDecimal(), AVERAGE_SCALE, RoundingMode.HALF_UP)

    private data class NegativeResult(
        val candidate: PlayerMatchPerformance,
        val recognition: NegativeRecognition,
        val criticism: BagreCriticism,
        val tackleSummary: AccuracySummary? = null,
        val passingSummary: AccuracySummary? = null,
        val peerAverageRating: BigDecimal? = null,
        val ratingDeficit: BigDecimal? = null,
        val peerAveragePassErrors: BigDecimal? = null,
    )

    private data class PassingEvidence(
        val candidateSummary: AccuracySummary,
        val peerAverageErrors: BigDecimal,
    )

    companion object {
        val BAGRE_RATING: BigDecimal = BigDecimal("7.0")
        val AUTOMATIC_LOW_PERFORMANCE_RATING: BigDecimal = BigDecimal("7.5")
        val NO_NEGATIVE_RECOGNITION_RATING: BigDecimal = BigDecimal("8.0")
        val STRONG_RATING_DEFICIT: BigDecimal = BigDecimal("1.0")
        val MODERATE_RATING_DEFICIT: BigDecimal = BigDecimal("0.5")
        val MATERIAL_PASS_ERROR_EXCESS: BigDecimal = BigDecimal("4")

        const val MATERIAL_PASS_ERROR_MIN_ATTEMPTS = 10
        const val MATERIAL_PASS_ERROR_MIN_ERRORS = 6
        const val POOR_TACKLE_MIN_ATTEMPTS = 5
        const val POOR_TACKLE_MAX_PERCENT = 40
        const val SEVERE_TACKLE_MIN_ATTEMPTS = 8
        const val SEVERE_TACKLE_MAX_PERCENT = 25
        const val LEGACY_SEVERITY = 0
        const val AVERAGE_SCALE = 2

        val RULE = RuleReference(RuleId("award.bagre"), version = 3)
    }
}
