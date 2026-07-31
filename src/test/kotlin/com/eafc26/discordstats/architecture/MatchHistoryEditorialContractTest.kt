package com.eafc26.discordstats.architecture

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.readText

class MatchHistoryEditorialContractTest {
    private val html = Path.of("src/main/resources/history.html").readText()
    private val script = Path.of("src/main/resources/static/match-history.js").readText()
    private val editorialLanguage = Path.of("src/main/resources/static/editorial-design-system.js").readText()

    @Test
    fun partidasPageDefinesTheApprovedEditorialReadingOrder() {
        val match = script.indexOf("<h2>A Partida</h2>")
        val characters = script.indexOf("\"Personagens da Partida\"")
        val stories = script.indexOf("\"A História do Jogo\"")
        val team = script.indexOf("\"O Time em Números\"")
        val players = script.indexOf("\"Jogador por Jogador\"")
        val evidence = script.indexOf("\"Critérios e Evidências\"")

        assertThat(html).contains("<title>Partidas — EA FC STATS</title>", "<h1>Partidas</h1>")
        assertThat(match).isGreaterThan(0)
        assertThat(characters).isGreaterThan(match)
        assertThat(stories).isGreaterThan(characters)
        assertThat(team).isGreaterThan(stories)
        assertThat(players).isGreaterThan(team)
        assertThat(evidence).isGreaterThan(players)
    }

    @Test
    fun technicalBagreRemainsTheContractKeyWhilePresentationUsesMenorDesempenho() {
        assertThat(editorialLanguage)
            .contains("BAGRE: {")
            .contains("label: \"Menor Desempenho\"")
            .contains("Menor desempenho entre os jogadores elegíveis nesta partida.")
            .doesNotContain("Bagre da Partida", "Atuação do Bagre")
        assertThat(script).contains("const awardVoice = editorialDesign.characters")
    }

    @Test
    fun footballConclusionsAreConsumedWithoutBrowserSideAggregation() {
        assertThat(script)
            .contains("match.summary.ourClub.score")
            .contains("story.type === \"HIGHLIGHTS\"")
            .contains("fact.label === \"Média do time\"")
            .doesNotContain(".reduce(", "MatchInterpreter", "MatchStoryExtractor")
    }

    @Test
    fun technicalEvidenceUsesProgressiveDisclosure() {
        assertThat(script)
            .contains("<details class=\"evidence-disclosure\">")
            .contains("award.ruleIds", "story.ruleIds", "story.evidenceCount")
            .contains("match.provenance.schemaVersion", "match.provenance.engineVersion")
    }
}
