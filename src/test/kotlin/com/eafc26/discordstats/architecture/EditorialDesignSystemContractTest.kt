package com.eafc26.discordstats.architecture

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.readText

class EditorialDesignSystemContractTest {
    private val html = Path.of("src/main/resources/history.html").readText()
    private val styles = Path.of("src/main/resources/static/editorial-design-system.css").readText()
    private val language = Path.of("src/main/resources/static/editorial-design-system.js").readText()
    private val matches = Path.of("src/main/resources/static/match-history.js").readText()
    private val documentation = Path.of("docs/product-design.md").readText()

    @Test
    fun officialComponentsAndTokensAreSharedOutsideTheMatchesPage() {
        assertThat(html)
            .contains("/editorial-design-system.css")
            .contains("/editorial-design-system.js")
        assertThat(styles)
            .contains("--editorial-space-1", "--editorial-space-7", "--editorial-reading-width")
            .contains(
                ".editorial-section",
                ".hero-section",
                ".character-card",
                ".story-chapter",
                ".metric-strip",
                ".evidence-disclosure",
                ".player-performance-table",
                ".player-performance-card",
                ".outcome-badge",
                ".empty-editorial-state",
                ".loading-editorial-state",
            )
    }

    @Test
    fun charactersShareAnAnatomyAndKeepOfficialIdentities() {
        assertThat(language)
            .contains("⭐", "🛡️", "📉")
            .contains("tone: \"highlight\"", "tone: \"defense\"", "tone: \"development\"")
        assertThat(matches)
            .contains("character-card character-card--")
            .contains("character-card__identity", "character-card__icon")
            .contains("data-awarded=\"${'$'}{award.awarded}\"")
        assertThat(styles)
            .contains(".character-card[data-awarded=\"false\"]")
            .contains("border-style: dashed")
    }

    @Test
    fun narrativesUseTheOfficialChapterLanguage() {
        assertThat(language)
            .contains("title: \"Fez a Diferença\"", "icon: \"⚡\"")
            .contains("title: \"Perigo Constante\"", "icon: \"🎯\"")
            .contains("title: \"Ficou no Quase\"", "icon: \"⏳\"")
        assertThat(matches)
            .contains("story-chapter story-chapter--")
            .contains("story-chapter__identity", "story-chapter__players")
    }

    @Test
    fun technicalEvidenceRemainsCollapsedByDefault() {
        assertThat(matches)
            .contains("<details class=\"evidence-disclosure\">")
            .doesNotContain("<details class=\"evidence-disclosure\" open>")
    }

    @Test
    fun productDesignDocumentsThePermanentSystemAndFutureReuse() {
        assertThat(documentation)
            .contains("## Design System Editorial")
            .contains("### Sistema oficial de iconografia")
            .contains("### Componentes oficiais")
            .contains("### Reuso na Etapa 3")
            .contains("Fato → Evidência → Mensagem")
    }
}
