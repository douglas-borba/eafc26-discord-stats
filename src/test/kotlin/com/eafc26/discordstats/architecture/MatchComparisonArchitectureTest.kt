package com.eafc26.discordstats.architecture

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.readText

class MatchComparisonArchitectureTest {
    @Test
    fun `comparison layer has history service as its only data source`() {
        val source = Path.of(
            "src/main/kotlin/com/eafc26/discordstats/service/MatchComparisonService.kt"
        ).readText()

        assertThat(source).contains("private val matchHistoryService: MatchHistoryService")
        assertThat(source).doesNotContain(
            "CanonicalMatchRepository",
            "MatchResponse",
            "EaMatchMapper",
            "MatchInterpreter",
            "MatchStoryExtractor",
        )
    }

    @Test
    fun `comparison web layer consumes only comparison service`() {
        val source = Path.of(
            "src/main/kotlin/com/eafc26/discordstats/web/MatchComparisonController.kt"
        ).readText()

        assertThat(source).contains("private val matchComparisonService: MatchComparisonService")
        assertThat(source).doesNotContain(
            "MatchHistoryService",
            "CanonicalMatchRepository",
            "CanonicalMatch",
            "MatchResponse",
            "MatchInterpreter",
            "MatchStoryExtractor",
        )
    }
}
