package com.eafc26.discordstats.explorer

import com.fasterxml.jackson.annotation.JsonProperty
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Pure, bounded analysis over already-persisted EA raw aggregates.
 *
 * It reports mathematical transport signals only. Nothing emitted here is a
 * sporting mapping, and nothing outside the admin Explorer may consume it.
 */
class AdvancedStatsDiscoveryEngine(
    private val thresholds: Thresholds = Thresholds(),
) {

    /** Centralized methodological thresholds for Discovery V2. */
    data class Thresholds(
        val minimumObservations: Int = 5,
        val minimumMatches: Int = 2,
        val candidateObservations: Int = 15,
        val candidateMatches: Int = 3,
        val strongObservations: Int = 30,
        val strongMatches: Int = 5,
        val minimumInformativeObservations: Int = 5,
        val candidateInformativeObservations: Int = 10,
        val strongInformativeObservations: Int = 15,
        val minimumInformativeSupport: Double = 0.70,
        val candidateInformativeSupport: Double = 0.80,
        val strongInformativeSupport: Double = 0.95,
        val zeroDominatedAllZeroRate: Double = 0.75,
        val zeroDominatedSupportGap: Double = 0.20,
        val minimumOverlapAmongActive: Double = 0.20,
        val familyMinimumPearson: Double = 0.80,
        val familyMinimumOverlapAmongActive: Double = 0.30,
        val maximumTernaryCodes: Int = 12,
        val maximumRelations: Int = 120,
        val maximumValuesPerCode: Int = 20,
    )

    /** One player/match/aggregate with raw coverage known to be AVAILABLE. */
    data class AggregateSample(
        val clubId: String,
        val matchId: String,
        val timestamp: String,
        val playerId: String,
        val playerName: String?,
        val aggregateIndex: Int,
        val sparseValues: Map<Int, Int>,
        val knownMetrics: Map<String, Int?>,
        val matchCompletion: String? = null,
    )

    /** Generated only after sparse coverage is known to be AVAILABLE. */
    data class DiscoveryObservation(
        val clubId: String,
        val matchId: String,
        val timestamp: String,
        val playerId: String,
        val playerName: String?,
        val aggregateIndex: Int,
        val code: Int,
        val value: Int,
        val rawCoverage: String = "AVAILABLE",
    )

    data class CodeRef(val aggregateIndex: Int, val code: Int)

    data class ObservedCodeValue(
        val matchId: String,
        val timestamp: String,
        val playerId: String,
        val playerName: String?,
        val value: Int,
    )

    data class CodeInventory(
        val aggregateIndex: Int,
        val code: Int,
        val confidence: String,
        val rawObservationCount: Int,
        val matchCount: Int,
        val playerCount: Int,
        val nonZeroCount: Int,
        val zeroCount: Int,
        val prevalence: Double,
        val min: Int,
        val max: Int,
        val mean: Double,
        val median: Double,
        val sum: Long,
        val distinctValueCount: Int,
        val technicalClassification: String,
        val observedValues: List<ObservedCodeValue>,
    )

    data class RelationExample(
        val matchId: String,
        val timestamp: String,
        val playerId: String,
        val playerName: String?,
        val a: Int,
        val b: Int,
        val c: Int? = null,
        val expected: Int,
        val difference: Int,
    )

    /**
     * Global coverage is descriptive. Evidence and ranking always use the
     * informative subset, which excludes all-zero observations.
     */
    data class RelationEvidence(
        val totalObservations: Int,
        val totalMatches: Int,
        val globalMatches: Int,
        val globalMatchesSatisfied: Int,
        val globalSupport: Double,
        val informativeObservations: Int,
        val informativeMatches: Int,
        val informativeSatisfied: Int,
        val informativeSupport: Double,
        val bothZeroCount: Int,
        val allZeroCount: Int,
        val aNonZeroCount: Int,
        val bNonZeroCount: Int,
        val bothNonZeroCount: Int,
        val eitherNonZeroCount: Int,
        val bothNonZeroRate: Double,
        val eitherNonZeroRate: Double,
        val overlapAmongActive: Double,
        val zeroDominated: Boolean,
    )

    data class DiscoveryScore(
        val total: Double,
        val informativeEvidenceComponent: Double,
        val matchComponent: Double,
        val variationComponent: Double,
        val relationTypeComponent: Double,
        val overlapComponent: Double,
        val counterexampleComponent: Double,
        val zeroDominationPenalty: Double,
        val inequalityPenalty: Double,
        val knownMetricPenalty: Double,
    )

    data class DiscoveryRelation(
        val id: String,
        val aggregateIndex: Int,
        val relationType: String,
        val codeA: Int,
        val codeB: Int,
        val codeC: Int? = null,
        // Retained aliases make the global sample readable in existing views.
        val observationsTested: Int,
        val matchesTested: Int,
        val exactMatches: Int,
        val violations: Int,
        val supportRate: Double,
        val evidence: RelationEvidence,
        val evidenceTier: String,
        val explainedByKnownMetric: Boolean,
        val score: DiscoveryScore,
        val examples: List<RelationExample>,
        val counterexamples: List<RelationExample>,
    )

    data class CodeCorrelation(
        val aggregateIndex: Int,
        val codeA: Int,
        val codeB: Int,
        val observationsTested: Int,
        val matchesTested: Int,
        val pearson: Double,
        val exactEqualityRate: Double,
        val informativeObservations: Int,
        val informativeSupport: Double,
        val bothZeroCount: Int,
        val aNonZeroCount: Int,
        val bNonZeroCount: Int,
        val bothNonZeroCount: Int,
        val eitherNonZeroCount: Int,
        val overlapAmongActive: Double,
        val zeroDominated: Boolean,
        val penalizedForLowOverlap: Boolean,
        val rankingScore: Double,
    )

    data class KnownMetricCalibration(
        val aggregateIndex: Int,
        val code: Int,
        val metric: String,
        val observationsTested: Int,
        val matchesTested: Int,
        val exactMatches: Int,
        val supportRate: Double,
        val informativeObservations: Int,
        val informativeMatches: Int,
        val informativeSatisfied: Int,
        val informativeSupport: Double,
        val bothZeroCount: Int,
        val zeroDominated: Boolean,
        val redundantWithKnownMetric: Boolean,
    )

    data class RelatedCodeFamily(
        val aggregateIndex: Int,
        val codes: List<Int>,
        val codeCount: Int,
        val relationshipCount: Int,
        val observations: Int,
        val matches: Int,
        val averagePearson: Double,
        val minimumPearson: Double,
        val averageNonZeroOverlap: Double,
        val strongestEdge: CodeCorrelation,
        val edges: List<CodeCorrelation>,
    )

    /** A cross-method ranking item. It never creates or changes a mapping. */
    data class TopDiscoverySignal(
        val id: String,
        val aggregateIndex: Int,
        val pattern: String,
        val type: String,
        val informativeObservations: Int,
        val informativeSupport: Double,
        val globalObservations: Int,
        val globalSupport: Double,
        val matches: Int,
        val nonZeroOverlap: Double,
        val counterexamples: Int,
        val tier: String,
        val zeroDominated: Boolean,
        val score: Double,
        val relationId: String? = null,
    )

    data class DiscoveryResult(
        val rawMatchesAnalyzed: Int,
        val playerMatchObservations: Int,
        val aggregate0CodeCount: Int,
        val aggregate1CodeCount: Int,
        val unknownCodeCount: Int,
        val knownCodeCount: Int,
        val hypothesisCodeCount: Int,
        val inventory: List<CodeInventory>,
        val topCandidates: List<DiscoveryRelation>,
        val topDiscoverySignals: List<TopDiscoverySignal> = emptyList(),
        val relations: List<DiscoveryRelation>,
        val correlations: List<CodeCorrelation>,
        val calibration: List<KnownMetricCalibration>,
        val relatedCodeFamilies: List<RelatedCodeFamily> = emptyList(),
    )

    fun analyze(samples: List<AggregateSample>, hideKnownRelationships: Boolean): DiscoveryResult {
        val observations = flatten(samples)
        val grouped = observations.groupBy { CodeRef(it.aggregateIndex, it.code) }
        val inventoryByRef = grouped.mapValues { (_, values) -> inventory(values) }
        val inventory = inventoryByRef.entries
            .map { (ref, item) -> item.copy(aggregateIndex = ref.aggregateIndex, code = ref.code) }
            .sortedWith(compareBy<CodeInventory> { it.aggregateIndex }.thenBy { it.code })

        val calibration = calibration(samples, grouped, inventoryByRef)
        val redundant = calibration.filter { it.redundantWithKnownMetric }
            .map { CodeRef(it.aggregateIndex, it.code) }.toSet()
        val candidates = inventoryByRef.filter { (ref, item) ->
            !AdvancedStatsCodeRegistry.isKnown(ref.aggregateIndex, ref.code) &&
                item.rawObservationCount >= thresholds.minimumObservations &&
                item.matchCount >= thresholds.minimumMatches &&
                item.technicalClassification !in setOf("ALWAYS_ZERO", "LOW_VARIANCE", "RARE")
        }.keys.sortedWith(compareBy<CodeRef> { it.aggregateIndex }.thenBy { it.code })

        val allRelations = candidates.groupBy { it.aggregateIndex }.flatMap { (_, refs) ->
            relationsForAggregate(refs, grouped, inventoryByRef, redundant)
        }
        val relations = (if (hideKnownRelationships) allRelations.filterNot { it.explainedByKnownMetric } else allRelations)
            .sortedWith(compareByDescending<DiscoveryRelation> { it.score.total }.thenByDescending { it.evidence.informativeSupport })
            .take(thresholds.maximumRelations)
        val correlations = candidates.groupBy { it.aggregateIndex }.flatMap { (_, refs) ->
            correlationsForAggregate(refs, grouped)
        }.sortedWith(compareByDescending<CodeCorrelation> { abs(it.pearson) }.thenByDescending { it.overlapAmongActive })
            .take(thresholds.maximumRelations)

        val families = relatedFamilies(correlations)
        return DiscoveryResult(
            rawMatchesAnalyzed = samples.map { it.matchId }.toSet().size,
            playerMatchObservations = samples.map { "${it.matchId}:${it.playerId}" }.toSet().size,
            aggregate0CodeCount = inventory.count { it.aggregateIndex == 0 },
            aggregate1CodeCount = inventory.count { it.aggregateIndex == 1 },
            unknownCodeCount = inventory.count { it.confidence == CodeConfidence.UNKNOWN.name },
            knownCodeCount = inventory.count { it.confidence in setOf(CodeConfidence.CONFIRMED.name, CodeConfidence.HIGH_CONFIDENCE.name) },
            hypothesisCodeCount = inventory.count { it.confidence == CodeConfidence.HYPOTHESIS.name },
            inventory = inventory,
            topCandidates = relations.filter { it.evidenceTier != "COINCIDENCE" }.take(20),
            topDiscoverySignals = topSignals(relations, correlations, families),
            relations = relations,
            correlations = correlations,
            calibration = calibration,
            relatedCodeFamilies = families,
        )
    }

    private fun flatten(samples: List<AggregateSample>): List<DiscoveryObservation> {
        val codesByAggregate = samples.groupBy { it.aggregateIndex }
            .mapValues { (_, entries) -> entries.flatMap { it.sparseValues.keys }.toSortedSet() }
        return samples.flatMap { sample ->
            codesByAggregate[sample.aggregateIndex].orEmpty().map { code ->
                DiscoveryObservation(sample.clubId, sample.matchId, sample.timestamp, sample.playerId, sample.playerName, sample.aggregateIndex, code, sample.sparseValues[code] ?: 0)
            }
        }
    }

    private fun inventory(values: List<DiscoveryObservation>): CodeInventory {
        val numbers = values.map { it.value }.sorted()
        val nonZero = numbers.count { it != 0 }
        val prevalence = ratio(nonZero, numbers.size)
        val classification = when {
            nonZero == 0 -> "ALWAYS_ZERO"
            prevalence < 0.15 -> "RARE"
            numbers.distinct().size <= 1 -> "LOW_VARIANCE"
            prevalence >= 0.75 -> "HIGH_PREVALENCE"
            else -> "VARIABLE"
        }
        return CodeInventory(
            aggregateIndex = -1, code = -1,
            confidence = AdvancedStatsCodeRegistry.lookup(values.first().aggregateIndex, values.first().code).confidence.name,
            rawObservationCount = values.size, matchCount = values.map { it.matchId }.toSet().size,
            playerCount = values.map { it.playerId }.toSet().size, nonZeroCount = nonZero, zeroCount = values.size - nonZero,
            prevalence = prevalence, min = numbers.firstOrNull() ?: 0, max = numbers.lastOrNull() ?: 0,
            mean = if (numbers.isEmpty()) 0.0 else numbers.average(), median = median(numbers), sum = numbers.sumOf { it.toLong() },
            distinctValueCount = numbers.distinct().size, technicalClassification = classification,
            observedValues = values.take(thresholds.maximumValuesPerCode).map { ObservedCodeValue(it.matchId, it.timestamp, it.playerId, it.playerName, it.value) },
        )
    }

    private fun relationsForAggregate(
        refs: List<CodeRef>,
        grouped: Map<CodeRef, List<DiscoveryObservation>>,
        inventory: Map<CodeRef, CodeInventory>,
        redundant: Set<CodeRef>,
    ): List<DiscoveryRelation> {
        val results = mutableListOf<DiscoveryRelation>()
        refs.indices.forEach { left ->
            ((left + 1) until refs.size).forEach { right ->
                val a = refs[left]; val b = refs[right]
                results += evaluate(a, b, null, "EQUAL", grouped, inventory, redundant) { x, y, _ -> (x == y) to y }
                results += evaluate(a, b, null, "GREATER_OR_EQUAL", grouped, inventory, redundant) { x, y, _ -> (x >= y) to y }
                results += evaluate(a, b, null, "LESS_OR_EQUAL", grouped, inventory, redundant) { x, y, _ -> (x <= y) to y }
            }
        }
        val shortList = refs.sortedByDescending { inventory.getValue(it).rawObservationCount }.take(thresholds.maximumTernaryCodes)
        shortList.forEach { a ->
            val others = shortList.filter { it != a }
            others.indices.forEach { left -> ((left + 1) until others.size).forEach { right ->
                val b = others[left]; val c = others[right]
                results += evaluate(a, b, c, "SUM", grouped, inventory, redundant) { x, y, z -> val expected = y + z!!; (x == expected) to expected }
                results += evaluate(a, b, c, "DIFFERENCE", grouped, inventory, redundant) { x, y, z -> val expected = y - z!!; (x == expected) to expected }
            } }
        }
        // Keep detail inspectable, but prevent mathematically weak noise from ranking.
        return results.filter { it.observationsTested >= thresholds.minimumObservations && it.supportRate >= 0.60 }
    }

    private data class EvaluatedCase(val observation: DiscoveryObservation, val b: Int, val c: Int?, val holds: Boolean, val expected: Int)

    private fun evaluate(
        a: CodeRef, b: CodeRef, c: CodeRef?, type: String,
        grouped: Map<CodeRef, List<DiscoveryObservation>>, inventory: Map<CodeRef, CodeInventory>, redundant: Set<CodeRef>,
        predicate: (Int, Int, Int?) -> Pair<Boolean, Int>,
    ): DiscoveryRelation {
        val aBySample = grouped.getValue(a).associateBy(::sampleKey)
        val bBySample = grouped.getValue(b).associateBy(::sampleKey)
        val cBySample = c?.let { grouped.getValue(it).associateBy(::sampleKey) }
        val common = aBySample.keys.intersect(bBySample.keys).let { if (cBySample == null) it else it.intersect(cBySample.keys) }.sorted()
        val cases = common.map { key ->
            val x = aBySample.getValue(key); val y = bBySample.getValue(key); val z = cBySample?.get(key)
            val (holds, expected) = predicate(x.value, y.value, z?.value)
            EvaluatedCase(x, y.value, z?.value, holds, expected)
        }
        val evidence = evidence(cases)
        val examples = cases.filter { it.holds }.take(8).map(::example)
        val counterexamples = cases.filterNot { it.holds }.take(8).map(::example)
        val variation = listOfNotNull(inventory[a], inventory[b], c?.let(inventory::get)).count { it.distinctValueCount > 1 } >= if (c == null) 2 else 3
        val explained = listOfNotNull(a, b, c).any { it in redundant }
        return DiscoveryRelation(
            id = "$type:${a.aggregateIndex}:${a.code}:${b.code}:${c?.code ?: ""}", aggregateIndex = a.aggregateIndex,
            relationType = type, codeA = a.code, codeB = b.code, codeC = c?.code,
            observationsTested = evidence.totalObservations, matchesTested = evidence.totalMatches,
            exactMatches = cases.count { it.holds }, violations = cases.count { !it.holds }, supportRate = evidence.globalSupport,
            evidence = evidence, evidenceTier = tier(type, evidence, variation), explainedByKnownMetric = explained,
            score = score(type, evidence, variation, explained), examples = examples, counterexamples = counterexamples,
        )
    }

    private fun example(value: EvaluatedCase) = RelationExample(
        matchId = value.observation.matchId, timestamp = value.observation.timestamp, playerId = value.observation.playerId,
        playerName = value.observation.playerName, a = value.observation.value, b = value.b, c = value.c,
        expected = value.expected, difference = value.observation.value - value.expected,
    )

    private fun evidence(cases: List<EvaluatedCase>): RelationEvidence {
        val informative = cases.filter { it.observation.value != 0 || it.b != 0 || it.c != null && it.c != 0 }
        val bothZero = cases.count { it.observation.value == 0 && it.b == 0 }
        val allZero = cases.count { it.observation.value == 0 && it.b == 0 && (it.c == null || it.c == 0) }
        val aNonZero = cases.count { it.observation.value != 0 }; val bNonZero = cases.count { it.b != 0 }
        val bothNonZero = cases.count { it.observation.value != 0 && it.b != 0 }
        val eitherNonZero = cases.count { it.observation.value != 0 || it.b != 0 }
        val globalSupport = ratio(cases.count { it.holds }, cases.size)
        val informativeSupport = ratio(informative.count { it.holds }, informative.size)
        return RelationEvidence(
            totalObservations = cases.size, totalMatches = cases.map { it.observation.matchId }.toSet().size,
            globalMatches = cases.map { it.observation.matchId }.toSet().size,
            globalMatchesSatisfied = cases.filter { it.holds }.map { it.observation.matchId }.toSet().size,
            globalSupport = globalSupport, informativeObservations = informative.size,
            informativeMatches = informative.map { it.observation.matchId }.toSet().size,
            informativeSatisfied = informative.count { it.holds }, informativeSupport = informativeSupport,
            bothZeroCount = bothZero, allZeroCount = allZero, aNonZeroCount = aNonZero, bNonZeroCount = bNonZero,
            bothNonZeroCount = bothNonZero, eitherNonZeroCount = eitherNonZero,
            bothNonZeroRate = ratio(bothNonZero, cases.size), eitherNonZeroRate = ratio(eitherNonZero, cases.size),
            overlapAmongActive = ratio(bothNonZero, eitherNonZero),
            zeroDominated = ratio(allZero, cases.size) >= thresholds.zeroDominatedAllZeroRate &&
                globalSupport - informativeSupport >= thresholds.zeroDominatedSupportGap,
        )
    }

    private fun correlationsForAggregate(refs: List<CodeRef>, grouped: Map<CodeRef, List<DiscoveryObservation>>): List<CodeCorrelation> = buildList {
        for (left in refs.indices) for (right in (left + 1) until refs.size) {
            val a = grouped.getValue(refs[left]).associateBy(::sampleKey)
            val b = grouped.getValue(refs[right]).associateBy(::sampleKey)
            val keys = a.keys.intersect(b.keys).sorted()
            if (keys.size < thresholds.candidateObservations) continue
            val cases = keys.map { key ->
                val x = a.getValue(key); val y = b.getValue(key)
                EvaluatedCase(x, y.value, null, x.value == y.value, y.value)
            }
            val stats = evidence(cases)
            if (stats.zeroDominated || stats.informativeObservations < thresholds.minimumInformativeObservations) continue
            val pearson = pearson(cases.map { it.observation.value.toDouble() }, cases.map { it.b.toDouble() }) ?: continue
            add(CodeCorrelation(
                aggregateIndex = refs[left].aggregateIndex, codeA = refs[left].code, codeB = refs[right].code,
                observationsTested = stats.totalObservations, matchesTested = stats.totalMatches, pearson = pearson,
                exactEqualityRate = stats.globalSupport, informativeObservations = stats.informativeObservations,
                informativeSupport = stats.informativeSupport, bothZeroCount = stats.bothZeroCount, aNonZeroCount = stats.aNonZeroCount,
                bNonZeroCount = stats.bNonZeroCount, bothNonZeroCount = stats.bothNonZeroCount,
                eitherNonZeroCount = stats.eitherNonZeroCount, overlapAmongActive = stats.overlapAmongActive,
                zeroDominated = stats.zeroDominated, penalizedForLowOverlap = stats.overlapAmongActive < thresholds.minimumOverlapAmongActive,
                rankingScore = abs(pearson) * (if (stats.overlapAmongActive < thresholds.minimumOverlapAmongActive) 0.50 else 1.0),
            ))
        }
    }

    private fun calibration(
        samples: List<AggregateSample>, grouped: Map<CodeRef, List<DiscoveryObservation>>, inventory: Map<CodeRef, CodeInventory>,
    ): List<KnownMetricCalibration> = grouped.flatMap { (ref, observations) ->
        val item = inventory.getValue(ref)
        if (AdvancedStatsCodeRegistry.isKnown(ref.aggregateIndex, ref.code) || item.rawObservationCount < thresholds.minimumObservations || item.nonZeroCount == 0) return@flatMap emptyList()
        val byKey = samples.filter { it.aggregateIndex == ref.aggregateIndex }.associateBy(::sampleKey)
        knownMetrics.mapNotNull { metric ->
            val cases = observations.mapNotNull { observation -> byKey[sampleKey(observation)]?.knownMetrics?.get(metric)?.let { target ->
                EvaluatedCase(observation, target, null, observation.value == target, target)
            } }
            if (cases.size < thresholds.minimumObservations) return@mapNotNull null
            val stats = evidence(cases)
            if (stats.zeroDominated || stats.informativeObservations < thresholds.minimumInformativeObservations ||
                stats.informativeMatches < thresholds.minimumMatches || stats.informativeSupport < thresholds.minimumInformativeSupport) return@mapNotNull null
            KnownMetricCalibration(
                aggregateIndex = ref.aggregateIndex, code = ref.code, metric = metric,
                observationsTested = stats.totalObservations, matchesTested = stats.totalMatches, exactMatches = cases.count { it.holds },
                supportRate = stats.globalSupport, informativeObservations = stats.informativeObservations,
                informativeMatches = stats.informativeMatches, informativeSatisfied = stats.informativeSatisfied,
                informativeSupport = stats.informativeSupport, bothZeroCount = stats.bothZeroCount, zeroDominated = stats.zeroDominated,
                redundantWithKnownMetric = stats.globalSupport >= 0.95 && stats.informativeSupport >= thresholds.strongInformativeSupport &&
                    stats.informativeObservations >= thresholds.strongInformativeObservations && stats.informativeMatches >= thresholds.candidateMatches,
            )
        }
    }

    private val knownMetrics = listOf("goals", "assists", "shots", "passesAttempted", "passesCompleted", "tacklesAttempted", "tacklesCompleted")

    private fun relatedFamilies(correlations: List<CodeCorrelation>): List<RelatedCodeFamily> = correlations.groupBy { it.aggregateIndex }.flatMap { (aggregate, edges) ->
        val qualifying = edges.filter {
            abs(it.pearson) >= thresholds.familyMinimumPearson &&
                it.matchesTested >= thresholds.candidateMatches &&
                it.informativeObservations >= thresholds.candidateInformativeObservations &&
                it.overlapAmongActive >= thresholds.familyMinimumOverlapAmongActive && !it.zeroDominated
        }
        val adjacency = mutableMapOf<Int, MutableSet<Int>>()
        qualifying.forEach { edge -> adjacency.getOrPut(edge.codeA) { mutableSetOf() }.add(edge.codeB); adjacency.getOrPut(edge.codeB) { mutableSetOf() }.add(edge.codeA) }
        val remaining = adjacency.keys.toMutableSet()
        buildList {
            while (remaining.isNotEmpty()) {
                val seed = remaining.first(); val queue = ArrayDeque<Int>(); val codes = mutableSetOf<Int>(); queue += seed
                while (queue.isNotEmpty()) { val code = queue.removeFirst(); if (!codes.add(code)) continue; remaining.remove(code); adjacency[code].orEmpty().forEach(queue::add) }
                if (codes.size < 2) continue
                val familyEdges = qualifying.filter { it.codeA in codes && it.codeB in codes }
                add(RelatedCodeFamily(
                    aggregateIndex = aggregate, codes = codes.sorted(), codeCount = codes.size, relationshipCount = familyEdges.size,
                    observations = familyEdges.minOf { it.observationsTested }, matches = familyEdges.minOf { it.matchesTested },
                    averagePearson = familyEdges.map { abs(it.pearson) }.average(), minimumPearson = familyEdges.minOf { abs(it.pearson) },
                    averageNonZeroOverlap = familyEdges.map { it.overlapAmongActive }.average(),
                    strongestEdge = familyEdges.maxBy { abs(it.pearson) }, edges = familyEdges.sortedByDescending { abs(it.pearson) },
                ))
            }
        }
    }.sortedWith(compareByDescending<RelatedCodeFamily> { it.averagePearson }.thenByDescending { it.codeCount })

    private fun topSignals(
        relations: List<DiscoveryRelation>,
        correlations: List<CodeCorrelation>,
        families: List<RelatedCodeFamily>,
    ): List<TopDiscoverySignal> {
        val structural = relations.filter { it.evidenceTier != "COINCIDENCE" && it.relationType !in inequalities }.map { relation ->
            TopDiscoverySignal(
                id = "relation:${relation.id}", aggregateIndex = relation.aggregateIndex, pattern = pattern(relation),
                type = when (relation.relationType) { "EQUAL" -> "EXACT_EQUALITY"; "SUM" -> "EXACT_SUM"; else -> "EXACT_DIFFERENCE" },
                informativeObservations = relation.evidence.informativeObservations, informativeSupport = relation.evidence.informativeSupport,
                globalObservations = relation.evidence.totalObservations, globalSupport = relation.evidence.globalSupport,
                matches = relation.evidence.informativeMatches, nonZeroOverlap = relation.evidence.overlapAmongActive,
                counterexamples = relation.violations, tier = relation.evidenceTier, zeroDominated = relation.evidence.zeroDominated,
                score = relation.score.total, relationId = relation.id,
            )
        }
        val correlationSignals = correlations.filter { correlation ->
            abs(correlation.pearson) >= thresholds.familyMinimumPearson &&
                correlation.informativeObservations >= thresholds.candidateInformativeObservations &&
                correlation.matchesTested >= thresholds.candidateMatches && !correlation.zeroDominated
        }.map { correlation ->
            TopDiscoverySignal(
                id = "correlation:${correlation.aggregateIndex}:${correlation.codeA}:${correlation.codeB}", aggregateIndex = correlation.aggregateIndex,
                pattern = "${correlation.codeA} ↔ ${correlation.codeB}", type = "STRONG_CORRELATION",
                informativeObservations = correlation.informativeObservations, informativeSupport = correlation.informativeSupport,
                globalObservations = correlation.observationsTested, globalSupport = correlation.exactEqualityRate,
                matches = correlation.matchesTested, nonZeroOverlap = correlation.overlapAmongActive, counterexamples = 0,
                tier = if (correlation.informativeObservations >= thresholds.strongInformativeObservations && correlation.matchesTested >= thresholds.strongMatches) "STRONG_CANDIDATE" else "CANDIDATE",
                zeroDominated = false, score = correlation.rankingScore * 40,
            )
        }
        val familySignals = families.map { family ->
            TopDiscoverySignal(
                id = "family:${family.aggregateIndex}:${family.codes.joinToString(":")}", aggregateIndex = family.aggregateIndex,
                pattern = family.codes.joinToString(" ↔ "), type = "RELATED_CODE_FAMILY",
                informativeObservations = family.edges.minOf { it.informativeObservations }, informativeSupport = family.edges.map { it.informativeSupport }.average(),
                globalObservations = family.observations, globalSupport = family.edges.map { it.exactEqualityRate }.average(),
                matches = family.matches, nonZeroOverlap = family.averageNonZeroOverlap, counterexamples = 0,
                tier = "CANDIDATE", zeroDominated = false, score = family.averagePearson * 35 + family.averageNonZeroOverlap * 10,
            )
        }
        return (structural + correlationSignals + familySignals)
            .sortedByDescending { it.score }
            .take(20)
    }

    private fun tier(type: String, evidence: RelationEvidence, variation: Boolean): String {
        if (evidence.zeroDominated || !variation || evidence.informativeObservations < thresholds.minimumInformativeObservations || evidence.informativeSupport < thresholds.minimumInformativeSupport) return "COINCIDENCE"
        val candidate = evidence.totalObservations >= thresholds.candidateObservations && evidence.totalMatches >= thresholds.candidateMatches &&
            evidence.informativeObservations >= thresholds.candidateInformativeObservations && evidence.informativeSupport >= thresholds.candidateInformativeSupport
        if (!candidate) return "COINCIDENCE"
        // An inequality needs complementary context and is never strong on its own.
        if (type in inequalities) return if (evidence.overlapAmongActive >= thresholds.minimumOverlapAmongActive) "CANDIDATE" else "COINCIDENCE"
        return if (evidence.totalObservations >= thresholds.strongObservations && evidence.totalMatches >= thresholds.strongMatches &&
            evidence.informativeObservations >= thresholds.strongInformativeObservations && evidence.informativeSupport >= thresholds.strongInformativeSupport) "STRONG_CANDIDATE" else "CANDIDATE"
    }

    private fun score(type: String, evidence: RelationEvidence, variation: Boolean, redundant: Boolean): DiscoveryScore {
        val informative = evidence.informativeSupport * 30
        val matches = (evidence.informativeMatches.coerceAtMost(thresholds.strongMatches).toDouble() / thresholds.strongMatches) * 18
        val variationScore = if (variation) 10.0 else 0.0
        val relationType = when (type) { "EQUAL" -> 20.0; "SUM", "DIFFERENCE" -> 18.0; else -> 2.0 }
        val overlap = evidence.overlapAmongActive * 14
        val counterexamples = (1.0 - evidence.informativeSupport) * -12
        val zeroPenalty = if (evidence.zeroDominated) -45.0 else 0.0
        val inequalityPenalty = if (type in inequalities) -18.0 else 0.0
        val knownPenalty = if (redundant) -25.0 else 0.0
        return DiscoveryScore(informative + matches + variationScore + relationType + overlap + counterexamples + zeroPenalty + inequalityPenalty + knownPenalty,
            informative, matches, variationScore, relationType, overlap, counterexamples, zeroPenalty, inequalityPenalty, knownPenalty)
    }

    private fun sampleKey(value: DiscoveryObservation): String = "${value.matchId}:${value.playerId}"
    private fun sampleKey(value: AggregateSample): String = "${value.matchId}:${value.playerId}"
    private fun ratio(numerator: Int, denominator: Int): Double = if (denominator == 0) 0.0 else numerator.toDouble() / denominator
    private fun median(sorted: List<Int>): Double = when { sorted.isEmpty() -> 0.0; sorted.size % 2 == 1 -> sorted[sorted.size / 2].toDouble(); else -> (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]).toDouble() / 2 }
    private fun pearson(x: List<Double>, y: List<Double>): Double? {
        if (x.isEmpty()) return null
        val meanX = x.average(); val meanY = y.average()
        if (!meanX.isFinite() || !meanY.isFinite()) return null
        val numerator = x.indices.sumOf { (x[it] - meanX) * (y[it] - meanY) }
        val product = x.sumOf { (it - meanX) * (it - meanX) } * y.sumOf { (it - meanY) * (it - meanY) }
        if (product <= 0.0) return null
        val result = numerator / sqrt(product)
        return if (result.isFinite()) result else null
    }
    private fun safeRatio(numerator: Int, denominator: Int): Double? =
        if (denominator == 0) null else (numerator.toDouble() / denominator).let { if (it.isFinite()) it else null }
    private fun Double.finiteOrNull(): Double? = if (isFinite()) this else null

    // ===================================================================
    // V2 — Anchor-based semantic investigation
    // ===================================================================

    data class AnchorRef(
        val type: String,
        val aggregateIndex: Int?,
        val code: Int?,
        val metricName: String?,
    )

    data class AnchorProfile(
        val anchor: AnchorRef,
        val registryStatus: String,
        val knownLabel: String?,
        val observations: Int,
        val matches: Int,
        val distinctPlayers: Int,
        val nonZeroObservations: Int,
        val prevalence: Double,
        val min: Int,
        val max: Int,
        val mean: Double,
        val median: Double,
    )

    data class ResidualDistribution(
        val residualCounts: Map<Int, Int>,
        val min: Int,
        val max: Int,
        val mean: Double,
        val median: Double,
        val zeroPercent: Double,
    )

    data class AnchorRelationshipScore(
        val total: Double,
        val informativeSampleComponent: Double,
        val matchDiversityComponent: Double,
        val nonZeroOverlapComponent: Double,
        val conditionalSupportComponent: Double,
        val equalityComponent: Double,
        val subtypeConsistencyComponent: Double,
        val correlationComponent: Double,
        val counterexamplePenalty: Double,
    )

    data class AnchorEvidenceRow(
        val matchId: String,
        val timestamp: String,
        val playerId: String,
        val playerName: String?,
        val anchorValue: Int,
        val candidateValue: Int,
        val difference: Int,
        val ratio: Double?,
        val goals: Int?,
        val assists: Int?,
        val shots: Int?,
        val passesAttempted: Int?,
        val passesCompleted: Int?,
        val tacklesAttempted: Int?,
        val tacklesCompleted: Int?,
        val code112: Int?,
        val code115: Int?,
        val code152: Int?,
        val code174: Int?,
        val matchCompletion: String?,
    )

    data class ConditionalProfile(
        val candidateCode: Int,
        val candidateActiveWhenAnchorActive: Int,
        val anchorActiveObservations: Int,
        val candidateInactiveWhenAnchorActive: Int,
        val anchorActiveWhenCandidateActive: Int,
        val candidateActiveObservations: Int,
        @get:JsonProperty("pCandidateActiveGivenAnchorActive")
        val pCandidateActiveGivenAnchorActive: Double?,
        @get:JsonProperty("pAnchorActiveGivenCandidateActive")
        val pAnchorActiveGivenCandidateActive: Double?,
    )

    data class AnchorRelationship(
        val candidateAggregateIndex: Int,
        val candidateCode: Int,
        val candidateRegistryStatus: String,
        val candidateKnownLabel: String?,
        val technicalClassification: String,
        val exactEqualityRate: Double,
        val informativeEqualityRate: Double,
        val anchorGteCandidateRate: Double,
        val candidateGteAnchorRate: Double,
        val residualAMinusB: ResidualDistribution,
        val residualBMinusA: ResidualDistribution,
        val ratioBAMeanWhenAPositive: Double?,
        val ratioABMeanWhenBPositive: Double?,
        val pearson: Double?,
        val spearman: Double?,
        val bothNonZero: Int,
        val eitherNonZero: Int,
        val nonZeroOverlap: Double,
        @get:JsonProperty("pCandidateActiveGivenAnchorActive")
        val pCandidateActiveGivenAnchorActive: Double?,
        @get:JsonProperty("pAnchorActiveGivenCandidateActive")
        val pAnchorActiveGivenCandidateActive: Double?,
        @get:JsonProperty("pEqualGivenEitherActive")
        val pEqualGivenEitherActive: Double?,
        val observations: Int,
        val informativeObservations: Int,
        val matches: Int,
        val distinctPlayers: Int,
        val score: AnchorRelationshipScore,
        val evidenceObservations: List<AnchorEvidenceRow>,
        val differenceCases: List<AnchorEvidenceRow>,
    )

    data class FamilyMatrixCell(
        val codeA: Int,
        val codeB: Int,
        val pearson: Double?,
        val informativeEquality: Double,
        val nonZeroOverlap: Double,
    )

    data class FamilyObservationRow(
        val matchId: String,
        val timestamp: String,
        val playerId: String,
        val playerName: String?,
        val values: Map<Int, Int>,
        val goals: Int?,
        val assists: Int?,
        val shots: Int?,
        val passesAttempted: Int?,
        val passesCompleted: Int?,
        val tacklesAttempted: Int?,
        val tacklesCompleted: Int?,
        val matchCompletion: String?,
    )

    data class FamilyInvestigation(
        val aggregateIndex: Int,
        val codes: List<Int>,
        val matrix: List<FamilyMatrixCell>,
        val observations: List<FamilyObservationRow>,
    )

    data class DatasetMetadata(
        val rawMatchesAnalyzed: Int,
        val playerMatchObservations: Int,
        val distinctPlayers: Int,
        val distinctMatches: Int,
    )

    data class AnchorInvestigation(
        val anchor: AnchorProfile,
        val relationships: List<AnchorRelationship>,
        val conditionalProfiles: List<ConditionalProfile>,
        val dataset: DatasetMetadata,
    )

    fun investigateAnchor(
        samples: List<AggregateSample>,
        anchor: AnchorRef,
    ): AnchorInvestigation {
        val anchorValues = extractAnchorValues(samples, anchor)
        if (anchorValues.isEmpty()) return emptyAnchorInvestigation(anchor, samples)

        val profile = buildAnchorProfile(anchor, anchorValues)
        val candidateCodes = findCandidateCodes(samples, anchor)
        val relationships = candidateCodes.map { candidate ->
            buildAnchorRelationship(anchorValues, candidate, samples)
        }.sortedByDescending { it.score.total }
        val conditionals = candidateCodes.map { candidate ->
            buildConditionalProfile(anchorValues, candidate, samples)
        }

        return AnchorInvestigation(
            anchor = profile,
            relationships = relationships,
            conditionalProfiles = conditionals,
            dataset = DatasetMetadata(
                rawMatchesAnalyzed = samples.map { it.matchId }.toSet().size,
                playerMatchObservations = samples.map { "${it.matchId}:${it.playerId}" }.toSet().size,
                distinctPlayers = samples.map { it.playerId }.toSet().size,
                distinctMatches = samples.map { it.matchId }.toSet().size,
            ),
        )
    }

    fun investigateFamily(
        samples: List<AggregateSample>,
        aggregateIndex: Int,
        codes: List<Int>,
    ): FamilyInvestigation {
        val relevantSamples = samples.filter { it.aggregateIndex == aggregateIndex }
        val matrix = mutableListOf<FamilyMatrixCell>()
        for (i in codes.indices) {
            for (j in (i + 1) until codes.size) {
                val pairs = relevantSamples.mapNotNull { sample ->
                    val a = sample.sparseValues[codes[i]] ?: 0
                    val b = sample.sparseValues[codes[j]] ?: 0
                    Triple(a, b, sample)
                }
                val informative = pairs.filter { it.first != 0 || it.second != 0 }
                val eitherNonZero = informative.size
                val bothNonZero = informative.count { it.first != 0 && it.second != 0 }
                val informativeEquality = if (eitherNonZero == 0) 0.0 else ratio(informative.count { it.first == it.second }, eitherNonZero)
                val p = if (pairs.size >= 3) pearson(pairs.map { it.first.toDouble() }, pairs.map { it.second.toDouble() }) else null
                matrix.add(FamilyMatrixCell(codes[i], codes[j], p, informativeEquality, ratio(bothNonZero, eitherNonZero)))
            }
        }
        val observations = relevantSamples.map { sample ->
            FamilyObservationRow(
                matchId = sample.matchId, timestamp = sample.timestamp, playerId = sample.playerId,
                playerName = sample.playerName,
                values = codes.associateWith { code -> sample.sparseValues[code] ?: 0 },
                goals = sample.knownMetrics["goals"], assists = sample.knownMetrics["assists"],
                shots = sample.knownMetrics["shots"], passesAttempted = sample.knownMetrics["passesAttempted"],
                passesCompleted = sample.knownMetrics["passesCompleted"], tacklesAttempted = sample.knownMetrics["tacklesAttempted"],
                tacklesCompleted = sample.knownMetrics["tacklesCompleted"],
                matchCompletion = sample.matchCompletion,
            )
        }.sortedWith(compareBy({ it.matchId }, { it.playerId }))
        return FamilyInvestigation(aggregateIndex, codes.sorted(), matrix, observations)
    }

    private data class AnchorObservation(
        val sampleKey: String,
        val matchId: String,
        val timestamp: String,
        val playerId: String,
        val playerName: String?,
        val anchorValue: Int,
        val knownMetrics: Map<String, Int?>,
        val sparseValues: Map<Int, Int>,
        val aggregateIndex: Int,
        val matchCompletion: String?,
    )

    private fun extractAnchorValues(samples: List<AggregateSample>, anchor: AnchorRef): List<AnchorObservation> {
        return when (anchor.type) {
            "KNOWN_METRIC" -> {
                val metric = anchor.metricName ?: return emptyList()
                samples.mapNotNull { sample ->
                    val value = sample.knownMetrics[metric] ?: return@mapNotNull null
                    AnchorObservation("${sample.matchId}:${sample.playerId}", sample.matchId, sample.timestamp, sample.playerId,
                        sample.playerName, value, sample.knownMetrics, sample.sparseValues, sample.aggregateIndex, sample.matchCompletion)
                }.distinctBy { it.sampleKey }
            }
            else -> {
                val aggIdx = anchor.aggregateIndex ?: return emptyList()
                val code = anchor.code ?: return emptyList()
                samples.filter { it.aggregateIndex == aggIdx }.map { sample ->
                    AnchorObservation("${sample.matchId}:${sample.playerId}", sample.matchId, sample.timestamp, sample.playerId,
                        sample.playerName, sample.sparseValues[code] ?: 0, sample.knownMetrics, sample.sparseValues, sample.aggregateIndex, sample.matchCompletion)
                }
            }
        }
    }

    private fun findCandidateCodes(samples: List<AggregateSample>, anchor: AnchorRef): List<CodeRef> {
        val targetAggregateIndex = anchor.aggregateIndex
        return samples
            .filter { targetAggregateIndex == null || it.aggregateIndex == targetAggregateIndex }
            .flatMap { sample -> sample.sparseValues.keys.map { CodeRef(sample.aggregateIndex, it) } }
            .toSet()
            .filter { ref ->
                if (anchor.type in setOf("AGGREGATE_CODE", "CONFIRMED_ADVANCED") && ref.aggregateIndex == anchor.aggregateIndex && ref.code == anchor.code) false
                else true
            }
            .sortedWith(compareBy({ it.aggregateIndex }, { it.code }))
    }

    private fun buildAnchorProfile(anchor: AnchorRef, observations: List<AnchorObservation>): AnchorProfile {
        val values = observations.map { it.anchorValue }.sorted()
        val nonZero = values.count { it != 0 }
        val registryStatus = if (anchor.type == "KNOWN_METRIC") "CONFIRMED"
        else AdvancedStatsCodeRegistry.lookup(anchor.aggregateIndex ?: 0, anchor.code ?: 0).confidence.name
        val knownLabel = if (anchor.type == "KNOWN_METRIC") anchor.metricName
        else AdvancedStatsCodeRegistry.lookup(anchor.aggregateIndex ?: 0, anchor.code ?: 0).metricName
        return AnchorProfile(
            anchor = anchor, registryStatus = registryStatus, knownLabel = knownLabel,
            observations = values.size, matches = observations.map { it.matchId }.toSet().size,
            distinctPlayers = observations.map { it.playerId }.toSet().size,
            nonZeroObservations = nonZero, prevalence = ratio(nonZero, values.size),
            min = values.firstOrNull() ?: 0, max = values.lastOrNull() ?: 0,
            mean = if (values.isEmpty()) 0.0 else values.average(), median = median(values),
        )
    }

    private fun buildAnchorRelationship(
        anchorObs: List<AnchorObservation>,
        candidate: CodeRef,
        samples: List<AggregateSample>,
    ): AnchorRelationship {
        val anchorByKey = anchorObs.associateBy { it.sampleKey }
        val candidateSamples = samples.filter { it.aggregateIndex == candidate.aggregateIndex }
            .associateBy { "${it.matchId}:${it.playerId}" }
        val commonKeys = anchorByKey.keys.intersect(candidateSamples.keys).sorted()

        data class Pair(val anchor: AnchorObservation, val candidateValue: Int, val sample: AggregateSample)
        val pairs = commonKeys.map { key ->
            val ao = anchorByKey.getValue(key)
            val cs = candidateSamples.getValue(key)
            Pair(ao, cs.sparseValues[candidate.code] ?: 0, cs)
        }

        val informative = pairs.filter { it.anchor.anchorValue != 0 || it.candidateValue != 0 }
        val anchorActive = pairs.filter { it.anchor.anchorValue != 0 }
        val candidateActive = pairs.filter { it.candidateValue != 0 }
        val bothNonZero = pairs.count { it.anchor.anchorValue != 0 && it.candidateValue != 0 }
        val eitherNonZero = informative.size

        val exactEqual = pairs.count { it.anchor.anchorValue == it.candidateValue }
        val informativeEqual = informative.count { it.anchor.anchorValue == it.candidateValue }
        val anchorGte = informative.count { it.anchor.anchorValue >= it.candidateValue }
        val candidateGte = informative.count { it.candidateValue >= it.anchor.anchorValue }

        val residualsAB = pairs.map { it.anchor.anchorValue - it.candidateValue }
        val residualsBA = pairs.map { it.candidateValue - it.anchor.anchorValue }

        val aPositive = pairs.filter { it.anchor.anchorValue > 0 }
        val bPositive = pairs.filter { it.candidateValue > 0 }
        val ratioBA = if (aPositive.isEmpty()) null else aPositive.map { it.candidateValue.toDouble() / it.anchor.anchorValue }.average()
        val ratioAB = if (bPositive.isEmpty()) null else bPositive.map { it.anchor.anchorValue.toDouble() / it.candidateValue }.average()

        val p = if (pairs.size >= 3) pearson(pairs.map { it.anchor.anchorValue.toDouble() }, pairs.map { it.candidateValue.toDouble() }) else null
        val sp = if (pairs.size >= 3) spearman(pairs.map { it.anchor.anchorValue.toDouble() }, pairs.map { it.candidateValue.toDouble() }) else null

        val pCandActiveGivenAnchorActive = safeRatio(anchorActive.count { it.candidateValue != 0 }, anchorActive.size)
        val pAnchorActiveGivenCandActive = safeRatio(candidateActive.count { it.anchor.anchorValue != 0 }, candidateActive.size)
        val pEqualGivenEitherActive = safeRatio(informativeEqual, eitherNonZero)

        val classification = classify(anchorGte, candidateGte, informativeEqual, p, eitherNonZero, bothNonZero, pCandActiveGivenAnchorActive ?: 0.0, pAnchorActiveGivenCandActive ?: 0.0)

        val distinctMatches = pairs.map { it.anchor.matchId }.toSet().size
        val distinctPlayers = pairs.map { it.anchor.playerId }.toSet().size
        val score = anchorScore(informative.size, distinctMatches, distinctPlayers, ratio(bothNonZero, eitherNonZero),
            pCandActiveGivenAnchorActive ?: 0.0, pEqualGivenEitherActive ?: 0.0, classification, p, informativeEqual, eitherNonZero)

        fun evidenceRow(pair: Pair): AnchorEvidenceRow {
            val diff = pair.anchor.anchorValue - pair.candidateValue
            val rat = if (pair.candidateValue != 0) pair.anchor.anchorValue.toDouble() / pair.candidateValue else null
            return AnchorEvidenceRow(
                matchId = pair.anchor.matchId, timestamp = pair.anchor.timestamp, playerId = pair.anchor.playerId,
                playerName = pair.anchor.playerName, anchorValue = pair.anchor.anchorValue, candidateValue = pair.candidateValue,
                difference = diff, ratio = rat,
                goals = pair.anchor.knownMetrics["goals"], assists = pair.anchor.knownMetrics["assists"],
                shots = pair.anchor.knownMetrics["shots"], passesAttempted = pair.anchor.knownMetrics["passesAttempted"],
                passesCompleted = pair.anchor.knownMetrics["passesCompleted"], tacklesAttempted = pair.anchor.knownMetrics["tacklesAttempted"],
                tacklesCompleted = pair.anchor.knownMetrics["tacklesCompleted"],
                code112 = pair.sample.sparseValues[112], code115 = pair.sample.sparseValues[115],
                code152 = pair.sample.sparseValues[152], code174 = pair.sample.sparseValues[174],
                matchCompletion = pair.anchor.matchCompletion,
            )
        }

        val differenceCases = pairs.filter { it.anchor.anchorValue != it.candidateValue }
            .sortedByDescending { abs(it.anchor.anchorValue - it.candidateValue) }
            .take(50).map(::evidenceRow)
        val allEvidence = pairs
            .sortedByDescending { abs(it.anchor.anchorValue - it.candidateValue) }
            .take(50).map(::evidenceRow)

        val mapping = AdvancedStatsCodeRegistry.lookup(candidate.aggregateIndex, candidate.code)
        return AnchorRelationship(
            candidateAggregateIndex = candidate.aggregateIndex, candidateCode = candidate.code,
            candidateRegistryStatus = mapping.confidence.name, candidateKnownLabel = mapping.metricName,
            technicalClassification = classification,
            exactEqualityRate = ratio(exactEqual, pairs.size), informativeEqualityRate = if (eitherNonZero == 0) 0.0 else ratio(informativeEqual, eitherNonZero),
            anchorGteCandidateRate = if (eitherNonZero == 0) 0.0 else ratio(anchorGte, eitherNonZero),
            candidateGteAnchorRate = if (eitherNonZero == 0) 0.0 else ratio(candidateGte, eitherNonZero),
            residualAMinusB = buildResidualDistribution(residualsAB),
            residualBMinusA = buildResidualDistribution(residualsBA),
            ratioBAMeanWhenAPositive = ratioBA, ratioABMeanWhenBPositive = ratioAB,
            pearson = p, spearman = sp,
            bothNonZero = bothNonZero, eitherNonZero = eitherNonZero,
            nonZeroOverlap = ratio(bothNonZero, eitherNonZero),
            pCandidateActiveGivenAnchorActive = pCandActiveGivenAnchorActive,
            pAnchorActiveGivenCandidateActive = pAnchorActiveGivenCandActive,
            pEqualGivenEitherActive = pEqualGivenEitherActive,
            observations = pairs.size, informativeObservations = eitherNonZero,
            matches = distinctMatches, distinctPlayers = distinctPlayers,
            score = score, evidenceObservations = allEvidence, differenceCases = differenceCases,
        )
    }

    private fun buildConditionalProfile(
        anchorObs: List<AnchorObservation>,
        candidate: CodeRef,
        samples: List<AggregateSample>,
    ): ConditionalProfile {
        val anchorByKey = anchorObs.associateBy { it.sampleKey }
        val candidateSamples = samples.filter { it.aggregateIndex == candidate.aggregateIndex }
            .associateBy { "${it.matchId}:${it.playerId}" }
        val commonKeys = anchorByKey.keys.intersect(candidateSamples.keys)

        val anchorActive = commonKeys.filter { anchorByKey.getValue(it).anchorValue != 0 }
        val candidateActive = commonKeys.filter { (candidateSamples.getValue(it).sparseValues[candidate.code] ?: 0) != 0 }
        val candidateActiveWhenAnchorActive = anchorActive.count { key ->
            (candidateSamples[key]?.sparseValues?.get(candidate.code) ?: 0) != 0
        }
        val anchorActiveWhenCandidateActive = candidateActive.count { key ->
            anchorByKey[key]?.anchorValue != 0
        }

        return ConditionalProfile(
            candidateCode = candidate.code,
            candidateActiveWhenAnchorActive = candidateActiveWhenAnchorActive,
            anchorActiveObservations = anchorActive.size,
            candidateInactiveWhenAnchorActive = anchorActive.size - candidateActiveWhenAnchorActive,
            anchorActiveWhenCandidateActive = anchorActiveWhenCandidateActive,
            candidateActiveObservations = candidateActive.size,
            pCandidateActiveGivenAnchorActive = safeRatio(candidateActiveWhenAnchorActive, anchorActive.size),
            pAnchorActiveGivenCandidateActive = safeRatio(anchorActiveWhenCandidateActive, candidateActive.size),
        )
    }

    private fun buildResidualDistribution(residuals: List<Int>): ResidualDistribution {
        if (residuals.isEmpty()) return ResidualDistribution(emptyMap(), 0, 0, 0.0, 0.0, 0.0)
        val counts = residuals.groupingBy { it }.eachCount().toSortedMap()
        val sorted = residuals.sorted()
        val zeroCount = counts[0] ?: 0
        return ResidualDistribution(
            residualCounts = counts, min = sorted.first(), max = sorted.last(),
            mean = sorted.average(), median = median(sorted),
            zeroPercent = ratio(zeroCount, sorted.size),
        )
    }

    private fun classify(
        anchorGte: Int, candidateGte: Int, informativeEqual: Int,
        pearson: Double?, eitherNonZero: Int, bothNonZero: Int,
        pCandGivenAnchor: Double, pAnchorGivenCand: Double,
    ): String {
        if (eitherNonZero < 3) return "INDEPENDENT"
        val ieRate = ratio(informativeEqual, eitherNonZero)
        val overlap = ratio(bothNonZero, eitherNonZero)
        if (ieRate >= 0.90 && overlap >= 0.70) return "NEAR_DUPLICATE"
        val aGteRate = ratio(anchorGte, eitherNonZero)
        val cGteRate = ratio(candidateGte, eitherNonZero)
        if (cGteRate <= 1.0 && aGteRate >= 0.85 && pCandGivenAnchor >= 0.80 && overlap >= 0.40) return "POSSIBLE_SUBTYPE"
        if (aGteRate <= 1.0 && cGteRate >= 0.85 && pAnchorGivenCand >= 0.80 && overlap >= 0.40) return "POSSIBLE_SUPERSET"
        if ((pearson != null && abs(pearson) >= 0.60) || overlap >= 0.50) return "RELATED"
        return "INDEPENDENT"
    }

    private fun anchorScore(
        informativeObs: Int, distinctMatches: Int, distinctPlayers: Int,
        overlap: Double, conditionalSupport: Double, equalityRate: Double,
        classification: String, pearson: Double?, informativeEqual: Int, eitherNonZero: Int,
    ): AnchorRelationshipScore {
        val sampleComp = (informativeObs.coerceAtMost(50).toDouble() / 50) * 15
        val playerDiversityBonus = if (distinctPlayers >= 3) 1.0 else distinctPlayers.toDouble() / 3
        val matchComp = (distinctMatches.coerceAtMost(10).toDouble() / 10) * 15 * playerDiversityBonus
        val overlapComp = overlap * 15
        val conditionalComp = conditionalSupport * 12
        val equalityComp = equalityRate * 18
        val subtypeComp = when (classification) { "NEAR_DUPLICATE" -> 15.0; "POSSIBLE_SUBTYPE", "POSSIBLE_SUPERSET" -> 10.0; "RELATED" -> 5.0; else -> 0.0 }
        val corrComp = (pearson?.let { abs(it) } ?: 0.0) * 10
        val counterPenalty = if (eitherNonZero > 0) (1.0 - ratio(informativeEqual, eitherNonZero)) * -10 else 0.0
        val total = sampleComp + matchComp + overlapComp + conditionalComp + equalityComp + subtypeComp + corrComp + counterPenalty
        return AnchorRelationshipScore(total, sampleComp, matchComp, overlapComp, conditionalComp, equalityComp, subtypeComp, corrComp, counterPenalty)
    }

    private fun spearman(x: List<Double>, y: List<Double>): Double? {
        if (x.size < 3) return null
        fun rank(values: List<Double>): List<Double> {
            val sorted = values.withIndex().sortedBy { it.value }
            val ranks = DoubleArray(values.size)
            var i = 0
            while (i < sorted.size) {
                var j = i
                while (j < sorted.size - 1 && sorted[j + 1].value == sorted[j].value) j++
                val avgRank = (i + j) / 2.0 + 1.0
                for (k in i..j) ranks[sorted[k].index] = avgRank
                i = j + 1
            }
            return ranks.toList()
        }
        return pearson(rank(x), rank(y))
    }

    // ===================================================================
    // V3 — Residual Explainer
    // ===================================================================

    data class ResidualExplainerRequest(
        val anchorRef: AnchorRef,
        val candidateAggregateIndex: Int,
        val candidateCode: Int,
    )

    data class ResidualGroup(val direction: String, val count: Int, val matches: Int, val players: Int)

    data class ResidualCodeStats(
        val count: Int, val activeCount: Int, val activationRate: Double?,
        val mean: Double?, val median: Double?, val min: Int?, val max: Int?,
    )

    data class ResidualContrast(val activationRateDelta: Double?, val meanDelta: Double?)

    data class ResidualDiscriminatorScore(
        val total: Double,
        val activationDeltaComponent: Double, val valueDeltaComponent: Double,
        val consistencyComponent: Double, val sampleSizeComponent: Double,
        val matchDiversityComponent: Double, val playerDiversityComponent: Double,
        val directionSpecificityComponent: Double,
        val singlePlayerPenalty: Double, val singleMatchPenalty: Double, val tinySamplePenalty: Double,
    )

    data class ResidualDiscriminator(
        val aggregateIndex: Int, val code: Int,
        val registryStatus: String, val registryLabel: String?,
        val technicalClassification: String,
        val totalObservations: Int, val activeObservations: Int,
        val negative: ResidualCodeStats, val zero: ResidualCodeStats, val positive: ResidualCodeStats,
        val positiveVsZero: ResidualContrast, val negativeVsZero: ResidualContrast, val positiveVsNegative: ResidualContrast,
        val pActiveGivenPositive: Double?, val pActiveGivenZero: Double?, val pActiveGivenNegative: Double?,
        val score: ResidualDiscriminatorScore, val warnings: List<String>,
        val distinctMatches: Int, val distinctPlayers: Int,
    )

    data class ResidualEvidenceRow(
        val matchId: String, val timestamp: String, val playerId: String, val playerName: String?,
        val anchorValue: Int, val candidateValue: Int, val residual: Int, val residualDirection: String,
        val investigatedCodeValue: Int,
        val goals: Int?, val assists: Int?, val shots: Int?,
        val passesAttempted: Int?, val passesCompleted: Int?,
        val tacklesAttempted: Int?, val tacklesCompleted: Int?,
        val code112: Int?, val code115: Int?, val code152: Int?, val code174: Int?,
        val matchCompletion: String?,
    )

    data class ResidualSignatureEntry(
        val aggregateIndex: Int, val code: Int, val value: Int,
        val registryStatus: String, val registryLabel: String?, val isTopDiscriminator: Boolean,
    )

    data class ResidualSignature(
        val matchId: String, val playerId: String, val playerName: String?,
        val anchorValue: Int, val candidateValue: Int, val residual: Int, val residualDirection: String,
        val matchCompletion: String?, val relevantCodes: List<ResidualSignatureEntry>,
    )

    data class ResidualExplainerResult(
        val anchor: AnchorRef, val candidateAggregateIndex: Int, val candidateCode: Int,
        val candidateRegistryStatus: String, val candidateLabel: String?,
        val groups: List<ResidualGroup>, val discriminators: List<ResidualDiscriminator>,
        val evidence: List<ResidualEvidenceRow>, val signatures: List<ResidualSignature>,
        val dataset: DatasetMetadata,
    )

    private data class ResidualObs(
        val anchor: AnchorObservation, val candidateValue: Int, val residual: Int,
        val direction: String, val sample: AggregateSample,
    )

    fun explainResiduals(samples: List<AggregateSample>, request: ResidualExplainerRequest): ResidualExplainerResult {
        val anchorValues = extractAnchorValues(samples, request.anchorRef)
        val anchorByKey = anchorValues.associateBy { it.sampleKey }
        val candidateSamples = samples.filter { it.aggregateIndex == request.candidateAggregateIndex }
            .associateBy { "${it.matchId}:${it.playerId}" }

        val observations = anchorByKey.keys.intersect(candidateSamples.keys).sorted().map { key ->
            val ao = anchorByKey.getValue(key)
            val cs = candidateSamples.getValue(key)
            val cv = cs.sparseValues[request.candidateCode] ?: 0
            val r = ao.anchorValue - cv
            ResidualObs(ao, cv, r, if (r < 0) "NEGATIVE" else if (r > 0) "POSITIVE" else "ZERO", cs)
        }

        val neg = observations.filter { it.direction == "NEGATIVE" }
        val zero = observations.filter { it.direction == "ZERO" }
        val pos = observations.filter { it.direction == "POSITIVE" }

        fun group(dir: String, obs: List<ResidualObs>) = ResidualGroup(dir, obs.size,
            obs.map { it.anchor.matchId }.toSet().size, obs.map { it.anchor.playerId }.toSet().size)
        val groups = listOf(group("NEGATIVE", neg), group("ZERO", zero), group("POSITIVE", pos))

        val allCodes = samples.flatMap { s -> s.sparseValues.keys.map { CodeRef(s.aggregateIndex, it) } }.toSet()
            .filter { !(it.aggregateIndex == request.candidateAggregateIndex && it.code == request.candidateCode) }
            .sortedWith(compareBy({ it.aggregateIndex }, { it.code }))

        val discriminators = allCodes.map { cr -> residualDiscriminator(cr, observations, neg, zero, pos) }
            .filter { it.totalObservations > 0 }
            .sortedByDescending { it.score.total }

        val topCodes = discriminators.take(20).map { CodeRef(it.aggregateIndex, it.code) }.toSet()

        val diffObs = observations.filter { it.direction != "ZERO" }
            .sortedWith(compareBy<ResidualObs> { it.direction }.thenByDescending { abs(it.residual) })

        val evidence = diffObs.take(100).map { obs ->
            ResidualEvidenceRow(obs.anchor.matchId, obs.anchor.timestamp, obs.anchor.playerId, obs.anchor.playerName,
                obs.anchor.anchorValue, obs.candidateValue, obs.residual, obs.direction, 0,
                obs.anchor.knownMetrics["goals"], obs.anchor.knownMetrics["assists"], obs.anchor.knownMetrics["shots"],
                obs.anchor.knownMetrics["passesAttempted"], obs.anchor.knownMetrics["passesCompleted"],
                obs.anchor.knownMetrics["tacklesAttempted"], obs.anchor.knownMetrics["tacklesCompleted"],
                obs.sample.sparseValues[112], obs.sample.sparseValues[115], obs.sample.sparseValues[152], obs.sample.sparseValues[174],
                obs.anchor.matchCompletion)
        }

        val signatures = diffObs.take(50).map { obs ->
            val relevant = allCodes
                .filter { cr -> cr.aggregateIndex == obs.sample.aggregateIndex || cr.aggregateIndex == obs.anchor.aggregateIndex }
                .mapNotNull { cr ->
                    val v = if (cr.aggregateIndex == obs.sample.aggregateIndex) obs.sample.sparseValues[cr.code] ?: 0
                            else obs.anchor.sparseValues[cr.code] ?: 0
                    if (v == 0 && cr !in topCodes) return@mapNotNull null
                    val m = AdvancedStatsCodeRegistry.lookup(cr.aggregateIndex, cr.code)
                    ResidualSignatureEntry(cr.aggregateIndex, cr.code, v, m.confidence.name, m.metricName, cr in topCodes)
                }
                .sortedWith(compareByDescending<ResidualSignatureEntry> { it.isTopDiscriminator }.thenBy { it.aggregateIndex }.thenBy { it.code })
                .take(25)
            ResidualSignature(obs.anchor.matchId, obs.anchor.playerId, obs.anchor.playerName,
                obs.anchor.anchorValue, obs.candidateValue, obs.residual, obs.direction, obs.anchor.matchCompletion, relevant)
        }

        val cm = AdvancedStatsCodeRegistry.lookup(request.candidateAggregateIndex, request.candidateCode)
        return ResidualExplainerResult(request.anchorRef, request.candidateAggregateIndex, request.candidateCode,
            cm.confidence.name, cm.metricName, groups, discriminators, evidence, signatures,
            DatasetMetadata(samples.map { it.matchId }.toSet().size, samples.map { "${it.matchId}:${it.playerId}" }.toSet().size,
                samples.map { it.playerId }.toSet().size, samples.map { it.matchId }.toSet().size))
    }

    private fun residualDiscriminator(
        cr: CodeRef, all: List<ResidualObs>, neg: List<ResidualObs>, zero: List<ResidualObs>, pos: List<ResidualObs>,
    ): ResidualDiscriminator {
        fun codeValue(obs: ResidualObs): Int =
            if (cr.aggregateIndex == obs.sample.aggregateIndex) obs.sample.sparseValues[cr.code] ?: 0
            else obs.anchor.sparseValues[cr.code] ?: 0

        fun stats(group: List<ResidualObs>): ResidualCodeStats {
            if (group.isEmpty()) return ResidualCodeStats(0, 0, null, null, null, null, null)
            val vals = group.map { codeValue(it) }
            val active = vals.count { it != 0 }
            val sorted = vals.sorted()
            return ResidualCodeStats(group.size, active, safeRatio(active, group.size),
                vals.average().finiteOrNull(), median(sorted).finiteOrNull(), sorted.first(), sorted.last())
        }

        val negS = stats(neg); val zeroS = stats(zero); val posS = stats(pos)
        val allVals = all.map { codeValue(it) }
        val totalActive = allVals.count { it != 0 }

        fun contrast(a: ResidualCodeStats, b: ResidualCodeStats) = ResidualContrast(
            if (a.activationRate != null && b.activationRate != null) (a.activationRate - b.activationRate).finiteOrNull() else null,
            if (a.mean != null && b.mean != null) (a.mean - b.mean).finiteOrNull() else null)

        val diff = neg + pos
        val dMatches = diff.map { it.anchor.matchId }.toSet().size
        val dPlayers = diff.map { it.anchor.playerId }.toSet().size

        val warnings = mutableListOf<String>()
        if (diff.size < 5) warnings.add("SMALL_SAMPLE")
        if (dPlayers == 1 && diff.isNotEmpty()) warnings.add("SINGLE_PLAYER_DOMINATED")
        if (dMatches == 1 && diff.isNotEmpty()) warnings.add("SINGLE_MATCH_DOMINATED")

        val posVsZero = contrast(posS, zeroS); val negVsZero = contrast(negS, zeroS); val posVsNeg = contrast(posS, negS)
        val maxADelta = maxOf(abs(posVsZero.activationRateDelta ?: 0.0), abs(negVsZero.activationRateDelta ?: 0.0))
        val maxMDelta = maxOf(abs(posVsZero.meanDelta ?: 0.0), abs(negVsZero.meanDelta ?: 0.0))
        val dirSpec = abs((posS.activationRate ?: 0.0) - (negS.activationRate ?: 0.0))

        val actComp = maxADelta * 25
        val valComp = (maxMDelta.coerceAtMost(20.0) / 20) * 15
        val consComp = if (zeroS.count > 0 && (zeroS.activationRate ?: 0.0) < 0.1 && maxADelta > 0.5) 10.0
                       else if (maxADelta > 0.3) 5.0 else 0.0
        val sampComp = (diff.size.coerceAtMost(20).toDouble() / 20) * 12
        val matchComp = (dMatches.coerceAtMost(5).toDouble() / 5) * 10
        val playerComp = (dPlayers.coerceAtMost(4).toDouble() / 4) * 8
        val dirComp = dirSpec * 10
        val spPen = if (dPlayers == 1 && diff.isNotEmpty()) -15.0 else if (dPlayers == 2) -5.0 else 0.0
        val smPen = if (dMatches == 1 && diff.isNotEmpty()) -12.0 else if (dMatches == 2) -4.0 else 0.0
        val tinyPen = if (diff.size < 3) -20.0 else if (diff.size < 5) -8.0 else 0.0

        val total = actComp + valComp + consComp + sampComp + matchComp + playerComp + dirComp + spPen + smPen + tinyPen

        val zar = zeroS.activationRate ?: 0.0
        val classification = when {
            diff.size < 3 -> "INSUFFICIENT_EVIDENCE"
            (posS.activationRate ?: 0.0) > zar + 0.3 && (negS.activationRate ?: 0.0) <= zar + 0.1 -> "POSITIVE_RESIDUAL_ASSOCIATED"
            (negS.activationRate ?: 0.0) > zar + 0.3 && (posS.activationRate ?: 0.0) <= zar + 0.1 -> "NEGATIVE_RESIDUAL_ASSOCIATED"
            maxADelta > 0.2 -> "DIFFERENCE_ASSOCIATED"
            else -> "NON_DISCRIMINATING"
        }

        val m = AdvancedStatsCodeRegistry.lookup(cr.aggregateIndex, cr.code)
        return ResidualDiscriminator(cr.aggregateIndex, cr.code, m.confidence.name, m.metricName, classification,
            all.size, totalActive, negS, zeroS, posS, posVsZero, negVsZero, posVsNeg,
            safeRatio(posS.activeCount, pos.size), safeRatio(zeroS.activeCount, zero.size), safeRatio(negS.activeCount, neg.size),
            ResidualDiscriminatorScore(total, actComp, valComp, consComp, sampComp, matchComp, playerComp, dirComp, spPen, smPen, tinyPen),
            warnings, dMatches, dPlayers)
    }

    private fun emptyAnchorInvestigation(anchor: AnchorRef, samples: List<AggregateSample>) = AnchorInvestigation(
        anchor = AnchorProfile(anchor, "UNKNOWN", null, 0, 0, 0, 0, 0.0, 0, 0, 0.0, 0.0),
        relationships = emptyList(), conditionalProfiles = emptyList(),
        dataset = DatasetMetadata(samples.map { it.matchId }.toSet().size, samples.map { "${it.matchId}:${it.playerId}" }.toSet().size,
            samples.map { it.playerId }.toSet().size, samples.map { it.matchId }.toSet().size),
    )

    private companion object {
        val inequalities = setOf("GREATER_OR_EQUAL", "LESS_OR_EQUAL")
        fun pattern(relation: DiscoveryRelation): String = when (relation.relationType) {
            "EQUAL" -> "${relation.codeA} == ${relation.codeB}"
            "GREATER_OR_EQUAL" -> "${relation.codeA} >= ${relation.codeB}"
            "LESS_OR_EQUAL" -> "${relation.codeA} <= ${relation.codeB}"
            "SUM" -> "${relation.codeA} == ${relation.codeB} + ${relation.codeC}"
            else -> "${relation.codeA} == ${relation.codeB} - ${relation.codeC}"
        }
    }
}
