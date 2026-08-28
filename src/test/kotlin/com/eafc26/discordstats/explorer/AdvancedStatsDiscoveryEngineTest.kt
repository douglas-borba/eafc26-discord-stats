package com.eafc26.discordstats.explorer

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AdvancedStatsDiscoveryEngineTest {

    private val engine = AdvancedStatsDiscoveryEngine(
        AdvancedStatsDiscoveryEngine.Thresholds(
            minimumObservations = 3,
            minimumMatches = 2,
            candidateObservations = 4,
            candidateMatches = 2,
            strongObservations = 6,
            strongMatches = 3,
            minimumInformativeObservations = 2,
            candidateInformativeObservations = 3,
            strongInformativeObservations = 4,
            maximumTernaryCodes = 9,
            maximumRelations = 1_000,
        ),
    )

    private fun sample(
        index: Int,
        values: Map<Int, Int>,
        aggregate: Int = 0,
        goals: Int = 0,
        assists: Int = 0,
    ) = AdvancedStatsDiscoveryEngine.AggregateSample(
        clubId = "club-1",
        matchId = "match-$index",
        timestamp = "2026-08-28T00:00:00Z",
        playerId = "player-$index",
        playerName = "Player $index",
        aggregateIndex = aggregate,
        sparseValues = values,
        knownMetrics = mapOf(
            "goals" to goals,
            "assists" to assists,
            "shots" to 0,
            "passesAttempted" to 0,
            "passesCompleted" to 0,
            "tacklesAttempted" to 0,
            "tacklesCompleted" to 0,
        ),
    )

    @Test
    fun `conditional probabilities are null for zero denominators and finite otherwise`() {
        val zero = engine.investigateAnchor(
            listOf(sample(1, mapOf(10 to 0, 11 to 2)), sample(2, mapOf(10 to 0, 11 to 3))),
            AdvancedStatsDiscoveryEngine.AnchorRef("AGGREGATE_CODE", 0, 10, null),
        ).conditionalProfiles.single { it.candidateCode == 11 }
        assertThat(zero.pCandidateActiveGivenAnchorActive).isNull()
        assertThat(zero.pAnchorActiveGivenCandidateActive).isEqualTo(0.0)

        val valid = engine.investigateAnchor(
            listOf(sample(1, mapOf(10 to 1, 11 to 2)), sample(2, mapOf(10 to 1, 11 to 0))),
            AdvancedStatsDiscoveryEngine.AnchorRef("AGGREGATE_CODE", 0, 10, null),
        ).conditionalProfiles.single { it.candidateCode == 11 }
        assertThat(valid.pCandidateActiveGivenAnchorActive).isEqualTo(0.5)
    }

    @Test
    fun `anchor payload serializes undefined conditional probabilities as null never NaN or Infinity`() {
        val result = engine.investigateAnchor(
            listOf(sample(1, mapOf(10 to 0, 11 to 2)), sample(2, mapOf(10 to 0, 11 to 3))),
            AdvancedStatsDiscoveryEngine.AnchorRef("AGGREGATE_CODE", 0, 10, null),
        )
        val json = jacksonObjectMapper().findAndRegisterModules().writeValueAsString(result)

        assertThat(json).contains("\"pCandidateActiveGivenAnchorActive\":null")
        assertThat(json).doesNotContain("\"pcandidateActiveGivenAnchorActive\"")
        assertThat(json).doesNotContain("NaN", "Infinity", "-Infinity")
    }

    @Test
    fun `sparse missing values become zero only inside available aggregate observations`() {
        val result = engine.analyze(
            listOf(sample(0, mapOf(10 to 4, 11 to 1)), sample(1, mapOf(11 to 2)), sample(2, mapOf(11 to 3))),
            hideKnownRelationships = false,
        )

        val code10 = result.inventory.single { it.aggregateIndex == 0 && it.code == 10 }
        assertThat(code10.rawObservationCount).isEqualTo(3)
        assertThat(code10.zeroCount).isEqualTo(2)
    }

    @Test
    fun `global and informative support separate zero zero coincidence from active failures`() {
        val result = engine.analyze((0 until 24).map { index ->
            if (index < 20) sample(index, mapOf(10 to 0, 11 to 0, 12 to index + 1))
            else sample(index, mapOf(10 to 1, 11 to 2, 12 to index + 1))
        }, hideKnownRelationships = false)

        val equality = result.relations.single { it.relationType == "EQUAL" && it.codeA == 10 && it.codeB == 11 }
        assertThat(equality.supportRate).isEqualTo(20.0 / 24.0)
        assertThat(equality.evidence.informativeSupport).isZero()
        assertThat(equality.evidence.bothZeroCount).isEqualTo(20)
        assertThat(equality.evidence.aNonZeroCount).isEqualTo(4)
        assertThat(equality.evidence.bNonZeroCount).isEqualTo(4)
        assertThat(equality.evidence.bothNonZeroCount).isEqualTo(4)
        assertThat(equality.evidence.eitherNonZeroCount).isEqualTo(4)
        assertThat(equality.evidence.overlapAmongActive).isEqualTo(1.0)
        assertThat(equality.evidence.zeroDominated).isTrue()
        assertThat(equality.evidenceTier).isEqualTo("COINCIDENCE")
    }

    @Test
    fun `active exact equality remains strong without hardcoded mappings`() {
        val result = engine.analyze((0 until 8).map { index ->
            val value = index % 4
            sample(index, mapOf(20 to value, 21 to value, 22 to index + 1))
        }, hideKnownRelationships = false)

        val equality = result.relations.single { it.relationType == "EQUAL" && it.codeA == 20 && it.codeB == 21 }
        assertThat(equality.evidence.informativeSupport).isEqualTo(1.0)
        assertThat(equality.evidence.zeroDominated).isFalse()
        assertThat(equality.evidenceTier).isEqualTo("STRONG_CANDIDATE")
    }

    @Test
    fun `all zero observations do not strengthen sum or difference`() {
        val result = engine.analyze((0 until 24).map { index ->
            if (index < 20) sample(index, mapOf(30 to 0, 31 to 0, 32 to 0, 33 to index + 1))
            else sample(index, mapOf(30 to 9, 31 to 2, 32 to 3, 33 to index + 1))
        }, hideKnownRelationships = false)

        val sum = result.relations.single { it.relationType == "SUM" && it.codeA == 30 && setOf(it.codeB, it.codeC) == setOf(31, 32) }
        val difference = result.relations.single { it.relationType == "DIFFERENCE" && it.codeA == 30 && setOf(it.codeB, it.codeC) == setOf(31, 32) }
        assertThat(sum.evidence.allZeroCount).isEqualTo(20)
        assertThat(sum.evidence.informativeSupport).isZero()
        assertThat(difference.evidence.informativeSupport).isZero()
        assertThat(sum.evidenceTier).isEqualTo("COINCIDENCE")
        assertThat(difference.evidenceTier).isEqualTo("COINCIDENCE")
    }

    @Test
    fun `inequality is never strong and ranks below exact equality sum and difference`() {
        val result = engine.analyze((0 until 8).map { index ->
            val base = index + 1
            val addend = (index % 3) + 1
            sample(index, mapOf(40 to base, 41 to base, 42 to base + addend, 43 to addend, 44 to base - addend))
        }, hideKnownRelationships = false)

        val equality = result.relations.single { it.relationType == "EQUAL" && it.codeA == 40 && it.codeB == 41 }
        val sum = result.relations.single { it.relationType == "SUM" && it.codeA == 42 && setOf(it.codeB, it.codeC) == setOf(40, 43) }
        val difference = result.relations.single { it.relationType == "DIFFERENCE" && it.codeA == 44 && setOf(it.codeB, it.codeC) == setOf(40, 43) }
        val inequality = result.relations.single { it.relationType == "LESS_OR_EQUAL" && it.codeA == 40 && it.codeB == 42 }
        assertThat(inequality.evidenceTier).isNotEqualTo("STRONG_CANDIDATE")
        assertThat(inequality.score.total).isLessThan(equality.score.total)
        assertThat(inequality.score.total).isLessThan(sum.score.total)
        assertThat(inequality.score.total).isLessThan(difference.score.total)
    }

    @Test
    fun `zero dominated calibration is hidden while meaningful controls remain`() {
        val result = engine.analyze((0 until 24).map { index ->
            if (index < 20) sample(index, mapOf(50 to 0, 51 to 0, 52 to index + 1), goals = 0)
            else sample(index, mapOf(50 to 1, 51 to index - 19, 52 to index + 1), goals = index - 19)
        }, hideKnownRelationships = false)

        assertThat(result.calibration).noneMatch { it.code == 50 && it.metric == "goals" }
        val meaningful = result.calibration.single { it.code == 51 && it.metric == "goals" }
        assertThat(meaningful.informativeSupport).isEqualTo(1.0)
        assertThat(meaningful.bothZeroCount).isEqualTo(20)
        assertThat(meaningful.redundantWithKnownMetric).isTrue()
    }

    @Test
    fun `positive controls emerge from data without registry mutation`() {
        val result = engine.analyze((0 until 8).map { index ->
            val goals = index % 3
            val assists = (index + 1) % 2
            sample(index, mapOf(6 to index, 11 to assists, 214 to goals, 215 to index + 1), goals = goals, assists = assists)
        }, hideKnownRelationships = false)

        assertThat(result.calibration).anyMatch { it.code == 11 && it.metric == "assists" && it.informativeSupport == 1.0 }
        assertThat(result.calibration).anyMatch { it.code == 214 && it.metric == "goals" && it.informativeSupport == 1.0 }
        assertThat(result.inventory.single { it.code == 6 }.confidence).isEqualTo("HYPOTHESIS")
    }

    @Test
    fun `constant and zero dominated correlation stay excluded while low overlap is flagged`() {
        val samples = (0 until 12).map { index ->
            val a = if (index < 6) index + 1 else 0
            val b = if (index < 6) 0 else index - 5
            sample(index, mapOf(60 to 1, 61 to if (index == 0) 2 else 0, 62 to a, 63 to b, 64 to index + 1))
        }
        val result = engine.analyze(samples, hideKnownRelationships = false)

        assertThat(result.correlations).noneMatch { it.codeA in setOf(60, 61) || it.codeB in setOf(60, 61) }
        val lowOverlap = result.correlations.single { setOf(it.codeA, it.codeB) == setOf(62, 63) }
        assertThat(lowOverlap.pearson).isLessThan(-0.6)
        assertThat(lowOverlap.overlapAmongActive).isZero()
        assertThat(lowOverlap.penalizedForLowOverlap).isTrue()
    }

    @Test
    fun `related code families are aggregate local and reject weak bridges`() {
        val result = engine.analyze((0 until 10).flatMap { index ->
            listOf(
                sample(index, mapOf(26 to index + 1, 30 to (index + 1) * 2, 34 to (index + 1) * 3, 90 to if (index == 0) 10 else 0), aggregate = 0),
                sample(index + 100, mapOf(26 to index + 1, 30 to (index + 1) * 2, 34 to (index + 1) * 3), aggregate = 1),
            )
        }, hideKnownRelationships = false)

        assertThat(result.relatedCodeFamilies).anyMatch { it.aggregateIndex == 0 && it.codes.containsAll(listOf(26, 30, 34)) }
        assertThat(result.relatedCodeFamilies).anyMatch { it.aggregateIndex == 1 && it.codes.containsAll(listOf(26, 30, 34)) }
        assertThat(result.relatedCodeFamilies).noneMatch { it.codes.contains(90) }
        assertThat(result.relatedCodeFamilies).allMatch { it.edges.all { edge -> edge.aggregateIndex == it.aggregateIndex } }
    }

    @Test
    fun `aggregate identities never mix and ternary work remains bounded`() {
        val bounded = AdvancedStatsDiscoveryEngine(
            AdvancedStatsDiscoveryEngine.Thresholds(
                minimumObservations = 3, minimumMatches = 2, candidateObservations = 3, candidateMatches = 2,
                minimumInformativeObservations = 2, candidateInformativeObservations = 2, strongInformativeObservations = 2,
                maximumTernaryCodes = 4, maximumRelations = 25,
            ),
        )
        val result = bounded.analyze((0 until 6).flatMap { index ->
            listOf(sample(index, (1..30).associateWith { code -> code + index }, 0), sample(index + 100, mapOf(24 to 20 - index), 1))
        }, hideKnownRelationships = false)

        assertThat(result.inventory.filter { it.code == 24 }).extracting<Int> { it.aggregateIndex }.containsExactly(0, 1)
        assertThat(result.relations).hasSizeLessThanOrEqualTo(25)
    }
}
