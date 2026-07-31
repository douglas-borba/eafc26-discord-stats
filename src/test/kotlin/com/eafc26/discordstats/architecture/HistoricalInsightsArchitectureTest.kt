package com.eafc26.discordstats.architecture

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.readText

class HistoricalInsightsArchitectureTest {
    @Test
    fun `historical insights reuse history and player profile services`() {
        val source = Path.of(
            "src/main/kotlin/com/eafc26/discordstats/service/HistoricalInsightsService.kt"
        ).readText()

        assertThat(source).contains(
            "private val matchHistoryService: MatchHistoryService",
            "private val playerProfileService: PlayerProfileService",
        )
        assertThat(source).doesNotContain(
            "CanonicalMatchRepository",
            "MatchResponse",
            "EaMatchMapper",
            "MatchInterpreter",
            "MatchStoryExtractor",
        )
    }

    @Test
    fun `insights web layer consumes only historical insights service`() {
        val source = Path.of(
            "src/main/kotlin/com/eafc26/discordstats/web/HistoricalInsightsController.kt"
        ).readText()

        assertThat(source).contains(
            "private val historicalInsightsService: HistoricalInsightsService"
        )
        assertThat(source).doesNotContain(
            "MatchHistoryService",
            "PlayerProfileService",
            "MatchComparisonService",
            "CanonicalMatchRepository",
            "CanonicalMatch",
            "MatchResponse",
            "MatchInterpreter",
        )
    }
}
