package com.eafc26.discordstats.explorer

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AdvancedStatsDiscoveryEngineTest {

    private val engine = AdvancedStatsDiscoveryEngine(
        AdvancedStatsDiscoveryEngine.Thresholds(
            minimumObservations = 3,
            minimumMatches = 2,
            candidateObservations = 4,
            candidateMatches = 2,
            strongObservations = 30,
            strongMatches = 5,
            maximumTernaryCodes = 9,
            maximumRelations = 1_000,
        ),
    )

    private fun sample(
        index: Int,
        values: Map<Int, Int>,
        aggregate: Int = 0,
        goals: Int = 0,
    ) = AdvancedStatsDiscoveryEngine.AggregateSample(
        clubId = "club-1",
        matchId = "match-${index / 2}",
        timestamp = "2026-08-28T00:00:00Z",
        playerId = "player-$index",
        playerName = "Player $index",
        aggregateIndex = aggregate,
        sparseValues = values,
        knownMetrics = mapOf(
            "goals" to goals,
            "assists" to 0,
            "shots" to 0,
            "passesAttempted" to 0,
            "passesCompleted" to 0,
            "tacklesAttempted" to 0,
            "tacklesCompleted" to 0,
        ),
    )

    @Test
    fun `missing sparse code becomes zero only within available aggregate observations`() {
        val result = engine.analyze(
            listOf(
                sample(0, mapOf(10 to 4, 11 to 1)),
                sample(1, mapOf(11 to 2)),
                sample(2, mapOf(11 to 3)),
                sample(3, mapOf(11 to 4)),
            ),
            hideKnownRelationships = false,
        )

        val code10 = result.inventory.single { it.aggregateIndex == 0 && it.code == 10 }
        assertThat(code10.rawObservationCount).isEqualTo(4)
        assertThat(code10.zeroCount).isEqualTo(3)
        assertThat(result.aggregate1CodeCount).isZero()
    }

    @Test
    fun `aggregate identities never mix even when the code is equal`() {
        val result = engine.analyze(
            (0..4).flatMap { index ->
                listOf(sample(index, mapOf(24 to index + 1), 0), sample(index, mapOf(24 to 20 - index), 1))
            },
            hideKnownRelationships = false,
        )

        assertThat(result.inventory.filter { it.code == 24 }).extracting<Int> { it.aggregateIndex }
            .containsExactly(0, 1)
        assertThat(result.relations).allMatch { it.aggregateIndex == 0 || it.aggregateIndex == 1 }
    }

    @Test
    fun `exact equality inequality sum and difference expose support and counterexamples`() {
        val samples = (0..5).map { index ->
            val base = index + 1
            val addend = (index % 3) + 1
            val subtractor = (index % 2) + 1
            sample(index, mapOf(
                10 to base,
                11 to base,
                12 to (base + 1),
                13 to base,
                14 to (base + addend),
                15 to base,
                16 to addend,
                17 to (base + addend - subtractor),
                18 to subtractor,
            ))
        }.toMutableList()
        samples[5] = sample(5, mapOf(10 to 6, 11 to 99, 12 to 7, 13 to 6, 14 to 9, 15 to 6, 16 to 3, 17 to 7, 18 to 2))

        val result = engine.analyze(samples, hideKnownRelationships = false)

        val equality = result.relations.first { it.relationType == "EQUAL" && it.codeA == 10 && it.codeB == 11 }
        assertThat(equality.exactMatches).isEqualTo(5)
        assertThat(equality.violations).isEqualTo(1)
        assertThat(equality.counterexamples).hasSize(1)
        assertThat(result.relations).anyMatch { it.relationType == "GREATER_OR_EQUAL" && it.codeA == 12 && it.codeB == 13 }
        assertThat(result.relations).anyMatch { it.relationType == "SUM" && it.codeA == 14 && setOf(it.codeB, it.codeC) == setOf(15, 16) }
        assertThat(result.relations).anyMatch { it.relationType == "DIFFERENCE" && it.codeA == 17 && setOf(it.codeB, it.codeC) == setOf(14, 18) }
    }

    @Test
    fun `constant and zero dominated codes do not create correlations`() {
        val result = engine.analyze(
            (0..19).map { index -> sample(index, mapOf(30 to 1, 31 to if (index == 0) 2 else 0, 32 to index)) },
            hideKnownRelationships = false,
        )

        assertThat(result.inventory.single { it.code == 30 }.technicalClassification).isEqualTo("LOW_VARIANCE")
        assertThat(result.inventory.single { it.code == 31 }.technicalClassification).isEqualTo("RARE")
        assertThat(result.correlations).noneMatch { it.codeA in setOf(30, 31) || it.codeB in setOf(30, 31) }
    }

    @Test
    fun `calibration is isolated and can mark redundant candidates without mapping them`() {
        val result = engine.analyze(
            (0..5).map { index -> sample(index, mapOf(70 to index % 3, 71 to index + 1), goals = index % 3) },
            hideKnownRelationships = false,
        )

        val calibration = result.calibration.single { it.code == 70 && it.metric == "goals" }
        assertThat(calibration.redundantWithKnownMetric).isTrue()
        assertThat(result.inventory.single { it.code == 70 }.confidence).isEqualTo("UNKNOWN")
    }

    @Test
    fun `code 6 remains hypothesis and is never confirmed by discovery`() {
        val result = engine.analyze(
            (0..5).map { index -> sample(index, mapOf(6 to index, 80 to index + 1)) },
            hideKnownRelationships = false,
        )

        assertThat(result.inventory.single { it.code == 6 }.confidence).isEqualTo("HYPOTHESIS")
        assertThat(result.inventory.none { it.code == 6 && it.confidence == "CONFIRMED" }).isTrue()
    }

    @Test
    fun `ternary search is explicitly capped before it can become combinatorial`() {
        val boundedEngine = AdvancedStatsDiscoveryEngine(
            AdvancedStatsDiscoveryEngine.Thresholds(
                minimumObservations = 3,
                minimumMatches = 2,
                candidateObservations = 3,
                candidateMatches = 2,
                maximumTernaryCodes = 4,
                maximumRelations = 25,
            ),
        )
        val samples = (0..5).map { index ->
            sample(index, (1..30).associateWith { code -> code + index })
        }

        val result = boundedEngine.analyze(samples, hideKnownRelationships = false)

        assertThat(result.relations).hasSizeLessThanOrEqualTo(25)
        assertThat(result.inventory).hasSize(30)
    }
}
