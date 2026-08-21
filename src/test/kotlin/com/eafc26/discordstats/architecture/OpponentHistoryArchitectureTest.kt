package com.eafc26.discordstats.architecture

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.readText

class OpponentHistoryArchitectureTest {
    @Test fun `opponent projection depends only on match history service`() {
        val source=Path.of("src/main/kotlin/com/eafc26/discordstats/service/OpponentHistoryService.kt").readText()
        assertThat(source).contains("private val matchHistoryService: MatchHistoryService")
            .doesNotContain("CanonicalMatchRepository","MatchResponse","EaMatchMapper","MatchInterpreter","MatchStoryExtractor","DiscordRenderer")
    }

    @Test fun `web layer consumes only opponent history service`() {
        val source=Path.of("src/main/kotlin/com/eafc26/discordstats/web/OpponentHistoryController.kt").readText()
        assertThat(source).contains("private val opponentHistoryService: OpponentHistoryService")
            .doesNotContain("CanonicalMatchRepository","MatchHistoryService","MatchInterpreter","MatchStoryExtractor","MatchResponse")
    }

    @Test fun `removed legacy opponents assets stay absent`() {
        assertThat(Path.of("src/main/resources/opponents.html")).doesNotExist()
        assertThat(Path.of("src/main/resources/static/opponents.js")).doesNotExist()
        assertThat(Path.of("src/main/resources/static/opponents.css")).doesNotExist()
        assertThat(Path.of("src/main/resources/insights.html")).doesNotExist()
        assertThat(Path.of("src/main/resources/static/club-history.js")).doesNotExist()
        assertThat(Path.of("src/main/resources/static/club-history.css")).doesNotExist()
    }

    @Test fun `legacy match detail keeps opponent text without a removed history link`() {
        val script = Path.of("src/main/resources/static/match-history.js").readText()
        assertThat(script).contains("<span class=\"match-hero-club\">")
            .doesNotContain("href=\"/opponents/")
    }
}
