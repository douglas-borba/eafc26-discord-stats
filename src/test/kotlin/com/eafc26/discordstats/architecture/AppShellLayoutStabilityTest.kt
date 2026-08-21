package com.eafc26.discordstats.architecture

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class AppShellLayoutStabilityTest {
    private val resources = Path.of("src/main/resources")

    @Test
    fun `app shell owns the global content geometry`() {
        val css = Files.readString(resources.resolve("static/app-shell.css"))

        assertThat(css).contains(
            "overflow-y: scroll;",
            "scrollbar-gutter: stable;",
            "grid-template-columns: var(--shell-sidebar-width) minmax(0, 1fr);",
            "--shell-content-max-width: 1480px;",
            "--shell-content-padding-x: 26px;",
            ".app-shell *::after",
            "box-sizing: border-box;",
            ".app-shell-content > [data-app-content]",
            "max-width: var(--shell-content-max-width);",
            "padding: var(--shell-content-padding-y) var(--shell-content-padding-x);",
        )
    }

    @Test
    fun `product pages do not own their outer width margin or padding`() {
        val pageStyles = mapOf(
            "index.html" to listOf("max-width: 1100px", "body { padding:"),
            "players.html" to listOf("width:min(1280px,100%)", ".shell { padding:", "margin:auto"),
            "compare.html" to listOf("width:min(1440px,100%)", ".shell{padding:", "margin:auto"),
            "static/match-history.css" to listOf("width: min(1480px, 100%)", ".matches-page { padding:", "margin: 0 auto"),
        )

        pageStyles.forEach { (file, forbiddenRules) ->
            assertThat(Files.readString(resources.resolve(file))).describedAs(file).doesNotContain(*forbiddenRules.toTypedArray())
        }
    }
}
