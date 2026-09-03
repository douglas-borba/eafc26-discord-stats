package com.eafc26.discordstats.explorer

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Ranks UNKNOWN aggregate transport codes by reverse-engineering value.
 * It deliberately emits statistical evidence only: no sporting label is ever
 * inferred or written back to [AdvancedStatsCodeRegistry].
 */
class NovelMetricDiscoveryEngine(
    private val thresholds: Thresholds = Thresholds(),
) {
    data class Thresholds(
        val minimumObservations: Int = 5,
        val minimumMatches: Int = 2,
        val minimumPlayers: Int = 2,
        val minimumActive: Int = 3,
        val highObservations: Int = 20,
        val highMatches: Int = 4,
        val highPlayers: Int = 4,
        val duplicatePearson: Double = 0.985,
        val relatedPearson: Double = 0.70,
        val duplicateEquality: Double = 0.98,
        val duplicateOverlap: Double = 0.80,
        val familyPearson: Double = 0.97,
        val familyEquality: Double = 0.95,
        val familyOverlap: Double = 0.70,
        val maximumEvidenceRows: Int = 20,
    )

    data class KnownRelation(
        val name: String,
        val observations: Int,
        val exactEqualityRate: Double?,
        val pearson: Double?,
        val spearman: Double?,
        val nonZeroOverlap: Double?,
        val pCandidateActiveGivenKnownActive: Double?,
        val pKnownActiveGivenCandidateActive: Double?,
        val stableRatio: Boolean?,
        val classification: String,
    )

    data class EvidenceRow(
        val matchId: String,
        val timestamp: String,
        val playerId: String,
        val playerName: String?,
        val completion: String?,
        val value: Int,
        val knownMetrics: Map<String, Int?>,
    )

    data class Candidate(
        val aggregateIndex: Int,
        val code: Int,
        val registryStatus: String,
        val observations: Int,
        val activeObservations: Int,
        val activeRate: Double?,
        val matches: Int,
        val players: Int,
        val min: Int,
        val max: Int,
        val mean: Double?,
        val median: Double?,
        val distinctValues: Int,
        val noveltyScore: Double,
        val priority: String,
        val classification: String,
        val closestKnownRelation: KnownRelation?,
        val warnings: List<String>,
        val familyId: String?,
        val familyRepresentative: Boolean,
    )

    data class UnknownFamily(
        val id: String,
        val aggregateIndex: Int,
        val representativeCode: Int,
        val relatedCodes: List<Int>,
        val relationship: String,
    )

    data class CandidateDetail(
        val candidate: Candidate,
        val knownRelations: List<KnownRelation>,
        val relatedFamily: UnknownFamily?,
        val highValues: List<EvidenceRow>,
        val lowNonZeroValues: List<EvidenceRow>,
        val zeroValues: List<EvidenceRow>,
    )

    data class Result(
        val rawMatchesAnalyzed: Int,
        val playerMatchObservations: Int,
        val candidates: List<Candidate>,
        val families: List<UnknownFamily>,
    )

    private data class WorkingCandidate(
        val ref: AdvancedStatsDiscoveryEngine.CodeRef,
        val samples: List<AdvancedStatsDiscoveryEngine.AggregateSample>,
        val values: List<Int>,
        val relations: List<KnownRelation>,
    )

    private val controls = listOf(
        "goals", "assists", "shots", "passesAttempted", "passesCompleted", "tacklesAttempted", "tacklesCompleted",
        "agg0[112] Beats", "agg0[115] Pre-assists", "agg0[152] Through passes",
    )

    fun analyze(samples: List<AdvancedStatsDiscoveryEngine.AggregateSample>): Result {
        val rawCodes = samples.flatMap { sample ->
            sample.sparseValues.keys.map { AdvancedStatsDiscoveryEngine.CodeRef(sample.aggregateIndex, it) }
        }.toSet().filter { AdvancedStatsCodeRegistry.lookup(it.aggregateIndex, it.code).confidence == CodeConfidence.UNKNOWN }

        val working = rawCodes.map { ref ->
            val matching = samples.filter { it.aggregateIndex == ref.aggregateIndex }
            WorkingCandidate(ref, matching, matching.map { it.sparseValues[ref.code] ?: 0 }, emptyList())
        }.map { item -> item.copy(relations = controls.mapNotNull { controlRelation(item, it) }) }

        val familyEdges = familyEdges(working)
        val families = families(working, familyEdges)
        val candidates = working.map { item -> candidate(item, families.firstOrNull { item.ref.code in it.relatedCodes && item.ref.aggregateIndex == it.aggregateIndex }) }
            .sortedWith(compareByDescending<Candidate> { it.noveltyScore }.thenBy { it.aggregateIndex }.thenBy { it.code })
        return Result(
            rawMatchesAnalyzed = samples.map { it.matchId }.toSet().size,
            playerMatchObservations = samples.map { "${it.matchId}:${it.playerId}" }.toSet().size,
            candidates = candidates,
            families = families,
        )
    }

    fun detail(samples: List<AdvancedStatsDiscoveryEngine.AggregateSample>, aggregateIndex: Int, code: Int): CandidateDetail? {
        val result = analyze(samples)
        val candidate = result.candidates.firstOrNull { it.aggregateIndex == aggregateIndex && it.code == code } ?: return null
        val matching = samples.filter { it.aggregateIndex == aggregateIndex }
        val item = WorkingCandidate(AdvancedStatsDiscoveryEngine.CodeRef(aggregateIndex, code), matching, matching.map { it.sparseValues[code] ?: 0 }, emptyList())
        val relations = controls.mapNotNull { controlRelation(item, it) }
        fun evidence(sample: AdvancedStatsDiscoveryEngine.AggregateSample) = EvidenceRow(
            sample.matchId, sample.timestamp, sample.playerId, sample.playerName, sample.matchCompletion,
            sample.sparseValues[code] ?: 0, sample.knownMetrics.filterKeys { it in controls },
        )
        return CandidateDetail(
            candidate = candidate,
            knownRelations = relations,
            relatedFamily = result.families.firstOrNull { it.aggregateIndex == aggregateIndex && code in it.relatedCodes },
            highValues = matching.sortedByDescending { it.sparseValues[code] ?: 0 }.take(thresholds.maximumEvidenceRows).map(::evidence),
            lowNonZeroValues = matching.filter { (it.sparseValues[code] ?: 0) > 0 }.sortedBy { it.sparseValues[code] ?: 0 }.take(thresholds.maximumEvidenceRows).map(::evidence),
            zeroValues = matching.filter { (it.sparseValues[code] ?: 0) == 0 }.sortedWith(compareBy({ it.matchId }, { it.playerId })).take(thresholds.maximumEvidenceRows).map(::evidence),
        )
    }

    private fun candidate(item: WorkingCandidate, family: UnknownFamily?): Candidate {
        val values = item.values
        val observations = values.size
        val active = values.count { it != 0 }
        val matches = item.samples.map { it.matchId }.toSet().size
        val players = item.samples.map { it.playerId }.toSet().size
        val distinct = values.toSet().size
        val closest = item.relations.sortedWith(compareByDescending<KnownRelation> { relationStrength(it) }.thenBy { it.name }).firstOrNull()
        val exactKnown = item.relations.any { it.classification == "REDUNDANT_WITH_KNOWN" }
        val likelyKnown = item.relations.any { it.classification == "LIKELY_REDUNDANT" }
        val relatedKnown = item.relations.any { it.classification == "RELATED_TO_KNOWN" }
        val warnings = buildList {
            if (observations < thresholds.minimumObservations || matches < thresholds.minimumMatches || players < thresholds.minimumPlayers) add("SMALL_SAMPLE")
            if (distinct <= 1) add("LOW_VARIANCE")
            if (active < thresholds.minimumActive || active.toDouble() / observations.coerceAtLeast(1) < 0.10) add("VERY_SPARSE")
            if (players <= 1) add("SINGLE_PLAYER_DOMINATED")
            if (matches <= 1) add("SINGLE_MATCH_DOMINATED")
            if (exactKnown || likelyKnown) add("LIKELY_KNOWN_DUPLICATE")
            if (family != null && family.representativeCode != item.ref.code) add("UNKNOWN_FAMILY_REDUNDANCY")
        }
        val sufficient = observations >= thresholds.minimumObservations && matches >= thresholds.minimumMatches && players >= thresholds.minimumPlayers && active >= thresholds.minimumActive && distinct > 1
        val classification = when {
            !sufficient -> "INSUFFICIENT_EVIDENCE"
            exactKnown -> "REDUNDANT_WITH_KNOWN"
            likelyKnown -> "LIKELY_REDUNDANT"
            relatedKnown -> "RELATED_TO_KNOWN"
            else -> "NOVEL_CANDIDATE"
        }
        /*
         * Investigation-priority score (0..100), never a sporting score:
         * evidence 30 + match/player diversity 40 + variation 15 +
         * independence 25, then penalties for sparsity (-12), low variation
         * (-18), non-representative unknown family (-20), and known
         * redundancy (-35/-55). HIGH additionally requires the explicit
         * high observation/match/player thresholds so a small clean sample
         * cannot outrank durable evidence.
         */
        val evidence = minOf(30.0, observations.toDouble() / thresholds.highObservations * 30)
        val diversity = minOf(20.0, matches.toDouble() / thresholds.highMatches * 10) + minOf(20.0, players.toDouble() / thresholds.highPlayers * 10)
        val information = if (distinct > 1) 15.0 else 0.0
        val independence = when (classification) { "NOVEL_CANDIDATE" -> 25.0; "RELATED_TO_KNOWN" -> 8.0; else -> 0.0 }
        val sparsePenalty = if ("VERY_SPARSE" in warnings) -12.0 else 0.0
        val lowVariancePenalty = if ("LOW_VARIANCE" in warnings) -18.0 else 0.0
        val familyPenalty = if (family != null && family.representativeCode != item.ref.code) -20.0 else 0.0
        val knownPenalty = if (exactKnown) -55.0 else if (likelyKnown) -35.0 else 0.0
        val score = (evidence + diversity + information + independence + sparsePenalty + lowVariancePenalty + familyPenalty + knownPenalty).coerceIn(0.0, 100.0)
        val priority = when {
            classification == "NOVEL_CANDIDATE" && score >= 65.0 && observations >= thresholds.highObservations && matches >= thresholds.highMatches && players >= thresholds.highPlayers -> "HIGH"
            classification == "NOVEL_CANDIDATE" && score >= 35.0 -> "MEDIUM"
            else -> "LOW"
        }
        return Candidate(item.ref.aggregateIndex, item.ref.code, "UNKNOWN", observations, active, safeRatio(active, observations), matches, players,
            values.minOrNull() ?: 0, values.maxOrNull() ?: 0, values.takeIf { it.isNotEmpty() }?.average()?.takeIf { it.isFinite() }, median(values), distinct,
            score, priority, classification, closest, warnings, family?.id, family?.representativeCode == item.ref.code)
    }

    private fun controlRelation(item: WorkingCandidate, name: String): KnownRelation? {
        val pairs = item.samples.mapNotNull { sample ->
            val known = controlValue(sample, name) ?: return@mapNotNull null
            (sample.sparseValues[item.ref.code] ?: 0) to known
        }
        if (pairs.isEmpty()) return null
        val candidate = pairs.map { it.first.toDouble() }
        val known = pairs.map { it.second.toDouble() }
        val active = pairs.filter { it.first != 0 || it.second != 0 }
        val both = active.count { it.first != 0 && it.second != 0 }
        val candidateActive = pairs.filter { it.first != 0 }
        val knownActive = pairs.filter { it.second != 0 }
        val exact = pairs.count { it.first == it.second }
        val ratios = pairs.filter { it.first != 0 && it.second != 0 }.map { it.first.toDouble() / it.second }.filter { it.isFinite() }
        val ratioStable = if (ratios.size < 3) null else {
            val mean = ratios.average(); mean != 0.0 && (ratios.sumOf { (it - mean) * (it - mean) } / ratios.size).let { sqrt(it) / abs(mean) } <= 0.05
        }
        val equality = safeRatio(exact, pairs.size)
        val pearson = pearson(candidate, known)
        val spearman = spearman(candidate, known)
        val overlap = safeRatio(both, active.size)
        val pCandidateKnown = safeRatio(knownActive.count { it.first != 0 }, knownActive.size)
        val pKnownCandidate = safeRatio(candidateActive.count { it.second != 0 }, candidateActive.size)
        val classification = when {
            pairs.size >= thresholds.minimumObservations && equality != null && equality >= thresholds.duplicateEquality && overlap != null && overlap >= thresholds.duplicateOverlap -> "REDUNDANT_WITH_KNOWN"
            pairs.size >= thresholds.minimumObservations && pearson != null && abs(pearson) >= thresholds.duplicatePearson && (ratioStable == true || (spearman != null && abs(spearman) >= thresholds.duplicatePearson)) -> "LIKELY_REDUNDANT"
            pearson != null && abs(pearson) >= thresholds.relatedPearson -> "RELATED_TO_KNOWN"
            else -> "INDEPENDENT"
        }
        return KnownRelation(name, pairs.size, equality, pearson, spearman, overlap, pCandidateKnown, pKnownCandidate, ratioStable, classification)
    }

    private fun controlValue(sample: AdvancedStatsDiscoveryEngine.AggregateSample, name: String): Int? = when (name) {
        "agg0[112] Beats" -> if (sample.aggregateIndex == 0) sample.sparseValues[112] ?: 0 else sample.knownMetrics["beats"]
        "agg0[115] Pre-assists" -> if (sample.aggregateIndex == 0) sample.sparseValues[115] ?: 0 else sample.knownMetrics["preAssists"]
        "agg0[152] Through passes" -> if (sample.aggregateIndex == 0) sample.sparseValues[152] ?: 0 else sample.knownMetrics["throughPasses"]
        else -> sample.knownMetrics[name]
    }

    private fun familyEdges(items: List<WorkingCandidate>): List<Pair<WorkingCandidate, WorkingCandidate>> = buildList {
        items.groupBy { it.ref.aggregateIndex }.values.forEach { group ->
            for (i in group.indices) for (j in i + 1 until group.size) {
                val a = group[i]; val b = group[j]
                val p = pearson(a.values.map(Int::toDouble), b.values.map(Int::toDouble)) ?: continue
                val active = a.values.indices.count { a.values[it] != 0 || b.values[it] != 0 }
                val equal = safeRatio(a.values.indices.count { a.values[it] == b.values[it] }, a.values.size) ?: 0.0
                val overlap = safeRatio(a.values.indices.count { a.values[it] != 0 && b.values[it] != 0 }, active) ?: 0.0
                if ((abs(p) >= thresholds.familyPearson || equal >= thresholds.familyEquality) && overlap >= thresholds.familyOverlap) add(a to b)
            }
        }
    }

    private fun families(items: List<WorkingCandidate>, edges: List<Pair<WorkingCandidate, WorkingCandidate>>): List<UnknownFamily> {
        val adjacency = mutableMapOf<AdvancedStatsDiscoveryEngine.CodeRef, MutableSet<AdvancedStatsDiscoveryEngine.CodeRef>>()
        edges.forEach { (a, b) ->
            adjacency.getOrPut(a.ref) { mutableSetOf() }.add(b.ref)
            adjacency.getOrPut(b.ref) { mutableSetOf() }.add(a.ref)
        }
        val byRef = items.associateBy { it.ref }; val seen = mutableSetOf<AdvancedStatsDiscoveryEngine.CodeRef>()
        return adjacency.keys.sortedWith(compareBy({ it.aggregateIndex }, { it.code })).mapNotNull { first ->
            if (!seen.add(first)) return@mapNotNull null
            val component = mutableSetOf(first); val queue = ArrayDeque(listOf(first))
            while (queue.isNotEmpty()) for (next in adjacency[queue.removeFirst()].orEmpty()) if (seen.add(next)) { component.add(next); queue.add(next) }
            if (component.size < 2) return@mapNotNull null
            val members = component.mapNotNull(byRef::get)
            val representative = members.sortedWith(compareByDescending<WorkingCandidate> { it.values.toSet().size }.thenByDescending { it.samples.map { s -> s.matchId }.toSet().size }.thenByDescending { it.samples.map { s -> s.playerId }.toSet().size }.thenBy { it.ref.code }).first()
            val codes = component.map { it.code }.sorted()
            UnknownFamily("agg${first.aggregateIndex}:${codes.joinToString("-")}", first.aggregateIndex, representative.ref.code, codes, "STATISTICAL_REDUNDANCY")
        }
    }

    private fun relationStrength(relation: KnownRelation): Double = when (relation.classification) {
        "REDUNDANT_WITH_KNOWN" -> 4.0; "LIKELY_REDUNDANT" -> 3.0; "RELATED_TO_KNOWN" -> 2.0; else -> abs(relation.pearson ?: 0.0)
    }
    private fun safeRatio(n: Int, d: Int): Double? = if (d == 0) null else (n.toDouble() / d).takeIf { it.isFinite() }
    private fun median(values: List<Int>): Double? = values.sorted().let { sorted -> when { sorted.isEmpty() -> null; sorted.size % 2 == 1 -> sorted[sorted.size / 2].toDouble(); else -> (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0 } }
    private fun pearson(x: List<Double>, y: List<Double>): Double? { if (x.size < 3 || x.size != y.size) return null; val mx=x.average(); val my=y.average(); val denominator=x.indices.sumOf{(x[it]-mx)*(x[it]-mx)}*y.indices.sumOf{(y[it]-my)*(y[it]-my)}; if (denominator <= 0.0 || !denominator.isFinite()) return null; return (x.indices.sumOf{(x[it]-mx)*(y[it]-my)}/sqrt(denominator)).takeIf { it.isFinite() } }
    private fun spearman(x: List<Double>, y: List<Double>): Double? = pearson(rank(x), rank(y))
    private fun rank(values: List<Double>): List<Double> { val sorted=values.withIndex().sortedBy{it.value}; val ranks=DoubleArray(values.size); var i=0; while(i<sorted.size){var j=i; while(j+1<sorted.size&&sorted[j+1].value==sorted[i].value)j++; val r=(i+j)/2.0+1; for(k in i..j)ranks[sorted[k].index]=r; i=j+1}; return ranks.toList() }
}
