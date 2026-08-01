package com.eafc26.discordstats.architecture

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.readText

class ClubHistoryExperienceContractTest {
    private val html = Path.of("src/main/resources/insights.html").readText()
    private val script = Path.of("src/main/resources/static/club-history.js").readText()
    private val overview = Path.of("src/main/resources/index.html").readText()

    @Test
    fun `memorial follows approved deterministic priorities and limits`() {
        assertThat(html).contains("História do Clube", "Memorial esportivo")
        assertThat(script)
            .contains("BIGGEST_WIN", "FIRST_WIN", "BEST_TEAM_AVERAGE", "TOP_SCORER")
            .contains(".slice(0, 4)", ".slice(0,3)", ".slice(0,5)")
            .contains("Ver comprovação", "História principal", "Pessoas que deixaram marcas")
            .doesNotContain("Math.random", "insights determinísticos", "partidas persistidas")
    }

    @Test
    fun `entity exploration uses canonical identifiers`() {
        assertThat(script)
            .contains("player.playerId", "matchId=", "playerId=")
            .doesNotContain("player.name)}&", "narrativeKey")
    }

    @Test
    fun `editorial terminology does not expose the legacy award name`() {
        assertThat(script).contains("Menor Desempenho").doesNotContain("Mais Bagres", "🐟")
    }

    @Test
    fun `official match summary shell is shared with overview`() {
        assertThat(html).contains("/match-summary-card.css")
        assertThat(overview).contains("/match-summary-card.css", "match-card match-summary-card")
    }
}
