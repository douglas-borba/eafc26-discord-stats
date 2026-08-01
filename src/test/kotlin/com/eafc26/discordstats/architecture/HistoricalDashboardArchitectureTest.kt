package com.eafc26.discordstats.architecture

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.readText

class HistoricalDashboardArchitectureTest {
    @Test
    fun `historical dashboard controller has MatchHistoryService as its only data dependency`() {
        val source = Path.of(
            "src/main/kotlin/com/eafc26/discordstats/web/MatchHistoryController.kt"
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
    fun `historical presentation only projects canonical objects`() {
        val source = Path.of(
            "src/main/kotlin/com/eafc26/discordstats/presentation/history/HistoricalMatchPresentation.kt"
        ).readText()

        assertThat(source).contains("canonical: CanonicalMatch")
        assertThat(source).doesNotContain(
            "CanonicalMatchRepository",
            "MatchHistoryService",
            "MatchResponse",
            "EaMatchMapper",
            "MatchInterpreter",
            "MatchStoryExtractor",
        )
    }
}
