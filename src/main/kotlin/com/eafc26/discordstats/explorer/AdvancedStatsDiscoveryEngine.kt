package com.eafc26.discordstats.explorer

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Pure, bounded analysis over already-persisted EA raw aggregates.
 *
 * It deliberately reports only mathematical observations. No result from this
 * class is a sporting mapping or may be consumed outside the admin Explorer.
 */
class AdvancedStatsDiscoveryEngine(
    private val thresholds: Thresholds = Thresholds(),
) {

    data class Thresholds(
        val minimumObservations: Int = 5,
        val minimumMatches: Int = 2,
        val candidateObservations: Int = 15,
        val candidateMatches: Int = 3,
        val strongObservations: Int = 30,
        val strongMatches: Int = 5,
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

    /** A flattened observation, generated only after sparse coverage is known. */
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

    data class DiscoveryScore(
        val total: Double,
        val observationComponent: Double,
        val matchComponent: Double,
        val variationComponent: Double,
        val relationComponent: Double,
        val counterexampleComponent: Double,
        val knownMetricPenalty: Double,
    )

    data class DiscoveryRelation(
        val id: String,
        val aggregateIndex: Int,
        val relationType: String,
        val codeA: Int,
        val codeB: Int,
        val codeC: Int? = null,
        val observationsTested: Int,
        val matchesTested: Int,
        val exactMatches: Int,
        val violations: Int,
        val supportRate: Double,
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
    )

    data class KnownMetricCalibration(
        val aggregateIndex: Int,
        val code: Int,
        val metric: String,
        val observationsTested: Int,
        val matchesTested: Int,
        val exactMatches: Int,
        val supportRate: Double,
        val redundantWithKnownMetric: Boolean,
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
        val relations: List<DiscoveryRelation>,
        val correlations: List<CodeCorrelation>,
        val calibration: List<KnownMetricCalibration>,
    )

    fun analyze(samples: List<AggregateSample>, hideKnownRelationships: Boolean): DiscoveryResult {
        val observations = flatten(samples)
        val grouped = observations.groupBy { CodeRef(it.aggregateIndex, it.code) }
        val inventoryByRef = grouped.mapValues { (_, values) -> inventory(values) }
        val inventory = inventoryByRef.entries
            .map { (ref, entry) -> entry.copy(aggregateIndex = ref.aggregateIndex, code = ref.code) }
            .sortedWith(compareBy<CodeInventory> { it.aggregateIndex }.thenBy { it.code })

        val calibration = calibration(samples, grouped, inventoryByRef)
        val redundant = calibration.filter { it.redundantWithKnownMetric }
            .map { CodeRef(it.aggregateIndex, it.code) }
            .toSet()
        val candidates = inventoryByRef
            .filter { (ref, item) ->
                !AdvancedStatsCodeRegistry.isKnown(ref.aggregateIndex, ref.code) &&
                    item.rawObservationCount >= thresholds.minimumObservations &&
                    item.matchCount >= thresholds.minimumMatches &&
                    item.technicalClassification !in setOf("ALWAYS_ZERO", "LOW_VARIANCE", "RARE")
            }
            .keys
            .sortedWith(compareBy<CodeRef> { it.aggregateIndex }.thenBy { it.code })

        val relations = candidates.groupBy { it.aggregateIndex }.flatMap { (_, refs) ->
            relationsForAggregate(refs, grouped, inventoryByRef, redundant)
        }.let { all ->
            if (hideKnownRelationships) all.filterNot { it.explainedByKnownMetric } else all
        }.sortedWith(compareByDescending<DiscoveryRelation> { it.score.total }.thenByDescending { it.supportRate })
            .take(thresholds.maximumRelations)

        val correlations = candidates.groupBy { it.aggregateIndex }.flatMap { (_, refs) ->
            correlationsForAggregate(refs, grouped)
        }.sortedByDescending { abs(it.pearson) }.take(thresholds.maximumRelations)

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
            relations = relations,
            correlations = correlations,
            calibration = calibration,
        )
    }

    private fun flatten(samples: List<AggregateSample>): List<DiscoveryObservation> {
        val codesByAggregate = samples.groupBy { it.aggregateIndex }
            .mapValues { (_, entries) -> entries.flatMap { it.sparseValues.keys }.toSortedSet() }
        return samples.flatMap { sample ->
            codesByAggregate[sample.aggregateIndex].orEmpty().map { code ->
                DiscoveryObservation(
                    clubId = sample.clubId,
                    matchId = sample.matchId,
                    timestamp = sample.timestamp,
                    playerId = sample.playerId,
                    playerName = sample.playerName,
                    aggregateIndex = sample.aggregateIndex,
                    code = code,
                    value = sample.sparseValues[code] ?: 0,
                )
            }
        }
    }

    private fun inventory(values: List<DiscoveryObservation>): CodeInventory {
        val numbers = values.map { it.value }.sorted()
        val nonZero = numbers.count { it != 0 }
        val prevalence = if (numbers.isEmpty()) 0.0 else nonZero.toDouble() / numbers.size
        val classification = when {
            nonZero == 0 -> "ALWAYS_ZERO"
            prevalence < 0.15 -> "RARE"
            numbers.distinct().size <= 1 -> "LOW_VARIANCE"
            prevalence >= 0.75 -> "HIGH_PREVALENCE"
            else -> "VARIABLE"
        }
        return CodeInventory(
            aggregateIndex = -1,
            code = -1,
            confidence = AdvancedStatsCodeRegistry.lookup(values.first().aggregateIndex, values.first().code).confidence.name,
            rawObservationCount = values.size,
            matchCount = values.map { it.matchId }.toSet().size,
            playerCount = values.map { it.playerId }.toSet().size,
            nonZeroCount = nonZero,
            zeroCount = values.size - nonZero,
            prevalence = prevalence,
            min = numbers.firstOrNull() ?: 0,
            max = numbers.lastOrNull() ?: 0,
            mean = if (numbers.isEmpty()) 0.0 else numbers.average(),
            median = median(numbers),
            sum = numbers.sumOf { it.toLong() },
            distinctValueCount = numbers.distinct().size,
            technicalClassification = classification,
            observedValues = values.take(thresholds.maximumValuesPerCode).map {
                ObservedCodeValue(it.matchId, it.timestamp, it.playerId, it.playerName, it.value)
            },
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
                val a = refs[left]
                val b = refs[right]
                results += evaluate(a, b, null, "EQUAL", grouped, inventory, redundant) { x, y, _ -> (x == y) to y }
                results += evaluate(a, b, null, "GREATER_OR_EQUAL", grouped, inventory, redundant) { x, y, _ -> (x >= y) to y }
                results += evaluate(a, b, null, "LESS_OR_EQUAL", grouped, inventory, redundant) { x, y, _ -> (x <= y) to y }
            }
        }
        val shortList = refs.sortedByDescending { inventory.getValue(it).rawObservationCount }
            .take(thresholds.maximumTernaryCodes)
        shortList.forEach { a ->
            val others = shortList.filter { it != a }
            others.indices.forEach { left ->
                ((left + 1) until others.size).forEach { right ->
                    val b = others[left]
                    val c = others[right]
                    results += evaluate(a, b, c, "SUM", grouped, inventory, redundant) { x, y, z ->
                        val expected = y + z!!
                        (x == expected) to expected
                    }
                    results += evaluate(a, b, c, "DIFFERENCE", grouped, inventory, redundant) { x, y, z ->
                        val expected = y - z!!
                        (x == expected) to expected
                    }
                }
            }
        }
        return results.filter { it.observationsTested >= thresholds.minimumObservations && it.supportRate >= 0.60 }
    }

    private fun evaluate(
        a: CodeRef,
        b: CodeRef,
        c: CodeRef?,
        type: String,
        grouped: Map<CodeRef, List<DiscoveryObservation>>,
        inventory: Map<CodeRef, CodeInventory>,
        redundant: Set<CodeRef>,
        predicate: (Int, Int, Int?) -> Pair<Boolean, Int>,
    ): DiscoveryRelation {
        val aBySample = grouped.getValue(a).associateBy { sampleKey(it) }
        val bBySample = grouped.getValue(b).associateBy { sampleKey(it) }
        val cBySample = c?.let { grouped.getValue(it).associateBy { observation -> sampleKey(observation) } }
        val common = aBySample.keys.intersect(bBySample.keys).let { keys ->
            if (cBySample == null) keys else keys.intersect(cBySample.keys)
        }.sorted()
        val examples = mutableListOf<RelationExample>()
        val counterexamples = mutableListOf<RelationExample>()
        var matches = 0
        var exact = 0
        common.groupBy { aBySample.getValue(it).matchId }.values.forEach { group ->
            if (group.isNotEmpty()) matches++
        }
        common.forEach { key ->
            val x = aBySample.getValue(key)
            val y = bBySample.getValue(key)
            val z = cBySample?.get(key)
            val (holds, expected) = predicate(x.value, y.value, z?.value)
            val example = RelationExample(
                matchId = x.matchId, timestamp = x.timestamp, playerId = x.playerId, playerName = x.playerName,
                a = x.value, b = y.value, c = z?.value, expected = expected, difference = x.value - expected,
            )
            if (holds) { exact++; if (examples.size < 8) examples += example }
            else if (counterexamples.size < 8) counterexamples += example
        }
        val support = if (common.isEmpty()) 0.0 else exact.toDouble() / common.size
        val variation = listOfNotNull(inventory[a], inventory[b], c?.let { inventory[it] })
            .count { it.distinctValueCount > 1 } >= if (c == null) 2 else 3
        val explained = listOfNotNull(a, b, c).any { it in redundant }
        val tier = tier(common.size, matches, support, variation)
        return DiscoveryRelation(
            id = "$type:${a.aggregateIndex}:${a.code}:${b.code}:${c?.code ?: ""}",
            aggregateIndex = a.aggregateIndex, relationType = type, codeA = a.code, codeB = b.code, codeC = c?.code,
            observationsTested = common.size, matchesTested = matches, exactMatches = exact,
            violations = common.size - exact, supportRate = support, evidenceTier = tier,
            explainedByKnownMetric = explained,
            score = score(common.size, matches, support, variation, explained),
            examples = examples, counterexamples = counterexamples,
        )
    }

    private fun correlationsForAggregate(
        refs: List<CodeRef>,
        grouped: Map<CodeRef, List<DiscoveryObservation>>,
    ): List<CodeCorrelation> = buildList {
        for (left in refs.indices) {
            for (right in (left + 1) until refs.size) {
                val a = grouped.getValue(refs[left]).associateBy { sampleKey(it) }
                val b = grouped.getValue(refs[right]).associateBy { sampleKey(it) }
                val keys = a.keys.intersect(b.keys)
                if (keys.size < thresholds.candidateObservations) continue
                val x = keys.map { a.getValue(it).value.toDouble() }
                val y = keys.map { b.getValue(it).value.toDouble() }
                val correlation = pearson(x, y) ?: continue
                val zeros = x.zip(y).count { (first, second) -> first == 0.0 && second == 0.0 }
                if (zeros.toDouble() / keys.size >= 0.80) continue
                add(CodeCorrelation(
                    aggregateIndex = refs[left].aggregateIndex, codeA = refs[left].code, codeB = refs[right].code,
                    observationsTested = keys.size, matchesTested = keys.map { a.getValue(it).matchId }.toSet().size,
                    pearson = correlation, exactEqualityRate = x.zip(y).count { it.first == it.second }.toDouble() / keys.size,
                ))
            }
        }
    }

    private fun calibration(
        samples: List<AggregateSample>,
        grouped: Map<CodeRef, List<DiscoveryObservation>>,
        inventory: Map<CodeRef, CodeInventory>,
    ): List<KnownMetricCalibration> = grouped.flatMap { (ref, observations) ->
        val codeInventory = inventory.getValue(ref)
        if (
            AdvancedStatsCodeRegistry.isKnown(ref.aggregateIndex, ref.code) ||
            codeInventory.rawObservationCount < thresholds.minimumObservations ||
            codeInventory.nonZeroCount == 0
        ) return@flatMap emptyList()
        val samplesByKey = samples.filter { it.aggregateIndex == ref.aggregateIndex }.associateBy { sampleKey(it) }
        listOf("goals", "assists", "shots", "passesAttempted", "passesCompleted", "tacklesAttempted", "tacklesCompleted").mapNotNull { metric ->
            val valid = observations.mapNotNull { observation ->
                samplesByKey[sampleKey(observation)]?.knownMetrics?.get(metric)?.let { target -> observation to target }
            }
            if (valid.size < thresholds.minimumObservations) return@mapNotNull null
            val sharedZeroes = valid.count { (observation, target) -> observation.value == 0 && target == 0 }
            if (sharedZeroes.toDouble() / valid.size >= 0.80) return@mapNotNull null
            val exact = valid.count { (observation, target) -> observation.value == target }
            val support = exact.toDouble() / valid.size
            KnownMetricCalibration(
                aggregateIndex = ref.aggregateIndex, code = ref.code, metric = metric,
                observationsTested = valid.size, matchesTested = valid.map { it.first.matchId }.toSet().size,
                exactMatches = exact, supportRate = support,
                redundantWithKnownMetric = support >= 0.95 && valid.map { it.first.matchId }.toSet().size >= thresholds.candidateMatches,
            )
        }.filter { it.supportRate >= 0.60 }
    }

    private fun tier(observations: Int, matches: Int, support: Double, variation: Boolean): String = when {
        observations >= thresholds.strongObservations && matches >= thresholds.strongMatches && support >= 0.95 && variation -> "STRONG_CANDIDATE"
        observations >= thresholds.candidateObservations && matches >= thresholds.candidateMatches && support >= 0.80 && variation -> "CANDIDATE"
        else -> "COINCIDENCE"
    }

    private fun score(observations: Int, matches: Int, support: Double, variation: Boolean, redundant: Boolean): DiscoveryScore {
        val observation = (observations.coerceAtMost(thresholds.strongObservations).toDouble() / thresholds.strongObservations) * 25
        val match = (matches.coerceAtMost(thresholds.strongMatches).toDouble() / thresholds.strongMatches) * 25
        val variationScore = if (variation) 15.0 else 0.0
        val relation = support * 25
        val counterexamples = (1.0 - support) * -10
        val penalty = if (redundant) -25.0 else 0.0
        return DiscoveryScore(observation + match + variationScore + relation + counterexamples + penalty, observation, match, variationScore, relation, counterexamples, penalty)
    }

    private fun sampleKey(value: DiscoveryObservation): String = "${value.matchId}:${value.playerId}"
    private fun sampleKey(value: AggregateSample): String = "${value.matchId}:${value.playerId}"
    private fun median(sorted: List<Int>): Double = when {
        sorted.isEmpty() -> 0.0
        sorted.size % 2 == 1 -> sorted[sorted.size / 2].toDouble()
        else -> (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]).toDouble() / 2
    }
    private fun pearson(x: List<Double>, y: List<Double>): Double? {
        val meanX = x.average(); val meanY = y.average()
        val numerator = x.indices.sumOf { (x[it] - meanX) * (y[it] - meanY) }
        val denominator = sqrt(x.sumOf { (it - meanX) * (it - meanX) } * y.sumOf { (it - meanY) * (it - meanY) })
        return if (denominator == 0.0) null else numerator / denominator
    }
}
