package com.eafc26.discordstats.architecture

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.readText

class PlayerProfileArchitectureTest {
    @Test
    fun `profile aggregation depends only on match history service`() {
        val source = Path.of(
            "src/main/kotlin/com/eafc26/discordstats/service/PlayerProfileService.kt"
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
    fun `profile web layer consumes only profile aggregation`() {
        val source = Path.of(
            "src/main/kotlin/com/eafc26/discordstats/web/PlayerProfileController.kt"
        ).readText()

        assertThat(source).contains("private val playerProfileService: PlayerProfileService")
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
