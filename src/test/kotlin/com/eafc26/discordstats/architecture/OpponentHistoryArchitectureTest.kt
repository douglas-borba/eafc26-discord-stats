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

    @Test fun `browser uses canonical links and has no subjective rivalry language`() {
        val script=Path.of("src/main/resources/static/opponents.js").readText()
        assertThat(script).contains("/history?matchId=","/players?playerId=","/compare?firstMatchId=","data.clubId")
            .doesNotContain("freguês","carrasco","rival equilibrado","vantagem","costumamos ganhar")
    }

    @Test fun `latest meeting summary has no dominant open match action`() {
        val script=Path.of("src/main/resources/static/opponents.js").readText()
        assertThat(script).contains("match-summary-card", "Último confronto")
            .doesNotContain("Abrir partida", "card-action")
    }

    @Test fun `memorial assets and page are removed`() {
        assertThat(Path.of("src/main/resources/insights.html")).doesNotExist()
        assertThat(Path.of("src/main/resources/static/club-history.js")).doesNotExist()
        assertThat(Path.of("src/main/resources/static/club-history.css")).doesNotExist()
    }
}
