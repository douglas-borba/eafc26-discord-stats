package com.eafc26.discordstats.explorer

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
        val meanX = x.average(); val meanY = y.average()
        val numerator = x.indices.sumOf { (x[it] - meanX) * (y[it] - meanY) }
        val denominator = sqrt(x.sumOf { (it - meanX) * (it - meanX) } * y.sumOf { (it - meanY) * (it - meanY) })
        return if (denominator == 0.0) null else numerator / denominator
    }

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
